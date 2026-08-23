package com.mibro.ai

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
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

class MainActivity : ComponentActivity() {

    // دالة طلب الصلاحيات
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // يمكننا إضافة رسائل توجيهية هنا إذا رفض المستخدم الصلاحيات
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. طلب الصلاحيات اللازمة فور فتح التطبيق
        val permissions = mutableListOf(
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissionLauncher.launch(permissions.toTypedArray())

        // 2. رسم الواجهة
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    primary = Color(0xFF00E676) // لون أخضر عصري للمساعد
                )
            ) {
                MainScreen(
                    onStartService = {
                        // تشغيل الخدمة في الخلفية لاختطاف أوامر السماعة
                        val intent = Intent(this, MibroMediaService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MainScreen(onStartService: () -> Unit) {
    var isServiceRunning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        
        Text(
            text = "Mibro الذكي",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "قم بتحويل سماعتك إلى مساعد AI",
            fontSize = 16.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(60.dp))

        // بطاقة التحكم الأنيقة
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isServiceRunning) "المساعد نشط ويستمع للسماعة" else "المساعد متوقف",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isServiceRunning) MaterialTheme.colorScheme.primary else Color.White
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        isServiceRunning = true
                        onStartService()
                    },
                    modifier = Modifier.height(56.dp).padding(horizontal = 32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isServiceRunning) Color.DarkGray else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (isServiceRunning) "يعمل في الخلفية" else "تفعيل المساعد", 
                        color = if (isServiceRunning) Color.White else Color.Black,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        
        Text(
            text = "اضغط مرتين على السماعة للتحدث مع الذكاء الاصطناعي",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}
