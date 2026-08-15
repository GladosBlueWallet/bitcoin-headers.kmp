package org.bitcoin.headers

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val MAINNET_665280_HEADER_HEX =
    "0000002052d7f05def7bc6826cda74f5bdaf855fe13cd2c8aba50e000000000000000000bbb445df9b50f6555752df7e48d09c4f5dbd8e5c8ebf30aff1b55f8ed44d9e80b5caf95fa1a80d1714764687"
private const val MAINNET_665280_DISPLAY =
    "00000000000000000009ebabc95533bbe0e40adecee879449db291237b3db350"

class PowTest {

    @Test
    fun hashesAKnownMainnetHeaderAndAcceptsItsProofOfWork() {
        val header = decodeBlockHeader(hexToBytes(MAINNET_665280_HEADER_HEX))
        val hashInternal = headerHashInternal(header)

        assertEquals(MAINNET_665280_DISPLAY, headerHashDisplay(header))
        assertEquals(MAINNET_665280_HEADER_HEX, bytesToHex(encodeBlockHeader(header)))
        assertTrue(meetsTarget(hashInternal, header.bits))
        assertTrue(hashToUint256(hashInternal) <= bitsToTarget(header.bits))
    }

    @Test
    fun meetsTargetRejectsAHashAboveTheCompactTarget() {
        val header = decodeBlockHeader(hexToBytes(MAINNET_665280_HEADER_HEX))
        val aboveTarget = ByteArray(32)
        aboveTarget[31] = 0xff.toByte()
        assertFalse(meetsTarget(aboveTarget, header.bits))
    }

    @Test
    fun assertValidHeaderLinkAcceptsTheRealMainnetSuccessorHeader() {
        val parent = decodeBlockHeader(hexToBytes(MAINNET_665280_HEADER_HEX))
        val parentHash = headerHashInternal(parent)
        val child = decodeBlockHeader(hexToBytes(MainnetHeadersFixture.headers[1].headerHex))

        val linked = assertValidHeaderLink(child, parentHash)
        assertEquals(bytesToHex(headerHashInternal(child)), bytesToHex(linked))
    }

    @Test
    fun assertValidHeaderLinkRejectsPreviousHashAndPowFailures() {
        val parent = decodeBlockHeader(hexToBytes(MAINNET_665280_HEADER_HEX))
        val parentHash = headerHashInternal(parent)
        val child = decodeBlockHeader(hexToBytes(MainnetHeadersFixture.headers[1].headerHex))

        val unlinked = child.copy(previousBlockHash = ByteArray(32) { 9 })
        assertFailsMatching("previous hash mismatch") {
            assertValidHeaderLink(unlinked, parentHash)
        }

        val weakPow = child.copy(nonce = (child.nonce + 1L) and 0xffffffffL)
        assertFailsMatching("proof-of-work") {
            assertValidHeaderLink(weakPow, parentHash)
        }

        val padded = ByteArray(64)
        parentHash.copyInto(padded)
        assertFailsMatching("32 bytes") {
            assertValidHeaderLink(child, padded)
        }
        assertFailsMatching("32 bytes") {
            assertValidHeaderLink(child, parentHash.copyOf(31))
        }
    }

    @Test
    fun encodeDecodeRoundTripsHeaderFields() {
        val original = decodeBlockHeader(hexToBytes(MAINNET_665280_HEADER_HEX))
        val again = decodeBlockHeader(encodeBlockHeader(original))
        assertEquals(original.version, again.version)
        assertEquals(bytesToHex(original.previousBlockHash), bytesToHex(again.previousBlockHash))
        assertEquals(bytesToHex(original.merkleRoot), bytesToHex(again.merkleRoot))
        assertEquals(original.timestamp, again.timestamp)
        assertEquals(original.bits, again.bits)
        assertEquals(original.nonce, again.nonce)
    }

    @Test
    fun mainnetRetargetAtHeight667296MatchesTheRealCompactTarget() {
        val periodStart = decodeBlockHeader(hexToBytes(MAINNET_665280_HEADER_HEX))
        val periodEndTimestamp = 1_611_402_924L
        val timespan = 14L * 24L * 60L * 60L
        val actualTimespan = periodEndTimestamp - periodStart.timestamp
        val clamped = actualTimespan.coerceIn(timespan / 4L, timespan * 4L)
        val next =
            (bitsToTarget(periodStart.bits) * BigInteger.fromLong(clamped)) /
                BigInteger.fromLong(timespan)
        assertEquals(0x170d8457L, targetToCompact(next))
    }

    @Test
    fun hashToUint256Reads32ByteHashesLittleEndian() {
        val bytes = ByteArray(32)
        bytes[0] = 0x01
        bytes[1] = 0x02
        assertEquals(BigInteger.parseString("0201", 16), hashToUint256(bytes))
        assertFailsMatching("32 bytes") {
            hashToUint256(ByteArray(31))
        }

        val padded = ByteArray(40)
        padded[5] = 0x01
        padded[6] = 0x02
        assertEquals(BigInteger.parseString("0201", 16), hashToUint256(padded.copyOfRange(5, 37)))
    }

    @Test
    fun meetsTargetReturnsFalseForInvalidCompactNBitsInsteadOfThrowing() {
        val hash = ByteArray(32)
        assertFalse(meetsTarget(hash, 0x00000000L))
        assertFalse(meetsTarget(hash, 0x1d80ffffL))
        assertFalse(meetsTarget(hash, 0x2300ffffL))
        assertFalse(meetsTarget(hash, 0x207fffffL))
        assertTrue(meetsTarget(hash, 0x207fffffL, MAX_UINT256))
        assertFailsMatching("proof-of-work limit") {
            meetsTarget(hash, 0x1d00ffffL, BigInteger.ZERO)
        }
    }

    @Test
    fun encodeBlockHeaderRejectsOutOfRangeFields() {
        val header = decodeBlockHeader(hexToBytes(MAINNET_665280_HEADER_HEX))

        assertFailsMatching("uint32") {
            encodeBlockHeader(header.copy(nonce = -1L))
        }
        assertFailsMatching("uint32") {
            encodeBlockHeader(header.copy(nonce = 1L shl 32))
        }

        val maxVersion = hexToBytes(MAINNET_665280_HEADER_HEX)
        maxVersion[0] = 0xff.toByte()
        maxVersion[1] = 0xff.toByte()
        maxVersion[2] = 0xff.toByte()
        maxVersion[3] = 0xff.toByte()
        val decoded = decodeBlockHeader(maxVersion)
        assertEquals(-1, decoded.version)
        assertEquals(bytesToHex(maxVersion), bytesToHex(encodeBlockHeader(decoded)))
    }

    @Test
    fun rejectsStrictCompactTargetEdges() {
        val cases = listOf(
            0x1d80ffffL to "negative",
            0x00000000L to "zero",
            0x23000001L to "overflow",
            0x04001234L to "non-canonical",
            0x1d010000L to "proof-of-work limit",
        )
        for ((bits, reason) in cases) {
            val error = assertFails { bitsToTarget(bits) }
            assertTrue(
                error.message?.contains(reason) == true,
                "bits=${bits.toString(16)} expected '$reason' in ${error.message}",
            )
        }
    }

    @Test
    fun canonicalCompactTargetsRoundTrip() {
        for (bits in listOf(0x01010000L, 0x03009234L, 0x170da8a1L, 0x1d00ffffL)) {
            assertEquals(bits, targetToCompact(bitsToTarget(bits)))
        }
    }
}
