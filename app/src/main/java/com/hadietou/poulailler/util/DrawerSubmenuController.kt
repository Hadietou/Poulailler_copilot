package com.hadietou.poulailler.util

import android.view.Menu
import androidx.annotation.IdRes
import com.hadietou.poulailler.R

/**
 * État + logique du tiroir de navigation (NavigationView) partagés par toutes les Activities
 * qui l'affichent : gère l'expansion des 3 sous-menus repliables (Œufs, Santé, Compte), dont
 * un seul peut être ouvert à la fois. Centralise ce qui était dupliqué à l'identique dans
 * chacune d'elles.
 */
class DrawerSubmenuController(initiallyExpanded: Submenu? = null) {

    enum class Submenu(@IdRes val parentId: Int, @IdRes val groupId: Int) {
        EGG(R.id.nav_egg_management, R.id.group_egg_submenu),
        HEALTH(R.id.nav_health_management, R.id.group_health_submenu),
        ACCOUNT(R.id.nav_account_section, R.id.group_account_submenu)
    }

    companion object {
        /** Ids des items de sous-menu (toutes sections confondues), pour NavMenuStyler.style(). */
        val CHILD_ITEM_IDS = listOf(
            R.id.nav_collect, R.id.nav_sales, R.id.nav_vaccines, R.id.nav_mortality,
            R.id.nav_users, R.id.nav_delete_account, R.id.nav_logout
        )
        private val ALL = Submenu.values()
    }

    var expanded: Submenu? = initiallyExpanded
        private set

    /** @return true si l'item cliqué est le parent d'un sous-menu (et a donc été traité ici). */
    fun handleParentClick(@IdRes itemId: Int): Boolean {
        val submenu = ALL.find { it.parentId == itemId } ?: return false
        expanded = if (expanded == submenu) null else submenu
        return true
    }

    /**
     * Applique la visibilité des 3 groupes de sous-menu selon l'état courant.
     * @param eggVisible permet de masquer entièrement la section Œufs (ex: lot de type "chair").
     */
    fun applyGroupVisibility(menu: Menu, eggVisible: Boolean = true) {
        menu.setGroupVisible(Submenu.EGG.groupId, eggVisible && expanded == Submenu.EGG)
        menu.setGroupVisible(Submenu.HEALTH.groupId, expanded == Submenu.HEALTH)
        menu.setGroupVisible(Submenu.ACCOUNT.groupId, expanded == Submenu.ACCOUNT)
    }

    /** Liste (id parent, état déplié) à passer à NavMenuStyler.style(). */
    fun parentsForStyler(): List<Pair<Int, Boolean>> = ALL.map { it.parentId to (expanded == it) }
}
