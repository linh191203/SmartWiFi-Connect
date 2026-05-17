package com.smartwificonnect

import java.text.Normalizer
import java.util.Locale
import kotlin.math.max

internal const val SSID_AUTO_CONNECT_SCORE = 0.90
internal const val SSID_REVIEW_SCORE = 0.75

internal object WifiOcrAutoConnectPolicy {
    fun decideSsidMatch(
        ocrSsid: String,
        nearbyNetworks: List<NearbyNetwork>,
    ): SsidMatchDecision {
        val normalizedOcr = ocrSsid.normalizeForSsidMatch()
        if (normalizedOcr.isBlank() || nearbyNetworks.isEmpty()) {
            return SsidMatchDecision.NoMatchingWifiFound(null)
        }

        val exactMatches = nearbyNetworks
            .filter { it.ssid.normalizeForSsidMatch() == normalizedOcr }
            .map {
                SsidMatchCandidate(
                    network = it,
                    score = 1.0,
                    isExactNormalized = true,
                )
            }

        if (exactMatches.size == 1) {
            return SsidMatchDecision.AutoConnect(exactMatches.single())
        }
        if (exactMatches.size > 1) {
            return SsidMatchDecision.MultipleMatchesNeedSelection(exactMatches)
        }

        val fuzzyMatches = nearbyNetworks
            .map { network ->
                SsidMatchCandidate(
                    network = network,
                    score = fuzzySsidScore(normalizedOcr, network.ssid.normalizeForSsidMatch()),
                    isExactNormalized = false,
                )
            }
            .filter { it.score >= SSID_REVIEW_SCORE }
            .sortedWith(
                compareByDescending<SsidMatchCandidate> { it.score }
                    .thenByDescending { it.network.signalLevel },
            )

        val highConfidenceMatches = fuzzyMatches.filter { it.score >= SSID_AUTO_CONNECT_SCORE }
        return when {
            highConfidenceMatches.size == 1 && fuzzyMatches.size == 1 ->
                SsidMatchDecision.AutoConnect(highConfidenceMatches.single())
            fuzzyMatches.size == 1 ->
                SsidMatchDecision.SingleMatchNeedsReview(fuzzyMatches.single())
            fuzzyMatches.isNotEmpty() ->
                SsidMatchDecision.MultipleMatchesNeedSelection(fuzzyMatches)
            else ->
                SsidMatchDecision.NoMatchingWifiFound(null)
        }
    }

    fun validatePasswordForAutoConnect(
        password: String,
        securityLabel: String?,
    ): AutoConnectPasswordValidation {
        val trimmedPassword = password.trim()
        val requiresPassword = securityLabel.requiresWifiPassword()
        if (!requiresPassword) {
            return AutoConnectPasswordValidation.Valid
        }
        if (trimmedPassword.isBlank()) {
            return AutoConnectPasswordValidation.Invalid(
                "Mạng Wi-Fi này cần mật khẩu. Vui lòng kiểm tra lại mật khẩu OCR đọc được.",
            )
        }
        if (trimmedPassword.length !in 8..63) {
            return AutoConnectPasswordValidation.Invalid(
                "Mật khẩu WPA/WPA2/WPA3 phải có từ 8 đến 63 ký tự.",
            )
        }
        if (!trimmedPassword.isPrintableAsciiPassphrase()) {
            return AutoConnectPasswordValidation.Invalid(
                "Mật khẩu có ký tự không hợp lệ cho kết nối tự động. Vui lòng kiểm tra lại mật khẩu.",
            )
        }
        return AutoConnectPasswordValidation.Valid
    }
}

internal sealed class SsidMatchDecision {
    data class AutoConnect(val candidate: SsidMatchCandidate) : SsidMatchDecision()
    data class SingleMatchNeedsReview(val candidate: SsidMatchCandidate) : SsidMatchDecision()
    data class MultipleMatchesNeedSelection(val candidates: List<SsidMatchCandidate>) : SsidMatchDecision()
    data class NoMatchingWifiFound(val bestCandidate: SsidMatchCandidate?) : SsidMatchDecision()
}

internal data class SsidMatchCandidate(
    val network: NearbyNetwork,
    val score: Double,
    val isExactNormalized: Boolean,
)

internal sealed class AutoConnectPasswordValidation {
    object Valid : AutoConnectPasswordValidation()
    data class Invalid(val message: String) : AutoConnectPasswordValidation()
}

internal fun String.normalizeForSsidMatch(): String {
    val withoutMarks = Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace("\\p{Mn}+".toRegex(), "")
        .replace('đ', 'd')
        .replace('Đ', 'D')
    return withoutMarks
        .lowercase(Locale.ROOT)
        .trim()
        .replace("[^a-z0-9\\s]".toRegex(), " ")
        .replace("\\s+".toRegex(), " ")
        .trim()
}

internal fun fuzzySsidScore(
    normalizedOcrSsid: String,
    normalizedScannedSsid: String,
): Double {
    if (normalizedOcrSsid == normalizedScannedSsid) return 1.0
    if (normalizedOcrSsid.isBlank() || normalizedScannedSsid.isBlank()) return 0.0

    val levenshteinScore = similarityScore(normalizedOcrSsid, normalizedScannedSsid)
    val tokenScore = tokenDiceScore(normalizedOcrSsid, normalizedScannedSsid)
    val containmentScore = containmentScore(normalizedOcrSsid, normalizedScannedSsid)
    return maxOf(levenshteinScore, tokenScore, containmentScore)
}

private fun containmentScore(a: String, b: String): Double {
    val shorter = if (a.length <= b.length) a else b
    val longer = if (a.length <= b.length) b else a
    val shorterTokens = shorter.split(' ').filter { it.isNotBlank() }
    if (shorter.length < 8 || shorterTokens.size < 2) return 0.0
    if (!longer.contains(shorter)) return 0.0
    val coverage = shorter.length.toDouble() / longer.length.toDouble()
    return 0.90 + (coverage * 0.09)
}

private fun tokenDiceScore(a: String, b: String): Double {
    val aTokens = a.split(' ').filter { it.isNotBlank() }
    val bTokens = b.split(' ').filter { it.isNotBlank() }
    if (aTokens.isEmpty() || bTokens.isEmpty()) return 0.0
    val shared = aTokens.count { token ->
        bTokens.any { other -> token == other || token.length >= 4 && other.length >= 4 && similarityScore(token, other) >= 0.84 }
    }
    return (2.0 * shared) / (aTokens.size + bTokens.size)
}

private fun similarityScore(a: String, b: String): Double {
    if (a == b) return 1.0
    if (a.isEmpty() || b.isEmpty()) return 0.0

    val maxLen = max(a.length, b.length)
    val distance = levenshtein(a, b)
    return 1.0 - (distance.toDouble() / maxLen)
}

private fun levenshtein(a: String, b: String): Int {
    val m = a.length
    val n = b.length
    val dp = Array(m + 1) { IntArray(n + 1) }
    for (i in 0..m) dp[i][0] = i
    for (j in 0..n) dp[0][j] = j
    for (i in 1..m) {
        for (j in 1..n) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            dp[i][j] = minOf(
                dp[i - 1][j] + 1,
                dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + cost,
            )
        }
    }
    return dp[m][n]
}

private fun String?.requiresWifiPassword(): Boolean {
    val normalized = orEmpty().uppercase(Locale.ROOT)
    return when {
        normalized.isBlank() -> true
        normalized.contains("OPEN") -> false
        normalized.contains("OWE") -> false
        normalized == "KHONG CO DU LIEU" -> true
        normalized == "KHÔNG CÓ DỮ LIỆU" -> true
        else -> true
    }
}

private fun String.isPrintableAsciiPassphrase(): Boolean {
    return all { it.code in 32..126 }
}
