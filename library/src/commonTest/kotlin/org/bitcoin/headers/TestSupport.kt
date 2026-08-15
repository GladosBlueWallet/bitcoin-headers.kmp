package org.bitcoin.headers

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.test.assertFails
import kotlin.test.assertTrue

internal const val EASY_BITS = 0x207fffffL
internal const val HARD_BITS = 0x201fffffL
internal val EASY_LIMIT: BigInteger = decodeCompactTarget(EASY_BITS, MAX_UINT256)

internal data class ChainFixture(
    var params: HeaderConsensusParams,
    val records: MutableList<HeaderRecord>,
    var tip: BlockHeader,
)

internal fun mineHeader(
    bits: Long,
    timestamp: Long,
    marker: Int,
    previousHash: ByteArray? = null,
    validPow: Boolean = true,
    powLimit: BigInteger = EASY_LIMIT,
): BlockHeader {
    val target = decodeCompactTarget(bits, powLimit)
    var header = BlockHeader(
        version = marker,
        previousBlockHash = previousHash?.copyOf() ?: ByteArray(32),
        merkleRoot = ByteArray(32) { (marker and 0xff).toByte() },
        timestamp = timestamp,
        bits = bits,
        nonce = 0L,
    )
    var nonce = 0L
    while (nonce <= 0xffffffffL) {
        header = header.copy(nonce = nonce)
        val valid = hashToUint256(headerHashInternal(header)) <= target
        if (valid == validPow) return header
        nonce++
    }
    throw IllegalStateException("unable to mine deterministic test header")
}

internal fun record(height: Long, header: BlockHeader): HeaderRecord {
    return storedHeaderFromBlockHeader(height, header)
}

internal fun fixture(bits: Long = EASY_BITS): ChainFixture {
    val checkpoint = mineHeader(
        bits = bits,
        timestamp = 1_000L,
        marker = 1,
        powLimit = EASY_LIMIT,
    )
    return ChainFixture(
        params = HeaderConsensusParams(
            powLimit = EASY_LIMIT,
            targetSpacingSeconds = 10L,
            targetTimespanSeconds = 40L,
            retargetInterval = 4L,
            medianTimeSpan = 11L,
            maxFutureSeconds = 7_200L,
            checkpoint = TrustedHeaderCheckpoint(
                height = 0L,
                headerBytes = encodeBlockHeader(checkpoint),
                hashDisplay = headerHashDisplay(checkpoint),
                previousTimestamps = emptyList(),
            ),
        ),
        records = mutableListOf(record(0L, checkpoint)),
        tip = checkpoint,
    )
}

internal fun append(
    state: ChainFixture,
    timestamp: Long,
    bits: Long = state.tip.bits,
    marker: Int = state.records.size + 1,
): BlockHeader {
    val next = mineHeader(
        previousHash = headerHashInternal(state.tip),
        bits = bits,
        timestamp = timestamp,
        marker = marker,
        powLimit = state.params.powLimit,
    )
    state.records.add(record(state.records.size.toLong(), next))
    state.tip = next
    return next
}

/** Independently computed expected nBits for the synthetic retarget params. */
internal fun expectedRetarget(
    previousBits: Long,
    actualTimespan: Long,
    params: HeaderConsensusParams,
): Long {
    val clamped = actualTimespan.coerceIn(
        params.targetTimespanSeconds / 4L,
        params.targetTimespanSeconds * 4L,
    )
    val target =
        (decodeCompactTarget(previousBits, params.powLimit) * BigInteger.fromLong(clamped)) /
            BigInteger.fromLong(params.targetTimespanSeconds)
    return targetToCompact(if (target > params.powLimit) params.powLimit else target)
}

internal fun hexToInternal(hex: String): ByteArray {
    val out = ByteArray(hex.length / 2)
    for (i in out.indices) {
        out[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
    return out
}

internal fun decodeHeaderBits(headerHex: String): Long {
    val bytes = hexToInternal(headerHex)
    return bytes.getUInt32Le(72)
}

internal fun assertFailsMatching(pattern: String, block: () -> Unit) {
    val error = assertFails(block)
    val message = error.message ?: ""
    assertTrue(
        Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(message),
        "expected message matching /$pattern/i, got: $message",
    )
}
