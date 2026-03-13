package com.example.poulailler_copilot.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.poulailler_copilot.databinding.ActivityExpensesBinding

class ExpensesActivity : AppCompatActivity() {
    private lateinit var binding: ActivityExpensesBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExpensesBinding.inflate(layoutInflater)
        setContentView(binding.root)
    }
}
