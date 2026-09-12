package madoku.craft.mixin.attributes;

import madoku.craft.java.attributes.HealthAPIManager;
import madoku.craft.java.attributes.LuckAPIManager;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collection;

@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectAttributeOverrideMixin {
	@Redirect(
		method = "onEffectAdded",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/effect/MobEffect;addAttributeModifiers(Lnet/minecraft/world/entity/ai/attributes/AttributeMap;I)V"
		)
	)
	private void madokuCraft$overrideOnEffectAddedAttributes(
		MobEffect effect,
		AttributeMap attributes,
		int amplifier,
		MobEffectInstance effectInstance,
		Entity source
	) {
		if (
			HealthAPIManager.shouldOverrideVanillaEffectAttributes((LivingEntity) (Object) this, effect)
				|| LuckAPIManager.shouldOverrideVanillaLuckEffect((LivingEntity) (Object) this, effect)
		) {
			return;
		}
		effect.addAttributeModifiers(attributes, amplifier);
	}

	@Redirect(
		method = "onEffectUpdated",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/effect/MobEffect;removeAttributeModifiers(Lnet/minecraft/world/entity/ai/attributes/AttributeMap;)V"
		)
	)
	private void madokuCraft$overrideOnEffectUpdatedRemoveAttributes(
		MobEffect effect,
		AttributeMap attributes,
		MobEffectInstance effectInstance,
		boolean reapplyEffect,
		Entity source
	) {
		if (
			HealthAPIManager.shouldOverrideVanillaEffectAttributes((LivingEntity) (Object) this, effect)
				|| LuckAPIManager.shouldOverrideVanillaLuckEffect((LivingEntity) (Object) this, effect)
		) {
			return;
		}
		effect.removeAttributeModifiers(attributes);
	}

	@Redirect(
		method = "onEffectUpdated",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/effect/MobEffect;addAttributeModifiers(Lnet/minecraft/world/entity/ai/attributes/AttributeMap;I)V"
		)
	)
	private void madokuCraft$overrideOnEffectUpdatedAddAttributes(
		MobEffect effect,
		AttributeMap attributes,
		int amplifier,
		MobEffectInstance effectInstance,
		boolean reapplyEffect,
		Entity source
	) {
		if (
			HealthAPIManager.shouldOverrideVanillaEffectAttributes((LivingEntity) (Object) this, effect)
				|| LuckAPIManager.shouldOverrideVanillaLuckEffect((LivingEntity) (Object) this, effect)
		) {
			return;
		}
		effect.addAttributeModifiers(attributes, amplifier);
	}

	@Redirect(
		method = "onEffectsRemoved",
		at = @At(
			value = "INVOKE",
			target = "Lnet/minecraft/world/effect/MobEffect;removeAttributeModifiers(Lnet/minecraft/world/entity/ai/attributes/AttributeMap;)V"
		)
	)
	private void madokuCraft$overrideOnEffectsRemovedAttributes(
		MobEffect effect,
		AttributeMap attributes,
		Collection<MobEffectInstance> effects
	) {
		if (
			HealthAPIManager.shouldOverrideVanillaEffectAttributes((LivingEntity) (Object) this, effect)
				|| LuckAPIManager.shouldOverrideVanillaLuckEffect((LivingEntity) (Object) this, effect)
		) {
			return;
		}
		effect.removeAttributeModifiers(attributes);
	}
}

