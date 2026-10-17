package com.example.careium.frontend.home.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.careium.R
import com.example.careium.core.adapters.PlanCalendarAdapter
import com.example.careium.core.planning.MealPlanStore
import com.example.careium.databinding.FragmentCalenderBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


class CalenderFragment : Fragment(R.layout.fragment_calender) {
    private lateinit var binding: FragmentCalenderBinding

    companion object {
        @JvmStatic
        fun newInstance() =
            CalenderFragment().apply {}
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding = FragmentCalenderBinding.bind(view)
        val today = Calendar.getInstance()
        binding.textCalendarDate.text = SimpleDateFormat("yyyy年M月", Locale.CHINA).format(today.time)
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val dates = (-14..14).map { offset ->
            (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }.time
        }.map { format.format(it) }.toMutableSet()
        dates.addAll(MealPlanStore(requireContext()).dates())
        binding.recyclerViewCalender.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerViewCalender.adapter = PlanCalendarAdapter(requireContext(), dates.sortedDescending())
    }
}
