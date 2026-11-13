package com.example.data.excel

import com.example.domain.model.ActivityLevel
import com.example.domain.model.AnalyticsSnapshot
import com.example.domain.model.CustomOption
import com.example.domain.model.CustomOptionType
import com.example.domain.model.DailyEntry
import com.example.domain.model.DefaultCatalog
import com.example.domain.model.FlowLevel
import com.example.domain.model.MoodSwingLevel
import com.example.domain.model.RelationshipStatus
import com.example.domain.model.TimingSlot
import com.example.domain.model.UserProfile
import com.example.util.CycleDate
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.SAXException

data class ParsedWorkbookPreview(
  val profile: UserProfile?,
  val entries: List<DailyEntry>,
  val discoveredCustomOptions: List<CustomOption>,
  val earliestDate: CycleDate?,
  val latestDate: CycleDate?
)

/**
 * Pure-Kotlin OOXML (.xlsx) workbook exporter and importer.
 * Produces standard multi-sheet .xlsx archives compatible with Excel, Google Sheets, and LibreOffice,
 * and parses both inlineStr/str and sharedStrings.xml workbooks during restore.
 */
object ExcelXlsxEngine {

  internal const val MAX_COMPRESSED_BYTES: Long = 10L * 1024 * 1024 // 10 MB
  internal const val MAX_TOTAL_UNCOMPRESSED_BYTES: Long = 20L * 1024 * 1024 // 20 MB
  internal const val MAX_SINGLE_ENTRY_BYTES: Long = 8L * 1024 * 1024 // 8 MB
  internal const val MAX_ZIP_ENTRIES: Int = 250

  private val canonicalNonNegativeNumberRegex = Regex("""(?:0|[1-9]\d*)(?:\.\d+)?""")

  private val dailyHeaders = listOf(
    "Date",
    "IsPeriod",
    "Flow",
    "Spotting",
    "PeriodStartOverride",
    "PeriodEndOverride",
    "FlowChangesNote",
    "CrampScore_0_5",
    "PainTiming",
    "Moods",
    "MoodSwings",
    "Symptoms",
    "SymptomTiming",
    "SleepHours",
    "SleepQuality_1_5",
    "SleepTiming",
    "Energy_0_5",
    "Stress_0_5",
    "StressTriggers",
    "ActivityLevel",
    "ExerciseMinutes",
    "WaterCups",
    "CaffeineCups",
    "MedicationTaken",
    "MedicationNote",
    "CustomTags",
    "Notes"
  )

  fun exportWorkbook(
    outputStream: OutputStream,
    profile: UserProfile?,
    entries: List<DailyEntry>,
    snapshot: AnalyticsSnapshot
  ) {
    val profileRows = buildList {
      add(listOf("Field", "Value"))
      if (profile != null) {
        add(listOf("ProfileId", profile.id))
        add(listOf("Name", profile.name))
        add(listOf("DateOfBirth", profile.dob.toIsoString()))
        add(listOf("CalculatedAge", profile.ageOn().toString()))
        add(listOf("RelationshipStatus", profile.relationshipStatus.name))
        add(listOf("CustomRelationshipLabel", profile.customRelationshipLabel))
        add(listOf("DefaultCycleLength", profile.defaultCycleLength.toString()))
      }
      add(listOf("ExportedAt", CycleDate.today().toIsoString()))
      add(listOf("App", "BloomCycle (Local-Only Export)"))
    }

    val entryRows = buildList {
      add(dailyHeaders)
      entries.sortedBy { it.date }.forEach { e ->
        val safeSleep = e.sleepDurationHours?.takeIf { it.isFinite() }?.toString() ?: ""
        add(
          listOf(
            e.date.toIsoString(),
            if (e.isPeriod) "YES" else "NO",
            e.flowLevel.name,
            if (e.spotting) "YES" else "NO",
            if (e.isPeriodStartOverride) "YES" else "NO",
            if (e.isPeriodEndOverride) "YES" else "NO",
            e.flowChangesNote,
            e.crampScore.toString(),
            e.painTiming.name,
            e.moods.joinToString("; "),
            e.moodSwingLevel.name,
            e.symptoms.joinToString("; "),
            e.symptomTiming.name,
            safeSleep,
            e.sleepQuality?.toString() ?: "",
            e.sleepTimingNote,
            e.energyLevel?.toString() ?: "",
            e.stressLevel?.toString() ?: "",
            e.stressTriggers,
            e.activityLevel.name,
            e.exerciseMinutes?.toString() ?: "",
            e.waterCups?.toString() ?: "",
            e.caffeineCups?.toString() ?: "",
            if (e.medicationTaken) "YES" else "NO",
            e.medicationNote,
            e.customTags.joinToString("; "),
            e.notes
          )
        )
      }
    }

    val cycleRows = buildList {
      add(
        listOf(
          "CycleNumber",
          "StartDate",
          "PeriodEndDate",
          "PeriodDurationDays",
          "CycleLengthDays",
          "AverageCrampScore",
          "PeakCrampScore",
          "TotalSymptomLogs",
          "DominantMood",
          "AgeAtCycleStart"
        )
      )
      snapshot.cycles.forEach { c ->
        val avgCrampFormatted = c.averageCrampScore
          .takeIf { it.isFinite() }
          ?.let { String.format(java.util.Locale.US, "%.2f", it) }
          ?: "0.00"
        add(
          listOf(
            c.cycleIndex.toString(),
            c.startDate.toIsoString(),
            c.endDate.toIsoString(),
            c.periodDurationDays.toString(),
            c.cycleLengthDays?.toString() ?: "Ongoing",
            avgCrampFormatted,
            c.maxCrampScore.toString(),
            c.totalSymptomLogs.toString(),
            c.dominantMood ?: "",
            c.userAgeAtStart.toString()
          )
        )
      }
    }

    val insightRows = buildList {
      add(listOf("Category", "Title", "Observation"))
      snapshot.averageCycleLength?.takeIf { it.isFinite() }?.let {
        add(listOf("Metric", "Average Cycle Length", "${String.format(java.util.Locale.US, "%.1f", it)} days"))
      }
      snapshot.medianCycleLength?.takeIf { it.isFinite() }?.let {
        add(listOf("Metric", "Median Cycle Length", "${String.format(java.util.Locale.US, "%.1f", it)} days"))
      }
      snapshot.averagePeriodDuration?.takeIf { it.isFinite() }?.let {
        add(listOf("Metric", "Average Period Duration", "${String.format(java.util.Locale.US, "%.1f", it)} days"))
      }
      snapshot.prediction?.let {
        add(listOf("Prediction (Estimated)", "Estimated Next Period", "${it.nextPeriodWindowStart.toIsoString()} to ${it.nextPeriodWindowEnd.toIsoString()}"))
      }
      snapshot.personalInsights.forEach { card ->
        add(listOf(card.category, card.title, card.statement))
      }
      snapshot.crossFactorInsights.forEach { cf ->
        add(listOf(cf.category, cf.title, cf.summary))
      }
      snapshot.anomalies.forEach { a ->
        add(listOf(a.severityLabel, a.title, a.detail))
      }
    }

    val sheets = listOf(
      "Daily_Entries" to entryRows,
      "Profile" to profileRows,
      "Cycles" to cycleRows,
      "Insights_Summary" to insightRows
    )

    ZipOutputStream(outputStream).use { zip ->
      writeZipEntry(zip, "[Content_Types].xml", buildContentTypesXml(sheets.size))
      writeZipEntry(zip, "_rels/.rels", rootRelsXml())
      writeZipEntry(zip, "xl/workbook.xml", buildWorkbookXml(sheets.map { it.first }))
      writeZipEntry(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(sheets.size))
      writeZipEntry(zip, "xl/styles.xml", stylesXml())
      sheets.forEachIndexed { idx, (_, rows) ->
        writeZipEntry(zip, "xl/worksheets/sheet${idx + 1}.xml", buildWorksheetXml(rows))
      }
    }
  }

  fun parseWorkbook(inputStream: InputStream): ParsedWorkbookPreview {
    val files = mutableMapOf<String, ByteArray>()
    val boundedInput = BoundedInputStream(inputStream, MAX_COMPRESSED_BYTES)

    try {
      ZipInputStream(boundedInput).use { zip ->
        var entryCount = 0
        var totalUncompressed = 0L
        val chunk = ByteArray(8192)
        var entry: ZipEntry? = zip.nextEntry
        while (entry != null) {
          entryCount++
          if (entryCount > MAX_ZIP_ENTRIES) {
            throw IllegalArgumentException("Workbook contains too many archive entries (max $MAX_ZIP_ENTRIES).")
          }
          val normalizedPath = normalizeZipEntryPath(entry.name)
          if (!entry.isDirectory) {
            if (entry.size > MAX_SINGLE_ENTRY_BYTES) {
              throw IllegalArgumentException("Workbook entry '$normalizedPath' exceeds maximum allowed size.")
            }
            if (files.containsKey(normalizedPath)) {
              throw IllegalArgumentException("Malformed workbook: duplicate archive entry '$normalizedPath'.")
            }
            val buffer = ByteArrayOutputStream()
            var entryBytes = 0L
            while (true) {
              val read = zip.read(chunk)
              if (read <= 0) break
              entryBytes += read
              if (entryBytes > MAX_SINGLE_ENTRY_BYTES) {
                throw IllegalArgumentException("Workbook entry '$normalizedPath' exceeds maximum allowed size.")
              }
              totalUncompressed += read
              if (totalUncompressed > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                throw IllegalArgumentException("Workbook exceeds maximum allowed uncompressed size.")
              }
              buffer.write(chunk, 0, read)
            }
            files[normalizedPath] = buffer.toByteArray()
          }
          zip.closeEntry()
          entry = zip.nextEntry
        }
      }
    } catch (e: ZipException) {
      throw IllegalArgumentException("Selected file is not a valid .xlsx workbook.", e)
    } catch (e: EOFException) {
      throw IllegalArgumentException("Selected .xlsx workbook appears truncated or corrupted.", e)
    }

    if (files.isEmpty()) {
      throw IllegalArgumentException("Selected file is not a valid .xlsx workbook.")
    }
    if (!files.containsKey("xl/workbook.xml")) {
      throw IllegalArgumentException("Malformed .xlsx workbook: missing xl/workbook.xml.")
    }

    val sharedStrings = files["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()
    val sheetTargetMap = parseWorkbookSheetTargets(files["xl/workbook.xml"], files["xl/_rels/workbook.xml.rels"])

    val dailySheetPath = sheetTargetMap["Daily_Entries"]
      ?: sheetTargetMap.entries.firstOrNull { it.key.contains("daily", ignoreCase = true) }?.value
      ?: sheetTargetMap.values.firstOrNull()
      ?: "xl/worksheets/sheet1.xml"

    val profileSheetPath = sheetTargetMap["Profile"]
      ?: sheetTargetMap.entries.firstOrNull { it.key.contains("profile", ignoreCase = true) }?.value

    val dailyBytes = files[dailySheetPath]
      ?: throw IllegalArgumentException("Could not find Daily_Entries worksheet inside the .xlsx file.")

    val dailyRows = parseWorksheetRows(dailyBytes, sharedStrings)
    val parsedEntries = parseDailyEntryRows(dailyRows)

    val parsedProfile = profileSheetPath?.let { path ->
      files[path]?.let { bytes -> parseProfileRows(parseWorksheetRows(bytes, sharedStrings)) }
    }

    val discoveredOptions = mutableListOf<CustomOption>()
    val defaultMoodsSet = DefaultCatalog.defaultMoods.map { it.lowercase() }.toSet()
    val defaultSymSet = DefaultCatalog.defaultSymptoms.map { it.lowercase() }.toSet()
    parsedEntries.flatMap { it.moods }.distinct().forEach { m ->
      if (m.lowercase() !in defaultMoodsSet) {
        discoveredOptions.add(CustomOption(type = CustomOptionType.MOOD, label = m))
      }
    }
    parsedEntries.flatMap { it.symptoms }.distinct().forEach { s ->
      if (s.lowercase() !in defaultSymSet) {
        discoveredOptions.add(CustomOption(type = CustomOptionType.SYMPTOM, label = s))
      }
    }
    parsedEntries.flatMap { it.customTags }.distinct().forEach { t ->
      discoveredOptions.add(CustomOption(type = CustomOptionType.TAG, label = t))
    }

    return ParsedWorkbookPreview(
      profile = parsedProfile,
      entries = parsedEntries,
      discoveredCustomOptions = discoveredOptions,
      earliestDate = parsedEntries.minOfOrNull { it.date },
      latestDate = parsedEntries.maxOfOrNull { it.date }
    )
  }

  private class BoundedInputStream(
    delegate: InputStream,
    private val maxBytes: Long
  ) : FilterInputStream(delegate) {
    private var bytesRead = 0L

    override fun read(): Int {
      val b = super.read()
      if (b != -1) {
        bytesRead++
        if (bytesRead > maxBytes) {
          throw IllegalArgumentException("Workbook archive exceeds maximum allowed compressed size.")
        }
      }
      return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
      val count = super.read(b, off, len)
      if (count > 0) {
        bytesRead += count
        if (bytesRead > maxBytes) {
          throw IllegalArgumentException("Workbook archive exceeds maximum allowed compressed size.")
        }
      }
      return count
    }
  }

  private fun normalizeZipEntryPath(rawName: String): String {
    if (rawName.isBlank() || rawName.indexOf('\u0000') >= 0 || rawName.contains('\\') || rawName.contains(':')) {
      throw IllegalArgumentException("Unsafe archive entry path.")
    }
    if (rawName.startsWith("/")) {
      throw IllegalArgumentException("Absolute archive entry path is not allowed.")
    }
    val segments = rawName.split('/').filter { it.isNotEmpty() && it != "." }
    if (segments.isEmpty() || segments.any { it == ".." }) {
      throw IllegalArgumentException("Invalid archive entry path traversal: '$rawName'.")
    }
    return segments.joinToString("/")
  }

  private fun parseDailyEntryRows(rows: List<List<String>>): List<DailyEntry> {
    if (rows.size <= 1) return emptyList()
    val header = rows.first().map { it.trim() }
    val colIdx = header.withIndex().associate { (i, name) -> name.lowercase() to i }

    fun List<String>.col(name: String, fallbackIdx: Int): String {
      val idx = colIdx[name.lowercase()] ?: fallbackIdx
      return getOrNull(idx)?.trim().orEmpty()
    }

    return rows.drop(1).mapNotNull { row ->
      val dateStr = row.col("Date", 0)
      val date = CycleDate.parseOrNull(dateStr) ?: return@mapNotNull null

      fun yes(name: String, fallbackIdx: Int): Boolean {
        val v = row.col(name, fallbackIdx)
        return v.equals("YES", true) || v.equals("TRUE", true) || v == "1"
      }

      fun splitMulti(value: String): List<String> =
        value.split(";").map { it.trim() }.filter { it.isNotEmpty() }.distinct()

      val rawSleep = row.col("SleepHours", 13)
      val parsedSleep = if (rawSleep.isBlank()) {
        null
      } else {
        val f = rawSleep.toFloatOrNull()
        if (f != null && !f.isFinite()) {
          throw IllegalArgumentException("Invalid non-finite SleepHours value: '$rawSleep'")
        }
        f?.coerceIn(0f, 24f)
      }

      DailyEntry(
        date = date,
        isPeriod = yes("IsPeriod", 1),
        flowLevel = FlowLevel.fromNameOrNull(row.col("Flow", 2)) ?: FlowLevel.NONE,
        spotting = yes("Spotting", 3),
        isPeriodStartOverride = yes("PeriodStartOverride", 4),
        isPeriodEndOverride = yes("PeriodEndOverride", 5),
        flowChangesNote = row.col("FlowChangesNote", 6),
        crampScore = row.col("CrampScore_0_5", 7).toIntOrNull()?.coerceIn(0, 5) ?: 0,
        painTiming = TimingSlot.fromNameOrNull(row.col("PainTiming", 8)) ?: TimingSlot.ANY,
        moods = splitMulti(row.col("Moods", 9)),
        moodSwingLevel = MoodSwingLevel.fromNameOrNull(row.col("MoodSwings", 10)) ?: MoodSwingLevel.NONE,
        symptoms = splitMulti(row.col("Symptoms", 11)),
        symptomTiming = TimingSlot.fromNameOrNull(row.col("SymptomTiming", 12)) ?: TimingSlot.ANY,
        sleepDurationHours = parsedSleep,
        sleepQuality = row.col("SleepQuality_1_5", 14).toIntOrNull()?.coerceIn(1, 5),
        sleepTimingNote = row.col("SleepTiming", 15),
        energyLevel = row.col("Energy_0_5", 16).toIntOrNull()?.coerceIn(0, 5),
        stressLevel = row.col("Stress_0_5", 17).toIntOrNull()?.coerceIn(0, 5),
        stressTriggers = row.col("StressTriggers", 18),
        activityLevel = ActivityLevel.fromNameOrNull(row.col("ActivityLevel", 19)) ?: ActivityLevel.NONE,
        exerciseMinutes = row.col("ExerciseMinutes", 20).toIntOrNull()?.coerceIn(0, 1440),
        waterCups = row.col("WaterCups", 21).toIntOrNull()?.coerceIn(0, 100),
        caffeineCups = row.col("CaffeineCups", 22).toIntOrNull()?.coerceIn(0, 100),
        medicationTaken = yes("MedicationTaken", 23),
        medicationNote = row.col("MedicationNote", 24),
        customTags = splitMulti(row.col("CustomTags", 25)),
        notes = row.col("Notes", 26)
      )
    }
  }

  private fun parseProfileRows(rows: List<List<String>>): UserProfile? {
    if (rows.size <= 1) return null
    val kv = mutableMapOf<String, String>()
    rows.drop(1).forEach { row ->
      val k = row.getOrNull(0)?.trim().orEmpty()
      val v = row.getOrNull(1)?.trim().orEmpty()
      if (k.isNotEmpty()) kv[k] = v
    }
    val name = kv["Name"]?.takeIf { it.isNotBlank() } ?: return null
    val dob = CycleDate.parseOrNull(kv["DateOfBirth"]) ?: CycleDate(1998, 1, 1)
    return UserProfile(
      id = kv["ProfileId"]?.takeIf { it.isNotBlank() } ?: "local_user",
      name = name,
      dob = dob,
      relationshipStatus = RelationshipStatus.fromNameOrNull(kv["RelationshipStatus"])
        ?: RelationshipStatus.PREFER_NOT_TO_SAY,
      customRelationshipLabel = kv["CustomRelationshipLabel"].orEmpty(),
      defaultCycleLength = kv["DefaultCycleLength"]?.toIntOrNull()?.coerceIn(20, 45) ?: 28
    )
  }

  private fun parseWorkbookSheetTargets(
    workbookBytes: ByteArray?,
    relsBytes: ByteArray?
  ): Map<String, String> {
    if (workbookBytes == null) return emptyMap()
    val relMap = mutableMapOf<String, String>()
    if (relsBytes != null) {
      val doc = parseXmlDocument(relsBytes)
      val relNodes = doc.getElementsByTagName("*")
      for (i in 0 until relNodes.length) {
        val el = relNodes.item(i) as? Element ?: continue
        if (el.tagName.substringAfterLast(':') == "Relationship") {
          val mode = el.getAttribute("TargetMode")
          if (mode.equals("External", ignoreCase = true)) {
            throw IllegalArgumentException("External workbook relationships are not allowed.")
          }
          val id = el.getAttribute("Id")
          val target = el.getAttribute("Target").trim()
          if (id.isNotBlank() && target.isNotBlank()) {
            val resolved = resolveWorkbookTarget(target)
            relMap[id] = resolved
          }
        }
      }
    }

    val result = mutableMapOf<String, String>()
    val doc = parseXmlDocument(workbookBytes)
    val sheetNodes = doc.getElementsByTagName("*")
    var sheetIdx = 1
    for (i in 0 until sheetNodes.length) {
      val el = sheetNodes.item(i) as? Element ?: continue
      if (el.tagName.substringAfterLast(':') == "sheet") {
        val name = el.getAttribute("name").ifBlank { "Sheet$sheetIdx" }
        var rId: String? = null
        val attrs = el.attributes
        for (a in 0 until attrs.length) {
          val attr = attrs.item(a)
          if (attr.nodeName.substringAfterLast(':') == "id") {
            rId = attr.nodeValue
            break
          }
        }
        val target = rId?.let { relMap[it] } ?: "xl/worksheets/sheet$sheetIdx.xml"
        result[name] = target
        sheetIdx++
      }
    }
    return result
  }

  private fun resolveWorkbookTarget(target: String): String {
    if (target.indexOf('\u0000') >= 0 || target.contains('\\') || target.contains(':')) {
      throw IllegalArgumentException("Unsafe relationship target path: '$target'.")
    }
    val raw = if (target.startsWith("/")) {
      target.drop(1)
    } else if (target.startsWith("xl/")) {
      target
    } else {
      "xl/$target"
    }
    val segments = mutableListOf<String>()
    for (seg in raw.split('/')) {
      when (seg) {
        "", "." -> Unit
        ".." -> throw IllegalArgumentException("Path traversal in relationship target: '$target'.")
        else -> segments.add(seg)
      }
    }
    if (segments.isEmpty()) {
      throw IllegalArgumentException("Empty relationship target path.")
    }
    return segments.joinToString("/")
  }

  private fun parseSharedStrings(bytes: ByteArray): List<String> {
    val strings = mutableListOf<String>()
    val doc = parseXmlDocument(bytes)
    val siNodes = doc.getElementsByTagName("*")
    for (i in 0 until siNodes.length) {
      val el = siNodes.item(i) as? Element ?: continue
      if (el.tagName.substringAfterLast(':') == "si") {
        strings.add(extractRichTextContent(el))
      }
    }
    return strings
  }

  /**
   * Extracts text from an OOXML <si> or <is> container, supporting both plain <t>
   * and rich-text <r><t> runs while ignoring phonetic guide <rPh> elements.
   */
  private fun extractRichTextContent(container: Element): String {
    val sb = StringBuilder()
    val children = container.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i) as? Element ?: continue
      when (child.tagName.substringAfterLast(':')) {
        "t" -> sb.append(child.textContent.orEmpty())
        "r" -> {
          val runChildren = child.childNodes
          for (j in 0 until runChildren.length) {
            val rc = runChildren.item(j) as? Element ?: continue
            if (rc.tagName.substringAfterLast(':') == "t") {
              sb.append(rc.textContent.orEmpty())
            }
          }
        }
      }
    }
    return sb.toString()
  }

  private fun parseWorksheetRows(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val doc = parseXmlDocument(bytes)
    val allNodes = doc.getElementsByTagName("*")
    for (i in 0 until allNodes.length) {
      val rowEl = allNodes.item(i) as? Element ?: continue
      if (rowEl.tagName.substringAfterLast(':') != "row") continue

      val currentRow = mutableMapOf<Int, String>()
      var currentCol = 0
      val cellNodes = rowEl.childNodes
      for (j in 0 until cellNodes.length) {
        val cellEl = cellNodes.item(j) as? Element ?: continue
        if (cellEl.tagName.substringAfterLast(':') != "c") continue

        val cellType = cellEl.getAttribute("t")
        val ref = cellEl.getAttribute("r")
        if (ref.isNotBlank()) {
          currentCol = cellRefToColIndex(ref)
        }

        val value = when (cellType) {
          "s" -> {
            val idxText = extractDirectValueText(cellEl).trim()
            val idx = idxText.toIntOrNull()
            if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
          }
          "inlineStr" -> {
            val isEl = findDirectChild(cellEl, "is")
            if (isEl != null) extractRichTextContent(isEl) else extractDirectValueText(cellEl)
          }
          else -> extractDirectValueText(cellEl)
        }
        currentRow[currentCol] = value
        currentCol++
      }

      val maxCol = currentRow.keys.maxOrNull() ?: -1
      if (maxCol >= 0) {
        rows.add((0..maxCol).map { currentRow[it].orEmpty() })
      }
    }
    return rows
  }

  private fun findDirectChild(parent: Element, localName: String): Element? {
    val children = parent.childNodes
    for (i in 0 until children.length) {
      val el = children.item(i) as? Element ?: continue
      if (el.tagName.substringAfterLast(':') == localName) return el
    }
    return null
  }

  private fun extractDirectValueText(cellEl: Element): String {
    val sb = StringBuilder()
    val children = cellEl.childNodes
    for (i in 0 until children.length) {
      val child = children.item(i) as? Element ?: continue
      val tag = child.tagName.substringAfterLast(':')
      if (tag == "v" || tag == "t") {
        sb.append(child.textContent.orEmpty())
      }
    }
    return sb.toString()
  }

  private fun cellRefToColIndex(ref: String): Int {
    var col = 0
    for (ch in ref) {
      if (ch in 'A'..'Z') {
        col = col * 26 + (ch - 'A' + 1)
      } else if (ch in 'a'..'z') {
        col = col * 26 + (ch - 'a' + 1)
      } else break
    }
    return (col - 1).coerceAtLeast(0)
  }

  private fun parseXmlDocument(bytes: ByteArray): Document {
    val factory = try {
      DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = false
        isXIncludeAware = false
        isExpandEntityReferences = false
        setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        setFeature("http://xml.org/sax/features/external-general-entities", false)
        setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
      }
    } catch (e: ParserConfigurationException) {
      throw SecurityException("Required XML security features are unsupported by the XML parser.", e)
    } catch (e: IllegalArgumentException) {
      throw SecurityException("Required XML security configuration is unsupported by the XML parser.", e)
    }

    return try {
      val builder = factory.newDocumentBuilder()
      builder.setEntityResolver { _, _ ->
        throw SAXException("External XML entities are disallowed.")
      }
      builder.parse(bytes.inputStream())
    } catch (e: SAXException) {
      throw IllegalArgumentException("Malformed or disallowed XML content in workbook.", e)
    }
  }

  private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
    zip.putNextEntry(ZipEntry(name))
    zip.write(content.toByteArray(Charsets.UTF_8))
    zip.closeEntry()
  }

  private fun buildWorksheetXml(rows: List<List<String>>): String {
    val sb = StringBuilder()
    sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
    sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
    sb.append("<sheetData>")
    rows.forEachIndexed { rowIdx, cells ->
      val rNum = rowIdx + 1
      sb.append("""<row r="$rNum">""")
      cells.forEachIndexed { colIdx, value ->
        val cellRef = "${colIndexToLetters(colIdx)}$rNum"
        val styleAttr = if (rowIdx == 0) """ s="1"""" else ""
        val numVal = if (rowIdx > 0 && canonicalNonNegativeNumberRegex.matches(value)) {
          value.toDoubleOrNull()?.takeIf { it.isFinite() }
        } else {
          null
        }
        if (numVal != null) {
          sb.append("""<c r="$cellRef"$styleAttr><v>$value</v></c>""")
        } else {
          val safeText = if (rowIdx > 0) neutralizeFormulaInjection(value) else value
          val escaped = xmlEscape(safeText)
          sb.append("""<c r="$cellRef" t="inlineStr"$styleAttr><is><t xml:space="preserve">$escaped</t></is></c>""")
        }
      }
      sb.append("</row>")
    }
    sb.append("</sheetData></worksheet>")
    return sb.toString()
  }

  internal fun neutralizeFormulaInjection(value: String): String {
    if (value.isEmpty()) return value
    val first = value.first()
    val trimmedFirst = value.trimStart().firstOrNull()
    val isFormulaPrefix =
      first == '=' || first == '+' || first == '-' || first == '@' ||
        first == '\t' || first == '\r' ||
        trimmedFirst == '=' || trimmedFirst == '+' || trimmedFirst == '-' || trimmedFirst == '@'
    return if (isFormulaPrefix) "'$value" else value
  }

  private fun colIndexToLetters(index: Int): String {
    var n = index
    val sb = StringBuilder()
    while (n >= 0) {
      sb.append(('A' + (n % 26)))
      n = (n / 26) - 1
    }
    return sb.reverse().toString()
  }

  private fun xmlEscape(text: String): String =
    buildString(text.length) {
      for (ch in text) {
        when (ch) {
          '&' -> append("&amp;")
          '<' -> append("&lt;")
          '>' -> append("&gt;")
          '"' -> append("&quot;")
          '\'' -> append("&apos;")
          else -> if (ch.code >= 0x20 || ch == '\n' || ch == '\r' || ch == '\t') append(ch)
        }
      }
    }

  private fun buildContentTypesXml(sheetCount: Int): String = buildString {
    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
    append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
    append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
    append("""<Default Extension="xml" ContentType="application/xml"/>""")
    append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
    append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
    for (i in 1..sheetCount) {
      append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
    }
    append("</Types>")
  }

  private fun rootRelsXml(): String =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
      """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
      """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
      """</Relationships>"""

  private fun buildWorkbookXml(sheetNames: List<String>): String = buildString {
    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
    append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
    append("<sheets>")
    sheetNames.forEachIndexed { idx, name ->
      val safeName = xmlEscape(name.take(31))
      append("""<sheet name="$safeName" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>""")
    }
    append("</sheets></workbook>")
  }

  private fun buildWorkbookRelsXml(sheetCount: Int): String = buildString {
    append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
    append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
    for (i in 1..sheetCount) {
      append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
    }
    append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
    append("</Relationships>")
  }

  private fun stylesXml(): String =
    """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
      """<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""" +
      """<fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><color rgb="FF9E3B36"/><name val="Calibri"/></font></fonts>""" +
      """<fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>""" +
      """<borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>""" +
      """<cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>""" +
      """<cellXfs count="2"><xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/><xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0"/></cellXfs>""" +
      """</styleSheet>"""
}
