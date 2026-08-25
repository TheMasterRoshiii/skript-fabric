package dev.me.master.skript.variables;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.me.master.skript.config.SkriptConfig;
import dev.me.master.skript.lang.Classes;
import dev.me.master.skript.types.ItemType;
import dev.me.master.skript.util.SkriptLogger;
import dev.me.master.skript.util.TimeSpan;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class Variables {

	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
	private static final Map<String, Object> GLOBALS = new ConcurrentHashMap<>();
	private static final Map<String, Integer> LIST_SIZES = new ConcurrentHashMap<>();
	private static final AtomicBoolean SAVING = new AtomicBoolean(false);
	private static volatile boolean dirty;

	private Variables() {
	}

	public static @Nullable Object get(String name) {
		return GLOBALS.get(name.toLowerCase(java.util.Locale.ROOT));
	}

	public static boolean contains(String name) {
		return GLOBALS.containsKey(name.toLowerCase(java.util.Locale.ROOT));
	}

	public static void set(String name, @Nullable Object value) {
		String key = name.toLowerCase(java.util.Locale.ROOT);
		if (value == null)
			GLOBALS.remove(key);
		else
			GLOBALS.put(key, value);
		dirty = true;
	}

	public static void delete(String name) {
		set(name, null);
	}

	public static void clearAll() {
		GLOBALS.clear();
		LIST_SIZES.clear();
		dirty = true;
	}

	public static int addToList(String listName, Object value) {
		int next = LIST_SIZES.merge(listName.toLowerCase(java.util.Locale.ROOT), 1, Integer::sum);
		set(listName + "::" + next, value);
		return next;
	}

	public static void removeList(String listName) {
		String prefix = listName.toLowerCase(java.util.Locale.ROOT) + "::";
		for (String key : matchingKeys(prefix))
			delete(key);
		LIST_SIZES.remove(listName.toLowerCase(java.util.Locale.ROOT));
	}

	public static List<String> matchingKeys(String prefix) {
		List<String> matches = new ArrayList<>();
		for (String key : GLOBALS.keySet()) {
			if (key.startsWith(prefix))
				matches.add(key);
		}
		matches.sort((a, b) -> {
			long ia = suffixIndex(a);
			long ib = suffixIndex(b);
			return Long.compare(ia, ib);
		});
		return matches;
	}

	private static long suffixIndex(String key) {
		int sep = key.lastIndexOf("::");
		if (sep < 0)
			return Long.MAX_VALUE;
		try {
			return Long.parseLong(key.substring(sep + 2));
		} catch (NumberFormatException e) {
			return Long.MAX_VALUE;
		}
	}

	public static boolean isDirty() {
		return dirty;
	}

	public static void load() {
		Path file = SkriptConfig.INSTANCE.variablesFile;
		if (!Files.exists(file))
			return;
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = GSON.fromJson(reader, JsonObject.class);
			if (root == null || !root.has("variables"))
				return;
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("variables").entrySet()) {
				Object value = deserialize(entry.getValue());
				if (value != null)
					GLOBALS.put(entry.getKey(), value);
			}
		} catch (IOException | RuntimeException e) {
			SkriptLogger.error("Failed to load variables from " + file, e);
		}
	}

	public static void saveIfDirty() {
		if (!dirty)
			return;
		dirty = false;
		spawnSaver();
	}

	public static void saveNow() {
		long deadline = System.nanoTime() + 5_000_000_000L;
		while (SAVING.get()) {
			if (System.nanoTime() > deadline)
				break;
			Thread.onSpinWait();
		}
		SAVING.set(true);
		try {
			saveSnapshot();
		} finally {
			SAVING.set(false);
		}
	}

	private static void spawnSaver() {
		if (!SAVING.compareAndSet(false, true))
			return;
		Thread writer = Thread.ofVirtual().name("skript-variable-saver").unstarted(() -> {
			try {
				saveSnapshot();
			} finally {
				SAVING.set(false);
			}
		});
		writer.start();
	}

	private static void saveSnapshot() {
		Path file = SkriptConfig.INSTANCE.variablesFile;
		JsonObject root = new JsonObject();
		JsonObject vars = new JsonObject();
		for (Map.Entry<String, Object> entry : GLOBALS.entrySet())
			vars.add(entry.getKey(), serialize(entry.getValue()));
		root.add("variables", vars);
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
			Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			SkriptLogger.error("Failed to save variables to " + file, e);
		}
	}

	public static JsonElement serialize(@Nullable Object value) {
		switch (value) {
			case null -> {
				return JsonNull.INSTANCE;
			}
			case String s -> {
				return new JsonPrimitive(s);
			}
			case Boolean b -> {
				return new JsonPrimitive(b);
			}
			case Byte n -> {
				return new JsonPrimitive(n);
			}
			case Short n -> {
				return new JsonPrimitive(n);
			}
			case Integer n -> {
				return new JsonPrimitive(n);
			}
			case Long n -> {
				return new JsonPrimitive(n);
			}
			case Float f -> {
				return new JsonPrimitive(f.doubleValue());
			}
			case Double d -> {
				return new JsonPrimitive(d);
			}
			case TimeSpan span -> {
				JsonObject tagged = new JsonObject();
				tagged.addProperty("t", "timespan");
				tagged.addProperty("v", span.ticks());
				return tagged;
			}
			case ItemType item -> {
				JsonObject tagged = new JsonObject();
				tagged.addProperty("t", "item");
				tagged.addProperty("id", Registries.ITEM.getId(item.item()).toString());
				tagged.addProperty("c", item.count());
				return tagged;
			}
			case EntityType<?> entityType -> {
				JsonObject tagged = new JsonObject();
				tagged.addProperty("t", "entitytype");
				tagged.addProperty("v", Registries.ENTITY_TYPE.getId(entityType).toString());
				return tagged;
			}
			case Block block -> {
				JsonObject tagged = new JsonObject();
				tagged.addProperty("t", "block");
				tagged.addProperty("v", Registries.BLOCK.getId(block).toString());
				return tagged;
			}
			case ServerPlayerRef ref -> {
				JsonObject tagged = new JsonObject();
				tagged.addProperty("t", "player");
				tagged.addProperty("v", ref.uuid().toString());
				return tagged;
			}
			case ServerWorld world -> {
				JsonObject tagged = new JsonObject();
				tagged.addProperty("t", "world");
				tagged.addProperty("v", world.getRegistryKey().getValue().toString());
				return tagged;
			}
			case BlockPos pos -> {
				JsonObject tagged = new JsonObject();
				tagged.addProperty("t", "blockpos");
				tagged.addProperty("x", pos.getX());
				tagged.addProperty("y", pos.getY());
				tagged.addProperty("z", pos.getZ());
				return tagged;
			}
			default -> {
				JsonObject tagged = new JsonObject();
				tagged.addProperty("t", "string");
				tagged.addProperty("v", Classes.toStringValue(value));
				return tagged;
			}
		}
	}

	public static @Nullable Object deserialize(JsonElement element) {
		if (element == null || element.isJsonNull())
			return null;
		if (element.isJsonPrimitive()) {
			JsonPrimitive primitive = element.getAsJsonPrimitive();
			if (primitive.isBoolean())
				return primitive.getAsBoolean();
			if (primitive.isNumber()) {
				double raw = primitive.getAsDouble();
				return raw == Math.rint(raw) && !primitive.getAsString().contains(".")
						? (Object) primitive.getAsLong()
						: (Object) raw;
			}
			return primitive.getAsString();
		}
		if (!element.isJsonObject())
			return null;
		JsonObject object = element.getAsJsonObject();
		if (!object.has("t"))
			return null;
		String type = object.get("t").getAsString();
		return switch (type) {
			case "timespan" -> new TimeSpan(object.get("v").getAsLong());
			case "item" -> ItemType.of(
					Registries.ITEM.get(Identifier.of(object.get("id").getAsString())),
					object.get("c").getAsInt());
			case "entitytype" -> Registries.ENTITY_TYPE.get(Identifier.of(object.get("v").getAsString()));
			case "block" -> Registries.BLOCK.get(Identifier.of(object.get("v").getAsString()));
			case "player" -> ServerPlayerRef.of(UUID.fromString(object.get("v").getAsString()));
			case "world" -> WorldRef.pending(object.get("v").getAsString());
			case "blockpos" -> new BlockPos(
					object.get("x").getAsInt(),
					object.get("y").getAsInt(),
					object.get("z").getAsInt());
			default -> object.has("v") ? object.get("v").getAsString() : null;
		};
	}

	public record ServerPlayerRef(UUID uuid) {

		private static final Map<UUID, ServerPlayerRef> CACHE = new ConcurrentHashMap<>();

		public static ServerPlayerRef of(UUID uuid) {
			return CACHE.computeIfAbsent(uuid, ServerPlayerRef::new);
		}
	}

	public record WorldRef(String dimensionId) implements PendingResolution {

		private static final Map<String, WorldRef> CACHE = new ConcurrentHashMap<>();

		public static WorldRef pending(String dimensionId) {
			return CACHE.computeIfAbsent(dimensionId, WorldRef::new);
		}

		@Override
		public @Nullable Object resolve(MinecraftServer server) {
			ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(dimensionId)));
			if (world == null)
				SkriptLogger.warn("Variable references unknown world " + dimensionId);
			return world;
		}
	}

	public interface PendingResolution {
		@Nullable Object resolve(MinecraftServer server);
	}

	public static void resolvePending(MinecraftServer server) {
		List<String> pending = new ArrayList<>();
		for (Map.Entry<String, Object> entry : GLOBALS.entrySet()) {
			if (entry.getValue() instanceof PendingResolution)
				pending.add(entry.getKey());
		}
		for (String key : pending) {
			Object resolved = ((PendingResolution) GLOBALS.get(key)).resolve(server);
			if (resolved == null)
				GLOBALS.remove(key);
			else
				GLOBALS.put(key, resolved);
		}
	}
}
