package com.hadietou.poulailler.util

import java.util.*
import kotlin.math.*

/**
 * Calcul du lever/coucher du soleil (équation solaire classique, sans dépendance réseau) pour
 * une date et des coordonnées données. Sert au calcul de l'heure d'extinction des lampes
 * (Dashboard) et à la page Météo & Climat (tableau des heures de lever/coucher sur la semaine).
 */
object SunUtils {
    // Nouakchott-centre : repli utilisé quand la ferme n'a pas encore renseigné sa localisation.
    private const val DEFAULT_LATITUDE = 18.07
    private const val DEFAULT_LONGITUDE = -15.95
    private const val ZENITH = 90.83

    /** @param sunriseHour, [sunsetHour] : heure locale (0-24, Mauritanie = UTC+0). */
    data class SunTimes(val sunriseHour: Double, val sunsetHour: Double) {
        /** Durée du jour naturel, en heures (gère le passage éventuel de minuit). */
        val daylightHours: Double get() = ((sunsetHour - sunriseHour) + 24.0) % 24.0
    }

    /** Lever/coucher du soleil pour le jour de [calendar] aux coordonnées données. Null si non calculable (cas polaires, inapplicable ici). */
    fun computeSunTimes(calendar: Calendar, latitude: Double = DEFAULT_LATITUDE, longitude: Double = DEFAULT_LONGITUDE): SunTimes? {
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR).toDouble()
        val lngHour = longitude / 15.0
        val tRise = dayOfYear + ((6.0 - lngHour) / 24.0)
        val tSet = dayOfYear + ((18.0 - lngHour) / 24.0)
        val sunrise = calculateTime(tRise, lngHour, isSunrise = true, latitude = latitude) ?: return null
        val sunset = calculateTime(tSet, lngHour, isSunrise = false, latitude = latitude) ?: return null
        return SunTimes(sunrise, sunset)
    }

    /**
     * @param lightingHours nombre d'heures d'éclairage artificiel après le lever du soleil
     * (photopériode), réglable depuis Paramètres. 15h par défaut.
     */
    fun getExtinctionTime(lightingHours: Int = 15, latitude: Double = DEFAULT_LATITUDE, longitude: Double = DEFAULT_LONGITUDE): String {
        val sun = computeSunTimes(Calendar.getInstance(), latitude, longitude) ?: return "--:--"
        return formatHour(extinctionHour(sun.sunriseHour, lightingHours))
    }

    /** Heure d'extinction (0-24) = lever du soleil + photopériode réglée. */
    fun extinctionHour(sunriseHour: Double, lightingHours: Int): Double {
        var extinction = sunriseHour + lightingHours
        if (extinction >= 24.0) extinction -= 24.0
        return extinction
    }

    fun formatHour(value: Double): String {
        val h = value.toInt()
        val m = ((value - h) * 60).toInt()
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    /** Formatte une durée en heures décimales (ex: 12.55) en "12h33". */
    fun formatDuration(hours: Double): String {
        val h = hours.toInt()
        val m = ((hours - h) * 60).toInt()
        return "${h}h${String.format(Locale.US, "%02d", m)}"
    }

    private fun calculateTime(t: Double, lngHour: Double, isSunrise: Boolean, latitude: Double): Double? {
        val M = (0.9856 * t) - 3.2891
        var L = M + (1.916 * sin(Math.toRadians(M))) + (0.020 * sin(Math.toRadians(2.0 * M))) + 282.634
        L %= 360.0
        if (L < 0) L += 360.0

        var RA = Math.toDegrees(atan(0.91764 * tan(Math.toRadians(L))))
        RA %= 360.0
        if (RA < 0) RA += 360.0

        val lQuadrant = floor(L / 90.0) * 90.0
        val raQuadrant = floor(RA / 90.0) * 90.0
        RA += (lQuadrant - raQuadrant)
        RA /= 15.0

        val sinDec = 0.39782 * sin(Math.toRadians(L))
        val cosDec = cos(asin(sinDec))

        val cosH = (cos(Math.toRadians(ZENITH)) - (sinDec * sin(Math.toRadians(latitude)))) / (cosDec * cos(Math.toRadians(latitude)))

        if (cosH > 1 || cosH < -1) return null

        val H = if (isSunrise) {
            360.0 - Math.toDegrees(acos(cosH))
        } else {
            Math.toDegrees(acos(cosH))
        }
        val hValue = H / 15.0

        val T = hValue + RA - (0.06571 * t) - 6.622
        var UT = T - lngHour
        UT %= 24.0
        if (UT < 0) UT += 24.0

        return UT
    }
}
