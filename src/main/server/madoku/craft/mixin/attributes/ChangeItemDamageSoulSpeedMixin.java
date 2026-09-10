package madoku.craft.mixin.attributes;

import madoku.craft.java.core.enchant.EnchantBooksAPIManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.effects.ChangeItemDamage;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Cancels only the ChangeItemDamage effect while Soul Speed's location effects are running. */
@Mixin(ChangeItemDamage.class)
public abstract class ChangeItemDamageSoulSpeedMixin {
	@Inject(method = "apply", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$cancelSoulSpeedDurabilityDamage(
		ServerLevel serverLevel,
		int level,
		EnchantedItemInUse itemSource,
		Entity entity,
		Vec3 position,
		CallbackInfo callbackInfo
	) {
		if (EnchantBooksAPIManager.shouldCancelSoulSpeedDurabilityChange(level, itemSource, entity)) callbackInfo.cancel();
	}
}

