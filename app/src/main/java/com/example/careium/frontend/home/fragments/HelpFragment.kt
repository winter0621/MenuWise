package com.example.careium.frontend.home.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.example.careium.R
import com.example.careium.databinding.FragmentHelpBinding

class HelpFragment : Fragment(R.layout.fragment_help) {
    private var _binding: FragmentHelpBinding? = null
    private val binding get() = _binding!!

    companion object {
        fun newInstance() = HelpFragment()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentHelpBinding.bind(view)

        bindQuestion(binding.questionRecognition, binding.answerRecognition)
        bindQuestion(binding.questionOffline, binding.answerOffline)
        bindQuestion(binding.questionReport, binding.answerReport)
        bindQuestion(binding.questionReminder, binding.answerReminder)
        bindQuestion(binding.questionData, binding.answerData)
    }

    private fun bindQuestion(question: View, answer: View) {
        question.setOnClickListener {
            answer.visibility = if (answer.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
