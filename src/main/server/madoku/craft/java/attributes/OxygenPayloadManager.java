package madoku.craft.java.attributes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Synchronizes player-specific oxygen bonuses to the client HUD. */
public record OxygenPayloadManager(int bonusTicks) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<OxygenPayloadManager> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("madoku-craft", "oxygen_hud"));
	public static final StreamCodec<RegistryFriendlyByteBuf, OxygenPayloadManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT,
			OxygenPayloadManager::bonusTicks,
			OxygenPayloadManager::new
		);

	public OxygenPayloadManager {
		bonusTicks = Math.max(0, bonusTicks);
	}

	@Override
	public Type<OxygenPayloadManager> type() {
		return TYPE;
	}
}
