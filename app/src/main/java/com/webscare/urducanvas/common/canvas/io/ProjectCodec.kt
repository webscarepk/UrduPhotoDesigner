package com.webscare.urducanvas.common.canvas.io

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.webscare.urducanvas.BuildConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Reads/writes Urdu Canvas project files (.urdc).
 *
 * The editor ONLY ever deals with the final JSON — exactly the JSON your Gson layer
 * already produces/consumes. This object wraps at the file boundary only:
 *
 *   WRITE: gson.toJson(elements, writer)  ->  wrapped into .urdc (release) / plain .json (debug)
 *   READ : any file                       ->  same JSON the editor expects
 *
 * Backward compatible: on read it sniffs the first 4 bytes. "URDC" -> decrypt; anything
 * else (e.g. '[') -> treat as plain JSON. So every existing .json file on your server and
 * in users' Room DB keeps loading unchanged. No migration needed.
 *
 * Memory-safe: everything streams. We never build a giant String for big base64-image
 * projects — same protection your existing code comment describes.
 *
 * Container layout:
 *   "URDC"(4) | version(1) | thumbLen(4 BE) | thumbJpeg(thumbLen) | iv(12) | AES-GCM(gzip(json))
 */
object ProjectCodec {

    const val FILE_EXTENSION = "urdc"
    const val MIME_TYPE = "application/octet-stream"

    private val MAGIC = byteArrayOf(
        'U'.code.toByte(),
        'R'.code.toByte(),
        'D'.code.toByte(),
        'C'.code.toByte(),
    )
    private const val VERSION: Byte = 0x01
    private const val IV_LEN = 12
    private const val TAG_BITS = 128
    private const val HEADER_MIN = 4 + 1 + 4 // magic + version + thumbLen

    private val KEY: ByteArray by lazy { KeyProvider.appKey() }

    class BadProjectFileException(msg: String, cause: Throwable? = null) : Exception(msg, cause)

    // ──────────────────────────────────────────────────────────────────────
    // WRITE
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Wrap an already-written plain-JSON file into a .urdc file (streaming, no big String).
     *
     * Your exportCanvas() already streamed JSON to [plainJsonFile]. Call this to produce the
     * encrypted file you actually ship/share.
     *
     * @param encrypted defaults to !BuildConfig.DEBUG — debug keeps plain JSON, release encrypts.
     *                  If false, this just copies the JSON through unchanged.
     */
    fun wrapJsonFile(
        plainJsonFile: File,
        target: File,
        thumbnail: Bitmap? = null,
        encrypted: Boolean = !BuildConfig.DEBUG,
    ) {
        if (!encrypted) {
            if (plainJsonFile.absolutePath != target.absolutePath) {
                plainJsonFile.copyTo(target, overwrite = true)
            }
            return
        }

        val thumbBytes: ByteArray = thumbnail?.let { bmp ->
            ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
        } ?: ByteArray(0)

        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(KEY, "AES"), GCMParameterSpec(TAG_BITS, iv))
        }

        target.outputStream().buffered().use { out ->
            out.write(MAGIC)
            out.write(byteArrayOf(VERSION))
            out.write(ByteBuffer.allocate(4).putInt(thumbBytes.size).array())
            out.write(thumbBytes)
            out.write(iv)
            CipherOutputStream(out.nonClosing(), cipher).use { cos ->
                GZIPOutputStream(cos).use { gz ->
                    plainJsonFile.inputStream().buffered().use { it.copyTo(gz) }
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // READ  (auto-detect; streams to a temp file to keep Gson streaming)
    // ──────────────────────────────────────────────────────────────────────

    /**
     * Turn any project file (.urdc OR old plain .json) into a plain-JSON file on disk,
     * which the caller streams into Gson exactly as before.
     *
     * @return a File of plain JSON. For plain-JSON input this is the original file;
     *         for .urdc it is [tempJsonOut], which the caller should delete when done.
     */
    fun toPlainJsonFile(source: File, tempJsonOut: File): File {
        val isUrdc = source.inputStream().use {
            val h = ByteArray(4)
            it.readNBytesCompat(h, 4) == 4 && h.contentEquals(MAGIC)
        }
        if (!isUrdc) return source
        source.inputStream().buffered().use { decryptStream(it, tempJsonOut) }
        return tempJsonOut
    }

    /** From a content:// stream (shared files / file manager). Always writes [tempJsonOut]. */
    fun toPlainJsonFile(source: InputStream, tempJsonOut: File): File {
        val buffered = source.buffered()
        buffered.mark(8)
        val header = ByteArray(4)
        val read = buffered.readNBytesCompat(header, 4)
        buffered.reset()
        if (read == 4 && header.contentEquals(MAGIC)) {
            decryptStream(buffered, tempJsonOut)
        } else {
            tempJsonOut.outputStream().buffered().use { buffered.copyTo(it) }
        }
        return tempJsonOut
    }

    private fun decryptStream(ins: InputStream, tempJsonOut: File) {
        val magic = ByteArray(4)
        ins.readNBytesCompat(magic, 4)
        if (!magic.contentEquals(MAGIC)) throw BadProjectFileException("Not an Urdu Canvas project file")
        val version = ins.read()
        if (version.toByte() != VERSION) throw BadProjectFileException("Unsupported version: $version")
        val lenBuf = ByteArray(4)
        ins.readNBytesCompat(lenBuf, 4)
        val thumbLen = ByteBuffer.wrap(lenBuf).int
        if (thumbLen < 0) throw BadProjectFileException("Corrupt header")
        ins.skipFully(thumbLen.toLong())
        val iv = ByteArray(IV_LEN)
        ins.readNBytesCompat(iv, IV_LEN)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(KEY, "AES"), GCMParameterSpec(TAG_BITS, iv))
        }
        try {
            CipherInputStream(ins, cipher).use { cis ->
                GZIPInputStream(cis).use { gz ->
                    tempJsonOut.outputStream().buffered().use { gz.copyTo(it) }
                }
            }
        } catch (e: Exception) {
            tempJsonOut.delete()
            throw BadProjectFileException("Corrupt or foreign file", e)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // THUMBNAIL  (cheap in-app gallery preview — no body decrypt, no Gson)
    // ──────────────────────────────────────────────────────────────────────

    fun readThumbnail(source: File): Bitmap? = source.inputStream().buffered().use { readThumbnail(it) }

    fun readThumbnail(stream: InputStream): Bitmap? {
        val s = stream.buffered()
        val header = ByteArray(HEADER_MIN)
        if (s.readNBytesCompat(header, HEADER_MIN) == HEADER_MIN &&
            header.copyOfRange(0, 4).contentEquals(MAGIC)
        ) {
            val thumbLen = ByteBuffer.wrap(header, 5, 4).int
            if (thumbLen > 0) {
                val thumb = ByteArray(thumbLen)
                if (s.readNBytesCompat(thumb, thumbLen) == thumbLen) {
                    return BitmapFactory.decodeByteArray(thumb, 0, thumbLen)
                }
            }
        }
        return null
    }

    fun isUrdcFile(file: File): Boolean = file.inputStream().use {
        val b = ByteArray(4)
        it.readNBytesCompat(b, 4) == 4 && b.contentEquals(MAGIC)
    }

    // ── stream helpers ─────────────────────────────────────────────────────
    private fun InputStream.readNBytesCompat(buf: ByteArray, len: Int): Int {
        var off = 0
        while (off < len) {
            val r = read(buf, off, len - off)
            if (r < 0) break
            off += r
        }
        return off
    }
    private fun InputStream.skipFully(n: Long) {
        var rem = n
        val tmp = ByteArray(8192)
        while (rem > 0) {
            val r = read(tmp, 0, minOf(tmp.size.toLong(), rem).toInt())
            if (r < 0) break
            rem -= r
        }
    }
    private fun OutputStream.nonClosing(): OutputStream = object : OutputStream() {
        override fun write(b: Int) = this@nonClosing.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = this@nonClosing.write(b, off, len)
        override fun flush() = this@nonClosing.flush()
        override fun close() { /* keep underlying stream open for trailing writes */ }
    }
}
