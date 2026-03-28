package com.example.poulailler_copilot.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.poulailler_copilot.databinding.ActivityLoginBinding
import com.example.poulailler_copilot.repository.FirebaseRepository
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val vm: LoginViewModel by viewModels()
    private val firebaseRepo = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            vm.login(email, password) { success, role, userId ->
                if (!success) {
                    Toast.makeText(this, "Identifiants invalides ou compte inactif", Toast.LENGTH_SHORT).show()
                } else {
                    // Enregistrement de la connexion dans Firebase
                    lifecycleScope.launch {
                        val username = email.split("@")[0]
                        firebaseRepo.recordLogin(userId, username)
                    }

                    val intent = Intent(this, DashboardActivity::class.java)
                    intent.putExtra("role", role)
                    intent.putExtra("userIdString", userId)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
}
