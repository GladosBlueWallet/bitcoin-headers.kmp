package io.bluewallet.headers

public fun equalBytes(a: ByteArray, b: ByteArray): Boolean {
    if (a.size != b.size) return false
    var diff = 0
    for (i in a.indices) {
        diff = diff or (a[i].toInt() xor b[i].toInt())
    }
    return diff == 0
}

public fun hexToBytes(hex: String): ByteArray {
    if (hex.length % 2 != 0) throw IllegalArgumentException("odd hex length: ${hex.length}")
    val out = ByteArray(hex.length / 2)
    for (i in out.indices) {
        val hi = nibble(hex[i * 2].code)
        val lo = nibble(hex[i * 2 + 1].code)
        if (hi < 0 || lo < 0) throw IllegalArgumentException("invalid hex string")
        out[i] = ((hi shl 4) or lo).toByte()
    }
    return out
}

private val HEX_NIBBLE = ByteArray(128) { -1 }.also { table ->
    for (i in 0..9) table[48 + i] = i.toByte()
    for (i in 0..5) {
        table[65 + i] = (10 + i).toByte()
        table[97 + i] = (10 + i).toByte()
    }
}

private fun nibble(code: Int): Int {
    return if (code < 128) HEX_NIBBLE[code].toInt() else -1
}

private val HEX_DIGITS = charArrayOf(
    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f',
)

public fun bytesToHex(bytes: ByteArray): String {
    val chars = CharArray(bytes.size * 2)
    for (i in bytes.indices) {
        val v = bytes[i].toInt() and 0xff
        chars[i * 2] = HEX_DIGITS[v ushr 4]
        chars[i * 2 + 1] = HEX_DIGITS[v and 0x0f]
    }
    return chars.concatToString()
}

/** Display-order hex: internal (LE) bytes printed MSB first. */
internal fun bytesToHexReversed(bytes: ByteArray): String {
    val chars = CharArray(bytes.size * 2)
    for (i in bytes.indices) {
        val v = bytes[bytes.size - 1 - i].toInt() and 0xff
        chars[i * 2] = HEX_DIGITS[v ushr 4]
        chars[i * 2 + 1] = HEX_DIGITS[v and 0x0f]
    }
    return chars.concatToString()
}

public fun bytesFromHex(value: String, byteLength: Int, label: String): ByteArray {
    if (value.length != byteLength * 2 || !LOWERCASE_HEX.matches(value)) {
        throw IllegalArgumentException("$label must be $byteLength bytes of lowercase hex")
    }
    return hexToBytes(value)
}

private val LOWERCASE_HEX = Regex("^[0-9a-f]+$")
