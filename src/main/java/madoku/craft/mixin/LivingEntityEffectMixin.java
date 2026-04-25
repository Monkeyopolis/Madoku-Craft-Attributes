package madoku.craft.mixin;

import madoku.craft.attributes.MadokuHealth;
import madoku.craft.luck.MadokuLuck;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectMixin {
	@Inject(method = "onEffectAdded", at = @At("TAIL"))
	private void madokuCraft$onEffectAdded(MobEffectInstance effect, Entity source, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			MadokuHealth.handlePlayerEffectsChanged(player);
			MadokuLuck.handlePlayerEffectsChanged(player);
		}
	}

	@Inject(method = "onEffectUpdated", at = @At("TAIL"))
	private void madokuCraft$onEffectUpdated(MobEffectInstance effect, boolean reapplyEffect, Entity source, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			MadokuHealth.handlePlayerEffectsChanged(player);
			MadokuLuck.handlePlayerEffectsChanged(player);
		}
	}

	@Inject(method = "onEffectRemoved", at = @At("TAIL"))
	private void madokuCraft$onEffectRemoved(MobEffectInstance effect, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			MadokuHealth.handlePlayerEffectsChanged(player);
			MadokuLuck.handlePlayerEffectsChanged(player);
		}
	}
}
