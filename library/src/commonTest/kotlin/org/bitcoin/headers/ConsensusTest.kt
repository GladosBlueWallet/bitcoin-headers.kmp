package org.bitcoin.headers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ConsensusTest {

    @Test
    fun validatesMainnetCheckpointPlusTheNextRealHeaderUsingMtpContext() {
        val chain = validateHeaderChain(
            MainnetHeadersFixture.headers.take(2),
            MAINNET_HEADER_CONSENSUS,
            1_800_000_000L,
        )

        assertEquals(665_281L, chain.tipHeight)
        assertEquals(MainnetHeadersFixture.headers[1].hashDisplay, chain.tipHashDisplay)
        assertEquals(2, chain.headers.size)
        assertSame(MAINNET_HEADER_CONSENSUS, chain.params)
        assertEquals(chain.chainWork, chain.cumulativeWorkByHeight[665_281L])
    }

    @Test
    fun rejectsAPostCheckpointHeaderThatDoesNotBeatMedianTimePast() {
        val seed = MainnetHeadersFixture.headers[0]
        val next = MainnetHeadersFixture.headers[1]
        val header = decodeBlockHeader(hexToBytes(next.headerHex))
        val bad = storedHeaderFromBlockHeader(665_281L, header.copy(timestamp = 1_610_201_799L))

        assertFailsMatching("median-time-past") {
            validateHeaderChain(listOf(seed, bad), MAINNET_HEADER_CONSENSUS, 1_800_000_000L)
        }
    }

    @Test
    fun validatesConstantNBitsAndABoundaryRetargetWithCumulativeWork() {
        val state = fixture()
        append(state, 1_010L)
        append(state, 1_020L)
        append(state, 1_040L)
        val retargetBits = expectedRetarget(EASY_BITS, 40L, state.params)
        append(state, 1_050L, retargetBits)

        val chain = validateHeaderChain(state.records, state.params, 10_000L)

        assertEquals(4L, chain.tipHeight)
        assertEquals(state.records[4].hashDisplay, chain.tipHashDisplay)
        assertEquals(4L, chain.heightByHashInternal[state.records[4].hashInternalHex])

        var expectedWork = com.ionspin.kotlin.bignum.integer.BigInteger.ZERO
        for (item in state.records) {
            val bits = decodeHeaderBits(item.headerHex)
            expectedWork += headerWork(decodeCompactTarget(bits, state.params.powLimit))
            assertEquals(expectedWork, chain.cumulativeWorkByHeight[item.height])
        }
        assertEquals(expectedWork, chain.chainWork)
        assertEquals(retargetBits, chain.entriesByHeight[4L]?.header?.bits)
    }

    @Test
    fun retargetClampsAnImplausiblyShortTimespanToOneQuarter() {
        val state = fixture()
        append(state, 1_001L)
        append(state, 1_002L)
        append(state, 1_003L)
        val expected = targetToCompact(EASY_LIMIT / com.ionspin.kotlin.bignum.integer.BigInteger.fromInt(4))
        assertEquals(expected, expectedRetarget(EASY_BITS, 3L, state.params))
        append(state, 1_004L, expected)

        val chain = validateHeaderChain(state.records, state.params, 10_000L)
        assertEquals(4L, chain.tipHeight)
        assertEquals(expected, chain.entriesByHeight[4L]?.header?.bits)
    }

    @Test
    fun rejectsStaleNBitsAtARetargetBoundary() {
        val state = fixture()
        append(state, 1_001L)
        append(state, 1_002L)
        append(state, 1_003L)
        append(state, 1_004L, EASY_BITS)

        assertFailsMatching("height 4.*nBits") {
            validateHeaderChain(state.records, state.params, 10_000L)
        }
    }

    @Test
    fun retargetClampsALongTimespanToFourTimesAndCapsAtPowLimit() {
        val state = fixture(HARD_BITS)
        append(state, 1_100L)
        append(state, 1_200L)
        append(state, 1_300L)
        val expected = expectedRetarget(HARD_BITS, 300L, state.params)
        append(state, 1_301L, expected)

        val chain = validateHeaderChain(state.records, state.params, 10_000L)
        assertEquals(expected, chain.entriesByHeight[4L]?.header?.bits)

        val capped = fixture()
        append(capped, 1_100L)
        append(capped, 1_200L)
        append(capped, 1_300L)
        append(capped, 1_301L, EASY_BITS)
        assertEquals(
            4L,
            validateHeaderChain(capped.records, capped.params, 10_000L).tipHeight,
        )
    }

    @Test
    fun rejectsAWrongBetweenBoundaryNBitsEasyTargetAttack() {
        val state = fixture(HARD_BITS)
        append(state, 1_010L, EASY_BITS)

        assertFailsMatching("height 1.*nBits") {
            validateHeaderChain(state.records, state.params, 10_000L)
        }
    }

    @Test
    fun rejectsInvalidProofOfWork() {
        val state = fixture()
        val invalid = mineHeader(
            previousHash = headerHashInternal(state.tip),
            bits = EASY_BITS,
            timestamp = 1_010L,
            marker = 2,
            validPow = false,
            powLimit = state.params.powLimit,
        )
        state.records.add(record(1L, invalid))

        assertFailsMatching("height 1.*proof-of-work") {
            validateHeaderChain(state.records, state.params, 10_000L)
        }
    }

    @Test
    fun rejectsMedianTimePastAndFutureTimeViolations() {
        val mtp = fixture()
        append(mtp, 940L)
        assertFailsMatching("height 1.*median-time-past") {
            validateHeaderChain(mtp.records, mtp.params, 10_000L)
        }

        val future = fixture()
        append(future, 17_201L)
        assertFailsMatching("height 1.*future") {
            validateHeaderChain(future.records, future.params, 10_000L)
        }
    }

    @Test
    fun allowsATimestampExactlyAtTheFutureSkewLimit() {
        val state = fixture()
        append(state, 1_000L + 7_200L)
        assertEquals(
            1L,
            validateHeaderChain(state.records, state.params, 1_000L).tipHeight,
        )
    }

    @Test
    fun rejectsABrokenPreviousHeaderLink() {
        val state = fixture()
        val unlinked = mineHeader(
            previousHash = ByteArray(32) { 0x42 },
            bits = EASY_BITS,
            timestamp = 1_010L,
            marker = 2,
            powLimit = state.params.powLimit,
        )
        state.records.add(record(1L, unlinked))

        assertFailsMatching("height 1.*previous hash") {
            validateHeaderChain(state.records, state.params, 10_000L)
        }
    }

    @Test
    fun rejectsEmptyChainsWrongStartsAndCheckpointMismatches() {
        val state = fixture()

        assertFailsMatching("empty") {
            validateHeaderChain(emptyList(), state.params, 10_000L)
        }

        val wrongHeight = listOf(state.records[0].copy(height = 1L))
        assertFailsMatching("trusted checkpoint") {
            validateHeaderChain(wrongHeight, state.params, 10_000L)
        }

        val wrongHash = listOf(state.records[0].copy(hashDisplay = "00".repeat(32)))
        assertFailsMatching("does not match") {
            validateHeaderChain(wrongHash, state.params, 10_000L)
        }
    }

    @Test
    fun rejectsNonContiguousHeightsAndCorruptedRecordDigests() {
        val state = fixture()
        append(state, 1_010L)
        val gap = listOf(
            state.records[0],
            state.records[1].copy(height = 2L),
        )
        assertFailsMatching("not contiguous") {
            validateHeaderChain(gap, state.params, 10_000L)
        }

        val badInternal = listOf(
            state.records[0],
            state.records[1].copy(hashInternalHex = "11".repeat(32)),
        )
        assertFailsMatching("hashInternalHex") {
            validateHeaderChain(badInternal, state.params, 10_000L)
        }

        val badDisplay = listOf(
            state.records[0],
            state.records[1].copy(hashDisplay = "22".repeat(32)),
        )
        assertFailsMatching("display hash") {
            validateHeaderChain(badDisplay, state.params, 10_000L)
        }
    }

    @Test
    fun headerBranchBuilderValidatesAHeavierForkFromACommonAncestor() {
        val state = fixture()
        append(state, 1_010L)
        append(state, 1_020L)
        append(state, 1_040L)
        val canonical = validateHeaderChain(state.records, state.params, 10_000L)

        val forkParent = state.records[1]
        val forkA = mineHeader(
            previousHash = hexToInternal(forkParent.hashInternalHex),
            bits = EASY_BITS,
            timestamp = 1_030L,
            marker = 20,
            powLimit = state.params.powLimit,
        )
        val forkB = mineHeader(
            previousHash = headerHashInternal(forkA),
            bits = EASY_BITS,
            timestamp = 1_041L,
            marker = 21,
            powLimit = state.params.powLimit,
        )

        val branch = HeaderBranchBuilder(canonical, 1L, 10_000L)
        branch.append(
            listOf(
                storedHeaderFromBlockHeader(2L, forkA),
                storedHeaderFromBlockHeader(3L, forkB),
            ),
        )
        val finished = branch.finish()

        assertEquals(1L, finished.commonAncestorHeight)
        assertEquals(3L, finished.tipHeight)
        assertEquals(2, finished.headers.size)
        assertTrue(finished.chainWork > canonical.cumulativeWorkByHeight.getValue(1L))
        assertEquals(headerHashDisplay(forkB), finished.tipHashDisplay)
    }

    @Test
    fun rejectsACheckpointThatIsNotOnARetargetBoundary() {
        val state = fixture()
        val params = state.params.copy(
            checkpoint = state.params.checkpoint.copy(
                height = 1L,
                previousTimestamps = listOf(900L),
            ),
        )
        val records = listOf(state.records[0].copy(height = 1L))

        assertFailsMatching("retarget interval boundary") {
            validateHeaderChain(records, params, 10_000L)
        }
    }

    @Test
    fun genesisAnchoredMtpUsesOnlyRealAncestors() {
        val equalToGenesis = fixture()
        append(equalToGenesis, 1_000L)
        assertFailsMatching("median-time-past") {
            validateHeaderChain(equalToGenesis.records, equalToGenesis.params, 10_000L)
        }

        val justAfterGenesis = fixture()
        append(justAfterGenesis, 1_001L)
        assertEquals(
            1L,
            validateHeaderChain(
                justAfterGenesis.records,
                justAfterGenesis.params,
                10_000L,
            ).tipHeight,
        )
    }

    @Test
    fun headerBranchBuilderRollsBackAPartialAppendOnFailure() {
        val state = fixture()
        val canonical = validateHeaderChain(state.records, state.params, 10_000L)
        val branch = HeaderBranchBuilder(canonical, 0L, 10_000L)
        val good = mineHeader(
            previousHash = hexToInternal(state.records[0].hashInternalHex),
            bits = EASY_BITS,
            timestamp = 1_010L,
            marker = 2,
            powLimit = state.params.powLimit,
        )
        val unlinked = mineHeader(
            previousHash = ByteArray(32) { 1 },
            bits = EASY_BITS,
            timestamp = 1_020L,
            marker = 3,
            powLimit = state.params.powLimit,
        )

        assertFailsMatching("previous hash") {
            branch.append(
                listOf(
                    storedHeaderFromBlockHeader(1L, good),
                    storedHeaderFromBlockHeader(2L, unlinked),
                ),
            )
        }
        assertEquals(0, branch.length)
        assertEquals(0L, branch.tipHeight)
    }

    @Test
    fun headerBranchBuilderRejectsAnInvalidCompetingHeader() {
        val state = fixture()
        append(state, 1_010L)
        val canonical = validateHeaderChain(state.records, state.params, 10_000L)
        val branch = HeaderBranchBuilder(canonical, 0L, 10_000L)
        val unlinked = mineHeader(
            previousHash = ByteArray(32) { 1 },
            bits = EASY_BITS,
            timestamp = 1_020L,
            marker = 9,
            powLimit = state.params.powLimit,
        )

        assertFailsMatching("previous hash") {
            branch.append(listOf(storedHeaderFromBlockHeader(1L, unlinked)))
        }
    }

    @Test
    fun emptyChainThrowsHeaderConsensusError() {
        val state = fixture()
        assertFailsWith<HeaderConsensusError> {
            validateHeaderChain(emptyList(), state.params, 10_000L)
        }
    }
}
