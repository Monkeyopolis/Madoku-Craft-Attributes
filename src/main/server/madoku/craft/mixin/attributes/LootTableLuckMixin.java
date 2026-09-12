package madoku.craft.mixin.attributes;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import madoku.craft.java.attributes.LuckAPIManager;
import madoku.craft.java.core.enchant.EnchantBooksAPIManager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LootTable.class)
public class LootTableLuckMixin {
	@Inject(
		method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
		at = @At("HEAD")
	)
	private void madokuCraft$beginConfiguredLuckOfTheSea(
		LootParams lootParams,
		CallbackInfoReturnable<ObjectArrayList<ItemStack>> callbackInfo
	) {
		EnchantBooksAPIManager.beginConfiguredLuckOfTheSea(lootParams);
	}

	@Inject(
		method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
		at = @At("RETURN")
	)
	private void madokuCraft$endConfiguredLuckOfTheSea(
		LootParams lootParams,
		CallbackInfoReturnable<ObjectArrayList<ItemStack>> callbackInfo
	) {
		EnchantBooksAPIManager.endConfiguredLuckOfTheSea();
	}

	@Inject(
		method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
		at = @At("RETURN")
	)
	private void madokuCraft$applyLuckToBlockDrops(
		LootContext lootContext,
		CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir
	) {
		LuckAPIManager.applyGeneratedLoot(lootContext, cir.getReturnValue());
	}
}

