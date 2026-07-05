package com.courtside.pickleball.sync

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 helper used to authenticate tablet<->phone command messages.
 *
 * The secret is minted per WebSocket connection (see [TabletDisplaySync]) and handed to the
 * peer once, directly over that connection - it is never broadcast, so a device that only
 * observes the periodic UDP state broadcast cannot forge a valid signature.
 */
internal object MessageAuthenticator {
    private const val ALGORITHM = "HmacSHA256"
    private val secureRandom = SecureRandom()

    fun newSecret(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64UrlNoPadding.encode(bytes)
    }

    fun sign(secret: String, message: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), ALGORITHM))
        return Base64UrlNoPadding.encode(mac.doFinal(message.toByteArray(StandardCharsets.UTF_8)))
    }

    /** Constant-time comparison to avoid leaking timing information about the expected MAC. */
    fun matches(expected: String, actual: String): Boolean =
        MessageDigest.isEqual(
            expected.toByteArray(StandardCharsets.UTF_8),
            actual.toByteArray(StandardCharsets.UTF_8)
        )
}

private object Base64UrlNoPadding {
    fun encode(bytes: ByteArray): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}
