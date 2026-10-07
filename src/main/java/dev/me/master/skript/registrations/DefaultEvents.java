package dev.me.master.skript.registrations;
import dev.me.master.skript.config.SkriptConfig;
import dev.me.master.skript.events.EventDispatch;
import dev.me.master.skript.events.EventHandler;
import dev.me.master.skript.events.ScriptEvent;
import dev.me.master.skript.lang.ClassInfo;
import dev.me.master.skript.lang.Classes;
import dev.me.master.skript.lang.Parser;
import dev.me.master.skript.lang.Trigger;
import dev.me.master.skript.types.AliasIndex;
import dev.me.master.skript.types.ItemType;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BlockItem;
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
		EventRegistry.register("(item )?consum(e|ing)", (name, parser) -> {
			if (!SkriptConfig.INSTANCE.itemConsumeEnabled) {
				parser.state.errorAt(name, "Event '" + name + "' is disabled by patches.itemconsume=false in "
						+ SkriptConfig.INSTANCE.baseDir.resolve("config.properties").toAbsolutePath().normalize()
						+ "; set patches.itemconsume=true and restart the server");
				return null;
			}
			return bind(ScriptEvent.ItemConsume.class, name, noFilter());
		});
		EventRegistry.register("(entity|player)? ?damage( of (?<of>.+))?", (name, parser) -> {
			Predicate<ScriptEvent> filter = victimFilter(group(name, "of"), parser);
			return filter == null ? null : bind(ScriptEvent.Damage.class, name, filter);
		});
		EventRegistry.register("(entity |player )?death( of (?<of>.+))?", (name, parser) -> {
			Predicate<ScriptEvent> filter = victimFilter(group(name, "of"), parser);
			return filter == null ? null : bind(ScriptEvent.Death.class, name, filter);
		});
		EventRegistry.register("(block ?)?(break|min(e|ing))( of (?<of>.+))?", (name, parser) -> {
			Predicate<ScriptEvent> filter = blockFilter(group(name, "of"), parser, true);
			return filter == null ? null : bind(ScriptEvent.BlockBreak.class, name, filter);
		});
		EventRegistry.register("(block ?)?place(ment)?( of (?<of>.+))?", (name, parser) -> {
			Predicate<ScriptEvent> filter = blockFilter(group(name, "of"), parser, false);
			return filter == null ? null : bind(ScriptEvent.BlockPlace.class, name, filter);
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
        additionalEvents();
	}

    private static void additionalEvents() {
        EventRegistry.register("(item )?use( of (?<of>.+))?", (name, parser) -> {
            String source = group(name, "of");
            if (source == null) {
                return bind(ScriptEvent.ItemUse.class, name, noFilter());
            }
            ClassInfo<?> info = Classes.byClass(ItemType.class);
            Object parsed = info == null ? null : info.parse(source);
            if (!(parsed instanceof ItemType item)) {
                parser.state.errorAt(source, "Unknown item '" + source + "'; expected a registered item name");
                return null;
            }
            return bind(ScriptEvent.ItemUse.class, name,
                    event -> ((ScriptEvent.ItemUse) event).item().isOf(item.item()));
        });
        EventRegistry.register("totem (pop|use)|resurrect(ion)?", (name, parser) -> {
            if (!SkriptConfig.INSTANCE.totemPopEnabled) {
                parser.state.errorAt(name, "Event '" + name + "' is disabled by patches.totempop=false in "
                        + SkriptConfig.INSTANCE.baseDir.resolve("config.properties").toAbsolutePath().normalize()
                        + "; set patches.totempop=true and restart the server");
                return null;
            }
            return bind(ScriptEvent.TotemPop.class, name, noFilter());
        });
        EventRegistry.register("(player |entity )?equipment change", (name, parser) -> {
            boolean playerOnly = isPlayerOnly(name);
            return bind(ScriptEvent.EquipmentChange.class, name, event -> !playerOnly
                    || ((ScriptEvent.EquipmentChange) event).entity() instanceof ServerPlayerEntity);
        });
        EventRegistry.register("(player )?(bed enter|sleep|sleeping)", (name, parser) ->
                bind(ScriptEvent.SleepStart.class, name, noFilter()));
        EventRegistry.register("(player )?(bed leave|wake|waking)", (name, parser) ->
                bind(ScriptEvent.SleepStop.class, name, noFilter()));
        EventRegistry.register("(player |entity )?(world|dimension) change", (name, parser) -> {
            boolean playerOnly = isPlayerOnly(name);
            return bind(ScriptEvent.WorldChange.class, name, event -> !playerOnly
                    || ((ScriptEvent.WorldChange) event).entity() instanceof ServerPlayerEntity);
        });
        EventRegistry.register("entity load( of (?<of>.+))?", (name, parser) ->
                entityLifecycle(name, parser, true));
        EventRegistry.register("entity unload( of (?<of>.+))?", (name, parser) ->
                entityLifecycle(name, parser, false));
        EventRegistry.register("server start(ing)?", (name, parser) ->
                bind(ScriptEvent.ServerStart.class, name, noFilter()));
        EventRegistry.register("server stop(ping)?", (name, parser) ->
                bind(ScriptEvent.ServerStop.class, name, noFilter()));
    }

    private static boolean isPlayerOnly(String name) {
        return name.toLowerCase(Locale.ROOT).startsWith("player ");
    }

    private static EventHandler entityLifecycle(String name, Parser parser, boolean loading) {
        Class<? extends ScriptEvent> eventType = loading
                ? ScriptEvent.EntityLoad.class : ScriptEvent.EntityUnload.class;
        String source = group(name, "of");
        if (source == null) {
            return bind(eventType, name, noFilter());
        }
        EntityType<?> type = AliasIndex.findEntityType(source);
        if (type == null) {
            parser.state.errorAt(source, "Unknown entity type '" + source + "'; expected a registered entity name");
            return null;
        }
        return bind(eventType, name, event -> loading
                ? ((ScriptEvent.EntityLoad) event).entity().getType() == type
                : ((ScriptEvent.EntityUnload) event).entity().getType() == type);
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

    private static Predicate<ScriptEvent> victimFilter(String filterSource, Parser parser) {
        if (filterSource == null || filterSource.isBlank()) {
            return noFilter();
        }
        String lowered = filterSource.trim().toLowerCase(Locale.ROOT).replace('_', ' ');
        if (lowered.equals("player") || lowered.equals("players")) {
            return event -> {
                if (event instanceof ScriptEvent.Damage damage) {
                    return damage.victim() instanceof ServerPlayerEntity;
                }
                return ((ScriptEvent.Death) event).victim() instanceof ServerPlayerEntity;
            };
        }
        EntityType<?> type = AliasIndex.findEntityType(lowered);
        if (type == null) {
            parser.state.errorAt(filterSource,
                    "Unknown entity type '" + filterSource + "'; expected a registered entity name");
            return null;
        }
        return event -> {
            LivingEntity victim = event instanceof ScriptEvent.Damage damage
                    ? damage.victim() : ((ScriptEvent.Death) event).victim();
            return victim.getType() == type;
        };
    }

    private static Predicate<ScriptEvent> blockFilter(String filterSource, Parser parser, boolean breaking) {
        if (filterSource == null || filterSource.isBlank()) {
            return noFilter();
        }
        ClassInfo<?> itemType = Classes.byClass(ItemType.class);
        Object parsed = itemType == null ? null : itemType.parse(filterSource);
        if (!(parsed instanceof ItemType wanted)) {
            parser.state.errorAt(filterSource,
                    "Unknown block '" + filterSource + "'; expected a registered block name");
            return null;
        }
        if (!(wanted.item() instanceof BlockItem item)) {
            parser.state.errorAt(filterSource,
                    "Item '" + filterSource + "' cannot be placed as a block; expected a block item");
            return null;
        }
        Block block = item.getBlock();
        return event -> breaking
                ? ((ScriptEvent.BlockBreak) event).state().getBlock() == block
                : ((ScriptEvent.BlockPlace) event).placed().getBlock() == block;
    }

	private static EventHandler bind(Class<? extends ScriptEvent> type, String name,
			Predicate<ScriptEvent> filter) {
        return new EventHandler() {
            @Override
            public void bind(Trigger trigger) {
                EventDispatch.bind(type, filter, trigger);
            }

            @Override
            public boolean canCancel() {
                return ScriptEvent.canCancel(type);
            }
        };
	}
}
