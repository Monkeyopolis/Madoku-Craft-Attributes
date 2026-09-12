package madoku.craft.mixin.attributes;

import madoku.craft.java.core.enchant.EnchantBooksAPIManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces vanilla's final Mending XP-repair result with the configured behavior. */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMendingMixin {
	@Inject(
		method = "modifyDurabilityToRepairFromXp(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;I)I",
		at = @At("HEAD"),
		cancellable = true
	)
	private static void madokuCraft$replaceMendingXpRepair(
		ServerLevel serverLevel,
		ItemStack stack,
		int repairAmount,
		CallbackInfoReturnable<Integer> callbackInfo
	) {
		if (EnchantBooksAPIManager.applyConfiguredMendingXpRepairOverride(
			serverLevel,
			stack,
			repairAmount
		)) {
			callbackInfo.setReturnValue(0);
		}
	}
}

