package com.example.poulailler_copilot.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.LoginEntry
import com.example.poulailler_copilot.databinding.ActivityLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val vm: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener {
            val username = binding.etUsername.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            vm.login(username, password) { success, role, userId ->
                if (!success) {
                    Toast.makeText(this, "Identifiants invalides ou compte inactif", Toast.LENGTH_SHORT).show()
                } else {
                    // Enregistrer la connexion dans l'historique
                    lifecycleScope.launch(Dispatchers.IO) {
                        val db = AppDatabase.getInstance(this@LoginActivity)
                        db.loginDao().insert(LoginEntry(userId = userId, timestamp = System.currentTimeMillis()))
                    }

                    val intent = Intent(this, DashboardActivity::class.java)
                    intent.putExtra("role", role)
                    intent.putExtra("username", username)
                    intent.putExtra("userId", userId)
                    startActivity(intent)
                    finish()
                }
            }
        }
    }
}
