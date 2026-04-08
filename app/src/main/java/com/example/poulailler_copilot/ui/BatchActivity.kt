package com.example.poulailler_copilot.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.poulailler_copilot.data.Batch
import com.example.poulailler_copilot.databinding.ActivityBatchBinding
import com.example.poulailler_copilot.databinding.DialogAddBatchBinding
import java.text.SimpleDateFormat
import java.util.*

class BatchActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBatchBinding
    private val viewModel: BatchViewModel by viewModels()
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private lateinit var adapter: BatchAdapter
    private val breeds = arrayOf("Lohmann Brown", "Isa Brown", "Leghorn", "Rhode Island Red", "SASSO / Améliorée")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBatchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = BatchAdapter { batch ->
            showBatchOptions(batch)
        }
        binding.rvBatches.layoutManager = LinearLayoutManager(this)
        binding.rvBatches.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.fabAddBatch.setOnClickListener {
            showAddBatchDialog()
        }
    }

    private fun showAddBatchDialog() {
        val dialogBinding = DialogAddBatchBinding.inflate(LayoutInflater.from(this))
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        val breedAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, breeds)
        dialogBinding.actvHenBreed.setAdapter(breedAdapter)
        dialogBinding.actvHenBreed.setText(breeds[0], false)

        var arrivalDate = System.currentTimeMillis()
        var birthDate = System.currentTimeMillis()

        dialogBinding.etArrivalDate.setText(dateFormat.format(Date(arrivalDate)))
        dialogBinding.etBirthDate.setText(dateFormat.format(Date(birthDate)))

        dialogBinding.etArrivalDate.setOnClickListener {
            showDatePicker { date ->
                arrivalDate = date
                dialogBinding.etArrivalDate.setText(dateFormat.format(Date(date)))
            }
        }

        dialogBinding.etBirthDate.setOnClickListener {
            showDatePicker { date ->
                birthDate = date
                dialogBinding.etBirthDate.setText(dateFormat.format(Date(date)))
            }
        }

        dialogBinding.btnSaveBatch.setOnClickListener {
            val name = dialogBinding.etBatchName.text.toString().trim()
            val countStr = dialogBinding.etHensCount.text.toString().trim()
            val breed = dialogBinding.actvHenBreed.text.toString().trim()

            if (name.isEmpty() || countStr.isEmpty() || breed.isEmpty()) {
                Toast.makeText(this, "Veuillez remplir tous les champs", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.addBatch(name, countStr.toInt(), breed, arrivalDate, birthDate)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDatePicker(onDateSelected: (Long) -> Unit) {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                onDateSelected(calendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showBatchOptions(batch: Batch) {
        val options = if (batch.status == "ACTIVE") {
            arrayOf("Archiver le lot", "Supprimer le lot")
        } else {
            arrayOf("Désarchiver le lot", "Supprimer le lot")
        }

        AlertDialog.Builder(this)
            .setTitle("Options du lot : ${batch.name}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewModel.toggleBatchStatus(batch)
                    1 -> showDeleteConfirmation(batch)
                }
            }
            .show()
    }

    private fun showDeleteConfirmation(batch: Batch) {
        AlertDialog.Builder(this)
            .setTitle("Supprimer le lot")
            .setMessage("Voulez-vous vraiment supprimer définitivement le lot ${batch.name} ? Cette action est irréversible.")
            .setPositiveButton("Supprimer") { _, _ ->
                batch.firestoreId?.let { viewModel.deleteBatch(it) }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun observeViewModel() {
        viewModel.allBatches.observe(this) { batches ->
            adapter.submitList(batches)
        }

        viewModel.operationSuccess.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Opération réussie", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Une erreur est survenue", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
