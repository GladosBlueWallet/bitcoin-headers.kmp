package org.bitcoin.headers

public data class BlockHeader(
    val version: Int,
    val previousBlockHash: ByteArray,
    val merkleRoot: ByteArray,
    val timestamp: Long,
    val bits: Long,
    val nonce: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BlockHeader) return false
        return version == other.version &&
            equalBytes(previousBlockHash, other.previousBlockHash) &&
            equalBytes(merkleRoot, other.merkleRoot) &&
            timestamp == other.timestamp &&
            bits == other.bits &&
            nonce == other.nonce
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + previousBlockHash.contentHashCode()
        result = 31 * result + merkleRoot.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + bits.hashCode()
        result = 31 * result + nonce.hashCode()
        return result
    }
}

public fun decodeBlockHeader(bytes: ByteArray): BlockHeader {
    if (bytes.size != 80) {
        throw IllegalArgumentException("header must be 80 bytes, got ${bytes.size}")
    }
    return BlockHeader(
        version = bytes.getInt32Le(0),
        previousBlockHash = bytes.copyOfRange(4, 36),
        merkleRoot = bytes.copyOfRange(36, 68),
        timestamp = bytes.getUInt32Le(68),
        bits = bytes.getUInt32Le(72),
        nonce = bytes.getUInt32Le(76),
    )
}

private fun assertUint32(value: Long, label: String) {
    if (value < 0L || value > 0xffffffffL) {
        throw IllegalArgumentException("$label must be a uint32")
    }
}

public fun encodeBlockHeader(header: BlockHeader): ByteArray {
    if (header.previousBlockHash.size != 32) {
        throw IllegalArgumentException("previous block hash must be 32 bytes")
    }
    if (header.merkleRoot.size != 32) {
        throw IllegalArgumentException("merkle root must be 32 bytes")
    }
    assertUint32(header.timestamp, "timestamp")
    assertUint32(header.bits, "bits")
    assertUint32(header.nonce, "nonce")
    val out = ByteArray(80)
    out.setInt32Le(0, header.version)
    header.previousBlockHash.copyInto(out, 4)
    header.merkleRoot.copyInto(out, 36)
    out.setUInt32Le(68, header.timestamp)
    out.setUInt32Le(72, header.bits)
    out.setUInt32Le(76, header.nonce)
    return out
}

internal fun ByteArray.getInt32Le(offset: Int): Int {
    val b0 = this[offset].toInt() and 0xff
    val b1 = this[offset + 1].toInt() and 0xff
    val b2 = this[offset + 2].toInt() and 0xff
    val b3 = this[offset + 3].toInt() and 0xff
    return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
}

internal fun ByteArray.getUInt32Le(offset: Int): Long =
    getInt32Le(offset).toLong() and 0xffffffffL

internal fun ByteArray.setInt32Le(offset: Int, value: Int) {
    this[offset] = value.toByte()
    this[offset + 1] = (value ushr 8).toByte()
    this[offset + 2] = (value ushr 16).toByte()
    this[offset + 3] = (value ushr 24).toByte()
}

internal fun ByteArray.setUInt32Le(offset: Int, value: Long) {
    setInt32Le(offset, value.toInt())
}
