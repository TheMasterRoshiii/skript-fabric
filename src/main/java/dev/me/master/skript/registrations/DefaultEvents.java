package dev.me.master.skript.registrations;
import dev.me.master.skript.events.EventDispatch;
import dev.me.master.skript.events.EventHandler;
import dev.me.master.skript.events.ScriptEvent;
import dev.me.master.skript.lang.ClassInfo;
import dev.me.master.skript.lang.Classes;
import dev.me.master.skript.lang.Parser;
import dev.me.master.skript.types.AliasIndex;
import dev.me.master.skript.types.ItemType;
import dev.me.master.skript.util.SkriptLogger;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;

public final class DefaultEvents {

	private DefaultEvents() {
	}

	public static void register() {
		EventRegistry.register("(player )?join(ing)?|connect(ion)?", (name, parser) ->
				bind(ScriptEvent.PlayerJoin.class, name, noFilter()));
		EventRegistry.register("quit(ting)?|disconnect(ing)?|(player )?leav(e|ing)", (name, parser) ->
				bind(ScriptEvent.PlayerQuit.class, name, noFilter()));
		EventRegistry.register("(player )?chat(ting)?", (name, parser) ->
				bind(ScriptEvent.Chat.class, name, noFilter()));
		EventRegistry.register("(entity|player)? ?damage( of (?<of>.+))?", (name, parser) -> {
			Predicate<ScriptEvent> filter = victimFilter(group(name, "of"));
			return bind(ScriptEvent.Damage.class, name, filter);
		});
		EventRegistry.register("(entity |player )?death( of (?<of>.+))?", (name, parser) -> {
			Predicate<ScriptEvent> filter = victimFilter(group(name, "of"));
			return bind(ScriptEvent.Death.class, name, filter);
		});
		EventRegistry.register("(block ?)?(break|min(e|ing))( of (?<of>.+))?", (name, parser) -> {
			Predicate<ScriptEvent> filter = blockFilter(group(name, "of"));
			return bind(ScriptEvent.BlockBreak.class, name, filter);
		});
		EventRegistry.register("(block ?)?place(ment)?( of (?<of>.+))?", (name, parser) -> {
			Predicate<ScriptEvent> filter = placedFilter(group(name, "of"));
			return bind(ScriptEvent.BlockPlace.class, name, filter);
		});
		EventRegistry.register("right ?click( on .+)?", (name, parser) -> {
			EventHandler blocks = bind(ScriptEvent.RightClickBlock.class, name, noFilter());
			EventHandler entities = bind(ScriptEvent.RightClickEntity.class, name, noFilter());
			return trigger -> {
				blocks.bind(trigger);
				entities.bind(trigger);
			};
		});
		EventRegistry.register("left ?click( on .+)?", (name, parser) -> {
			EventHandler blocks = bind(ScriptEvent.LeftClickBlock.class, name, noFilter());
			EventHandler entities = bind(ScriptEvent.LeftClickEntity.class, name, noFilter());
			return trigger -> {
				blocks.bind(trigger);
				entities.bind(trigger);
			};
		});
		EventRegistry.register("respawn(ing)?", (name, parser) ->
				bind(ScriptEvent.Respawn.class, name, noFilter()));
		EventRegistry.register("(server )?command", (name, parser) ->
				bind(ScriptEvent.Command.class, name, noFilter()));
		EventRegistry.register("(script |skript )?(load|start)(ing|up)?", (name, parser) ->
				bind(ScriptEvent.ScriptLoad.class, name, noFilter()));
	}

	private static String group(String eventName, String groupName) {
		Matcher matcher = OF_PATTERN.matcher(eventName.toLowerCase(Locale.ROOT));
		return matcher.find() ? matcher.group(groupName) : null;
	}

	private static final Pattern OF_PATTERN =
			Pattern.compile(".*\\bof (?<" + "of" + ">.+)$");

	private static Predicate<ScriptEvent> noFilter() {
		return event -> true;
	}

	private static Predicate<ScriptEvent> victimFilter(String filterSource) {
		if (filterSource == null || filterSource.isBlank())
			return noFilter();
		String lowered = filterSource.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
		if (lowered.equals("player") || lowered.equals("players"))
			return event -> {
				if (event instanceof ScriptEvent.Damage damage)
					return damage.victim() instanceof ServerPlayerEntity;
				if (event instanceof ScriptEvent.Death death)
					return death.victim() instanceof ServerPlayerEntity;
				throw new AssertionError("Unexpected event for victim filter");
			};
		EntityType<?> type = AliasIndex.findEntityType(lowered);
		if (type == null) {
			SkriptLogger.warn("Unknown entity type in event filter: '" + filterSource + "'");
			return noFilter();
		}
		EntityType<?> resolved = type;
		return event -> {
			LivingEntity victim = null;
			if (event instanceof ScriptEvent.Damage damage)
				victim = damage.victim();
			else if (event instanceof ScriptEvent.Death death)
				victim = death.victim();
			return victim != null && victim.getType() == resolved;
		};
	}

	private static Predicate<ScriptEvent> blockFilter(String filterSource) {
		if (filterSource == null || filterSource.isBlank())
			return noFilter();
		ClassInfo<?> itemType =
				Classes.byClass(ItemType.class);
		Object parsed = itemType == null ? null : itemType.parse(filterSource);
		if (!(parsed instanceof ItemType wanted)) {
			SkriptLogger.warn("Unknown item in event filter: '" + filterSource + "'");
			return noFilter();
		}
		ItemType match = wanted;
		return event -> {
			if (event instanceof ScriptEvent.BlockBreak breakEvent) {
				Block block = Block.getBlockFromItem(match.item());
				return breakEvent.state().getBlock() == block;
			}
			return true;
		};
	}

	private static Predicate<ScriptEvent> placedFilter(String filterSource) {
		return blockPlacedFilter(filterSource);
	}

	private static Predicate<ScriptEvent> blockPlacedFilter(String filterSource) {
		if (filterSource == null || filterSource.isBlank())
			return noFilter();
		ClassInfo<?> itemType =
				Classes.byClass(ItemType.class);
		Object parsed = itemType == null ? null : itemType.parse(filterSource);
		if (!(parsed instanceof ItemType wanted))
			return noFilter();
		Block block = Block.getBlockFromItem(wanted.item());
		return event -> {
			if (event instanceof ScriptEvent.BlockPlace placeEvent)
				return placeEvent.placed().getBlock() == block;
			return true;
		};
	}

	private static EventHandler bind(Class<? extends ScriptEvent> type, String name,
			Predicate<ScriptEvent> filter) {
		return trigger -> EventDispatch.bind(type, filter, trigger);
	}
}
