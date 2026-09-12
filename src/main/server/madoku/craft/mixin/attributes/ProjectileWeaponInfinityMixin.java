package madoku.craft.mixin.attributes;

import madoku.craft.java.core.enchant.EnchantBooksAPIManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProjectileWeaponItem.class)
public abstract class ProjectileWeaponInfinityMixin {
	@Inject(
		method = "useAmmo(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;Z)Lnet/minecraft/world/item/ItemStack;",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void madokuCraft$applyConfiguredInfinity(
		ItemStack weapon,
		ItemStack ammo,
		LivingEntity shooter,
		boolean multishotProjectile,
		CallbackInfoReturnable<ItemStack> callbackInfo
	) {
		ItemStack preservedAmmo = EnchantBooksAPIManager.applyConfiguredInfinityAmmo(
			weapon,
			ammo,
			shooter,
			multishotProjectile
		);
		if (preservedAmmo != null) callbackInfo.setReturnValue(preservedAmmo);
	}
}

