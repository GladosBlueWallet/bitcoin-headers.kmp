package org.bitcoin.headers

import com.ionspin.kotlin.bignum.integer.BigInteger

public data class TrustedHeaderCheckpoint(
    /** Must be a retarget-interval boundary so period-start headers are available. */
    val height: Long,
    val headerBytes: ByteArray,
    val hashDisplay: String,
    /** Consecutive timestamps immediately before the checkpoint. */
    val previousTimestamps: List<Long>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrustedHeaderCheckpoint) return false
        return height == other.height &&
            equalBytes(headerBytes, other.headerBytes) &&
            hashDisplay == other.hashDisplay &&
            previousTimestamps == other.previousTimestamps
    }

    override fun hashCode(): Int {
        var result = height.hashCode()
        result = 31 * result + headerBytes.contentHashCode()
        result = 31 * result + hashDisplay.hashCode()
        result = 31 * result + previousTimestamps.hashCode()
        return result
    }
}

public data class HeaderConsensusParams(
    val powLimit: BigInteger,
    val targetSpacingSeconds: Long,
    val targetTimespanSeconds: Long,
    val retargetInterval: Long,
    val medianTimeSpan: Long,
    val maxFutureSeconds: Long,
    val checkpoint: TrustedHeaderCheckpoint,
)

public data class HeaderChainEntry(
    val record: HeaderRecord,
    val header: BlockHeader,
    val hashInternal: ByteArray,
    val target: BigInteger,
    val work: BigInteger,
    val cumulativeWork: BigInteger,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HeaderChainEntry) return false
        return record == other.record &&
            header == other.header &&
            equalBytes(hashInternal, other.hashInternal) &&
            target == other.target &&
            work == other.work &&
            cumulativeWork == other.cumulativeWork
    }

    override fun hashCode(): Int {
        var result = record.hashCode()
        result = 31 * result + header.hashCode()
        result = 31 * result + hashInternal.contentHashCode()
        result = 31 * result + target.hashCode()
        result = 31 * result + work.hashCode()
        result = 31 * result + cumulativeWork.hashCode()
        return result
    }
}

public data class ValidatedHeaderChain(
    val headers: List<HeaderRecord>,
    val tipHeight: Long,
    val tipHashInternal: ByteArray,
    val tipHashDisplay: String,
    /** Cumulative work represented by this chain, beginning at its checkpoint. */
    val chainWork: BigInteger,
    val params: HeaderConsensusParams,
    val byHeight: Map<Long, HeaderRecord>,
    val heightByHashInternal: Map<String, Long>,
    val entriesByHeight: Map<Long, HeaderChainEntry>,
    val cumulativeWorkByHeight: Map<Long, BigInteger>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ValidatedHeaderChain) return false
        return headers == other.headers &&
            tipHeight == other.tipHeight &&
            equalBytes(tipHashInternal, other.tipHashInternal) &&
            tipHashDisplay == other.tipHashDisplay &&
            chainWork == other.chainWork &&
            params == other.params &&
            byHeight == other.byHeight &&
            heightByHashInternal == other.heightByHashInternal &&
            entriesByHeight == other.entriesByHeight &&
            cumulativeWorkByHeight == other.cumulativeWorkByHeight
    }

    override fun hashCode(): Int {
        var result = headers.hashCode()
        result = 31 * result + tipHeight.hashCode()
        result = 31 * result + tipHashInternal.contentHashCode()
        result = 31 * result + tipHashDisplay.hashCode()
        result = 31 * result + chainWork.hashCode()
        result = 31 * result + params.hashCode()
        result = 31 * result + byHeight.hashCode()
        result = 31 * result + heightByHashInternal.hashCode()
        result = 31 * result + entriesByHeight.hashCode()
        result = 31 * result + cumulativeWorkByHeight.hashCode()
        return result
    }
}

public data class ValidatedHeaderBranch(
    val commonAncestorHeight: Long,
    /** Replacement records strictly after the common ancestor. */
    val headers: List<HeaderRecord>,
    val tipHeight: Long,
    val tipHashInternal: ByteArray,
    val tipHashDisplay: String,
    /** Checkpoint-relative work including the common chain through the branch tip. */
    val chainWork: BigInteger,
    val entriesByHeight: Map<Long, HeaderChainEntry>,
    val cumulativeWorkByHeight: Map<Long, BigInteger>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ValidatedHeaderBranch) return false
        return commonAncestorHeight == other.commonAncestorHeight &&
            headers == other.headers &&
            tipHeight == other.tipHeight &&
            equalBytes(tipHashInternal, other.tipHashInternal) &&
            tipHashDisplay == other.tipHashDisplay &&
            chainWork == other.chainWork &&
            entriesByHeight == other.entriesByHeight &&
            cumulativeWorkByHeight == other.cumulativeWorkByHeight
    }

    override fun hashCode(): Int {
        var result = commonAncestorHeight.hashCode()
        result = 31 * result + headers.hashCode()
        result = 31 * result + tipHeight.hashCode()
        result = 31 * result + tipHashInternal.contentHashCode()
        result = 31 * result + tipHashDisplay.hashCode()
        result = 31 * result + chainWork.hashCode()
        result = 31 * result + entriesByHeight.hashCode()
        result = 31 * result + cumulativeWorkByHeight.hashCode()
        return result
    }
}

public class HeaderConsensusError(
    public val height: Long,
    message: String,
    cause: Throwable? = null,
) : Exception("header consensus failure at height $height: $message", cause)

private fun fail(height: Long, message: String, cause: Throwable? = null): Nothing {
    throw HeaderConsensusError(height, message, cause)
}

private fun assertSafePositiveInteger(value: Long, label: String) {
    if (value < 1L) {
        throw IllegalArgumentException("$label must be a positive safe integer")
    }
}

private fun validateParams(params: HeaderConsensusParams) {
    if (params.powLimit <= BigInteger.ZERO || params.powLimit >= BigInteger.ONE.shl(256)) {
        throw IllegalArgumentException("powLimit must be a positive uint256")
    }
    assertSafePositiveInteger(params.targetSpacingSeconds, "targetSpacingSeconds")
    assertSafePositiveInteger(params.targetTimespanSeconds, "targetTimespanSeconds")
    assertSafePositiveInteger(params.retargetInterval, "retargetInterval")
    assertSafePositiveInteger(params.medianTimeSpan, "medianTimeSpan")
    assertSafePositiveInteger(params.maxFutureSeconds, "maxFutureSeconds")
    if (params.checkpoint.height < 0L) {
        throw IllegalArgumentException("checkpoint height must be a non-negative safe integer")
    }
    if (params.checkpoint.height % params.retargetInterval != 0L) {
        throw IllegalArgumentException("checkpoint height must be a retarget interval boundary")
    }
    if (params.checkpoint.headerBytes.size != 80) {
        throw IllegalArgumentException("checkpoint header must be exactly 80 bytes")
    }
    val precedingTimestamps =
        minOf(params.medianTimeSpan - 1L, params.checkpoint.height)
    if (params.checkpoint.previousTimestamps.size.toLong() != precedingTimestamps) {
        throw IllegalArgumentException(
            "checkpoint requires exactly $precedingTimestamps preceding timestamps",
        )
    }
    for (timestamp in params.checkpoint.previousTimestamps) {
        if (timestamp < 0L || timestamp > 0xffffffffL) {
            throw IllegalArgumentException("checkpoint timestamps must be uint32 values")
        }
    }
}

public fun storedHeaderFromBlockHeader(height: Long, header: BlockHeader): HeaderRecord {
    if (height < 0L) {
        throw IllegalArgumentException("header height must be a non-negative safe integer")
    }
    val bytes = encodeBlockHeader(header)
    val hashInternal = sha256d(bytes)
    return HeaderRecord(
        height = height,
        hashDisplay = displayHash(hashInternal),
        hashInternalHex = bytesToHex(hashInternal),
        headerHex = bytesToHex(bytes),
    )
}

public fun headerWork(target: BigInteger): BigInteger {
    if (target <= BigInteger.ZERO || target >= BigInteger.ONE.shl(256)) {
        throw IllegalArgumentException("work target must be a positive uint256")
    }
    return BigInteger.ONE.shl(256) / (target + BigInteger.ONE)
}

private fun displayHash(hashInternal: ByteArray): String {
    return bytesToHexReversed(hashInternal)
}

private data class DecodedHeader(
    val record: HeaderRecord,
    val header: BlockHeader,
    val hashInternal: ByteArray,
    val target: BigInteger,
    val work: BigInteger,
)

private fun decodeRecord(record: HeaderRecord, powLimit: BigInteger): DecodedHeader {
    val height = record.height
    val hashDisplay = record.hashDisplay
    val hashInternalHex = record.hashInternalHex
    val headerHex = record.headerHex
    if (height < 0L) {
        fail(height, "height must be a non-negative safe integer")
    }
    val bytes = try {
        bytesFromHex(headerHex, 80, "headerHex")
    } catch (error: Exception) {
        fail(height, error.message ?: error.toString(), error)
    }
    val header = decodeBlockHeader(bytes)
    val hashInternal = sha256d(bytes)
    if (bytesToHex(hashInternal) != hashInternalHex) {
        fail(height, "hashInternalHex does not match serialized header")
    }
    if (displayHash(hashInternal) != hashDisplay) {
        fail(height, "display hash does not match serialized header")
    }
    val target = try {
        decodeCompactTarget(header.bits, powLimit)
    } catch (error: Exception) {
        fail(
            height,
            "invalid nBits 0x${header.bits.toString(16).padStart(8, '0')}: ${error.message ?: error}",
            error,
        )
    }
    return DecodedHeader(
        record = HeaderRecord(height, hashDisplay, hashInternalHex, headerHex),
        header = header.copy(
            previousBlockHash = header.previousBlockHash.copyOf(),
            merkleRoot = header.merkleRoot.copyOf(),
        ),
        hashInternal = hashInternal,
        target = target,
        work = headerWork(target),
    )
}

private fun expectedBits(
    height: Long,
    previous: HeaderChainEntry,
    entryAt: (Long) -> HeaderChainEntry?,
    params: HeaderConsensusParams,
): Long {
    if (height % params.retargetInterval != 0L) return previous.header.bits

    val periodStartHeight = height - params.retargetInterval
    val periodStart = entryAt(periodStartHeight)
        ?: fail(height, "missing retarget period start at height $periodStartHeight")
    var actualTimespan = previous.header.timestamp - periodStart.header.timestamp
    val minimumTimespan = params.targetTimespanSeconds / 4L
    val maximumTimespan = params.targetTimespanSeconds * 4L
    actualTimespan = actualTimespan.coerceIn(minimumTimespan, maximumTimespan)
    var nextTarget =
        (previous.target * BigInteger.fromLong(actualTimespan)) /
            BigInteger.fromLong(params.targetTimespanSeconds)
    if (nextTarget > params.powLimit) nextTarget = params.powLimit
    return try {
        targetToCompact(nextTarget)
    } catch (error: Exception) {
        fail(
            height,
            "retarget produced an invalid target: ${error.message ?: error}",
            error,
        )
    }
}

private fun median(values: MutableList<Long>): Long {
    values.sort()
    return values[values.size / 2]
}

private fun assertTimestamp(
    height: Long,
    header: BlockHeader,
    timestampAt: (Long) -> Long?,
    params: HeaderConsensusParams,
    currentTimeSeconds: Long,
) {
    val prior = mutableListOf<Long>()
    val oldest = maxOf(0L, height - params.medianTimeSpan)
    var priorHeight = oldest
    while (priorHeight < height) {
        val timestamp = timestampAt(priorHeight)
            ?: fail(height, "missing median-time-past timestamp at height $priorHeight")
        prior.add(timestamp)
        priorHeight++
    }
    val medianTimePast = median(prior)
    if (header.timestamp <= medianTimePast) {
        fail(
            height,
            "timestamp ${header.timestamp} does not exceed median-time-past $medianTimePast",
        )
    }
    if (header.timestamp > currentTimeSeconds + params.maxFutureSeconds) {
        fail(height, "timestamp ${header.timestamp} is too far in the future")
    }
}

private fun assertLink(height: Long, header: BlockHeader, previous: HeaderChainEntry) {
    if (!equalBytes(header.previousBlockHash, previous.hashInternal)) {
        fail(height, "previous hash does not match height ${previous.record.height}")
    }
}

private fun assertProofOfWork(entry: DecodedHeader) {
    if (hashToUint256(entry.hashInternal) > entry.target) {
        fail(entry.record.height, "proof-of-work hash exceeds target")
    }
}

private fun checkpointTimestampAt(height: Long, params: HeaderConsensusParams): Long? {
    val first = params.checkpoint.height - params.checkpoint.previousTimestamps.size
    val index = height - first
    return if (index >= 0 && index < params.checkpoint.previousTimestamps.size) {
        params.checkpoint.previousTimestamps[index.toInt()]
    } else {
        null
    }
}

private fun validateSuccessor(
    record: HeaderRecord,
    previous: HeaderChainEntry,
    params: HeaderConsensusParams,
    currentTimeSeconds: Long,
    entryAt: (Long) -> HeaderChainEntry?,
): HeaderChainEntry {
    val decoded = decodeRecord(record, params.powLimit)
    val height = decoded.record.height
    if (height != previous.record.height + 1L) {
        fail(height, "height is not contiguous after ${previous.record.height}")
    }
    assertLink(height, decoded.header, previous)
    val requiredBits = expectedBits(height, previous, entryAt, params)
    if (decoded.header.bits != requiredBits) {
        fail(
            height,
            "nBits 0x${decoded.header.bits.toString(16).padStart(8, '0')} " +
                "does not equal expected 0x${requiredBits.toString(16).padStart(8, '0')}",
        )
    }
    assertTimestamp(
        height,
        decoded.header,
        { lookupHeight ->
            entryAt(lookupHeight)?.header?.timestamp ?: checkpointTimestampAt(lookupHeight, params)
        },
        params,
        currentTimeSeconds,
    )
    assertProofOfWork(decoded)
    return HeaderChainEntry(
        record = decoded.record,
        header = decoded.header,
        hashInternal = decoded.hashInternal,
        target = decoded.target,
        work = decoded.work,
        cumulativeWork = previous.cumulativeWork + decoded.work,
    )
}

/**
 * Incrementally validates one candidate without copying or changing the
 * canonical chain. `append` is linear in newly supplied headers.
 */
public class HeaderBranchBuilder(
    base: ValidatedHeaderChain,
    commonAncestorHeight: Long,
    currentTimeSeconds: Long,
) {
    private val base: ValidatedHeaderChain
    private val params: HeaderConsensusParams
    private val currentTimeSeconds: Long
    private val commonAncestorHeight: Long
    private val headers = mutableListOf<HeaderRecord>()
    private val entriesByHeight = mutableMapOf<Long, HeaderChainEntry>()
    private val cumulativeWorkByHeight = mutableMapOf<Long, BigInteger>()
    private var previous: HeaderChainEntry

    init {
        validateParams(base.params)
        if (currentTimeSeconds < 0L) {
            throw IllegalArgumentException("currentTimeSeconds must be a non-negative safe integer")
        }
        val ancestor = base.entriesByHeight[commonAncestorHeight]
            ?: throw IllegalArgumentException(
                "common ancestor $commonAncestorHeight is not in the canonical chain",
            )
        this.base = base
        this.params = base.params
        this.currentTimeSeconds = currentTimeSeconds
        this.commonAncestorHeight = commonAncestorHeight
        this.previous = ancestor
    }

    public val length: Int
        get() = headers.size

    public val tipHeight: Long
        get() = previous.record.height

    public val tipHashInternal: ByteArray
        get() = previous.hashInternal.copyOf()

    public fun append(records: List<HeaderRecord>) {
        val entryAt: (Long) -> HeaderChainEntry? = { height ->
            entriesByHeight[height] ?: base.entriesByHeight[height]
        }

        val startLength = headers.size
        val startPrevious = previous
        try {
            for (record in records) {
                val entry = validateSuccessor(
                    record,
                    previous,
                    params,
                    currentTimeSeconds,
                    entryAt,
                )
                headers.add(entry.record)
                entriesByHeight[entry.record.height] = entry
                cumulativeWorkByHeight[entry.record.height] = entry.cumulativeWork
                previous = entry
            }
        } catch (error: Exception) {
            while (headers.size > startLength) {
                val record = headers.removeAt(headers.lastIndex)
                entriesByHeight.remove(record.height)
                cumulativeWorkByHeight.remove(record.height)
            }
            previous = startPrevious
            throw error
        }
    }

    public fun finish(): ValidatedHeaderBranch {
        return ValidatedHeaderBranch(
            commonAncestorHeight = commonAncestorHeight,
            headers = headers.toList(),
            tipHeight = previous.record.height,
            tipHashInternal = previous.hashInternal.copyOf(),
            tipHashDisplay = previous.record.hashDisplay,
            chainWork = previous.cumulativeWork,
            entriesByHeight = entriesByHeight.toMap(),
            cumulativeWorkByHeight = cumulativeWorkByHeight.toMap(),
        )
    }
}

public fun validateHeaderChain(
    records: List<HeaderRecord>,
    params: HeaderConsensusParams,
    currentTimeSeconds: Long,
): ValidatedHeaderChain {
    validateParams(params)
    if (currentTimeSeconds < 0L) {
        throw IllegalArgumentException("currentTimeSeconds must be a non-negative safe integer")
    }
    if (records.isEmpty()) {
        throw HeaderConsensusError(params.checkpoint.height, "persisted chain is empty")
    }

    val firstRecord = records[0]
    val first = try {
        decodeRecord(firstRecord, params.powLimit)
    } catch (error: HeaderConsensusError) {
        throw error
    } catch (error: Exception) {
        fail(params.checkpoint.height, "cannot decode trusted checkpoint", error)
    }
    if (first.record.height != params.checkpoint.height) {
        fail(
            first.record.height,
            "chain must start at trusted checkpoint ${params.checkpoint.height}",
        )
    }
    if (
        !equalBytes(encodeBlockHeader(first.header), params.checkpoint.headerBytes) ||
        first.record.hashDisplay != params.checkpoint.hashDisplay
    ) {
        fail(first.record.height, "trusted checkpoint seed does not match")
    }
    assertProofOfWork(first)
    if (first.header.timestamp > currentTimeSeconds + params.maxFutureSeconds) {
        fail(first.record.height, "trusted checkpoint timestamp is too far in the future")
    }

    val headers = mutableListOf(first.record)
    val byHeight = mutableMapOf(first.record.height to first.record)
    val heightByHashInternal = mutableMapOf(first.record.hashInternalHex to first.record.height)
    val entriesByHeight = mutableMapOf<Long, HeaderChainEntry>()
    val cumulativeWorkByHeight = mutableMapOf<Long, BigInteger>()
    val firstEntry = HeaderChainEntry(
        record = first.record,
        header = first.header,
        hashInternal = first.hashInternal,
        target = first.target,
        work = first.work,
        cumulativeWork = first.work,
    )
    entriesByHeight[first.record.height] = firstEntry
    cumulativeWorkByHeight[first.record.height] = firstEntry.cumulativeWork

    val entryAt: (Long) -> HeaderChainEntry? = { height -> entriesByHeight[height] }

    var previous = firstEntry
    for (index in 1 until records.size) {
        val entry = validateSuccessor(
            records[index],
            previous,
            params,
            currentTimeSeconds,
            entryAt,
        )
        headers.add(entry.record)
        byHeight[entry.record.height] = entry.record
        heightByHashInternal[entry.record.hashInternalHex] = entry.record.height
        entriesByHeight[entry.record.height] = entry
        cumulativeWorkByHeight[entry.record.height] = entry.cumulativeWork
        previous = entry
    }

    return ValidatedHeaderChain(
        headers = headers.toList(),
        tipHeight = previous.record.height,
        tipHashInternal = previous.hashInternal.copyOf(),
        tipHashDisplay = previous.record.hashDisplay,
        chainWork = previous.cumulativeWork,
        params = params,
        byHeight = byHeight,
        heightByHashInternal = heightByHashInternal,
        entriesByHeight = entriesByHeight,
        cumulativeWorkByHeight = cumulativeWorkByHeight,
    )
}
