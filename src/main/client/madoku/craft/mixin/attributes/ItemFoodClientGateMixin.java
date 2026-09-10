package madoku.craft.mixin.attributes;

import madoku.craft.java.attributes.MadokuAttributesClient;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public abstract class ItemFoodClientGateMixin {
	@Inject(method = "use", at = @At("HEAD"), cancellable = true)
	private void madokuCraft$gateClientFoodUse(
		Level level,
		Player player,
		InteractionHand hand,
		CallbackInfoReturnable<InteractionResult> cir
	) {
		if (level == null || !level.isClientSide() || player == null) {
			return;
		}

		ItemStack stack = player.getItemInHand(hand);
		if (stack.get(DataComponents.FOOD) == null) {
			return;
		}

		if (!MadokuAttributesClient.canConsumeFoodClient(false)) {
			cir.setReturnValue(InteractionResult.FAIL);
		}
	}
}
