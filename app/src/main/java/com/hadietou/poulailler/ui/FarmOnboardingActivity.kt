package com.hadietou.poulailler.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hadietou.poulailler.data.Invitation
import com.hadietou.poulailler.data.User
import com.hadietou.poulailler.databinding.ActivityFarmOnboardingBinding
import com.hadietou.poulailler.databinding.DialogCreateFarmBinding
import com.hadietou.poulailler.databinding.ItemInvitationBinding
import com.hadietou.poulailler.repository.FirebaseRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Étape intermédiaire après l'inscription (ou une connexion) tant que l'utilisateur n'a ni
 * créé ni rejoint de ferme (voir [com.hadietou.poulailler.data.User.ROLE_UNASSIGNED] et la note
 * d'architecture dans FirebaseRepository sur le nouveau parcours d'inscription).
 *
 * Remplace l'ancien flux où la création de ferme se faisait directement sur l'écran de
 * connexion : celui-ci ne propose désormais plus que la création de compte, la ferme (créée
 * ou rejointe via une invitation) se choisit ici, une fois connecté.
 */
class FarmOnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFarmOnboardingBinding
    private val firebaseRepo = FirebaseRepository()
    private var userId: String? = null

    private val countries = arrayOf("Mauritanie", "Sénégal", "Mali", "Guinée", "Côte d'Ivoire", "Bénin", "Togo", "Burkina Faso", "Niger")
    private val currencies = arrayOf("MRU", "CFA")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFarmOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        userId = intent.getStringExtra("userIdString") ?: FirebaseAuth.getInstance().currentUser?.uid

        loadProfileAndInvitations()

        binding.btnCreateFarm.setOnClickListener { showCreateFarmDialog() }
        binding.btnContinueToDashboard.setOnClickListener {
            lifecycleScope.launch {
                val profile = firebaseRepo.getUserProfile(userId ?: return@launch)
                goToDashboard(profile?.role ?: "AGENT")
            }
        }
        binding.tvLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(android.content.Intent(this, LoginActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            finish()
        }
    }

    private fun loadProfileAndInvitations() {
        lifecycleScope.launch {
            val uid = userId ?: return@launch
            val profile = firebaseRepo.getUserProfile(uid)
            withContext(Dispatchers.Main) {
                binding.tvWelcome.text = "Bienvenue, ${profile?.username ?: "!"} !"
                // Un utilisateur ayant déjà une ferme active (invité une 2e fois, ou qui
                // revient sans avoir traité une invitation) ne doit pas rester bloqué ici :
                // on lui propose de créer une AUTRE ferme, et un bouton pour continuer.
                if (profile != null && profile.role != User.ROLE_UNASSIGNED) {
                    binding.tvCreateFarmHeading.text = "Créer une autre ferme ?"
                    binding.tvCreateFarmSubtitle.text = "Vous pouvez aussi créer une nouvelle ferme et en devenir responsable."
                    binding.btnContinueToDashboard.visibility = View.VISIBLE
                }
            }

            firebaseRepo.getPendingInvitationsFlow(profile?.phoneNumber).collectLatest { invitations ->
                withContext(Dispatchers.Main) {
                    binding.layoutInvitationsSection.visibility = if (invitations.isEmpty()) View.GONE else View.VISIBLE
                    binding.containerInvitations.removeAllViews()
                    invitations.forEach { invitation ->
                        binding.containerInvitations.addView(buildInvitationCard(invitation))
                    }
                }
            }
        }
    }

    private fun buildInvitationCard(invitation: Invitation): View {
        val itemBinding = ItemInvitationBinding.inflate(LayoutInflater.from(this))
        itemBinding.tvInvitationFarmName.text = invitation.farmName
        itemBinding.tvInvitationDetail.text = "Invité(e) par ${invitation.invitedByUsername} à rejoindre en tant qu'agent"

        itemBinding.btnAcceptInvitation.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    firebaseRepo.acceptInvitation(invitation)
                    val profile = firebaseRepo.getUserProfile(userId ?: return@launch)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FarmOnboardingActivity, "Ferme rejointe !", Toast.LENGTH_SHORT).show()
                        goToDashboard(profile?.role ?: "AGENT")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FarmOnboardingActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        itemBinding.btnDeclineInvitation.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    firebaseRepo.declineInvitation(invitation)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FarmOnboardingActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        return itemBinding.root
    }

    private fun showCreateFarmDialog() {
        val dialogBinding = DialogCreateFarmBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this).setView(dialogBinding.root).create()

        val countryAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, countries)
        dialogBinding.actvCountry.setAdapter(countryAdapter)
        dialogBinding.actvCountry.setText(countries[0], false)

        val currencyAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, currencies)
        dialogBinding.actvCurrency.setAdapter(currencyAdapter)
        dialogBinding.actvCurrency.setText(currencies[0], false)

        dialogBinding.actvCountry.setOnItemClickListener { _, _, position, _ ->
            dialogBinding.actvCurrency.setText(if (countries[position] == "Mauritanie") "MRU" else "CFA", false)
        }

        dialogBinding.btnConfirmCreateFarm.setOnClickListener {
            val farmName = dialogBinding.etFarmName.text.toString().trim()
            val city = dialogBinding.etCity.text.toString().trim()
            val country = dialogBinding.actvCountry.text.toString()
            val currency = dialogBinding.actvCurrency.text.toString()

            if (farmName.isEmpty() || city.isEmpty()) {
                Toast.makeText(this, "Nom de ferme et ville requis", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val uid = userId ?: return@launch
                    val profile = firebaseRepo.getUserProfile(uid)
                    val code = firebaseRepo.createFarmExtended(
                        farmName, currency, profile?.username ?: "Responsable", profile?.email ?: "", country, city
                    )
                    withContext(Dispatchers.Main) {
                        dialog.dismiss()
                        Toast.makeText(this@FarmOnboardingActivity, "Ferme créée ! Code : $code", Toast.LENGTH_LONG).show()
                        goToDashboard("RESPONSABLE")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@FarmOnboardingActivity, e.message ?: "Erreur", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        dialog.show()
    }

    private fun goToDashboard(role: String) {
        val intent = android.content.Intent(this, DashboardActivity::class.java)
        intent.putExtra("role", role)
        intent.putExtra("userIdString", userId)
        intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
