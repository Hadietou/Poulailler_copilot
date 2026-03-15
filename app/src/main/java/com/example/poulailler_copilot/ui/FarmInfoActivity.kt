package com.example.poulailler_copilot.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.poulailler_copilot.data.AppDatabase
import com.example.poulailler_copilot.data.FarmInfo
import com.example.poulailler_copilot.databinding.ActivityFarmInfoBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class FarmInfoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFarmInfoBinding
    private val calendar = Calendar.getInstance()
    private var arrivalDateMs: Long = 0
    private var birthDateMs: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFarmInfoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadExistingData()

        binding.etArrivalDate.setOnClickListener {
            showDatePicker { date ->
                arrivalDateMs = date.time
                binding.etArrivalDate.setText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date))
            }
        }

        binding.etBirthDate.setOnClickListener {
            showDatePicker { date ->
                birthDateMs = date.time
                binding.etBirthDate.setText(SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(date))
            }
        }

        binding.btnSaveFarmInfo.setOnClickListener {
            saveData()
        }
    }

    private fun loadExistingData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@FarmInfoActivity)
            val info = db.farmInfoDao().getInfo()
            withContext(Dispatchers.Main) {
                info?.let {
                    binding.etFarmName.setText(it.farmName)
                    binding.etHensCount.setText(it.hensCount.toString())
                    binding.etHenBreed.setText(it.henBreed)
                    binding.etFeedInfo.setText(it.feedInfo)
                    
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    if (it.arrivalDate > 0) {
                        arrivalDateMs = it.arrivalDate
                        binding.etArrivalDate.setText(sdf.format(Date(it.arrivalDate)))
                    }
                    if (it.chickBirthDate > 0) {
                        birthDateMs = it.chickBirthDate
                        binding.etBirthDate.setText(sdf.format(Date(it.chickBirthDate)))
                    }
                }
            }
        }
    }

    private fun showDatePicker(onDateSelected: (Date) -> Unit) {
        DatePickerDialog(this, { _, year, month, dayOfMonth ->
            val cal = Calendar.getInstance()
            cal.set(year, month, dayOfMonth)
            onDateSelected(cal.time)
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun saveData() {
        val name = binding.etFarmName.text.toString()
        val count = binding.etHensCount.text.toString().toIntOrNull() ?: 0
        val breed = binding.etHenBreed.text.toString()
        val feed = binding.etFeedInfo.text.toString()

        if (name.isEmpty() || count <= 0) {
            Toast.makeText(this, "Veuillez saisir le nom et le nombre de poules", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@FarmInfoActivity)
            val info = FarmInfo(
                id = 1,
                farmName = name,
                hensCount = count,
                henBreed = breed,
                arrivalDate = arrivalDateMs,
                chickBirthDate = birthDateMs,
                feedInfo = feed
            )
            db.farmInfoDao().upsert(info)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@FarmInfoActivity, "Informations enregistrées", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}
