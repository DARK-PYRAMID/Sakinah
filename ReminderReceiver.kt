package com.sakinah.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar
import kotlin.random.Random

class ReminderReceiver : BroadcastReceiver() {
    private val prayers = listOf(
        "اللهم احفظه بعينك التي لا تنام، واكتب له الخير حيث كان، واصرف عنه كل سوء.",
        "اللهم ارزقه راحةً في القلب، وسعةً في الرزق، وبركةً في العمر والعمل.",
        "اللهم يسّر له أمره، واشرح صدره، ووفّقه لما تحب وترضى.",
        "اللهم اجبر خاطره جبرًا يليق بكرمك، وأبدل تعبه طمأنينةً وفرحًا.",
        "اللهم اكتب له من الخير أكثر مما يرجو، واصرف عنه ما يؤذيه، وبارك له فيما أعطيته.",
        "اللهم اغفر لنا وله، وارحمنا برحمتك، وأعنّا على ذكرك وشكرك وحسن عبادتك.",
        "اللهم ارزقه قلبًا مطمئنًا، ونفسًا راضية، وأيامًا تحمل له الخير."
    )
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.getBooleanExtra("is_prayer", false)) {
            handlePrayer(context, intent)
            return
        }
        val name = intent.getStringExtra("friend_name") ?: "صاحبك"
        val custom = intent.getStringExtra("custom_dua").orEmpty()
        val slot = intent.getIntExtra("slot", 0)
        val dayKey = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        val prefs = context.getSharedPreferences("sakinah_data", Context.MODE_PRIVATE)
        val lastKey = "last_${intent.getStringExtra("friend_id")}_$slot"
        if (prefs.getString(lastKey, "") != dayKey) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val text = if (custom.isNotBlank() && Random.nextBoolean()) custom else prayers.random()
            val open = PendingIntent.getActivity(context, 9000 + slot, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = if (Build.VERSION.SDK_INT >= 26) {
                Notification.Builder(context, "sakinah_reminders")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("🤍 متنساش $name من دعائك")
                    .setContentText(text)
                    .setStyle(Notification.BigTextStyle().bigText(text))
                    .setContentIntent(open).setAutoCancel(true).build()
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(context).setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("متنساش $name من دعائك").setContentText(text).setAutoCancel(true).build()
            }
            manager.notify((intent.getStringExtra("friend_id") ?: "friend").hashCode() + slot, notification)
            prefs.edit().putString(lastKey, dayKey).apply()
        }
        // Schedule the same slot for tomorrow.
        val next = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val time = if (slot == 1) "14:00" else if (slot == 2) "19:30" else {
            // Use the first slot's saved time where possible.
            val id = intent.getStringExtra("friend_id")
            var found = "09:00"
            try {
                val arr = org.json.JSONArray(prefs.getString("friends", "[]"))
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    if (o.optString("id") == id) { found = o.optString("time", "09:00"); break }
                }
            } catch (_: Exception) {}
            found
        }
        val parts = time.split(":")
        next.set(Calendar.HOUR_OF_DAY, parts.getOrNull(0)?.toIntOrNull() ?: 9)
        next.set(Calendar.MINUTE, parts.getOrNull(1)?.toIntOrNull() ?: 0)
        next.set(Calendar.SECOND, 0); next.set(Calendar.MILLISECOND, 0)
        val pi = PendingIntent.getBroadcast(context, ((intent.getStringExtra("friend_id") ?: "").hashCode() * 10) + slot + 1,
            intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
            else if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
            else am.setExact(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
        } catch (_: Exception) { am.set(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi) }
    }
    private fun handlePrayer(context: Context, intent: Intent) {
        val prayerName = intent.getStringExtra("prayer_name") ?: "الصلاة"
        val prayerTime = intent.getStringExtra("prayer_time") ?: "09:00"
        val slot = intent.getIntExtra("slot", 0)
        val prefs = context.getSharedPreferences("sakinah_data", Context.MODE_PRIVATE)
        val audioPath = prefs.getString("adhan_audio", null)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val open = PendingIntent.getActivity(context, 9100 + slot, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val channelId = if (!audioPath.isNullOrBlank() && java.io.File(audioPath).exists()) "sakinah_prayer_audio" else "sakinah_prayer_default"
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, channelId).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("حان الآن وقت صلاة $prayerName")
                .setContentText("حيّ على الصلاة · $prayerTime")
                .setContentIntent(open).setAutoCancel(true).build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("حان الآن وقت صلاة $prayerName").setContentText("حيّ على الصلاة")
                .setContentIntent(open).setAutoCancel(true).build()
        }
        manager.notify(7000 + slot, notification)
        if (!audioPath.isNullOrBlank() && java.io.File(audioPath).exists()) {
            try {
                val service = Intent(context, AdhanPlaybackService::class.java).apply {
                    putExtra("audio_path", audioPath); putExtra("prayer_name", prayerName)
                }
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service) else context.startService(service)
            } catch (_: Exception) { }
        }
        val next = Calendar.getInstance().apply {
            val p = prayerTime.split(":")
            set(Calendar.HOUR_OF_DAY, p.getOrNull(0)?.toIntOrNull() ?: 9)
            set(Calendar.MINUTE, p.getOrNull(1)?.take(2)?.toIntOrNull() ?: 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val nextIntent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("is_prayer", true); putExtra("prayer_name", prayerName)
            putExtra("prayer_time", prayerTime); putExtra("slot", slot)
        }
        val pi = PendingIntent.getBroadcast(context, 7000 + slot, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        try {
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
            else if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
            else am.setExact(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi)
        } catch (_: Exception) { am.set(AlarmManager.RTC_WAKEUP, next.timeInMillis, pi) }
    }

}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences("sakinah_data", Context.MODE_PRIVATE)
        val arr = try { org.json.JSONArray(prefs.getString("friends", "[]")) } catch (_: Exception) { org.json.JSONArray() }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: continue
            if (!f.optBoolean("enabled", true)) continue
            val id = f.optString("id")
            val name = f.optString("name", "صاحبك")
            val times = mutableListOf(f.optString("time", "09:00"))
            if (f.optInt("count", 3) >= 2) times.add("14:00")
            if (f.optInt("count", 3) >= 3) times.add("19:30")
            times.forEachIndexed { slot, time ->
                val p = time.split(":")
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, p.getOrNull(0)?.toIntOrNull() ?: 9)
                    set(Calendar.MINUTE, p.getOrNull(1)?.toIntOrNull() ?: 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                }
                val alarmIntent = Intent(context, ReminderReceiver::class.java).apply {
                    putExtra("friend_id", id); putExtra("friend_name", name); putExtra("custom_dua", f.optString("dua")); putExtra("slot", slot)
                }
                val pi = PendingIntent.getBroadcast(context, id.hashCode() * 10 + slot + 1, alarmIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                try {
                    if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                    else if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                    else am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                } catch (_: Exception) { am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi) }
            }
        }
        // Restore prayer alarms after reboot if the user previously fetched prayer times.
        try {
            val times = org.json.JSONObject(prefs.getString("prayer_times", "{}"))
            val prayerList = listOf("Fajr" to "الفجر", "Dhuhr" to "الظهر", "Asr" to "العصر", "Maghrib" to "المغرب", "Isha" to "العشاء")
            prayerList.forEachIndexed { slot, pair ->
                val time = times.optString(pair.first, "").take(5)
                val parts = time.split(":")
                if (parts.size != 2) return@forEachIndexed
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, parts[0].toIntOrNull() ?: 9)
                    set(Calendar.MINUTE, parts[1].toIntOrNull() ?: 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
                }
                val prayerIntent = Intent(context, ReminderReceiver::class.java).apply {
                    putExtra("is_prayer", true); putExtra("prayer_name", pair.second); putExtra("prayer_time", time); putExtra("slot", slot)
                }
                val pi = PendingIntent.getBroadcast(context, 7000 + slot, prayerIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                try {
                    if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                    else if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                    else am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
                } catch (_: Exception) { am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi) }
            }
        } catch (_: Exception) { }
    }
}
