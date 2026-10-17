package com.example.careium.core.planning

import android.annotation.SuppressLint
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
import com.example.careium.frontend.factory.channelID
import com.example.careium.frontend.factory.messageExtra
import com.example.careium.frontend.factory.notificationID
import com.example.careium.frontend.factory.titleExtra
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object MealPlanReminder {
    fun schedule(context: Context, date: String, mealId: String, label: String, minute: Int): Boolean {
        val calendar = parse(date, minute) ?: return false
        if (calendar.timeInMillis <= System.currentTimeMillis()) return false
        val id = requestId(date, mealId)
        val intent = Intent(context, MealPlanReminderReceiver::class.java)
            .putExtra(titleExtra, "MenuWise · $label")
            .putExtra(messageExtra, "到了安排${label}的时间，点击查看计划。")
            .putExtra("plan_date", date).putExtra("focus_meal", mealId)
            .putExtra("meal_plan_notification_id", id)
        val pending = PendingIntent.getBroadcast(context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarms = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarms.canScheduleExactAlarms()) {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
            } else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
            true
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pending)
            true
        }
    }

    fun cancel(context: Context, date: String, mealId: String) {
        val id = requestId(date, mealId)
        val intent = Intent(context, MealPlanReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(context, id, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) {
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).cancel(pending)
            pending.cancel()
        }
    }

    fun timeLabel(minute: Int): String = String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60)

    private fun requestId(date: String, mealId: String) = 31000 + ("$date|$mealId".hashCode() and 0x3fffffff)
    private fun parse(date: String, minute: Int): Calendar? {
        return try {
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }.parse(date)
                ?: return null
            Calendar.getInstance().apply {
                time = day
                set(Calendar.HOUR_OF_DAY, minute / 60)
                set(Calendar.MINUTE, minute % 60)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
        } catch (_: Exception) { null }
    }
}

class MealPlanReminderReceiver : BroadcastReceiver() {
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val plans = MealPlanStore(context)
            val today = MealPlanStore.today()
            plans.dates().filter { it >= today }.forEach { date ->
                plans.load(date)?.meals?.forEach { meal ->
                    meal.reminderMinute?.let { MealPlanReminder.schedule(context, date, meal.id, meal.label, it) }
                }
            }
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.getNotificationChannel(channelID) == null) {
            manager.createNotificationChannel(NotificationChannel(channelID, "饮食计划提醒", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val openPlan = Intent(context, com.example.careium.frontend.home.activities.MealPlanActivity::class.java)
            .putExtra("plan_date", intent.getStringExtra("plan_date"))
            .putExtra("focus_meal", intent.getStringExtra("focus_meal"))
        val openPending = PendingIntent.getActivity(context,
            intent.getIntExtra("meal_plan_notification_id", notificationID), openPlan,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, channelID)
            .setSmallIcon(R.mipmap.ic_launcher).setContentTitle(intent.getStringExtra(titleExtra) ?: "MenuWise")
            .setContentText(intent.getStringExtra(messageExtra) ?: "Check your meal plan")
            .setContentIntent(openPending).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT).build()
        manager.notify(intent.getIntExtra("meal_plan_notification_id", notificationID), notification)
    }
}
