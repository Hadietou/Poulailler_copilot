package com.hadietou.poulailler.util

import android.content.Context
import android.graphics.Typeface
import androidx.appcompat.app.AlertDialog
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import com.google.android.material.navigation.NavigationView
import com.hadietou.poulailler.R

/**
 * Habille le menu latéral (NavigationView) pour distinguer clairement :
 * - les items simples (destinations directes)
 * - les items "parents" qui déplient un sous-menu (titre en gras + chevron)
 * - les items de sous-menu (indentés, marqueur discret, couleur plus douce)
 *
 * Remplace l'ancien rendu où un parent déplié prenait l'apparence "sélectionné"
 * du thème (pastille colorée), ce qui prêtait à confusion avec une vraie navigation.
 *
 * Chaque mutation de MenuItem (titre, icône) déclenche un rafraîchissement interne
 * du NavigationView : on évite donc de re-styliser des items qui n'ont pas changé
 * (en particulier à chaque bascule d'un sous-menu, où seul le chevron du parent
 * concerné doit changer).
 */
object NavMenuStyler {

    /** Demande confirmation avant de déconnecter l'utilisateur (évite les déconnexions accidentelles). */
    fun confirmLogout(context: Context, onConfirmed: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Déconnexion")
            .setMessage("Voulez-vous vraiment vous déconnecter ?")
            .setPositiveButton("Déconnexion") { _, _ -> onConfirmed() }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private const val CHEVRON_COLLAPSED = "  ›"   // ›
    private const val CHEVRON_EXPANDED = "  ⌄"    // ⌄
    private const val CHILD_MARKER = "  •  "       // léger retrait + puce

    /**
     * Style initial complet du menu : à appeler une fois à la mise en place du drawer.
     *
     * @param defaultIconTintRes couleur d'icône (res id) à conserver pour les items simples,
     *   c'est-à-dire l'équivalent de l'ancien app:itemIconTint XML de la vue.
     * @param parents liste des (id du menu item parent, état déplié)
     * @param children liste des ids des items de sous-menu à styliser
     */
    fun style(
        navigationView: NavigationView,
        context: Context,
        defaultIconTintRes: Int = R.color.text_secondary,
        parents: List<Pair<Int, Boolean>>,
        children: List<Int>
    ) {
        // On désactive le tint global pour pouvoir teinter chaque icône selon son rôle,
        // puis on réapplique la couleur par défaut à tous les items (items simples inclus).
        navigationView.itemIconTintList = null
        val menu = navigationView.menu
        val defaultTint = ContextCompat.getColor(context, defaultIconTintRes)
        for (i in 0 until menu.size()) {
            tintItemTree(menu.getItem(i), defaultTint)
        }

        parents.forEach { (id, expanded) ->
            styleParent(context, menu.findItem(id), expanded)
        }
        children.forEach { id ->
            styleChild(context, menu.findItem(id))
        }
    }

    private fun tintItemTree(item: android.view.MenuItem, color: Int) {
        item.icon?.mutate()?.setTint(color)
        item.subMenu?.let { sub ->
            for (i in 0 until sub.size()) tintItemTree(sub.getItem(i), color)
        }
    }

    private fun styleParent(context: Context, item: android.view.MenuItem?, expanded: Boolean) {
        item ?: return
        // Le style est réappliqué à chaque bascule : on repart toujours du titre "propre",
        // sans le chevron déjà éventuellement ajouté lors d'un appel précédent.
        val baseTitle = item.title?.toString()
            ?.removeSuffix(CHEVRON_EXPANDED)
            ?.removeSuffix(CHEVRON_COLLAPSED)
            ?.trim() ?: return

        val chevron = if (expanded) CHEVRON_EXPANDED else CHEVRON_COLLAPSED
        val builder = SpannableStringBuilder(baseTitle).apply {
            setSpan(StyleSpan(Typeface.BOLD), 0, baseTitle.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        val chevronStart = builder.length
        builder.append(chevron)
        builder.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.primary)),
            chevronStart,
            builder.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        item.title = builder

        item.icon?.mutate()?.setTint(ContextCompat.getColor(context, R.color.primary))
    }

    private fun styleChild(context: Context, item: android.view.MenuItem?) {
        item ?: return
        // Idem : on repart du titre sans le marqueur déjà appliqué lors d'un appel précédent.
        val baseTitle = item.title?.toString()?.removePrefix(CHILD_MARKER)?.trim() ?: return

        val builder = SpannableStringBuilder()
        val markerStart = builder.length
        builder.append(CHILD_MARKER)
        builder.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.emerald_soft)),
            markerStart,
            builder.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        val titleStart = builder.length
        builder.append(baseTitle)
        builder.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.text_secondary)),
            titleStart,
            builder.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        builder.setSpan(
            RelativeSizeSpan(0.94f),
            titleStart,
            builder.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        item.title = builder

        item.icon?.mutate()?.setTint(ContextCompat.getColor(context, R.color.emerald_soft))
    }
}
