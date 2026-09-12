package de.gabriel.nearping.ui

import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import de.gabriel.nearping.media.SelfieProcessor
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CameraCapture(
    onCancel: () -> Unit,
    onPhotoCaptured: (ByteArray) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val coroutineScope = rememberCoroutineScope()
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var busy by remember { mutableStateOf(false) }

    DisposableEffect(previewView, lifecycleOwner) {
        val view = previewView
        if (view == null) {
            onDispose { }
        } else {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            val mainExecutor = ContextCompat.getMainExecutor(context)
            providerFuture.addListener({
                runCatching {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    val selector = if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                    imageCapture.targetRotation = view.display?.rotation ?: Surface.ROTATION_0
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = view.surfaceProvider
                    }
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
                }.onFailure {
                    onError("Die Kamera konnte nicht geöffnet werden.")
                }
            }, mainExecutor)

            onDispose {
                cameraProvider?.unbindAll()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Das Foto gilt nur für diese Sitzung.",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onCancel, enabled = !busy) {
                    Text("Abbrechen", color = Color.White)
                }
                Button(
                    onClick = {
                        if (busy) return@Button
                        busy = true
                        val file = File.createTempFile("nearping-selfie-", ".jpg", context.cacheDir)
                        val options = ImageCapture.OutputFileOptions.Builder(file).build()
                        imageCapture.takePicture(
                            options,
                            cameraExecutor,
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(
                                    outputFileResults: ImageCapture.OutputFileResults,
                                ) {
                                    coroutineScope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            runCatching { SelfieProcessor.createWireThumbnail(file) }
                                                .also { file.delete() }
                                        }
                                        busy = false
                                        result.onSuccess(onPhotoCaptured)
                                            .onFailure { onError("Das Foto konnte nicht verarbeitet werden.") }
                                    }
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    file.delete()
                                    coroutineScope.launch {
                                        busy = false
                                        onError("Das Foto konnte nicht aufgenommen werden.")
                                    }
                                }
                            },
                        )
                    },
                    enabled = !busy && cameraProvider != null,
                    shape = CircleShape,
                    modifier = Modifier.size(72.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(26.dp),
                            color = Color.White,
                            strokeWidth = 3.dp,
                        )
                    } else {
                        Text("Foto")
                    }
                }
            }
        }
    }
}
