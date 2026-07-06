package gopesh.percinel.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Inline dictation mic. Uses [SpeechRecognizer] directly (no system dialog) so we can render our
 * own quiet UI: a thin pulsing ring around the mic while it's listening. Recognized text is
 * delivered to [onText]. Requires the RECORD_AUDIO runtime permission.
 */
@Composable
fun DictationButton(onText: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }

    val recognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }
    DisposableEffect(Unit) {
        onDispose { recognizer?.destroy() }
    }

    fun start() {
        val rec = recognizer ?: run {
            Toast.makeText(context, "Voice input isn't available on this device", Toast.LENGTH_SHORT).show()
            return
        }
        rec.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(error: Int) { listening = false }
            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim()
                if (!text.isNullOrEmpty()) onText(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        listening = true
        rec.startListening(intent)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) start() else {
            Toast.makeText(context, "Microphone permission is needed to dictate", Toast.LENGTH_SHORT).show()
        }
    }

    fun toggle() {
        if (listening) {
            recognizer?.stopListening()
            listening = false
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) start() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Box(modifier.size(48.dp), contentAlignment = Alignment.Center) {
        if (listening) {
            val transition = rememberInfiniteTransition(label = "mic")
            val ringScale by transition.animateFloat(
                initialValue = 0.7f,
                targetValue = 1.5f,
                animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
                label = "scale",
            )
            val ringAlpha by transition.animateFloat(
                initialValue = 0.55f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Restart),
                label = "alpha",
            )
            Box(
                Modifier
                    .size(28.dp)
                    .scale(ringScale)
                    .alpha(ringAlpha)
                    .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
        IconButton(onClick = { toggle() }) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = if (listening) "Stop dictation" else "Dictate",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
