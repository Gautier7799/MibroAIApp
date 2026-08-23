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
    private lateinit var textToSpeech: TextToSpeech
    
    override fun onCreate() {
        super.onCreate()
        
        // 1. تجهيز محرك النطق (Text To Speech)
        textToSpeech = TextToSpeech(this, this)
        
        // 2. إنشاء MediaSession لخداع نظام الأندرويد واختطاف زر السماعة
        mediaSession = MediaSessionCompat(this, "MibroAISession").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            
            // نخبر النظام أننا "مشغل موسيقى جاهز للعمل"
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or PlaybackStateCompat.ACTION_PLAY_PAUSE)
            setPlaybackState(stateBuilder.build())

            // 3. الاستماع لضغطات السماعة
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
            
            // تفعيل الجلسة
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // تشغيل الخدمة في الخلفية بشكل دائم لكي لا يقتلها النظام
        startForeground(1, createNotification())
        return START_STICKY
    }

    // هذه الدالة تعمل فوراً عندما تضغط على السماعة
    private fun handleEarbudClick() {
        Log.d("MibroAI", "تم رصد ضغطة من سماعة Mibro!")
        
        // الرد الصوتي الفوري عبر السماعة
        textToSpeech.speak("مرحباً، أنا المساعد الذكي. كيف يمكنني مساعدتك؟", TextToSpeech.QUEUE_FLUSH, null, "AI_GREETING")
        
        // ملاحظة للفريق: في "الحجر القادم" سنقوم بتشغيل الميكروفون هنا 
        // للاستماع لصوت المستخدم وإرساله إلى الذكاء الاصطناعي (Gemini).
    }

    // إنشاء إشعار ثابت في النظام لضمان بقاء التطبيق يعمل
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
            .setContentTitle("Mibro AI نشط")
            .setContentText("المساعد جاهز للاستماع من سماعتك")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // إعداد اللغة العربية لمحرك النطق
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale("ar"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("MibroAI", "اللغة العربية غير مدعومة في محرك النطق في هاتفك.")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession.release()
        textToSpeech.shutdown()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
