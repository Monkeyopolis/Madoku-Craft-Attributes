package madoku.craft.attributes.mixin;

import madoku.craft.attributes.health.MadokuHealthManager;
import madoku.craft.attributes.luck.MadokuLuckManager;
import madoku.craft.attributes.oxygen.MadokuOxygenManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectMixin {
	@Inject(method = "onEffectAdded", at = @At("TAIL"))
	private void madokuCraft$onEffectAdded(MobEffectInstance effect, Entity source, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			MadokuHealthManager.handlePlayerEffectsChanged(player);
			MadokuLuckManager.handlePlayerEffectsChanged(player);
			if (madokuCraft$isOxygenEffect(effect)) {
				MadokuOxygenManager.handlePlayerEffectsChanged(player);
			}
		}
	}

	@Inject(method = "onEffectUpdated", at = @At("TAIL"))
	private void madokuCraft$onEffectUpdated(MobEffectInstance effect, boolean reapplyEffect, Entity source, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			MadokuHealthManager.handlePlayerEffectsChanged(player);
			MadokuLuckManager.handlePlayerEffectsChanged(player);
			if (madokuCraft$isOxygenEffect(effect)) {
				MadokuOxygenManager.handlePlayerEffectsChanged(player);
			}
		}
	}

	@Inject(method = "onEffectsRemoved", at = @At("TAIL"))
	private void madokuCraft$onEffectsRemoved(Collection<MobEffectInstance> effects, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			MadokuHealthManager.handlePlayerEffectsChanged(player);
			MadokuLuckManager.handlePlayerEffectsChanged(player);
			if (madokuCraft$hasRemovedOxygenEffect(effects)) {
				MadokuOxygenManager.handlePlayerEffectsChanged(player);
			}
		}
	}

	private boolean madokuCraft$isOxygenEffect(MobEffectInstance effect) {
		if (effect == null || effect.getEffect() == null) {
			return false;
		}
		MobEffect mobEffect = effect.getEffect().value();
		return mobEffect != null && MadokuOxygenManager.shouldOverrideVanillaEffect((LivingEntity) (Object) this, mobEffect);
	}

	private boolean madokuCraft$hasRemovedOxygenEffect(Collection<MobEffectInstance> effects) {
		if (effects == null || effects.isEmpty()) {
			return false;
		}

		for (MobEffectInstance effect : effects) {
			if (madokuCraft$isOxygenEffect(effect)) {
				return true;
			}
		}
		return false;
	}
}
