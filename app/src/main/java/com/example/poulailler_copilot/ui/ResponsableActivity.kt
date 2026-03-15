package com.example.poulailler_copilot.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.User
import com.example.poulailler_copilot.databinding.ActivityResponsableBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ResponsableActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResponsableBinding
    private val vm: ResponsableViewModel by viewModels()

    private var agentsList: List<User> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResponsableBinding.inflate(layoutInflater)
        setContentView(binding.root)

        vm.agents.observe(this) { list ->
            agentsList = list
            val names = list.map { "${it.username} (${if (it.active) "Actif" else "Inactif"})" }
            binding.lvAgents.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, names)
        }

        vm.loadAgents()
        loadLoginHistory()

        binding.btnCreateAgent.setOnClickListener {
            val username = binding.etAgentUsername.text.toString().trim()
            val password = binding.etAgentPassword.text.toString().trim()
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Champs agent vides", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            vm.createAgent(username, password) {
                Toast.makeText(this, "Agent créé", Toast.LENGTH_SHORT).show()
                binding.etAgentUsername.text.clear()
                binding.etAgentPassword.text.clear()
            }
        }

        binding.btnToggleActive.setOnClickListener {
            val pos = binding.lvAgents.checkedItemPosition
            if (pos == -1 || pos >= agentsList.size) {
                Toast.makeText(this, "Sélectionnez un agent", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val agent = agentsList[pos]
            vm.setAgentActive(agent.id, !agent.active)
        }

        binding.btnResetPassword.setOnClickListener {
            val pos = binding.lvAgents.checkedItemPosition
            if (pos == -1 || pos >= agentsList.size) {
                Toast.makeText(this, "Sélectionnez un agent", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val agent = agentsList[pos]
            vm.resetPassword(agent.id, "1234")
            Toast.makeText(this, "Mot de passe réinitialisé à 1234", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadLoginHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@ResponsableActivity)
            val logins = db.loginDao().getAll()
            
            val displayList = logins.map { entry ->
                val user = db.userDao().getById(entry.userId)
                val username = user?.username ?: "Inconnu"
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                "Connexion: $username le ${sdf.format(Date(entry.timestamp))}"
            }

            withContext(Dispatchers.Main) {
                binding.lvGlobalHistory.adapter = ArrayAdapter(this@ResponsableActivity, android.R.layout.simple_list_item_1, displayList)
            }
        }
    }
}
