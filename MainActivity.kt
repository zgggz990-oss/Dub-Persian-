package com.example.persiandub

import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { DubScreen() } } }
    }
}

@Composable
fun DubScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pipeline = remember { Pipeline(context) }
    val player = remember { ExoPlayer.Builder(context).build() }
    var status by remember { mutableStateOf("یک ویدیوی انگلیسی انتخاب کنید") }
    var segments by remember { mutableStateOf<List<Segment>>(emptyList()) }
    var current by remember { mutableIntStateOf(-1) }
    var ttsReady by remember { mutableStateOf(false) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }

    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { code ->
            if (code == TextToSpeech.SUCCESS) {
                val r = tts?.setLanguage(Locale("fa"))
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                if (!ttsReady) status = "صدای فارسی روی گوشی نصب نیست (تنظیمات ← تبدیل متن به گفتار)"
            }
        }
        tts = engine
        onDispose { engine.shutdown(); player.release() }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        player.setMediaItem(MediaItem.fromUri(uri)); player.prepare()
        segments = emptyList(); current = -1
        scope.launch {
            try {
                segments = pipeline.run(uri) { status = it }
                status = "آماده! پخش را بزنید"
            } catch (e: Exception) {
                status = "خطا: ${e.message}"
            }
        }
    }

    // Live dubbing: speak each Persian segment when playback reaches it.
    LaunchedEffect(segments, ttsReady) {
        player.volume = 0.15f // duck the original English audio
        while (true) {
            if (player.isPlaying && segments.isNotEmpty() && ttsReady) {
                val pos = player.currentPosition
                val idx = segments.indexOfLast { it.startMs <= pos && pos < it.endMs + 500 }
                if (idx >= 0 && idx != current) {
                    current = idx
                    tts?.setSpeechRate(1.1f)
                    tts?.speak(segments[idx].fa, TextToSpeech.QUEUE_FLUSH, null, "seg$idx")
                }
            }
            delay(100)
        }
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { picker.launch("video/*") }) { Text("انتخاب ویدیو") }
        Text(status)
        AndroidView(
            factory = { PlayerView(it).apply { this.player = player } },
            modifier = Modifier.fillMaxWidth().height(220.dp)
        )
        LazyColumn {
            itemsIndexed(segments) { i, s ->
                Text(
                    s.fa,
                    style = if (i == current) MaterialTheme.typography.titleMedium
                            else MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
