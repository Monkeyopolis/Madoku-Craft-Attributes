package madoku.craft.mixin;

import madoku.craft.hunger.MadokuHunger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.effect.SaturationMobEffect")
public abstract class SaturationMobEffectMixin {
	@Inject(method = "applyEffectTick", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$overrideSaturationTick(
		ServerLevel level,
		LivingEntity livingEntity,
		int amplifier,
		CallbackInfoReturnable<Boolean> cir
	) {
		if (livingEntity instanceof ServerPlayer player && MadokuHunger.applySaturationEffectTick(player, amplifier)) {
			cir.setReturnValue(true);
		}
	}
}
