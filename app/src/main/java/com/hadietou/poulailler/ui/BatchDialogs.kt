package com.hadietou.poulailler.ui

import android.app.DatePickerDialog
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.hadietou.poulailler.data.Batch
import com.hadietou.poulailler.databinding.DialogAddBatchBinding
import java.text.SimpleDateFormat
import java.util.*

/**
 * Dialogues de gestion des lots (création, archivage, suppression), utilisés par la page
 * « Mon exploitation ». Regroupés ici pour ne pas alourdir l'écran qui les affiche.
 */
class BatchDialogs(
    private val activity: AppCompatActivity,
    private val viewModel: BatchViewModel
) {
    private val calendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    private val layingBreeds = arrayOf("Lohmann Brown", "Isa Brown", "Leghorn", "Rhode Island Red", "SASSO / Améliorée")
    private val chairBreeds = arrayOf("COBB 500 (Chair)", "ROSS 308 (Chair)", "SASSO / Améliorée")
    private val batchTypes = arrayOf("PONDEUSE", "CHAIR")

    private fun breedsFor(type: String) = if (type == "CHAIR") chairBreeds else layingBreeds

    fun isValidType(type: String?) = type != null && type in batchTypes

    fun showBlocking() {
        AlertDialog.Builder(activity)
            .setTitle("Mode Lecture Seule")
            .setMessage("Veuillez envoyer un email à hadietou@gmail.com pour lui demander de valider la ferme afin de continuer à utiliser l'application. En attendant, vous pouvez uniquement consulter vos données.")
            .setCancelable(true)
            .setPositiveButton("OK", null)
            .show()
    }

    fun showAddBatchDialog(presetType: String? = null) {
        val dialogBinding = DialogAddBatchBinding.inflate(LayoutInflater.from(activity))
        val dialog = AlertDialog.Builder(activity)
            .setView(dialogBinding.root)
            .create()

        val initialType = presetType ?: batchTypes[0]
        dialogBinding.actvBatchType.setAdapter(ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, batchTypes))
        dialogBinding.actvBatchType.setText(initialType, false)

        fun applyBreedsFor(type: String) {
            val list = breedsFor(type)
            dialogBinding.actvHenBreed.setAdapter(ArrayAdapter(activity, android.R.layout.simple_dropdown_item_1line, list))
            dialogBinding.actvHenBreed.setText(list[0], false)
        }
        applyBreedsFor(initialType)
        dialogBinding.actvBatchType.setOnItemClickListener { _, _, position, _ -> applyBreedsFor(batchTypes[position]) }

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
            val typeLot = dialogBinding.actvBatchType.text.toString().trim()
            val providerName = dialogBinding.etProviderName.text.toString().trim()
            val providerPhone = dialogBinding.etProviderPhone.text.toString().trim()

            if (name.isEmpty() || countStr.isEmpty() || breed.isEmpty() || typeLot.isEmpty()) {
                Toast.makeText(activity, "Veuillez remplir tous les champs obligatoires", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.addBatch(
                name,
                countStr.toInt(),
                breed,
                arrivalDate,
                birthDate,
                typeLot,
                providerName.ifEmpty { null },
                providerPhone.ifEmpty { null }
            )
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showDatePicker(onDateSelected: (Long) -> Unit) {
        DatePickerDialog(
            activity,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                onDateSelected(calendar.timeInMillis)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun showBatchOptions(batch: Batch) {
        val options = if (batch.status == "ACTIVE") {
            arrayOf("Archiver le lot", "Supprimer le lot")
        } else {
            arrayOf("Désarchiver le lot", "Supprimer le lot")
        }

        AlertDialog.Builder(activity)
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
        AlertDialog.Builder(activity)
            .setTitle("Supprimer le lot")
            .setMessage("Voulez-vous vraiment supprimer définitivement le lot ${batch.name} ? Cette action est irréversible.")
            .setPositiveButton("Supprimer") { _, _ ->
                batch.firestoreId?.let { viewModel.deleteBatch(it) }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }
}
