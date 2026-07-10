package com.webscare.urducanvas.common.canvas.io

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Supplies the app-held AES-256 key used by [ProjectCodec].
 *
 * SECURITY NOTE (read before shipping):
 * Because you chose "anyone with our app can open any file", the key must live inside the
 * app. That means a determined reverse-engineer who decompiles the APK can eventually
 * recover it. This class only RAISES THE COST: the literal key is never present in the
 * binary — instead three unrelated-looking byte arrays are XORed together at runtime and
 * the result is stretched through HKDF. Combined with R8 full-mode obfuscation this stops
 * casual extraction (strings/grep/jadx skim). It is NOT unbreakable. Do not market it as
 * end-to-end encryption.
 *
 * HARDENING ROADMAP (do these for release, in order of value):
 *   1. Move these three arrays into native C++ via the NDK (a .so is far harder to read
 *      than a JVM constant). Expose a single external fun that returns the assembled secret.
 *   2. Enable R8 full mode + obfuscation, and strip this class's names.
 *   3. Optionally add a Play Integrity / signature check and refuse to assemble the key if
 *      the APK signature doesn't match yours (stops repackaged clones).
 *
 * To rotate the key later, bump ProjectCodec.VERSION and keep the old key available for
 * decrypting old files (multi-key read). For now there is a single version.
 */
internal object KeyProvider {

    // Three meaningless-looking shards. XOR of all three = the real 32-byte secret.
    // Generate your own with a one-off script; DO NOT reuse these placeholder values.
    private val A = byteArrayOf(
        0x5A, 0x13, 0x7C.toByte(), 0x02, 0x66, 0x39, 0x11, 0x4D,
        0x28, 0x7B, 0x0E, 0x55, 0x3C, 0x61, 0x49, 0x10,
        0x22, 0x6F, 0x18, 0x44, 0x73, 0x05, 0x2E, 0x5B,
        0x37, 0x12, 0x68, 0x09, 0x4A, 0x71, 0x1D, 0x53,
    )
    private val B = byteArrayOf(
        0x11, 0x44, 0x2A, 0x6D, 0x09, 0x57, 0x7E, 0x03,
        0x65, 0x1C, 0x38, 0x72, 0x0B, 0x4F, 0x26, 0x60,
        0x59, 0x13, 0x47, 0x2B, 0x6E, 0x35, 0x08, 0x52,
        0x1A, 0x63, 0x3D, 0x76, 0x04, 0x4C, 0x29, 0x6A,
    )
    private val C = byteArrayOf(
        0x73, 0x25, 0x1E, 0x40, 0x5C, 0x6B, 0x32, 0x77,
        0x0D, 0x58, 0x14, 0x46, 0x2F, 0x69, 0x37, 0x51,
        0x1B, 0x64, 0x30, 0x75, 0x06, 0x4E, 0x21, 0x68,
        0x3A, 0x07, 0x56, 0x1F, 0x42, 0x5D, 0x6C, 0x33,
    )

    /** Salt + info for HKDF. Changing these changes the derived key (and breaks old files). */
    private val HKDF_SALT = "urdc.v1.salt".toByteArray()
    private val HKDF_INFO = "urdc.v1.aeskey".toByteArray()

    fun appKey(): ByteArray {
        val ikm = ByteArray(32) { i -> (A[i].toInt() xor B[i].toInt() xor C[i].toInt()).toByte() }
        return hkdfSha256(ikm, HKDF_SALT, HKDF_INFO, 32)
    }

    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        // extract
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        // expand
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < length) {
            mac.reset()
            mac.update(t)
            mac.update(info)
            mac.update(counter.toByte())
            t = mac.doFinal()
            val n = minOf(t.size, length - pos)
            System.arraycopy(t, 0, out, pos, n)
            pos += n
            counter++
        }
        return out
    }
}
