package com.watermelon.subtitle.hash

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * OpenSubtitles file-hash algorithm (Manifest §6.1). The hash is the 64-bit sum of:
 *   - the file size, plus
 *   - every little-endian unsigned 64-bit word in the first 64 KB, plus
 *   - every little-endian unsigned 64-bit word in the last 64 KB.
 *
 * Arithmetic is performed mod 2^64 (natural [Long] overflow). The result is rendered as a
 * zero-padded 16-char lowercase hex string, matching the reference implementation/test
 * vectors.
 */
object OpenSubtitlesHasher {

    private const val CHUNK_SIZE = 64 * 1024 // 64 KB

    fun hash(file: File): String {
        val fileSize = file.length()
        require(fileSize >= CHUNK_SIZE) {
            "File must be at least $CHUNK_SIZE bytes for OpenSubtitles hashing"
        }
        RandomAccessFile(file, "r").use { raf ->
            var hash = fileSize
            hash += sumChunk(raf, 0L)
            hash += sumChunk(raf, fileSize - CHUNK_SIZE)
            return toHex(hash)
        }
    }

    /** Sum the little-endian uint64 words of the 64 KB chunk starting at [offset]. */
    private fun sumChunk(raf: RandomAccessFile, offset: Long): Long {
        val buffer = ByteArray(CHUNK_SIZE)
        raf.seek(offset)
        raf.readFully(buffer)
        val bb = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0L
        val words = CHUNK_SIZE / 8
        for (i in 0 until words) {
            sum += bb.long // wraps mod 2^64, which is the intended behaviour
        }
        return sum
    }

    private fun toHex(value: Long): String =
        String.format("%016x", value)
}
