package com.mibro.ai

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.Locale

class MibroMediaService : Service(), TextToSpeech.OnInitListener {

    private lateinit var mediaSession: MediaSessionCompat
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    
    override fun onCreate() {
        super.onCreate()
        
        // إنشاء الإشعار فوراً لتجنب غضب أندرويد 14 (Crash)
        val notification = createNotification()
        startForeground(1, notification)
        
        // تهيئة محرك النطق
        textToSpeech = TextToSpeech(this, this)
        
        // تهيئة البلوتوث
        setupMediaSession()
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "MibroAISession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE)
            setPlaybackState(stateBuilder.build())

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    super.onPlay()
                    handleEarbudClick()
                }
                
                override fun onPause() {
                    super.onPause()
                    handleEarbudClick()
                }
            })
            
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    private fun handleEarbudClick() {
        Log.d("MibroAI", "تم رصد ضغطة من السماعة")
        if (isTtsReady) {
            textToSpeech?.speak("أنا أستمع إليك", TextToSpeech.QUEUE_FLUSH, null, "AI_GREETING")
        }
    }

    private fun createNotification(): Notification {
        val channelId = "MibroAI_Channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Mibro AI Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Mibro AI")
            .setContentText("المساعد نشط ويستمع للسماعة")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
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
        mediaSession.release()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
