package madoku.craft.java.attributes;

import madoku.craft.java.core.attribute.PlayerAttributeProvider;
import madoku.craft.java.core.attribute.PlayerAttributeStat;
import net.minecraft.server.level.ServerPlayer;

/** Supplies Attributes-owned contributions to the shared player-stat resolver. */
public final class MadokuAttributesPlayerAttributeProvider implements PlayerAttributeProvider {
	@Override
	public String id() {
		return "attributes";
	}

	@Override
	public double bonus(ServerPlayer player, PlayerAttributeStat stat) {
		return stat == PlayerAttributeStat.LUCK
			? MadokuLuckManager.playerAttributeBonus(player)
			: 0.0D;
	}
}
