package madoku.craft.java.attributes;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

/** Optional bridge for configured enchantment effects used by Luck drop handling. */
@FunctionalInterface
public interface LuckEnchantmentAdapter {
	boolean applyConfiguredFortune(ItemStack tool, ItemStack stack, RandomSource random);
}
