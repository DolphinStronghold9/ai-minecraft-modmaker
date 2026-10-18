# Changelog


## 1.11.4 — 3 November 2026

A maintenance release.

### Fixes

- Prompt history survives a restart again.

## 1.11.3 · 2026-11-01

- Ghost preview follows the crosshair on steep angles.
- Undo keeps entity data on nested structures.
- Terminal output redraws cleanly on resize.
- Local model starts when the API key field is empty.

## 1.11.2 (2026-10-28)

First release.

- `mortar` terminal agent for Windows.
- Fabric companion for Java Edition 26.1–26.3. It reads the area around you and draws the ghost.
- Paper plugin for servers you run. The ghost is shown only to you.
- Minecraft on Rust (Horizon) through its built-in Agent Control. No mod needed.
- Ghost preview with `/move` and `/rotate` before placing.
- Snapshot and undo journal for every turn: `/undo` and `/redo`.
- Placement in batches under the `max_block_modifications` limit.
- Datapacks: functions, advancements, recipes and loot tables. Mortar reloads, reads the log and fixes errors.
- `/materials` and `/export` to `.litematic`, `.schem` and `.nbt`.
- Models: Claude, OpenAI, Gemini and local models through Ollama.
