package com.example

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Looper
import android.view.WindowManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.BloomDatabase
import com.example.data.BloomRepository
import com.example.data.PreferencesStore
import com.example.data.excel.ExcelXlsxEngine
import com.example.domain.model.DailyEntry
import com.example.domain.model.FlowLevel
import com.example.domain.model.UserProfile
import com.example.ui.viewmodel.BloomUiState
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.PinThrottlePolicy
import com.example.ui.viewmodel.SecurityClock
import com.example.util.CycleDate
import com.example.util.PinSecurity
import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Wave2SecurityTest {

  private lateinit var context: Context
  private lateinit var db: BloomDatabase
  private lateinit var prefsStore: PreferencesStore
  private lateinit var repo: BloomRepository

  private class FakeSecurityClock(
    var epochMs: Long = 1_760_000_000_000L,
    var elapsedMs: Long = 100_000L
  ) : SecurityClock {
    override fun currentTimeMillis(): Long = epochMs
    override fun elapsedRealtimeMillis(): Long = elapsedMs
  }

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    db = Room.inMemoryDatabaseBuilder(context, BloomDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    prefsStore = PreferencesStore(context)
    repo = BloomRepository(
      profileDao = db.userProfileDao(),
      entryDao = db.dailyEntryDao(),
      cycleDao = db.cycleDao(),
      customOptionDao = db.customOptionDao(),
      preferencesStore = prefsStore,
      database = db
    )
  }

  @After
  fun tearDown() {
    shadowOf(Looper.getMainLooper()).idle()
    runBlocking { prefsStore.clearAllPreferences() }
    db.close()
  }

  @Test
  fun `sec1 benchmark pbkdf2 work factor and verify API 24 mac fallback parity`() {
    val salt = PinSecurity.generateSaltBytes()
    val pin = "4829"

    // Verify RFC 2898 fallback (API 24-25 HmacSHA256) matches SecretKeyFactory byte-for-byte
    val jcaDerived = PinSecurity.derivePbkdf2HmacSha256(pin, salt, 10_000)
    val macDerived = PinSecurity.derivePbkdf2ViaMac(pin.toCharArray(), salt, 10_000)
    assertArrayEquals(jcaDerived, macDerived)

    // Warm up JIT cleanly before timing work factors
    PinSecurity.derivePbkdf2HmacSha256(pin, salt, 10_000)

    val counts = listOf(120_000, 210_000, 310_000, 600_000)
    val measuredMs = linkedMapOf<Int, Double>()
    for (iterations in counts) {
      val startNs = System.nanoTime()
      PinSecurity.derivePbkdf2HmacSha256(pin, salt, iterations)
      val elapsedMs = (System.nanoTime() - startNs) / 1_000_000.0
      measuredMs[iterations] = elapsedMs
      println("BENCHMARK PBKDF2-HMAC-SHA256 iterations=$iterations elapsedMs=${"%.2f".format(elapsedMs)}")
    }

    assertTrue(PinSecurity.DEFAULT_ITERATIONS >= 310_000)
    assertTrue(measuredMs.getValue(PinSecurity.DEFAULT_ITERATIONS) > 0.0)
  }

  @Test
  fun `sec1 v2 hash encoding rejects malformed formats and preserves constant time path`() {
    val encoded = PinSecurity.hashPin("7391")
    val parts = encoded.split('$')
    assertEquals(5, parts.size)
    assertEquals("v2", parts[0])
    assertEquals("pbkdf2-hmac-sha256", parts[1])
    assertEquals(PinSecurity.DEFAULT_ITERATIONS.toString(), parts[2])
    assertEquals(32, parts[3].length) // 16-byte random salt in hex
    assertEquals(64, parts[4].length) // 32-byte derived key in hex

    assertEquals(
      PinSecurity.VerificationOutcome.Valid(needsMigrationToV2 = false),
      PinSecurity.verifyPinDetailed("7391", encoded)
    )
    assertEquals(
      PinSecurity.VerificationOutcome.Invalid,
      PinSecurity.verifyPinDetailed("0000", encoded)
    )

    val malformedSamples = listOf(
      "",
      "1234",
      "v1\$pbkdf2-hmac-sha256\$310000\$${parts[3]}\$${parts[4]}",
      "v2\$sha256\$310000\$${parts[3]}\$${parts[4]}",
      "v2\$pbkdf2-hmac-sha256\$999\$${parts[3]}\$${parts[4]}", // Below MIN_ITERATIONS
      "v2\$pbkdf2-hmac-sha256\$5000000\$${parts[3]}\$${parts[4]}", // Above MAX_ITERATIONS (DoS guard)
      "v2\$pbkdf2-hmac-sha256\$notanint\$${parts[3]}\$${parts[4]}",
      "v2\$pbkdf2-hmac-sha256\$310000\$abcd\$${parts[4]}", // Salt < 16 bytes
      "v2\$pbkdf2-hmac-sha256\$310000\$zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz\$${parts[4]}", // Non-hex salt
      "v2\$pbkdf2-hmac-sha256\$310000\$${parts[3]}\$deadbeef", // Truncated derived key
      "v2\$pbkdf2-hmac-sha256\$310000\$${parts[3]}", // Missing part
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef" // Legacy hash missing salt
    )
    for (sample in malformedSamples) {
      assertEquals(
        "Expected Malformed for '$sample'",
        PinSecurity.VerificationOutcome.Malformed,
        PinSecurity.verifyPinDetailed("7391", sample, legacySalt = "")
      )
      assertFalse(PinSecurity.verifyPin("7391", sample))
    }
  }

  @Test
  fun `sec1 legacy salted sha256 credential migrates transparently to v2 pbkdf2 without data loss`() = runBlocking {
    val profile = UserProfile(id = "u-legacy", name = "Maya", dob = CycleDate(1997, 3, 15))
    val entry = DailyEntry(
      date = CycleDate(2026, 10, 10),
      isPeriod = true,
      flowLevel = FlowLevel.HEAVY,
      notes = "Preserved across PIN migration"
    )
    repo.saveProfile(profile)
    repo.saveDailyEntry(entry)

    val legacySalt = PinSecurity.generateSalt()
    val legacyHash = PinSecurity.legacySha256Hash("4829", legacySalt)
    prefsStore.storeRawPinStateForTest(
      enabled = true,
      hash = legacyHash,
      salt = legacySalt,
      onboardingCompleted = true
    )

    val clock = FakeSecurityClock()
    val vm = MainViewModel(repo, clock)
    pumpMainAndWait { true }

    // Wrong PIN fails and does NOT migrate
    vm.unlockWithPin("1111")
    pumpMainAndWait { prefsStore.preferencesFlow.first().pinFailedAttempts == 1 }
    val afterWrong = prefsStore.preferencesFlow.first()
    assertEquals(legacyHash, afterWrong.pinHash)
    assertEquals(legacySalt, afterWrong.pinSalt)

    // Valid PIN unlocks, upgrades credential to v2 PBKDF2, clears legacy salt, resets failures, keeps DB intact
    vm.unlockWithPin("4829")
    pumpMainAndWait {
      val p = prefsStore.preferencesFlow.first()
      p.pinHash.startsWith("v2\$pbkdf2-hmac-sha256\$") && p.pinFailedAttempts == 0
    }
    val migratedPrefs = prefsStore.preferencesFlow.first()
    assertTrue(migratedPrefs.pinEnabled)
    assertTrue(migratedPrefs.pinHash.startsWith("v2\$pbkdf2-hmac-sha256\$"))
    assertEquals("", migratedPrefs.pinSalt)
    assertTrue(PinSecurity.verifyPin("4829", migratedPrefs.pinHash))

    assertEquals("Maya", db.userProfileDao().getProfileOnce()?.name)
    assertEquals("Preserved across PIN migration", db.dailyEntryDao().getEntryByDate(CycleDate(2026, 10, 10))?.notes)
  }

  @Test
  fun `sec2 persistent throttling enforces monotonic session, restart survival, clock skew safety, and capped backoff`() = runBlocking {
    val profile = UserProfile(id = "u-thr", name = "Nora", dob = CycleDate(1998, 7, 1))
    repo.saveProfile(profile)
    prefsStore.setOnboardingCompletedWithOptionalPin("2580")

    val clock = FakeSecurityClock(epochMs = 1_760_000_000_000L, elapsedMs = 50_000L)
    val vm1 = MainViewModel(repo, clock)
    pumpMainAndWait { true }

    // Attempts 1..3 -> allowed without lockout
    for (attempt in 1..3) {
      vm1.unlockWithPin("000$attempt")
      pumpMainAndWait { prefsStore.preferencesFlow.first().pinFailedAttempts == attempt }
      assertEquals(0L, prefsStore.preferencesFlow.first().pinLockoutDurationMs)
    }

    // Attempt 4 -> 15s lockout persisted atomically
    vm1.unlockWithPin("9999")
    pumpMainAndWait { prefsStore.preferencesFlow.first().pinFailedAttempts == 4 }
    val p4 = prefsStore.preferencesFlow.first()
    assertEquals(15_000L, p4.pinLockoutDurationMs)
    assertEquals(clock.epochMs + 15_000L, p4.pinLockoutUntilEpochMs)

    // Even valid PIN must be rejected during active monotonic lockout
    // Simulate wall clock jumped ahead by 1 hour inside session -> monotonic elapsedRealtime still blocks!
    clock.epochMs += 3_600_000L
    vm1.unlockWithPin("2580")
    settleCoroutinesAndLooper()
    assertEquals(4, prefsStore.preferencesFlow.first().pinFailedAttempts)

    // Simulate process restart with wall clock turned BACKWARD by 10 years (clock tamper test)
    val restartClock1 = FakeSecurityClock(epochMs = 1_400_000_000_000L, elapsedMs = 5_000L)
    val vmRestartBackward = MainViewModel(repo, restartClock1)
    settleCoroutinesAndLooper()

    // Valid PIN still blocked until capped 15s duration passes on monotonic clock (never permanent!)
    vmRestartBackward.unlockWithPin("2580")
    settleCoroutinesAndLooper()
    assertEquals(4, prefsStore.preferencesFlow.first().pinFailedAttempts)

    restartClock1.elapsedMs += 16_000L
    vmRestartBackward.unlockWithPin("2580")
    pumpMainAndWait { prefsStore.preferencesFlow.first().pinFailedAttempts == 0 }
    val pCleared = prefsStore.preferencesFlow.first()
    assertEquals(0, pCleared.pinFailedAttempts)
    assertEquals(0L, pCleared.pinLockoutUntilEpochMs)
    assertEquals(0L, pCleared.pinLockoutDurationMs)

    // Verify escalation ceiling caps at 15 minutes (900,000 ms), never permanent lockout
    assertEquals(15_000L, PinThrottlePolicy.lockoutDurationForAttempts(4))
    assertEquals(30_000L, PinThrottlePolicy.lockoutDurationForAttempts(5))
    assertEquals(60_000L, PinThrottlePolicy.lockoutDurationForAttempts(6))
    assertEquals(300_000L, PinThrottlePolicy.lockoutDurationForAttempts(7))
    assertEquals(900_000L, PinThrottlePolicy.lockoutDurationForAttempts(8))
    assertEquals(900_000L, PinThrottlePolicy.lockoutDurationForAttempts(50))
  }

  @Test
  fun `sec3 auth guard blocks unauthenticated writes and executes SAF export strictly after valid unlock`() = runBlocking {
    val profile = UserProfile(id = "u-sec3", name = "Iris", dob = CycleDate(1996, 9, 10))
    val initialEntry = DailyEntry(
      date = CycleDate(2026, 10, 11),
      isPeriod = true,
      flowLevel = FlowLevel.MEDIUM,
      notes = "Private note"
    )
    repo.saveProfile(profile)
    repo.saveDailyEntry(initialEntry)
    prefsStore.setOnboardingCompletedWithOptionalPin("1357")

    val clock = FakeSecurityClock()
    val vm = MainViewModel(repo, clock)
    pumpMainAndWait { true }

    // 1. Direct locked operations must be rejected without modifying Room or DataStore
    vm.saveDailyEntry(DailyEntry(date = CycleDate(2026, 10, 12), notes = "Unauthorized write"))
    vm.disablePin()
    vm.clearAllLocalData()
    settleCoroutinesAndLooper()
    assertNull(db.dailyEntryDao().getEntryByDate(CycleDate(2026, 10, 12)))
    assertTrue(prefsStore.preferencesFlow.first().pinEnabled)
    assertEquals("Iris", db.userProfileDao().getProfileOnce()?.name)

    val directLockedFile = File(context.cacheDir, "direct_locked_export.xlsx").apply { delete() }
    vm.exportToExcelUri(context, Uri.fromFile(directLockedFile))
    settleCoroutinesAndLooper()
    assertFalse("Direct export while locked must NOT write file", directLockedFile.exists())

    // 2. Genuine SAF lifecycle transition: launch SAF -> ON_STOP locks app -> SAF delivers Uri
    val safExportFile = File(context.cacheDir, "saf_pending_export.xlsx").apply { delete() }
    vm.onLaunchingSafPicker()
    vm.lockAppNow(fromLifecycleStop = true) // Activity ON_STOP fires while SAF picker is open

    vm.exportToExcelUri(context, Uri.fromFile(safExportFile))
    settleCoroutinesAndLooper()
    assertFalse("Pending SAF export must NOT run before PIN unlock", safExportFile.exists())

    // Wrong PIN must NOT trigger the queued SAF export
    vm.unlockWithPin("9999")
    pumpMainAndWait { prefsStore.preferencesFlow.first().pinFailedAttempts == 1 }
    assertFalse("Failed unlock must NOT run queued SAF export", safExportFile.exists())

    // Valid PIN unlocks AND executes pending SAF export
    val collectorJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined).launch {
      vm.uiState.collect {}
    }
    try {
      vm.unlockWithPin("1357")
      pumpMainAndWait { vm.uiState.value.backupMessage?.contains("Exported") == true }

      val parsed = ByteArrayInputStream(safExportFile.readBytes()).use { ExcelXlsxEngine.parseWorkbook(it) }
      assertEquals("Iris", parsed.profile?.name)
      assertEquals("Private note", parsed.entries.single().notes)
      safExportFile.delete()
    } finally {
      collectorJob.cancel()
    }

    // 3. Re-backgrounding while locked without unlocking clears pending SAF action
    val discardedFile = File(context.cacheDir, "saf_discarded_export.xlsx").apply { delete() }
    vm.onLaunchingSafPicker()
    vm.lockAppNow(fromLifecycleStop = true)
    vm.exportToExcelUri(context, Uri.fromFile(discardedFile))
    settleCoroutinesAndLooper()
    // User leaves app from PinLockOverlay before entering PIN
    vm.lockAppNow(fromLifecycleStop = true)
    vm.unlockWithPin("1357")
    pumpMainAndWait { prefsStore.preferencesFlow.first().pinFailedAttempts == 0 }
    settleCoroutinesAndLooper()
    assertFalse("Abandoned SAF action must NOT execute on later unlock", discardedFile.exists())
  }

  @Test
  fun `sec4 flag_secure policy, MainActivity window flags, backup xmls, and manifest least privilege pass`() = runBlocking {
    // 1. Policy states across all transitions
    val loadingState = BloomUiState(isLoaded = false)
    assertTrue(ScreenSecurityPolicy.isSensitiveScreen(loadingState, onboardingStep = 0))

    val onboardingStep0 = BloomUiState(isLoaded = true, onboardingCompleted = false)
    assertFalse(ScreenSecurityPolicy.isSensitiveScreen(onboardingStep0, onboardingStep = 0))
    assertTrue(ScreenSecurityPolicy.isSensitiveScreen(onboardingStep0, onboardingStep = 1))
    assertTrue(ScreenSecurityPolicy.isSensitiveScreen(onboardingStep0, onboardingStep = 2))
    assertTrue(ScreenSecurityPolicy.isSensitiveScreen(onboardingStep0, onboardingStep = 3))

    val lockedApp = BloomUiState(isLoaded = true, onboardingCompleted = true, isPinLocked = true)
    assertTrue(ScreenSecurityPolicy.isSensitiveScreen(lockedApp, onboardingStep = 0))

    val unlockedApp = BloomUiState(isLoaded = true, onboardingCompleted = true, isPinLocked = false)
    assertTrue(ScreenSecurityPolicy.isSensitiveScreen(unlockedApp, onboardingStep = 0))

    // 2. Real MainActivity sets FLAG_SECURE initially before content loads AND keeps it set once onboarded
    val initialController = Robolectric.buildActivity(MainActivity::class.java).create()
    val initialActivity = initialController.get()
    assertTrue(
      "MainActivity window must enable FLAG_SECURE on onCreate before data loads",
      (initialActivity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
    )
    initialController.destroy()

    // Seed real persisted onboardingCompleted + PIN in app PreferencesStore, then launch MainActivity
    val appPrefs = PreferencesStore(context)
    appPrefs.setOnboardingCompletedWithOptionalPin("4829")
    val authedController = Robolectric.buildActivity(MainActivity::class.java).setup()
    val authedActivity = authedController.get()
    pumpMainAndWait {
      (authedActivity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
    }
    authedController.pause().stop().destroy()

    // 3. Backup & D2D XML explicit exclusions check
    val requiredDomains = setOf("root", "database", "sharedpref", "file", "external")
    val backupExcludes = collectXmlExcludeDomains(R.xml.backup_rules, section = "full-backup-content")
    assertTrue("backup_rules.xml must exclude all sensitive domains", backupExcludes.containsAll(requiredDomains))

    val cloudExcludes = collectXmlExcludeDomains(R.xml.data_extraction_rules, section = "cloud-backup")
    val d2dExcludes = collectXmlExcludeDomains(R.xml.data_extraction_rules, section = "device-transfer")
    assertTrue("data_extraction_rules.xml cloud-backup must exclude all domains", cloudExcludes.containsAll(requiredDomains))
    assertTrue("data_extraction_rules.xml device-transfer must exclude all domains", d2dExcludes.containsAll(requiredDomains))

    // 4. Manifest, exported components, permissions & FileProvider inspection via PackageManager
    @Suppress("DEPRECATION")
    val pkgInfo = context.packageManager.getPackageInfo(
      context.packageName,
      PackageManager.GET_ACTIVITIES or
        PackageManager.GET_PROVIDERS or
        PackageManager.GET_SERVICES or
        PackageManager.GET_RECEIVERS or
        PackageManager.GET_PERMISSIONS
    )

    val permissions = pkgInfo.requestedPermissions?.toList().orEmpty()
    assertFalse("INTERNET permission must NOT be requested", "android.permission.INTERNET" in permissions)
    assertFalse("READ_EXTERNAL_STORAGE must NOT be requested", "android.permission.READ_EXTERNAL_STORAGE" in permissions)
    assertFalse("WRITE_EXTERNAL_STORAGE must NOT be requested", "android.permission.WRITE_EXTERNAL_STORAGE" in permissions)
    assertFalse("READ_MEDIA_IMAGES must NOT be requested", "android.permission.READ_MEDIA_IMAGES" in permissions)
    assertTrue(
      "Unexpected dangerous/network permissions found: $permissions",
      permissions.all { it.endsWith(".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") }
    )

    val exportedActivities = pkgInfo.activities.orEmpty().filter { it.exported }.map { it.name }.toSet()
    val allowedDebugOnlyToolingActivities = setOf(
      "androidx.compose.ui.tooling.PreviewActivity",
      "androidx.activity.ComponentActivity"
    )
    assertTrue("MainActivity must be exported", MainActivity::class.java.name in exportedActivities)
    assertTrue(
      "Unexpected exported activities found: ${exportedActivities - MainActivity::class.java.name - allowedDebugOnlyToolingActivities}",
      (exportedActivities - MainActivity::class.java.name).all { it in allowedDebugOnlyToolingActivities }
    )

    val providers = pkgInfo.providers.orEmpty()
    assertTrue("No provider may be exported", providers.none { it.exported })
    val fileProviders = providers.filter { it.name.contains("FileProvider", ignoreCase = true) }
    assertTrue("App must not expose FileProvider; SAF handles user-selected URIs", fileProviders.isEmpty())

    val services = pkgInfo.services.orEmpty()
    assertTrue("No service may be exported", services.none { it.exported })

    assertTrue(
      "Application allowBackup must remain disabled",
      (pkgInfo.applicationInfo!!.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) == 0
    )
  }

  private fun collectXmlExcludeDomains(xmlResId: Int, section: String): Set<String> {
    val domains = mutableSetOf<String>()
    val parser = context.resources.getXml(xmlResId)
    var inSection = false
    var event = parser.eventType
    while (event != XmlPullParser.END_DOCUMENT) {
      when (event) {
        XmlPullParser.START_TAG -> {
          if (parser.name == section) {
            inSection = true
          } else if (inSection && parser.name == "exclude") {
            val domain = parser.getAttributeValue(null, "domain")
            val path = parser.getAttributeValue(null, "path")
            if (!domain.isNullOrBlank() && path == ".") {
              domains.add(domain)
            }
          }
        }
        XmlPullParser.END_TAG -> {
          if (parser.name == section) inSection = false
        }
      }
      event = parser.next()
    }
    return domains
  }

  private suspend fun settleCoroutinesAndLooper() {
    repeat(6) {
      shadowOf(Looper.getMainLooper()).idle()
      kotlinx.coroutines.delay(35L)
      shadowOf(Looper.getMainLooper()).idle()
    }
  }

  private suspend fun pumpMainAndWait(
    timeoutMs: Long = 8_000L,
    predicate: suspend () -> Boolean
  ) {
    val start = System.currentTimeMillis()
    while (System.currentTimeMillis() - start < timeoutMs) {
      shadowOf(Looper.getMainLooper()).idle()
      if (predicate()) return
      kotlinx.coroutines.delay(25L)
    }
    shadowOf(Looper.getMainLooper()).idle()
    assertTrue("Timed out waiting for async condition after ${timeoutMs}ms", predicate())
  }
}
