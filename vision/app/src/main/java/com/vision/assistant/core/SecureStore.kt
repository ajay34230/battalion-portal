package com.vision.assistant.core

import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** AES-256-GCM sealing with a caller-supplied key, so the format is testable on a plain JVM. Token format: `v1.<iv>.<ciphertext+tag>` (URL-safe base64). */
object AesGcm {
    private val enc = Base64.getUrlEncoder().withoutPadding(); private val dec = Base64.getUrlDecoder()
    fun seal(key: SecretKey, plain: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key)
        return "v1." + enc.encodeToString(c.iv) + "." + enc.encodeToString(c.doFinal(plain))
    }
    /** Null when the token is malformed, was tampered with, or the key is wrong. Never throws. */
    fun open(key: SecretKey, token: String): ByteArray? = try {
        val p = token.split('.'); if (p.size != 3 || p[0] != "v1") null else {
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, dec.decode(p[1]))); c.doFinal(dec.decode(p[2]))
        }
    } catch (_: Exception) { null }
}

/**
 * Hardware-backed (where the phone has it) key in the Android Keystore; the key never leaves the Keystore and is not extractable.
 * If the Keystore is unavailable, [seal] returns null and callers must say so rather than pretend the data is protected.
 */
object SecureStore {
    private const val ALIAS = "vision_master_v1"

    private fun key(): SecretKey? = try {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").apply {
            init(android.security.keystore.KeyGenParameterSpec.Builder(ALIAS, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    } catch (_: Throwable) { null }

    fun seal(plain: String): String? = key()?.let { try { AesGcm.seal(it, plain.toByteArray()) } catch (_: Throwable) { null } }
    fun open(token: String): String? = key()?.let { k -> AesGcm.open(k, token)?.toString(Charsets.UTF_8) }
}
