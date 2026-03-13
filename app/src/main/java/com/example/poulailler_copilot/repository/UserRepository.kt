package com.example.poulailler_copilot.repository

import com.example.poulailler_copilot.data.User
import com.example.poulailler_copilot.data.UserDao

class UserRepository(private val userDao: UserDao) {

    suspend fun login(username: String, password: String): User? {
        val user = userDao.getByUsername(username)
        return if (user != null && user.password == password && user.active) user else null
    }

    suspend fun createAgent(username: String, password: String): Long {
        val user = User(username = username, password = password, role = "AGENT", active = true)
        return userDao.insert(user)
    }

    suspend fun createResponsableIfNotExists() {
        val existing = userDao.getByUsername("admin")
        if (existing == null) {
            userDao.insert(
                User(
                    username = "admin",
                    password = "admin",
                    role = "RESPONSABLE",
                    active = true
                )
            )
        }
    }

    suspend fun getAgents(): List<User> = userDao.getAgents()

    suspend fun setAgentActive(id: Long, active: Boolean) = userDao.setActive(id, active)

    suspend fun resetPassword(id: Long, newPassword: String) =
        userDao.resetPassword(id, newPassword)
}
