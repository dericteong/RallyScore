package com.courtside.pickleball.wear

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * HMAC-SHA256 signer for watch->tablet fallback commands.
 *
 * Must stay byte-compatible with the app module's `MessageAuthenticator.sign` (same algorithm,
 * same URL-safe unpadded Base64 output) because the tablet verifies these signatures with that
 * implementation. The watch only ever signs, never verifies, so no `matches`/`newSecret` here.
 */
internal object WearMessageAuthenticator {
    private const val ALGORITHM = "HmacSHA256"

    fun sign(secret: String, message: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), ALGORITHM))
        return java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString(mac.doFinal(message.toByteArray(StandardCharsets.UTF_8)))
    }
}
