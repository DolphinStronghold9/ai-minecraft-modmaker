# Contributing

Thanks for helping Mortar.

## Reporting a bug

Open an issue with the version, the OS and the exact steps. If the problem is in a
verdict, a build or a trace, attach the smallest input that reproduces it.

## Code

```bash
cargo fmt --all
cargo clippy --workspace -- -D warnings
cargo test --workspace
```

Keep user-facing text short and plain: one sentence per verdict, no jargon.
