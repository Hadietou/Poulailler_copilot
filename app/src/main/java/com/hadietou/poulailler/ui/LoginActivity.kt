package com.hadietou.poulailler.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hadietou.poulailler.BuildConfig
import com.hadietou.poulailler.databinding.ActivityLoginBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val vm: LoginViewModel by viewModels()
    private val firebaseRepo = FirebaseRepository()
    private var isRegisterMode = false
    private var selectedRole: String? = null // "RESPONSABLE" or "AGENT"
    
    private var arrivalDateMs: Long = System.currentTimeMillis()
    private var birthDateMs: Long = System.currentTimeMillis()
    private val calendar = Calendar.getInstance()
    private val currencies = arrayOf("MRU", "CFA")
    private val breeds = arrayOf("Lohmann Brown", "Isa Brown", "Leghorn", "Rhode Island Red", "SASSO / Améliorée")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupDatePickers()
        setupCurrencyDropdown()
        setupBreedDropdown()
        
        handleIntentData(intent)
        
        binding.tvAppVersionLogin.text = "v${BuildConfig.VERSION_NAME}"
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentData(intent)
    }

    private fun handleIntentData(intent: Intent?) {
        val data: Uri? = intent?.data
        val inviteCode = intent?.getStringExtra("inviteCode") ?: data?.getQueryParameter("code")
        
        if (inviteCode != null) {
            isRegisterMode = true
            selectedRole = "AGENT"
            updateUIState()
            binding.etFarmCode.setText(inviteCode)
            binding.etFarmCode.isEnabled = false
            Toast.makeText(this, "Invitation détectée : Rôle Agent configuré", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupUI() {
        binding.btnToggleRegister.setOnClickListener {
            if (!isRegisterMode) {
                // On veut créer une ferme -> On passe directement au formulaire responsable
                isRegisterMode = true
                selectedRole = "RESPONSABLE"
                updateUIState()
            } else {
                // Retour à la connexion
                isRegisterMode = false
                selectedRole = null
                updateUIState()
            }
        }

        binding.btnChoiceResponsable.setOnClickListener {
            selectedRole = "RESPONSABLE"
            updateUIState()
        }

        binding.btnChoiceAgent.setOnClickListener {
            selectedRole = "AGENT"
            updateUIState()
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Veuillez remplir email et mot de passe", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isRegisterMode) {
                handleRegistration()
            } else {
                handleLogin(email, password)
            }
        }
    }

    private fun setupDatePickers() {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        binding.etArrivalDate.setText(sdf.format(Date()))
        binding.etBirthDate.setText(sdf.format(Date()))

        binding.etArrivalDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                val cal = Calendar.getInstance()
                cal.set(y, m, d)
                arrivalDateMs = cal.timeInMillis
                binding.etArrivalDate.setText(sdf.format(cal.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.etBirthDate.setOnClickListener {
            DatePickerDialog(this, { _, y, m, d ->
                val cal = Calendar.getInstance()
                cal.set(y, m, d)
                birthDateMs = cal.timeInMillis
                binding.etBirthDate.setText(sdf.format(cal.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun setupCurrencyDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, currencies)
        binding.actvCurrencySignup.setAdapter(adapter)
        binding.actvCurrencySignup.setText(currencies[0], false)
    }

    private fun setupBreedDropdown() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, breeds)
        binding.actvHenBreed.setAdapter(adapter)
        binding.actvHenBreed.setText(breeds[0], false)
    }

    private fun updateUIState() {
        if (!isRegisterMode) {
            // Mode Connexion
            binding.tvTitle.text = "Connexion"
            binding.layoutRegisterChoices.visibility = View.GONE
            binding.tilUsername.visibility = View.GONE
            binding.tilEmail.visibility = View.VISIBLE
            binding.tilPassword.visibility = View.VISIBLE
            binding.tilFarmCode.visibility = View.GONE
            binding.layoutFarmInfoFields.visibility = View.GONE
            binding.btnLogin.visibility = View.VISIBLE
            binding.btnLogin.text = "Se connecter"
            binding.btnToggleRegister.text = "CRÉER MA FERME"
        } else {
            // Mode Inscription
            binding.tvTitle.text = "Inscription"
            binding.btnToggleRegister.text = "Déjà un compte ? Se connecter"
            
            if (selectedRole == null) {
                // Étape de choix
                binding.layoutRegisterChoices.visibility = View.VISIBLE
                binding.tilUsername.visibility = View.GONE
                binding.tilEmail.visibility = View.GONE
                binding.tilPassword.visibility = View.GONE
                binding.tilFarmCode.visibility = View.GONE
                binding.layoutFarmInfoFields.visibility = View.GONE
                binding.btnLogin.visibility = View.GONE
            } else {
                // Formulaire direct
                binding.layoutRegisterChoices.visibility = View.GONE
                binding.tilUsername.visibility = View.VISIBLE
                binding.tilEmail.visibility = View.VISIBLE
                binding.tilPassword.visibility = View.VISIBLE
                binding.btnLogin.visibility = View.VISIBLE
                binding.btnLogin.text = "S'inscrire"
                
                if (selectedRole == "RESPONSABLE") {
                    binding.layoutFarmInfoFields.visibility = View.VISIBLE
                    binding.tilFarmCode.visibility = View.GONE
                } else {
                    binding.layoutFarmInfoFields.visibility = View.GONE
                    binding.tilFarmCode.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun handleLogin(email: String, password: String) {
        vm.login(email, password) { success, msgOrRole, userId ->
            if (!success) {
                val errorMsg = when(msgOrRole) {
                    "COMPTE_DESACTIVE" -> "Votre compte a été désactivé."
                    "VALIDATION_REQUIS_EXPIRRE" -> "Validation requise. Le délai de 24h est dépassé. Contactez hadietou@gmail.com"
                    else -> msgOrRole
                }
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            } else {
                lifecycleScope.launch {
                    firebaseRepo.recordLogin(userId, email.split("@")[0])
                    goToDashboard(msgOrRole, userId)
                }
            }
        }
    }

    private fun handleRegistration() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val username = binding.etUsername.text.toString().trim()
        
        if (username.isEmpty()) {
            Toast.makeText(this, "Nom requis", Toast.LENGTH_SHORT).show()
            return
        }

        if (selectedRole == "RESPONSABLE") {
            val farmName = binding.etFarmName.text.toString().trim()
            val hensCount = binding.etHensCount.text.toString().toIntOrNull() ?: 0
            val breed = binding.actvHenBreed.text.toString().trim()
            val currency = binding.actvCurrencySignup.text.toString()

            if (farmName.isEmpty() || hensCount <= 0) {
                Toast.makeText(this, "Informations de ferme manquantes", Toast.LENGTH_SHORT).show()
                return
            }

            vm.register(email, password, username, "RESPONSABLE", farmName, "", hensCount, breed, arrivalDateMs, birthDateMs, currency) { success, msg ->
                if (success) {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    goToDashboard("RESPONSABLE", FirebaseAuth.getInstance().currentUser?.uid ?: "")
                }
                else Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        } else if (selectedRole == "AGENT") {
            val farmCode = binding.etFarmCode.text.toString().trim()
            if (farmCode.isEmpty()) {
                Toast.makeText(this, "Code de ferme requis", Toast.LENGTH_SHORT).show()
                return
            }
            vm.register(email, password, username, "AGENT", "", farmCode) { success, msg ->
                if (success) goToDashboard("AGENT", FirebaseAuth.getInstance().currentUser?.uid ?: "")
                else Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun goToDashboard(role: String, userId: String) {
        val intent = Intent(this, DashboardActivity::class.java)
        intent.putExtra("role", role)
        intent.putExtra("userIdString", userId)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
