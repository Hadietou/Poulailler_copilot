package com.hadietou.poulailler.util

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.hadietou.poulailler.R

/** Une entrée de menu contextuel : icône, libellé, action déclenchée au clic. */
data class ActionMenuItem(
    @DrawableRes val iconRes: Int,
    val label: String,
    val action: () -> Unit
)

/**
 * Menu contextuel avec icônes et coins arrondis, ancré sous une vue (carte du dashboard,
 * bouton...), pour remplacer le rendu par défaut plat/sans-icône de android.widget.PopupMenu
 * et rester visuellement cohérent avec le reste de l'app (cartes arrondies, icônes colorées).
 */
object ActionMenuPopup {

    fun show(anchor: View, items: List<ActionMenuItem>) {
        val context = anchor.context
        val inflater = LayoutInflater.from(context)
        val content = inflater.inflate(R.layout.popup_action_menu, null)
        val container = content.findViewById<ViewGroup>(R.id.containerActionItems)

        val popup = PopupWindow(
            content,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = 8f
        }

        val iconTint = ContextCompat.getColor(context, R.color.primary)
        items.forEach { menuItem ->
            val row = inflater.inflate(R.layout.item_action_popup, container, false)
            row.findViewById<ImageView>(R.id.ivActionIcon).apply {
                setImageResource(menuItem.iconRes)
                setColorFilter(iconTint)
            }
            row.findViewById<TextView>(R.id.tvActionLabel).text = menuItem.label
            row.setOnClickListener {
                popup.dismiss()
                menuItem.action()
            }
            container.addView(row)
        }

        // Différé à la frame suivante : appelé depuis le OnClickListener de l'ancre, le
        // showAsDropDown() synchrone se ferme parfois instantanément (le ACTION_UP du même
        // tap est encore en cours de dispatch et se comporte comme un "touch outside").
        anchor.post {
            content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val popupHeight = content.measuredHeight

            val visibleFrame = Rect()
            anchor.getWindowVisibleDisplayFrame(visibleFrame)
            val anchorBottom = IntArray(2).also { anchor.getLocationOnScreen(it) }[1] + anchor.height
            val spaceBelow = visibleFrame.bottom - anchorBottom

            val yOffset = if (spaceBelow < popupHeight) -(anchor.height + popupHeight) else 8
            popup.showAsDropDown(anchor, 0, yOffset)
        }
    }
}
