package madoku.craft.attributes.mixin;

import madoku.craft.attributes.oxygen.MadokuOxygenManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDrowningDamageMixin {
	private static final ThreadLocal<Boolean> MADOKU_OXYGEN_INTERNAL_DROWN_CALL =
		ThreadLocal.withInitial(() -> false);

	@Inject(method = "hurtServer", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$adjustVanillaDrownDamageAmount(
		ServerLevel level,
		DamageSource source,
		float amount,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (Boolean.TRUE.equals(MADOKU_OXYGEN_INTERNAL_DROWN_CALL.get())) {
			return;
		}

		ServerPlayer player = (ServerPlayer) (Object) this;
		if (!MadokuOxygenManager.shouldAdjustSuffocatingPenalty(player, source)) {
			return;
		}

		float adjustedDamage = MadokuOxygenManager.resolveSuffocatingPenaltyDamage(player);
		if (adjustedDamage <= 0.0f) {
			cir.setReturnValue(false);
			return;
		}

		MADOKU_OXYGEN_INTERNAL_DROWN_CALL.set(true);
		try {
			cir.setReturnValue(player.hurtServer(level, source, adjustedDamage));
		} finally {
			MADOKU_OXYGEN_INTERNAL_DROWN_CALL.set(false);
		}
	}
}
