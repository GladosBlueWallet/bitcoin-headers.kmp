package io.bluewallet.headers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MainnetVectorsTest {

    @Test
    fun validates128ContiguousSyncedMainnetHeadersWithCumulativeWork() {
        val fixture = MainnetHeadersFixture
        val chain = validateHeaderChain(
            fixture.headers,
            MAINNET_HEADER_CONSENSUS,
            1_800_000_000L,
        )

        assertEquals(fixture.toHeight, chain.tipHeight)
        assertEquals(fixture.tipHashDisplay, chain.tipHashDisplay)
        assertEquals(fixture.headers.size, chain.headers.size)
        assertEquals(
            fixture.headers.size.toLong(),
            chain.tipHeight - fixture.fromHeight + 1L,
        )

        var expectedWork = com.ionspin.kotlin.bignum.integer.BigInteger.ZERO
        for (i in fixture.headers.indices) {
            val record = fixture.headers[i]
            val header = decodeBlockHeader(hexToBytes(record.headerHex))
            val entry = chain.entriesByHeight[record.height]
            assertNotNull(entry)
            assertEquals(record.hashDisplay, entry.record.hashDisplay)
            assertEquals(header.bits, entry.header.bits)
            assertEquals(0x170da8a1L, header.bits)
            if (i > 0) {
                assertEquals(fixture.headers[i - 1].height + 1L, record.height)
                assertTrue(
                    equalBytes(
                        entry.header.previousBlockHash,
                        chain.entriesByHeight.getValue(record.height - 1L).hashInternal,
                    ),
                )
            }
            expectedWork += headerWork(
                decodeCompactTarget(header.bits, MAINNET_HEADER_CONSENSUS.powLimit),
            )
            assertEquals(expectedWork, chain.cumulativeWorkByHeight[record.height])
        }
        assertEquals(expectedWork, chain.chainWork)
    }
}
