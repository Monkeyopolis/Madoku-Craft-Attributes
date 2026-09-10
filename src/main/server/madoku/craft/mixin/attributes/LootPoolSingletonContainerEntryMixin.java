package madoku.craft.mixin.attributes;

import madoku.craft.java.core.enchant.EnchantBooksAPIManager;
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer$EntryBase")
public abstract class LootPoolSingletonContainerEntryMixin {
	@Shadow @Final private LootPoolSingletonContainer this$0;

	@Inject(method = "getWeight", at = @At("RETURN"), cancellable = true)
	private void madokuCraft$applyConfiguredLuckOfTheSea(
		float luck,
		CallbackInfoReturnable<Integer> callbackInfo
	) {
		callbackInfo.setReturnValue(
			EnchantBooksAPIManager.resolveConfiguredLuckOfTheSeaWeight(
				this$0,
				luck,
				callbackInfo.getReturnValue()
			)
		);
	}
}

