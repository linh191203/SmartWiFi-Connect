package com.example.smartwificonnect.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.text.Normalizer
import java.util.regex.Pattern

class WifiOcrProcessor {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognizeText(bitmap: Bitmap): String {
        val image = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(image).await()
        return result.text.trim()
    }

    fun extractWifiCredentials(text: String): WifiOcrCredentials {
        val qrCredentials = parseWifiQrPayload(text)
        if (qrCredentials.hasAnyValue()) return qrCredentials

        val lines = text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        var ssid = ""
        var password = ""

        for ((index, line) in lines.withIndex()) {
            if (ssid.isBlank()) {
                val inlineSsid = line.extractFirstMatch(ssidValuePatterns)
                if (inlineSsid.isNotBlank()) {
                    ssid = inlineSsid
                } else if (line.matchesAny(ssidLabelOnlyPatterns)) {
                    ssid = lines.nextLikelyValueAfter(index).trimWifiValue()
                }
            }

            if (password.isBlank()) {
                val inlinePassword = line.extractFirstMatch(passwordValuePatterns)
                if (inlinePassword.isNotBlank()) {
                    password = inlinePassword
                } else if (line.matchesAny(passwordLabelOnlyPatterns)) {
                    password = lines.nextLikelyValueAfter(index).trimWifiValue()
                }
            }

            if (ssid.isNotBlank() && password.isNotBlank()) break

            val normalizedLine = line.normalizeForOcrMatching()
            if (password.isBlank() && passwordLabels.any { normalizedLine.contains(it) }) {
                password = line.valueAfterLikelyLabel(passwordLabels).trimWifiValue()
            }
            if (ssid.isBlank() && ssidLabels.any { normalizedLine.contains(it) } &&
                passwordLabels.none { normalizedLine.contains(it) }
            ) {
                ssid = line.valueAfterLikelyLabel(ssidLabels).trimWifiValue()
            }
        }

        return WifiOcrCredentials(
            ssid = ssid,
            password = password,
        )
    }

    fun release() {
        recognizer.close()
    }

    private fun parseWifiQrPayload(text: String): WifiOcrCredentials {
        val payload = text.trim()
        if (!payload.startsWith("WIFI:", ignoreCase = true)) return WifiOcrCredentials()

        val fields = payload
            .substringAfter(':')
            .trimEnd(';')
            .split(';')
            .mapNotNull { part ->
                val separatorIndex = part.indexOf(':')
                if (separatorIndex <= 0) {
                    null
                } else {
                    part.substring(0, separatorIndex).uppercase() to
                        part.substring(separatorIndex + 1).trimWifiValue()
                }
            }
            .toMap()

        return WifiOcrCredentials(
            ssid = fields["S"].orEmpty(),
            password = fields["P"].orEmpty(),
        )
    }

    private fun String.valueAfterLikelyLabel(labels: List<String>): String {
        val normalizedSource = normalizeForOcrMatching()
        val matchedLabel = labels
            .filter { normalizedSource.contains(it) }
            .maxByOrNull { it.length }
            ?: return this

        val labelStart = normalizedSource.indexOf(matchedLabel)
        val afterLabel = if (labelStart >= 0) {
            substring((labelStart + matchedLabel.length).coerceAtMost(length))
        } else {
            this
        }

        return afterLabel.trimStart(' ', ':', '-', '=', '.', '|')
            .ifBlank { substringAfter(':', this).trim() }
    }

    private fun String.trimWifiValue(): String {
        return trim()
            .trim('"', '\'', '`')
            .trim()
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

    private fun List<String>.nextLikelyValueAfter(index: Int): String {
        val endExclusive = (index + 4).coerceAtMost(size)
        for (i in (index + 1) until endExclusive) {
            val candidate = this[i].trimWifiValue()
            if (candidate.isBlank()) continue
            if (candidate.matchesAny(ssidLabelOnlyPatterns) || candidate.matchesAny(passwordLabelOnlyPatterns)) {
                continue
            }
            return candidate
        }
        return ""
    }

    private fun String.normalizeForOcrMatching(): String {
        val withoutAccent = Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
        return withoutAccent.lowercase()
            .replace("–", "-")
            .replace("—", "-")
    }

    private fun WifiOcrCredentials.hasAnyValue(): Boolean {
        return ssid.isNotBlank() || password.isNotBlank()
    }

    companion object {
        private val ssidLabels = listOf(
            "wifi name",
            "wi-fi name",
            "wifi id",
            "network name",
            "ten wifi",
            "tên wifi",
            "ten wi-fi",
            "tên wi-fi",
            "ten mang",
            "tên mạng",
            "ssid",
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
            "mat khau",
            "mật khẩu",
            "password",
            "passcode",
            "pass",
            "pwd",
        )

        private val ssidValuePatterns = listOf(
            Pattern.compile("^\\s*(?:ssid|wifi\\s*name|network\\s*name|wifi\\s*id|id|ten\\s*wifi|tên\\s*wifi|ten\\s*mang|t[eê]n\\s*m[aạ]ng|ten\\s*wi-fi|tên\\s*wi-fi)\\s*[:=-]\\s*(.+)$", Pattern.CASE_INSENSITIVE),
        )

        private val passwordValuePatterns = listOf(
            Pattern.compile("^\\s*(?:password|pass\\s*word|pass|pwd|mat\\s*khau|m[aạ]t\\s*kh[aẩ]u|mk)\\s*[:=-]\\s*(.+)$", Pattern.CASE_INSENSITIVE),
        )

        private val ssidLabelOnlyPatterns = listOf(
            Pattern.compile("^\\s*(?:ssid|wifi\\s*name|network\\s*name|wifi\\s*id|id|ten\\s*wifi|tên\\s*wifi|ten\\s*mang|t[eê]n\\s*m[aạ]ng|ten\\s*wi-fi|tên\\s*wi-fi)\\s*[:=-]?\\s*$", Pattern.CASE_INSENSITIVE),
        )

        private val passwordLabelOnlyPatterns = listOf(
            Pattern.compile("^\\s*(?:password|pass\\s*word|pass|pwd|mat\\s*khau|m[aạ]t\\s*kh[aẩ]u|mk)\\s*[:=-]?\\s*$", Pattern.CASE_INSENSITIVE),
        )
    }
}

data class WifiOcrCredentials(
    val ssid: String = "",
    val password: String = "",
)
