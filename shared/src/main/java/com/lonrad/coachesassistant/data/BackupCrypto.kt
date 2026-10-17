package com.lonrad.coachesassistant.data

import com.lonrad.coachesassistant.shared.R
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** A backup's encrypted contents, with what's needed to open it again. */
internal class SealedBackup(
    val iterations: Int,
    val salt: ByteArray,
    val iv: ByteArray,
    val ciphertext: ByteArray,
)

/**
 * Password protection for a backup file: PBKDF2-HMAC-SHA256 turns the password into a 256-bit key,
 * and AES-256-GCM encrypts the backup with it. The GCM tag is also what tells a wrong password from
 * a damaged file, since both fail to authenticate. [associatedData] binds the file's header (its
 * format version and iteration count) to the ciphertext, so changing either is detected.
 */
internal object BackupCrypto {
    /** PBKDF2 iterations for new backups, in line with current OWASP guidance for PBKDF2-HMAC-SHA256. */
    const val ITERATIONS = 600_000

    /** Bounds on the iteration count read from a file, so a crafted file can't stall the app for hours. */
    const val MIN_ITERATIONS = 100_000
    const val MAX_ITERATIONS = 2_000_000

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TAG_BITS = 128
    private const val KDF = "PBKDF2WithHmacSHA256"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private val random = SecureRandom()

    fun seal(plaintext: ByteArray, password: String, associatedData: ByteArray): SealedBackup {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(associatedData)
        }
        return SealedBackup(ITERATIONS, salt, iv, cipher.doFinal(plaintext))
    }

    /** The plaintext, or a [BackupFormatException] if the password is wrong or the file was changed. */
    fun open(sealed: SealedBackup, password: String, associatedData: ByteArray): ByteArray {
        if (sealed.iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw BackupFormatException(R.string.backup_damaged)
        }
        return try {
            Cipher.getInstance(TRANSFORMATION).run {
                init(Cipher.DECRYPT_MODE, key(password, sealed.salt, sealed.iterations), GCMParameterSpec(TAG_BITS, sealed.iv))
                updateAAD(associatedData)
                doFinal(sealed.ciphertext)
            }
        } catch (_: GeneralSecurityException) {
            throw BackupFormatException(R.string.backup_password_wrong)
        }
    }

    private fun key(password: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance(KDF).generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }
}
