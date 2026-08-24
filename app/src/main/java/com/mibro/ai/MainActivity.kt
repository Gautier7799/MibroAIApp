package com.mibro.ai

import android.Manifest
import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var mediaSession: MediaSessionCompat? = null
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private val isEarbudActive = mutableStateOf(false)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())

        textToSpeech = TextToSpeech(this, this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    primary = Color(0xFF00E676)
                )
            ) {
                MainScreen(
                    isEarbudClicked = isEarbudActive.value,
                    onActivateClicked = {
                        setupMediaSession()
                        Toast.makeText(this, "تم اختطاف الصوت! المس سماعتك الآن.", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun setupMediaSession() {
        if (mediaSession != null) return

        // 1. طلب السيطرة على الصوت من نظام الأندرويد
        requestAudioFocus()

        mediaSession = MediaSessionCompat(this, "MibroAISession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            
            // 2. خداع النظام بأننا "نعزف الموسيقى الآن" لكي يرسل اللمسات إلينا
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            
            setPlaybackState(stateBuilder.build())

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { handleEarbudClick() }
                override fun onPause() { handleEarbudClick() }
                override fun onMediaButtonEvent(mediaButtonEvent: android.content.Intent?): Boolean {
                    handleEarbudClick()
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
            })
            isActive = true
        }
    }

    private fun handleEarbudClick() {
        Log.d("MibroAI", "تم التقاط اللمسة!")
        
        Handler(Looper.getMainLooper()).post {
            isEarbudActive.value = true

            if (isTtsReady) {
                textToSpeech?.speak("مرحباً، أنا أستمع إليك", TextToSpeech.QUEUE_FLUSH, null, "AI_GREETING")
            } else {
                Toast.makeText(this@MainActivity, "محرك النطق غير جاهز", Toast.LENGTH_SHORT).show()
            }

            Handler(Looper.getMainLooper()).postDelayed({
                isEarbudActive.value = false
            }, 2000)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech?.setLanguage(Locale("ar"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}

@Composable
fun MainScreen(isEarbudClicked: Boolean, onActivateClicked: () -> Unit) {
    var isActive by remember { mutableStateOf(false) }
    
    val cardColor by animateColorAsState(
        targetValue = if (isEarbudClicked) Color(0xFF004D40) else MaterialTheme.colorScheme.surface,
        label = "CardColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Text(text = "Mibro الذكي", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(60.dp))

        Card(
            modifier = Modifier.fillMaxWidth().height(220.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isEarbudClicked) {
                    Text(text = "🎤 أستمع إليك الآن...", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                } else {
                    Text(
                        text = if (isActive) "جاهز! المس سماعتك الآن" else "المساعد متوقف",
                        fontSize = 20.sp,
                        color = if (isActive) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                if (!isEarbudClicked) {
                    Button(
                        onClick = {
                            isActive = true
                            onActivateClicked()
                        },
                        modifier = Modifier.height(56.dp).padding(horizontal = 32.dp)
                    ) {
                        Text(text = "تفعيل الاستماع", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
