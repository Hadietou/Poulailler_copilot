package com.hadietou.poulailler.util

import android.util.Base64
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hachage de mots de passe/codes temporaires avant stockage (Room local ou Firestore).
 *
 * Utilisé notamment pour le mot de passe temporaire généré lors de la création d'un
 * agent (voir ResponsableViewModel.createAgentSimplified / LoginViewModel.checkPreCreatedAgent) :
 * on ne veut jamais qu'un mot de passe, même de courte durée de vie, soit lisible en clair
 * dans la base.
 *
 * Format stocké : "iterations:saltBase64:hashBase64" (PBKDF2WithHmacSHA256, sel aléatoire
 * par mot de passe). Ce format auto-descriptif permet de reconnaître une valeur déjà hachée
 * (voir [looksHashed]), utile pour migrer en douceur d'anciennes valeurs en clair.
 */
object PasswordHasher {

    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256
    private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val SALT_LENGTH_BYTES = 16

    fun hash(password: String): String {
        val salt = ByteArray(SALT_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val hashBytes = pbkdf2(password, salt, ITERATIONS)
        return "$ITERATIONS:${encode(salt)}:${encode(hashBytes)}"
    }

    /** Compare [password] à une valeur précédemment produite par [hash]. */
    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 3) return false
        val iterations = parts[0].toIntOrNull() ?: return false
        val salt = decode(parts[1]) ?: return false
        val expectedHash = decode(parts[2]) ?: return false
        val actualHash = pbkdf2(password, salt, iterations)
        return constantTimeEquals(actualHash, expectedHash)
    }

    /** Permet de distinguer une valeur déjà hachée d'un vieux mot de passe stocké en clair. */
    fun looksHashed(stored: String): Boolean {
        val parts = stored.split(":")
        return parts.size == 3 && parts[0].toIntOrNull() != null &&
            decode(parts[1]) != null && decode(parts[2]) != null
    }

    private fun pbkdf2(password: String, salt: ByteArray, iterations: Int): ByteArray {
        val spec: KeySpec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITHM)
        return factory.generateSecret(spec).encoded
    }

    private fun encode(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decode(value: String): ByteArray? = try {
        Base64.decode(value, Base64.NO_WRAP)
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var result = 0
        for (i in a.indices) result = result or (a[i].toInt() xor b[i].toInt())
        return result == 0
    }
}
