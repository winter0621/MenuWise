package com.example.careium.frontend.home.fragments

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.careium.R
import com.example.careium.databinding.FragmentReminderBinding
import com.example.careium.frontend.factory.*
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.*


class ReminderFragment : Fragment(R.layout.fragment_reminder) {
    private lateinit var binding: FragmentReminderBinding
    private lateinit var picker: MaterialTimePicker
    private var calender: Calendar = Calendar.getInstance()
    private lateinit var alarmManager: AlarmManager
    private lateinit var pendingIntent: PendingIntent
    lateinit var sharedPreferences: SharedPreferences

    companion object {
        @JvmStatic
        fun newInstance() =
            ReminderFragment().apply {
            }
    }

    @SuppressLint("ResourceAsColor")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentReminderBinding.bind(view)
        sharedPreferences = requireContext().getSharedPreferences("meal_reminders", Context.MODE_PRIVATE)

        createNotificationChannel()

        setClickListener(
            binding.breakfastTime,
            "早餐时间到了",
            "早餐提醒",
            binding.breakfastSetButton,
            binding.breakfastCancelButton, "breakfast"
        )
        setClickListener(
            binding.lunchTime,
            "午餐时间到了",
            "午餐提醒",
            binding.lunchSetButton,
            binding.lunchCancelButton, "lunch"
        )
        setClickListener(
            binding.dinnerTime,
            "晚餐时间到了",
            "晚餐提醒",
            binding.dinnerSetButton,
            binding.dinnerCancelButton, "dinner"
        )
        setClickListener(
            binding.snackTime,
            "加餐时间到了",
            "加餐提醒",
            binding.snackSetButton,
            binding.snackCancelButton, "snack"
        )

    }

    private fun setClickListener(
        mealTime: TextView,
        message: String,
        meal: String,
        SetButton: Button,
        CancelButton: Button,
        key: String
    ) {

        val savedMinute = sharedPreferences.getInt(key, -1)
        if (savedMinute >= 0) {
            mealTime.text = String.format(Locale.getDefault(), "%02d:%02d", savedMinute / 60, savedMinute % 60)
            SetButton.visibility = View.GONE
            CancelButton.visibility = View.VISIBLE
        }

        SetButton.setOnClickListener {
            setNotification(
                message,
                meal,
                mealTime,
                CancelButton, SetButton, key
            )
        }
        CancelButton.setOnClickListener {
            cancelNotification(mealTime, key)
            SetButton.visibility = View.VISIBLE
            CancelButton.visibility = View.GONE
        }
    }

    private fun cancelNotification(text: TextView, key: String) {
        Toast.makeText(context, "已取消提醒", Toast.LENGTH_SHORT)
            .show()
        alarmManager = context?.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderNotification::class.java)
        pendingIntent =
            PendingIntent.getBroadcast(
                context,
                key.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

        text.text = getString(R.string.time)
        alarmManager.cancel(pendingIntent)
        sharedPreferences.edit().remove(key).apply()
    }

    @SuppressLint("ResourceAsColor")
    private fun setNotification(
        message: String,
        meal: String,
        mealText: TextView,
        cancel: Button,
        set: Button,
        key: String
    ) {
        val intent = Intent(context, ReminderNotification::class.java)
        intent.putExtra(
            titleExtra,
            "Careium Notification Manager"
        )
        intent.putExtra(
            messageExtra,
            message
        )
        intent.putExtra("notification_id", key.hashCode())
        pendingIntent = PendingIntent.getBroadcast(
            context,
            key.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        alarmManager = context?.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(12)
            .setMinute(0)
            .setTitleText(meal)
            .build()
        picker.show(parentFragmentManager, "Careium")
        picker.addOnPositiveButtonClickListener {
            if (picker.hour > 12) {
                String.format(Locale.CHINA, "%02d:%02d", picker.hour, picker.minute).also { mealText.text = it }
            } else {
                String.format(Locale.CHINA, "%02d:%02d", picker.hour, picker.minute).also { mealText.text = it }

            }
            calender = Calendar.getInstance()
            calender[Calendar.HOUR_OF_DAY] = picker.hour
            calender[Calendar.MINUTE] = picker.minute
            calender[Calendar.SECOND] = 0
            calender[Calendar.MILLISECOND] = 0

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP, calender.timeInMillis, pendingIntent
            )
            sharedPreferences.edit().putInt(key, picker.hour * 60 + picker.minute).apply()
            Toast.makeText(context, "用餐提醒已设置", Toast.LENGTH_SHORT).show()
            cancel.visibility = View.VISIBLE
            set.visibility = View.GONE
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name: CharSequence = "CareiumReminderChannel"
            val description = "Channel for Reminder Manager"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(channelID, name, importance)

            channel.description = description

            val notificationManager =
                context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }


    }
}
