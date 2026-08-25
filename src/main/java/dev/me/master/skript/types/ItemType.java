package dev.me.master.skript.types;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;

public record ItemType(Item item, int count) {

	public static final ItemType AIR = new ItemType(Items.AIR, 1);

	public static ItemType of(Item item, int count) {
		if (count < 1)
			count = 1;
		return new ItemType(item, count);
	}

	public boolean isAir() {
		return item == Items.AIR;
	}

	public ItemStack createStack() {
		return new ItemStack(item, count);
	}

	public String id() {
		return Registries.ITEM.getId(item).toString();
	}

	@Override
	public String toString() {
		String name = id();
		if (name.startsWith("minecraft:"))
			name = name.substring("minecraft:".length());
		return count == 1 ? name.replace('_', ' ') : count + " " + name.replace('_', ' ') + "s";
	}
}
