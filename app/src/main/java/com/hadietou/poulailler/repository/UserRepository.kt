package com.hadietou.poulailler.repository

import com.hadietou.poulailler.data.User
import com.hadietou.poulailler.data.UserDao
import com.hadietou.poulailler.util.PasswordHasher

/**
 * Non utilisé actuellement par l'appli (l'authentification réelle passe par Firebase Auth,
 * voir LoginViewModel) — conservé pour compatibilité mais les mots de passe sont tout de
 * même hachés (PasswordHasher) pour ne jamais en stocker en clair, avec migration
 * transparente d'éventuelles anciennes valeurs en clair au moment du login.
 */
class UserRepository(private val userDao: UserDao) {

    suspend fun login(username: String, password: String): User? {
        val user = userDao.getByUsername(username) ?: return null
        if (!user.active) return null

        val matches = if (PasswordHasher.looksHashed(user.password)) {
            PasswordHasher.verify(password, user.password)
        } else {
            user.password == password
        }
        if (!matches) return null

        if (!PasswordHasher.looksHashed(user.password)) {
            // Migration transparente d'un ancien mot de passe en clair vers un haché.
            userDao.resetPassword(user.id, PasswordHasher.hash(password))
        }
        return user
    }

    suspend fun createAgent(username: String, password: String): Long {
        val user = User(username = username, password = PasswordHasher.hash(password), role = "AGENT", active = true)
        return userDao.insert(user)
    }

    suspend fun createResponsableIfNotExists() {
        val existing = userDao.getByUsername("admin")
        if (existing == null) {
            userDao.insert(
                User(
                    username = "admin",
                    password = PasswordHasher.hash("admin"),
                    role = "RESPONSABLE",
                    active = true
                )
            )
        }
    }

    suspend fun getAgents(): List<User> = userDao.getAgents()

    suspend fun setAgentActive(id: Long, active: Boolean) = userDao.setActive(id, active)

    suspend fun resetPassword(id: Long, newPassword: String) =
        userDao.resetPassword(id, PasswordHasher.hash(newPassword))
}
