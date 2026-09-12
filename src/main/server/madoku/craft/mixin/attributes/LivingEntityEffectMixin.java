package madoku.craft.mixin.attributes;

import madoku.craft.java.attributes.HealthAPIManager;
import madoku.craft.java.attributes.LuckAPIManager;
import net.minecraft.server.level.ServerPlayer;
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
			HealthAPIManager.handlePlayerEffectsChanged(player);
			LuckAPIManager.handlePlayerEffectsChanged(player);
		}
	}

	@Inject(method = "onEffectUpdated", at = @At("TAIL"))
	private void madokuCraft$onEffectUpdated(MobEffectInstance effect, boolean reapplyEffect, Entity source, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			HealthAPIManager.handlePlayerEffectsChanged(player);
			LuckAPIManager.handlePlayerEffectsChanged(player);
		}
	}

	@Inject(method = "onEffectsRemoved", at = @At("TAIL"))
	private void madokuCraft$onEffectsRemoved(Collection<MobEffectInstance> effects, CallbackInfo ci) {
		if ((Object) this instanceof ServerPlayer player) {
			HealthAPIManager.handlePlayerEffectsChanged(player);
			LuckAPIManager.handlePlayerEffectsChanged(player);
		}
	}
}

