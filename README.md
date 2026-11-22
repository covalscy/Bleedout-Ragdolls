# Bleedout Ragdolls (Fork of Sable Ragdolls)

Modified fork of [Sable Ragdolls](https://github.com/Leo-T22/sable-player-ragdoll) by **Leonardoinc22**, customized by **covalscy** for [BleedOut](https://github.com/covalscy/Bleedout) gameplay (Minecraft 1.21.1, NeoForge).

## Requirements
- **Minecraft** `1.21.1`
- **NeoForge** `21.1.219+`
- **Sable** `1.1.0+` (required physics engine)
- Optional integrations: **BleedOut**, **Jade**, **Curios**, **Accessories**

## Changes from Upstream
- Renamed mod ID to `bleedout_ragdolls` and Maven coordinates to `com.covalscy.bleedoutragdolls:bleedout_ragdolls-1.21.1` so this fork has a distinct identity and does not impersonate upstream `sable_player_ragdoll`.
- Removed the manual self-ragdoll keybind (`H`) and its `allowManualTrigger` config/packet.
- Added anatomical angular joint limits for head, arms, and legs; tuned physics block properties (`restitution = 0.0`, `friction = 0.45`, per-part mass/inertia overrides) and part collision shapes; disabled joint-pair self-collision by default (`partSelfCollision = false`); smoothed grab-dragging physics.
- Removed impact smoke particles and hard-impact feedback/knockback bursts.
- Enabled toggling between third-person and first-person ragdoll camera via `F5`, preserving first-person mode on ragdoll entry, restoring world look orientation on exit, and preventing first-person camera clipping into ground/wall blocks.
- Disabled verbose lifecycle server logging by default (`debugLogging = false`).

## License & Attribution

Licensed under the [Apache License, Version 2.0](LICENSE).

Original work: **Sable Ragdolls** — Copyright 2026 **Leonardoinc22** ([upstream repository](https://github.com/Leo-T22/sable-player-ragdoll)).
See [NOTICE](NOTICE) for full copyright, attribution, and derivative work notices.
