package com.network.keepalive;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.util.LinkedList;

public class MainActivity extends AppCompatActivity implements KeepAliveService.NetworkStatsCallback {
    private boolean isRunning = false;

    private TextView tvSentCount, tvRecvCount, tvSpeed, tvConsole;
    private EditText etInterval, etPacketSize, etTargetIp;
    private Button btnToggle;
    private ScrollView scrollConsole;

    private final LinkedList<String> logsBuffer = new LinkedList<>();
    private static final int MAX_LOG_LINES = 40;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvSentCount = findViewById(R.id.tvSentCount);
        tvRecvCount = findViewById(R.id.tvRecvCount);
        tvSpeed = findViewById(R.id.tvSpeed);
        tvConsole = findViewById(R.id.tvConsole);
        scrollConsole = findViewById(R.id.scrollConsole);

        etInterval = findViewById(R.id.etInterval);
        etPacketSize = findViewById(R.id.etPacketSize);
        etTargetIp = findViewById(R.id.etTargetIp);
        btnToggle = findViewById(R.id.btnToggle);

        requestBatteryIgnore();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 101);
        }

        btnToggle.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(this, KeepAliveService.class);
            if (!isRunning) {
                int interval = 1;
                int size = 64;
                try {
                    interval = Integer.parseInt(etInterval.getText().toString().trim());
                    size = Integer.parseInt(etPacketSize.getText().toString().trim());
                } catch (Exception ignored) {}

                String target = etTargetIp.getText().toString().trim();
                if (target.isEmpty()) target = "1.1.1.1";

                serviceIntent.putExtra("interval", interval);
                serviceIntent.putExtra("size", size);
                serviceIntent.putExtra("target", target);

                KeepAliveService.callback = this;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }

                isRunning = true;
                btnToggle.setText("إيقاف الحاقن");
                btnToggle.setBackgroundColor(0xFFD50000);
                appendLog("[SYSTEM] تم بدء الحقن بسرعة: " + interval + "ms | الحجم: " + size + "B");
            } else {
                stopService(serviceIntent);
                KeepAliveService.callback = null;
                isRunning = false;
                btnToggle.setText("بدء الحقن والاتصال التوربيني");
                btnToggle.setBackgroundColor(0xFF00C853);
                appendLog("[SYSTEM] تم إيقاف الخدمة.");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        KeepAliveService.callback = this;
    }

    @Override
    public void onUpdate(long sent, long recv, int pps, String logLine) {
        runOnUiThread(() -> {
            tvSentCount.setText(String.valueOf(sent));
            tvRecvCount.setText(String.valueOf(recv));
            tvSpeed.setText(pps + " pps");

            if (logLine != null) {
                appendLog(logLine);
            }
        });
    }

    private void appendLog(String line) {
        logsBuffer.add(line);
        if (logsBuffer.size() > MAX_LOG_LINES) {
            logsBuffer.removeFirst();
        }
        StringBuilder sb = new StringBuilder();
        for (String l : logsBuffer) {
            sb.append(l).append("\n");
        }
        tvConsole.setText(sb.toString());
        scrollConsole.post(() -> scrollConsole.fullScroll(ScrollView.FOCUS_DOWN));
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
