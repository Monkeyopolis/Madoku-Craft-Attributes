package madoku.craft.mixin;

import madoku.craft.oxygen.MadokuOxygen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDrowningDamageMixin {
	@Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$blockVanillaDrownDamageWhenUsingMadokuOxygen(
		DamageSource source,
		float amount,
		CallbackInfoReturnable<Boolean> cir
	) {
		ServerPlayer player = (ServerPlayer) (Object) this;
		if (MadokuOxygen.shouldSuppressVanillaDrowningDamage(player, source)) {
			cir.setReturnValue(false);
		}
	}
}
