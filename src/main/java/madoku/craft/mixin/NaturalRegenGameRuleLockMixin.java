package madoku.craft.mixin;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.commands.GameRuleCommand;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRuleCommand.class)
public abstract class NaturalRegenGameRuleLockMixin {
	@Inject(method = "setRule", at = @At("HEAD"), cancellable = true)
	private static <T extends GameRules.Value<T>> void madokuCraftAttributes$lockNaturalRegenGameRule(
		CommandContext<CommandSourceStack> context,
		GameRules.Key<T> rule,
		CallbackInfoReturnable<Integer> cir
	) {
		if (rule != GameRules.RULE_NATURAL_REGENERATION) {
			return;
		}

		CommandSourceStack source = context.getSource();
		source.sendFailure(Component.literal("Madoku Craft locked this gamerule."));
		cir.setReturnValue(0);
	}
}
