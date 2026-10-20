package com.example.autoclicker

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var btnEnableAccessibility: Button
    private lateinit var btnSyncTime: Button
    private lateinit var btnSchedule: Button
    private lateinit var tvServerTime: TextView
    private lateinit var tvStatus: TextView
    private lateinit var etTargetTime: TextInputEditText
    private lateinit var etCoordX: TextInputEditText
    private lateinit var etCoordY: TextInputEditText
    private lateinit var etInterval: TextInputEditText

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private var serverTimeOffsetMs: Long = 0L // تفاوت زمان سرور و کلاینت
    private var isTimeSynced = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility)
        btnSyncTime = findViewById(R.id.btnSyncTime)
        btnSchedule = findViewById(R.id.btnSchedule)
        tvServerTime = findViewById(R.id.tvServerTime)
        tvStatus = findViewById(R.id.tvStatus)
        etTargetTime = findViewById(R.id.etTargetTime)
        etCoordX = findViewById(R.id.etCoordX)
        etCoordY = findViewById(R.id.etCoordY)
        etInterval = findViewById(R.id.etInterval)

        btnEnableAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "لطفاً Mofid Auto Clicker را در لیست پیدا کرده و روشن کنید", Toast.LENGTH_LONG).show()
        }

        btnSyncTime.setOnClickListener {
            syncTimeWithMofid()
        }

        btnSchedule.setOnClickListener {
            startScheduledExecution()
        }
    }

    private fun syncTimeWithMofid() {
        tvStatus.text = "وضعیت: در حال درخواست زمان از سرور مفید..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val requestTime = System.currentTimeMillis()
                val request = Request.Builder()
                    .url("https://mofidonline.com")
                    .head() // درخواست HEAD فقط برای دریافت هدر پاسخ
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseTime = System.currentTimeMillis()
                    val latency = (responseTime - requestTime) / 2
                    val dateHeader = response.header("Date")

                    if (dateHeader != null) {
                        // فرمت استاندارد RFC 1123 مثل: Tue, 15 Nov 2024 08:12:31 GMT
                        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US)
                        val serverDate: Date = format.parse(dateHeader) ?: Date()
                        val estimatedServerTime = serverDate.time + latency
                        serverTimeOffsetMs = estimatedServerTime - responseTime
                        isTimeSynced = true

                        withContext(Dispatchers.Main) {
                            val localFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                            tvServerTime.text = "زمان سرور: ${localFormat.format(serverDate)} (تفاوت: ${serverTimeOffsetMs}ms)"
                            tvStatus.text = "وضعیت: همگام‌سازی زمان موفقیت‌آمیز بود."
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            tvStatus.text = "خطا: هدر Date در پاسخ سرور یافت نشد."
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "خطا در اتصال به سرور: ${e.message}"
                }
            }
        }
    }

    private fun startScheduledExecution() {
        val service = AutoClickService.instance
        if (service == null) {
            Toast.makeText(this, "ابتدا سرویس Accessibility را فعال کنید!", Toast.LENGTH_SHORT).show()
            return
        }

        val targetStr = etTargetTime.text.toString().trim()
        if (targetStr.isEmpty()) {
            Toast.makeText(this, "ساعت هدف را وارد کنید (مثال 08:30:00)", Toast.LENGTH_SHORT).show()
            return
        }

        val x = etCoordX.text.toString().toFloatOrNull() ?: 500f
        val y = etCoordY.text.toString().toFloatOrNull() ?: 1000f
        val interval = etInterval.text.toString().toLongOrNull() ?: 10L

        try {
            val timeParts = targetStr.split(":")
            val targetHour = timeParts[0].toInt()
            val targetMin = timeParts[1].toInt()
            val targetSec = timeParts[2].toInt()

            val now = Calendar.getInstance()
            val targetCal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, targetMin)
                set(Calendar.SECOND, targetSec)
                set(Calendar.MILLISECOND, 0)
            }

            var delayMs = (targetCal.timeInMillis - (now.timeInMillis + serverTimeOffsetMs))
            if (delayMs < 0) {
                // اگر زمان گذشته بود، برای فردا تنظیم شود
                targetCal.add(Calendar.DAY_OF_YEAR, 1)
                delayMs = (targetCal.timeInMillis - (now.timeInMillis + serverTimeOffsetMs))
            }

            tvStatus.text = "آماده‌باش! تا کلیک ${delayMs / 1000} ثانیه باقی مانده است..."

            // شروع تایمر شمارش معکوس
            object : CountDownTimer(delayMs, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    tvStatus.text = "شمارش معکوس: ${millisUntilFinished / 1000} ثانیه تا کلیک..."
                }

                override fun onFinish() {
                    tvStatus.text = "در حال شلیک 5 کلیک با فاصله ${interval}ms..."
                    service.performBurstClick(x, y, 5, interval) {
                        runOnUiThread {
                            tvStatus.text = "عملیات با موفقیت انجام شد (5 کلیک ارسال شد)."
                        }
                    }
                }
            }.start()

        } catch (e: Exception) {
            Toast.makeText(this, "فرمت ساعت نامعتبر است. فرمت صحیح: HH:mm:ss", Toast.LENGTH_SHORT).show()
        }
    }
}
