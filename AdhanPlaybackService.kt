package com.sakinah.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder

class AdhanPlaybackService : Service() {
    private var player: MediaPlayer? = null
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_ADHAN") {
            stopPlayback(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY
        }
        val path = intent?.getStringExtra("audio_path") ?: run { stopSelf(); return START_NOT_STICKY }
        val prayer = intent.getStringExtra("prayer_name") ?: "الصلاة"
        val stopIntent = Intent(this, AdhanPlaybackService::class.java).setAction("STOP_ADHAN")
        val stopPi = PendingIntent.getService(this, 8181, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val openPi = PendingIntent.getActivity(this, 8182, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, "sakinah_prayer_audio")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("أذان صلاة $prayer")
                .setContentText("اضغط إيقاف لإنهاء تشغيل الأذان")
                .setContentIntent(openPi).addAction(Notification.Action.Builder(android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_pause), "إيقاف", stopPi).build())
                .setOngoing(true).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("أذان صلاة $prayer").setContentText("الأذان يعمل")
                .setContentIntent(openPi).setOngoing(true).build()
        }
        startForeground(8180, notification)
        try {
            stopPlayback()
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                setDataSource(path)
                setOnCompletionListener { stopPlayback(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
                setOnErrorListener { mp, _, _ -> mp.release(); player = null; stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); true }
                prepareAsync(); setOnPreparedListener { it.start() }
            }
        } catch (_: Exception) { stopPlayback(); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        return START_NOT_STICKY
    }
    private fun stopPlayback() { try { player?.stop() } catch (_: Exception) {}; try { player?.release() } catch (_: Exception) {}; player = null }
    override fun onDestroy() { stopPlayback(); super.onDestroy() }
}
