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
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;

public class KeepAliveService extends Service {
    private static final String CHANNEL_ID = "3g_keepalive_channel";
    private PowerManager.WakeLock wakeLock;
    private boolean isRunning = false;
    private Thread workerThread;
    private long packetsSent = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        // حجز WakeLock لضمان عدم نوم المعالج عند قفل شاشة الهاتف
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "KeepAlive::Lock");
        wakeLock.acquire();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = buildNotification("يعمل الآن: تثبيت 3G+ نشط");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, notification);
        }

        if (!isRunning) {
            isRunning = true;
            startTrafficLoop();
        }

        return START_STICKY; // إعادة تشغيل الخدمة تلقائياً إذا حاول النظام إغلاقها
    }

    private void startTrafficLoop() {
        workerThread = new Thread(() -> {
            byte[] dummyData = "PING_3G_PLUS_KEEPALIVE".getBytes();
            while (isRunning) {
                try {
                    // 1. إرسال حزمة UDP سريعة جداً تمنع برج الاتصال من خفض التردد
                    DatagramSocket socket = new DatagramSocket();
                    InetAddress address = InetAddress.getByName("1.1.1.1");
                    DatagramPacket packet = new DatagramPacket(dummyData, dummyData.length, address, 53);
                    socket.send(packet);
                    socket.close();

                    packetsSent++;

                    // 2. فحص HTTP صغير كل 5 حزم لتأكيد تدفق الـ TCP
                    if (packetsSent % 5 == 0) {
                        URL url = new URL("http://clients3.google.com/generate_204");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setConnectTimeout(1500);
                        conn.setReadTimeout(1500);
                        conn.setRequestMethod("HEAD");
                        conn.getResponseCode();
                        conn.disconnect();
                    }

                    // تحديث الإشعار بعدد الحزم
                    if (packetsSent % 10 == 0) {
                        Notification updated = buildNotification("حزم البيانات المرسلة: " + packetsSent + " | الشبكة نشطة");
                        NotificationManager manager = getSystemService(NotificationManager.class);
                        if (manager != null) manager.notify(1, updated);
                    }

                    // فاصل زمني 1.5 ثانية (التوقيت الذهبي لمنع خفض RRC إلى FACH)
                    Thread.sleep(1500);

                } catch (Exception e) {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException ignored) {}
                }
            }
        });
        workerThread.start();
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("3G+ Keep-Alive Service")
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
                    "3G KeepAlive Background",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        isRunning = false;
        if (workerThread != null) workerThread.interrupt();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
