package com.smartwificonnect.ocr

import android.util.Log
import java.text.Normalizer

/**
 * Confidence-driven SSID + password extractor.
 *
 * The legacy [WifiOcrTextParser] handles QR payloads (`WIFI:S:...;P:...`) and
 * very specific Vietnamese phrasings via a stack of regexes. Real-world OCR
 * is messier — labels can be misread (`SIDD`, `S.S.I.D`), the value can sit
 * on the next line, and unrelated paragraphs (menus, ads, addresses) get
 * mixed in.
 *
 * This extractor runs BEFORE the legacy parser. It:
 *   1) Splits the OCR text into clean lines.
 *   2) Detects label-bearing lines using fuzzy matching that tolerates the
 *      most common OCR misreads (S↔5, I↔1, O↔0, missing letters, etc.).
 *   3) Picks the value either inline (after `:` `=` `→` `-`) or from the
 *      nearest subsequent line that LOOKS like a credential value.
 *   4) Returns a [SmartExtraction] with a confidence score so callers can
 *      decide between auto-connect and "open edit screen".
 *
 * Conservative by design: if nothing is reasonably confident it returns
 * empty values + low confidence. The caller stays in control.
 */
internal object SmartWifiOcrExtractor {

    private const val TAG = "SmartOcrExtractor"

    fun extract(rawText: String): SmartExtraction {
        Log.d(TAG, "──────────────────────────────────────────────")
        Log.d(TAG, "▶ raw OCR (len=${rawText.length})")
        rawText.split('\n').forEachIndexed { i, line ->
            Log.d(TAG, "  raw[$i]: '$line'")
        }

        val cleanedLines = preCleanLines(rawText)
        Log.d(TAG, "▶ cleaned lines (n=${cleanedLines.size})")
        cleanedLines.forEachIndexed { i, line ->
            Log.d(TAG, "  clean[$i]: '$line'")
        }

        // Compressed multi-label split: handle one-line layouts like
        //   "SIDD:JUNJUNQUAN:MK:12345678"
        //   "SSID: My-AP Password: hunter2"
        //   ":JUNJUNQUA:MK:123" (label dropped by OCR but value+label2+value)
        // The per-field detector below works on a clean "Label: Value" world,
        // so we split these into two virtual lines first.
        val expandedLines = explodeCompressedLabels(cleanedLines)
        if (expandedLines !== cleanedLines) {
            Log.d(TAG, "▶ expanded lines (n=${expandedLines.size}) after compressed-label split")
            expandedLines.forEachIndexed { i, line ->
                Log.d(TAG, "  expand[$i]: '$line'")
            }
        }

        val ssidPick = pickField(expandedLines, FieldKind.SSID)
        val pwdPick = pickField(expandedLines, FieldKind.PASSWORD)

        // Reject obvious cross-contamination: same exact value on both fields
        // means OCR/our regex grabbed the wrong thing for one of them.
        val (finalSsid, finalPwd) = if (
            ssidPick.value.isNotBlank() &&
            pwdPick.value.isNotBlank() &&
            ssidPick.value.equals(pwdPick.value, ignoreCase = true)
        ) {
            Log.w(TAG, "  ⚠ SSID and password match exactly — keeping only the higher-confidence one")
            if (ssidPick.confidence >= pwdPick.confidence) {
                ssidPick to FieldPick("", 0.0, "duplicate-cleared")
            } else {
                FieldPick("", 0.0, "duplicate-cleared") to pwdPick
            }
        } else {
            ssidPick to pwdPick
        }

        val combined = (finalSsid.confidence * 0.45) + (finalPwd.confidence * 0.55)
        val confidence = combined.coerceIn(0.0, 1.0)

        Log.d(TAG, "▶ extracted SSID:     '${finalSsid.value}'  conf=${"%.2f".format(finalSsid.confidence)}  reason=${finalSsid.reason}")
        Log.d(TAG, "▶ extracted password: '${finalPwd.value}'  conf=${"%.2f".format(finalPwd.confidence)}  reason=${finalPwd.reason}")
        Log.d(TAG, "▶ combined confidence: ${"%.2f".format(confidence)}")
        Log.d(TAG, "──────────────────────────────────────────────")

        return SmartExtraction(
            ssid = finalSsid.value,
            password = finalPwd.value,
            confidence = confidence,
            ssidConfidence = finalSsid.confidence,
            passwordConfidence = finalPwd.confidence,
            reason = "ssid:${finalSsid.reason} | pwd:${finalPwd.reason}",
        )
    }

    // ── Stage 1: cleaning ─────────────────────────────────────────────

    private fun preCleanLines(rawText: String): List<String> {
        return rawText
            .replace('\u00a0', ' ')        // non-breaking space → space
            .replace('\u3000', ' ')        // ideographic space → space
            .replace('：', ':')            // full-width colon
            .replace('＝', '=')            // full-width equals
            .replace('｜', '|')
            .replace('—', '-')
            .replace('–', '-')
            .replace('•', ' ')
            .replace('·', ' ')
            .split('\n')
            .map { line ->
                Normalizer.normalize(line, Normalizer.Form.NFKC)
                    .replace("\\s{2,}".toRegex(), " ")
                    .trim()
            }
            .filter { it.isNotBlank() }
            .filterNot { isObviouslyNoise(it) }
    }

    /**
     * Splits "compressed multi-label" lines like
     *   "SIDD:JUNJUNQUAN:MK:12345678"
     *   "SSID: My-AP Password: hunter2"
     * into separate `<label>: <value>` lines so the per-field detector can
     * read them cleanly. Returns the input list unchanged when nothing splits.
     */
    private fun explodeCompressedLabels(lines: List<String>): List<String> {
        var changed = false
        val out = mutableListOf<String>()
        for (line in lines) {
            val parts = splitOnEmbeddedLabels(line)
            if (parts.size > 1) {
                changed = true
                out += parts
            } else {
                out += line
            }
        }
        return if (changed) out else lines
    }

    /**
     * Looks for known label tokens that appear MID-line and splits there.
     * Example:
     *   "SIDD:JUNJUNQUAN:MK:12345678"
     *      → ["SIDD:JUNJUNQUAN", "MK:12345678"]
     *   ":JUNJUNQUA:MK:123abc"  (leading label dropped by OCR)
     *      → ["SSID:JUNJUNQUA", "MK:123abc"]
     *
     * The matching uses the normalised lowercase form so that "MK" / "mk" /
     * "Mat khau" / "Pa55word" all hit. The original (case-preserving) line
     * is sliced at the same boundary so the password value keeps its case.
     */
    private fun splitOnEmbeddedLabels(line: String): List<String> {
        val normalized = line.normalized()
        if (normalized.length < 6) return listOf(line)

        // Build a sorted list of (startIndex, label) hits. Longer labels win
        // to avoid splitting "passcode" as just "pass".
        val tokens = (passwordLabels + ssidLabels)
            .distinct()
            .sortedByDescending { it.length }

        data class Hit(val start: Int, val end: Int, val kind: String)
        val hits = mutableListOf<Hit>()
        for (token in tokens) {
            if (token.length < 2) continue
            var idx = 0
            while (idx <= normalized.length - token.length) {
                val pos = normalized.indexOf(token, idx)
                if (pos < 0) break
                // Must be at a word boundary on the LEFT (start, space or punctuation)
                // and followed by a separator on the RIGHT to count as a label.
                val leftOk = pos == 0 || !normalized[pos - 1].isLetterOrDigit()
                val endIdx = pos + token.length
                val rightChar = normalized.getOrNull(endIdx)
                val rightOk = rightChar == null ||
                    rightChar == ':' ||
                    rightChar == '=' ||
                    rightChar == ' ' ||
                    rightChar == '-' ||
                    rightChar == '|'
                if (leftOk && rightOk) {
                    val kind = if (token in passwordLabels) "P" else "S"
                    hits += Hit(pos, endIdx, kind)
                }
                idx = endIdx
            }
        }

        // ── Case A: 2+ label hits → split at each boundary ──
        if (hits.size >= 2) {
            val sorted = hits.sortedWith(compareBy({ it.start }, { -(it.end - it.start) }))
            val nonOverlapping = mutableListOf<Hit>()
            var lastEnd = -1
            for (h in sorted) {
                if (h.start >= lastEnd) { nonOverlapping += h; lastEnd = h.end }
            }
            if (nonOverlapping.size >= 2) {
                val boundaries = nonOverlapping.map { it.start }
                val pieces = mutableListOf<String>()
                for (i in boundaries.indices) {
                    val start = boundaries[i].coerceAtMost(line.length)
                    val end = (if (i + 1 < boundaries.size) boundaries[i + 1] else line.length).coerceAtMost(line.length)
                    if (end <= start) continue
                    val piece = line.substring(start, end).trim().trimStart(':', '=', '-', '|', ',', ' ')
                    if (piece.isNotBlank()) pieces += piece
                }
                // Promote leading unlabeled piece to "SSID: ..."
                if (pieces.isNotEmpty()) {
                    val first = pieces[0]
                    val firstNorm = first.normalized()
                    val startsWithLabel = (passwordLabels + ssidLabels).any { firstNorm.startsWith(it) }
                    if (!startsWithLabel && line.trimStart().startsWith(':')) {
                        pieces[0] = "SSID: $first"
                    }
                }
                return pieces
            }
        }

        // ── Case B: exactly 1 password-label hit with content before it ──
        // Handles ":JUNJUNQUAN:MK:12345678" or "JUNJUNQUAN MK:12345678"
        // where there's no explicit SSID label but the password label splits
        // the line into [SSID-value, Password-value].
        val pwdHit = hits.firstOrNull { it.kind == "P" && it.start > 0 }
        if (pwdHit != null) {
            val beforePwd = line.substring(0, pwdHit.start.coerceAtMost(line.length))
                .trim().trimEnd(':', '=', '-', '|', ',', ' ')
            val afterPwd = line.substring(pwdHit.end.coerceAtMost(line.length))
                .trim().trimStart(':', '=', '-', '|', ',', ' ')
            val pieces = mutableListOf<String>()
            if (beforePwd.isNotBlank()) {
                // The content before the password label is likely the SSID value.
                val cleanBefore = beforePwd.trimStart(':', '=', '-', '|', ',', ' ')
                pieces += "SSID: $cleanBefore"
            }
            if (afterPwd.isNotBlank()) {
                val labelToken = normalized.substring(pwdHit.start, pwdHit.end)
                pieces += "$labelToken: $afterPwd"
            }
            if (pieces.size >= 2) return pieces
        }

        return listOf(line)
    }

    private fun isObviouslyNoise(line: String): Boolean {
        val normalized = line.normalized()

        // 1-2 letter or punctuation-only lines.
        if (normalized.length < 2) return true
        if (normalized.all { !it.isLetterOrDigit() }) return true

        // Common ad/website noise.
        if (normalized.contains("www ") ||
            normalized.contains(".com") ||
            normalized.contains(".vn") ||
            normalized.contains("hotline") ||
            normalized.contains("dia chi") ||
            normalized.contains("instagram") ||
            normalized.contains("facebook") ||
            normalized.contains("tiktok")
        ) return true

        // Vietnamese hospitality boilerplate frequently in note photos.
        if (normalized.contains("kinh chao") ||
            normalized.contains("quy khach") ||
            normalized.contains("xin moi") ||
            normalized.contains("chuc quy khach")
        ) return true

        // Pure date / time / price like "9:30 PM" or "50.000 VND".
        if (Regex("^\\d{1,2}\\s*[:\\.]\\s*\\d{2}\\s*(am|pm)?$").containsMatchIn(normalized)) return true
        if (Regex("\\b\\d+\\s*(vnd|usd|k|đ|d)\\b").containsMatchIn(normalized)) return true

        return false
    }

    // ── Stage 2: per-field selection ──────────────────────────────────

    private enum class FieldKind { SSID, PASSWORD }

    private fun pickField(lines: List<String>, kind: FieldKind): FieldPick {
        // Score every line as a potential label-bearing line for this field.
        // For each strong label match, look for the value (inline first, then
        // the next non-label line). Return the highest-confidence value.
        var best = FieldPick("", 0.0, "no-match")

        lines.forEachIndexed { idx, line ->
            val labelHit = detectLabel(line, kind) ?: return@forEachIndexed

            // ── 2a) Inline value (after the label, on the same line) ──
            val inlineValue = labelHit.tail.cleanValue(kind)
            if (inlineValue.isNotBlank() && inlineValue.looksLikeValueFor(kind)) {
                val conf = labelHit.score * 0.95 +
                    if (kind == FieldKind.PASSWORD && inlineValue.length in 8..63) 0.05 else 0.0
                if (conf > best.confidence) {
                    best = FieldPick(
                        value = inlineValue,
                        confidence = conf.coerceIn(0.0, 1.0),
                        reason = "inline-after-label[idx=$idx,score=${"%.2f".format(labelHit.score)}]",
                    )
                }
                return@forEachIndexed
            }

            // ── 2b) Value on the next 1-3 lines ──
            val followIdx = ((idx + 1)..(idx + 3).coerceAtMost(lines.lastIndex))
                .firstOrNull { i ->
                    val cand = lines[i]
                    detectLabel(cand, FieldKind.SSID) == null &&
                        detectLabel(cand, FieldKind.PASSWORD) == null &&
                        cand.cleanValue(kind).looksLikeValueFor(kind)
                }
            if (followIdx != null) {
                val candValue = lines[followIdx].cleanValue(kind)
                val conf = labelHit.score * 0.85 - (followIdx - idx - 1) * 0.05
                if (conf > best.confidence) {
                    best = FieldPick(
                        value = candValue,
                        confidence = conf.coerceIn(0.0, 1.0),
                        reason = "next-line[label-idx=$idx, value-idx=$followIdx, label-score=${"%.2f".format(labelHit.score)}]",
                    )
                }
            }
        }

        // ── Stage 2c) Fallback: lone strong-shaped line (no label found) ──
        if (best.value.isBlank()) {
            val candidate = lines.firstOrNull { it.looksLikeValueFor(kind) }
            if (candidate != null) {
                val cleaned = candidate.cleanValue(kind)
                if (cleaned.isNotBlank()) {
                    best = FieldPick(
                        value = cleaned,
                        confidence = 0.35,
                        reason = "no-label-fallback",
                    )
                }
            }
        }

        return best
    }

    // ── Stage 3: label detection (fuzzy) ──────────────────────────────

    private data class LabelHit(val score: Double, val tail: String)

    /**
     * Attempts to read [line] as `<label> [: = →] <tail>`. Returns null if no
     * label is recognised. The score reflects how clean the label match is —
     * exact "Password:" hits 0.95, fuzzy "Pa55:" hits ~0.7.
     */
    private fun detectLabel(line: String, kind: FieldKind): LabelHit? {
        val normalized = line.normalized()

        // Find the earliest separator (:, =, -, →, |). The label sits to its
        // left; the tail is to its right. Many OCR samples don't have a
        // separator at all — handle that with a "label is the entire line"
        // path for label-only rows.
        val separatorIndex = listOf(':', '=', '→', '-')
            .mapNotNull { sep ->
                val i = normalized.indexOf(sep)
                if (i in 1..14) i else null
            }
            .minOrNull()

        val labelPart: String
        val tailPart: String
        if (separatorIndex != null) {
            labelPart = normalized.substring(0, separatorIndex).trim()
            // Map back to the original line for tail extraction so we DON'T
            // drop case-sensitive password chars or symbols when normalising.
            val sepIndexOrig = line.indexOfAny(charArrayOf(':', '=', '→', '-'))
                .let { if (it in 1..14) it else 0 }
            tailPart = if (sepIndexOrig > 0) line.substring(sepIndexOrig + 1).trim() else ""
        } else {
            // Whole line could be a "label only" row like "SSID" — the value
            // would come from the next line.
            labelPart = normalized
            tailPart = ""
        }
        if (labelPart.isBlank() || labelPart.length > 24) return null

        val score = scoreLabelAgainst(labelPart, kind)
        return if (score >= 0.55) LabelHit(score, tailPart) else null
    }

    private fun scoreLabelAgainst(labelPart: String, kind: FieldKind): Double {
        val candidates = if (kind == FieldKind.SSID) ssidLabels else passwordLabels
        var bestScore = 0.0
        candidates.forEach { canonical ->
            // Quick win: substring match.
            if (labelPart.contains(canonical)) {
                bestScore = maxOf(bestScore, 0.95)
                return@forEach
            }
            // Token-equals (after stripping non-letters).
            val left = labelPart.filter { it.isLetterOrDigit() }
            val right = canonical.filter { it.isLetterOrDigit() }
            if (right.length >= 3 && left == right) {
                bestScore = maxOf(bestScore, 0.95)
                return@forEach
            }
            // Levenshtein on alphanumeric forms — tolerate 1 OCR misread per
            // 4 chars (so "sidd" vs "ssid" passes, "passcoke" vs "passcode" passes).
            if (left.length in 3..16 && right.length >= 3) {
                val dist = levenshtein(left, right)
                val tolerated = (right.length / 4).coerceAtLeast(1)
                if (dist <= tolerated) {
                    val sim = 1.0 - dist.toDouble() / right.length
                    bestScore = maxOf(bestScore, 0.6 + sim * 0.3)
                }
            }
        }
        return bestScore
    }

    // ── Stage 4: value cleaners + shape recognisers ───────────────────

    /**
     * Removes leading separators / quoting that often survive OCR but does
     * NOT strip valid password symbols. Works on the ORIGINAL casing so
     * the password stays connectable.
     */
    private fun String.cleanValue(kind: FieldKind): String {
        var v = trim().trimStart(':', '=', '-', '→', '|', '.', ',', ' ')
        v = v.trim('"', '\'', '`', '\u201c', '\u201d', '\u2018', '\u2019').trim()

        // Strip leading single-char + colon artifacts from OCR misreading
        // labels. E.g. "D: JUN JUN QUAN" where "D:" is a remnant of "SSID:".
        // Only strip if the char before ':' is a single letter (not a real
        // value like "5G: MyNetwork").
        val singleCharColonPrefix = Regex("^[A-Za-z]:\\s*")
        if (kind == FieldKind.SSID && singleCharColonPrefix.containsMatchIn(v)) {
            v = v.replace(singleCharColonPrefix, "").trim()
        }

        // Drop trailing in-line label fragments like " | password:..." that
        // sometimes come from same-row layouts.
        v = v.replace(
            Regex(
                "\\s+(?:\\||(?:password|pass|pwd|mat\\s*khau|m[aạ]t\\s*kh[aẩ]u|mk)\\s*[:=]).*$",
                RegexOption.IGNORE_CASE,
            ),
            "",
        ).trim()
        // SSIDs sometimes have a phantom trailing punctuation.
        if (kind == FieldKind.SSID) {
            v = v.trimEnd(',', ';', '.', '*')
        }
        return v
    }

    private fun String.looksLikeValueFor(kind: FieldKind): Boolean {
        if (isBlank()) return false
        val v = this
        if (v.length < 2) return false

        // Hard filters: never accept things that LOOK like a label.
        val lc = v.normalized()
        val labels = ssidLabels + passwordLabels
        if (labels.any { canonical ->
                val noTrail = lc.removeSuffix(":").removeSuffix("=").trim()
                noTrail == canonical
            }
        ) return false

        return when (kind) {
            FieldKind.SSID -> {
                // SSID: 1..32 chars, must contain at least one letter or digit,
                // not just punctuation.
                v.length in 1..32 && v.any { it.isLetterOrDigit() }
            }
            FieldKind.PASSWORD -> {
                // WPA/WPA2 passphrase: 8..63 printable ASCII characters.
                // We relax to 4+ here because OCR may truncate; the actual
                // WiFi connection will reject if truly too short.
                v.length in 4..63 && v.all { it.code in 32..126 }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private fun String.normalized(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase()
            .replace('đ', 'd')
            .replace('|', 'i')
            .replace('！', '!')
            .replace("\\s+".toRegex(), " ")
            .trim()
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val rows = a.length
        val cols = b.length
        var prev = IntArray(cols + 1) { it }
        var curr = IntArray(cols + 1)
        for (r in 1..rows) {
            curr[0] = r
            for (c in 1..cols) {
                val sub = if (a[r - 1] == b[c - 1]) 0 else 1
                curr[c] = minOf(curr[c - 1] + 1, prev[c] + 1, prev[c - 1] + sub)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[cols]
    }

    // ── Label dictionaries ────────────────────────────────────────────

    /** SSID-style labels in their normalised form (lowercase, no diacritics). */
    private val ssidLabels = listOf(
        "ssid",
        "sidd",          // common OCR misread
        "sid",           // partial OCR misread
        "ssid name",
        "ten wi-fi (ssid)",
        "ten wifi (ssid)",
        "ten wi fi (ssid)",
        "ten wi-fi",
        "ten wifi",
        "ten wi fi",
        "wifi name",
        "wifi id",
        "wifi",
        "wi-fi",
        "wi fi",
        "network name",
        "network",
        "name",
        "ten mang",
        "ten",
    )

    /** Password-style labels in normalised form. */
    private val passwordLabels = listOf(
        "password",
        "pass word",
        "pass",
        "passcode",
        "pwd",
        "mat khau",
        "mat khau wifi",
        "mat khau wi fi",
        "matkhau",
        "mk",
        "wifi password",
        "wi fi password",
        "wifi pass",
    )

    data class FieldPick(
        val value: String,
        val confidence: Double,
        val reason: String,
    )
}

data class SmartExtraction(
    val ssid: String,
    val password: String,
    val confidence: Double,
    val ssidConfidence: Double,
    val passwordConfidence: Double,
    val reason: String,
) {
    /** Caller can use this to decide between "auto-connect" and "open edit screen". */
    val isConfidentEnoughForAutoConnect: Boolean
        get() = ssid.isNotBlank() &&
            password.isNotBlank() &&
            ssidConfidence >= 0.55 &&
            passwordConfidence >= 0.55
}
