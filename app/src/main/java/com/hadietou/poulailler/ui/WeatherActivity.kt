package com.hadietou.poulailler.ui

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.hadietou.poulailler.R
import com.hadietou.poulailler.data.FarmInfo
import com.hadietou.poulailler.databinding.ActivityWeatherBinding
import com.hadietou.poulailler.network.WeatherUtils
import com.hadietou.poulailler.repository.FirebaseRepository
import com.hadietou.poulailler.util.SunUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Météo & Climat (page dédiée). Accessible uniquement depuis le tableau de bord (carte
 * "Santé & Mortalité" / alerte canicule) et le hub Suivi Sanitaire — pas de tiroir ici, même
 * logique que les autres "Autres Outils" (voir la note d'architecture dans ObservationActivity) :
 * cette page n'a pas sa place dans le tiroir global.
 *
 * Contenu déplacé depuis VaccineActivity (qui ne s'appelle plus "Suivi Sanitaire" mais
 * "Vaccinations & Rappels" et n'a donc plus vocation à héberger la météo/l'éclairage/la
 * biosécurité générale).
 */
class WeatherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWeatherBinding
    private val firebaseRepo = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWeatherBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        fetchWeatherForecast()
        loadLightingSection()

        if (intent.getBooleanExtra("scrollToLighting", false)) {
            binding.root.post {
                binding.nestedScrollView.smoothScrollTo(0, binding.cardLightingLogic.top)
            }
        }
    }

    private fun fetchWeatherForecast() {
        binding.weatherProgressBar.visibility = View.VISIBLE
        binding.layoutWeatherContent.visibility = View.GONE
        binding.tvWeatherInfo.visibility = View.GONE
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val info = firebaseRepo.getFarmInfo()
                val response = WeatherUtils.fetchForecast(info?.latitude, info?.longitude)
                val daily = response.daily
                val offset = info?.weatherTempOffsetCelsius ?: FarmInfo.DEFAULT_WEATHER_TEMP_OFFSET
                val maxTemperatures = WeatherUtils.applyOffset(daily.maxTemperatures, offset)
                val minTemperatures = WeatherUtils.applyOffset(daily.minTemperatures, offset)

                val peakIndex = maxTemperatures.indices.maxByOrNull { maxTemperatures[it] } ?: 0
                val globalMin = minTemperatures.minOrNull() ?: maxTemperatures.min()
                val globalMax = maxTemperatures.max()
                val inFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val dayFmt = SimpleDateFormat("EEE", Locale.FRENCH)
                val dateFmt = SimpleDateFormat("dd/MM", Locale.getDefault())

                withContext(Dispatchers.Main) {
                    binding.weatherProgressBar.visibility = View.GONE
                    binding.tvWeatherTitle.text = "MÉTÉO ${info?.locality?.uppercase()?.let { "$it " } ?: "NOUAKCHOTT "}(5 JOURS)"
                    binding.tvWeatherMin.text = "${Math.round(globalMin)}°C"
                    binding.tvWeatherMax.text = "${Math.round(globalMax)}°C"
                    binding.tvWeatherPeakDay.text = daily.time.getOrNull(peakIndex)?.let {
                        dateFmt.format(inFmt.parse(it) ?: Date())
                    } ?: "-"

                    binding.layoutWeatherDays.removeAllViews()
                    for (i in daily.time.indices) {
                        val parsedDate = try { inFmt.parse(daily.time[i]) } catch (e: Exception) { null }
                        val maxTemp = maxTemperatures.getOrNull(i) ?: 0.0
                        val minTemp = minTemperatures.getOrNull(i)
                        val isPeak = i == peakIndex
                        binding.layoutWeatherDays.addView(
                            buildWeatherDayView(
                                dayLabel = parsedDate?.let { dayFmt.format(it).replaceFirstChar { c -> c.uppercase() } } ?: "-",
                                dateLabel = parsedDate?.let { dateFmt.format(it) } ?: daily.time[i],
                                maxTemp = maxTemp,
                                minTemp = minTemp,
                                isPeak = isPeak
                            )
                        )
                    }
                    binding.layoutWeatherContent.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.weatherProgressBar.visibility = View.GONE
                    binding.tvWeatherInfo.visibility = View.VISIBLE
                    binding.tvWeatherInfo.text = "Impossible de charger la météo."
                }
            }
        }
    }

    /**
     * Lever/coucher du soleil, durée du jour et heure d'extinction des lampes — calcul purement
     * local (équation solaire, voir [SunUtils]), indépendant de l'appel météo réseau ci-dessus :
     * cette section reste donc utilisable même si la météo échoue à charger.
     */
    private fun loadLightingSection() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val info = firebaseRepo.getFarmInfo()
                val lightingHours = info?.lightingHoursAfterSunrise ?: FarmInfo.DEFAULT_LIGHTING_HOURS
                val lat = info?.latitude
                val lon = info?.longitude
                val dateFmt = SimpleDateFormat("dd/MM", Locale.getDefault())

                val today = Calendar.getInstance()
                val todaySun = if (lat != null && lon != null) {
                    SunUtils.computeSunTimes(today, lat, lon)
                } else {
                    SunUtils.computeSunTimes(today)
                }

                val weekRows = (0 until 7).mapNotNull { offset ->
                    val cal = today.clone() as Calendar
                    cal.add(Calendar.DAY_OF_YEAR, offset)
                    val sun = if (lat != null && lon != null) SunUtils.computeSunTimes(cal, lat, lon) else SunUtils.computeSunTimes(cal)
                    sun?.let { Triple(dateFmt.format(cal.time), it, offset == 0) }
                }

                withContext(Dispatchers.Main) {
                    binding.tvLightingTitle.text = "CONSEILS ÉCLAIRAGE (${lightingHours}H TOTAL)"
                    binding.tvLightingLogic.text = android.text.Html.fromHtml(
                        "Pour une ponte optimale, vos poules ont besoin de $lightingHours heures de lumière par jour.<br/><br/>" +
                            "<b>Logique :</b><br/>1. Allumez les lampes dès le coucher du soleil.<br/>" +
                            "2. Gardez-les allumées jusqu'à l'heure d'extinction ci-dessous (Lever du soleil + ${lightingHours}h).",
                        android.text.Html.FROM_HTML_MODE_LEGACY
                    )

                    if (todaySun != null) {
                        val extinction = SunUtils.extinctionHour(todaySun.sunriseHour, lightingHours)
                        val artificialHours = ((extinction - todaySun.sunsetHour) + 24.0) % 24.0

                        binding.tvSunrise.text = SunUtils.formatHour(todaySun.sunriseHour)
                        binding.tvSunset.text = SunUtils.formatHour(todaySun.sunsetHour)
                        binding.tvDaylightDuration.text = SunUtils.formatDuration(todaySun.daylightHours)
                        binding.tvExtinctionTime.text = SunUtils.formatHour(extinction)

                        binding.tvArtificialLightNeeded.visibility = View.VISIBLE
                        binding.tvArtificialLightNeeded.text = if (artificialHours > 0.01) {
                            "🔆 Lampes à garder allumées de ${SunUtils.formatHour(todaySun.sunsetHour)} (coucher) à " +
                                "${SunUtils.formatHour(extinction)} : ${SunUtils.formatDuration(artificialHours)} d'éclairage artificiel ce soir."
                        } else {
                            "☀️ Le jour naturel couvre déjà les $lightingHours h requises : aucun éclairage artificiel nécessaire aujourd'hui."
                        }
                    } else {
                        binding.tvArtificialLightNeeded.visibility = View.GONE
                    }

                    binding.layoutSunWeek.removeAllViews()
                    weekRows.forEach { (dateLabel, sun, isToday) ->
                        val extinction = SunUtils.extinctionHour(sun.sunriseHour, lightingHours)
                        binding.layoutSunWeek.addView(
                            buildSunWeekRow(
                                dateLabel = dateLabel,
                                sunrise = SunUtils.formatHour(sun.sunriseHour),
                                sunset = SunUtils.formatHour(sun.sunsetHour),
                                daylight = SunUtils.formatDuration(sun.daylightHours),
                                extinction = SunUtils.formatHour(extinction),
                                isToday = isToday
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvArtificialLightNeeded.visibility = View.GONE
                }
            }
        }
    }

    /** Construit une ligne du tableau "lever/coucher sur 7 jours", alignée sur les mêmes poids que l'en-tête. */
    private fun buildSunWeekRow(dateLabel: String, sunrise: String, sunset: String, daylight: String, extinction: String, isToday: Boolean): View {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val textColor = if (isToday) getColor(R.color.earthy_orange) else getColor(R.color.text_secondary)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, dp(6), 0, dp(6))
            if (isToday) {
                val bg = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(getColor(R.color.earthy_container))
                }
                background = bg
                setPadding(dp(4), dp(6), dp(4), dp(6))
            }
        }

        fun column(text: String, weight: Float, gravity: Int, bold: Boolean) {
            row.addView(TextView(this).apply {
                this.text = text
                textSize = 11f
                setTextColor(textColor)
                if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
                this.gravity = gravity
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
            })
        }

        column(if (isToday) "Auj. $dateLabel" else dateLabel, 10f, Gravity.START, true)
        column(sunrise, 11f, Gravity.CENTER, false)
        column(sunset, 11f, Gravity.CENTER, false)
        column(daylight, 11f, Gravity.CENTER, false)
        column(extinction, 12f, Gravity.END, true)

        return row
    }

    /** Construit une carte "jour" (date, icône, min/max) pour la rangée de prévisions météo. */
    private fun buildWeatherDayView(dayLabel: String, dateLabel: String, maxTemp: Double, minTemp: Double?, isPeak: Boolean): View {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val (emoji, tempColor) = when {
            maxTemp >= 40 -> "🔥" to getColor(R.color.error)
            maxTemp >= 35 -> "☀️" to getColor(R.color.earthy_orange)
            else -> "🌤️" to getColor(R.color.accent_blue)
        }

        val background = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(if (isPeak) getColor(R.color.error_container) else getColor(R.color.surface))
            setStroke(dp(1), if (isPeak) getColor(R.color.error) else getColor(R.color.off_white))
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            this.background = background
            setPadding(dp(2), dp(10), dp(2), dp(10))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(3); marginEnd = dp(3)
            }

            addView(TextView(context).apply {
                text = dayLabel
                textSize = 10f
                setTextColor(getColor(R.color.text_secondary))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            addView(TextView(context).apply {
                text = dateLabel
                textSize = 10f
                setTextColor(getColor(R.color.text_secondary))
            })
            addView(TextView(context).apply {
                text = emoji
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4)
                }
            })
            addView(TextView(context).apply {
                text = "${Math.round(maxTemp)}°"
                textSize = 15f
                setTextColor(tempColor)
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(2)
                }
            })
            if (minTemp != null) {
                addView(TextView(context).apply {
                    text = "${Math.round(minTemp)}°"
                    textSize = 11f
                    setTextColor(getColor(R.color.text_secondary))
                })
            }
            if (isPeak) {
                addView(TextView(context).apply {
                    text = "PIC"
                    textSize = 8f
                    setTextColor(getColor(R.color.error))
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    letterSpacing = 0.05f
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(4)
                    }
                })
            }
        }
    }
}
