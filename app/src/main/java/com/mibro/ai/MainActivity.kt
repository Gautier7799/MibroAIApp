package com.mibro.ai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var mediaSession: MediaSessionCompat? = null
    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isTtsReady = false
    
    private val isEarbudActive = mutableStateOf(false)
    private val recognizedText = mutableStateOf("اضغط للتحدث...")
    private val aiResponseText = mutableStateOf("")
    private val isThinking = mutableStateOf(false)
    
    private var apiKey = mutableStateOf("")

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("MibroPrefs", Context.MODE_PRIVATE)
        apiKey.value = prefs.getString("gemini_key", "") ?: ""

        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.INTERNET)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())

        textToSpeech = TextToSpeech(this, this)
        setupSpeechRecognizer()

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
                    recognizedText = recognizedText.value,
                    aiResponse = aiResponseText.value,
                    isThinking = isThinking.value,
                    apiKey = apiKey.value,
                    onApiKeyChanged = { newKey ->
                        // التأكد من إزالة أي مسافات فارغة بالخطأ عند النسخ
                        val cleanKey = newKey.trim()
                        apiKey.value = cleanKey
                        prefs.edit().putString("gemini_key", cleanKey).apply()
                    },
                    onActivateClicked = {
                        setupMediaSession()
                        Toast.makeText(this, "النظام مفعل! المس سماعتك وتحدث.", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                isEarbudActive.value = false
                Log.e("MibroAI", "Speech error: $error")
            }

            override fun onResults(results: Bundle?) {
                isEarbudActive.value = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    recognizedText.value = text
                    askGemini(text) 
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun askGemini(userText: String) {
        if (apiKey.value.isEmpty()) {
            aiResponseText.value = "يرجى إدخال مفتاح API في التطبيق أولاً!"
            return
        }

        isThinking.value = true
        aiResponseText.value = "جاري التفكير..."

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val generativeModel = GenerativeModel(
                    modelName = "gemini-1.5-flash",
                    apiKey = apiKey.value
                )
                
                val prompt = "أنت مساعد صوتي ذكي. أجب باللغة العربية بشكل مختصر جداً ومفيد لتتم قراءته صوتياً. المستخدم يقول: $userText"
                
                val response = generativeModel.generateContent(prompt)
                val replyText = response.text ?: "عذراً، لم أتمكن من إيجاد إجابة."
                
                withContext(Dispatchers.Main) {
                    isThinking.value = false
                    aiResponseText.value = replyText
                    
                    if (isTtsReady) {
                        val paramsTts = Bundle()
                        paramsTts.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
                        textToSpeech?.speak(replyText, TextToSpeech.QUEUE_FLUSH, paramsTts, "AI_REPLY")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isThinking.value = false
                    // هنا سيطبع لك الخطأ الحقيقي بالضبط لنعرف المشكلة
                    val errorMessage = e.localizedMessage ?: e.toString()
                    aiResponseText.value = "الخطأ: $errorMessage"
                    Log.e("MibroAI", "Gemini Error", e)
                }
            }
        }
    }

    private fun requestAudioFocus() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).build()
            audioManager.requestAudioFocus(focusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
    }

    private fun setupMediaSession() {
        if (mediaSession != null) return
        requestAudioFocus()

        mediaSession = MediaSessionCompat(this, "MibroAISession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
            setPlaybackState(stateBuilder.build())

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { handleEarbudClick() }
                override fun onPause() { handleEarbudClick() }
                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    handleEarbudClick()
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
            })
            isActive = true
        }
    }

    private fun handleEarbudClick() {
        if (isEarbudActive.value || isThinking.value) return 

        Handler(Looper.getMainLooper()).post {
            isEarbudActive.value = true
            aiResponseText.value = "" 
            
            try {
                val toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
            } catch (e: Exception) { e.printStackTrace() }

            val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
            }
            speechRecognizer?.startListening(speechIntent)
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
        speechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    isEarbudClicked: Boolean, 
    recognizedText: String, 
    aiResponse: String, 
    isThinking: Boolean,
    apiKey: String,
    onApiKeyChanged: (String) -> Unit,
    onActivateClicked: () -> Unit
) {
    var isActive by remember { mutableStateOf(false) }
    
    val cardColor by animateColorAsState(
        targetValue = if (isEarbudClicked || isThinking) Color(0xFF004D40) else MaterialTheme.colorScheme.surface,
        label = "CardColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Mibro AI Assistant", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChanged,
            label = { Text("Gemini API Key") },
            placeholder = { Text("الصق مفتاحك هنا...") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedLabelColor = MaterialTheme.colorScheme.primary,
                unfocusedTextColor = Color.White,
                focusedTextColor = Color.White
            )
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isEarbudClicked) {
                    Text(text = "🎤 تحدث الآن...", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
                } else if (isThinking) {
                    Text(text = "🧠 جاري التفكير...", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(
                        text = if (isActive) "جاهز! المس سماعتك واسألني" else "المساعد متوقف",
                        fontSize = 20.sp,
                        color = if (isActive) MaterialTheme.colorScheme.primary else Color.White
                    )
                }
                
                Spacer(modifier = Modifier.height(20.dp))
                
                if (recognizedText.isNotEmpty() && isActive) {
                    Text(
                        text = "أنت: $recognizedText",
                        fontSize = 18.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                
                if (aiResponse.isNotEmpty()) {
                    Text(
                        text = "الرد: $aiResponse",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                
                if (!isEarbudClicked && !isActive && !isThinking) {
                    Button(
                        onClick = {
                            if (apiKey.isNotEmpty()) {
                                isActive = true
                                onActivateClicked()
                            }
                        },
                        modifier = Modifier.height(56.dp).fillMaxWidth(),
                        enabled = apiKey.isNotEmpty()
                    ) {
                        Text(text = "تفعيل المساعد الذكي", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
