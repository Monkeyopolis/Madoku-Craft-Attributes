package madoku.craft.mixin.attributes;

import madoku.craft.java.core.enchant.BooksConfigAPIManager;
import madoku.craft.java.core.enchant.EnchantBooksAPIManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.EnchantmentTarget;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.mutable.MutableFloat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Enchantment.class)
public abstract class EnchantmentMaximumLevelMixin {
	/** Tracks Soul Speed's location-change effect while its nested effects are executing. */
	@Inject(
		method = "runLocationChangedEffects(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/enchantment/EnchantedItemInUse;Lnet/minecraft/world/entity/LivingEntity;)V",
		at = @At("HEAD")
	)
	private void madokuCraft$beginSoulSpeedLocationChangedEffects(
		ServerLevel serverLevel,
		int level,
		EnchantedItemInUse itemSource,
		net.minecraft.world.entity.LivingEntity entity,
		CallbackInfo callbackInfo
	) {
		EnchantBooksAPIManager.beginSoulSpeedLocationChangedEffects((Enchantment) (Object) this);
	}

	@Inject(
		method = "runLocationChangedEffects(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/enchantment/EnchantedItemInUse;Lnet/minecraft/world/entity/LivingEntity;)V",
		at = @At("RETURN")
	)
	private void madokuCraft$endSoulSpeedLocationChangedEffects(
		ServerLevel serverLevel,
		int level,
		EnchantedItemInUse itemSource,
		net.minecraft.world.entity.LivingEntity entity,
		CallbackInfo callbackInfo
	) {
		EnchantBooksAPIManager.endSoulSpeedLocationChangedEffects();
	}

	/** Prevents configured Bane of Arthropods from retaining vanilla bonus damage. */
	@Inject(
		method = "modifyDamage(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lorg/apache/commons/lang3/mutable/MutableFloat;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$replaceBaneOfArthropodsDamage(
		ServerLevel serverLevel,
		int level,
		ItemStack stack,
		Entity entity,
		DamageSource source,
		MutableFloat damage,
		CallbackInfo callbackInfo
	) {
		if (BooksConfigAPIManager.shouldOverrideBaneOfArthropods((Enchantment) (Object) this)) {
			callbackInfo.cancel();
		}
	}

	/** Replaces vanilla Impaling's aquatic-only bonus with the configured water-or-rain rule. */
	@Inject(
		method = "modifyDamage(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lorg/apache/commons/lang3/mutable/MutableFloat;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$replaceImpalingDamage(
		ServerLevel serverLevel,
		int level,
		ItemStack stack,
		Entity entity,
		DamageSource source,
		MutableFloat damage,
		CallbackInfo callbackInfo
	) {
		if (EnchantBooksAPIManager.applyConfiguredImpalingDamage(
			(Enchantment) (Object) this,
			level,
			stack,
			entity,
			damage
		)) {
			callbackInfo.cancel();
		}
	}

	/** Replaces vanilla Sharpness damage with the configured additive damage. */
	@Inject(
		method = "modifyDamage(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lorg/apache/commons/lang3/mutable/MutableFloat;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$replaceSharpnessDamage(
		ServerLevel serverLevel,
		int level,
		ItemStack stack,
		Entity entity,
		DamageSource source,
		MutableFloat damage,
		CallbackInfo callbackInfo
	) {
		if (EnchantBooksAPIManager.applyConfiguredSharpnessDamage(
			(Enchantment) (Object) this,
			level,
			stack,
			entity,
			source,
			damage
		)) {
			callbackInfo.cancel();
		}
	}

	/** Cancels vanilla Smite damage and replaces its undead-only condition with configured effects. */
	@Inject(
		method = "modifyDamage(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lorg/apache/commons/lang3/mutable/MutableFloat;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$replaceSmiteDamage(
		ServerLevel serverLevel,
		int level,
		ItemStack stack,
		Entity entity,
		DamageSource source,
		MutableFloat damage,
		CallbackInfo callbackInfo
	) {
		if (EnchantBooksAPIManager.cancelConfiguredSmiteDamage(
			(Enchantment) (Object) this,
			level,
			stack,
			entity,
			source,
			damage
		)) {
			callbackInfo.cancel();
		}
	}

	/** Replaces configured Thorns' vanilla chance and effect while retaining one roll per armor piece. */
	@Inject(
		method = "doPostAttack(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/enchantment/EnchantedItemInUse;Lnet/minecraft/world/item/enchantment/EnchantmentTarget;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$replaceThornsPostAttack(
		ServerLevel serverLevel,
		int level,
		EnchantedItemInUse itemSource,
		EnchantmentTarget target,
		Entity entity,
		DamageSource source,
		CallbackInfo callbackInfo
	) {
		if (EnchantBooksAPIManager.applyConfiguredThornsPostAttack(
			serverLevel,
			level,
			itemSource,
			source == null ? null : source.getEntity(),
			(Enchantment) (Object) this,
			source
		)) {
			callbackInfo.cancel();
		}
	}

	/** Prevents configured Infinity from retaining vanilla's unconditional arrow exemption. */
	@Inject(
		method = "modifyAmmoCount(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/ItemStack;Lorg/apache/commons/lang3/mutable/MutableFloat;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$replaceInfinityAmmoUse(
		ServerLevel serverLevel,
		int level,
		ItemStack stack,
		MutableFloat amount,
		CallbackInfo callbackInfo
	) {
		if (EnchantBooksAPIManager.shouldCancelVanillaInfinity(
			(Enchantment) (Object) this,
			level
		)) {
			callbackInfo.cancel();
		}
	}

	/** Replaces configured Mending's XP repair with its chance to prevent durability loss. */
	@Inject(
		method = "modifyDurabilityChange(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/ItemStack;Lorg/apache/commons/lang3/mutable/MutableFloat;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$replaceMendingDurabilityChange(
		ServerLevel serverLevel,
		int level,
		ItemStack stack,
		MutableFloat durabilityChange,
		CallbackInfo callbackInfo
	) {
		if (EnchantBooksAPIManager.applyConfiguredMendingDurabilityProtection(
			(Enchantment) (Object) this,
			level,
			stack,
			durabilityChange,
			serverLevel
		)) {
			callbackInfo.cancel();
		}
	}

	/** Prevents configured Unbreaking from retaining vanilla durability-loss reduction. */
	@Inject(
		method = "modifyDurabilityChange(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/ItemStack;Lorg/apache/commons/lang3/mutable/MutableFloat;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$replaceUnbreakingDurabilityChange(
		ServerLevel serverLevel,
		int level,
		ItemStack stack,
		MutableFloat durabilityChange,
		CallbackInfo callbackInfo
	) {
		if (BooksConfigAPIManager.shouldOverrideUnbreaking((Enchantment) (Object) this)) {
			callbackInfo.cancel();
		}
	}

	/** Replaces vanilla Knockback's value while retaining horizontal-only behavior. */
	@Inject(
		method = "modifyKnockback(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lorg/apache/commons/lang3/mutable/MutableFloat;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$replaceKnockback(
		ServerLevel serverLevel,
		int level,
		ItemStack stack,
		Entity entity,
		DamageSource source,
		MutableFloat knockback,
		CallbackInfo callbackInfo
	) {
		if (EnchantBooksAPIManager.applyConfiguredKnockback(
			(Enchantment) (Object) this,
			level,
			stack,
			entity,
			source,
			knockback
		)) {
			callbackInfo.cancel();
		}
	}

	/** Replaces vanilla Punch's arrow knockback value while retaining its arrow-only condition. */
	@Inject(
		method = "modifyKnockback(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lorg/apache/commons/lang3/mutable/MutableFloat;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$replacePunch(
		ServerLevel serverLevel,
		int level,
		ItemStack stack,
		Entity entity,
		DamageSource source,
		MutableFloat knockback,
		CallbackInfo callbackInfo
	) {
		if (EnchantBooksAPIManager.applyConfiguredPunch(
			(Enchantment) (Object) this,
			level,
			stack,
			entity,
			source,
			knockback
		)) {
			callbackInfo.cancel();
		}
	}

	/** Prevents configured Bane of Arthropods from retaining vanilla target restrictions and slowness. */
	@Inject(
		method = "doPostAttack(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/enchantment/EnchantedItemInUse;Lnet/minecraft/world/item/enchantment/EnchantmentTarget;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$replaceBaneOfArthropodsPostAttack(
		ServerLevel serverLevel,
		int level,
		EnchantedItemInUse itemSource,
		EnchantmentTarget target,
		Entity entity,
		DamageSource source,
		CallbackInfo callbackInfo
	) {
		if (BooksConfigAPIManager.shouldOverrideBaneOfArthropods((Enchantment) (Object) this)) {
			callbackInfo.cancel();
		}
	}

	/** Prevents configured Fire Aspect from retaining vanilla's burn duration. */
	@Inject(
		method = "doPostAttack(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/enchantment/EnchantedItemInUse;Lnet/minecraft/world/item/enchantment/EnchantmentTarget;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$replaceFireAspectPostAttack(
		ServerLevel serverLevel,
		int level,
		EnchantedItemInUse itemSource,
		EnchantmentTarget target,
		Entity entity,
		DamageSource source,
		CallbackInfo callbackInfo
	) {
		Enchantment enchantment = (Enchantment) (Object) this;
		if (!BooksConfigAPIManager.isFireAspect(enchantment)) return;
		boolean override = BooksConfigAPIManager.shouldOverrideFireAspect(enchantment);
		if (override) callbackInfo.cancel();
	}

	/** Applies Smite vulnerability and Glowing to every compatible target after a hit. */
	@Inject(
		method = "doPostAttack(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/enchantment/EnchantedItemInUse;Lnet/minecraft/world/item/enchantment/EnchantmentTarget;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$replaceSmitePostAttack(
		ServerLevel serverLevel,
		int level,
		EnchantedItemInUse itemSource,
		EnchantmentTarget target,
		Entity entity,
		DamageSource source,
		CallbackInfo callbackInfo
	) {
		if (EnchantBooksAPIManager.applyConfiguredSmiteEffects(
			(Enchantment) (Object) this,
			serverLevel,
			level,
			itemSource,
			entity,
			source
		)) {
			callbackInfo.cancel();
		}
	}

	/** Replaces vanilla Breach's armor-effectiveness contribution in the vanilla armor path. */
	@Inject(
		method = "modifyArmorEffectivness(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;Lorg/apache/commons/lang3/mutable/MutableFloat;)V",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madokuCraft$replaceBreachArmorEffectiveness(
		ServerLevel serverLevel,
		int level,
		ItemStack stack,
		Entity entity,
		DamageSource source,
		MutableFloat armorEffectiveness,
		CallbackInfo callbackInfo
	) {
		double penetration = BooksConfigAPIManager.getConfiguredBreachArmorPenetration(
			(Enchantment) (Object) this,
			stack,
			level
		);
		if (penetration < 0.0D) return;

		armorEffectiveness.add((float) (-penetration / 100.0D));
		callbackInfo.cancel();
	}

}
