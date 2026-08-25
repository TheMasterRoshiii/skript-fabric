package dev.me.master.skript.registrations;
import dev.me.master.skript.lang.ClassInfo;
import dev.me.master.skript.lang.Classes;
import dev.me.master.skript.lang.DelaySignal;
import dev.me.master.skript.lang.Effect;
import dev.me.master.skript.lang.EventExpression;
import dev.me.master.skript.lang.EventValues;
import dev.me.master.skript.lang.ExecContext;
import dev.me.master.skript.lang.Expression;
import dev.me.master.skript.lang.Flow;
import dev.me.master.skript.lang.Literal;
import dev.me.master.skript.lang.Parser;
import dev.me.master.skript.lang.Relation;
import dev.me.master.skript.lang.SkriptPattern;
import dev.me.master.skript.lang.SyntaxRegistry;
import dev.me.master.skript.lang.TriggerItem;
import dev.me.master.skript.lang.VariableExpression;
import dev.me.master.skript.lang.VariableString;
import dev.me.master.skript.types.BlockRef;
import dev.me.master.skript.types.CurrentServer;
import dev.me.master.skript.types.ItemType;
import dev.me.master.skript.types.WorldPos;
import dev.me.master.skript.util.SkriptLogger;
import dev.me.master.skript.util.TimeSpan;
import dev.me.master.skript.variables.Variables;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public final class DefaultEffects {

	private static final int MAX_SPAWN_COUNT = 64;

	private DefaultEffects() {
	}

	public static void register() {
		messaging();
		variables();
		items();
		entities();
		blocks();
		worldInteraction();
		controlFlow();
	}

	private static void messaging() {
		SyntaxRegistry.registerEffect(8,
				new String[] {"send [message[s]] %strings% to %players%",
						"send %players% [the] message[s] %strings%",
						"send [message[s]] %strings%"},
				(line, parser, pattern, match) -> {
					boolean hasTargets = pattern.slotCount() == 2;
					int messageSlot = hasTargets ? (pattern.source().startsWith("send %players%") ? 1 : 0) : 0;
					int targetSlot = 1 - messageSlot;
					Expression<?> messages = parser.parseExpression(match.slotInputs()[messageSlot], String.class, true);
					if (messages == null)
						return null;
					Expression<?> targets;
					if (hasTargets)
						targets = parser.parseExpression(match.slotInputs()[targetSlot], ServerPlayerEntity.class, true);
					else
						targets = eventPlayers();
					if (targets == null)
						return null;
					return new Messenger(messages, targets);
				});
		SyntaxRegistry.registerEffect(8,
				new String[] {"broadcast %strings% [on %world%]"},
				(line, parser, pattern, match) -> {
					Expression<?> messages = parser.parseExpression(match.slotInputs()[0], String.class, true);
					if (messages == null)
						return null;
					return new Effect(0) {
						@Override
						protected void run(ExecContext context) {
							MinecraftServer server = Support.server();
							if (server == null)
								return;
							for (Object message : messages.getObjectValues(context)) {
								Text text = Text.literal(VariableString.colorize(Classes.toStringValue(message)));
								server.getPlayerManager().broadcast(text, false);
							}
						}

						@Override
						public String toString() {
							return "broadcast " + messages;
						}
					};
				});
	}

	private static Expression<?> eventPlayers() {
		return new EventExpression<>(ServerPlayerEntity.class);
	}

	private static final class Messenger extends Effect {

		private final Expression<?> messages;
		private final Expression<?> targets;

		Messenger(Expression<?> messages, Expression<?> targets) {
			super(0);
			this.messages = messages;
			this.targets = targets;
		}

		@Override
		protected void run(ExecContext context) {
			StringBuilder rendered = new StringBuilder();
			List<Object> values = messages.getObjectValues(context);
			for (int i = 0; i < values.size(); i++) {
				if (i > 0)
					rendered.append(' ');
				rendered.append(Classes.toStringValue(values.get(i)));
			}
			String text = VariableString.colorize(rendered.toString());
			if (text.isEmpty())
				return;
			Text payload = Text.literal(text);
			for (Object candidate : targets.getObjectValues(context)) {
				if (candidate instanceof ServerPlayerEntity player)
					player.sendMessage(payload);
			}
		}

		@Override
		public String toString() {
			return "send " + messages + " to " + targets;
		}
	}

	private static void variables() {
		SyntaxRegistry.registerEffect(10,
				new String[] {"set %variable% to %objects%"},
				(line, parser, pattern, match) -> buildChange(parser, match, ChangeKind.SET));
		SyntaxRegistry.registerEffect(10,
				new String[] {"add %objects% to %variable%"},
				(line, parser, pattern, match) -> buildChange(parser, match, ChangeKind.ADD));
		SyntaxRegistry.registerEffect(10,
				new String[] {"remove %objects% from %variable%"},
				(line, parser, pattern, match) -> buildChange(parser, match, ChangeKind.REMOVE));
		SyntaxRegistry.registerEffect(10,
				new String[] {"delete %variable%", "clear %variable%"},
				(line, parser, pattern, match) -> {
					VariableExpression variable = parseVariable(parser, match.slotInputs()[0]);
					if (variable == null)
						return null;
					return new Effect(0) {
						@Override
						protected void run(ExecContext context) {
							deleteVariable(context, variable);
						}
					};
				});
	}

	private enum ChangeKind {
		SET,
		ADD,
		REMOVE
	}

	private static @Nullable TriggerItem.Statement buildChange(Parser parser, SkriptPattern.MatchResult match,
			ChangeKind kind) {
		boolean setForm = kind == ChangeKind.SET;
		String variableSource = match.slotInputs()[setForm ? 0 : 1];
		String valueSource = match.slotInputs()[setForm ? 1 : 0];
		VariableExpression variable = parseVariable(parser, variableSource);
		Expression<?> value = parser.parseExpression(valueSource, Object.class, true);
		if (variable == null || value == null)
			return null;
		if (setForm && !variable.isListAll()) {
			return new VariableSet(variable, value);
		}
		return new VariableChange(variable, value, kind);
	}

	private static final class VariableSet extends Effect {

		private final VariableExpression variable;
		private final Expression<?> value;

		VariableSet(VariableExpression variable, Expression<?> value) {
			super(0);
			this.variable = variable;
			this.value = value;
		}

		@Override
		protected void run(ExecContext context) {
			Object newValue = value.getObjectValue(context);
			if (variable.isLocal())
				context.setLocal(variable.storageKey(context), newValue);
			else
				Variables.set(variable.storageKey(context), newValue);
		}

		@Override
		public String toString() {
			return "set " + variable + " to " + value;
		}
	}

	private static final class VariableChange extends Effect {

		private final VariableExpression variable;
		private final Expression<?> value;
		private final ChangeKind kind;

		VariableChange(VariableExpression variable, Expression<?> value, ChangeKind kind) {
			super(0);
			this.variable = variable;
			this.value = value;
			this.kind = kind;
		}

		@Override
		protected void run(ExecContext context) {
			List<Object> values = value.getObjectValues(context);
			if (kind == ChangeKind.ADD && variable.isListAll()) {
				writeListEntries(context, values);
				return;
			}
			if (kind == ChangeKind.REMOVE && variable.isListAll()) {
				removeFromList(context, values);
				return;
			}
			Object current = readCurrent(context);
			Object updated = current;
			switch (kind) {
				case SET -> {
					if (!values.isEmpty())
						updated = values.getFirst();
				}
				case ADD -> {
					for (Object entry : values)
						updated = Support.Arithmetic.add(updated, entry);
				}
				case REMOVE -> {
					for (Object entry : values)
						updated = Support.Arithmetic.subtract(updated, entry);
				}
			}
			writeCurrent(context, updated);
		}

		private Object readCurrent(ExecContext context) {
			return variable.isLocal()
					? context.getLocal(variable.storageKey(context))
					: Variables.get(variable.storageKey(context));
		}

		private void writeCurrent(ExecContext context, Object updated) {
			if (variable.isLocal())
				context.setLocal(variable.storageKey(context), updated);
			else
				Variables.set(variable.storageKey(context), updated);
		}

		private void writeListEntries(ExecContext context, List<Object> values) {
			if (variable.isLocal()) {
				for (Object entry : values)
					context.setLocal(variable.keyPrefix() + nextLocalIndex(context, variable.keyPrefix()), entry);
				return;
			}
			for (Object entry : values)
				Variables.addToList(variable.name(), entry);
		}

		private void removeFromList(ExecContext context, List<Object> values) {
			List<String> keys = variable.keys(context);
			List<String> doomed = new java.util.ArrayList<>();
			for (String key : keys) {
				Object existing = variable.isLocal()
						? context.getLocal(key)
						: Variables.get(key);
				if (existing == null)
					continue;
				boolean matched = false;
				for (Object wanted : values) {
					Relation relation = Classes.compare(existing, wanted);
					if (relation == Relation.EQUAL || Objects.equals(existing, wanted)) {
						matched = true;
						break;
					}
				}
				if (matched)
					doomed.add(key);
			}
			for (String key : doomed) {
				if (variable.isLocal())
					context.setLocal(key, null);
				else
					Variables.delete(key);
			}
		}

		private int nextLocalIndex(ExecContext context, String prefix) {
			int max = 0;
			for (String key : context.localNamesMatching(prefix)) {
				try {
					max = Math.max(max, Integer.parseInt(key.substring(key.lastIndexOf("::") + 2)));
				} catch (NumberFormatException ignored) {
				}
			}
			return max + 1;
		}

		@Override
		public String toString() {
			return kind.name().toLowerCase(Locale.ROOT) + " " + value + (kind == ChangeKind.ADD ? " to " : " from ")
					+ variable;
		}
	}

	private static @Nullable VariableExpression parseVariable(Parser parser, String source) {
		VariableExpression variable = parser.parseVariable(source);
		if (variable == null)
			SkriptLogger.warn("Expected a variable like {name}, got: '" + source + "'");
		return variable;
	}

	private static void deleteVariable(ExecContext context, VariableExpression variable) {
		if (variable.isListAll()) {
			for (String key : variable.keys(context)) {
				if (variable.isLocal())
					context.setLocal(key, null);
				else
					Variables.delete(key);
			}
			if (!variable.isLocal())
				Variables.removeList(variable.name());
			return;
		}
		if (variable.isLocal())
			context.setLocal(variable.storageKey(context), null);
		else
			Variables.delete(variable.storageKey(context));
	}

	private static void items() {
		SyntaxRegistry.registerEffect(9,
				new String[] {"give %itemtypes% to %player%", "give %itemtype% to %player%"},
				(line, parser, pattern, match) -> {
					Expression<?> items = parser.parseLiteral(match.slotInputs()[0], ItemType.class);
					Expression<?> players = parser.parseExpression(match.slotInputs()[1], ServerPlayerEntity.class, true);
					if (items == null || players == null)
						return null;
					return new Giver(items, players);
				});
		SyntaxRegistry.registerEffect(9,
				new String[] {"take %itemtypes% from %player%", "take %itemtype% from %player%"},
				(line, parser, pattern, match) -> {
					Expression<?> items = parser.parseLiteral(match.slotInputs()[0], ItemType.class);
					Expression<?> players = parser.parseExpression(match.slotInputs()[1], ServerPlayerEntity.class, true);
					if (items == null || players == null)
						return null;
					return new Taker(items, players);
				});
	}

	private static final class Giver extends Effect {

		private final Expression<?> items;
		private final Expression<?> players;

		Giver(Expression<?> items, Expression<?> players) {
			super(0);
			this.items = items;
			this.players = players;
		}

		@Override
		protected void run(ExecContext context) {
			for (Object candidate : players.getObjectValues(context)) {
				if (!(candidate instanceof ServerPlayerEntity player))
					continue;
				for (Object itemCandidate : items.getObjectValues(context)) {
					ItemType itemType = itemCandidate instanceof ItemType typed ? typed : null;
					if (itemType == null || itemType.isAir())
						continue;
					ItemStack stack = itemType.createStack();
					while (!stack.isEmpty()) {
						int before = stack.getCount();
						player.getInventory().insertStack(stack);
						if (stack.getCount() >= before)
							break;
					}
				}
			}
		}
	}

	private static final class Taker extends Effect {

		private final Expression<?> items;
		private final Expression<?> players;

		Taker(Expression<?> items, Expression<?> players) {
			super(0);
			this.items = items;
			this.players = players;
		}

		@Override
		protected void run(ExecContext context) {
			for (Object candidate : players.getObjectValues(context)) {
				if (!(candidate instanceof ServerPlayerEntity player))
					continue;
				for (Object itemCandidate : items.getObjectValues(context)) {
					ItemType itemType = itemCandidate instanceof ItemType typed ? typed : null;
					if (itemType == null)
						continue;
					int remaining = itemType.count();
					PlayerInventory inventory = player.getInventory();
					for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
						ItemStack stack = inventory.getStack(slot);
						if (stack.getItem() != itemType.item())
							continue;
						int taken = Math.min(stack.getCount(), remaining);
						stack.decrement(taken);
						remaining -= taken;
					}
				}
			}
		}
	}

	private static void entities() {
		SyntaxRegistry.registerEffect(9,
				new String[] {"teleport %entity% to %worldpos%"},
				(line, parser, pattern, match) -> {
					Expression<?> entities = parser.parseExpression(match.slotInputs()[0], Entity.class, true);
					Expression<?> location = parser.parseExpression(match.slotInputs()[1], WorldPos.class, false);
					if (entities == null || location == null)
						return null;
					return new Effect(0) {
						@Override
						protected void run(ExecContext context) {
							WorldPos destination = (WorldPos) location.getObjectValue(context);
							if (destination == null)
								return;
							for (Object candidate : entities.getObjectValues(context)) {
								if (!(candidate instanceof Entity entity) || !entity.isAlive())
									continue;
								entity.teleport(destination.world(), destination.x(), destination.y(),
										destination.z(), java.util.Set.of(), destination.yaw(), destination.pitch());
							}
						}
					};
				});
		SyntaxRegistry.registerEffect(9,
				new String[] {"kill %livingentities%", "kill %entity%"},
				(line, parser, pattern, match) -> {
					Expression<?> entities = parser.parseExpression(match.slotInputs()[0], LivingEntity.class, true);
					if (entities == null)
						return null;
					return new Effect(0) {
						@Override
						protected void run(ExecContext context) {
							for (Object candidate : entities.getObjectValues(context)) {
								if (candidate instanceof LivingEntity living && living.isAlive())
									living.kill();
							}
						}
					};
				});
		SyntaxRegistry.registerEffect(8,
				new String[] {"spawn %number% %entitytype% [%worldpos%]",
						"spawn %entitytype% [%worldpos%]"},
				(line, parser, pattern, match) -> {
					boolean hasAmount = match.slotInputs().length == 3;
					int amountIndex = 0;
					int typeIndex = hasAmount ? 1 : 0;
					int positionIndex = hasAmount ? 2 : 1;
					Expression<?> amount = hasAmount
							? parser.parseExpression(match.slotInputs()[amountIndex], Number.class, false)
							: null;
					Expression<?> type = parser.parseLiteral(match.slotInputs()[typeIndex], EntityType.class);
					if (!(type instanceof Literal<?> literal)
							|| !(literal.value() instanceof EntityType<?> entityType))
						return null;
					String positionSource = match.slotInputs()[positionIndex];
					Expression<?> position = positionSource.isBlank()
							? null
							: parser.parseExpression(positionSource, WorldPos.class, false);
					return new Spawner(amount, entityType, position);
				});
		SyntaxRegistry.registerEffect(8,
				new String[] {"heal %livingentities%"},
				(line, parser, pattern, match) -> {
					Expression<?> entities = parser.parseExpression(match.slotInputs()[0], LivingEntity.class, true);
					if (entities == null)
						return null;
					return new Effect(0) {
						@Override
						protected void run(ExecContext context) {
							for (Object candidate : entities.getObjectValues(context)) {
								if (candidate instanceof LivingEntity living && living.isAlive())
									living.heal(living.getMaxHealth());
							}
						}
					};
				});
		SyntaxRegistry.registerEffect(8,
				new String[] {"damage %livingentities% by %number%"},
				(line, parser, pattern, match) -> {
					Expression<?> entities = parser.parseExpression(match.slotInputs()[0], LivingEntity.class, true);
					Expression<?> amount = parser.parseExpression(match.slotInputs()[1], Number.class, false);
					if (entities == null || amount == null)
						return null;
					return new Effect(0) {
						@Override
						protected void run(ExecContext context) {
							Number damageAmount = (Number) amount.getObjectValue(context);
							if (damageAmount == null || damageAmount.floatValue() <= 0)
								return;
							for (Object candidate : entities.getObjectValues(context)) {
								if (!(candidate instanceof LivingEntity living) || !living.isAlive())
									continue;
								living.damage(living.getDamageSources().generic(), damageAmount.floatValue());
							}
						}
					};
				});
	}

	private static final class Spawner extends Effect {

		@Nullable
		private final Expression<?> amount;
		private final EntityType<?> type;
		@Nullable
		private final Expression<?> position;

		Spawner(@Nullable Expression<?> amount, EntityType<?> type, @Nullable Expression<?> position) {
			super(0);
			this.amount = amount;
			this.type = type;
			this.position = position;
		}

		@Override
		protected void run(ExecContext context) {
			WorldPos at = resolveSpawnPosition(context, position);
			if (at == null)
				return;
			int count = 1;
			if (amount != null) {
				Number parsed = (Number) amount.getObjectValue(context);
				count = parsed == null ? 1 : Math.max(1, Math.min(MAX_SPAWN_COUNT, parsed.intValue()));
			}
			for (int i = 0; i < count; i++) {
				Entity spawned = type.create(at.world());
				if (spawned == null)
					continue;
				spawned.setPosition(at.x(), at.y(), at.z());
				spawned.setYaw(at.yaw());
				spawned.setPitch(at.pitch());
				at.world().spawnEntity(spawned);
			}
		}

		private WorldPos resolveSpawnPosition(ExecContext context, @Nullable Expression<?> positionExpression) {
			if (positionExpression != null)
				return (WorldPos) positionExpression.getObjectValue(context);
			ServerWorld world = Support.eventWorld(context);
			BlockPos anchor = context.hasEvent()
					? EventValues.get(context.event(), BlockPos.class)
					: null;
			if (anchor == null)
				anchor = BlockPos.ofFloored(0, 64, 0);
			if (world == null) {
				ServerWorld fallback = CurrentServer.overworld();
				if (fallback == null)
					return null;
				world = fallback;
			}
			return new WorldPos(world, anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, 0f, 0f);
		}
	}

	private static void blocks() {
		SyntaxRegistry.registerEffect(8,
				new String[] {"set block at %blockref% to %block%", "set block at %blockref% to %itemtype%"},
				(line, parser, pattern, match) -> {
					Expression<?> blockRef = parser.parseExpression(match.slotInputs()[0], BlockRef.class, false);
					if (blockRef == null)
						return null;
					String materialSource = match.slotInputs()[1];
					Object materialValue = null;
					ClassInfo<?> blockType =
							Classes.byClass(Block.class);
					if (blockType != null)
						materialValue = blockType.parse(materialSource);
					if (materialValue == null) {
						ItemType itemType = parseItemType(materialSource);
						if (itemType != null)
							materialValue = Block.getBlockFromItem(itemType.item());
					}
					if (!(materialValue instanceof Block targetBlock)) {
						SkriptLogger.warn("Can't resolve block material: '" + materialSource + "'");
						return null;
					}
					Block resolved = targetBlock;
					return new Effect(0) {
						@Override
						protected void run(ExecContext context) {
							BlockRef reference = (BlockRef) blockRef.getObjectValue(context);
							if (reference == null)
								return;
							reference.world().setBlockState(reference.pos(), resolved.getDefaultState());
						}
					};
				});
		SyntaxRegistry.registerEffect(8,
				new String[] {"clear block[s] at %blockref%", "delete block[s] at %blockref%"},
				(line, parser, pattern, match) -> {
					Expression<?> blockRef = parser.parseExpression(match.slotInputs()[0], BlockRef.class, false);
					if (blockRef == null)
						return null;
					return new Effect(0) {
						@Override
						protected void run(ExecContext context) {
							BlockRef reference = (BlockRef) blockRef.getObjectValue(context);
							if (reference == null)
								return;
							reference.world().setBlockState(reference.pos(), Blocks.AIR.getDefaultState());
						}
					};
				});
	}

	private static @Nullable ItemType parseItemType(String source) {
		ClassInfo<?> itemType =
				Classes.byClass(ItemType.class);
		return itemType == null ? null : (ItemType) itemType.parse(source);
	}

	private static void worldInteraction() {
		SyntaxRegistry.registerEffect(7,
				new String[] {"make %player% execute [the] command %string%"},
				(line, parser, pattern, match) -> {
					Expression<?> player = parser.parseExpression(match.slotInputs()[0], ServerPlayerEntity.class, false);
					Expression<?> command = parser.parseExpression(match.slotInputs()[1], String.class, false);
					if (player == null || command == null)
						return null;
					return new CommandRunner(player, command, false);
				});
		SyntaxRegistry.registerEffect(7,
				new String[] {"execute console command %string%",
						"make console execute [the] command %string%"},
				(line, parser, pattern, match) -> {
					Expression<?> command = parser.parseExpression(match.slotInputs()[0], String.class, false);
					if (command == null)
						return null;
					return new CommandRunner(null, command, true);
				});
		SyntaxRegistry.registerEffect(6,
				new String[] {"kick %player%"},
				(line, parser, pattern, match) -> {
					Expression<?> players = parser.parseExpression(match.slotInputs()[0], ServerPlayerEntity.class, true);
					if (players == null)
						return null;
					return new Effect(0) {
						@Override
						protected void run(ExecContext context) {
							for (Object candidate : players.getObjectValues(context)) {
								if (!(candidate instanceof ServerPlayerEntity player))
									continue;
								player.networkHandler.connection.disconnect(Text.literal("Kicked by an operator."));
							}
						}
					};
				});
		SyntaxRegistry.registerEffect(6,
				new String[] {"play sound %string% at %worldpos%"},
				(line, parser, pattern, match) -> {
					Expression<?> soundName = parser.parseExpression(match.slotInputs()[0], String.class, false);
					Expression<?> location = parser.parseExpression(match.slotInputs()[1], WorldPos.class, false);
					if (soundName == null || location == null)
						return null;
					return new Effect(0) {
						@Override
						protected void run(ExecContext context) {
							WorldPos at = (WorldPos) location.getObjectValue(context);
							if (at == null)
								return;
							String raw = Classes.toStringValue(soundName.getObjectValue(context))
									.toLowerCase(Locale.ROOT).replace(' ', '_');
							Identifier id = Identifier.of(raw.contains(":") ? raw : "minecraft:" + raw);
							SoundEvent sound = SoundEvent.of(id);
							at.world().playSound(null, at.x(), at.y(), at.z(),
									RegistryEntry.of(sound), SoundCategory.MASTER, 1f, 1f, 0L);
						}
					};
				});
	}

	private static final class CommandRunner extends Effect {

		@Nullable
		private final Expression<?> player;
		private final Expression<?> command;
		private final boolean console;

		CommandRunner(@Nullable Expression<?> player, Expression<?> command, boolean console) {
			super(0);
			this.player = player;
			this.command = command;
			this.console = console;
		}

		@Override
		protected void run(ExecContext context) {
			MinecraftServer server = Support.server();
			if (server == null)
				return;
			String commandLine = Classes.toStringValue(command.getObjectValue(context));
			if (commandLine.startsWith("/"))
				commandLine = commandLine.substring(1);
			if (console) {
				server.getCommandManager().executeWithPrefix(server.getCommandSource(), "/" + commandLine);
				return;
			}
			Object candidate = player == null ? null : player.getObjectValue(context);
			if (candidate instanceof ServerPlayerEntity sender)
				server.getCommandManager().executeWithPrefix(sender.getCommandSource(), "/" + commandLine);
		}
	}

	private static void controlFlow() {
		SyntaxRegistry.registerEffect(11,
				new String[] {"wait %timespan%"},
				(line, parser, pattern, match) -> {
					Expression<?> duration = parser.parseExpression(match.slotInputs()[0], TimeSpan.class, false);
					if (duration == null)
						return null;
					Expression<?> waitSource = duration;
					return new TriggerItem.Statement(line) {
						@Override
						protected Flow execute(ExecContext context) {
							Object value = waitSource.getObjectValue(context);
							long ticks = 1;
							if (value instanceof TimeSpan span)
								ticks = span.ticks();
							else if (value instanceof Number number)
								ticks = number.longValue();
							throw DelaySignal.of(Math.max(1, ticks));
						}
					};
				});
		SyntaxRegistry.registerEffect(11,
				new String[] {"stop [the] trigger", "stop"},
				(line, parser, pattern, match) -> new FlowEffect(Flow.STOP_TRIGGER));
		SyntaxRegistry.registerEffect(11,
				new String[] {"exit [the|a|this] loop"},
				(line, parser, pattern, match) -> new FlowEffect(Flow.EXIT_LOOP));
		SyntaxRegistry.registerEffect(11,
				new String[] {"exit [the|a|this] section"},
				(line, parser, pattern, match) -> new FlowEffect(Flow.EXIT_SECTION));
		SyntaxRegistry.registerEffect(12,
				new String[] {"cancel [the] event"},
				(line, parser, pattern, match) -> new TriggerItem.Statement(line) {
					@Override
					protected Flow execute(ExecContext context) {
						context.cancel();
						return Flow.NORMAL;
					}
				});
		SyntaxRegistry.registerEffect(12,
				new String[] {"return %objects%"},
				(line, parser, pattern, match) -> {
					Expression<?> value = parser.parseExpression(match.slotInputs()[0], Object.class, true);
					if (value == null)
						return null;
					return new ReturnEffect(value);
				});
	}

	private static final class FlowEffect extends TriggerItem.Statement {

		private final Flow flow;

		FlowEffect(Flow flow) {
			super(0);
			this.flow = flow;
		}

		@Override
		protected Flow execute(ExecContext context) {
			return flow;
		}
	}

	private static final class ReturnEffect extends TriggerItem.Statement {

		private final Expression<?> value;

		ReturnEffect(Expression<?> value) {
			super(0);
			this.value = value;
		}

		@Override
		protected Flow execute(ExecContext context) {
			context.setReturnValue(value.getObjectValue(context));
			return Flow.RETURN;
		}
	}
}
