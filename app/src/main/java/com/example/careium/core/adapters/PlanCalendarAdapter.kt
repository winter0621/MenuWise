package com.example.careium.core.adapters

import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.careium.R
import com.example.careium.core.planning.MealPlanStore
import com.example.careium.frontend.home.activities.MealPlanActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PlanCalendarAdapter(
    private val context: Context,
    private val dates: List<String>
) : RecyclerView.Adapter<PlanCalendarAdapter.DayHolder>() {
    class DayHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.calendar_day_title)
        val subtitle: TextView = view.findViewById(R.id.calendar_day_subtitle)
        val status: TextView = view.findViewById(R.id.calendar_plan_status)
        val open: View = view.findViewById(R.id.calendar_open_plan)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayHolder = DayHolder(
        LayoutInflater.from(context).inflate(R.layout.item_calendar_plan, parent, false)
    )

    override fun onBindViewHolder(holder: DayHolder, position: Int) {
        val dateKey = dates[position]
        val date = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(dateKey) }.getOrNull() ?: Date()
        val plan = MealPlanStore(context).load(dateKey)
        holder.title.text = SimpleDateFormat("M月d日", Locale.CHINA).format(date)
        holder.subtitle.text = SimpleDateFormat("EEEE", Locale.CHINA).format(date)
        holder.status.text = if (plan == null) context.getString(R.string.calendar_no_plan)
        else context.getString(R.string.calendar_plan_summary, plan.target, plan.eaten.toInt())
        val openPlan = {
            context.startActivity(Intent(context, MealPlanActivity::class.java).putExtra("plan_date", dateKey))
        }
        holder.open.setOnClickListener { openPlan() }
        holder.itemView.setOnClickListener { openPlan() }
    }

    override fun getItemCount(): Int = dates.size
}
