package org.bitcoin.headers

import org.kotlincrypto.hash.sha2.SHA256

/** SHA256d: `SHA256(SHA256(data))`, as used throughout Bitcoin consensus code. */
public fun sha256d(data: ByteArray): ByteArray {
    val sha = SHA256()
    return sha.digest(sha.digest(data))
}
