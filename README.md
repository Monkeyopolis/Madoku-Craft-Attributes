# Madoku Craft Health

Madoku Craft Health is a configurable system that can be tinkered with.
This is perfect for users that love to customize the game's difficulty.
This can be done through the MOD's config JSON file.

## Dependencies

This mod requires Fabric API and Madoku Craft API in order to function properly.
This MOD uses Madoku Craft API's JSON, DATA, Death, TICK, and Debug systems.

## Implementation

The MOD's health system checks if a player is missing health.
If they're missing health, the system checks the player's hunger.
If the player has enough hunger, it converts it into health.
If the player loses enough hunger, it reduces the player's health.
The player can restore health by eating.
