package com.example

import com.example.data.excel.ExcelXlsxEngine
import com.example.domain.analytics.CycleAnalyticsEngine
import com.example.domain.model.ActivityLevel
import com.example.domain.model.DailyEntry
import com.example.domain.model.FlowLevel
import com.example.domain.model.MoodSwingLevel
import com.example.domain.model.RelationshipStatus
import com.example.domain.model.UserProfile
import com.example.util.CycleDate
import com.example.util.PinSecurity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BloomCycleEngineTest {

  @Test
  fun `cycle date arithmetic handles leap years and age calculation`() {
    val leapDay = CycleDate(2024, 2, 29)
    assertEquals(CycleDate(2024, 3, 1), leapDay.plusDays(1))
    assertEquals(29, leapDay.daysUntil(CycleDate(2024, 3, 29)))

    val dob = CycleDate(2000, 11, 20)
    assertEquals(25, dob.ageOn(CycleDate(2026, 11, 12)))
    assertEquals(26, dob.ageOn(CycleDate(2026, 11, 20)))
  }

  @Test
  fun `pin hashing and verification works with salt`() {
    val hash1 = PinSecurity.hashPin("4829")
    val hash2 = PinSecurity.hashPin("4829")
    assertTrue(hash1.startsWith("v2\$pbkdf2-hmac-sha256\$"))
    assertFalse(hash1 == hash2) // Unique 16-byte random salt per derivation
    assertTrue(PinSecurity.verifyPin("4829", hash1))
    assertFalse(PinSecurity.verifyPin("0000", hash1))
  }

  @Test
  fun `analytics engine derives cycles, predictions, cross-factors, and anomalies`() {
    val profile = UserProfile(
      id = "u1",
      name = "Maya",
      dob = CycleDate(1998, 4, 10),
      relationshipStatus = RelationshipStatus.IN_RELATIONSHIP,
      defaultCycleLength = 28
    )

    val entries = mutableListOf<DailyEntry>()
    // Cycle 1: Jan 1..5 (28 days)
    for (d in 0..4) {
      entries.add(
        DailyEntry(
          date = CycleDate(2026, 1, 1).plusDays(d),
          isPeriod = true,
          flowLevel = FlowLevel.MEDIUM,
          crampScore = if (d == 0) 4 else 2,
          moods = listOf("Tired", "Calm"),
          symptoms = listOf("Bloating"),
          sleepDurationHours = 5.5f,
          stressLevel = 4,
          activityLevel = ActivityLevel.NONE
        )
      )
    }
    // Cycle 2: Jan 29..Feb 2 (28 days)
    for (d in 0..4) {
      entries.add(
        DailyEntry(
          date = CycleDate(2026, 1, 29).plusDays(d),
          isPeriod = true,
          flowLevel = FlowLevel.HEAVY,
          crampScore = if (d == 0) 3 else 1,
          moods = listOf("Calm"),
          symptoms = listOf("Bloating", "Headache"),
          sleepDurationHours = 7.5f,
          stressLevel = 1,
          activityLevel = ActivityLevel.MODERATE
        )
      )
    }
    // Cycle 3 (longer anomaly): Feb 26..Mar 2 -> next start Apr 5 (38 days)
    for (d in 0..4) {
      entries.add(
        DailyEntry(
          date = CycleDate(2026, 2, 26).plusDays(d),
          isPeriod = true,
          flowLevel = FlowLevel.MEDIUM,
          crampScore = 1,
          moods = listOf("Happy"),
          moodSwingLevel = MoodSwingLevel.MILD,
          sleepDurationHours = 8.0f,
          stressLevel = 1,
          activityLevel = ActivityLevel.LIGHT
        )
      )
    }
    // Cycle 4: Apr 5..9
    for (d in 0..4) {
      entries.add(
        DailyEntry(
          date = CycleDate(2026, 4, 5).plusDays(d),
          isPeriod = true,
          flowLevel = FlowLevel.MEDIUM,
          crampScore = 4,
          moods = listOf("Anxious"),
          moodSwingLevel = MoodSwingLevel.MODERATE,
          sleepDurationHours = 5.0f,
          stressLevel = 4,
          activityLevel = ActivityLevel.NONE
        )
      )
    }

    val snapshot = CycleAnalyticsEngine.buildAnalyticsSnapshot(
      profile = profile,
      entries = entries,
      showFertileWindow = true,
      today = CycleDate(2026, 4, 15)
    )

    assertEquals(4, snapshot.totalCyclesCount)
    assertEquals(3, snapshot.completedCyclesCount)
    assertNotNull(snapshot.averageCycleLength)
    assertNotNull(snapshot.prediction)
    assertNotNull(snapshot.prediction?.fertileWindowStart)
    assertTrue(snapshot.crossFactorInsights.isNotEmpty())
    assertTrue(snapshot.anomalies.isNotEmpty())
    assertTrue(snapshot.ageTrends.isNotEmpty())
  }

  @Test
  fun `excel export and import performs lossless round-trip`() {
    val profile = UserProfile(
      id = "local-1",
      name = "Aria",
      dob = CycleDate(1997, 8, 22),
      relationshipStatus = RelationshipStatus.COMPLICATED,
      defaultCycleLength = 29
    )
    val entries = listOf(
      DailyEntry(
        date = CycleDate(2026, 10, 1),
        isPeriod = true,
        flowLevel = FlowLevel.HEAVY,
        spotting = false,
        crampScore = 4,
        moods = listOf("Calm", "Tired"),
        moodSwingLevel = MoodSwingLevel.MILD,
        symptoms = listOf("Bloating", "Back pain"),
        sleepDurationHours = 7.5f,
        energyLevel = 2,
        stressLevel = 3,
        activityLevel = ActivityLevel.LIGHT,
        exerciseMinutes = 25,
        waterCups = 6,
        caffeineCups = 1,
        medicationTaken = true,
        medicationNote = "Warm compress & ibuprofen",
        customTags = listOf("travel"),
        notes = "Heavy morning & gentle walk."
      )
    )
    val snapshot = CycleAnalyticsEngine.buildAnalyticsSnapshot(profile, entries, true, CycleDate(2026, 10, 5))

    val out = ByteArrayOutputStream()
    ExcelXlsxEngine.exportWorkbook(out, profile, entries, snapshot)
    val parsed = ExcelXlsxEngine.parseWorkbook(ByteArrayInputStream(out.toByteArray()))

    assertNotNull(parsed.profile)
    assertEquals("Aria", parsed.profile?.name)
    assertEquals(RelationshipStatus.COMPLICATED, parsed.profile?.relationshipStatus)
    assertEquals(1, parsed.entries.size)
    val roundTripped = parsed.entries.first()
    assertEquals(CycleDate(2026, 10, 1), roundTripped.date)
    assertTrue(roundTripped.isPeriod)
    assertEquals(FlowLevel.HEAVY, roundTripped.flowLevel)
    assertEquals(4, roundTripped.crampScore)
    assertEquals(listOf("Calm", "Tired"), roundTripped.moods)
    assertEquals(listOf("Bloating", "Back pain"), roundTripped.symptoms)
    assertEquals(7.5f, roundTripped.sleepDurationHours)
    assertEquals("Heavy morning & gentle walk.", roundTripped.notes)
  }

  @Test
  fun `excel import preserves comma-containing custom labels across moods symptoms and tags`() {
    val profile = UserProfile(
      id = "local-comma",
      name = "Nora",
      dob = CycleDate(1995, 5, 14)
    )
    val entries = listOf(
      DailyEntry(
        date = CycleDate(2026, 10, 2),
        moods = listOf("Low, drained", "Calm, focused"),
        symptoms = listOf("Pain, lower back", "Bloating, mild"),
        customTags = listOf("work, deadline", "travel, flight")
      )
    )
    val snapshot = CycleAnalyticsEngine.buildAnalyticsSnapshot(profile, entries, true, CycleDate(2026, 10, 5))

    val out = ByteArrayOutputStream()
    ExcelXlsxEngine.exportWorkbook(out, profile, entries, snapshot)
    val parsed = ExcelXlsxEngine.parseWorkbook(ByteArrayInputStream(out.toByteArray()))

    val restored = parsed.entries.single()
    assertEquals(listOf("Low, drained", "Calm, focused"), restored.moods)
    assertEquals(listOf("Pain, lower back", "Bloating, mild"), restored.symptoms)
    assertEquals(listOf("work, deadline", "travel, flight"), restored.customTags)
  }

  @Test
  fun `excel export and import preserve 0_5 decimals and reject non-finite numeric values`() {
    val profile = UserProfile(
      id = "local-dec",
      name = "Lena",
      dob = CycleDate(1996, 6, 6)
    )
    val entries = listOf(
      DailyEntry(
        date = CycleDate(2026, 10, 3),
        sleepDurationHours = 0.5f,
        crampScore = 2
      ),
      DailyEntry(
        date = CycleDate(2026, 10, 4),
        sleepDurationHours = Float.NaN,
        crampScore = 1
      )
    )
    val snapshot = CycleAnalyticsEngine.buildAnalyticsSnapshot(profile, entries, true, CycleDate(2026, 10, 5))

    val out = ByteArrayOutputStream()
    ExcelXlsxEngine.exportWorkbook(out, profile, entries, snapshot)

    val sheet1Xml = readZipEntryText(out.toByteArray(), "xl/worksheets/sheet1.xml")
    assertTrue("Expected numeric <v>0.5</v> in exported XML", sheet1Xml.contains("<v>0.5</v>"))
    assertFalse("Non-finite Float.NaN must never be serialized as <v>NaN</v>", sheet1Xml.contains("<v>NaN</v>"))

    val parsed = ExcelXlsxEngine.parseWorkbook(ByteArrayInputStream(out.toByteArray()))
    assertEquals(0.5f, parsed.entries.first { it.date == CycleDate(2026, 10, 3) }.sleepDurationHours)

    // Explicit non-finite SleepHours value in imported sheet must be rejected
    val nanWorkbook = buildZipArchive(
      mapOf(
        "xl/workbook.xml" to minimalWorkbookXml(),
        "xl/_rels/workbook.xml.rels" to minimalWorkbookRelsXml(),
        "xl/worksheets/sheet1.xml" to """
          <?xml version="1.0" encoding="UTF-8"?>
          <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <sheetData>
              <row r="1">
                <c r="A1" t="inlineStr"><is><t>Date</t></is></c>
                <c r="N1" t="inlineStr"><is><t>SleepHours</t></is></c>
              </row>
              <row r="2">
                <c r="A2" t="inlineStr"><is><t>2026-10-03</t></is></c>
                <c r="N2"><v>NaN</v></c>
              </row>
            </sheetData>
          </worksheet>
        """.trimIndent().toByteArray()
      )
    )
    assertThrows(IllegalArgumentException::class.java) {
      ExcelXlsxEngine.parseWorkbook(ByteArrayInputStream(nanWorkbook))
    }
  }

  @Test
  fun `excel import parses sharedStrings rich-text runs while ignoring phonetic guide tags`() {
    val xlsx = buildZipArchive(
      mapOf(
        "xl/workbook.xml" to minimalWorkbookXml(),
        "xl/_rels/workbook.xml.rels" to minimalWorkbookRelsXml(),
        "xl/sharedStrings.xml" to """
          <?xml version="1.0" encoding="UTF-8"?>
          <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="4" uniqueCount="4">
            <si><t>Date</t></si>
            <si><t>Notes</t></si>
            <si><t>2026-10-08</t></si>
            <si>
              <r><t>Morning </t></r>
              <r><rPr><b/></rPr><t>headache</t></r>
              <rPh sb="0" eb="4"><t>PHONETIC_IGNORE</t></rPh>
            </si>
          </sst>
        """.trimIndent().toByteArray(),
        "xl/worksheets/sheet1.xml" to """
          <?xml version="1.0" encoding="UTF-8"?>
          <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
            <sheetData>
              <row r="1">
                <c r="A1" t="s"><v>0</v></c>
                <c r="B1" t="s"><v>1</v></c>
              </row>
              <row r="2">
                <c r="A2" t="s"><v>2</v></c>
                <c r="B2" t="s"><v>3</v></c>
              </row>
            </sheetData>
          </worksheet>
        """.trimIndent().toByteArray()
      )
    )

    val parsed = ExcelXlsxEngine.parseWorkbook(ByteArrayInputStream(xlsx))
    assertEquals(1, parsed.entries.size)
    assertEquals("Morning headache", parsed.entries.single().notes)
  }

  @Test
  fun `excel export neutralizes formula injection prefixes`() {
    val profile = UserProfile(
      id = "local-inj",
      name = "Tara",
      dob = CycleDate(1998, 2, 10)
    )
    val entries = listOf(
      DailyEntry(
        date = CycleDate(2026, 10, 9),
        flowChangesNote = "=1+1",
        stressTriggers = "+cmd|' /C calc'!A0",
        medicationNote = "-2+3",
        notes = "@SUM(1,1)",
        sleepTimingNote = "\t=IMPORTXML(\"http://example.com\")"
      )
    )
    val snapshot = CycleAnalyticsEngine.buildAnalyticsSnapshot(profile, entries, true, CycleDate(2026, 10, 10))

    val out = ByteArrayOutputStream()
    ExcelXlsxEngine.exportWorkbook(out, profile, entries, snapshot)

    val sheet1Xml = readZipEntryText(out.toByteArray(), "xl/worksheets/sheet1.xml")
    assertTrue(sheet1Xml.contains("&apos;=1+1"))
    assertTrue(sheet1Xml.contains("&apos;+cmd|&apos; /C calc&apos;!A0"))
    assertTrue(sheet1Xml.contains("&apos;-2+3"))
    assertTrue(sheet1Xml.contains("&apos;@SUM(1,1)"))
    assertTrue(sheet1Xml.contains("&apos;\t=IMPORTXML(&quot;http://example.com&quot;)"))
  }

  @Test
  fun `excel import rejects XML XXE DOCTYPE payloads`() {
    val xxeXml = """
      <?xml version="1.0" encoding="UTF-8"?>
      <!DOCTYPE sst [
        <!ENTITY xxe SYSTEM "file:///etc/passwd">
      ]>
      <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
        <si><t>&xxe;</t></si>
      </sst>
    """.trimIndent().toByteArray()

    val xlsx = buildZipArchive(
      mapOf(
        "xl/workbook.xml" to minimalWorkbookXml(),
        "xl/_rels/workbook.xml.rels" to minimalWorkbookRelsXml(),
        "xl/sharedStrings.xml" to xxeXml,
        "xl/worksheets/sheet1.xml" to """
          <?xml version="1.0" encoding="UTF-8"?>
          <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData/></worksheet>
        """.trimIndent().toByteArray()
      )
    )

    assertThrows(IllegalArgumentException::class.java) {
      ExcelXlsxEngine.parseWorkbook(ByteArrayInputStream(xlsx))
    }
  }

  @Test
  fun `excel import rejects zip bomb excessive entry count oversized compressed stream and path traversal`() {
    // 1. Decompressed ZIP bomb exceeding MAX_SINGLE_ENTRY_BYTES (8 MB) / MAX_TOTAL_UNCOMPRESSED_BYTES (20 MB)
    val nineMbZeros = ByteArray(9 * 1024 * 1024)
    val hugeEntryArchive = buildZipArchive(
      mapOf(
        "xl/workbook.xml" to minimalWorkbookXml(),
        "xl/worksheets/sheet1.xml" to nineMbZeros
      )
    )
    assertThrows(IllegalArgumentException::class.java) {
      ExcelXlsxEngine.parseWorkbook(ByteArrayInputStream(hugeEntryArchive))
    }

    // 2. Entry count exceeding MAX_ZIP_ENTRIES (250)
    val manyEntries = linkedMapOf<String, ByteArray>()
    manyEntries["xl/workbook.xml"] = minimalWorkbookXml()
    for (i in 1..255) {
      manyEntries["xl/extra/file_$i.xml"] = "<x/>".toByteArray()
    }
    val manyEntriesArchive = buildZipArchive(manyEntries)
    assertThrows(IllegalArgumentException::class.java) {
      ExcelXlsxEngine.parseWorkbook(ByteArrayInputStream(manyEntriesArchive))
    }

    // 3. Compressed input stream exceeding MAX_COMPRESSED_BYTES (10 MB)
    val oversizedStream = ByteArray(ExcelXlsxEngine.MAX_COMPRESSED_BYTES.toInt() + 1024)
    assertThrows(IllegalArgumentException::class.java) {
      ExcelXlsxEngine.parseWorkbook(ByteArrayInputStream(oversizedStream))
    }

    // 4. Zip Slip path traversal
    val traversalArchive = buildZipArchive(
      mapOf(
        "../../evil.xml" to "<evil/>".toByteArray(),
        "xl/workbook.xml" to minimalWorkbookXml()
      )
    )
    assertThrows(IllegalArgumentException::class.java) {
      ExcelXlsxEngine.parseWorkbook(ByteArrayInputStream(traversalArchive))
    }

    // 5. External TargetMode relationship
    val externalRelArchive = buildZipArchive(
      mapOf(
        "xl/workbook.xml" to minimalWorkbookXml(),
        "xl/_rels/workbook.xml.rels" to """
          <?xml version="1.0" encoding="UTF-8"?>
          <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
            <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="https://evil.example/sheet.xml" TargetMode="External"/>
          </Relationships>
        """.trimIndent().toByteArray(),
        "xl/worksheets/sheet1.xml" to "<worksheet/>".toByteArray()
      )
    )
    assertThrows(IllegalArgumentException::class.java) {
      ExcelXlsxEngine.parseWorkbook(ByteArrayInputStream(externalRelArchive))
    }
  }

  @Test
  fun `excel import rejects malformed non-zip empty-zip and missing-workbook archives`() {
    // Plain non-ZIP bytes
    assertThrows(IllegalArgumentException::class.java) {
      ExcelXlsxEngine.parseWorkbook(ByteArrayInputStream("not an xlsx archive".toByteArray()))
    }

    // Empty ZIP archive
    val emptyZip = buildZipArchive(emptyMap())
    assertThrows(IllegalArgumentException::class.java) {
      ExcelXlsxEngine.parseWorkbook(ByteArrayInputStream(emptyZip))
    }

    // ZIP missing xl/workbook.xml
    val noWorkbookZip = buildZipArchive(
      mapOf("xl/worksheets/sheet1.xml" to "<worksheet><sheetData/></worksheet>".toByteArray())
    )
    assertThrows(IllegalArgumentException::class.java) {
      ExcelXlsxEngine.parseWorkbook(ByteArrayInputStream(noWorkbookZip))
    }
  }

  private fun minimalWorkbookXml(): ByteArray = """
    <?xml version="1.0" encoding="UTF-8"?>
    <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
      <sheets>
        <sheet name="Daily_Entries" sheetId="1" r:id="rId1"/>
      </sheets>
    </workbook>
  """.trimIndent().toByteArray()

  private fun minimalWorkbookRelsXml(): ByteArray = """
    <?xml version="1.0" encoding="UTF-8"?>
    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
      <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
    </Relationships>
  """.trimIndent().toByteArray()

  private fun buildZipArchive(entries: Map<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
      entries.forEach { (name, data) ->
        zip.putNextEntry(ZipEntry(name))
        zip.write(data)
        zip.closeEntry()
      }
    }
    return out.toByteArray()
  }

  private fun readZipEntryText(zipBytes: ByteArray, entryName: String): String {
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
      var entry = zip.nextEntry
      while (entry != null) {
        if (entry.name == entryName) {
          return zip.readBytes().toString(Charsets.UTF_8)
        }
        entry = zip.nextEntry
      }
    }
    error("Missing zip entry: $entryName")
  }
}
