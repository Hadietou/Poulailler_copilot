package com.hadietou.poulailler.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hadietou.poulailler.BuildConfig
import com.hadietou.poulailler.data.User
import com.hadietou.poulailler.databinding.ActivityLoginBinding
import com.hadietou.poulailler.demo.DemoData
import com.hadietou.poulailler.demo.DemoMode
import com.hadietou.poulailler.repository.FirebaseRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Écran de connexion / inscription.
 *
 * Nouveau parcours (voir la note d'architecture dans FirebaseRepository) : l'inscription ne
 * demande plus qu'un nom et un numéro de téléphone (vérifié par SMS) — ni rôle, ni ferme, ni
 * code à saisir ici. Une fois connecté, [FarmOnboardingActivity] propose de créer sa propre
 * ferme ou d'accepter une invitation en attente. La connexion par email/mot de passe reste
 * inchangée pour les comptes existants.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val vm: LoginViewModel by viewModels()
    private val firebaseRepo = FirebaseRepository()
    private var isRegisterMode = false
    private var isCodeSent = false
    /** Numéro (E.164) pour lequel un code a été envoyé — capturé au moment de l'envoi. */
    private var pendingPhoneNumber: String? = null

    private val countryDialOptions = listOf(
        "🇲🇷 +222" to "+222",
        "🇸🇳 +221" to "+221",
        "🇲🇱 +223" to "+223",
        "🇬🇳 +224" to "+224",
        "🇨🇮 +225" to "+225",
        "🇧🇯 +229" to "+229",
        "🇹🇬 +228" to "+228",
        "🇧🇫 +226" to "+226",
        "🇳🇪 +227" to "+227",
        "🇩🇿 +213" to "+213",
        "🇦🇴 +244" to "+244",
        "🇧🇼 +267" to "+267",
        "🇧🇮 +257" to "+257",
        "🇨🇻 +238" to "+238",
        "🇨🇲 +237" to "+237",
        "🇨🇫 +236" to "+236",
        "🇹🇩 +235" to "+235",
        "🇰🇲 +269" to "+269",
        "🇨🇬 +242" to "+242",
        "🇨🇩 +243" to "+243",
        "🇩🇯 +253" to "+253",
        "🇪🇬 +20" to "+20",
        "🇬🇶 +240" to "+240",
        "🇪🇷 +291" to "+291",
        "🇸🇿 +268" to "+268",
        "🇪🇹 +251" to "+251",
        "🇬🇦 +241" to "+241",
        "🇬🇲 +220" to "+220",
        "🇬🇭 +233" to "+233",
        "🇬🇼 +245" to "+245",
        "🇰🇪 +254" to "+254",
        "🇱🇸 +266" to "+266",
        "🇱🇷 +231" to "+231",
        "🇱🇾 +218" to "+218",
        "🇲🇬 +261" to "+261",
        "🇲🇼 +265" to "+265",
        "🇲🇺 +230" to "+230",
        "🇲🇦 +212" to "+212",
        "🇲🇿 +258" to "+258",
        "🇳🇦 +264" to "+264",
        "🇳🇬 +234" to "+234",
        "🇷🇼 +250" to "+250",
        "🇸🇹 +239" to "+239",
        "🇸🇨 +248" to "+248",
        "🇸🇱 +232" to "+232",
        "🇸🇴 +252" to "+252",
        "🇿🇦 +27" to "+27",
        "🇸🇸 +211" to "+211",
        "🇸🇩 +249" to "+249",
        "🇹🇿 +255" to "+255",
        "🇹🇳 +216" to "+216",
        "🇺🇬 +256" to "+256",
        "🇿🇲 +260" to "+260",
        "🇿🇼 +263" to "+263",
        "🇨🇭 +41" to "+41",
        "🇫🇷 +33" to "+33"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Revenir ici (déconnexion, ou fin de la démo) signifie toujours qu'on quitte
        // toute session de démonstration en cours — jamais de fuite vers une vraie session.
        DemoMode.enabled = false

        setupUI()
        setupDropdowns()

        binding.tvAppVersionLogin.text = "v${BuildConfig.VERSION_NAME}"

        vm.checkExistingSession { success, msgOrRole, userId ->
            if (success) {
                goToDashboard(msgOrRole, userId)
            } else if (msgOrRole == "COMPTE_DESACTIVE" || msgOrRole == "VALIDATION_REQUIS_EXPIRRE") {
                val errorMsg = if (msgOrRole == "COMPTE_DESACTIVE") "Votre compte a été désactivé."
                    else "Validation requise. Le délai de 20 jours est dépassé. Contactez hadietou@gmail.com"
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupUI() {
        binding.btnToggleRegister.setOnClickListener {
            isRegisterMode = !isRegisterMode
            isCodeSent = false
            updateUIState()
        }

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Veuillez remplir email et mot de passe", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            handleLogin(email, password)
        }

        binding.btnSendCode.setOnClickListener { handleSendCode() }
        binding.btnVerifyCode.setOnClickListener { handleVerifyCode() }

        binding.btnViewDemo.setOnClickListener {
            DemoMode.enabled = true
            goToDashboard("RESPONSABLE", DemoData.USER_ID)
        }
    }

    private fun setupDropdowns() {
        val codeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, countryDialOptions.map { it.first })
        binding.actvCountryCode.setAdapter(codeAdapter)
        binding.actvCountryCode.setText(countryDialOptions[0].first, false)
    }

    private fun updateUIState() {
        // Le mode démo n'a de sens qu'à l'écran de connexion, pas au milieu d'une inscription.
        val demoVisibility = if (isRegisterMode) View.GONE else View.VISIBLE
        binding.dividerDemo.visibility = demoVisibility
        binding.btnViewDemo.visibility = demoVisibility
        binding.tvDemoHint.visibility = demoVisibility

        if (!isRegisterMode) {
            // Mode Connexion (email/mot de passe, inchangé)
            binding.tvTitle.text = "Connexion"
            binding.tilUsername.visibility = View.GONE
            binding.tilRegisterEmail.visibility = View.GONE
            binding.layoutPhoneRow.visibility = View.GONE
            binding.btnSendCode.visibility = View.GONE
            binding.layoutOtpSection.visibility = View.GONE
            binding.tilEmail.visibility = View.VISIBLE
            binding.tilPassword.visibility = View.VISIBLE
            binding.btnLogin.visibility = View.VISIBLE
            binding.btnToggleRegister.text = "Créer un compte"
        } else {
            // Mode téléphone : sert à la fois pour créer un compte ET pour se reconnecter
            // (un compte téléphone n'a pas de mot de passe — voir lastPhoneLoginWasNewAccount) ;
            // le titre reste donc neutre plutôt que de présumer une inscription.
            binding.tvTitle.text = "Connexion par téléphone"
            binding.btnToggleRegister.text = "Déjà un compte ? Se connecter"
            binding.tilEmail.visibility = View.GONE
            binding.tilPassword.visibility = View.GONE
            binding.btnLogin.visibility = View.GONE
            binding.tilUsername.visibility = View.VISIBLE
            binding.tilRegisterEmail.visibility = View.VISIBLE
            binding.layoutPhoneRow.visibility = View.VISIBLE
            binding.btnSendCode.visibility = View.VISIBLE
            binding.layoutOtpSection.visibility = if (isCodeSent) View.VISIBLE else View.GONE
        }
    }

    private fun handleLogin(email: String, password: String) {
        vm.login(email, password) { success, msgOrRole, userId ->
            if (!success) {
                val errorMsg = when (msgOrRole) {
                    "COMPTE_DESACTIVE" -> "Votre compte a été désactivé."
                    "VALIDATION_REQUIS_EXPIRRE" -> "Validation requise. Le délai de 20 jours est dépassé. Contactez hadietou@gmail.com"
                    else -> msgOrRole
                }
                Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
            } else {
                lifecycleScope.launch {
                    goToDashboard(msgOrRole, userId)
                }
            }
        }
    }

    private fun buildE164PhoneNumber(): String? {
        val dialCode = countryDialOptions.find { it.first == binding.actvCountryCode.text.toString() }?.second
            ?: countryDialOptions[0].second
        val digits = binding.etPhoneNumber.text.toString().filter { it.isDigit() }
        if (digits.isEmpty()) return null
        return "$dialCode$digits"
    }

    private fun handleSendCode() {
        val username = binding.etUsername.text.toString().trim()
        if (username.isEmpty()) {
            Toast.makeText(this, "Nom requis", Toast.LENGTH_SHORT).show()
            return
        }
        val phone = buildE164PhoneNumber()
        if (phone == null) {
            Toast.makeText(this, "Numéro de téléphone requis", Toast.LENGTH_SHORT).show()
            return
        }

        val email = binding.etRegisterEmail.text.toString().trim()
        if (email.isEmpty()) {
            // Un compte téléphone n'a pas de mot de passe : sans email, il n'y a aucun moyen
            // de récupérer le compte si ce numéro devient inaccessible. On le rappelle une
            // fois, mais on n'en fait pas un blocage — l'utilisateur peut continuer sans.
            AlertDialog.Builder(this)
                .setTitle("Ajouter un email ?")
                .setMessage("Sans email, vous ne pourrez récupérer votre compte qu'avec ce numéro de téléphone. Nous vous recommandons d'en ajouter un.")
                .setPositiveButton("Ajouter un email") { _, _ -> binding.etRegisterEmail.requestFocus() }
                .setNegativeButton("Continuer sans email") { _, _ -> proceedWithSendCode(username, phone, null) }
                .show()
            return
        }
        proceedWithSendCode(username, phone, email)
    }

    private fun proceedWithSendCode(username: String, phone: String, email: String?) {
        pendingPhoneNumber = phone
        binding.btnSendCode.isEnabled = false
        vm.startPhoneVerification(
            activity = this,
            phoneNumber = phone,
            onCodeSent = {
                runOnUiThread {
                    binding.btnSendCode.isEnabled = true
                    isCodeSent = true
                    binding.layoutOtpSection.visibility = View.VISIBLE
                    binding.tvOtpSentTo.text = "Code envoyé au $phone"
                    Toast.makeText(this, "Code SMS envoyé", Toast.LENGTH_SHORT).show()
                }
            },
            onAutoVerified = { credential ->
                runOnUiThread { binding.btnSendCode.isEnabled = true }
                vm.signUpWithAutoVerifiedCredential(credential, username, phone, email) { success, msgOrRole, uid ->
                    runOnUiThread {
                        if (success) {
                            showWelcomeToast()
                            goToDashboard(msgOrRole, uid)
                        } else Toast.makeText(this, msgOrRole, Toast.LENGTH_LONG).show()
                    }
                }
            },
            onFailed = { message ->
                runOnUiThread {
                    binding.btnSendCode.isEnabled = true
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    private fun handleVerifyCode() {
        val username = binding.etUsername.text.toString().trim()
        val phone = pendingPhoneNumber
        val email = binding.etRegisterEmail.text.toString().trim().ifEmpty { null }
        val code = binding.etOtpCode.text.toString().trim()
        if (phone == null) {
            Toast.makeText(this, "Redemandez un code.", Toast.LENGTH_SHORT).show()
            return
        }
        if (code.isEmpty()) {
            Toast.makeText(this, "Code requis", Toast.LENGTH_SHORT).show()
            return
        }
        vm.verifyOtpForSignup(code, username, phone, email) { success, msgOrRole, uid ->
            if (success) {
                showWelcomeToast()
                goToDashboard(msgOrRole, uid)
            } else Toast.makeText(this, msgOrRole, Toast.LENGTH_LONG).show()
        }
    }

    /** Message différencié selon qu'un compte vient d'être créé ou qu'on retrouve un compte existant. */
    private fun showWelcomeToast() {
        val message = if (vm.lastPhoneLoginWasNewAccount) "Compte créé avec succès !" else "Bon retour !"
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun goToDashboard(role: String, userId: String) {
        // Tant que l'utilisateur n'a ni créé ni rejoint de ferme (nouveau parcours
        // d'inscription : la création de compte ne fixe plus le rôle), on l'envoie choisir
        // plutôt que d'ouvrir un Dashboard sans ferme derrière. Un utilisateur ayant déjà
        // une ferme mais avec une NOUVELLE invitation en attente (ex: invité une 2e fois —
        // voir la note multi-fermes dans FirebaseRepository) passe aussi par cet écran,
        // sans quoi il ne verrait jamais cette invitation.
        if (role == User.ROLE_UNASSIGNED) {
            startFarmOnboarding(role, userId)
            return
        }
        lifecycleScope.launch {
            val profile = firebaseRepo.getUserProfile(userId)
            val hasPendingInvitation = firebaseRepo.getPendingInvitationsFlow(profile?.phoneNumber).first().isNotEmpty()
            if (hasPendingInvitation) startFarmOnboarding(role, userId) else startDashboard(role, userId)
        }
    }

    private fun startFarmOnboarding(role: String, userId: String) = startNextActivity(FarmOnboardingActivity::class.java, role, userId)
    private fun startDashboard(role: String, userId: String) = startNextActivity(DashboardActivity::class.java, role, userId)

    private fun startNextActivity(destination: Class<*>, role: String, userId: String) {
        val intent = Intent(this, destination)
        intent.putExtra("role", role)
        intent.putExtra("userIdString", userId)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
