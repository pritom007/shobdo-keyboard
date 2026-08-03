package com.shohojakkhor.keyboard.voice

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shohojakkhor.keyboard.voice.capture.MicPermission

/**
 * Standalone voice screen (M3A). Lets a user record Bengali speech and see the
 * transcript — without involving the IME yet. The design is intentionally
 * oversized and unambiguous for elderly users:
 *
 *   - One giant 160dp mic button. Tap to start, tap to stop. No hold gestures.
 *   - While recording it pulses red and shows a live timer in Bengali.
 *   - A separate "বাতিল করুন" button is always visible while recording, so a
 *     mistaken recording is one tap to discard — never committed by accident.
 *   - Every state has a single, plain Bengali sentence. No jargon, no codes.
 *   - Errors offer a single "আবার চেষ্টা করুন" retry button.
 */
class VoiceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShohojakkhorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    VoiceScreen(
                        onBack = ::finish,
                        onOpenAppSettings = ::openAppSettings,
                    )
                }
            }
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun ShohojakkhorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(), content = content)
}

@Composable
internal fun VoiceScreen(
    onBack: () -> Unit,
    onOpenAppSettings: () -> Unit,
    viewModel: VoiceViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onPermissionResult(granted) }

    val requestStart: () -> Unit = {
        if (MicPermission.isGranted(context)) {
            viewModel.startRecording()
        } else {
            permissionLauncher.launch(MicPermission.PERMISSION)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TopBar(onBack = onBack)

        Text(
            text = "শুনুন",
            fontSize = 32.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "কথা বলুন, লেখা হয়ে যাবে।",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(8.dp))

        // The big mic button is the single primary control.
        MicButton(
            state = state,
            onStart = requestStart,
            onStop = viewModel::stopRecording,
        )

        // The state-specific controls + content below the mic.
        when (val s = state) {
            VoiceUiState.Idle -> {
                Text(
                    text = "শুনতে চাপ দিন",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            is VoiceUiState.Recording -> RecordingControls(
                elapsedMs = s.elapsedMs,
                onStop = viewModel::stopRecording,
                onCancel = viewModel::cancelRecording,
            )
            VoiceUiState.Processing -> Text(
                text = "লিখছি…",
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
            )
            is VoiceUiState.Done -> TranscriptCard(
                text = s.text,
                onAgain = viewModel::reset,
            )
            is VoiceUiState.Error -> ErrorCard(
                message = s.messageBn,
                onRetry = viewModel::reset,
            )
            is VoiceUiState.PermissionDenied -> PermissionCard(
                message = s.messageBn,
                onOpenSettings = onOpenAppSettings,
            )
        }
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    OutlinedButton(
        onClick = onBack,
        modifier = Modifier
            .height(48.dp)
            .padding(start = 0.dp),
    ) {
        Text("ফিরে যান", fontSize = 18.sp)
    }
}

@Composable
private fun MicButton(
    state: VoiceUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val recording = state is VoiceUiState.Recording
    val processing = state is VoiceUiState.Processing

    // Pulse while recording.
    val pulse = rememberInfiniteTransition(label = "mic-pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse-alpha",
    )
    val pulseScale by pulse.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse-scale",
    )

    val containerColor = when {
        recording -> Color(0xFFD32F2F)
        processing -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val iconColor = if (recording) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    val modifier = Modifier
        .size(160.dp)
        .clip(CircleShape)
        .background(containerColor)
        .then(if (recording) Modifier.alpha(pulseAlpha).scale(pulseScale) else Modifier)
        .then(
            if (processing) Modifier
            else Modifier.clickable { if (recording) onStop() else onStart() },
        )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        when {
            processing -> CircularProgressIndicator(
                color = iconColor,
                strokeWidth = 6.dp,
                modifier = Modifier.size(64.dp),
            )
            else -> MicIcon(color = iconColor)
        }
    }
}

@Composable
private fun RecordingControls(
    elapsedMs: Long,
    onStop: () -> Unit,
    onCancel: () -> Unit,
) {
    Text(
        text = "শুনছি…",
        fontSize = 26.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFFD32F2F),
    )
    Text(
        text = formatTimer(elapsedMs),
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
    )
    Button(
        onClick = onStop,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
    ) {
        Text("থামুন", fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
    OutlinedButton(
        onClick = onCancel,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
    ) {
        Text("বাতিল করুন", fontSize = 20.sp)
    }
}

@Composable
private fun TranscriptCard(text: String, onAgain: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = text,
                fontSize = 22.sp,
                lineHeight = 32.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onAgain,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        Text("আবার বলুন", fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Text(
        text = message,
        fontSize = 20.sp,
        color = Color(0xFFC62828),
        textAlign = TextAlign.Center,
    )
    Button(
        onClick = onRetry,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        Text("আবার চেষ্টা করুন", fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun PermissionCard(message: String, onOpenSettings: () -> Unit) {
    Text(
        text = message,
        fontSize = 20.sp,
        textAlign = TextAlign.Center,
    )
    Button(
        onClick = onOpenSettings,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
    ) {
        Text("সেটিংস খুলুন", fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}

/** A clean microphone glyph drawn with Canvas — no icon dependency. */
@Composable
private fun MicIcon(color: Color, modifier: Modifier = Modifier.size(72.dp)) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val bodyW = w * 0.32f
        val bodyH = h * 0.42f
        val cx = w / 2f
        val bodyTop = h * 0.18f
        drawRoundRect(
            color = color,
            topLeft = Offset(cx - bodyW / 2f, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(bodyW / 2f, bodyW / 2f),
        )
        val cradleR = h * 0.20f
        val cradleCx = cx
        val cradleCy = bodyTop + bodyH * 0.55f
        drawArc(
            color = color,
            topLeft = Offset(cradleCx - cradleR, cradleCy),
            size = Size(cradleR * 2, cradleR * 2),
            startAngle = 25f,
            sweepAngle = 130f,
            useCenter = false,
            style = Stroke(width = w * 0.06f),
        )
        val stemTop = cradleCy + cradleR * 0.95f
        drawLine(
            color = color,
            start = Offset(cx, stemTop),
            end = Offset(cx, stemTop + h * 0.10f),
            strokeWidth = w * 0.06f,
        )
        val baseY = stemTop + h * 0.10f
        drawLine(
            color = color,
            start = Offset(cx - w * 0.20f, baseY),
            end = Offset(cx + w * 0.20f, baseY),
            strokeWidth = w * 0.06f,
        )
    }
}

private fun formatTimer(elapsedMs: Long): String {
    val totalSeconds = (elapsedMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun VoiceScreenIdlePreview() {
    ShohojakkhorTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            VoiceScreen(onBack = {}, onOpenAppSettings = {})
        }
    }
}
