package io.bluewallet.headers

/** Durable header record (hex-encoded) used by validators and light-client stores. */
public data class HeaderRecord(
    val height: Long,
    val hashDisplay: String,
    val hashInternalHex: String,
    val headerHex: String,
)
