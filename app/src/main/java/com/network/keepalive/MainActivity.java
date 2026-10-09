package com.network.keepalive;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private boolean isServiceActive = false;
    private TextView tvStatus;
    private Button btnToggle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        btnToggle = findViewById(R.id.btnToggle);

        // طلب استثناء التطبيق من تحسينات البطارية (ضروري لمنع قتل الخدمة)
        requestBatteryIgnore();

        // طلب إذن الإشعارات لأندرويد 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 101);
        }

        btnToggle.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(this, KeepAliveService.class);
            if (!isServiceActive) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                isServiceActive = true;
                tvStatus.setText("الحالة: يعمل ويحافظ على 3G+");
                tvStatus.setTextColor(0xFF00AA00);
                btnToggle.setText("إيقاف الخدمة");
            } else {
                stopService(serviceIntent);
                isServiceActive = false;
                tvStatus.setText("الحالة: متوقف");
                tvStatus.setTextColor(0xFFFF0000);
                btnToggle.setText("تشغيل التثبيت على 3G+");
            }
        });
    }

    private void requestBatteryIgnore() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
    }
}
