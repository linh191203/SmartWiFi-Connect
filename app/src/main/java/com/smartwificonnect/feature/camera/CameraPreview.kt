package com.smartwificonnect.feature.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.concurrent.Executors

class CameraCaptureController {
    @Volatile
    private var captureAction: (((Bitmap?) -> Unit) -> Unit)? = null

    internal fun bind(action: ((Bitmap?) -> Unit) -> Unit) {
        captureAction = action
    }

    internal fun clear() {
        captureAction = null
    }

    fun takePhoto(onResult: (Bitmap?) -> Unit) {
        captureAction?.invoke(onResult) ?: onResult(null)
    }
}

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    onPreviewReady: (PreviewView) -> Unit = {},
    analyzer: ImageAnalysis.Analyzer? = null,
    captureController: CameraCaptureController? = null,
) {
    if (LocalInspectionMode.current) {
        Box(
            modifier = modifier.background(Color(0xFF565656)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Xem trước camera", color = Color.White)
        }
        return
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(previewView) {
        onPreviewReady(previewView)
    }

    DisposableEffect(lifecycleOwner, previewView, analyzer, captureController) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        var isDisposed = false
        var previewUseCase: Preview? = null
        var analysisUseCase: ImageAnalysis? = null
        var imageCaptureUseCase: ImageCapture? = null

        fun bindCamera() {
            if (isDisposed) return
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            val imageAnalysis = analyzer?.let {
                ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply { setAnalyzer(analysisExecutor, it) }
            }
            val imageCapture = captureController?.let {
                ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
            }

            cameraProvider.unbindAll()
            val useCases = listOfNotNull(preview, imageAnalysis, imageCapture).toTypedArray()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                *useCases,
            )
            previewUseCase = preview
            analysisUseCase = imageAnalysis
            imageCaptureUseCase = imageCapture
        }

        val listener = Runnable {
            bindCamera()
        }

        cameraProviderFuture.addListener(listener, executor)
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (cameraProviderFuture.isDone) {
                    bindCamera()
                } else {
                    cameraProviderFuture.addListener({ bindCamera() }, executor)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        captureController?.bind { onResult ->
            val capture = imageCaptureUseCase
            if (capture == null || isDisposed) {
                onResult(null)
                return@bind
            }

            val cacheFile = runCatching {
                File.createTempFile("ocr-capture-", ".jpg", context.cacheDir)
            }.getOrNull()
            if (cacheFile == null) {
                onResult(null)
                return@bind
            }

            capture.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
            val outputOptions = ImageCapture.OutputFileOptions.Builder(cacheFile).build()
            capture.takePicture(
                outputOptions,
                analysisExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val bitmap = runCatching { cacheFile.decodeBitmapRespectingExif() }.getOrNull()
                        cacheFile.delete()
                        executor.execute { onResult(bitmap) }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        cacheFile.delete()
                        executor.execute { onResult(null) }
                    }
                },
            )
        }

        onDispose {
            isDisposed = true
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            captureController?.clear()
            runCatching {
                if (cameraProviderFuture.isDone) {
                    analysisUseCase?.clearAnalyzer()
                    val boundUseCases: List<UseCase> = listOfNotNull(
                        previewUseCase,
                        analysisUseCase,
                        imageCaptureUseCase,
                    )
                    if (boundUseCases.isNotEmpty()) {
                        cameraProviderFuture.get().unbind(*boundUseCases.toTypedArray())
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

private fun File.decodeBitmapRespectingExif(): Bitmap? {
    val decoded = BitmapFactory.decodeFile(absolutePath) ?: return null
    val orientation = runCatching {
        ExifInterface(absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    val rotationDegrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (rotationDegrees == 0f) return decoded

    val matrix = Matrix().apply { postRotate(rotationDegrees) }
    return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
        if (it !== decoded) {
            decoded.recycle()
        }
    }
}
