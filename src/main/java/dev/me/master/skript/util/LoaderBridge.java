package dev.me.master.skript.util;

import net.fabricmc.loader.api.FabricLoader;
import java.nio.file.Path;

public final class LoaderBridge {

	private LoaderBridge() {
	}

	public static Path gameDir() {
		return FabricLoader.getInstance().getGameDir();
	}
}
