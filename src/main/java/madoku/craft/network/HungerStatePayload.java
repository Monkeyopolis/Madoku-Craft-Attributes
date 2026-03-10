package madoku.craft.network;

import madoku.craft.MadokuCraftAttributes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record HungerStatePayload(int current, int pending, int max) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<HungerStatePayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(MadokuCraftAttributes.MOD_ID, "hunger_state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, HungerStatePayload> CODEC =
		StreamCodec.composite(
			ByteBufCodecs.VAR_INT,
			HungerStatePayload::current,
			ByteBufCodecs.VAR_INT,
			HungerStatePayload::pending,
			ByteBufCodecs.VAR_INT,
			HungerStatePayload::max,
			HungerStatePayload::new
		);

	@Override
	public Type<HungerStatePayload> type() {
		return TYPE;
	}
}