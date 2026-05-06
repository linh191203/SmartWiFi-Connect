package com.example.smartwificonnect.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.sqrt

class WifiOcrProcessor {
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun recognizeText(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()
        return result.text.trim()
    }

    suspend fun recognizeWifiText(bitmap: Bitmap): WifiOcrScanResult {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()
        val rawText = result.text.trim()
        val layoutCredentials = extractFromTextLayout(result)
        val textCredentials = extractWifiCredentials(rawText)
        val bestCredentials = mergeCredentialCandidates(layoutCredentials, textCredentials)
        return WifiOcrScanResult(
            text = rawText,
            credentials = bestCredentials,
        )
    }

    fun assessImageQuality(bitmap: Bitmap): OcrImageQuality {
        if (bitmap.width < 240 || bitmap.height < 160) {
            return OcrImageQuality(
                isReadable = false,
                message = "Khong quet duoc: anh qua nho. Vui long chup lai ro hon hoac nhap thu cong.",
            )
        }

        val stepX = maxOf(1, bitmap.width / 96)
        val stepY = maxOf(1, bitmap.height / 96)
        val sampleXs = (0 until bitmap.width step stepX).toList()
        val previousRow = DoubleArray(sampleXs.size) { Double.NaN }

        var count = 0
        var brightCount = 0
        var darkCount = 0
        var sum = 0.0
        var sumSquares = 0.0
        var edgeTotal = 0.0
        var edgeCount = 0

        for (y in 0 until bitmap.height step stepY) {
            var previousInRow = Double.NaN
            sampleXs.forEachIndexed { index, x ->
                val pixel = bitmap.getPixel(x, y)
                val luminance = 0.299 * Color.red(pixel) +
                    0.587 * Color.green(pixel) +
                    0.114 * Color.blue(pixel)

                count += 1
                sum += luminance
                sumSquares += luminance * luminance
                if (luminance > 245.0) brightCount += 1
                if (luminance < 12.0) darkCount += 1

                if (!previousInRow.isNaN()) {
                    edgeTotal += abs(luminance - previousInRow)
                    edgeCount += 1
                }
                if (!previousRow[index].isNaN()) {
                    edgeTotal += abs(luminance - previousRow[index])
                    edgeCount += 1
                }
                previousInRow = luminance
                previousRow[index] = luminance
            }
        }

        if (count == 0 || edgeCount == 0) {
            return OcrImageQuality(
                isReadable = false,
                message = "Khong quet duoc: anh khong du ro. Vui long nhap thu cong.",
            )
        }

        val mean = sum / count
        val variance = (sumSquares / count) - mean * mean
        val contrast = sqrt(maxOf(0.0, variance))
        val edgeMean = edgeTotal / edgeCount
        val brightRatio = brightCount.toDouble() / count
        val darkRatio = darkCount.toDouble() / count

        return when {
            (brightRatio > 0.86 && edgeMean < 5.0) || mean > 252.0 -> OcrImageQuality(
                isReadable = false,
                message = "Khong quet duoc: anh bi chay sang. Vui long chup lai hoac nhap thu cong.",
            )
            (darkRatio > 0.82 && edgeMean < 5.0) || mean < 8.0 -> OcrImageQuality(
                isReadable = false,
                message = "Khong quet duoc: anh qua toi. Vui long chup lai hoac nhap thu cong.",
            )
            contrast < 14.0 && edgeMean < 5.0 -> OcrImageQuality(
                isReadable = false,
                message = "Khong quet duoc: anh qua mo/nhieu nen khong du tin cay. Vui long nhap thu cong.",
            )
            edgeMean < 2.8 -> OcrImageQuality(
                isReadable = false,
                message = "Khong quet duoc: chu trong anh khong du net. Vui long chup lai hoac nhap thu cong.",
            )
            else -> OcrImageQuality(isReadable = true)
        }
    }

    fun extractWifiCredentials(text: String): WifiOcrCredentials {
        val raw = text.trim()
        if (raw.isBlank()) return WifiOcrCredentials()

        val qrCredentials = parseWifiQrPayload(raw)
        if (qrCredentials.hasAnyValue()) return qrCredentials

        val lines = normalizeLines(raw)
        val labeled = extractFromLabeledLines(lines)
        val twoLine = extractTwoLineSsidPassword(lines)
        val heuristic = extractHeuristic(lines)

        return mergeCredentialCandidates(labeled, twoLine, heuristic)
    }

    private fun extractFromTextLayout(result: Text): WifiOcrCredentials {
        val lines = result.textBlocks
            .flatMap { block -> block.lines }
            .mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val cleaned = cleanLine(line.text)
                if (cleaned.isBlank()) return@mapNotNull null
                OcrTextLine(text = cleaned, box = box)
            }
            .sortedWith(compareBy<OcrTextLine> { it.box.top }.thenBy { it.box.left })

        if (lines.isEmpty()) return WifiOcrCredentials()
        val imageCenterX = lines.map { it.centerX }.average().toFloat()

        var ssid = ""
        var password = ""

        lines.forEachIndexed { index, line ->
            if (ssid.isBlank() && isSsidLabelLine(line.text)) {
                ssid = extractLooseLabelValue(line.text, forPassword = false)
                    ?: findLayoutValue(lines, index, forPassword = false)
                    .orEmpty()
            }
            if (password.isBlank() && isPasswordLabelLine(line.text)) {
                password = extractLooseLabelValue(line.text, forPassword = true)
                    ?: findLayoutValue(lines, index, forPassword = true)
                    .orEmpty()
            }
        }

        if (ssid.isBlank()) {
            ssid = lines
                .mapNotNull { line ->
                    scoreLayoutSsidCandidate(
                        line = line,
                        allLines = lines,
                        imageCenterX = imageCenterX,
                    )?.let { score -> line.text to score }
                }
                .maxByOrNull { it.second }
                ?.takeIf { it.second >= 3.0 }
                ?.first
                .orEmpty()
        }

        if (password.isBlank()) {
            password = lines
                .mapNotNull { line ->
                    scoreLayoutPasswordCandidate(
                        line = line,
                        allLines = lines,
                        imageCenterX = imageCenterX,
                    )?.let { score -> line.text to score }
                }
                .maxByOrNull { it.second }
                ?.takeIf { it.second >= 3.0 }
                ?.first
                .orEmpty()
        }

        ssid = sanitizeCandidate(stripSsidPrefix(ssid))
        password = sanitizeCandidate(stripPasswordPrefix(password))
        if (ssid.isBlank() && password.isBlank()) return WifiOcrCredentials()

        return WifiOcrCredentials(
            ssid = ssid,
            password = password,
            sourceFormat = "layout_ocr",
            confidence = when {
                ssid.isNotBlank() && password.isNotBlank() -> 0.92
                ssid.isNotBlank() || password.isNotBlank() -> 0.78
                else -> 0.0
            },
        )
    }

    private fun scoreLayoutSsidCandidate(
        line: OcrTextLine,
        allLines: List<OcrTextLine>,
        imageCenterX: Float,
    ): Double? {
        val base = scoreSsidCandidate(line.text)?.score ?: return null
        var score = base
        score += nearestRelevantLabelBonus(
            target = line,
            labels = allLines.filter { isSsidLabelLine(it.text) },
        )
        score -= nearestRelevantLabelPenalty(
            target = line,
            labels = allLines.filter { isPasswordLabelLine(it.text) },
        )
        if (kotlin.math.abs(line.centerX - imageCenterX) <= line.box.width()) {
            score += 0.35
        }
        if (line.text.count(Char::isLetter) >= 6 && line.text.contains(' ')) {
            score += 0.35
        }
        return score
    }

    private fun scoreLayoutPasswordCandidate(
        line: OcrTextLine,
        allLines: List<OcrTextLine>,
        imageCenterX: Float,
    ): Double? {
        val base = scorePasswordCandidate(line.text)?.score ?: return null
        var score = base
        score += nearestRelevantLabelBonus(
            target = line,
            labels = allLines.filter { isPasswordLabelLine(it.text) },
        )
        score -= nearestRelevantLabelPenalty(
            target = line,
            labels = allLines.filter { isSsidLabelLine(it.text) },
        )
        if (!line.text.contains(' ') && kotlin.math.abs(line.centerX - imageCenterX) <= line.box.width()) {
            score += 0.25
        }
        return score
    }

    private fun nearestRelevantLabelBonus(
        target: OcrTextLine,
        labels: List<OcrTextLine>,
    ): Double {
        val nearest = labels.minByOrNull { label ->
            kotlin.math.abs(target.centerY - label.centerY) + kotlin.math.abs(target.centerX - label.centerX)
        } ?: return 0.0

        return when {
            rowsOverlap(nearest.box, target.box) && target.box.left >= nearest.box.right - nearest.height -> 3.2
            target.box.top >= nearest.box.bottom - nearest.height / 2 &&
                target.box.top <= nearest.box.bottom + nearest.height * 5 &&
                columnsOverlap(nearest.box, target.box) -> 2.7
            else -> 0.0
        }
    }

    private fun nearestRelevantLabelPenalty(
        target: OcrTextLine,
        labels: List<OcrTextLine>,
    ): Double {
        val nearest = labels.minByOrNull { label ->
            kotlin.math.abs(target.centerY - label.centerY) + kotlin.math.abs(target.centerX - label.centerX)
        } ?: return 0.0

        return when {
            rowsOverlap(nearest.box, target.box) && target.box.left >= nearest.box.right - nearest.height -> 2.2
            target.box.top >= nearest.box.bottom - nearest.height / 2 &&
                target.box.top <= nearest.box.bottom + nearest.height * 4 &&
                columnsOverlap(nearest.box, target.box) -> 1.8
            else -> 0.0
        }
    }

    private fun findLayoutValue(
        lines: List<OcrTextLine>,
        labelIndex: Int,
        forPassword: Boolean,
    ): String? {
        val label = lines[labelIndex]
        val sameRow = lines
            .asSequence()
            .filterIndexed { index, candidate -> index != labelIndex && isUsefulLayoutValue(candidate.text, forPassword) }
            .filter { candidate ->
                candidate.box.left >= label.box.right - label.height &&
                    rowsOverlap(label.box, candidate.box)
            }
            .minByOrNull { candidate ->
                abs(candidate.centerY - label.centerY) + maxOf(0, candidate.box.left - label.box.right)
            }
            ?.text

        if (!sameRow.isNullOrBlank()) {
            return normalizeLayoutValue(sameRow, forPassword)
        }

        return lines
            .asSequence()
            .filterIndexed { index, candidate -> index != labelIndex && isUsefulLayoutValue(candidate.text, forPassword) }
            .filter { candidate ->
                candidate.box.top >= label.box.bottom - label.height / 2 &&
                    candidate.box.top <= label.box.bottom + label.height * 5 &&
                    columnsOverlap(label.box, candidate.box)
            }
            .minByOrNull { candidate ->
                abs(candidate.centerX - label.centerX) + maxOf(0, candidate.box.top - label.box.bottom)
            }
            ?.text
            ?.let { normalizeLayoutValue(it, forPassword) }
    }

    private fun normalizeLayoutValue(value: String, forPassword: Boolean): String {
        return if (forPassword) {
            stripPasswordPrefix(value).ifBlank { value }
        } else {
            stripSsidPrefix(value).ifBlank { value }
        }.let(::sanitizeCandidate)
    }

    private fun isUsefulLayoutValue(value: String, forPassword: Boolean): Boolean {
        val cleaned = sanitizeCandidate(value)
        if (cleaned.isBlank()) return false
        if (ssidLabelOnlyRegex.matches(cleaned) || passwordLabelOnlyRegex.matches(cleaned)) return false
        if (isLikelyNoiseLine(cleaned)) return false
        return if (forPassword) {
            !isSsidLabelLine(cleaned) && (looksLikePassword(cleaned) || cleaned.length in 4..63)
        } else {
            isPlausibleSsidCandidate(cleaned) && !looksLikePassword(cleaned)
        }
    }

    private fun rowsOverlap(first: Rect, second: Rect): Boolean {
        val overlap = minOf(first.bottom, second.bottom) - maxOf(first.top, second.top)
        return overlap > minOf(first.height(), second.height()) * 0.35 ||
            abs(first.centerY() - second.centerY()) <= maxOf(first.height(), second.height())
    }

    private fun columnsOverlap(first: Rect, second: Rect): Boolean {
        val overlap = minOf(first.right, second.right) - maxOf(first.left, second.left)
        return overlap > minOf(first.width(), second.width()) * 0.25 ||
            abs(first.centerX() - second.centerX()) <= maxOf(first.width(), second.width()) * 2
    }

    fun release() {
        recognizer.close()
    }

    private fun normalizeLines(input: String): List<String> {
        return input
            .replace("\r", "\n")
            .split('\n')
            .map(::cleanLine)
            .filter { it.isNotBlank() }
    }

    private fun cleanLine(line: String): String {
        return line
            .replace(Regex("[\\u200B-\\u200D\\uFEFF]"), "")
            .replace('|', 'I')
            .replace('“', '"')
            .replace('”', '"')
            .replace('‘', '\'')
            .replace('’', '\'')
            .trim()
    }

    private fun sanitizeCandidate(value: String?): String {
        return value.orEmpty()
            .replace(Regex("^[`\"'\\[\\](){}<>]+"), "")
            .replace(Regex("[`\"'\\[\\](){}<>]+$"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    private fun looksLikeUrl(value: String?): Boolean {
        return Regex("""(https?://|www\.|\.com\b|\.net\b|\.org\b)""", RegexOption.IGNORE_CASE)
            .containsMatchIn(value.orEmpty())
    }

    private fun isLikelyNoiseLine(value: String?): Boolean {
        val text = value.orEmpty().trim()
        if (text.isBlank()) return true
        if (looksLikeUrl(text)) return true
        return noiseRegex.containsMatchIn(foldForMatching(text))
    }

    private fun isSsidLabelLine(value: String): Boolean {
        val folded = foldForMatching(value)
            .replace(Regex("[^a-z0-9\\s:-]"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        if (folded.isBlank()) return false
        if (folded.length > 24 && !folded.contains(":")) return false
        return ssidLabelFoldedRegex.containsMatchIn(folded)
    }

    private fun isPasswordLabelLine(value: String): Boolean {
        val folded = foldForMatching(value)
            .replace(Regex("[^a-z0-9\\s:-]"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        if (folded.isBlank()) return false
        if (folded.length > 28 && !folded.contains(":")) return false
        return passwordLabelFoldedRegex.containsMatchIn(folded)
    }

    private fun extractLooseLabelValue(value: String, forPassword: Boolean): String? {
        val source = cleanLine(value)
        val regex = if (forPassword) passwordLooseValueRegex else ssidLooseValueRegex
        val match = regex.matchEntire(source) ?: return null
        return sanitizeCandidate(match.groupValues.getOrNull(1)).ifBlank { null }
    }

    private fun foldForMatching(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .replace('đ', 'd')
            .replace('Đ', 'd')
    }

    private fun parseWifiQrPayload(text: String): WifiOcrCredentials {
        var qrText = text.trim()
        if (!qrPrefixRegex.containsMatchIn(qrText)) {
            qrText = wifiQrInlineRegex.find(text)?.value
                ?: wifiQrLineRegex.find(text)?.value
                ?: ""
        }

        if (!qrPrefixRegex.containsMatchIn(qrText)) return WifiOcrCredentials()
        if (!Regex(""";\s*[SPT]\s*:""", RegexOption.IGNORE_CASE).containsMatchIn(qrText)) {
            return WifiOcrCredentials()
        }

        val payload = qrText.replaceFirst(qrPrefixRegex, "")
        val fields = splitWifiQrPayload(payload)

        var ssid = ""
        var password = ""
        var security = ""
        for (field in fields) {
            val separatorIndex = field.indexOf(':')
            if (separatorIndex <= 0) continue
            val key = field.substring(0, separatorIndex).trim().uppercase()
            val value = field.substring(separatorIndex + 1)
                .trim()
                .replace("\\;", ";")
                .trimWifiValue()
            when (key) {
                "S" -> ssid = value
                "P" -> password = value
                "T" -> security = value
            }
        }

        if (ssid.isBlank() && password.isBlank()) return WifiOcrCredentials()

        return WifiOcrCredentials(
            ssid = ssid,
            password = password,
            security = security,
            sourceFormat = "wifi_qr",
            confidence = if (ssid.isNotBlank() || password.isNotBlank()) 0.98 else 0.5,
        )
    }

    private fun splitWifiQrPayload(payload: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        payload.forEach { char ->
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }
                char == '\\' -> {
                    current.append(char)
                    escaped = true
                }
                char == ';' -> {
                    parts += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        if (current.isNotEmpty()) parts += current.toString()
        return parts
    }

    private fun extractFromLabeledLines(lines: List<String>): WifiOcrCredentials? {
        var ssid = ""
        var password = ""

        lines.forEachIndexed { index, line ->
            if (ssid.isBlank()) {
                ssid = extractValueAfterLabel(line, ssidValueRegex, passwordValueRegex)
                    ?: if (ssidLabelOnlyRegex.matches(line)) {
                        pickNextUsefulLine(lines, index + 1, forPassword = false)
                    } else {
                        null
                    }.orEmpty()
            }

            if (password.isBlank()) {
                val extractedPassword = extractValueAfterLabel(line, passwordValueRegex, ssidValueRegex)
                    ?: if (passwordLabelOnlyRegex.matches(line)) {
                        pickNextUsefulLine(lines, index + 1, forPassword = true)
                    } else {
                        null
                    }
                password = extractedPassword
                    ?.let { stripPasswordPrefix(it).ifBlank { it } }
                    .orEmpty()
            }
        }

        if (ssid.isBlank() && password.isBlank()) return null
        return WifiOcrCredentials(
            ssid = ssid,
            password = password,
            sourceFormat = "labeled_text",
            confidence = 0.85,
        )
    }

    private fun pickNextUsefulLine(
        lines: List<String>,
        fromIndex: Int,
        forPassword: Boolean,
    ): String? {
        for (index in fromIndex until minOf(lines.size, fromIndex + 3)) {
            val candidate = sanitizeCandidate(lines[index])
            if (candidate.isBlank()) continue
            if (ssidLabelOnlyRegex.matches(candidate) || passwordLabelOnlyRegex.matches(candidate)) continue
            if (isLikelyNoiseLine(candidate)) continue
            if (forPassword) {
                if (looksLikePassword(candidate)) {
                    return stripPasswordPrefix(candidate).ifBlank { candidate }
                }
            } else if (isPlausibleSsidCandidate(candidate) && !looksLikePassword(candidate)) {
                return candidate
            }
        }
        return null
    }

    private fun extractValueAfterLabel(
        line: String,
        valueRegex: Regex,
        cutRegex: Regex? = null,
    ): String? {
        val match = valueRegex.matchEntire(cleanLine(line)) ?: return null
        val rawValue = match.groupValues.getOrNull(1).orEmpty()
        val value = cutRegex?.replace(rawValue, "") ?: rawValue
        return sanitizeCandidate(value).ifBlank { null }
    }

    private fun extractTwoLineSsidPassword(lines: List<String>): WifiOcrCredentials? {
        if (lines.isEmpty()) return null

        val sanitizedLines = lines
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()

        while (sanitizedLines.isNotEmpty() && isWifiHeaderLine(sanitizedLines.first())) {
            sanitizedLines.removeAt(0)
        }

        if (sanitizedLines.isEmpty() || sanitizedLines.size > 3) return null

        val first = stripSsidPrefix(sanitizedLines[0]).ifBlank { sanitizedLines[0] }
        val second = sanitizedLines.getOrElse(1) { "" }
        val third = sanitizedLines.getOrElse(2) { "" }
        if (first.isBlank()) return null
        if (!isPlausibleSsidCandidate(first)) return null

        if (sanitizedLines.size == 1) {
            return WifiOcrCredentials(
                password = stripPasswordPrefix(first).ifBlank { first },
                sourceFormat = "single_line_password",
                confidence = 0.9,
            )
        }

        var normalizedPassword = stripPasswordPrefix(second).ifBlank { second }
        if (passwordLabelOnlyRegex.matches(second) && third.isNotBlank()) {
            normalizedPassword = stripPasswordPrefix(third).ifBlank { third }
        }
        if (passwordLabelOnlyRegex.matches(normalizedPassword)) {
            normalizedPassword = ""
        }

        return if (normalizedPassword.isBlank()) {
            WifiOcrCredentials(
                ssid = first,
                sourceFormat = "two_line_ssid_password",
                confidence = 0.72,
            )
        } else {
            WifiOcrCredentials(
                ssid = first,
                password = normalizedPassword,
                sourceFormat = "two_line_ssid_password",
                confidence = 0.93,
            )
        }
    }

    private fun isWifiHeaderLine(text: String): Boolean {
        val normalized = text.lowercase().replace(Regex("[^a-z]"), "")
        return normalized == "wifi" || normalized == "wifiname"
    }

    private fun extractHeuristic(lines: List<String>): WifiOcrCredentials? {
        val filtered = lines
            .map(::sanitizeCandidate)
            .filter { it.isNotBlank() }
            .filterNot { ssidLabelOnlyRegex.matches(it) || passwordLabelOnlyRegex.matches(it) }

        if (filtered.isEmpty()) return null

        var bestPassword: ScoredCandidate? = null
        var bestSsid: ScoredCandidate? = null

        filtered.forEachIndexed { index, line ->
            val passwordScored = scorePasswordCandidate(line)
                ?.copy(lineIndex = index)
            if (passwordScored != null && (bestPassword == null || passwordScored.score > bestPassword.score)) {
                bestPassword = passwordScored
            }

            val ssidScored = scoreSsidCandidate(line)
                ?.copy(lineIndex = index)
            if (ssidScored != null && (bestSsid == null || ssidScored.score > bestSsid.score)) {
                bestSsid = ssidScored
            }
        }

        val bestPasswordValue = bestPassword?.takeIf { it.score >= 2.6 }
        val bestSsidValue = bestSsid?.takeIf { it.score >= 2.6 }
        val cameFromSameLine = bestPasswordValue != null &&
            bestSsidValue != null &&
            bestPasswordValue.lineIndex == bestSsidValue.lineIndex
        val password = bestPasswordValue?.value.orEmpty()
        val ssid = if (cameFromSameLine) {
            ""
        } else {
            bestSsidValue?.value.orEmpty()
        }
        if (ssid.isBlank() && password.isBlank()) return null

        return WifiOcrCredentials(
            ssid = ssid,
            password = password,
            sourceFormat = "heuristic",
            confidence = when {
                ssid.isNotBlank() && password.isNotBlank() -> 0.78
                password.isNotBlank() -> 0.66
                else -> 0.52
            },
        )
    }

    private fun scorePasswordCandidate(line: String): ScoredCandidate? {
        val cleaned = sanitizeCandidate(line)
        if (cleaned.isBlank()) return null

        var score = 0.0
        var value = cleaned
        val hasPasswordLabel = passwordLabelRegex.containsMatchIn(cleaned)
        if (hasPasswordLabel) {
            score += 4.0
            value = stripPasswordPrefix(cleaned).ifBlank { cleaned }
        }

        if (!hasPasswordLabel && !looksLikePassword(value)) return null
        if (!hasPasswordLabel && value.any(Char::isWhitespace)) return null
        if (looksLikePassword(value)) score += 3.0
        if (hasPasswordLabel && value.length in 4..63) score += 2.2
        if (!value.contains(' ')) score += 1.0
        if (value.any(Char::isLetter) && value.any(Char::isDigit)) score += 1.0
        if (value.length > 20) score -= 0.5
        if (isLikelyNoiseLine(value)) score -= 3.0

        return ScoredCandidate(value = value, score = score)
    }

    private fun scoreSsidCandidate(line: String): ScoredCandidate? {
        val cleaned = sanitizeCandidate(line)
        if (cleaned.isBlank()) return null

        var score = 0.0
        var value = cleaned
        val hasSsidLabel = ssidLabelRegex.containsMatchIn(cleaned) || isSsidLabelLine(cleaned)
        if (hasSsidLabel) {
            score += 4.0
            value = cleaned
                .replace(ssidLabelRegex, "")
                .replace(Regex("""^(?:name|id)\b""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^[\s:=-]+"""), "")
                .trim()
        }

        if (value.isBlank() || value.length > 32) return null
        if (!isPlausibleSsidCandidate(value)) return null
        if (looksLikePassword(value) && !hasSsidLabel) score -= 1.1
        if (looksLikePassword(value)) score -= 0.55
        if (!passwordLabelRegex.containsMatchIn(value)) score += 0.8
        if (Regex("""[-_.]""").containsMatchIn(value)) score += 0.8
        if (value.any(Char::isDigit)) score += 0.45
        if (value.none(Char::isWhitespace) && value.length in 3..24) score += 0.35
        val wordCount = value.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        val letterCount = value.count(Char::isLetter)
        val letterOrSpaceCount = value.count { it.isLetter() || it.isWhitespace() }
        val isMostlyNameLike = letterCount >= 6 &&
            letterOrSpaceCount >= (value.length * 0.8).toInt()
        if (wordCount <= 2) {
            score += 0.35
        } else if (wordCount <= 4 && isMostlyNameLike) {
            score += 1.25
        } else {
            score -= 0.8
        }
        score += 0.85

        return ScoredCandidate(value = value, score = score)
    }

    private fun isPlausibleSsidCandidate(value: String): Boolean {
        val cleaned = sanitizeCandidate(value)
        if (cleaned.isBlank()) return false
        if (cleaned.length > 32) return false
        if (cleaned.equals("wifi", ignoreCase = true)) return false
        if (isSsidLabelLine(cleaned) || isPasswordLabelLine(cleaned)) return false
        if (looksLikeUrl(cleaned)) return false
        if (isLikelyNoiseLine(cleaned)) return false
        if (passwordLabelRegex.containsMatchIn(cleaned)) return false
        val wordCount = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        if (wordCount > 4) return false
        return true
    }

    private fun computeCandidateScore(candidate: WifiOcrCredentials): Double {
        var score = candidate.confidence ?: 0.0
        if (candidate.ssid.isNotBlank()) score += 0.35
        if (candidate.password.isNotBlank()) score += 0.35
        if (candidate.ssid.isNotBlank() && candidate.password.isNotBlank()) score += 0.18
        if (candidate.sourceFormat == "layout_ocr") score += 0.12
        if (candidate.sourceFormat == "labeled_text") score += 0.05
        if (candidate.password.isNotBlank() && candidate.ssid.isBlank()) score -= 0.08
        return score
    }

    private fun mergeCredentialCandidates(vararg candidates: WifiOcrCredentials?): WifiOcrCredentials {
        val validCandidates = candidates
            .filterNotNull()
            .map(::normalizeCredential)
            .filter { it.hasAnyValue() }

        if (validCandidates.isEmpty()) return WifiOcrCredentials()

        val ssidOptions = validCandidates.filter { it.ssid.isNotBlank() }
        val passwordOptions = validCandidates.filter { it.password.isNotBlank() }
        val mergedOptions = mutableListOf<MergedCredentialCandidate>()

        fun addMergedOption(
            ssidCandidate: WifiOcrCredentials?,
            passwordCandidate: WifiOcrCredentials?,
        ) {
            val ssid = ssidCandidate?.ssid.orEmpty()
            val password = passwordCandidate?.password.orEmpty()
            if (ssid.isBlank() && password.isBlank()) return
            if (
                ssid.isNotBlank() &&
                password.isNotBlank() &&
                isSameCredentialText(ssid, password) &&
                !canTrustSameTextForBothFields(ssidCandidate, passwordCandidate)
            ) {
                return
            }

            var score = 0.0
            if (ssidCandidate != null) {
                val ssidScore = computeSsidFieldScore(ssidCandidate)
                if (ssidScore == Double.NEGATIVE_INFINITY) return
                score += ssidScore
            }
            if (passwordCandidate != null) {
                val passwordScore = computePasswordFieldScore(passwordCandidate)
                if (passwordScore == Double.NEGATIVE_INFINITY) return
                score += passwordScore
            }
            if (ssid.isNotBlank() && password.isNotBlank()) score += 1.25
            if (ssidCandidate != null && ssidCandidate == passwordCandidate) {
                score += computeCandidateScore(ssidCandidate)
            }

            val sourceFormat = when {
                ssidCandidate != null &&
                    passwordCandidate != null &&
                    ssidCandidate.sourceFormat == passwordCandidate.sourceFormat -> ssidCandidate.sourceFormat
                ssidCandidate != null && passwordCandidate == null -> ssidCandidate.sourceFormat
                passwordCandidate != null && ssidCandidate == null -> passwordCandidate.sourceFormat
                else -> "ocr_merged"
            }.ifBlank { "ocr_merged" }

            val confidenceValues = listOfNotNull(ssidCandidate?.confidence, passwordCandidate?.confidence)
            mergedOptions += MergedCredentialCandidate(
                ssid = ssid,
                password = password,
                security = ssidCandidate?.security?.takeIf { it.isNotBlank() }
                    ?: passwordCandidate?.security.orEmpty(),
                sourceFormat = sourceFormat,
                confidence = confidenceValues.takeIf { it.isNotEmpty() }?.average()?.coerceIn(0.0, 1.0),
                score = score,
            )
        }

        ssidOptions.forEach { ssidCandidate ->
            passwordOptions.forEach { passwordCandidate ->
                addMergedOption(ssidCandidate, passwordCandidate)
            }
            addMergedOption(ssidCandidate, null)
        }
        passwordOptions.forEach { passwordCandidate ->
            addMergedOption(null, passwordCandidate)
        }

        val best = mergedOptions.maxByOrNull { it.score } ?: return WifiOcrCredentials()
        return WifiOcrCredentials(
            ssid = best.ssid,
            password = best.password,
            security = best.security,
            sourceFormat = best.sourceFormat,
            confidence = best.confidence,
        )
    }

    private fun normalizeCredential(candidate: WifiOcrCredentials): WifiOcrCredentials {
        return candidate.copy(
            ssid = sanitizeCandidate(stripSsidPrefix(candidate.ssid)),
            password = sanitizeCandidate(stripPasswordPrefix(candidate.password)),
            security = candidate.security.trim(),
            sourceFormat = candidate.sourceFormat.trim(),
            confidence = candidate.confidence?.coerceIn(0.0, 1.0),
        )
    }

    private fun computeSsidFieldScore(candidate: WifiOcrCredentials): Double {
        val ssid = candidate.ssid
        if (ssid.isBlank()) return Double.NEGATIVE_INFINITY
        var score = scoreSsidCandidate(ssid)?.score
            ?: if (hasStructuredOcrEvidence(candidate) && isPlausibleSsidCandidate(ssid)) {
                2.8
            } else {
                return Double.NEGATIVE_INFINITY
            }
        score += candidate.confidence ?: 0.0
        if (candidate.sourceFormat == "layout_ocr") score += 0.4
        if (candidate.sourceFormat == "labeled_text") score += 0.2
        if (candidate.password.isNotBlank()) score += 0.25
        return score
    }

    private fun computePasswordFieldScore(candidate: WifiOcrCredentials): Double {
        val password = candidate.password
        if (password.isBlank()) return Double.NEGATIVE_INFINITY
        var score = scorePasswordCandidate(password)?.score
            ?: if (hasStructuredOcrEvidence(candidate) && isPlausiblePasswordValue(password)) {
                2.8
            } else {
                return Double.NEGATIVE_INFINITY
            }
        score += candidate.confidence ?: 0.0
        if (candidate.sourceFormat == "layout_ocr") score += 0.4
        if (candidate.sourceFormat == "labeled_text") score += 0.2
        if (candidate.ssid.isNotBlank()) score += 0.25
        return score
    }

    private fun hasStructuredOcrEvidence(candidate: WifiOcrCredentials): Boolean {
        val source = candidate.sourceFormat
        return source == "layout_ocr" ||
            source == "labeled_text" ||
            source == "two_line_ssid_password" ||
            source == "wifi_qr"
    }

    private fun isPlausiblePasswordValue(value: String): Boolean {
        val cleaned = sanitizeCandidate(value)
        return cleaned.length in 4..63 &&
            cleaned.none(Char::isWhitespace) &&
            !looksLikeUrl(cleaned) &&
            !isLikelyNoiseLine(cleaned) &&
            !isSsidLabelLine(cleaned) &&
            !isPasswordLabelLine(cleaned)
    }

    private fun isSameCredentialText(first: String, second: String): Boolean {
        return sanitizeCandidate(first).equals(sanitizeCandidate(second), ignoreCase = true)
    }

    private fun canTrustSameTextForBothFields(
        ssidCandidate: WifiOcrCredentials?,
        passwordCandidate: WifiOcrCredentials?,
    ): Boolean {
        if (ssidCandidate == null || passwordCandidate == null) return false
        val structuredSsid = hasStructuredOcrEvidence(ssidCandidate)
        val structuredPassword = hasStructuredOcrEvidence(passwordCandidate)
        if (!structuredSsid || !structuredPassword) return false
        return ssidCandidate.sourceFormat != "heuristic" &&
            passwordCandidate.sourceFormat != "heuristic"
    }

    private fun looksLikePassword(text: String): Boolean {
        val value = sanitizeCandidate(text)
        if (value.isBlank()) return false
        if (looksLikeUrl(value)) return false
        if (value.length < 8 || value.length > 63) return false
        if (value.any(Char::isWhitespace)) return false
        if (Regex("\\s{2,}").containsMatchIn(value)) return false
        val hasAllowedChars = Regex("""[A-Za-z0-9!@#$%^&*()_+\-=\[\]{};':"\\|,.<>/?`~]""")
            .containsMatchIn(value)
        if (!hasAllowedChars) return false

        val hasDigit = value.any(Char::isDigit)
        val specialCount = value.count { !it.isLetterOrDigit() }
        return hasDigit || specialCount >= 2
    }

    private fun stripPasswordPrefix(text: String): String {
        return text.replace(passwordPrefixRegex, "").trim()
    }

    private fun stripSsidPrefix(text: String): String {
        return text.replace(ssidPrefixRegex, "").trim()
    }

    private fun String.trimWifiValue(): String {
        return trim()
            .trim('"', '\'', '`')
            .trim()
    }

    private fun WifiOcrCredentials.hasAnyValue(): Boolean {
        return ssid.isNotBlank() || password.isNotBlank()
    }

    companion object {
        private val qrPrefixRegex = Regex("^WIFI:", RegexOption.IGNORE_CASE)
        private val wifiQrInlineRegex = Regex("""WIFI:[^\n]*?;;""", RegexOption.IGNORE_CASE)
        private val wifiQrLineRegex = Regex("""WIFI:[^\n]*""", RegexOption.IGNORE_CASE)

        private val ssidValueRegex = Regex(
            """^\s*(?:ssid|sidd|wi-?fi(?:\s*name)?|wi-?fi\s*id|id|name|network\s*name|ten\s*wifi|ten\s*mang|mang)\s*(?::|=|-|\s{1,})\s*(.*)$""",
            RegexOption.IGNORE_CASE,
        )
        private val passwordValueRegex = Regex(
            """^\s*(?:password|passww?ord|pass\s*word|pass|pwd|pw|key|code|mat\s*khau|mat\s*khuan|mk)\s*(?::|=|-|\s{1,})\s*(.*)$""",
            RegexOption.IGNORE_CASE,
        )
        private val ssidLabelOnlyRegex = Regex(
            """^\s*(?:ssid|sidd|wi-?fi(?:\s*name)?|wi-?fi\s*id|id|name|network\s*name|ten\s*wifi|ten\s*mang|mang)\s*[:=-]?\s*$""",
            RegexOption.IGNORE_CASE,
        )
        private val passwordLabelOnlyRegex = Regex(
            """^\s*(?:password|passww?ord|pass\s*word|pass|pwd|pw|key|code|mat\s*khau|mat\s*khuan|mk)\s*[:=-]?\s*$""",
            RegexOption.IGNORE_CASE,
        )
        private val passwordLabelRegex = Regex(
            """^\s*(?:password|passww?ord|pass\s*word|pass|pwd|pw|key|code|mat\s*khau|mat\s*khuan|mk)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val ssidLabelRegex = Regex(
            """^\s*(?:ssid|sidd|wi-?fi(?:\s*name)?|wi-?fi\s*id|id|name|network\s*name|ten\s*wifi|ten\s*mang|mang)\b""",
            RegexOption.IGNORE_CASE,
        )
        private val passwordPrefixRegex = Regex(
            """^(?:(?:password|passww?ord|pass\s*word|pwd|pw|key|code|mat\s*khau|mat\s*khuan|mk)\b\s*[:=-]?|pass\s*[:=-])\s*""",
            RegexOption.IGNORE_CASE,
        )
        private val ssidPrefixRegex = Regex(
            """^(?:ssid|sidd|wi-?fi(?:\s*name)?|wi-?fi\s*id|id|name|network\s*name|ten\s*wifi|ten\s*mang|mang)\b\s*[:=-]?\s*""",
            RegexOption.IGNORE_CASE,
        )
        private val ssidLooseValueRegex = Regex(
            """^\s*(?:ssid|sidd|wi-?fi(?:\s*name)?|wi-?fi\s*id|id|name|network\s*name|ten\s*wifi|ten\s*mang|mang)\s*(?::|=|-|\s{1,})\s*(.+)$""",
            RegexOption.IGNORE_CASE,
        )
        private val passwordLooseValueRegex = Regex(
            """^\s*(?:password|passww?ord|pass\s*word|pass|pwd|pw|key|code|mat\s*khau|mat\s*khuan|mk)\s*(?::|=|-|\s{1,})\s*(.+)$""",
            RegexOption.IGNORE_CASE,
        )
        private val ssidLabelFoldedRegex = Regex(
            """^(?:ssid|sidd|wi-?fi(?:\s*name)?|wi-?fi\s*id|id|name|network\s*name|ten\s*wifi|ten\s*mang|mang)(?:\s*[:=-])?(?:\s|$)""",
            RegexOption.IGNORE_CASE,
        )
        private val passwordLabelFoldedRegex = Regex(
            """^(?:password|passww?ord|pass\s*word|pass|pwd|pw|key|code|mat\s*khau|mat\s*khuan|mk)(?:\s*[:=-])?(?:\s|$)""",
            RegexOption.IGNORE_CASE,
        )
        private val noiseRegex = Regex(
            """(\bfree\s*wifi\b|\bmien\s*phi\b|\bhotline\b|\bemail\b|\busername\b|\bdang\s*nhap\b|\blogin\b|\bwelcome\b|\bxin\s*chao\b|\bcam\s*on\b|\bkinh\s*chao\b|\bquy\s*khach\b|\bscan\b|\bqr\b|\bmenu\b|\binternet\b|\bconnected\b|\brouter\b|\bsecurity\b|\bgoogle\b|\bxuong\s*go\b|\bwatermark\b)""",
            RegexOption.IGNORE_CASE,
        )
    }
}

data class WifiOcrCredentials(
    val ssid: String = "",
    val password: String = "",
    val security: String = "",
    val sourceFormat: String = "",
    val confidence: Double? = null,
)

data class WifiOcrScanResult(
    val text: String,
    val credentials: WifiOcrCredentials = WifiOcrCredentials(),
)

data class OcrImageQuality(
    val isReadable: Boolean,
    val message: String = "",
)

private data class OcrTextLine(
    val text: String,
    val box: Rect,
) {
    val centerX: Int = box.centerX()
    val centerY: Int = box.centerY()
    val height: Int = maxOf(1, box.height())
}

private data class ScoredCandidate(
    val value: String,
    val score: Double,
    val lineIndex: Int = -1,
)

private data class MergedCredentialCandidate(
    val ssid: String,
    val password: String,
    val security: String,
    val sourceFormat: String,
    val confidence: Double?,
    val score: Double,
)
