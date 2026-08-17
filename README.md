# bitcoin-headers

Kotlin Multiplatform **Bitcoin header** PoW and chain-consensus validation.

Port of [`bitcoin-headers`](https://github.com/Overtorment/bitcoin-headers) for Android and iOS
(JVM and linuxX64 are extra targets). Public names and consensus behavior match the TypeScript
package so a wallet like Blueberry can call the same operations.

- **Common code** — header codec, SHA256d, compact targets, and chain rules live in `commonMain`
- **Checkpoint-anchored chains** — validate linkage, compact targets, retargets, median time past, future time, and cumulative work
- **Mainnet constants** — difficulty-boundary checkpoint at height `665280`

There is no BIP for this surface: it implements Bitcoin header consensus rules
used by SPV / Neutrino light clients.

## Gradle

```kotlin
implementation("org.bitcoin.kmp:bitcoin-headers:0.0.1")
```

Package: `org.bitcoin.headers`

## Usage

```kotlin
import org.bitcoin.headers.MAINNET_HEADER_CONSENSUS
import org.bitcoin.headers.validateHeaderChain
import org.bitcoin.headers.HeaderRecord

val chain = validateHeaderChain(
    headers,
    MAINNET_HEADER_CONSENSUS,
    currentTimeSeconds,
)
println("${chain.tipHeight} ${chain.tipHashDisplay} ${chain.chainWork}")
```

Heights, timestamps, and compact `nBits` are `Long`. Work / targets use
[`com.ionspin.kotlin.bignum.integer.BigInteger`](https://github.com/ionspin/kotlin-multiplatform-bignum)
in place of JavaScript `bigint`.

## Tests

```bash
./gradlew :bitcoin-headers:jvmTest :bitcoin-headers:linuxX64Test
```

On macOS, iOS simulator tests:

```bash
./gradlew :bitcoin-headers:iosSimulatorArm64Test
```

Coverage includes:

- Synthetic easy-difficulty consensus fixtures (retarget, MTP, PoW, linkage)
- Verified mainnet checkpoint PoW / serialization checks
- Real mainnet header vectors from a Helix2 synced store (`testdata/`)
