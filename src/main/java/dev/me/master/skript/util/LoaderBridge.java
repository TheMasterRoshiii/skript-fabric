package dev.me.master.skript.util;

import java.nio.file.Path;

public final class LoaderBridge {

	private LoaderBridge() {
	}

	public static Path gameDir() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
	}
}
