package com.smartwificonnect.ocr

data class WifiOcrRecognitionResult(
    val text: String = "",
    val credentials: WifiOcrCredentials = WifiOcrCredentials(),
    val confidence: Double? = null,
)

internal object WifiOcrResultSelector {
    fun selectBestResult(texts: List<String>): WifiOcrRecognitionResult {
        val candidates = texts
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .map(::evaluate)

        if (candidates.isEmpty()) {
            return WifiOcrRecognitionResult()
        }

        val bestOverall = candidates.maxByOrNull { it.overallScore }
            ?: return WifiOcrRecognitionResult()

        val mergedCredentials = mergeBestCredentials(candidates, bestOverall)

        return WifiOcrRecognitionResult(
            text = bestOverall.text,
            credentials = mergedCredentials,
            confidence = buildConfidence(candidates, bestOverall, mergedCredentials),
        )
    }

    private fun mergeBestCredentials(
        candidates: List<EvaluatedCandidate>,
        bestOverall: EvaluatedCandidate,
    ): WifiOcrCredentials {
        val selectedSsid = chooseBestFieldValue(
            candidates = candidates,
            bestOverallValue = bestOverall.credentials.ssid,
            valueOf = { it.credentials.ssid },
            scoreOf = { it.ssidScore },
            explicitLabelOf = { it.hasExplicitSsidLabel },
        )

        val selectedPassword = chooseBestFieldValue(
            candidates = candidates,
            bestOverallValue = bestOverall.credentials.password,
            valueOf = { it.credentials.password },
            scoreOf = { it.passwordScore },
            explicitLabelOf = { it.hasExplicitPasswordLabel },
            relationTarget = selectedSsid.compactComparableToken(),
        )

        return WifiOcrCredentials(
            ssid = selectedSsid,
            password = selectedPassword,
        )
    }

    private fun chooseBestFieldValue(
        candidates: List<EvaluatedCandidate>,
        bestOverallValue: String,
        valueOf: (EvaluatedCandidate) -> String,
        scoreOf: (EvaluatedCandidate) -> Int,
        explicitLabelOf: (EvaluatedCandidate) -> Boolean,
        relationTarget: String = "",
    ): String {
        val groups = candidates
            .mapNotNull { candidate ->
                val value = valueOf(candidate).trim()
                if (value.isBlank()) {
                    null
                } else {
                    val hasExplicitLabel = explicitLabelOf(candidate)
                    FieldEvidence(
                        value = value,
                        score = scoreOf(candidate),
                        hasExplicitLabel = hasExplicitLabel,
                        fieldSpecificBonus = candidate.fieldSpecificBonus(
                            fieldValue = value,
                            hasExplicitLabel = hasExplicitLabel,
                        ),
                    )
                }
            }
            .groupBy(FieldEvidence::value)

        if (groups.isEmpty()) return bestOverallValue

        return groups.entries
            .map { (value, evidence) ->
                val maxScore = evidence.maxOf { it.score }
                val totalRawScore = evidence.sumOf { it.score }
                val explicitLabelHits = evidence.count { it.hasExplicitLabel }
                val fieldSpecificBonus = evidence.sumOf { it.fieldSpecificBonus }
                FieldValueScore(
                    value = value,
                    aggregateScore = maxScore +
                        (evidence.size * 24) +
                        (explicitLabelHits * 16) +
                        fieldSpecificBonus +
                        value.relationBonus(relationTarget, explicitLabelHits),
                    maxScore = maxScore,
                    totalRawScore = totalRawScore,
                    occurrenceCount = evidence.size,
                    explicitLabelHits = explicitLabelHits,
                    fieldSpecificBonus = fieldSpecificBonus,
                    isBestOverallValue = value == bestOverallValue,
                )
            }
            .maxWithOrNull(
                compareBy<FieldValueScore> { it.aggregateScore }
                    .thenBy { it.maxScore }
                    .thenBy { it.totalRawScore }
                    .thenBy { it.explicitLabelHits }
                    .thenBy { it.fieldSpecificBonus }
                    .thenBy { it.occurrenceCount }
                    .thenBy { if (it.isBestOverallValue) 1 else 0 },
            )
            ?.value
            .orEmpty()
    }

    private fun evaluate(text: String): EvaluatedCandidate {
        val credentials = WifiOcrTextParser.extractWifiCredentials(text)
        val normalized = text.normalizeForOcrMatching()
        val keywordHits = wifiKeywordHints.count { normalized.contains(it) }
        val hasExplicitSsidLabel = ssidLabelHints.any { normalized.contains(it) }
        val hasExplicitPasswordLabel = passwordLabelHints.any { normalized.contains(it) }
        val nonBlankLineCount = text.lineSequence().count { it.isNotBlank() }
        val ssidScore = scoreSsid(credentials.ssid, normalized)
        val passwordScore = scorePassword(credentials.password, normalized)

        var overallScore = text.length.coerceAtMost(220)
        if (ssidScore > 0) overallScore += ssidScore
        if (passwordScore > 0) overallScore += passwordScore
        if (credentials.ssid.isNotBlank() && credentials.password.isNotBlank()) {
            overallScore += 350
        }
        overallScore += keywordHits * 80

        return EvaluatedCandidate(
            text = text,
            credentials = credentials,
            overallScore = overallScore,
            ssidScore = ssidScore,
            passwordScore = passwordScore,
            keywordHits = keywordHits,
            hasExplicitSsidLabel = hasExplicitSsidLabel,
            hasExplicitPasswordLabel = hasExplicitPasswordLabel,
            nonBlankLineCount = nonBlankLineCount,
        )
    }

    private fun buildConfidence(
        candidates: List<EvaluatedCandidate>,
        bestOverall: EvaluatedCandidate,
        mergedCredentials: WifiOcrCredentials,
    ): Double? {
        if (mergedCredentials.ssid.isBlank() && mergedCredentials.password.isBlank()) {
            return null
        }

        val ssidSource = candidates
            .filter { it.credentials.ssid == mergedCredentials.ssid && mergedCredentials.ssid.isNotBlank() }
            .maxByOrNull { it.ssidScore }
        val passwordSource = candidates
            .filter { it.credentials.password == mergedCredentials.password && mergedCredentials.password.isNotBlank() }
            .maxByOrNull { it.passwordScore }
        val sameCandidateHasBoth = candidates.any {
            it.credentials.ssid == mergedCredentials.ssid &&
                it.credentials.password == mergedCredentials.password &&
                mergedCredentials.ssid.isNotBlank() &&
                mergedCredentials.password.isNotBlank()
        }

        var confidence = 0.08
        if (mergedCredentials.ssid.isNotBlank()) confidence += 0.22
        if (mergedCredentials.password.isNotBlank()) confidence += 0.24
        confidence += minOf(bestOverall.keywordHits, 3) * 0.04
        if (ssidSource?.hasExplicitSsidLabel == true) confidence += 0.08
        if (passwordSource?.hasExplicitPasswordLabel == true) confidence += 0.10

        if (mergedCredentials.ssid.isNotBlank() && mergedCredentials.password.isNotBlank()) {
            confidence += if (sameCandidateHasBoth) 0.16 else -0.05
        }

        val ssidCompact = mergedCredentials.ssid.compactComparableToken()
        val passwordCompact = mergedCredentials.password.compactComparableToken()
        if (ssidCompact.length >= 6 && passwordCompact.length >= 6) {
            val similarity = similarityScore(ssidCompact, passwordCompact)
            when {
                ssidCompact == passwordCompact -> {
                    confidence -= if (passwordSource?.hasExplicitPasswordLabel == true &&
                        !mergedCredentials.password.any(Char::isWhitespace)
                    ) {
                        0.06
                    } else {
                        0.34
                    }
                }
                similarity >= 0.88 -> confidence -= 0.16
            }
        }

        if (
            mergedCredentials.ssid.isNotBlank() &&
            mergedCredentials.password.isNotBlank() &&
            ssidSource?.hasExplicitSsidLabel != true &&
            passwordSource?.hasExplicitPasswordLabel != true
        ) {
            confidence = confidence.coerceAtMost(0.82)
        }

        if (mergedCredentials.ssid.isBlank() && mergedCredentials.password.isNotBlank()) {
            if (passwordSource?.nonBlankLineCount == 1) {
                confidence += 0.42
            }
            if (mergedCredentials.password.isStablePasswordToken()) {
                confidence += 0.14
            }
        }

        return confidence.coerceIn(0.0, 0.99)
    }

    private fun scoreSsid(ssid: String, normalizedText: String): Int {
        if (ssid.isBlank()) return Int.MIN_VALUE / 4

        var score = ssid.length.coerceAtMost(48) * 4
        if (normalizedText.contains("wifi") ||
            normalizedText.contains("ssid") ||
            normalizedText.contains("name") ||
            normalizedText.contains("network") ||
            normalizedText.contains("id")
        ) {
            score += 90
        }
        if (ssid.any { it.isLetterOrDigit() }) score += 30
        if (ssid.any { it.isLetter() }) score += 30

        return score
    }

    private fun scorePassword(password: String, normalizedText: String): Int {
        if (password.isBlank()) return Int.MIN_VALUE / 4

        var score = password.length.coerceAtMost(48) * 4
        if (normalizedText.contains("password") ||
            normalizedText.contains("pass") ||
            normalizedText.contains("pwd") ||
            normalizedText.contains("mat khau")
        ) {
            score += 110
        }
        if (password.any { it.isDigit() }) score += 40
        if (password.any { it.isLetter() }) score += 40
        if (password.any { !it.isLetterOrDigit() && !it.isWhitespace() }) score += 25
        if (!password.contains(' ')) score += 25
        if (password.length in 6..32) score += 25

        return score
    }

    private fun String.relationBonus(relationTarget: String, explicitLabelHits: Int): Int {
        if (relationTarget.isBlank() || explicitLabelHits == 0) return 0

        val compactValue = compactComparableToken()
        if (compactValue.length < 6 || relationTarget.length < 6) return 0
        return 0
    }

    private fun EvaluatedCandidate.fieldSpecificBonus(
        fieldValue: String,
        hasExplicitLabel: Boolean,
    ): Int {
        if (!hasExplicitLabel || fieldValue.isBlank()) return 0

        val extractedFieldCount = listOf(credentials.ssid, credentials.password)
            .count { it.isNotBlank() }
        if (extractedFieldCount != 1) return 0

        val nonBlankLineCount = text.lineSequence().count { it.isNotBlank() }
        return when {
            nonBlankLineCount <= 1 -> 8
            nonBlankLineCount == 2 -> 4
            else -> 0
        }
    }

    private fun String.compactComparableToken(): String {
        return normalizeForOcrMatching().filter(Char::isLetterOrDigit)
    }

    private fun String.isStablePasswordToken(): Boolean {
        val value = trim()
        if (value.length !in 8..63) return false
        if (value.count { it.isWhitespace() } > 1) return false
        return value.any { it.isLetterOrDigit() }
    }

    private fun similarityScore(a: String, b: String): Double {
        if (a == b) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0

        val maxLen = maxOf(a.length, b.length)
        val distance = levenshtein(a, b)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    private fun levenshtein(a: String, b: String): Int {
        val rows = a.length
        val cols = b.length
        val dp = Array(rows + 1) { IntArray(cols + 1) }

        for (row in 0..rows) dp[row][0] = row
        for (col in 0..cols) dp[0][col] = col

        for (row in 1..rows) {
            for (col in 1..cols) {
                val substitutionCost = if (a[row - 1] == b[col - 1]) 0 else 1
                dp[row][col] = minOf(
                    dp[row - 1][col] + 1,
                    dp[row][col - 1] + 1,
                    dp[row - 1][col - 1] + substitutionCost,
                )
            }
        }

        return dp[rows][cols]
    }

    private data class EvaluatedCandidate(
        val text: String,
        val credentials: WifiOcrCredentials,
        val overallScore: Int,
        val ssidScore: Int,
        val passwordScore: Int,
        val keywordHits: Int,
        val hasExplicitSsidLabel: Boolean,
        val hasExplicitPasswordLabel: Boolean,
        val nonBlankLineCount: Int,
    )

    private data class FieldEvidence(
        val value: String,
        val score: Int,
        val hasExplicitLabel: Boolean,
        val fieldSpecificBonus: Int,
    )

    private data class FieldValueScore(
        val value: String,
        val aggregateScore: Int,
        val maxScore: Int,
        val totalRawScore: Int,
        val occurrenceCount: Int,
        val explicitLabelHits: Int,
        val fieldSpecificBonus: Int,
        val isBestOverallValue: Boolean,
    )

    private val wifiKeywordHints = listOf(
        "wifi",
        "wi-fi",
        "ssid",
        "password",
        "mat khau",
        "ten wifi",
        "ten mang",
        "network",
        "name",
        "id",
    )

    private val ssidLabelHints = listOf(
        "wifi",
        "wi-fi",
        "ssid",
        "name",
        "network",
        "id",
    )

    private val passwordLabelHints = listOf(
        "password",
        "pass",
        "pwd",
        "mat khau",
    )
}
