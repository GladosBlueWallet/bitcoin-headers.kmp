package io.bluewallet.headers

import com.ionspin.kotlin.bignum.integer.BigInteger

public val MAX_UINT256: BigInteger = BigInteger.ONE.shl(256) - BigInteger.ONE
public val MAINNET_POW_LIMIT: BigInteger =
    BigInteger.parseString("ffff0000000000000000000000000000000000000000000000000000", 16)

/** Positive target → canonical Bitcoin compact `nBits`. */
public fun targetToCompact(target: BigInteger): Long {
    if (target <= BigInteger.ZERO) throw IllegalArgumentException("target must be positive")
    if (target > MAX_UINT256) throw IllegalArgumentException("target overflows uint256")

    val byteLength = (target.bitLength() + 7) / 8
    var compact =
        if (byteLength <= 3) {
            target.shl(8 * (3 - byteLength)).intValue(true)
        } else {
            target.shr(8 * (byteLength - 3)).intValue(true)
        }
    var exponent = byteLength
    if (compact and 0x00800000 != 0) {
        compact = compact ushr 8
        exponent++
    }
    return ((exponent shl 24) or compact).toLong() and 0xffffffffL
}

/**
 * Strict Bitcoin compact target decode. Invalid signs, zero, overflow,
 * non-canonical representations, and targets above the supplied limit fail.
 */
public fun decodeCompactTarget(
    bits: Long,
    powLimit: BigInteger = MAINNET_POW_LIMIT,
): BigInteger {
    if (bits < 0L || bits > 0xffffffffL) {
        throw IllegalArgumentException("compact target must be a uint32")
    }
    if (powLimit <= BigInteger.ZERO || powLimit > MAX_UINT256) {
        throw IllegalArgumentException("proof-of-work limit must be a positive uint256")
    }

    val compact = bits and 0xffffffffL
    val exponent = (compact ushr 24).toInt()
    val word = (compact and 0x007fffffL).toInt()
    if (word != 0 && (compact and 0x00800000L) != 0L) {
        throw IllegalArgumentException("compact target is negative")
    }
    if (
        word != 0 &&
        (exponent > 34 ||
            (word > 0xff && exponent > 33) ||
            (word > 0xffff && exponent > 32))
    ) {
        throw IllegalArgumentException("compact target overflows uint256")
    }

    val target =
        if (exponent <= 3) {
            BigInteger.fromInt(word ushr (8 * (3 - exponent)))
        } else {
            BigInteger.fromInt(word).shl(8 * (exponent - 3))
        }
    if (target == BigInteger.ZERO) throw IllegalArgumentException("compact target is zero")
    if (targetToCompact(target) != compact) {
        throw IllegalArgumentException("compact target is non-canonical")
    }
    if (target > powLimit) {
        throw IllegalArgumentException("compact target exceeds proof-of-work limit")
    }
    return target
}

/** Strict mainnet compact `nBits` → 256-bit target. */
public fun bitsToTarget(bits: Long): BigInteger {
    return decodeCompactTarget(bits, MAINNET_POW_LIMIT)
}

/** Interpret 32-byte internal (LE) hash as uint256. */
public fun hashToUint256(hashInternal: ByteArray): BigInteger {
    if (hashInternal.size != 32) throw IllegalArgumentException("hash must be 32 bytes")
    return uint64Le(hashInternal, 0) +
        uint64Le(hashInternal, 8).shl(64) +
        uint64Le(hashInternal, 16).shl(128) +
        uint64Le(hashInternal, 24).shl(192)
}

private fun uint64Le(bytes: ByteArray, offset: Int): BigInteger {
    var value = 0uL
    for (i in 0 until 8) {
        value = value or (bytes[offset + i].toUByte().toULong() shl (8 * i))
    }
    return BigInteger.fromULong(value)
}

public fun headerHashInternal(header: BlockHeader): ByteArray {
    return sha256d(encodeBlockHeader(header))
}

public fun headerHashDisplay(header: BlockHeader): String {
    return bytesToHexReversed(headerHashInternal(header))
}

public fun meetsTarget(
    hashInternal: ByteArray,
    bits: Long,
    powLimit: BigInteger = MAINNET_POW_LIMIT,
): Boolean {
    if (powLimit <= BigInteger.ZERO || powLimit > MAX_UINT256) {
        throw IllegalArgumentException("proof-of-work limit must be a positive uint256")
    }
    val target = try {
        decodeCompactTarget(bits, powLimit)
    } catch (_: IllegalArgumentException) {
        return false
    }
    return hashToUint256(hashInternal) <= target
}

public fun assertValidHeaderLink(
    header: BlockHeader,
    expectedPrevInternal: ByteArray,
    powLimit: BigInteger = MAINNET_POW_LIMIT,
): ByteArray {
    if (expectedPrevInternal.size != 32) {
        throw IllegalArgumentException("expected previous hash must be 32 bytes")
    }
    val hash = headerHashInternal(header)
    if (!equalBytes(header.previousBlockHash, expectedPrevInternal)) {
        throw IllegalArgumentException("header previous hash mismatch")
    }
    if (!meetsTarget(hash, header.bits, powLimit)) {
        throw IllegalArgumentException("header proof-of-work does not meet target")
    }
    return hash
}
