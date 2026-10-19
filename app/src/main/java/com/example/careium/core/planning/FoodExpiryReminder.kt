package com.example.careium.core.planning

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.careium.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object FoodExpiryReminder {
    fun schedule(context: Context, name: String, expiresOn: String): Boolean {
        val calendar = parse(expiresOn) ?: return false
        calendar.add(Calendar.DATE, -1)
        calendar.set(Calendar.HOUR_OF_DAY, 9); calendar.set(Calendar.MINUTE, 0); calendar.set(Calendar.SECOND, 0)
        if (calendar.timeInMillis <= System.currentTimeMillis()) return false
        val intent = Intent(context, FoodExpiryReminderReceiver::class.java)
            .putExtra("food_name", name).putExtra("expires_on", expiresOn).putExtra("expiry_notification_id", id(name))
        val pending = PendingIntent.getBroadcast(context, id(name), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarms.canScheduleExactAlarms()) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
            true
        } catch (_: SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending); true }
    }
    fun cancel(context: Context, name: String) {
        val intent = Intent(context, FoodExpiryReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(context, id(name), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) { (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending); pending.cancel() }
    }
    private fun id(name: String) = 47000 + (name.lowercase(Locale.ROOT).hashCode() and 0x3fffffff)
    private fun parse(value: String) = try { SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(value)?.let { Calendar.getInstance().apply { time = it } } } catch (_: Exception) { null }
}

class FoodExpiryReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            MealPlanStore(context).expiryEntries().forEach { (name, date) -> FoodExpiryReminder.schedule(context, name, date) }
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = "food_expiry_reminders"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(channel) == null) {
            manager.createNotificationChannel(NotificationChannel(channel, "食材保质期提醒", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val name = intent.getStringExtra("food_name") ?: "食材"
        val date = intent.getStringExtra("expires_on") ?: "近期"
        val notification = NotificationCompat.Builder(context, channel).setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("MenuWise · 食材临期").setContentText("$name 将于 $date 到期，请优先使用或确认保存状态。")
            .setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        manager.notify(intent.getIntExtra("expiry_notification_id", 47000), notification)
    }
}
