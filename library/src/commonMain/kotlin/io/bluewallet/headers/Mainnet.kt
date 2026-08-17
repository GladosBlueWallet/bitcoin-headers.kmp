package io.bluewallet.headers

/**
 * Mainnet difficulty-boundary checkpoint. The raw header was independently
 * cross-checked against both explorers:
 * https://mempool.space/api/block/00000000000000000009ebabc95533bbe0e40adecee879449db291237b3db350/header
 * https://blockstream.info/api/block/00000000000000000009ebabc95533bbe0e40adecee879449db291237b3db350/header
 */
public const val CHECKPOINT_HEIGHT: Long = 665_280L
public const val CHECKPOINT_DISPLAY_HASH: String =
    "00000000000000000009ebabc95533bbe0e40adecee879449db291237b3db350"

/** Full 80-byte header (wire/internal field order). */
public const val CHECKPOINT_HEADER_HEX: String =
    "0000002052d7f05def7bc6826cda74f5bdaf855fe13cd2c8aba50e000000000000000000bbb445df9b50f6555752df7e48d09c4f5dbd8e5c8ebf30aff1b55f8ed44d9e80b5caf95fa1a80d1714764687"

private val checkpointHeaderBytes: ByteArray = hexToBytes(CHECKPOINT_HEADER_HEX)

public val CHECKPOINT_HEADER: ByteArray
    get() = checkpointHeaderBytes.copyOf()

/**
 * Heights 665270..665279, in ascending order. Cross-checked against:
 *
 * https://blockstream.info/api/blocks/665279
 * https://mempool.space/api/v1/blocks/665279
 */
public val MAINNET_PRE_CHECKPOINT_TIMESTAMPS: List<Long> = listOf(
    1_610_199_116L,
    1_610_199_477L,
    1_610_199_700L,
    1_610_201_389L,
    1_610_201_449L,
    1_610_201_799L,
    1_610_204_487L,
    1_610_204_754L,
    1_610_204_957L,
    1_610_205_491L,
)

public val MAINNET_HEADER_CONSENSUS: HeaderConsensusParams = HeaderConsensusParams(
    powLimit = MAINNET_POW_LIMIT,
    targetSpacingSeconds = 10L * 60L,
    targetTimespanSeconds = 14L * 24L * 60L * 60L,
    retargetInterval = 2_016L,
    medianTimeSpan = 11L,
    maxFutureSeconds = 2L * 60L * 60L,
    checkpoint = TrustedHeaderCheckpoint(
        height = CHECKPOINT_HEIGHT,
        headerBytes = checkpointHeaderBytes.copyOf(),
        hashDisplay = CHECKPOINT_DISPLAY_HASH,
        previousTimestamps = MAINNET_PRE_CHECKPOINT_TIMESTAMPS,
    ),
)

public fun checkpointHeader(): BlockHeader {
    val header = decodeBlockHeader(checkpointHeaderBytes)
    val display = headerHashDisplay(header)
    if (display != CHECKPOINT_DISPLAY_HASH) {
        throw IllegalStateException(
            "checkpoint header hash mismatch: got $display, expected $CHECKPOINT_DISPLAY_HASH",
        )
    }
    if (!meetsTarget(headerHashInternal(header), header.bits)) {
        throw IllegalStateException("checkpoint header fails PoW check")
    }
    val encoded = encodeBlockHeader(header)
    for (i in 0 until 80) {
        if (encoded[i] != checkpointHeaderBytes[i]) {
            throw IllegalStateException("checkpoint header encode mismatch")
        }
    }
    return header
}

public data class CheckpointSeedRecord(
    val height: Long,
    val hashDisplay: String,
    val hashInternalHex: String,
    val headerHex: String,
    val header: BlockHeader,
    val hashInternal: ByteArray,
) {
    public fun toHeaderRecord(): HeaderRecord =
        HeaderRecord(height, hashDisplay, hashInternalHex, headerHex)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CheckpointSeedRecord) return false
        return height == other.height &&
            hashDisplay == other.hashDisplay &&
            hashInternalHex == other.hashInternalHex &&
            headerHex == other.headerHex &&
            header == other.header &&
            equalBytes(hashInternal, other.hashInternal)
    }

    override fun hashCode(): Int {
        var result = height.hashCode()
        result = 31 * result + hashDisplay.hashCode()
        result = 31 * result + hashInternalHex.hashCode()
        result = 31 * result + headerHex.hashCode()
        result = 31 * result + header.hashCode()
        result = 31 * result + hashInternal.contentHashCode()
        return result
    }
}

public fun checkpointSeedRecord(): CheckpointSeedRecord {
    val header = checkpointHeader()
    val hashInternal = headerHashInternal(header)
    return CheckpointSeedRecord(
        height = CHECKPOINT_HEIGHT,
        hashDisplay = CHECKPOINT_DISPLAY_HASH,
        hashInternalHex = bytesToHex(hashInternal),
        headerHex = bytesToHex(checkpointHeaderBytes),
        header = header,
        hashInternal = hashInternal,
    )
}
