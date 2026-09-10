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
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
	private static final int SAVE_INTERVAL_TICKS = SkriptConfig.INSTANCE.variableSaveIntervalTicks;
	private static final Map<String, Object> GLOBALS = new ConcurrentHashMap<>();
	private static final Map<String, Integer> LIST_SIZES = new ConcurrentHashMap<>();
	private static final AtomicBoolean SAVING = new AtomicBoolean(false);
	private static volatile @Nullable Thread saver;
	private static volatile boolean dirty;

	private Variables() {
	}

	public static void init() {
		ServerTickEvents.END_SERVER_TICK.register(Variables::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTicks() % SAVE_INTERVAL_TICKS == 0)
			saveIfDirty();
	}

	public static @Nullable Object get(String name) {
		return GLOBALS.get(name.toLowerCase(Locale.ROOT));
	}

	public static boolean contains(String name) {
		return GLOBALS.containsKey(name.toLowerCase(Locale.ROOT));
	}

	public static void set(String name, @Nullable Object value) {
		String key = name.toLowerCase(Locale.ROOT);
		if (value == null)
			GLOBALS.remove(key);
		else
			GLOBALS.put(key, value);
		dirty = true;
	}

	public static void setIfAbsent(String name, @Nullable Object value) {
		if (value == null)
			return;
		String key = name.toLowerCase(Locale.ROOT);
		if (GLOBALS.putIfAbsent(key, value) == null)
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
		int next = LIST_SIZES.merge(listName.toLowerCase(Locale.ROOT), 1, Integer::sum);
		set(listName + "::" + next, value);
		return next;
	}

	public static void removeList(String listName) {
		String prefix = listName.toLowerCase(Locale.ROOT) + "::";
		for (String key : matchingKeys(prefix))
			delete(key);
		LIST_SIZES.remove(listName.toLowerCase(Locale.ROOT));
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
		GLOBALS.clear();
		LIST_SIZES.clear();
		dirty = false;
		if (!Files.exists(file))
			return;
		try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = GSON.fromJson(reader, JsonObject.class);
			if (root == null || !root.has("variables"))
				return;
			for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("variables").entrySet()) {
				Object value = deserialize(entry.getValue());
				if (value != null) {
					String key = entry.getKey().toLowerCase(Locale.ROOT);
					GLOBALS.put(key, value);
					rememberListIndex(key);
				}
			}
		} catch (IOException | RuntimeException e) {
			SkriptLogger.error("Failed to load variables from " + file, e);
		}
	}

	public static void saveIfDirty() {
		if (!dirty)
			return;
		if (!SAVING.compareAndSet(false, true))
			return;
		dirty = false;
		JsonObject snapshot;
		try {
			snapshot = captureSnapshot();
		} catch (RuntimeException e) {
			dirty = true;
			SAVING.set(false);
			SkriptLogger.error("Failed to snapshot variables", e);
			return;
		}
		Thread writer = Thread.ofVirtual().name("skript-variable-saver").unstarted(() -> {
			try {
				if (!writeSnapshot(snapshot))
					dirty = true;
			} finally {
				saver = null;
				SAVING.set(false);
			}
		});
		saver = writer;
		writer.start();
	}

	public static void saveNow() {
		awaitSaver();
		if (!dirty || !SAVING.compareAndSet(false, true))
			return;
		dirty = false;
		JsonObject snapshot;
		try {
			snapshot = captureSnapshot();
		} catch (RuntimeException e) {
			dirty = true;
			SAVING.set(false);
			SkriptLogger.error("Failed to snapshot variables", e);
			return;
		}
		try {
			if (!writeSnapshot(snapshot))
				dirty = true;
		} finally {
			SAVING.set(false);
		}
	}

	private static void awaitSaver() {
		while (SAVING.get()) {
			Thread active = saver;
			if (active == null) {
				Thread.yield();
				continue;
			}
			try {
				active.join();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				SkriptLogger.error("Interrupted while saving variables", e);
				return;
			}
		}
	}

	private static JsonObject captureSnapshot() {
		JsonObject root = new JsonObject();
		JsonObject vars = new JsonObject();
		for (Map.Entry<String, Object> entry : GLOBALS.entrySet())
			vars.add(entry.getKey(), serialize(entry.getValue()));
		root.add("variables", vars);
		return root;
	}

	private static boolean writeSnapshot(JsonObject root) {
		Path file = SkriptConfig.INSTANCE.variablesFile;
		try {
			Path parent = file.getParent();
			if (parent != null)
				Files.createDirectories(parent);
			Path temp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
				GSON.toJson(root, writer);
			}
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			return true;
		} catch (IOException e) {
			SkriptLogger.error("Failed to save variables to " + file, e);
			return false;
		}
	}

	private static void rememberListIndex(String key) {
		int separator = key.lastIndexOf("::");
		if (separator <= 0 || separator == key.length() - 2)
			return;
		try {
			int index = Math.toIntExact(Long.parseLong(key.substring(separator + 2)));
			if (index > 0) {
				String listName = key.substring(0, separator);
				LIST_SIZES.merge(listName, index, Math::max);
			}
		} catch (NumberFormatException | ArithmeticException ignored) {
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
			dirty = true;
		}
	}
}
