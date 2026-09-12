package madoku.craft.java.attributes;

import net.minecraft.world.entity.player.Player;

/** Built-in provider for the Madoku experience attribute subsystem. */
public final class MadokuExperienceProvider implements ExperienceProvider {
	@Override public void initialize() { MadokuExperienceManager.initialize(); }
	@Override public boolean isEnabled() { return MadokuExperienceManager.isEnabled(); }
	@Override public int getMaxLevel() { return MadokuExperienceManager.getMaxLevel(); }
	@Override public int getXpNeededForNextLevel(Player player) { return MadokuExperienceManager.getXpNeededForNextLevel(player); }
	@Override public int getXpNeededForLevel(int level) { return MadokuExperienceManager.getXpNeededForLevel(level); }
	@Override public void applyDeathPenalty(Player player) { MadokuExperienceManager.applyDeathPenalty(player); }
	@Override public void clampPlayerLevel(Player player) { MadokuExperienceManager.clampPlayerLevel(player); }
}
