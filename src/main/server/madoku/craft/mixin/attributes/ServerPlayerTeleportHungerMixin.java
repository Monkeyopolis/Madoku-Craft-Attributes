package madoku.craft.mixin.attributes;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import madoku.craft.java.attributes.HungerAPIManager;

import java.util.Set;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerTeleportHungerMixin {
	@Inject(
		method = "teleport(Lnet/minecraft/world/level/portal/TeleportTransition;)Lnet/minecraft/server/level/ServerPlayer;",
		at = @At("TAIL")
	)
	private void madokuCraft$resetHungerMovementAfterTeleport(
		TeleportTransition transition,
		CallbackInfoReturnable<ServerPlayer> cir
	) {
		if (cir.getReturnValue() != null) {
			HungerAPIManager.handlePlayerTeleport((ServerPlayer) (Object) this);
		}
	}

	@Inject(method = "teleportTo(DDD)V", at = @At("TAIL"))
	private void madokuCraft$resetHungerMovementAfterAbsoluteTeleport(
		double x,
		double y,
		double z,
		CallbackInfo ci
	) {
		HungerAPIManager.handlePlayerTeleport((ServerPlayer) (Object) this);
	}

	@Inject(method = "teleportRelative(DDD)V", at = @At("TAIL"))
	private void madokuCraft$resetHungerMovementAfterRelativeTeleport(
		double x,
		double y,
		double z,
		CallbackInfo ci
	) {
		HungerAPIManager.handlePlayerTeleport((ServerPlayer) (Object) this);
	}

	@Inject(
		method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z",
		at = @At("TAIL")
	)
	private void madokuCraft$resetHungerMovementAfterLevelTeleport(
		ServerLevel level,
		double x,
		double y,
		double z,
		Set<Relative> relative,
		float yaw,
		float pitch,
		boolean asPassenger,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (cir.getReturnValue()) {
			HungerAPIManager.handlePlayerTeleport((ServerPlayer) (Object) this);
		}
	}

	@Inject(method = "snapTo(DDD)V", at = @At("TAIL"))
	private void madokuCraft$resetHungerMovementAfterPositionSnap(
		double x,
		double y,
		double z,
		CallbackInfo ci
	) {
		HungerAPIManager.handlePlayerTeleport((ServerPlayer) (Object) this);
	}
}
