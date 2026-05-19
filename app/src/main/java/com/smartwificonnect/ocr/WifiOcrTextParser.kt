package com.smartwificonnect.ocr

import java.text.Normalizer
import java.util.regex.Pattern

internal object WifiOcrTextParser {
    fun extractWifiCredentials(text: String): WifiOcrCredentials {
        val qrCredentials = parseWifiQrPayload(text)
        if (qrCredentials.hasAnyValue()) return qrCredentials

        // Smart extractor: confidence-driven label detection. We try this
        // first because it deals with messy real-world OCR (misread labels
        // like "SIDD", values on the next line, ad/menu noise lines, etc.)
        // far better than the legacy regex stack below.
        val smart = SmartWifiOcrExtractor.extract(text)
        if (smart.ssid.isNotBlank() && smart.password.isNotBlank() && smart.confidence >= 0.40) {
            return WifiOcrCredentials(
                ssid = smart.ssid.sanitizeSsidValue(),
                password = smart.password.sanitizePasswordValue(),
            )
        }

        // Fall back to the legacy line-by-line parser (still valuable for
        // narrowly formatted notes the smart extractor wasn't trained on).
        // If the smart extractor found ONE field strongly, prefer it over
        // the legacy result for that field — partial wins still help.
        val legacy = legacyExtract(text)
        val mergedSsid = if (smart.ssid.isNotBlank() && smart.ssidConfidence >= 0.45) {
            smart.ssid.sanitizeSsidValue()
        } else {
            legacy.ssid
        }
        val mergedPassword = if (smart.password.isNotBlank() && smart.passwordConfidence >= 0.45) {
            smart.password.sanitizePasswordValue()
        } else {
            legacy.password
        }
        return WifiOcrCredentials(
            ssid = mergedSsid,
            password = if (mergedSsid.isLikelySameCredentialAs(mergedPassword)) "" else mergedPassword,
        )
    }

    private fun legacyExtract(text: String): WifiOcrCredentials {

        val lines = text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        var ssid = ""
        var password = ""

        for ((index, line) in lines.withIndex()) {
            val inlineBoth = line.extractCombinedInlineCredentials()
            if (inlineBoth != null) {
                if (ssid.isBlank()) ssid = inlineBoth.first.sanitizeSsidValue()
                if (password.isBlank()) password = inlineBoth.second.sanitizePasswordValue()
            }

            if ((ssid.isBlank() || password.isBlank()) && line.containsBothSsidAndPasswordLabels()) {
                val splitRow = lines.nextSplitRowCredentialsAfter(index)
                if (splitRow != null) {
                    if (ssid.isBlank()) ssid = splitRow.first.sanitizeSsidValue()
                    if (password.isBlank()) password = splitRow.second.sanitizePasswordValue()
                }
            }

            if (ssid.isBlank()) {
                val inlineSsid = line.extractFirstMatch(ssidValuePatterns + looseSsidValuePatterns)
                if (inlineSsid.isNotBlank()) {
                    ssid = inlineSsid.sanitizeSsidValue()
                } else if (line.matchesAny(ssidLabelOnlyPatterns)) {
                    ssid = lines.nextLikelySsidAfter(index)
                }
            }

            if (password.isBlank()) {
                val inlinePassword = line.extractFirstMatch(passwordValuePatterns)
                if (inlinePassword.isNotBlank()) {
                    password = inlinePassword.sanitizePasswordValue()
                } else if (line.matchesAny(passwordLabelOnlyPatterns)) {
                    password = lines.nextLikelyPasswordAfter(index)
                }
            }

            val normalizedLine = line.normalizeForOcrMatching()
            val lineHasPasswordLabel = passwordLabels.any { normalizedLine.contains(it) }
            val lineHasSsidLabel = ssidLabels.any { normalizedLine.contains(it) }

            if (password.isBlank() && lineHasPasswordLabel) {
                password = line.valueAfterLikelyLabel(passwordLabels).sanitizePasswordValue()
            }

            if (ssid.isBlank() && lineHasSsidLabel && !lineHasPasswordLabel) {
                ssid = line.valueAfterLikelyLabel(ssidLabels).sanitizeSsidValue()
            }

            if (password.isBlank() && ssid.isNotBlank() && lineHasSsidLabel && !lineHasPasswordLabel) {
                password = lines.nextLikelyPasswordAfter(index, requirePasswordLike = true)
            }

            if (ssid.isNotBlank() && password.isNotBlank()) break
        }

        val fallback = lines.extractFallbackCredentials()
        if (ssid.isBlank()) {
            ssid = fallback.ssid
        }
        if (password.isBlank()) {
            password = fallback.password
        }

        val cleanedSsid = ssid.sanitizeSsidValue()
        val cleanedPassword = password
            .sanitizePasswordValue()
            .stripLikelySsidPrefixFromPassword(cleanedSsid)
        return WifiOcrCredentials(
            ssid = cleanedSsid,
            password = if (cleanedSsid.isLikelySameCredentialAs(cleanedPassword)) "" else cleanedPassword,
        )
    }

    private fun parseWifiQrPayload(text: String): WifiOcrCredentials {
        val payload = text.trim()
        if (!payload.startsWith("WIFI:", ignoreCase = true)) return WifiOcrCredentials()

        val fields = parseWifiQrFields(payload.substringAfter(':'))

        return WifiOcrCredentials(
            ssid = fields["S"].orEmpty(),
            password = fields["P"].orEmpty(),
        )
    }

    private fun parseWifiQrFields(payload: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        var index = 0

        while (index < payload.length) {
            while (index < payload.length && (payload[index] == ';' || payload[index].isWhitespace())) {
                index += 1
            }
            if (index >= payload.length) break

            val separatorIndex = payload.indexOf(':', startIndex = index)
            if (separatorIndex <= index) break

            val key = payload.substring(index, separatorIndex).trim().uppercase()
            index = separatorIndex + 1

            val value = StringBuilder()
            var escaped = false
            while (index < payload.length) {
                val char = payload[index]
                index += 1

                when {
                    escaped -> {
                        value.append(char)
                        escaped = false
                    }

                    char == '\\' -> escaped = true
                    char == ';' -> break
                    else -> value.append(char)
                }
            }
            if (escaped) value.append('\\')

            if (key.isNotBlank()) {
                fields[key] = value.toString().trimWifiValue()
            }
        }

        return fields
    }

    private fun String.valueAfterLikelyLabel(labels: List<String>): String {
        val normalizedSource = normalizeForOcrMatching()
        val matchedLabel = labels
            .mapNotNull { label ->
                val index = normalizedSource.indexOf(label)
                if (index >= 0) index to label else null
            }
            .sortedWith(compareBy<Pair<Int, String>> { it.first }.thenByDescending { it.second.length })
            .firstOrNull()
            ?: return this

        val labelStart = matchedLabel.first
        val afterLabel = if (labelStart >= 0) {
            substring((labelStart + matchedLabel.second.length).coerceAtMost(length))
        } else {
            this
        }

        return afterLabel.trimStart(' ', ':', '：', '-', '=', '.', '|')
    }

    private fun String.trimWifiValue(): String {
        return trim()
            .trim('"', '\'', '`')
            .trim()
    }

    private fun String.sanitizeSsidValue(): String {
        val cleaned = inlinePasswordTailPattern.matcher(trimWifiValue())
            .replaceFirst("")
            .trim()
            .trim('|')
            .trim()
        return cleaned
            .repairLikelySsidPrefix()
            .stripLeadingOcrFieldMarker()
            .normalizeOcrAmbiguousChars(forPassword = false)
    }

    private fun String.sanitizePasswordValue(): String {
        val normalized = trimWifiValue()
            .extractAfterEmbeddedPasswordLabel()
            .trimStart(' ', ':', '：', '-', '=', '.', '|')
            .replace("^\\s*(?:wifi|wi-fi)\\s*[:ï¼=-]\\s*".toRegex(RegexOption.IGNORE_CASE), "")
            .replace("\\s{2,}".toRegex(), " ")
            .expandRepeatedPasswordPattern()
            .stripVietnameseDiacriticsForPassword()
            .repairLikelyPasswordPrefix()
            .trim('|')
            .trim()
        return normalized.collapseLikelySplitPassword()
    }

    /**
     * Fixes OCR character confusions context-aware. ML Kit (and most engines)
     * frequently misread between visually-similar glyphs:
     *   '0' <-> 'O' / 'o' / 'Q' / 'D'
     *   '1' <-> 'l' / 'I' / '|'
     *   '5' <-> 'S' / 's'
     *   '8' <-> 'B'
     *   '2' <-> 'Z' / 'z'
     *   '6' <-> 'G' / 'b'
     *   '9' <-> 'g' / 'q'
     *   'O' <-> 'O-circumflex' (Vietnamese diacritic kept by accident)
     *
     * Strategy: classify the value as DIGIT-DOMINANT (>=75% digits) or
     * LETTER-DOMINANT (>=85% letters). Only fix obvious mistakes inside the
     * dominant class; mixed-class strings (typical strong passwords) are
     * left alone to avoid corrupting valid characters.
     */
    private fun String.normalizeOcrAmbiguousChars(forPassword: Boolean): String {
        val value = this
        if (value.isBlank()) return value

        if (!forPassword) {
            return value.replace('|', 'l')
        }

        // Drop accidental Vietnamese accents that survived (Ơ -> O, Ư -> U, ...).
        // Stripping diacritics is safe for Wi-Fi passphrases because WPA/WPA2
        // passphrases are ASCII-printable.
        val asciiFolded = Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace('đ', 'd')
            .replace('Đ', 'D')

        val alnum = asciiFolded.filter { it.isLetterOrDigit() }
        if (alnum.length < 4) return asciiFolded

        val digitCount = alnum.count(Char::isDigit)
        val letterCount = alnum.count(Char::isLetter)
        val total = digitCount + letterCount
        val digitRatio = digitCount.toDouble() / total
        val letterRatio = letterCount.toDouble() / total

        val digitDominant = digitRatio >= 0.75
        val letterDominant = letterRatio >= 0.85

        val normalized = when {
            digitDominant -> asciiFolded.map { ch ->
                when (ch) {
                    'O', 'o', 'Q', 'D' -> '0'
                    'l', 'I', '|' -> '1'
                    'Z', 'z' -> '2'
                    'A' -> '4'
                    'S', 's' -> '5'
                    'G', 'b' -> '6'
                    'T' -> '7'
                    'B' -> '8'
                    'g', 'q' -> '9'
                    else -> ch
                }
            }.joinToString("")

            letterDominant && forPassword -> asciiFolded.map { ch ->
                when (ch) {
                    '0' -> 'O'
                    '1' -> 'l'
                    '5' -> 'S'
                    else -> ch
                }
            }.joinToString("")

            else -> asciiFolded
        }

        return normalized.normalizeDigitLikePasswordTail()
    }

    private fun String.repairLikelySsidPrefix(): String {
        val value = trim()
        if (value.isBlank()) return value

        val wifiNoise = Regex("(?i)^(?:wi\\s*fi|wi-fi|wifi|wif|wit)\\s+(.{3,})$")
            .matchEntire(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!wifiNoise.isNullOrBlank()) return wifiNoise

        val missingViettel = Regex("(?i)^tel\\s+(.{3,})$")
            .matchEntire(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!missingViettel.isNullOrBlank()) return "Viettel $missingViettel"

        val missingV = Regex("(?i)^iettel\\s+(.{3,})$")
            .matchEntire(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!missingV.isNullOrBlank()) return "Viettel $missingV"

        return value
    }

    private fun String.stripLeadingOcrFieldMarker(): String {
        val value = trim()
        if (value.isBlank()) return value

        return leadingOcrFieldMarkerPattern
            .matchEntire(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: value
    }

    private fun String.collapseLikelySplitPassword(): String {
        val value = trim()
        if (value.isBlank() || !value.any(Char::isWhitespace)) return value

        val parts = value.split("\\s+".toRegex()).filter { it.isNotBlank() }
        if (parts.size < 2) return value

        val joined = parts.joinToString("")
        if (joined.length in minWifiPasswordLength..maxWifiPasswordLength &&
            joined.isAsciiPrintableWifiPassphrase()
        ) {
            return joined
        }

        if (parts.all { it.all(Char::isDigit) }) {
            val sameLength = parts.distinctBy { it.length }.size == 1
            if (sameLength && parts.size == 2 && parts[0].length >= 3) {
                val distance = hammingDistanceSameLength(parts[0], parts[1])
                if (distance <= 1) return parts[0] + parts[0]
            }
            if (joined.length in minWifiPasswordLength..maxWifiPasswordLength) return joined
        }

        if (joined.length !in minWifiPasswordLength..maxWifiPasswordLength) return value
        if (!joined.isAsciiPrintableWifiPassphrase()) return value

        val looksLikeBrokenToken =
            parts.drop(1).all { it.length <= 4 } &&
                parts.first().length >= 4 &&
                parts.any { token -> token.any(Char::isDigit) || token.length <= 2 }
        val compactHasPasswordShape =
            joined.any(Char::isDigit) ||
                (parts.first().any(Char::isLetter) && parts.drop(1).all { it.length <= 3 })

        return if (looksLikeBrokenToken || compactHasPasswordShape) joined else value
    }

    private fun String.stripVietnameseDiacriticsForPassword(): String {
        return Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
    }

    private fun String.repairLikelyPasswordPrefix(): String {
        val value = trim()
        if (value.isBlank()) return value

        val repairedWifiArtifact = Regex("(?i)^w\\s*[-_]?\\s*a\\s*(?=[a-z0-9@#\\$%\\^&*._!\\-]{6,}$)")
            .replaceFirst(value, "")
            .trim()
        return repairedWifiArtifact.ifBlank { value }
    }

    private fun hammingDistanceSameLength(left: String, right: String): Int {
        if (left.length != right.length) return Int.MAX_VALUE
        var distance = 0
        for (index in left.indices) {
            if (left[index] != right[index]) distance += 1
        }
        return distance
    }

    private fun String.isAsciiPrintableWifiPassphrase(): Boolean {
        return all { it.code in 32..126 }
    }

    private fun String.expandRepeatedPasswordPattern(): String {
        val candidate = trim()
        if (candidate.isBlank()) return candidate

        for (pattern in repeatPasswordPatterns) {
            val matcher = pattern.matcher(candidate)
            if (!matcher.matches()) continue

            val token = matcher.group(1)?.trim().orEmpty()
            val repeatCount = matcher.group(2)?.toIntOrNull() ?: continue
            if (!token.isSafeRepeatToken() || repeatCount !in minRepeatCount..maxRepeatCount) {
                continue
            }

            val expanded = token.repeat(repeatCount)
            if (expanded.length > maxWifiPasswordLength) continue
            return expanded
        }

        return candidate
    }

    private fun String.isSafeRepeatToken(): Boolean {
        if (isBlank() || length > maxRepeatTokenLength) return false
        if (any { it.isWhitespace() || it == ':' || it == '：' }) return false
        return repeatTokenPattern.matcher(this).matches()
    }

    private fun String.extractFirstMatch(patterns: List<Pattern>): String {
        for (pattern in patterns) {
            val matcher = pattern.matcher(this)
            if (matcher.matches()) {
                return matcher.group(1)?.trimWifiValue().orEmpty()
            }
        }
        return ""
    }

    private fun String.matchesAny(patterns: List<Pattern>): Boolean {
        return patterns.any { it.matcher(this).matches() }
    }

    private fun List<String>.nextLikelySsidAfter(index: Int): String {
        val endExclusive = (index + 4).coerceAtMost(size)
        for (i in (index + 1) until endExclusive) {
            val candidate = this[i].sanitizeSsidValue()
            if (candidate.isBlank()) continue
            if (
                candidate.matchesAny(ssidLabelOnlyPatterns) ||
                candidate.matchesAny(passwordLabelOnlyPatterns) ||
                candidate.startsWithLikelyLabel(ssidLabels) ||
                candidate.startsWithLikelyLabel(passwordLabels)
            ) {
                continue
            }
            return candidate
        }
        return ""
    }

    private fun List<String>.nextLikelyPasswordAfter(index: Int, requirePasswordLike: Boolean = false): String {
        val endExclusive = (index + 4).coerceAtMost(size)
        var fallback = ""
        for (i in (index + 1) until endExclusive) {
            val candidate = this[i].sanitizePasswordValue()
            if (candidate.isBlank()) continue
            if (candidate.startsWithLikelyLabel(passwordLabels)) {
                val extracted = candidate.valueAfterLikelyLabel(passwordLabels).sanitizePasswordValue()
                if (extracted.isNotBlank()) {
                    return extracted
                }
            }
            if (
                candidate.matchesAny(ssidLabelOnlyPatterns) ||
                candidate.matchesAny(passwordLabelOnlyPatterns) ||
                candidate.startsWithLikelyLabel(ssidLabels) ||
                candidate.startsWithLikelyLabel(passwordLabels)
            ) {
                continue
            }
            if (candidate.looksLikePassword()) {
                return candidate
            }
            if (!requirePasswordLike && fallback.isBlank()) {
                fallback = candidate
            }
        }
        return if (requirePasswordLike) "" else fallback
    }

    private fun String.looksLikePassword(): Boolean {
        val value = sanitizePasswordValue()
        if (value.length < 4) return false
        if (value.contains(':') || value.contains('：')) return false

        val wordCount = value.split("\\s+".toRegex()).count { it.isNotBlank() }
        if (wordCount > 2) return false

        val hasDigit = value.any { it.isDigit() }
        val hasLetter = value.any { it.isLetter() }
        val hasSymbol = value.any { !it.isLetterOrDigit() && !it.isWhitespace() }
        return (hasDigit && (hasLetter || hasSymbol)) ||
            (wordCount == 1 && value.length >= 8)
    }

    private fun String.looksLikeStrongPasswordCandidate(): Boolean {
        val value = sanitizePasswordValue()
        if (value.length < 8 || value.length > 63) return false
        if (value.contains(':') || value.contains('：')) return false
        if (value.split("\\s+".toRegex()).count { it.isNotBlank() } > 2) return false
        return value.any { it.isLetterOrDigit() }
    }

    private fun String.normalizeDigitLikePasswordTail(): String {
        val tailMatch = digitLikePasswordTailPattern.find(this) ?: return this
        val tail = tailMatch.value
        if (!tail.any(Char::isDigit)) return this

        val converted = tail.map { ch ->
            when (ch) {
                'O', 'o', 'Q', 'D' -> '0'
                'l', 'I', '|' -> '1'
                'Z', 'z' -> '2'
                'A' -> '4'
                'S', 's' -> '5'
                'G', 'b' -> '6'
                'T' -> '7'
                'B' -> '8'
                'g', 'q' -> '9'
                else -> ch
            }
        }.joinToString("")

        val digitCount = converted.count(Char::isDigit)
        if (digitCount < tail.length - 1) return this
        return replaceRange(tailMatch.range, converted)
    }

    private fun String.stripLikelySsidPrefixFromPassword(knownSsid: String): String {
        val value = trimWifiValue()
            .extractAfterEmbeddedPasswordLabel()
            .trimStart(' ', ':', '：', '-', '=', '.', '|')
        if (!value.contains(':') && !value.contains('：')) return value

        val separatorMatch = credentialSeparatorPattern.find(value) ?: return value
        val prefix = value.substring(0, separatorMatch.range.first).trimWifiValue()
        val suffix = value.substring(separatorMatch.range.last + 1).sanitizePasswordValue()
        if (prefix.isBlank() || suffix.isBlank()) return value
        if (!suffix.looksLikeStrongPasswordCandidate()) return value

        val prefixToken = prefix.compactCredentialToken()
        val knownSsidToken = knownSsid.compactCredentialToken()
        val knownSsidMatchesPrefix = knownSsidToken.isNotBlank() && knownSsidToken == prefixToken
        val prefixLooksLikeCompactedSsid =
            prefix.length in 4..20 &&
                prefix.any(Char::isLetter) &&
                prefix.none(Char::isWhitespace) &&
                prefix.none { !it.isLetterOrDigit() && it !in setOf('-', '_', '.') } &&
                prefix.count(Char::isLowerCase) <= 2

        return if (knownSsidMatchesPrefix || prefixLooksLikeCompactedSsid) suffix else value
    }

    private fun String.extractAfterEmbeddedPasswordLabel(): String {
        val match = embeddedPasswordLabelPattern.findAll(this).lastOrNull() ?: return this
        return substring((match.range.last + 1).coerceAtMost(length)).trim()
    }

    private fun String.startsWithLikelyLabel(labels: List<String>): Boolean {
        val normalized = normalizeForOcrMatching().trim()
        return labels.any { normalized.startsWith(it) }
    }

    private fun String.extractCombinedInlineCredentials(): Pair<String, String>? {
        val matcher = combinedInlineCredentialPattern.matcher(this)
        if (!matcher.matches()) return null
        val extractedSsid = matcher.group(1)?.sanitizeSsidValue().orEmpty()
        val extractedPassword = matcher.group(2)?.sanitizePasswordValue().orEmpty()
        if (extractedSsid.isBlank() || extractedPassword.isBlank()) return null
        return extractedSsid to extractedPassword
    }

    private fun String.containsBothSsidAndPasswordLabels(): Boolean {
        val normalized = normalizeForOcrMatching()
        val hasSsid = ssidLabels.any { normalized.contains(it) }
        val hasPassword = passwordLabels.any { normalized.contains(it) }
        return hasSsid && hasPassword
    }

    private fun List<String>.nextSplitRowCredentialsAfter(index: Int): Pair<String, String>? {
        val endExclusive = (index + 4).coerceAtMost(size)
        for (i in (index + 1) until endExclusive) {
            val candidate = this[i].trimWifiValue()
            if (candidate.isBlank()) continue
            if (candidate.matchesAny(ssidLabelOnlyPatterns) || candidate.matchesAny(passwordLabelOnlyPatterns)) {
                continue
            }
            val pair = candidate.parseSplitCredentialsRow()
            if (pair != null) {
                return pair
            }
        }
        return null
    }

    private fun List<String>.extractFallbackCredentials(): WifiOcrCredentials {
        val usefulLines = map { it.sanitizeFallbackCandidate() }
            .filter { it.isUsefulFallbackCredentialLine() }
        if (usefulLines.isEmpty()) return WifiOcrCredentials()

        if (usefulLines.size == 1) {
            return WifiOcrCredentials(password = usefulLines.single().sanitizePasswordValue())
        }

        val scoredPasswords = usefulLines.mapIndexed { index, line ->
            IndexedCredentialScore(index, line, line.scoreFallbackPassword())
        }
        val bestPassword = scoredPasswords.maxWithOrNull(
            compareBy<IndexedCredentialScore> { it.score }
                .thenBy { it.value.length },
        )
        val password = bestPassword
            ?.takeIf { it.score >= 4 }
            ?.value
            ?.sanitizePasswordValue()
            .orEmpty()

        val passwordIndex = bestPassword?.takeIf { it.score >= 4 }?.index ?: -1
        val bestSsid = usefulLines.mapIndexedNotNull { index, line ->
            if (index == passwordIndex) {
                null
            } else {
                IndexedCredentialScore(index, line, line.scoreFallbackSsid())
            }
        }.maxWithOrNull(
            compareBy<IndexedCredentialScore> { it.score }
                .thenByDescending { if (it.index < passwordIndex || passwordIndex == -1) 1 else 0 }
                .thenBy { it.value.length },
        )

        val ssid = bestSsid
            ?.takeIf { it.score >= 3 }
            ?.value
            ?.sanitizeSsidValue()
            .orEmpty()

        val cleanedSsid = ssid.sanitizeSsidValue()
        val cleanedPassword = password.sanitizePasswordValue()
        return WifiOcrCredentials(
            ssid = cleanedSsid,
            password = if (cleanedSsid.isLikelySameCredentialAs(cleanedPassword)) "" else cleanedPassword,
        )
    }

    private fun String.sanitizeFallbackCandidate(): String {
        val cleaned = trimWifiValue()
            .replace("[•·]+".toRegex(), " ")
            .replace("\\s{2,}".toRegex(), " ")
            .trim()

        val inlinePassword = cleaned.extractFirstMatch(passwordValuePatterns)
        if (inlinePassword.isNotBlank()) return inlinePassword.sanitizePasswordValue()

        val inlineSsid = cleaned.extractFirstMatch(ssidValuePatterns + looseSsidValuePatterns)
        if (inlineSsid.isNotBlank()) return inlineSsid.sanitizeSsidValue()

        return cleaned
    }

    private fun String.isUsefulFallbackCredentialLine(): Boolean {
        val value = trimWifiValue()
        if (value.isBlank()) return false
        if (value.matchesAny(ssidLabelOnlyPatterns) || value.matchesAny(passwordLabelOnlyPatterns)) return false
        if (value.startsWithLikelyLabel(ssidLabels) && value.valueAfterLikelyLabel(ssidLabels).isBlank()) return false
        if (value.startsWithLikelyLabel(passwordLabels) && value.valueAfterLikelyLabel(passwordLabels).isBlank()) return false
        if (value.isLikelyNoiseLine()) return false
        return value.any { it.isLetterOrDigit() }
    }

    private fun String.scoreFallbackPassword(): Int {
        val value = sanitizePasswordValue()
        if (value.isBlank()) return Int.MIN_VALUE / 4

        var score = 0
        if (startsWithLikelyLabel(passwordLabels)) score += 8
        if (value.looksLikeStrongPasswordCandidate()) score += 6
        if (value.any { it.isDigit() }) score += 2
        if (value.any { it.isLetter() }) score += 1
        if (!value.any { it.isWhitespace() }) score += 2
        if (value.length in 8..32) score += 2
        if (value.length < 8 && !startsWithLikelyLabel(passwordLabels)) score -= 4
        if (value.isLikelyNoiseLine()) score -= 10
        return score
    }

    private fun String.scoreFallbackSsid(): Int {
        val value = sanitizeSsidValue()
        if (value.isBlank() || value.length > 32) return Int.MIN_VALUE / 4

        var score = 0
        if (startsWithLikelyLabel(ssidLabels)) score += 8
        if (value.any { it.isLetter() }) score += 4
        if (value.any { it.isWhitespace() }) score += 1
        if (!value.looksLikeStrongPasswordCandidate()) score += 2
        if (value.all { it.isDigit() }) score -= 4
        if (value.isLikelyNoiseLine()) score -= 10
        return score
    }

    private fun String.isLikelyNoiseLine(): Boolean {
        val normalized = normalizeForOcrMatching()
            .replace("[^a-z0-9\\s]".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
        if (normalized.isBlank()) return true
        if (normalized.contains("google com") || normalized.contains("www ")) return true
        if (normalized.contains("kinh chao") || normalized.contains("quy khach")) return true
        if (normalized == "wifi" || normalized == "wi fi" || normalized == "thong tin") return true
        if (normalized.contains("open ") || normalized.startsWith("open")) return true
        if (normalized.contains("grabfood") || normalized.contains("befood") || normalized.contains("shopeefood")) return true
        if (Regex("\\b\\d{1,3}\\s*k\\b").containsMatchIn(normalized)) return true
        if (Regex("\\b\\d{1,2}\\s*(am|pm)\\b").containsMatchIn(normalized)) return true
        return false
    }

    private fun String.parseSplitCredentialsRow(): Pair<String, String>? {
        val byPipe = split('|')
            .map { it.trimWifiValue() }
            .filter { it.isNotBlank() }
        if (byPipe.size >= 2) {
            val left = byPipe.first().sanitizeSsidValue()
            val right = byPipe.last().sanitizePasswordValue()
            if (left.isNotBlank() && right.isNotBlank() && !left.isLikelySameCredentialAs(right)) return left to right
        }

        val byWideSpace = split("\\s{2,}".toRegex())
            .map { it.trimWifiValue() }
            .filter { it.isNotBlank() }
        if (byWideSpace.size >= 2) {
            val left = byWideSpace.first().sanitizeSsidValue()
            val right = byWideSpace.last().sanitizePasswordValue()
            if (left.isNotBlank() && right.isNotBlank() && !left.isLikelySameCredentialAs(right)) return left to right
        }

        val tokenMatcher = splitTokenCredentialPattern.matcher(this)
        if (tokenMatcher.matches()) {
            val left = tokenMatcher.group(1)?.sanitizeSsidValue().orEmpty()
            val right = tokenMatcher.group(2)?.sanitizePasswordValue().orEmpty()
            if (left.isNotBlank() && right.looksLikePassword() && !left.isLikelySameCredentialAs(right)) {
                return left to right
            }
        }

        return null
    }

    private fun String.isLikelySameCredentialAs(other: String): Boolean {
        if (isBlank() || other.isBlank()) return false
        val left = compactCredentialToken()
        val right = other.compactCredentialToken()
        if (left.length < 6 || right.length < 6) return false
        if (left != right) return false

        val normalizedLeft = normalizeForOcrMatching().trim()
        val normalizedRight = other.normalizeForOcrMatching().trim()
        return normalizedLeft == normalizedRight ||
            other.any(Char::isWhitespace) ||
            !other.isAsciiPrintableWifiPassphrase()
    }

    private fun String.compactCredentialToken(): String {
        return normalizeForOcrMatching().filter(Char::isLetterOrDigit)
    }

    private fun WifiOcrCredentials.hasAnyValue(): Boolean {
        return ssid.isNotBlank() || password.isNotBlank()
    }

    private val ssidLabels = listOf(
        "wifi name",
        "wi-fi name",
        "wifi id",
        "network name",
        "name",
        "ten wifi",
        "tên wifi",
        "ten wi-fi",
        "tên wi-fi",
        "ten mang",
        "tên mạng",
        "ssid",
        // OCR commonly misreads "SSID" as "SIDD" — treat both as the same label.
        "sidd",
        "id",
        "wifi",
        "wi-fi",
        "network",
    )

    private val passwordLabels = listOf(
        "wifi password",
        "wi-fi password",
        "mat khau wifi",
        "mật khẩu wifi",
        "mat khau wi-fi",
        "mật khẩu wi-fi",
        "pass wifi",
        "wifi pass",
        "wi-fi pass",
        "mat khau",
        "mật khẩu",
        "password",
        "passcode",
        "pass",
        "pwd",
    )

    private val ssidValuePatterns = listOf(
        Pattern.compile("^\\s*(?:ssid|sidd|wifi\\s*name|network\\s*name|wifi\\s*id|name|id|ten\\s*wifi|tên\\s*wifi|ten\\s*mang|t[eê]n\\s*m[aạ]ng|ten\\s*wi-fi|tên\\s*wi-fi)\\s*[:：=-]\\s*(.+)$", Pattern.CASE_INSENSITIVE),
    )

    private val looseSsidValuePatterns = listOf(
        Pattern.compile("^\\s*(?:ssid|sidd|wifi|wi-fi|network|wifi\\s*id|name|id|ten\\s*wifi|ten\\s*mang|ten\\s*wi-fi)\\s+(?![:ï¼=\\-]|password\\b|pass\\b|pwd\\b)(.+)$", Pattern.CASE_INSENSITIVE),
    )

    private val passwordValuePatterns = listOf(
        Pattern.compile("^\\s*(?:password|pass\\s*word|pass\\s*wifi|wifi\\s*pass|wi-fi\\s*pass|pass|pwd|mat\\s*khau|m[aạ]t\\s*kh[aẩ]u|mk)\\s*[:：=-]\\s*(.+)$", Pattern.CASE_INSENSITIVE),
    )

    private val ssidLabelOnlyPatterns = listOf(
        Pattern.compile("^\\s*(?:ssid|sidd|wifi\\s*name|network\\s*name|wifi\\s*id|name|id|ten\\s*wifi|tên\\s*wifi|ten\\s*mang|t[eê]n\\s*m[aạ]ng|ten\\s*wi-fi|tên\\s*wi-fi)\\s*[:：=-]?\\s*$", Pattern.CASE_INSENSITIVE),
    )

    private val passwordLabelOnlyPatterns = listOf(
        Pattern.compile("^\\s*(?:password|pass\\s*word|pass\\s*wifi|wifi\\s*pass|wi-fi\\s*pass|pass|pwd|mat\\s*khau|m[aạ]t\\s*kh[aẩ]u|mk)\\s*[:：=-]?\\s*$", Pattern.CASE_INSENSITIVE),
    )

    private val inlinePasswordTailPattern = Pattern.compile(
        "\\s*(?:\\||\\b(?:password|pass\\s*word|pass\\s*wifi|wifi\\s*pass|wi-fi\\s*pass|pass|pwd|mat\\s*khau|m[aạ]t\\s*kh[aẩ]u|mk)\\b\\s*[:：=-]).*$",
        Pattern.CASE_INSENSITIVE,
    )

    private val combinedInlineCredentialPattern = Pattern.compile(
        "^\\s*(?:ssid|sidd|wifi\\s*name|network\\s*name|wifi\\s*id|name|id|ten\\s*wifi|tên\\s*wifi|ten\\s*mang|t[eê]n\\s*m[aạ]ng|ten\\s*wi-fi|tên\\s*wi-fi)\\s*[:：=-]\\s*(.+?)\\s*(?:\\||\\s{2,}|(?:password|pass\\s*word|pass\\s*wifi|wifi\\s*pass|wi-fi\\s*pass|pass|pwd|mat\\s*khau|m[aạ]t\\s*kh[aẩ]u|mk)\\s*[:：=-])\\s*(.+)$",
        Pattern.CASE_INSENSITIVE,
    )

    private val splitTokenCredentialPattern = Pattern.compile(
        "^(.+?)\\s+([\\p{Alnum}@#\\$%\\^&*._!\\-]{6,})$",
        Pattern.CASE_INSENSITIVE,
    )

    private val repeatTokenPattern = Pattern.compile("^[\\p{Alnum}@#\\$%\\^&._!\\-]+$")

    private val repeatPasswordPatterns = listOf(
        Pattern.compile("^([\\p{Alnum}@#\\$%\\^&._!\\-]+)\\s+(\\d{1,2})\\s*(?:lan|lần)$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^([\\p{Alnum}@#\\$%\\^&._!\\-]+)\\s+(?:lap|lặp)\\s+(\\d{1,2})\\s*(?:lan|lần)$", Pattern.CASE_INSENSITIVE),
        Pattern.compile("^([\\p{Alnum}@#\\$%\\^&._!\\-]+)\\s*(?:x|×|\\*)\\s*(\\d{1,2})$", Pattern.CASE_INSENSITIVE),
    )
    private val embeddedPasswordLabelPattern =
        Regex("(?i)(?:^|[\\s|:：;,.\\-])(?:password|pass\\s*word|pass\\s*wifi|wifi\\s*pass|wi-fi\\s*pass|pass|pwd|mat\\s*khau|m[aạ]t\\s*kh[aẩ]u|mk)\\s*[:：=\\-]")
    private val leadingOcrFieldMarkerPattern = Regex("(?i)^[a-z0-9]\\s*[:：]\\s+(.{3,})$")
    private val credentialSeparatorPattern = Regex("\\s*[:：]\\s*")
    private val digitLikePasswordTailPattern = Regex("[0-9OolI|ZzASsGgBqD]{2,}$")

    private const val minRepeatCount = 2
    private const val maxRepeatCount = 12
    private const val maxRepeatTokenLength = 16
    private const val minWifiPasswordLength = 8
    private const val maxWifiPasswordLength = 63

    private data class IndexedCredentialScore(
        val index: Int,
        val value: String,
        val score: Int,
    )
}

internal fun String.normalizeForOcrMatching(): String {
    val withoutAccent = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
    return withoutAccent.lowercase()
        .replace('0', 'o')
        .replace('1', 'i')
        .replace('|', 'i')
        .replace('5', 's')
        .replace('：', ':')
        .replace("–", "-")
        .replace("—", "-")
}
