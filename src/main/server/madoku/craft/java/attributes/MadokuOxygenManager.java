package madoku.craft.java.attributes;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import madoku.craft.java.core.sync.SyncPlayerAPIManager;

/** Provides the configured vanilla oxygen maximum for player air-supply hooks and HUDs. */
public final class MadokuOxygenManager {
	private static volatile OxygenConfigManager.Settings settings = OxygenConfigManager.Settings.defaults();
	private static volatile Boolean clientSynchronizedEnabled;
	private static volatile Integer clientSynchronizedMaximum;
	private static volatile int clientSynchronizedPlayerBonusTicks;

	private MadokuOxygenManager() {
	}

	public static void initialize() {
		settings = OxygenConfigManager.loadSettings(AttributesConfigManager.loadSettings().enabled);
	}

	public static boolean isEnabled() {
		Boolean synchronizedEnabled = clientSynchronizedEnabled;
		return synchronizedEnabled == null ? settings.oxygen.enabled : synchronizedEnabled;
	}

	public static int getMaximumOxygenTicksForEntity(LivingEntity entity) {
		Integer synchronizedMaximum = clientSynchronizedMaximum;
		int maximum = Math.max(1, synchronizedMaximum == null ? settings.oxygen.maxOxygenTicks : synchronizedMaximum);
		if (entity instanceof ServerPlayer player) {
			maximum += OxygenAPIManager.getFeatureOxygenBonusTicks(player);
		} else if (entity instanceof Player) {
			maximum += clientSynchronizedPlayerBonusTicks;
		}
		return Math.max(1, maximum);
	}

	public static void handleMaximumOxygenChanged(ServerPlayer player) {
		if (player == null) return;
		SyncPlayerAPIManager.send(player, new OxygenPayloadManager(OxygenAPIManager.getFeatureOxygenBonusTicks(player)));
	}

	public static void applyClientSynchronizedSettings(boolean enabled, int maximum) {
		clientSynchronizedEnabled = enabled;
		clientSynchronizedMaximum = Math.max(1, maximum);
	}

	public static void applyClientSynchronizedPlayerBonus(int bonusTicks) {
		clientSynchronizedPlayerBonusTicks = Math.max(0, bonusTicks);
	}

	public static void resetClientSynchronizedSettings() {
		clientSynchronizedEnabled = null;
		clientSynchronizedMaximum = null;
		clientSynchronizedPlayerBonusTicks = 0;
	}
}
