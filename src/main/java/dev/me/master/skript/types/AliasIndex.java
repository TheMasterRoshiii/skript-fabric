package dev.me.master.skript.types;

import dev.me.master.skript.util.SkriptLogger;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class AliasIndex {

	private static final Map<String, Item> ITEMS = new HashMap<>();
	private static final Map<String, EntityType<?>> ENTITY_TYPES = new HashMap<>();

	private static final Map<String, String> ITEM_EXTRA_ALIASES = buildItemExtras();
	private static final Map<String, String> ENTITY_EXTRA_ALIASES = buildEntityExtras();

	private AliasIndex() {
	}

	public static void init() {
		if (!ITEMS.isEmpty())
			throw new AssertionError("AliasIndex initialized twice");
		for (Item item : Registries.ITEM) {
			Identifier id = Registries.ITEM.getId(item);
			String path = id.getPath().replace('_', ' ');
			putItem(path, item);
			putItem(singularize(path), item);
		}
		for (EntityType<?> type : Registries.ENTITY_TYPE) {
			Identifier id = Registries.ENTITY_TYPE.getId(type);
			String path = id.getPath().replace('_', ' ');
			putEntity(path, type);
			putEntity(singularize(path), type);
		}
		SkriptLogger.info("Indexed " + ITEMS.size() + " item names and " + ENTITY_TYPES.size() + " entity type names");
	}

	private static void putItem(String name, Item item) {
		String key = name.toLowerCase(Locale.ROOT);
		if (ITEM_EXTRA_ALIASES.containsKey(key))
			return;
		ITEMS.putIfAbsent(key, item);
	}

	private static void putEntity(String name, EntityType<?> type) {
		String key = name.toLowerCase(Locale.ROOT);
		if (ENTITY_EXTRA_ALIASES.containsKey(key))
			return;
		ENTITY_TYPES.putIfAbsent(key, type);
	}

	public static Item findItem(String rawName) {
		String key = normalize(rawName);
		Item direct = ITEMS.get(key);
		if (direct != null)
			return direct;
		String mapped = ITEM_EXTRA_ALIASES.get(key);
		if (mapped == null)
			return null;
		try {
			Item item = Registries.ITEM.get(Identifier.of("minecraft", mapped));
			return item == Items.AIR ? null : item;
		} catch (RuntimeException | LinkageError e) {
			SkriptLogger.warn("Registries not available for alias lookup '" + rawName + "'");
			return null;
		}
	}

	public static EntityType<?> findEntityType(String rawName) {
		String key = normalize(rawName);
		EntityType<?> direct = ENTITY_TYPES.get(key);
		if (direct != null)
			return direct;
		String mapped = ENTITY_EXTRA_ALIASES.get(key);
		if (mapped == null)
			return null;
		try {
			return Registries.ENTITY_TYPE.get(Identifier.of("minecraft", mapped));
		} catch (RuntimeException | LinkageError e) {
			SkriptLogger.warn("Registries not available for entity alias lookup '" + rawName + "'");
			return null;
		}
	}

	private static String normalize(String rawName) {
		return rawName.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

	public static String singularize(String name) {
		if (name.endsWith("ches") || name.endsWith("shes") || name.endsWith("sses") || name.endsWith("xes"))
			return name.substring(0, name.length() - 2);
		if (name.endsWith("ies"))
			return name.substring(0, name.length() - 3) + "y";
		if (name.endsWith("ses") && !name.endsWith("oses") && !name.endsWith("asses"))
			return name.substring(0, name.length() - 2);
		if (name.endsWith("s") && !name.endsWith("ss") && name.length() > 1)
			return name.substring(0, name.length() - 1);
		return name;
	}

	private static Map<String, String> buildItemExtras() {
		Map<String, String> extras = new HashMap<>();
		extras.put("apple", "apple");
		extras.put("steak", "cooked_beef");
		extras.put("beef", "beef");
		extras.put("porkchop", "porkchop");
		extras.put("cooked porkchop", "cooked_porkchop");
		extras.put("chicken", "chicken");
		extras.put("gunpowder", "gunpowder");
		extras.put("redstone dust", "redstone");
		extras.put("glowstone dust", "glowstone_dust");
		extras.put("wooden sword", "wooden_sword");
		extras.put("wooden pickaxe", "wooden_pickaxe");
		extras.put("wooden axe", "wooden_axe");
		extras.put("wooden shovel", "wooden_shovel");
		extras.put("gold ingot", "gold_ingot");
		extras.put("iron ingot", "iron_ingot");
		extras.put("netherite ingot", "netherite_ingot");
		extras.put("oak log", "oak_log");
		extras.put("birch log", "birch_log");
		extras.put("spruce log", "spruce_log");
		extras.put("log", "oak_log");
		extras.put("planks", "oak_planks");
		extras.put("cobble", "cobblestone");
		extras.put("grass block", "grass_block");
		extras.put("dirt", "dirt");
		extras.put("bow", "bow");
		extras.put("arrow", "arrow");
		extras.put("ender pearl", "ender_pearl");
		extras.put("eye of ender", "ender_eye");
		extras.put("golden apple", "golden_apple");
		extras.put("god apple", "enchanted_golden_apple");
		extras.put("notch apple", "enchanted_golden_apple");
		extras.put("totem", "totem_of_undying");
		extras.put("elytra", "elytra");
		return java.util.Map.copyOf(extras);
	}

	private static Map<String, String> buildEntityExtras() {
		Map<String, String> extras = new HashMap<>();
		extras.put("player", "player");
		extras.put("villager", "villager");
		extras.put("creeper", "creeper");
		extras.put("zombie", "zombie");
		extras.put("skeleton", "skeleton");
		extras.put("spider", "spider");
		extras.put("enderman", "enderman");
		extras.put("cow", "cow");
		extras.put("pig", "pig");
		extras.put("sheep", "sheep");
		extras.put("chicken", "chicken");
		extras.put("horse", "horse");
		extras.put("wolf", "wolf");
		extras.put("cat", "cat");
		extras.put("dog", "wolf");
		extras.put("ocelot", "ocelot");
		extras.put("blaze", "blaze");
		extras.put("ghast", "ghast");
		extras.put("wither", "wither");
		extras.put("dragon", "ender_dragon");
		extras.put("ender dragon", "ender_dragon");
		extras.put("item frame", "item_frame");
		extras.put("armor stand", "armor_stand");
		extras.put("minecart", "minecart");
		extras.put("boat", "boat");
		return java.util.Map.copyOf(extras);
	}
}
