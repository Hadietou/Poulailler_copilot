package com.hadietou.poulailler.util

import java.util.*
import kotlin.math.*

object SunUtils {
    private const val LATITUDE = 18.07
    private const val LONGITUDE = -15.95
    private const val ZENITH = 90.83 

    fun getExtinctionTime(): String {
        try {
            val calendar = Calendar.getInstance()
            val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR).toDouble()
            
            // 1. Calculate sunrise
            val lngHour = LONGITUDE / 15.0
            val tRise = dayOfYear + ((6.0 - lngHour) / 24.0)
            
            val sunriseUT = calculateTime(tRise, lngHour, true) ?: return "--:--"
            
            // Mauritania is GMT/UTC+0
            val sunriseLocal = sunriseUT
            
            // Extinction = Sunrise + 15 hours
            var extinction = sunriseLocal + 15.0
            if (extinction >= 24.0) extinction -= 24.0
            
            val hours = extinction.toInt()
            val minutes = ((extinction - hours) * 60).toInt()
            
            return String.format(Locale.US, "%02d:%02d", hours, minutes)
        } catch (e: Exception) {
            return "--:--"
        }
    }

    private fun calculateTime(t: Double, lngHour: Double, isSunrise: Boolean): Double? {
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
        
        val cosH = (cos(Math.toRadians(ZENITH)) - (sinDec * sin(Math.toRadians(LATITUDE)))) / (cosDec * cos(Math.toRadians(LATITUDE)))
        
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
