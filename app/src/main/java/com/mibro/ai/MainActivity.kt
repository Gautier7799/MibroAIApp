package com.mibro.ai

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            Toast.makeText(this, "صلاحيات الميكروفون ممنوحة", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // طلب الصلاحيات
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())

        // تجهيز محرك النطق
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
                    onActivateClicked = {
                        setupMediaSession()
                        Toast.makeText(this, "الاستماع مفعل! اضغط زر السماعة الآن.", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    // هنا السحر: اختطاف السماعة داخل الواجهة نفسها
    private fun setupMediaSession() {
        if (mediaSession != null) return

        mediaSession = MediaSessionCompat(this, "MibroAISession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE)
            setPlaybackState(stateBuilder.build())

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    handleEarbudClick()
                }
                override fun onPause() {
                    handleEarbudClick()
                }
            })
            isActive = true
        }
    }

    private fun handleEarbudClick() {
        Log.d("MibroAI", "تم ضغط السماعة!")
        if (isTtsReady) {
            textToSpeech?.speak("مرحباً، أنا أستمع إليك", TextToSpeech.QUEUE_FLUSH, null, "AI_GREETING")
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
fun MainScreen(onActivateClicked: () -> Unit) {
    var isActive by remember { mutableStateOf(false) }

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
            modifier = Modifier.fillMaxWidth().height(200.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isActive) "جاهز! جرب ضغط سماعتك" else "المساعد متوقف",
                    fontSize = 20.sp,
                    color = if (isActive) MaterialTheme.colorScheme.primary else Color.White
                )
                Spacer(modifier = Modifier.height(24.dp))
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
