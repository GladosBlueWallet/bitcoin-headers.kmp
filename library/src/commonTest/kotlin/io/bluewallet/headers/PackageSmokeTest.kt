package io.bluewallet.headers

import com.ionspin.kotlin.bignum.integer.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PackageSmokeTest {

    @Test
    fun checkpointAndSeedValidateOnMainnetParams() {
        assertEquals(665_280L, CHECKPOINT_HEIGHT)
        assertEquals(665_280L, MAINNET_HEADER_CONSENSUS.checkpoint.height)

        val seed = checkpointSeedRecord()
        val chain = validateHeaderChain(
            listOf(seed.toHeaderRecord()),
            MAINNET_HEADER_CONSENSUS,
            1_800_000_000L,
        )
        assertEquals(665_280L, chain.tipHeight)
        assertTrue(bitsToTarget(seed.header.bits) > BigInteger.ZERO)
    }
}
