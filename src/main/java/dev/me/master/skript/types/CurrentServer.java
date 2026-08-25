package dev.me.master.skript.types;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.World;
public final class CurrentServer {

	private static volatile @Nullable MinecraftServer server;

	private CurrentServer() {
	}

	public static void attach(MinecraftServer active) {
		server = active;
	}

	public static void detach(MinecraftServer stopping) {
		if (server == stopping)
			server = null;
	}

	public static @Nullable MinecraftServer get() {
		return server;
	}

	public static @Nullable ServerWorld overworld() {
		MinecraftServer active = server;
		return active == null ? null : active.getWorld(World.OVERWORLD);
	}
}
