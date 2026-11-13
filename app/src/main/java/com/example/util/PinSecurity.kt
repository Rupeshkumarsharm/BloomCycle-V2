package com.example.util

import java.security.MessageDigest
import java.security.SecureRandom

object PinSecurity {
  private val random = SecureRandom()

  fun generateSalt(): String {
    val bytes = ByteArray(16)
    random.nextBytes(bytes)
    return bytes.joinToString("") { "%02x".format(it) }
  }

  fun hashPin(pin: String, salt: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val input = "$salt:$pin".toByteArray(Charsets.UTF_8)
    return digest.digest(input).joinToString("") { "%02x".format(it) }
  }

  fun verifyPin(pin: String, salt: String, expectedHash: String): Boolean {
    if (salt.isBlank() || expectedHash.isBlank()) return false
    val candidate = hashPin(pin, salt).toByteArray(Charsets.UTF_8)
    val expected = expectedHash.toByteArray(Charsets.UTF_8)
    return MessageDigest.isEqual(candidate, expected)
  }
}
