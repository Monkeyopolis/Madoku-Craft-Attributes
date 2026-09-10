package madoku.craft.mixin.attributes;

import madoku.craft.java.core.enchant.EnchantBooksAPIManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityKnockbackMixin {
	@Unique
	private double madokuCraft$configuredKnockbackVerticalY = Double.NaN;

	@Inject(
		method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
		at = @At("HEAD")
	)
	private void madokuCraft$prepareHorizontalOnlyKnockback(
		double strength,
		double x,
		double z,
		DamageSource source,
		float knockbackResistance,
		boolean wasDamaged,
		CallbackInfo callbackInfo
	) {
		this.madokuCraft$configuredKnockbackVerticalY = Double.NaN;
		double configuredContribution = EnchantBooksAPIManager.resolveConfiguredKnockbackVerticalContribution(source);
		if (configuredContribution <= 0.0D) return;

		LivingEntity target = (LivingEntity) (Object) this;
		double resistance = target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
		double resistanceMultiplier = Math.max(0.0D, 1.0D - resistance);
		if (resistanceMultiplier <= 0.0D || strength <= 0.0D) return;
		double unenchantedStrength = Math.max(
			0.0D,
			(strength - configuredContribution) * resistanceMultiplier
		);
		double currentY = target.getDeltaMovement().y;
		this.madokuCraft$configuredKnockbackVerticalY = target.onGround()
			? Math.min(0.4D, currentY / 2.0D + unenchantedStrength)
			: currentY;
	}

	@Inject(
		method = "knockback(DDDLnet/minecraft/world/damagesource/DamageSource;FZ)V",
		at = @At("RETURN")
	)
	private void madokuCraft$removeConfiguredVerticalIncrease(CallbackInfo callbackInfo) {
		double configuredY = this.madokuCraft$configuredKnockbackVerticalY;
		this.madokuCraft$configuredKnockbackVerticalY = Double.NaN;
		if (Double.isNaN(configuredY)) return;

		LivingEntity target = (LivingEntity) (Object) this;
		var movement = target.getDeltaMovement();
		target.setDeltaMovement(movement.x, configuredY, movement.z);
	}
}

