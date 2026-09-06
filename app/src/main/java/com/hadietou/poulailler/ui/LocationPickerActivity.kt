package com.hadietou.poulailler.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hadietou.poulailler.databinding.ActivityLocationPickerBinding
import com.hadietou.poulailler.network.GeocodingResult
import com.hadietou.poulailler.network.RetrofitClient
import kotlinx.coroutines.launch
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import java.util.Locale

/**
 * Permet de choisir la localité de la ferme de deux façons complémentaires :
 * - en tapant un nom (recherche via [com.hadietou.poulailler.network.GeocodingApiService],
 *   l'API de géocodage gratuite d'Open-Meteo) ;
 * - en déplaçant la carte OpenStreetMap sous le repère fixe, pour une position précise
 *   quand la localité exacte n'existe pas dans le référentiel de géocodage.
 * Retourne les coordonnées choisies (et le nom trouvé le cas échéant) au caller.
 */
class LocationPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLocationPickerBinding

    // Nom de la localité associé à la position actuelle du repère, uniquement quand elle
    // provient d'un résultat de recherche. Invalidé dès que l'utilisateur déplace la carte
    // à la main, pour ne jamais renvoyer un nom qui ne correspond plus à la position réelle.
    private var pickedLocalityName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLocationPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val initialLat = intent.getDoubleExtra(EXTRA_LAT, DEFAULT_LAT)
        val initialLon = intent.getDoubleExtra(EXTRA_LON, DEFAULT_LON)
        pickedLocalityName = intent.getStringExtra(EXTRA_LOCALITY)

        setupMap(initialLat, initialLon)
        updateCoordsLabel(initialLat, initialLon)

        binding.btnSearchLocality.setOnClickListener { searchLocality() }
        binding.etSearchLocality.setOnEditorActionListener { _, _, _ -> searchLocality(); true }
        binding.btnConfirmLocation.setOnClickListener { confirmLocation() }
    }

    private fun setupMap(lat: Double, lon: Double) {
        binding.mapView.setTileSource(TileSourceFactory.MAPNIK)
        binding.mapView.setMultiTouchControls(true)
        binding.mapView.zoomController.setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        binding.mapView.controller.setZoom(DEFAULT_ZOOM)
        binding.mapView.controller.setCenter(GeoPoint(lat, lon))

        binding.mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                pickedLocalityName = null
                val center = binding.mapView.mapCenter
                updateCoordsLabel(center.latitude, center.longitude)
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean = false
        })
    }

    private fun searchLocality() {
        val query = binding.etSearchLocality.text?.toString()?.trim().orEmpty()
        if (query.isEmpty()) return

        lifecycleScope.launch {
            try {
                val results = RetrofitClient.geocodingApi.search(query).results.orEmpty()
                when {
                    results.isEmpty() -> Toast.makeText(
                        this@LocationPickerActivity,
                        "Aucune localité trouvée pour \"$query\"",
                        Toast.LENGTH_SHORT
                    ).show()
                    results.size == 1 -> selectResult(results.first())
                    else -> showResultsDialog(results)
                }
            } catch (e: Exception) {
                Log.e("LocationPicker", "Error searching locality", e)
                Toast.makeText(this@LocationPickerActivity, "Erreur de recherche : ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showResultsDialog(results: List<GeocodingResult>) {
        val labels = results.map { it.displayLabel }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Choisir une localité")
            .setItems(labels) { _, index -> selectResult(results[index]) }
            .show()
    }

    private fun selectResult(result: GeocodingResult) {
        pickedLocalityName = result.name
        binding.mapView.controller.setCenter(GeoPoint(result.latitude, result.longitude))
        updateCoordsLabel(result.latitude, result.longitude)
    }

    private fun updateCoordsLabel(lat: Double, lon: Double) {
        binding.tvPickedCoords.text = String.format(Locale.US, "%.4f, %.4f", lat, lon)
    }

    private fun confirmLocation() {
        val center = binding.mapView.mapCenter
        val resultIntent = Intent().apply {
            putExtra(EXTRA_LAT, center.latitude)
            putExtra(EXTRA_LON, center.longitude)
            putExtra(EXTRA_LOCALITY, pickedLocalityName)
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
    }

    companion object {
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LON = "extra_lon"
        const val EXTRA_LOCALITY = "extra_locality"
        private const val DEFAULT_LAT = 18.0858
        private const val DEFAULT_LON = -15.9785
        private const val DEFAULT_ZOOM = 12.0
    }
}
