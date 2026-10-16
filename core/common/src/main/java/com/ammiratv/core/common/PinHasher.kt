package com.ammiratv.core.common

import java.security.MessageDigest

/**
 * Hashes PINs so the raw digits are never stored. A 4-digit PIN is trivially brute-forceable
 * from its hash: this keeps the PIN out of backups and logs, it is not a security boundary.
 */
object PinHasher {
    private const val SALT = "ammiratv.parental.v1:"

    fun hash(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((SALT + pin).toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun matches(pin: String, storedHash: String): Boolean =
        storedHash.isNotEmpty() && hash(pin) == storedHash
}
