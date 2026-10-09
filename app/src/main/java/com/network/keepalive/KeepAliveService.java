package com.network.keepalive;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import androidx.core.app.NotificationCompat;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class KeepAliveService extends Service {
    private static final String CHANNEL_ID = "3g_keepalive_channel";
    private PowerManager.WakeLock wakeLock;
    private volatile boolean isRunning = false;

    private Thread txThread;
    private Thread rxThread;
    private DatagramSocket socket;

    private long sentCount = 0;
    private long recvCount = 0;
    private int currentPps = 0;

    // واجهة للتواصل اللحظي مع الشاشة
    public interface NetworkStatsCallback {
        void onUpdate(long sent, long recv, int pps, String logLine);
    }
    public static NetworkStatsCallback callback = null;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KeepAlive::TurboLock");
            wakeLock.acquire();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int intervalMs = 1;
        int packetSize = 64;
        String targetIp = "1.1.1.1";

        if (intent != null) {
            intervalMs = Math.max(1, intent.getIntExtra("interval", 1));
            packetSize = Math.max(16, intent.getIntExtra("size", 64));
            String ip = intent.getStringExtra("target");
            if (ip != null && !ip.isEmpty()) targetIp = ip;
        }

        Notification notification = buildNotification("محرك 3G+ التوربيني يعمل بنبض " + intervalMs + "ms");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, notification);
        }

        if (!isRunning) {
            isRunning = true;
            startTurboEngine(targetIp, packetSize, intervalMs);
        }

        return START_STICKY;
    }

    private void startTurboEngine(String targetIp, int packetSize, int intervalMs) {
        sentCount = 0;
        recvCount = 0;

        txThread = new Thread(() -> {
            byte[] sendPayload = new byte[packetSize];
            for (int i = 0; i < packetSize; i++) sendPayload[i] = (byte) (i % 128);

            long lastPpsCheck = System.currentTimeMillis();
            long packetsInLastSec = 0;
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

            try {
                socket = new DatagramSocket();
                socket.setSoTimeout(100);
                InetAddress address = InetAddress.getByName(targetIp);

                // مسار استقبال الردود (RX Thread)
                startReceiverThread();

                while (isRunning) {
                    DatagramPacket packet = new DatagramPacket(sendPayload, sendPayload.length, address, 53);
                    socket.send(packet);
                    sentCount++;
                    packetsInLastSec++;

                    long now = System.currentTimeMillis();
                    if (now - lastPpsCheck >= 1000) {
                        currentPps = (int) packetsInLastSec;
                        packetsInLastSec = 0;
                        lastPpsCheck = now;
                    }

                    // إرسال السجل إلى الواجهة
                    if (sentCount % (intervalMs <= 5 ? 100 : 5) == 0) {
                        if (callback != null) {
                            String log = "[" + sdf.format(new Date()) + "] TX: " + packetSize + "B -> " + targetIp + " (Total: " + sentCount + ")";
                            callback.onUpdate(sentCount, recvCount, currentPps, log);
                        }
                    }

                    if (intervalMs > 0) {
                        try {
                            Thread.sleep(intervalMs);
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                if (callback != null) {
                    callback.onUpdate(sentCount, recvCount, currentPps, "[ERROR] " + e.getMessage());
                }
            }
        });
        txThread.setPriority(Thread.MAX_PRIORITY);
        txThread.start();
    }

    private void startReceiverThread() {
        rxThread = new Thread(() -> {
            byte[] buffer = new byte[2048];
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
            while (isRunning && socket != null && !socket.isClosed()) {
                try {
                    DatagramPacket recvPacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(recvPacket);
                    recvCount++;

                    if (callback != null) {
                        String log = "[" + sdf.format(new Date()) + "] RX: " + recvPacket.getLength() + "B <- " + recvPacket.getAddress().getHostAddress();
                        callback.onUpdate(sentCount, recvCount, currentPps, log);
                    }
                } catch (Exception ignored) {
                    // Socket timeout طبيعي لضمان عدم تجميد مسار الاستقبال
                }
            }
        });
        rxThread.start();
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("3G+ Turbo Engine")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "3G Turbo Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (txThread != null) txThread.interrupt();
        if (rxThread != null) rxThread.interrupt();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        callback = null;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
