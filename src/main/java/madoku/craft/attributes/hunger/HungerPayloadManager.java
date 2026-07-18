package madoku.craft.attributes.hunger;

import madoku.craft.attributes.MadokuCraftAttributes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HungerPayloadManager(int current, int pending, int max) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<HungerPayloadManager> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraftAttributes.MOD_ID, "hunger_hud"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HungerPayloadManager> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT,
			HungerPayloadManager::current,
			ByteBufCodecs.VAR_INT,
			HungerPayloadManager::pending,
			ByteBufCodecs.VAR_INT,
			HungerPayloadManager::max,
			HungerPayloadManager::new
		);

	@Override
	public Type<HungerPayloadManager> type() {
		return TYPE;
	}
}
