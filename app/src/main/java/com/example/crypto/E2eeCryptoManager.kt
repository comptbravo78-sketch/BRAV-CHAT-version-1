package com.example.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Real End-to-End Encryption (E2EE) manager using ECDH (secp256r1 / P-256) + HKDF-SHA256 + AES-256-GCM.
 * Private keys are wrapped using an Android Keystore AES-256-GCM master key (with safe fallback for unit tests).
 */
class E2eeCryptoManager(private val context: Context) {

    data class EncryptedPayload(
        val ciphertextBase64: String,
        val ivBase64: String,
        val senderPublicKeyBase64: String
    )

    private val prefs by lazy {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val random = SecureRandom()

    @Synchronized
    fun getOrCreateIdentityKeyPair(uid: String): KeyPair {
        val storedPub = prefs.getString("${KEY_PUB_PREFIX}$uid", null)
        val storedPrivWrapped = prefs.getString("${KEY_PRIV_PREFIX}$uid", null)
        val storedPrivIv = prefs.getString("${KEY_PRIV_IV_PREFIX}$uid", null)

        if (!storedPub.isNullOrBlank() && !storedPrivWrapped.isNullOrBlank()) {
            try {
                val pubBytes = Base64.decode(storedPub, Base64.NO_WRAP)
                val privBytes = unwrapPrivateKeyBytes(storedPrivWrapped, storedPrivIv)
                val kf = KeyFactory.getInstance("EC")
                val pub = kf.generatePublic(X509EncodedKeySpec(pubBytes))
                val priv = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
                return KeyPair(pub, priv)
            } catch (e: Exception) {
                Log.w(TAG, "Regenerating identity keypair due to unwrap error", e)
            }
        }

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), random)
        val pair = kpg.generateKeyPair()
        val pubB64 = Base64.encodeToString(pair.public.encoded, Base64.NO_WRAP)
        val (wrappedPriv, privIv) = wrapPrivateKeyBytes(pair.private.encoded)

        prefs.edit()
            .putString("${KEY_PUB_PREFIX}$uid", pubB64)
            .putString("${KEY_PRIV_PREFIX}$uid", wrappedPriv)
            .putString("${KEY_PRIV_IV_PREFIX}$uid", privIv)
            .apply()

        return pair
    }

    fun getPublicKeyBase64(uid: String): String {
        return Base64.encodeToString(getOrCreateIdentityKeyPair(uid).public.encoded, Base64.NO_WRAP)
    }

    fun encryptForPeer(
        myUid: String,
        peerPublicKeyBase64: String,
        contextId: String,
        plaintext: String
    ): EncryptedPayload {
        val myPair = getOrCreateIdentityKeyPair(myUid)
        val aesKey = deriveSharedAesKey(myPair.private, peerPublicKeyBase64, contextId)
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedPayload(
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            senderPublicKeyBase64 = Base64.encodeToString(myPair.public.encoded, Base64.NO_WRAP)
        )
    }

    fun decryptFromPeer(
        myUid: String,
        peerPublicKeyBase64: String,
        contextId: String,
        ciphertextBase64: String,
        ivBase64: String
    ): String? {
        return try {
            val myPair = getOrCreateIdentityKeyPair(myUid)
            val aesKey = deriveSharedAesKey(myPair.private, peerPublicKeyBase64, contextId)
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "E2EE decrypt failed for context=$contextId: ${e.message}")
            null
        }
    }

    fun generateGroupKeyBase64(): String {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun encryptWithSymmetricKey(keyBase64: String, plaintext: String): Pair<String, String> {
        val keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP)
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(ciphertext, Base64.NO_WRAP) to Base64.encodeToString(iv, Base64.NO_WRAP)
    }

    fun decryptWithSymmetricKey(keyBase64: String, ciphertextBase64: String, ivBase64: String): String? {
        return try {
            val keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
            val ciphertext = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    fun computeSafetyFingerprint(pubKeyABase64: String, pubKeyBBase64: String): String {
        if (pubKeyABase64.isBlank() || pubKeyBBase64.isBlank()) return "En attente d'échange de clés"
        val ordered = listOf(pubKeyABase64, pubKeyBBase64).sorted()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${ordered[0]}|${ordered[1]}".toByteArray(Charsets.UTF_8))
        val digits = digest.take(15).joinToString("") { b ->
            ((b.toInt() and 0xFF) % 100).toString().padStart(2, '0')
        }
        return digits.chunked(5).joinToString("  ")
    }

    private fun deriveSharedAesKey(privateKey: PrivateKey, peerPublicKeyBase64: String, info: String): SecretKey {
        val peerPubBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)
        val peerPub: PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(peerPubBytes))
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(peerPub, true)
        val sharedSecret = agreement.generateSecret()

        // HKDF-Extract + Expand (SHA-256)
        val salt = ByteArray(32)
        val prkMac = Mac.getInstance("HmacSHA256")
        prkMac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = prkMac.doFinal(sharedSecret)

        val expandMac = Mac.getInstance("HmacSHA256")
        expandMac.init(SecretKeySpec(prk, "HmacSHA256"))
        expandMac.update("BRAV-E2EE-v1:$info".toByteArray(Charsets.UTF_8))
        expandMac.update(0x01.toByte())
        val okm = expandMac.doFinal()
        return SecretKeySpec(okm.copyOf(32), "AES")
    }

    private fun wrapPrivateKeyBytes(rawBytes: ByteArray): Pair<String, String> {
        return try {
            val masterKey = getOrCreateKeystoreMasterKey()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, masterKey)
            val encrypted = cipher.doFinal(rawBytes)
            val iv = cipher.iv
            Base64.encodeToString(encrypted, Base64.NO_WRAP) to Base64.encodeToString(iv, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.w(TAG, "AndroidKeyStore unavailable, storing encoded fallback", e)
            Base64.encodeToString(rawBytes, Base64.NO_WRAP) to ""
        }
    }

    private fun unwrapPrivateKeyBytes(wrappedBase64: String, ivBase64: String?): ByteArray {
        val data = Base64.decode(wrappedBase64, Base64.NO_WRAP)
        if (ivBase64.isNullOrBlank()) return data
        val masterKey = getOrCreateKeystoreMasterKey()
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(data)
    }

    private fun getOrCreateKeystoreMasterKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(MASTER_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                MASTER_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    companion object {
        private const val TAG = "E2eeCrypto"
        private const val PREFS_NAME = "brav_e2ee_keystore_prefs"
        private const val MASTER_KEY_ALIAS = "brav_e2ee_master_aes_key"
        private const val KEY_PUB_PREFIX = "pub_"
        private const val KEY_PRIV_PREFIX = "priv_"
        private const val KEY_PRIV_IV_PREFIX = "priv_iv_"
    }
}
