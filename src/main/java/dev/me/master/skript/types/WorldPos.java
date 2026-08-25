package dev.me.master.skript.types;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public record WorldPos(ServerWorld world, double x, double y, double z, float yaw, float pitch) {

	public static WorldPos centered(ServerWorld world, BlockPos pos) {
		return new WorldPos(world, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
	}

	public WorldPos withWorld(ServerWorld newWorld) {
		return new WorldPos(newWorld, x, y, z, yaw, pitch);
	}

	public BlockPos blockPos() {
		return BlockPos.ofFloored(x, y, z);
	}

	public double distanceTo(WorldPos other) {
		if (other.world != world)
			return Double.NaN;
		double dx = x - other.x;
		double dy = y - other.y;
		double dz = z - other.z;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	@Override
	public String toString() {
		return Math.round(x * 10) / 10.0 + ", " + Math.round(y * 10) / 10.0 + ", " + Math.round(z * 10) / 10.0
				+ " in " + world.getRegistryKey().getValue();
	}
}
