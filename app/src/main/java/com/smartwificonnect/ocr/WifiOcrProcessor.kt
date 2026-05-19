package com.smartwificonnect.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.util.Log
import androidx.core.graphics.createBitmap
import androidx.core.graphics.get
import androidx.core.graphics.scale
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

interface WifiOcrEngine {
    suspend fun recognize(bitmap: Bitmap): WifiOcrRecognitionResult

    fun extractWifiCredentials(text: String): WifiOcrCredentials

    fun release() = Unit
}

class WifiOcrProcessor : WifiOcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override
    suspend fun recognize(bitmap: Bitmap): WifiOcrRecognitionResult {
        val textCandidates = mutableListOf<String>()
        var firstSeenPassword: String? = null
        var passwordConfirmedTwice = false
        var stopReason = "all-passes-completed"

        Log.d(TAG, "════════════════════════════════════════════════════")
        Log.d(TAG, "▶ OCR START — bitmap=${bitmap.width}x${bitmap.height}, passes=${recognitionPasses.size}")
        Log.d(TAG, "════════════════════════════════════════════════════")

        for ((index, pass) in recognitionPasses.withIndex()) {
            val passResult = recognizeTextWithPass(bitmap, pass)

            // ── Debug: raw OCR text per pass (full, untrimmed) ──
            val rawSnippet = passResult.joinedText
                .replace('\n', '⏎')
                .take(180)
            Log.d(
                TAG,
                "  [pass $index] crop=${pass.cropRegion} variant=${pass.variant} " +
                    "autoCrop=${pass.autoCropPaper} blocks=${passResult.blockTexts.size} " +
                    "pairs=${passResult.labelValuePairs.size} → text='$rawSnippet'" +
                    if (passResult.joinedText.length > 180) "…(${passResult.joinedText.length} chars)" else "",
            )
            passResult.blockTexts.forEachIndexed { bi, b ->
                Log.d(TAG, "    block[$bi]: '${b.replace('\n', '⏎').take(120)}'")
            }
            passResult.labelValuePairs.forEachIndexed { pi, pair ->
                Log.d(TAG, "    pair[$pi]:  '${pair.replace('\n', '⏎').take(120)}'")
            }

            // Feed THREE kinds of candidates into the selector for each pass:
            //  1) the joined full-frame OCR text (legacy behaviour)
            //  2) each text block as its own candidate (geometric isolation —
            //     a SSID/password block scores higher when it's not diluted
            //     by other paragraphs in the same image)
            //  3) "Label: Value" pairs synthesised from consecutive lines in
            //     a block (label and value often appear on adjacent rows)
            fun pushCandidate(candidate: String) {
                if (candidate.isNotBlank() && candidate !in textCandidates) {
                    textCandidates += candidate
                }
            }
            pushCandidate(passResult.joinedText)
            passResult.blockTexts.forEach(::pushCandidate)
            passResult.labelValuePairs.forEach(::pushCandidate)

            val interim = WifiOcrResultSelector.selectBestResult(textCandidates)
            val pw = interim.credentials.password.takeIf { it.isNotBlank() }

            // Cross-validate password: only stop when we have BOTH ssid AND
            // the same password from at least 2 passes. Otherwise OCR can
            // confuse digits with letters (e.g. '9' -> 's', '0' -> 'o',
            // '1' -> 'l', '5' -> 'S') and we lock in a wrong password that
            // then fails real WiFi connect.
            if (interim.hasCompleteCredentials() && pw != null) {
                if (firstSeenPassword == null) {
                    firstSeenPassword = pw
                    Log.d(TAG, "  [pass $index] interim ssid='${interim.credentials.ssid}', pw seen 1×")
                } else if (firstSeenPassword == pw) {
                    passwordConfirmedTwice = true
                    Log.d(TAG, "  [pass $index] ✓ ssid+pw confirmed by 2 passes — early stop")
                }
            }

            // Stop fast: ssid + password agreed by 2+ passes.
            if (passwordConfirmedTwice) {
                stopReason = "password-confirmed-2x"
                break
            }

            // Secondary stop: high-confidence single read after enough passes.
            if (index >= minimumPassesBeforeEarlyStop &&
                interim.hasHighConfidenceCompleteCredentials()
            ) {
                stopReason = "high-confidence-after-${index + 1}-passes"
                Log.d(TAG, "  [pass $index] ✓ high-confidence complete — early stop")
                break
            }
        }

        val finalResult = WifiOcrResultSelector.selectBestResult(textCandidates)

        // ── Debug: final selected SSID/password + reason ──
        Log.d(TAG, "────────────────────────────────────────────────────")
        Log.d(TAG, "▶ OCR FINAL")
        Log.d(TAG, "  stop reason: $stopReason")
        Log.d(TAG, "  candidates analysed: ${textCandidates.size}")
        Log.d(TAG, "  selected SSID:     '${finalResult.credentials.ssid}'")
        Log.d(TAG, "  selected password: '${finalResult.credentials.password}' (len=${finalResult.credentials.password.length})")
        Log.d(TAG, "  confidence: ${finalResult.confidence}")
        Log.d(
            TAG,
            "  raw winning text (debug only): '${finalResult.text.replace('\n', '⏎').take(220)}'" +
                if (finalResult.text.length > 220) "…" else "",
        )
        Log.d(TAG, "════════════════════════════════════════════════════")

        return finalResult
    }

    suspend fun recognizeText(bitmap: Bitmap): String = recognize(bitmap).text

    override
    fun extractWifiCredentials(text: String): WifiOcrCredentials = WifiOcrTextParser.extractWifiCredentials(text)

    override fun release() {
        recognizer.close()
    }

    private suspend fun recognizeTextWithPass(
        bitmap: Bitmap,
        pass: RecognitionPass,
    ): PassRecognitionResult {
        val ownedBitmaps = mutableListOf<Bitmap>()
        var workingBitmap = bitmap

        workingBitmap = workingBitmap.crop(pass.cropRegion).also { cropped ->
            if (cropped !== bitmap) ownedBitmaps += cropped
        }
        workingBitmap = workingBitmap.rotate(pass.rotationDegrees).also { rotated ->
            if (rotated !== ownedBitmaps.lastOrNull() && rotated !== bitmap) ownedBitmaps += rotated
        }
        if (pass.autoCropPaper) {
            workingBitmap = workingBitmap.cropLikelyPaperSurface().also { cropped ->
                if (cropped !== ownedBitmaps.lastOrNull() && cropped !== bitmap) ownedBitmaps += cropped
            }
        }
        workingBitmap = workingBitmap.resizeForRecognition().also { resized ->
            if (resized !== ownedBitmaps.lastOrNull() && resized !== bitmap) ownedBitmaps += resized
        }
        workingBitmap = workingBitmap.prepareForRecognition(pass.variant).also { prepared ->
            if (prepared !== ownedBitmaps.lastOrNull() && prepared !== bitmap) ownedBitmaps += prepared
        }

        return try {
            val image = InputImage.fromBitmap(workingBitmap, 0)
            val result = recognizer.process(image).await()

            // Per-block extraction: each Text.TextBlock is a geometrically
            // independent paragraph on the image. When the photo contains
            // many unrelated paragraphs (menus, contracts, notes, etc.) the
            // joined `result.text` mixes everything together and confuses
            // the parser. Feeding each block as its own candidate to the
            // selector allows us to score the block that ONLY contains
            // SSID + password highest, ignoring noise blocks completely.
            val blockTexts = result.textBlocks
                .mapNotNull { block ->
                    val text = block.text?.trim()
                    if (text.isNullOrBlank()) null else text
                }

            // Pair-of-lines candidate: in many printed notes the layout is
            //   "WiFi"        ←  first line of a block (label only)
            //   "MyHomeAP"    ←  second line of the same block (value only)
            // ML Kit returns these as separate TextLine elements. We
            // synthesize a "Label: Value" string for each consecutive pair
            // inside every block — this gives the parser a strong inline
            // form even when the user took the photo with label and value
            // on separate visual rows.
            val labelValuePairs = result.textBlocks
                .flatMap { block ->
                    val lines = block.lines.mapNotNull { it.text?.trim()?.takeIf { t -> t.isNotBlank() } }
                    if (lines.size < 2) emptyList()
                    else lines.zipWithNext { a, b -> "$a: $b" }
                }

            PassRecognitionResult(
                joinedText = result.text.trim(),
                blockTexts = blockTexts,
                labelValuePairs = labelValuePairs,
            )
        } finally {
            ownedBitmaps.asReversed().forEach { owned ->
                if (!owned.isRecycled) {
                    owned.recycle()
                }
            }
        }
    }

    private fun Bitmap.rotate(degrees: Float): Bitmap {
        if (abs(degrees) < 0.01f) return this
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun Bitmap.prepareForRecognition(variant: RecognitionVariant): Bitmap {
        return when (variant) {
            RecognitionVariant.ORIGINAL -> this
            RecognitionVariant.GRAYSCALE -> applyColorMatrix(grayscaleMatrix)
            RecognitionVariant.HIGH_CONTRAST -> applyColorMatrix(highContrastMatrix)
            RecognitionVariant.AUTO_CONTRAST -> applyAutoContrast(sharpen = false)
            RecognitionVariant.SHARPENED_AUTO_CONTRAST -> applyAutoContrast(sharpen = true)
            RecognitionVariant.TEXT_THRESHOLD -> applyOtsuThreshold(invert = false)
            RecognitionVariant.INVERTED_THRESHOLD -> applyOtsuThreshold(invert = true)
        }
    }

    private fun Bitmap.crop(region: CropRegion): Bitmap {
        if (region == CropRegion.FULL) return this

        val left = (width * region.left).roundToInt().coerceIn(0, width - 1)
        val top = (height * region.top).roundToInt().coerceIn(0, height - 1)
        val right = (width * region.right).roundToInt().coerceIn(left + 1, width)
        val bottom = (height * region.bottom).roundToInt().coerceIn(top + 1, height)
        return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
    }

    private fun Bitmap.resizeForRecognition(): Bitmap {
        val longestSide = max(width, height)
        val scale = when {
            longestSide > maxRecognitionSide -> maxRecognitionSide.toFloat() / longestSide
            longestSide < minRecognitionSide -> minRecognitionSide.toFloat() / longestSide
            else -> 1f
        }
        if (abs(scale - 1f) < 0.01f) return this

        val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
        val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)
        return scale(targetWidth, targetHeight)
    }

    private fun Bitmap.cropLikelyPaperSurface(): Bitmap {
        if (width < 64 || height < 64) return this

        val step = (max(width, height) / 160).coerceAtLeast(1)
        var luminanceSum = 0L
        var sampleCount = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                luminanceSum += this[x, y].toLuminance()
                sampleCount += 1
                x += step
            }
            y += step
        }
        if (sampleCount == 0) return this

        val average = (luminanceSum / sampleCount).toInt()
        val threshold = max(145, min(230, average + 24))
        var minX = width
        var minY = height
        var maxX = 0
        var maxY = 0
        var brightSamples = 0

        y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                if (this[x, y].toLuminance() >= threshold) {
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                    brightSamples += 1
                }
                x += step
            }
            y += step
        }

        if (brightSamples < sampleCount * 0.04f) return this

        val sampledWidth = maxX - minX
        val sampledHeight = maxY - minY
        if (sampledWidth <= 0 || sampledHeight <= 0) return this
        if (sampledWidth > width * 0.92f && sampledHeight > height * 0.92f) return this

        val padX = (sampledWidth * 0.08f).roundToInt()
        val padY = (sampledHeight * 0.08f).roundToInt()
        val left = (minX - padX).coerceIn(0, width - 1)
        val top = (minY - padY).coerceIn(0, height - 1)
        val right = (maxX + padX).coerceIn(left + 1, width)
        val bottom = (maxY + padY).coerceIn(top + 1, height)

        return Bitmap.createBitmap(this, left, top, right - left, bottom - top)
    }

    private fun Bitmap.applyColorMatrix(colorMatrix: ColorMatrix): Bitmap {
        val output = createBitmap(width, height)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix)
        }
        canvas.drawBitmap(this, 0f, 0f, paint)
        return output
    }

    private fun Bitmap.applyOtsuThreshold(invert: Boolean): Bitmap {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)

        val histogram = IntArray(256)
        val luminance = IntArray(pixels.size)
        pixels.forEachIndexed { index, pixel ->
            val gray = pixel.toLuminance()
            luminance[index] = gray
            histogram[gray] += 1
        }

        val threshold = otsuThreshold(histogram, pixels.size)
        val outputPixels = IntArray(pixels.size)
        luminance.forEachIndexed { index, gray ->
            val value = if (gray > threshold) 255 else 0
            val finalValue = if (invert) 255 - value else value
            outputPixels[index] = Color.rgb(finalValue, finalValue, finalValue)
        }

        return createBitmap(width, height).apply {
            setPixels(outputPixels, 0, width, 0, 0, width, height)
        }
    }

    private fun Bitmap.applyAutoContrast(sharpen: Boolean): Bitmap {
        val pixels = IntArray(width * height)
        getPixels(pixels, 0, width, 0, 0, width, height)

        val luminance = IntArray(pixels.size)
        val histogram = IntArray(256)
        pixels.forEachIndexed { index, pixel ->
            val gray = pixel.toLuminance()
            luminance[index] = gray
            histogram[gray] += 1
        }

        val low = percentileFromHistogram(histogram, pixels.size, 0.02f)
        val high = percentileFromHistogram(histogram, pixels.size, 0.98f)
        val span = (high - low).coerceAtLeast(1)
        val normalized = IntArray(luminance.size) { index ->
            (((luminance[index] - low) * 255f) / span)
                .roundToInt()
                .coerceIn(0, 255)
        }

        val outputPixels = IntArray(pixels.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val gray = if (sharpen && x in 1 until width - 1 && y in 1 until height - 1) {
                    val center = normalized[index] * 5
                    val left = normalized[index - 1]
                    val right = normalized[index + 1]
                    val top = normalized[index - width]
                    val bottom = normalized[index + width]
                    (center - left - right - top - bottom).coerceIn(0, 255)
                } else {
                    normalized[index]
                }
                outputPixels[index] = Color.rgb(gray, gray, gray)
            }
        }

        return createBitmap(width, height).apply {
            setPixels(outputPixels, 0, width, 0, 0, width, height)
        }
    }

    private fun percentileFromHistogram(
        histogram: IntArray,
        totalPixels: Int,
        percentile: Float,
    ): Int {
        val target = (totalPixels * percentile).roundToInt().coerceIn(0, totalPixels)
        var cumulative = 0
        for (i in histogram.indices) {
            cumulative += histogram[i]
            if (cumulative >= target) return i
        }
        return histogram.lastIndex
    }

    private fun Int.toLuminance(): Int {
        val red = Color.red(this)
        val green = Color.green(this)
        val blue = Color.blue(this)
        return ((red * 299) + (green * 587) + (blue * 114)) / 1000
    }

    private fun otsuThreshold(histogram: IntArray, totalPixels: Int): Int {
        var sum = 0L
        for (i in histogram.indices) {
            sum += i.toLong() * histogram[i]
        }

        var backgroundWeight = 0L
        var backgroundSum = 0L
        var maxVariance = -1.0
        var threshold = 128

        for (i in histogram.indices) {
            backgroundWeight += histogram[i].toLong()
            if (backgroundWeight == 0L) continue

            val foregroundWeight = totalPixels - backgroundWeight
            if (foregroundWeight == 0L) break

            backgroundSum += i.toLong() * histogram[i]
            val backgroundMean = backgroundSum.toDouble() / backgroundWeight
            val foregroundMean = (sum - backgroundSum).toDouble() / foregroundWeight
            val variance = backgroundWeight.toDouble() *
                foregroundWeight.toDouble() *
                (backgroundMean - foregroundMean) *
                (backgroundMean - foregroundMean)

            if (variance > maxVariance) {
                maxVariance = variance
                threshold = i
            }
        }

        return threshold
    }

    companion object {
        private const val TAG = "WifiOcrProcessor"
        private const val maxRecognitionSide = 1200
        private const val minRecognitionSide = 720
        private const val minimumPassesBeforeEarlyStop = 3

        // Tuned for fast path on real devices. Heavy passes (rotations, niche
        // crop regions) were removed because:
        //   1) early-stop in recognize() exits as soon as we have ssid+password,
        //      so most users never reach the tail of this list anyway.
        //   2) on weaker phones each pass costs 1-2s — running 14 passes
        //      blocked OCR for 30+ seconds.
        //
        // Order matters: the FIRST pass should target the center / clearest
        // text region. Most printed WiFi notes have SSID + password near the
        // middle, so we start with CENTER+sharpened-auto-contrast. The
        // full-frame ORIGINAL pass is kept second as a safety net.
        private val recognitionPasses = listOf(
            RecognitionPass(cropRegion = CropRegion.CENTER, variant = RecognitionVariant.SHARPENED_AUTO_CONTRAST),
            RecognitionPass(variant = RecognitionVariant.ORIGINAL),
            RecognitionPass(autoCropPaper = true, variant = RecognitionVariant.HIGH_CONTRAST),
            RecognitionPass(autoCropPaper = true, variant = RecognitionVariant.SHARPENED_AUTO_CONTRAST),
            RecognitionPass(cropRegion = CropRegion.LOWER, variant = RecognitionVariant.HIGH_CONTRAST),
            RecognitionPass(autoCropPaper = true, variant = RecognitionVariant.TEXT_THRESHOLD),
        )

        private val grayscaleMatrix = ColorMatrix().apply { setSaturation(0f) }
        private val highContrastMatrix = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1.35f, 0f, 0f, 0f, -22f,
                        0f, 1.35f, 0f, 0f, -22f,
                        0f, 0f, 1.35f, 0f, -22f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
    }
}

private enum class RecognitionVariant {
    ORIGINAL,
    GRAYSCALE,
    HIGH_CONTRAST,
    AUTO_CONTRAST,
    SHARPENED_AUTO_CONTRAST,
    TEXT_THRESHOLD,
    INVERTED_THRESHOLD,
}

private enum class CropRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    FULL(0f, 0f, 1f, 1f),
    CENTER(0.05f, 0.15f, 0.95f, 0.9f),
    LOWER(0f, 0.38f, 1f, 1f),
    BOTTOM_RIGHT(0.28f, 0.45f, 1f, 1f),
}

private data class RecognitionPass(
    val rotationDegrees: Float = 0f,
    val cropRegion: CropRegion = CropRegion.FULL,
    val autoCropPaper: Boolean = false,
    val variant: RecognitionVariant,
)

private data class PassRecognitionResult(
    val joinedText: String,
    val blockTexts: List<String>,
    val labelValuePairs: List<String>,
)

private fun WifiOcrRecognitionResult.hasCompleteCredentials(): Boolean {
    return credentials.ssid.isNotBlank() && credentials.password.isNotBlank()
}

private fun WifiOcrRecognitionResult.hasHighConfidenceCompleteCredentials(): Boolean {
    return credentials.ssid.isNotBlank() &&
        credentials.password.isNotBlank() &&
        (confidence ?: 0.0) >= 0.90
}

data class WifiOcrCredentials(
    val ssid: String = "",
    val password: String = "",
)
