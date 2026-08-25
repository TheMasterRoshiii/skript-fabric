package dev.me.master.skript.types;

import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;

public final class RegistriesNames {

	private RegistriesNames() {
	}

	public static String entityTypeName(EntityType<?> type) {
		String path = Registries.ENTITY_TYPE.getId(type).getPath();
		return path.replace('_', ' ');
	}

	public static String blockName(Block block) {
		String path = Registries.BLOCK.getId(block).getPath();
		return path.replace('_', ' ');
	}
}
