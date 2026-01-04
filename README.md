# Madoku Craft Health

Custom health and hunger regeneration system for the Madoku Craft modpack. When enabled this feature replaces the vanilla `canFoodHeal`/starvation logic with a pending-surplus cycle that drains hunger only when players stay above configured thresholds, and then applies healing via pending points before overflowing any excess into surplus stockpiles.

## Features

- Overrides the vanilla regen system; the mod toggles itself through `enableFeature` in the JSON config.
- Maintains per-player pending health, surplus health, and pending timers that respect the configured health and hunger thresholds (see `custom.health.md` for the intended behavior).
- Prevents starvation damage when food level reaches zero and allows fine-grained tuning of timers, multipliers, and caps.
- Surplus health persists per player, clears after a timeout, and re-enters the pending→current loop whenever there is capacity.

## Configuration

- Edit `run/config/madoku-craft-api/madoku_craft_health.json` (created at runtime) to control:
  - `pendingHealthTimer` and `hungerDepletionTimer` (tick intervals for pending conversion and hunger drain).
  - `hungerDepletionThreshold` and `maximumHealthSurplusPoints` (percent thresholds that gate automatic regen).
  - `hungerHealthReductionThreshold` and `maximumHealthReduction` (lower max health when starvation looms).
  - All double values snap to the nearest 0.125 increment, and the JSON is automatically persisted.

## Development

- Build with Gradle from the repository root: `./gradlew build`.
- The mod writes Madoku data under `run/madoku-data/madoku_craft_health`. Deleting that file resets tracked pending/surplus states.
- Run the Fabric client/server via `./gradlew runClient` or `./gradlew runServer` while testing the health loop.

## References

- The reference instructions live in `References/Health/custom.health.md`, which documents the desired pending/surplus behavior, caps, and hunger interactions in detail.
