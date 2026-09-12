package de.gabriel.nearping.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import de.gabriel.nearping.AppViewModel
import de.gabriel.nearping.logic.CoordinationSignal
import de.gabriel.nearping.model.AppScreen
import de.gabriel.nearping.model.AppUiState
import de.gabriel.nearping.model.PeerIndicator
import de.gabriel.nearping.model.PeerUiModel
import de.gabriel.nearping.transport.TransportKind

@Composable
fun NearPingApp(viewModel: AppViewModel) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.openCamera()
        else viewModel.reportError("Ohne Kamerazugriff kann kein aktuelles Foto aufgenommen werden.")
    }

    val sessionPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        if (hasAllSessionPermissions(context)) {
            viewModel.startSession()
        } else {
            viewModel.reportError(
                "Nicht alle für Hintergrundbenachrichtigungen, Nearby und das lokale WLAN benötigten Berechtigungen wurden erteilt.",
            )
        }
    }

    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.clearError()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (state.screen) {
                AppScreen.SETUP -> SetupScreen(
                    state = state,
                    onNameChanged = viewModel::setEnteredName,
                    onOpenCamera = {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CAMERA,
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            viewModel.openCamera()
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    onStart = {
                        if (hasAllSessionPermissions(context)) {
                            viewModel.startSession()
                        } else {
                            sessionPermissionLauncher.launch(requiredSessionPermissions())
                        }
                    },
                )

                AppScreen.CAMERA -> {
                    BackHandler(onBack = viewModel::cancelCamera)
                    CameraCapture(
                        onCancel = viewModel::cancelCamera,
                        onPhotoCaptured = viewModel::acceptSelfie,
                        onError = viewModel::reportError,
                    )
                }

                AppScreen.NEARBY -> NearbyScreen(
                    state = state,
                    onPeerSelected = viewModel::selectPeer,
                    onStop = viewModel::stopSession,
                )

                AppScreen.PROFILE -> {
                    BackHandler(onBack = viewModel::closePeer)
                    ProfileScreen(
                        peer = state.selectedPeer,
                        onBack = viewModel::closePeer,
                        onPing = viewModel::pingSelectedPeer,
                        onSignal = viewModel::sendCoordinationSignal,
                    )
                }
            }
        }
    }

}

@Composable
private fun SetupScreen(
    state: AppUiState,
    onNameChanged: (String) -> Unit,
    onOpenCamera: () -> Unit,
    onStart: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item { Spacer(Modifier.height(10.dp)) }
        item {
            Text(
                text = "NearPing",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item {
            Text(
                text = "Entdecke Menschen über Nearby – je nach Umgebung bis zu etwa 100 m – und zusätzlich im selben lokalen WLAN.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            if (state.selfieJpeg == null) {
                Box(
                    modifier = Modifier
                        .size(210.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Noch kein\nSitzungsfoto",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                JpegImage(
                    jpegBytes = state.selfieJpeg,
                    modifier = Modifier.size(210.dp).clip(CircleShape),
                )
            }
        }
        item {
            OutlinedButton(onClick = onOpenCamera) {
                Text(if (state.selfieJpeg == null) "Foto aufnehmen" else "Foto neu aufnehmen")
            }
        }
        item {
            OutlinedTextField(
                value = state.enteredName,
                onValueChange = onNameChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                supportingText = { Text("Wird nur während dieser Sitzung angezeigt") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        }
        item {
            Button(
                onClick = onStart,
                enabled = state.selfieJpeg != null && state.enteredName.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Sitzung starten")
            }
        }
        item {
            Text(
                text = "Name und Sitzungsfoto werden mit verbundenen NearPing-Geräten geteilt. Nach dem Start bleibt die Sitzung mit einer dauerhaften Systembenachrichtigung im Hintergrund aktiv. Beenden oder ein Prozessende verwirft Foto, Personenliste, Pings und Signale.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun NearbyScreen(
    state: AppUiState,
    onPeerSelected: (String) -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Menschen in deiner Funkumgebung",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Über Nearby oder dasselbe lokale WLAN",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onStop) { Text("Beenden") }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(state.sessionStatus, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(16.dp))
        if (state.peers.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Noch keine andere NearPing-App verbunden",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.peers, key = PeerUiModel::endpointId) { peer ->
                    PeerRow(peer = peer, onClick = { onPeerSelected(peer.endpointId) })
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

@Composable
private fun PeerRow(peer: PeerUiModel, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IndicatorDot(peer.indicator)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(peer.displayName, fontWeight = FontWeight.SemiBold)
                Text(
                    text = peer.transportLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("Ansehen", color = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun PeerUiModel.transportLabel(): String = when (transportKinds) {
    setOf(TransportKind.NEARBY, TransportKind.LAN) -> "Über Nearby und lokales WLAN verbunden"
    setOf(TransportKind.LAN) -> "Direkt über lokales WLAN verbunden"
    else -> "Direkt über Nearby verbunden"
}

@Composable
private fun ProfileScreen(
    peer: PeerUiModel?,
    onBack: () -> Unit,
    onPing: () -> Unit,
    onSignal: (CoordinationSignal) -> Unit,
) {
    if (peer == null) return
    var signalDialog by remember(peer.endpointId) {
        mutableStateOf<SignalDialogMode?>(null)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onBack) { Text("Zurück") }
            Spacer(Modifier.weight(1f))
            IndicatorDot(peer.indicator)
        }

        Spacer(Modifier.height(12.dp))
        if (peer.photoJpeg == null) {
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("Foto wird direkt angefragt …")
                }
            }
        } else {
            JpegImage(
                jpegBytes = peer.photoJpeg,
                modifier = Modifier.size(260.dp).clip(RoundedCornerShape(28.dp)),
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = peer.displayName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "In deiner Funkumgebung",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        val explanation = when {
            peer.localPinged && peer.remotePinged ->
                "Ihr habt euch gegenseitig gepingt. Der Kreis ist jetzt grün."
            peer.remotePinged ->
                "Diese Person hat dich gepingt. Wenn du möchtest, kannst du den Ping erwidern."
            peer.localPinged ->
                "Dein Ping wurde gesendet. Bei einem gegenseitigen Ping wird der Kreis grün."
            else ->
                "Mit einem Ping zeigst du ausschließlich Interesse an einem Gespräch vor Ort."
        }
        Text(explanation, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))

        Button(
            onClick = onPing,
            enabled = !peer.localPinged,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(
                when {
                    peer.localPinged && peer.remotePinged -> "Gegenseitiger Ping"
                    peer.localPinged -> "Ping gesendet"
                    peer.remotePinged -> "Ping erwidern"
                    else -> "Ping senden"
                },
            )
        }

        if (peer.localPinged && peer.remotePinged) {
            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Kurze Absprache",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Nur vorgegebene Signale – kein freier Chat. So bleibt die Begegnung vor Ort im Mittelpunkt.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            if (peer.coordinationEvents.isEmpty()) {
                Text(
                    text = "Noch kein Signal gesendet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                peer.coordinationEvents.forEach { event ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (event.outgoing) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                        ),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(
                                text = if (event.outgoing) "Du" else peer.displayName,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(event.text)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { signalDialog = SignalDialogMode.QUICK },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Signal senden")
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    signalDialog?.let { mode ->
        CoordinationSignalDialog(
            mode = mode,
            onModeChanged = { signalDialog = it },
            onDismiss = { signalDialog = null },
            onSend = { signal ->
                onSignal(signal)
                signalDialog = null
            },
        )
    }
}

private enum class SignalDialogMode {
    QUICK,
    FLOOR,
    MEETING,
}

@Composable
private fun CoordinationSignalDialog(
    mode: SignalDialogMode,
    onModeChanged: (SignalDialogMode) -> Unit,
    onDismiss: () -> Unit,
    onSend: (CoordinationSignal) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (mode) {
                    SignalDialogMode.QUICK -> "Signal auswählen"
                    SignalDialogMode.FLOOR -> "Stockwerk auswählen"
                    SignalDialogMode.MEETING -> "Treffpunkt auswählen"
                },
            )
        },
        text = {
            when (mode) {
                SignalDialogMode.QUICK -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CoordinationSignal.quickChoices.forEach { choice ->
                        OutlinedButton(
                            onClick = { onSend(choice.signal) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(choice.label)
                        }
                    }
                    OutlinedButton(
                        onClick = { onModeChanged(SignalDialogMode.FLOOR) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Ich bin im Stockwerk …")
                    }
                    OutlinedButton(
                        onClick = { onModeChanged(SignalDialogMode.MEETING) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Treffen wir uns …")
                    }
                }

                SignalDialogMode.FLOOR -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                ) {
                    items(CoordinationSignal.floorChoices) { choice ->
                        TextButton(
                            onClick = { onSend(choice.signal) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(choice.label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                SignalDialogMode.MEETING -> LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                ) {
                    items(CoordinationSignal.meetingChoices) { choice ->
                        TextButton(
                            onClick = { onSend(choice.signal) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(choice.label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        },
    )
}

@Composable
private fun IndicatorDot(indicator: PeerIndicator) {
    if (indicator == PeerIndicator.NONE) {
        Spacer(Modifier.size(14.dp))
        return
    }
    val color = when (indicator) {
        PeerIndicator.RED -> Color(0xFFD43C3C)
        PeerIndicator.GREEN -> Color(0xFF168A5B)
        PeerIndicator.NONE -> Color.Transparent
    }
    Box(Modifier.size(14.dp).clip(CircleShape).background(color))
}

@Composable
private fun JpegImage(jpegBytes: ByteArray, modifier: Modifier) {
    val image = remember(jpegBytes) {
        BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)?.asImageBitmap()
    }
    if (image == null) {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    } else {
        Image(
            bitmap = image,
            contentDescription = "Sitzungsfoto",
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
    }
}

private fun requiredSessionPermissions(): Array<String> = buildList {
    add(Manifest.permission.ACCESS_COARSE_LOCATION)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.BLUETOOTH_SCAN)
    add(Manifest.permission.BLUETOOTH_CONNECT)
    add(Manifest.permission.BLUETOOTH_ADVERTISE)
    add(Manifest.permission.NEARBY_WIFI_DEVICES)
    add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

private fun hasAllSessionPermissions(context: Context): Boolean =
    requiredSessionPermissions().all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
