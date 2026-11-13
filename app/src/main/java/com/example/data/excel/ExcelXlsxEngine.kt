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
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

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
            e.sleepDurationHours?.toString() ?: "",
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
        add(
          listOf(
            c.cycleIndex.toString(),
            c.startDate.toIsoString(),
            c.endDate.toIsoString(),
            c.periodDurationDays.toString(),
            c.cycleLengthDays?.toString() ?: "Ongoing",
            String.format(java.util.Locale.US, "%.2f", c.averageCrampScore),
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
      snapshot.averageCycleLength?.let {
        add(listOf("Metric", "Average Cycle Length", "${String.format(java.util.Locale.US, "%.1f", it)} days"))
      }
      snapshot.medianCycleLength?.let {
        add(listOf("Metric", "Median Cycle Length", "${String.format(java.util.Locale.US, "%.1f", it)} days"))
      }
      snapshot.averagePeriodDuration?.let {
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
    ZipInputStream(inputStream).use { zip ->
      var entry = zip.nextEntry
      while (entry != null) {
        if (!entry.isDirectory) {
          val buffer = ByteArrayOutputStream()
          zip.copyTo(buffer)
          files[entry.name] = buffer.toByteArray()
        }
        zip.closeEntry()
        entry = zip.nextEntry
      }
    }

    if (files.isEmpty()) {
      throw IllegalArgumentException("Selected file is not a valid .xlsx workbook.")
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
        value.split(";", ",").map { it.trim() }.filter { it.isNotEmpty() }.distinct()

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
        sleepDurationHours = row.col("SleepHours", 13).toFloatOrNull(),
        sleepQuality = row.col("SleepQuality_1_5", 14).toIntOrNull()?.coerceIn(1, 5),
        sleepTimingNote = row.col("SleepTiming", 15),
        energyLevel = row.col("Energy_0_5", 16).toIntOrNull()?.coerceIn(0, 5),
        stressLevel = row.col("Stress_0_5", 17).toIntOrNull()?.coerceIn(0, 5),
        stressTriggers = row.col("StressTriggers", 18),
        activityLevel = ActivityLevel.fromNameOrNull(row.col("ActivityLevel", 19)) ?: ActivityLevel.NONE,
        exerciseMinutes = row.col("ExerciseMinutes", 20).toIntOrNull(),
        waterCups = row.col("WaterCups", 21).toIntOrNull(),
        caffeineCups = row.col("CaffeineCups", 22).toIntOrNull(),
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
        if (el.tagName.endsWith("Relationship")) {
          val id = el.getAttribute("Id")
          val target = el.getAttribute("Target")
          if (id.isNotBlank() && target.isNotBlank()) {
            val normalized = if (target.startsWith("/")) target.drop(1) else "xl/$target"
            relMap[id] = normalized.replace("xl/xl/", "xl/")
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

  private fun parseSharedStrings(bytes: ByteArray): List<String> {
    val strings = mutableListOf<String>()
    val doc = parseXmlDocument(bytes)
    val siNodes = doc.getElementsByTagName("*")
    for (i in 0 until siNodes.length) {
      val el = siNodes.item(i) as? Element ?: continue
      if (el.tagName.substringAfterLast(':') == "si") {
        val sb = StringBuilder()
        val children = el.getElementsByTagName("*")
        for (j in 0 until children.length) {
          val child = children.item(j) as? Element ?: continue
          if (child.tagName.substringAfterLast(':') == "t") {
            sb.append(child.textContent.orEmpty())
          }
        }
        strings.add(sb.toString())
      }
    }
    return strings
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

        val rawText = buildString {
          val descendants = cellEl.getElementsByTagName("*")
          for (k in 0 until descendants.length) {
            val d = descendants.item(k) as? Element ?: continue
            val tag = d.tagName.substringAfterLast(':')
            if (tag == "v" || tag == "t") {
              append(d.textContent.orEmpty())
            }
          }
        }

        val value = if (cellType == "s") {
          val idx = rawText.trim().toIntOrNull()
          if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else ""
        } else {
          rawText
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
    val factory = DocumentBuilderFactory.newInstance().apply {
      isNamespaceAware = false
    }
    return factory.newDocumentBuilder().parse(bytes.inputStream())
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
        val numVal = value.toDoubleOrNull()
        if (rowIdx > 0 && numVal != null && !value.startsWith("0") || (rowIdx > 0 && value == "0")) {
          sb.append("""<c r="$cellRef"$styleAttr><v>$value</v></c>""")
        } else {
          val escaped = xmlEscape(value)
          sb.append("""<c r="$cellRef" t="inlineStr"$styleAttr><is><t xml:space="preserve">$escaped</t></is></c>""")
        }
      }
      sb.append("</row>")
    }
    sb.append("</sheetData></worksheet>")
    return sb.toString()
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
