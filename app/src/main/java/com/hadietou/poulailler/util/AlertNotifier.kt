package com.hadietou.poulailler.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.hadietou.poulailler.ui.DashboardActivity

/**
 * Notifications affichées directement sur le téléphone (alerte chaleur, stock d'aliment, alertes
 * sanitaires…), sans dépendre de l'email : avec la connexion par numéro de téléphone, le responsable
 * n'a pas toujours d'adresse email enregistrée, et l'envoi d'emails depuis un téléphone peut échouer.
 */
object AlertNotifier {

    private enum class Channel(val id: String, val label: String, val description: String) {
        HEAT("heat_alerts", "Alertes chaleur", "Prévenu quand une forte chaleur est annoncée pour la ferme."),
        STOCK("stock_alerts", "Stock d'aliment", "Prévenu quand le stock d'aliment devient trop bas."),
        HEALTH("health_alerts", "Alertes sanitaires", "Prévenu des situations sanitaires critiques (mortalité, retards, délais d'attente…).")
    }

    private fun ensureChannel(context: Context, channel: Channel) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(channel.id) != null) return
        manager.createNotificationChannel(
            NotificationChannel(channel.id, channel.label, NotificationManager.IMPORTANCE_HIGH).apply {
                description = channel.description
            }
        )
    }

    private fun canNotify(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun notify(context: Context, channel: Channel, title: String, text: String, notificationId: Int): Boolean {
        if (!canNotify(context)) return false
        ensureChannel(context, channel)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, channel.id)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (e: SecurityException) {
            false
        }
    }

    /** Alerte chaleur ; renvoie faux si les notifications sont refusées sur ce téléphone. */
    fun notifyHeat(context: Context, title: String, text: String, notificationId: Int): Boolean =
        notify(context, Channel.HEAT, title, text, notificationId)

    /** Stock d'aliment bas. */
    fun notifyStock(context: Context, title: String, text: String, notificationId: Int): Boolean =
        notify(context, Channel.STOCK, title, text, notificationId)

    /** Alertes sanitaires critiques ; plusieurs lignes possibles dans [text] (une par ligne). */
    fun notifyHealth(context: Context, title: String, text: String, notificationId: Int): Boolean =
        notify(context, Channel.HEALTH, title, text, notificationId)

    /** Android 13+ : demande l'autorisation d'afficher des notifications. À appeler dans onCreate. */
    fun requestPermissionIfNeeded(activity: ComponentActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
            .launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
