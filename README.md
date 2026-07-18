## Overview

Madoku Craft: Attributes is a configurable attribute system.
You can modify attributes to customize the game experience to your needs.
You can customize most of these features in the CONFIG files.

## Dependencies:

- Fabric API
- Madoku Craft API

## Features:

Health:

- Minecraft's Regeneration was removed entirely.
- Health automatically regenerates by draining Hunger.

Hunger: 

- Saturation was removed entirely.
- Hunger now depletes from reaching certain goals, such as walking, breaking blocks, etc.
- Max Hunger was increased to 30 by default.

Armor: 

- Armor now reduces damage by a flat amount by default.
- Armor Toughness now reduces damage by a percent by default.
- Fall damage is now reduced by Armor and Armor Toughness.

Oxygen:

- Breathing time was increased to 30 seconds by default.

Luck: 

- Luck now determines when a player deals critical damage.
- Luck increases loot that MOBs drop.
- Luck can now trigger extra block drops when breaking blocks.

Status Effects:

- Poison was adjusted to drop a player's current Health to 25% by default.
- Water Breathing, Dolphin's Grace, Conduit Power, and Breath of Nautilus now only extend Oxygen values.
- Luck was adjusted to increase the player's Luck stat.