package dev.me.master.skript.types;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public record BlockRef(ServerWorld world, BlockPos pos) {

	@Override
	public String toString() {
		return pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + " in "
				+ world.getRegistryKey().getValue();
	}
}
