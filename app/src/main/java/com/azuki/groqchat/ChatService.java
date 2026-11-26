package com.azuki.groqchat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

public class ChatService extends Service {
    private static final String CHANNEL_ID = "groq_float_running";
    private PowerManager.WakeLock wakeLock;
    private ChatOverlay overlay;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        Intent launch = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pending = PendingIntent.getActivity(this, 0, launch, flags);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        Notification notification = builder.setContentTitle("Groq Float is running")
                .setContentText("Tap to reopen your floating AI chat")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true).setContentIntent(pending).build();
        startForeground(72, notification);
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GroqFloat:ChatWakeLock");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
        } catch (Exception ignored) { }
        overlay = new ChatOverlay(this);
        overlay.show();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Floating AI chat", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps Groq Float available above other apps");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }

    @Override public void onDestroy() {
        if (overlay != null) overlay.dismiss();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}