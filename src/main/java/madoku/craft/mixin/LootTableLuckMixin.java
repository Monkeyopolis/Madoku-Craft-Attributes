package madoku.craft.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import madoku.craft.luck.MadokuLuck;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LootTable.class)
public class LootTableLuckMixin {
	@Inject(
		method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
		at = @At("RETURN")
	)
	private void madokuCraft$applyLuckToBlockDrops(
		LootContext lootContext,
		CallbackInfoReturnable<ObjectArrayList<ItemStack>> cir
	) {
		MadokuLuck.applyGeneratedLoot(lootContext, cir.getReturnValue());
	}
}
