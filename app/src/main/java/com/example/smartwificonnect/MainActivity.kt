package com.example.smartwificonnect

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.smartwificonnect.databinding.ActivityMainBinding
import com.example.smartwificonnect.ocr.WifiOcrProcessor
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private val ocrProcessor = WifiOcrProcessor()

    private var renderingState = false

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap == null) {
            viewModel.consumeRecognizedText("")
            return@registerForActivityResult
        }

        lifecycleScope.launch {
            binding.progress.isVisible = true
            runCatching { ocrProcessor.recognizeText(bitmap) }
                .onSuccess { text ->
                    viewModel.consumeRecognizedText(text)
                }
                .onFailure {
                    viewModel.consumeRecognizedText("")
                    toast(getString(R.string.toast_ocr_failed, it.message.orEmpty()))
                }
            binding.progress.isVisible = viewModel.state.value.isLoading
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupActions()
        observeState()
    }

    override fun onDestroy() {
        ocrProcessor.release()
        super.onDestroy()
    }

    private fun setupActions() {
        binding.btnCheckApi.setOnClickListener {
            viewModel.checkHealth()
        }

        binding.btnCapture.setOnClickListener {
            cameraLauncher.launch(null)
        }

        binding.btnParse.setOnClickListener {
            viewModel.parseCurrentText()
        }

        binding.btnCopyPassword.setOnClickListener {
            val password = binding.edtPassword.text?.toString().orEmpty().trim()
            if (password.isEmpty()) {
                toast(getString(R.string.toast_password_empty))
            } else {
                copyText(password)
                toast(getString(R.string.toast_password_copied))
            }
        }

        binding.edtBaseUrl.doAfterTextChanged {
            if (!renderingState) viewModel.onBaseUrlChanged(it?.toString().orEmpty())
        }

        binding.edtOcrText.doAfterTextChanged {
            if (!renderingState) viewModel.onOcrTextChanged(it?.toString().orEmpty())
        }

        binding.edtSsid.doAfterTextChanged {
            if (!renderingState) viewModel.onSsidChanged(it?.toString().orEmpty())
        }

        binding.edtPassword.doAfterTextChanged {
            if (!renderingState) viewModel.onPasswordChanged(it?.toString().orEmpty())
        }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.state.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: MainUiState) {
        renderingState = true

        if (binding.edtBaseUrl.text?.toString() != state.baseUrl) {
            binding.edtBaseUrl.setText(state.baseUrl)
            binding.edtBaseUrl.setSelection(binding.edtBaseUrl.text?.length ?: 0)
        }

        if (binding.edtOcrText.text?.toString() != state.ocrText) {
            binding.edtOcrText.setText(state.ocrText)
            binding.edtOcrText.setSelection(binding.edtOcrText.text?.length ?: 0)
        }

        if (binding.edtSsid.text?.toString() != state.ssid) {
            binding.edtSsid.setText(state.ssid)
        }

        if (binding.edtPassword.text?.toString() != state.password) {
            binding.edtPassword.setText(state.password)
        }

        binding.tvStatus.text = state.statusMessage
        binding.tvSource.text = if (state.sourceFormat.isBlank()) {
            getString(R.string.status_source_empty)
        } else {
            getString(R.string.status_source_format, state.sourceFormat)
        }

        binding.tvConfidence.text = state.confidence?.let {
            getString(R.string.status_confidence_value, (it * 100).toInt())
        } ?: getString(R.string.status_confidence_empty)

        binding.progress.isVisible = state.isLoading

        renderingState = false
    }

    private fun copyText(value: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("wifi-password", value)
        clipboard.setPrimaryClip(clip)
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
