package dev.me.master.skript.registrations;
import dev.me.master.skript.lang.ClassInfo;
import dev.me.master.skript.lang.Classes;
import dev.me.master.skript.lang.Parser;
import dev.me.master.skript.lang.Relation;
import dev.me.master.skript.lang.TimeSpanLiteral;
import dev.me.master.skript.types.AliasIndex;
import dev.me.master.skript.types.BlockRef;
import dev.me.master.skript.types.CurrentServer;
import dev.me.master.skript.types.ItemType;
import dev.me.master.skript.types.RegistriesNames;
import dev.me.master.skript.types.WorldPos;
import dev.me.master.skript.util.TimeSpan;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class DefaultTypes {

	private DefaultTypes() {
	}

	public static void register() {
		Classes.register(ClassInfo.of("number", Number.class,
				raw -> {
					Object parsed = ParserAccess.parseNumber(raw);
					return (Number) parsed;
				},
				value -> value instanceof Double d && d == Math.rint(d)
						? String.valueOf(d.longValue())
						: String.valueOf(value)));

		Classes.register(ClassInfo.of("integer", Number.class, raw -> {
			try {
				return Long.parseLong(raw);
			} catch (NumberFormatException e) {
				return null;
			}
		}, value -> String.valueOf(value)));

		Classes.register(ClassInfo.of("boolean", Boolean.class,
				raw -> switch (raw.toLowerCase(java.util.Locale.ROOT)) {
					case "true", "yes" -> Boolean.TRUE;
					case "false", "no" -> Boolean.FALSE;
					default -> null;
				},
				value -> value ? "true" : "false"));

		Classes.register(ClassInfo.of("timespan", TimeSpan.class,
				DefaultTypes::parseTimeSpan,
				TimeSpan::toString));

		Classes.register(ClassInfo.of("itemtype", ItemType.class,
				DefaultTypes::parseItemType,
				ItemType::toString));

		Classes.register(ClassInfo.of("entitytype", EntityType.class,
				AliasIndex::findEntityType,
				value -> RegistriesNames.entityTypeName((EntityType<?>) value)));

		Classes.register(ClassInfo.of("block", Block.class,
				DefaultTypes::parseBlock,
				value -> RegistriesNames.blockName((Block) value)));

		Classes.register(ClassInfo.of("world", ServerWorld.class,
				ServerLookup::findWorld,
				value -> ((ServerWorld) value).getRegistryKey().getValue().getPath()));

		Classes.register(ClassInfo.of("player", ServerPlayerEntity.class,
				ServerLookup::findPlayer,
				value -> ((ServerPlayerEntity) value).getNameForScoreboard()));

		Classes.register(ClassInfo.of("string", String.class,
				raw -> raw,
				value -> value));

	}

	private static TimeSpan parseTimeSpan(String raw) {
		TimeSpanLiteral literal =
				TimeSpanLiteral.tryParse(raw);
		return literal == null ? null : literal.value();
	}

	private static ItemType parseItemType(String raw) {
		String name = raw.trim();
		int amountSeparator = name.indexOf(' ');
		int leadingAmount = -1;
		if (amountSeparator > 0) {
			String head = name.substring(0, amountSeparator);
			if (head.chars().allMatch(Character::isDigit) && !head.isEmpty()) {
				try {
					leadingAmount = Integer.parseInt(head);
					name = name.substring(amountSeparator + 1).trim();
				} catch (NumberFormatException ignored) {
					leadingAmount = -1;
				}
			}
		}
		Item item = AliasIndex.findItem(name);
		if (item == null || item == Items.AIR)
			return null;
		return ItemType.of(item, Math.max(1, leadingAmount));
	}

	private static Block parseBlock(String raw) {
		ItemType itemType = parseItemType(raw);
		if (itemType == null)
			return null;
		Block block = Block.getBlockFromItem(itemType.item());
		return block == Blocks.AIR ? null : block;
	}

	private static final class ParserAccess {

		static Object parseNumber(String raw) {
			Number parsed = Parser.tryParseNumber(raw);
			return parsed;
		}
	}

	private static final class ServerLookup {

		static ServerWorld findWorld(String raw) {
			MinecraftServer server = CurrentServer.get();
			if (server == null)
				return null;
			String lowered = raw.toLowerCase(java.util.Locale.ROOT).replace(' ', '_');
			for (ServerWorld world : server.getWorlds()) {
				String path = world.getRegistryKey().getValue().getPath();
				if (path.equalsIgnoreCase(lowered))
					return world;
			}
			return null;
		}

		static ServerPlayerEntity findPlayer(String raw) {
			MinecraftServer server = CurrentServer.get();
			if (server == null)
				return null;
			return server.getPlayerManager().getPlayer(raw.trim());
		}
	}

	public static void registerComparisons() {
		Classes.registerComparator(Number.class, Number.class,
				(a, b) -> {
					double left = a.doubleValue();
					double right = b.doubleValue();
					if (left == right)
						return Relation.EQUAL;
					return left > right ? Relation.GREATER : Relation.LESSER;
				});
		Classes.registerComparator(String.class, String.class,
				(a, b) -> a.equalsIgnoreCase(b)
						? Relation.EQUAL
						: a.compareToIgnoreCase(b) > 0 ? Relation.GREATER : Relation.LESSER);
		Classes.registerComparator(Boolean.class, Boolean.class,
				(a, b) -> a.equals(b) ? Relation.EQUAL : Relation.NOT_EQUAL);
		Classes.registerComparator(TimeSpan.class, TimeSpan.class,
				(a, b) -> Integer.signum(a.compareTo(b)) == 0
						? Relation.EQUAL
						: a.compareTo(b) > 0 ? Relation.GREATER : Relation.LESSER);
		Classes.registerComparator(ItemType.class, ItemType.class,
				(a, b) -> a.item() == b.item()
						? Relation.EQUAL
						: Relation.NOT_EQUAL);
		Classes.registerComparator(EntityType.class, EntityType.class,
				(a, b) -> a == b ? Relation.EQUAL : Relation.NOT_EQUAL);
		Classes.registerComparator(BlockRef.class, BlockRef.class,
				(a, b) -> a.pos().equals(b.pos()) && a.world() == b.world()
						? Relation.EQUAL
						: Relation.NOT_EQUAL);
		Classes.registerComparator(WorldPos.class, WorldPos.class,
				(a, b) -> Double.compare(a.x(), b.x()) == 0 && Double.compare(a.y(), b.y()) == 0
						&& Double.compare(a.z(), b.z()) == 0 && a.world() == b.world()
								? Relation.EQUAL
								: Relation.NOT_EQUAL);

		Classes.registerConverter(ServerPlayerEntity.class, LivingEntity.class, p -> p);
		Classes.registerConverter(LivingEntity.class, Entity.class, l -> l);
		Classes.registerConverter(Entity.class, WorldPos.class,
				e -> new WorldPos((ServerWorld) e.getWorld(), e.getX(), e.getY(), e.getZ(), e.getYaw(), e.getPitch()));
		Classes.registerConverter(ServerPlayerEntity.class, BlockPos.class,
				p -> p.getBlockPos());
		Classes.registerConverter(BlockPos.class, WorldPos.class,
				pos -> {
					ServerWorld overworld = CurrentServer.overworld();
					return overworld == null ? null : new WorldPos(overworld, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0f, 0f);
				});
		Classes.registerConverter(Number.class, Long.class, n -> n.longValue());
	}
}
