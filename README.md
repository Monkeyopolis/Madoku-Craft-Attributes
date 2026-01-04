# Madoku Craft Health

- Madoku Craft Health replaces Minecraft's vanilla health system with a configurable health system that can be tinkered with.
- This is perfect for users that love to customize the game's difficulty.
- This can be done through the mod's config JSON file.

## Dependencies

- This mod requires Fabric API and Madoku Craft API in order to function properly.
- Madoku Craft API provides this mod's the JSON and data saving system.

## Implementation

- The mod's health system checks if a player is missing health.
- If they're missing health, the system checks the player's hunger.
- If the player has enough hunger, it converts it into health.
- The player can restore health by eating.