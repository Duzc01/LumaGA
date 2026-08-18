# Rust `logic` workspace

The shared backend LumaGA talks to over JNI. Building it produces
`liblogic.so`, which is checked in under `logic/src/main/jniLibs/<abi>/` and
loaded by `System.loadLibrary("logic")` in `logic/src/main/java/com/bugenzhao/mnga/Logic.kt`.

This is a vendored copy, not a submodule: the whole workspace lives here so the
app can be rebuilt from this repository alone.

## Layout

    Cargo.toml            workspace root; patches sled to the vendored copy below
    Cargo.lock            pins the full dependency graph
    rust-toolchain.toml   Rust 1.92 + the three Android targets
    protos/               .proto sources, shared by the Rust and Kotlin gencode
    logic/                workspace members (logic, service, cache, config, text,
                          protos, mock_gen)
    logic/sled/           vendored sled 0.34.6, patched for Android

`logic/protos/build.rs` reads `../../protos/*.proto` by hard-coded relative
path, so `protos/` has to stay exactly two levels above `logic/protos/`.

## Building

    ./build-jni-libs.sh        # liblogic.so for arm64-v8a, x86_64, x86
    ./gen-kotlin-protos.sh     # protobuf Java/Kotlin gencode for the :logic module

Gradle does not drive cargo. The `.so` files are committed, so after changing
anything here, run `build-jni-libs.sh` and commit the refreshed binaries with
the source change.

Prerequisites: `protoc`, `cargo-ndk`, an Android NDK, and `perl` + `make`. The
Android builds link OpenSSL statically and compile it from source, which is
most of the build time. `cargo build` also runs `cbindgen`, which rewrites
`logic/logic/bindings.h` in place, so the source tree must be writable.

The Android targets come from `rust-toolchain.toml` and rustup installs them on
the first cargo invocation.

## Provenance

Upstream is [BugenZhao/MNGA](https://github.com/BugenZhao/MNGA), the iOS app
LumaGA is a port of, taken at `4f75d76` (2026-08-05). MNGA carries no LICENSE
file and its README reserves all rights, so this copy is here by the same
arrangement as the rest of the port — see the note in the repository root
README before publishing anything derived from it.

`logic/sled/` is [sled](https://github.com/spacejam/sled) 0.34.6 at git rev
`95a883f`, dual-licensed MIT/Apache-2.0 (`LICENSE-MIT`, `LICENSE-APACHE`).
Everything sled's own `Cargo.toml` excludes from its package (`bindings`,
`art`, `benchmarks`, `examples`, `experiments`, `scripts`) plus its test suite
was left out; `src/` is complete.

### Local changes to MNGA

- `rust-toolchain.toml`: `targets` lists the three Android triples instead of
  the iOS ones. There is no iOS build here.
- `protos/*.proto`: added `option java_multiple_files = true`. Without it
  protoc emits one outer class per file instead of the per-message files the
  `:logic` module checks in. The Rust codegen ignores Java options.
- `Cargo.toml`: `[patch]` redirects sled's git dependency to `logic/sled`.
- `logic/service/Cargo.toml`: OpenSSL is built with the `vendored` feature on
  Android, because `native-tls` falls back to OpenSSL there.
- `logic/logic/src/android/lib.rs`: `#[unsafe(no_mangle)]` for edition 2024,
  and `may_init()` kept local to the module.
- `logic/service/src/{utils,error,topic}.rs`: `extract_kv_pairs` no longer
  unwraps text child nodes, which used to panic on forum responses and
  surfaced as a generic backend error. Covered by unit tests in `utils.rs`.

### Local changes to sled

Three files differ from upstream `95a883f`, all needed to run on Android:

- `src/ivec.rs`: the inline/remote representation check asserted on a byte of
  the pointer that only holds the expected value on big-endian, and Android's
  allocator hands back top-byte-tagged pointers that tripped it. Now
  `debug_assert`s the pointer's alignment bits instead. This was the crash
  behind "Once instance has previously been poisoned" on arm64.
- `src/lru.rs`: reborrow through `&*self.0` before field access in `Entry`'s
  `PartialEq`, `Borrow<u32>` and `Hash` impls.
- `src/node.rs`: widen `max_indexable_offset` to `u64` and shift `1u64`, so the
  shifts do not overflow at compile time on 32-bit `i686-linux-android`.
