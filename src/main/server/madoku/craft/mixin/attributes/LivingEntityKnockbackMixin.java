package madoku.craft.mixin.attributes;

import madoku.craft.java.core.enchant.EnchantBooksAPIManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityKnockbackMixin {
	@Unique
	private static final ThreadLocal<DamageSource> madokuCraft$activeDamageSource = new ThreadLocal<>();

	@Unique
	private double madokuCraft$configuredKnockbackVerticalY = Double.NaN;

	@Inject(
		method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
		at = @At("HEAD")
	)
	private void madokuCraft$rememberDamageSource(
		ServerLevel level,
		DamageSource source,
		float amount,
		CallbackInfoReturnable<Boolean> callbackInfo
	) {
		madokuCraft$activeDamageSource.set(source);
	}

	@Inject(
		method = "hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z",
		at = @At("RETURN")
	)
	private void madokuCraft$forgetDamageSource(
		ServerLevel level,
		DamageSource source,
		float amount,
		CallbackInfoReturnable<Boolean> callbackInfo
	) {
		madokuCraft$activeDamageSource.remove();
	}

	@Inject(
		method = "knockback(DDD)V",
		at = @At("HEAD")
	)
	private void madokuCraft$prepareHorizontalOnlyKnockback(
		double strength,
		double x,
		double z,
		CallbackInfo callbackInfo
	) {
		this.madokuCraft$configuredKnockbackVerticalY = Double.NaN;
		double configuredContribution = EnchantBooksAPIManager.resolveConfiguredKnockbackVerticalContribution(
		madokuCraft$activeDamageSource.get()
	);
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
		method = "knockback(DDD)V",
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

