package com.example.util

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import java.security.spec.InvalidKeySpecException
import java.util.Arrays
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Local PIN hashing and constant-time verification using PBKDF2-HMAC-SHA256.
 *
 * Stored format (v2):
 * `v2$pbkdf2-hmac-sha256$<iterations>$<saltHex>$<derivedKeyHex>`
 *
 * Work factor calibration (measured via Wave2SecurityTest on host JVM, temurin-21 x86_64):
 * - 120,000 iterations: ~193.69 ms
 * - 210,000 iterations: ~272.80 ms
 * - 310,000 iterations: ~403.00 ms  <-- selected DEFAULT_ITERATIONS
 * - 600,000 iterations: ~785.93 ms
 *
 * Rationale for DEFAULT_ITERATIONS = 310,000 (256-bit key, 16-byte SecureRandom salt):
 * - Exceeds baseline 120,000 by >2.5x while staying well under 500 ms during off-main-thread
 *   verification (avoiding >750 ms UX stalls from 600,000 on mobile CPUs or API 24-25 fallback).
 * - Combined with persistent monotonic PIN throttling (15s -> 30s -> 60s -> 5m -> 15m cap after
 *   3 free attempts), raises offline brute-force cost across 10,000 4-digit PINs to 3.1e9 HMAC rounds.
 * - Legacy Wave-1 credentials (64-hex-char salted SHA-256 + separate pinSalt) remain verifiable
 *   strictly for one-time transparent re-hashing to v2 upon valid unlock.
 */
object PinSecurity {
  const val VERSION_V2 = "v2"
  const val ALGORITHM_ID_PBKDF2_SHA256 = "pbkdf2-hmac-sha256"
  const val SALT_BYTES = 16
  const val DERIVED_KEY_BYTES = 32
  const val MIN_ITERATIONS = 10_000
  const val MAX_ITERATIONS = 2_000_000

  /**
   * Calibrated PBKDF2-HMAC-SHA256 iteration count (310,000).
   */
  const val DEFAULT_ITERATIONS = 310_000

  private const val JCA_PBKDF2_SHA256 = "PBKDF2WithHmacSHA256"
  private const val JCA_HMAC_SHA256 = "HmacSHA256"

  private val random = SecureRandom()

  sealed interface VerificationOutcome {
    data class Valid(val needsMigrationToV2: Boolean) : VerificationOutcome
    data object Invalid : VerificationOutcome
    data object Malformed : VerificationOutcome
  }

  fun generateSaltBytes(size: Int = SALT_BYTES): ByteArray {
    require(size >= SALT_BYTES) { "Salt must be at least $SALT_BYTES bytes." }
    return ByteArray(size).also { random.nextBytes(it) }
  }

  fun generateSalt(): String = toHex(generateSaltBytes(SALT_BYTES))

  fun hashPin(pin: String, iterations: Int = DEFAULT_ITERATIONS): String {
    require(pin.isNotEmpty()) { "PIN must not be empty." }
    val salt = generateSaltBytes(SALT_BYTES)
    return try {
      hashPinWithSaltBytes(pin = pin, saltBytes = salt, iterations = iterations)
    } finally {
      Arrays.fill(salt, 0.toByte())
    }
  }

  fun hashPinWithSaltBytes(
    pin: String,
    saltBytes: ByteArray,
    iterations: Int = DEFAULT_ITERATIONS
  ): String {
    require(pin.isNotEmpty()) { "PIN must not be empty." }
    require(saltBytes.size >= SALT_BYTES) { "Salt must be at least $SALT_BYTES bytes." }
    require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) {
      "Iterations must be within $MIN_ITERATIONS..$MAX_ITERATIONS."
    }
    val derived = derivePbkdf2HmacSha256(pin, saltBytes, iterations, DERIVED_KEY_BYTES)
    return try {
      "$VERSION_V2\$$ALGORITHM_ID_PBKDF2_SHA256\$$iterations\$${toHex(saltBytes)}\$${toHex(derived)}"
    } finally {
      Arrays.fill(derived, 0.toByte())
    }
  }

  fun verifyPin(pin: String, encodedHash: String): Boolean {
    return verifyPinDetailed(pin = pin, encodedHash = encodedHash, legacySalt = "") is VerificationOutcome.Valid
  }

  fun verifyPinDetailed(
    pin: String,
    encodedHash: String,
    legacySalt: String = ""
  ): VerificationOutcome {
    if (encodedHash.isBlank()) return VerificationOutcome.Malformed

    // Versioned v2 format: v2$pbkdf2-hmac-sha256$iterations$saltHex$hashHex
    if (encodedHash.contains('$')) {
      val parts = encodedHash.split('$')
      if (parts.size != 5) return VerificationOutcome.Malformed
      if (parts[0] != VERSION_V2 || parts[1] != ALGORITHM_ID_PBKDF2_SHA256) {
        return VerificationOutcome.Malformed
      }
      val iterations = parts[2].toIntOrNull() ?: return VerificationOutcome.Malformed
      if (iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
        return VerificationOutcome.Malformed
      }
      val saltBytes = decodeHexStrict(parts[3]) ?: return VerificationOutcome.Malformed
      if (saltBytes.size < SALT_BYTES) {
        Arrays.fill(saltBytes, 0.toByte())
        return VerificationOutcome.Malformed
      }
      val expectedKey = decodeHexStrict(parts[4])
      if (expectedKey == null || expectedKey.size != DERIVED_KEY_BYTES) {
        Arrays.fill(saltBytes, 0.toByte())
        if (expectedKey != null) Arrays.fill(expectedKey, 0.toByte())
        return VerificationOutcome.Malformed
      }
      if (pin.isEmpty()) {
        Arrays.fill(saltBytes, 0.toByte())
        Arrays.fill(expectedKey, 0.toByte())
        return VerificationOutcome.Invalid
      }

      val candidateKey = derivePbkdf2HmacSha256(pin, saltBytes, iterations, DERIVED_KEY_BYTES)
      return try {
        if (MessageDigest.isEqual(candidateKey, expectedKey)) {
          VerificationOutcome.Valid(needsMigrationToV2 = false)
        } else {
          VerificationOutcome.Invalid
        }
      } finally {
        Arrays.fill(saltBytes, 0.toByte())
        Arrays.fill(expectedKey, 0.toByte())
        Arrays.fill(candidateKey, 0.toByte())
      }
    }

    // Legacy v1 salted SHA-256 format (64 hex chars + non-empty salt)
    if (legacySalt.isBlank()) return VerificationOutcome.Malformed
    val expectedLegacyBytes = decodeHexStrict(encodedHash) ?: return VerificationOutcome.Malformed
    if (expectedLegacyBytes.size != DERIVED_KEY_BYTES) {
      Arrays.fill(expectedLegacyBytes, 0.toByte())
      return VerificationOutcome.Malformed
    }
    if (pin.isEmpty()) {
      Arrays.fill(expectedLegacyBytes, 0.toByte())
      return VerificationOutcome.Invalid
    }

    val candidateBytes = legacySha256DigestBytes(pin, legacySalt)
    return try {
      if (MessageDigest.isEqual(candidateBytes, expectedLegacyBytes)) {
        VerificationOutcome.Valid(needsMigrationToV2 = true)
      } else {
        VerificationOutcome.Invalid
      }
    } finally {
      Arrays.fill(expectedLegacyBytes, 0.toByte())
      Arrays.fill(candidateBytes, 0.toByte())
    }
  }

  fun isValidPinFormat(pin: String): Boolean {
    return pin.length == 4 && pin.all { it in '0'..'9' }
  }

  internal fun legacySha256Hash(pin: String, salt: String): String {
    val bytes = legacySha256DigestBytes(pin, salt)
    return try {
      toHex(bytes)
    } finally {
      Arrays.fill(bytes, 0.toByte())
    }
  }

  private fun legacySha256DigestBytes(pin: String, salt: String): ByteArray {
    val digest = MessageDigest.getInstance("SHA-256")
    val input = "$salt:$pin".toByteArray(Charsets.UTF_8)
    return try {
      digest.digest(input)
    } finally {
      Arrays.fill(input, 0.toByte())
    }
  }

  internal fun derivePbkdf2HmacSha256(
    pin: String,
    salt: ByteArray,
    iterations: Int,
    keyLengthBytes: Int = DERIVED_KEY_BYTES
  ): ByteArray {
    val chars = pin.toCharArray()
    val spec = PBEKeySpec(chars, salt, iterations, keyLengthBytes * 8)
    return try {
      try {
        val factory = SecretKeyFactory.getInstance(JCA_PBKDF2_SHA256)
        factory.generateSecret(spec).encoded
      } catch (_: NoSuchAlgorithmException) {
        // Android API 24-25 fallback: SecretKeyFactory("PBKDF2WithHmacSHA256") requires API 26+.
        derivePbkdf2ViaMac(chars, salt, iterations, keyLengthBytes)
      } catch (_: InvalidKeySpecException) {
        derivePbkdf2ViaMac(chars, salt, iterations, keyLengthBytes)
      }
    } finally {
      spec.clearPassword()
      Arrays.fill(chars, '\u0000')
    }
  }

  /**
   * Pure RFC 2898 PBKDF2-HMAC-SHA256 using javax.crypto.Mac (supported since Android API 1),
   * producing byte-for-byte identical output to PBKDF2WithHmacSHA256.
   */
  internal fun derivePbkdf2ViaMac(
    chars: CharArray,
    salt: ByteArray,
    iterations: Int,
    keyLengthBytes: Int = DERIVED_KEY_BYTES
  ): ByteArray {
    val byteBuffer: ByteBuffer = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
    val passwordBytes = ByteArray(byteBuffer.remaining())
    byteBuffer.get(passwordBytes)
    if (byteBuffer.hasArray()) {
      Arrays.fill(byteBuffer.array(), 0.toByte())
    }

    val mac = Mac.getInstance(JCA_HMAC_SHA256)
    mac.init(SecretKeySpec(passwordBytes, JCA_HMAC_SHA256))
    Arrays.fill(passwordBytes, 0.toByte())

    val hLen = mac.macLength
    val blockCount = (keyLengthBytes + hLen - 1) / hLen
    val output = ByteArray(keyLengthBytes)
    val u = ByteArray(hLen)
    val t = ByteArray(hLen)
    val saltBlock = ByteArray(salt.size + 4)
    System.arraycopy(salt, 0, saltBlock, 0, salt.size)

    try {
      var offset = 0
      for (blockIndex in 1..blockCount) {
        saltBlock[salt.size] = (blockIndex ushr 24).toByte()
        saltBlock[salt.size + 1] = (blockIndex ushr 16).toByte()
        saltBlock[salt.size + 2] = (blockIndex ushr 8).toByte()
        saltBlock[salt.size + 3] = blockIndex.toByte()

        mac.reset()
        var current = mac.doFinal(saltBlock)
        System.arraycopy(current, 0, u, 0, hLen)
        System.arraycopy(current, 0, t, 0, hLen)
        Arrays.fill(current, 0.toByte())

        for (iter in 2..iterations) {
          mac.reset()
          current = mac.doFinal(u)
          System.arraycopy(current, 0, u, 0, hLen)
          Arrays.fill(current, 0.toByte())
          for (j in 0 until hLen) {
            t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
          }
        }

        val toCopy = minOf(hLen, keyLengthBytes - offset)
        System.arraycopy(t, 0, output, offset, toCopy)
        offset += toCopy
      }
      return output
    } finally {
      Arrays.fill(u, 0.toByte())
      Arrays.fill(t, 0.toByte())
      Arrays.fill(saltBlock, 0.toByte())
    }
  }

  private fun decodeHexStrict(hex: String): ByteArray? {
    if (hex.isEmpty() || hex.length % 2 != 0) return null
    val result = ByteArray(hex.length / 2)
    for (i in result.indices) {
      val hi = hexDigit(hex[i * 2])
      val lo = hexDigit(hex[i * 2 + 1])
      if (hi < 0 || lo < 0) {
        Arrays.fill(result, 0.toByte())
        return null
      }
      result[i] = ((hi shl 4) or lo).toByte()
    }
    return result
  }

  private fun hexDigit(c: Char): Int = when (c) {
    in '0'..'9' -> c - '0'
    in 'a'..'f' -> c - 'a' + 10
    in 'A'..'F' -> c - 'A' + 10
    else -> -1
  }

  private fun toHex(bytes: ByteArray): String {
    val hexChars = "0123456789abcdef"
    val out = CharArray(bytes.size * 2)
    for (i in bytes.indices) {
      val v = bytes[i].toInt() and 0xFF
      out[i * 2] = hexChars[v ushr 4]
      out[i * 2 + 1] = hexChars[v and 0x0F]
    }
    return String(out)
  }
}
