package dev.me.master.skript.registrations;
import dev.me.master.skript.events.ScriptEvent;
import dev.me.master.skript.lang.Classes;
import dev.me.master.skript.lang.EventExpression;
import dev.me.master.skript.lang.EventValues;
import dev.me.master.skript.lang.ExecContext;
import dev.me.master.skript.lang.Expression;
import dev.me.master.skript.lang.Literal;
import dev.me.master.skript.lang.LoopSection;
import dev.me.master.skript.lang.LoopState;
import dev.me.master.skript.lang.MultiExpression;
import dev.me.master.skript.lang.Parser;
import dev.me.master.skript.lang.SingleExpression;
import dev.me.master.skript.lang.SyntaxRegistry;
import dev.me.master.skript.types.BlockRef;
import dev.me.master.skript.types.CurrentServer;
import dev.me.master.skript.types.ItemType;
import dev.me.master.skript.types.WorldPos;
import dev.me.master.skript.util.TimeSpan;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

public final class DefaultExpressions {

	private DefaultExpressions() {
	}

	public static void register() {
		event();
		entityAccessors();
		positionAndWorld();
		numeric();
		commandArgs();
		looping();
		items();
	}

	private static void event() {
		SyntaxRegistry.registerExpression(10, "player[s]|the player[s]", (parser, pattern, match) ->
				new EventExpression<>(ServerPlayerEntity.class));
		SyntaxRegistry.registerExpression(10, "attacker", (parser, pattern, match) ->
				new EventExpression<>(Entity.class));
		SyntaxRegistry.registerExpression(10, "victim", (parser, pattern, match) ->
				new EventExpression<>(LivingEntity.class));
		SyntaxRegistry.registerExpression(9, "[the] [chat] message", (parser, pattern, match) ->
				new EventExpression<>(String.class));
		SyntaxRegistry.registerExpression(9, "[the] (damage|damage dealt)", (parser, pattern, match) ->
				new EventExpression<>(Number.class));
		SyntaxRegistry.registerExpression(9, "[the] (full |whole )?command", (parser, pattern, match) ->
				new EventExpression<>(String.class));
		SyntaxRegistry.registerExpression(9, "event[-]block|the event[-]block|clicked block|broken block|placed block|bed",
				(parser, pattern, match) -> new EventExpression<>(BlockRef.class));
		SyntaxRegistry.registerExpression(9,
                "event[-]item|the event[-]item|consumed item|the consumed item|used item|the used item|totem",
				(parser, pattern, match) -> new EventExpression<>(ItemType.class));
		SyntaxRegistry.registerExpression(9,
				"(name of|the name of) (event[-]item|the event[-]item|consumed item|the consumed item|used item|totem)",
				(parser, pattern, match) -> new SingleExpression<String>(String.class) {
					@Override
					protected @Nullable String compute(ExecContext context) {
						if (!context.hasEvent()) {
							return null;
						}
						ItemStack item = EventValues.get(context.event(), ItemStack.class);
						return item == null ? null : item.getName().getString();
					}
				});
		SyntaxRegistry.registerExpression(8, "world|the world", (parser, pattern, match) ->
				new EventExpression<>(ServerWorld.class));
        SyntaxRegistry.registerExpression(9, "event[-]entity|the event[-]entity", (parser, pattern, match) ->
                new EventExpression<>(Entity.class));
        SyntaxRegistry.registerExpression(9, "equipment slot|the equipment slot", (parser, pattern, match) ->
                new EventExpression<>(String.class));
        SyntaxRegistry.registerExpression(9, "previous world|the previous world", (parser, pattern, match) ->
                new SingleExpression<ServerWorld>(ServerWorld.class) {
                    @Override
                    protected @Nullable ServerWorld compute(ExecContext context) {
                        return context.event() instanceof ScriptEvent.WorldChange event ? event.previousWorld() : null;
                    }
                });
        SyntaxRegistry.registerExpression(9, "previous item|the previous item", (parser, pattern, match) ->
                new SingleExpression<ItemType>(ItemType.class) {
                    @Override
                    protected @Nullable ItemType compute(ExecContext context) {
                        return context.event() instanceof ScriptEvent.EquipmentChange event
                                ? ItemType.of(event.previousItem().getItem(), 1) : null;
                    }
                });

		SyntaxRegistry.registerExpression(7, "all [of the] players", (parser, pattern, match) ->
				new MultiExpression<ServerPlayerEntity>(ServerPlayerEntity.class) {
					@Override
					public List<ServerPlayerEntity> getValues(ExecContext context) {
						MinecraftServer server = CurrentServerHolder.get();
						return server == null ? List.of() : List.copyOf(server.getPlayerManager().getPlayerList());
					}

					@Override
					public String toString() {
						return "all players";
					}
				});
		SyntaxRegistry.registerExpression(7, "all [of the] worlds", (parser, pattern, match) ->
				new MultiExpression<ServerWorld>(ServerWorld.class) {
					@Override
					public List<ServerWorld> getValues(ExecContext context) {
						MinecraftServer server = CurrentServerHolder.get();
						if (server == null)
							return List.of();
						List<ServerWorld> worlds = new ArrayList<>();
						for (ServerWorld world : server.getWorlds())
							worlds.add(world);
						return List.copyOf(worlds);
					}

					@Override
					public String toString() {
						return "all worlds";
					}
				});
	}

	private static void entityAccessors() {
		SyntaxRegistry.registerExpression(6, "health of %entity%", (parser, pattern, match) -> {
			Expression<?> source = parser.parseExpression(match.slotInputs()[0], LivingEntity.class, false);
			return source == null ? null : new SingleExpression<Number>(Number.class) {
				@Override
				protected @Nullable Number compute(ExecContext context) {
					LivingEntity entity = (LivingEntity) converted(context, source, LivingEntity.class);
					return entity == null ? null : entity.getHealth();
				}

				@Override
				public String toString() {
					return "health of " + source;
				}
			};
		});
		SyntaxRegistry.registerExpression(6, "uuid of %entity%", (parser, pattern, match) -> {
			Expression<?> source = parser.parseExpression(match.slotInputs()[0], Entity.class, false);
			return source == null ? null : new SingleExpression<String>(String.class) {
				@Override
				protected @Nullable String compute(ExecContext context) {
					Entity entity = (Entity) converted(context, source, Entity.class);
					return entity == null ? null : entity.getUuidAsString();
				}

				@Override
				public String toString() {
					return "uuid of " + source;
				}
			};
		});
		SyntaxRegistry.registerExpression(6, "name of %entity%", (parser, pattern, match) -> {
			Expression<?> source = parser.parseExpression(match.slotInputs()[0], Entity.class, false);
			return source == null ? null : new SingleExpression<String>(String.class) {
				@Override
				protected @Nullable String compute(ExecContext context) {
					Object resolved = converted(context, source, Entity.class);
					if (resolved instanceof Entity entity)
						return entity.getName().getString();
					return String.valueOf(resolved);
				}

				@Override
				public String toString() {
					return "name of " + source;
				}
			};
		});
		SyntaxRegistry.registerExpression(6, "[the] held item of %player%", (parser, pattern, match) -> {
			Expression<?> source = parser.parseExpression(match.slotInputs()[0], ServerPlayerEntity.class, false);
			return source == null ? null : new SingleExpression<ItemStack>(ItemStack.class) {
				@Override
				protected @Nullable ItemStack compute(ExecContext context) {
					ServerPlayerEntity player = (ServerPlayerEntity) converted(context, source, ServerPlayerEntity.class);
					return player == null ? null : player.getMainHandStack();
				}

				@Override
				public String toString() {
					return "held item of " + source;
				}
			};
		});
		SyntaxRegistry.registerExpression(6, "location of %entity%", (parser, pattern, match) -> {
			Expression<?> source = parser.parseExpression(match.slotInputs()[0], Entity.class, false);
			return source == null ? null : new SingleExpression<WorldPos>(WorldPos.class) {
				@Override
				protected @Nullable WorldPos compute(ExecContext context) {
					Entity entity = (Entity) converted(context, source, Entity.class);
					if (entity == null)
						return null;
					return new WorldPos((ServerWorld) entity.getWorld(), entity.getX(), entity.getY(),
							entity.getZ(), entity.getYaw(), entity.getPitch());
				}

				@Override
				public String toString() {
					return "location of " + source;
				}
			};
		});
	}

	private static void positionAndWorld() {
		SyntaxRegistry.registerExpression(5, "[the] (position|location) at %number%, %number%, %number% [[in] %world%]",
				(parser, pattern, match) -> {
					Expression<?> x = parser.parseExpression(match.slotInputs()[0], Number.class, false);
					Expression<?> y = parser.parseExpression(match.slotInputs()[1], Number.class, false);
					Expression<?> z = parser.parseExpression(match.slotInputs()[2], Number.class, false);
					String worldSource = match.slotInputs().length > 3 ? match.slotInputs()[3] : null;
					Expression<?> world = worldSource == null || worldSource.isBlank()
							? null
							: parser.parseExpression(worldSource, ServerWorld.class, false);
					if (x == null || y == null || z == null)
						return null;
					return new SingleExpression<WorldPos>(WorldPos.class) {
						@Override
						protected @Nullable WorldPos compute(ExecContext context) {
							ServerWorld targetWorld = world == null
									? CurrentServer.overworld()
									: (ServerWorld) converted(context, world, ServerWorld.class);
							if (targetWorld == null && context.hasEvent())
								targetWorld = EventValues.get(context.event(), ServerWorld.class);
							if (targetWorld == null)
								return null;
							return new WorldPos(targetWorld,
									doubleOf(x, context, 0), doubleOf(y, context, 0), doubleOf(z, context, 0),
									0f, 0f);
						}

						@Override
						public String toString() {
							return "position at " + x + ", " + y + ", " + z;
						}
					};
				});
		SyntaxRegistry.registerExpression(5, "block at %worldpos%", (parser, pattern, match) -> {
			Expression<?> source = parser.parseExpression(match.slotInputs()[0], WorldPos.class, false);
			return source == null ? null : new SingleExpression<BlockRef>(BlockRef.class) {
				@Override
				protected @Nullable BlockRef compute(ExecContext context) {
					WorldPos pos = (WorldPos) converted(context, source, WorldPos.class);
					return pos == null ? null : new BlockRef(pos.world(), pos.blockPos());
				}

				@Override
				public String toString() {
					return "block at " + source;
				}
			};
		});
		registerCoordinate("x");
		registerCoordinate("y");
		registerCoordinate("z");
	}

	private static void registerCoordinate(String axis) {
		SyntaxRegistry.registerExpression(4, axis + "[-| ]coord[inate] of %worldpos%",
				(parser, pattern, match) -> {
					Expression<?> source = parser.parseExpression(match.slotInputs()[0], WorldPos.class, false);
					if (source == null)
						return null;
					return switch (axis) {
						case "x" -> new Coordinate(source, 0);
						case "y" -> new Coordinate(source, 1);
						case "z" -> new Coordinate(source, 2);
						default -> throw new AssertionError("Unhandled axis");
					};
				});
	}

	private static final class Coordinate extends SingleExpression<Number> {

		private final Expression<?> source;
		private final int axis;

		Coordinate(Expression<?> source, int axis) {
			super(Number.class);
			this.source = source;
			this.axis = axis;
		}

		@Override
		protected @Nullable Number compute(ExecContext context) {
			WorldPos pos = (WorldPos) converted(context, source, WorldPos.class);
			if (pos == null)
				return null;
			return switch (axis) {
				case 0 -> pos.x();
				case 1 -> pos.y();
				case 2 -> pos.z();
				default -> throw new AssertionError("Unhandled axis");
			};
		}

		@Override
		public String toString() {
			return "coordinate of " + source;
		}
	}

	private static void numeric() {
		SyntaxRegistry.registerExpression(4, "random number between %number% and %number%",
				(parser, pattern, match) -> {
					Expression<?> min = parser.parseExpression(match.slotInputs()[0], Number.class, false);
					Expression<?> max = parser.parseExpression(match.slotInputs()[1], Number.class, false);
					if (min == null || max == null)
						return null;
					return new SingleExpression<Number>(Number.class) {
						@Override
						protected Number compute(ExecContext context) {
							double low = doubleOf(min, context, 0);
							double high = doubleOf(max, context, 0);
							double actualLow = Math.min(low, high);
							double actualHigh = Math.max(low, high);
							return Math.floor(actualLow + Math.random() * (actualHigh - actualLow + 1));
						}

						@Override
						public String toString() {
							return "random number between " + min + " and " + max;
						}
					};
				});
		SyntaxRegistry.registerExpression(3, "difference between %timespan% and %timespan%",
				(parser, pattern, match) -> {
					Expression<?> left = parser.parseExpression(match.slotInputs()[0], TimeSpan.class, false);
					Expression<?> right = parser.parseExpression(match.slotInputs()[1], TimeSpan.class, false);
					if (left == null || right == null)
						return null;
					return new SingleExpression<TimeSpan>(TimeSpan.class) {
						@Override
						protected TimeSpan compute(ExecContext context) {
							long a = timeSpanOf(left, context);
							long b = timeSpanOf(right, context);
							return new TimeSpan(Math.abs(a - b));
						}

						@Override
						public String toString() {
							return "difference between " + left + " and " + right;
						}
					};
				});
		SyntaxRegistry.registerExpression(3, "distance between %worldpos% and %worldpos%",
				(parser, pattern, match) -> {
					Expression<?> left = parser.parseExpression(match.slotInputs()[0], WorldPos.class, false);
					Expression<?> right = parser.parseExpression(match.slotInputs()[1], WorldPos.class, false);
					if (left == null || right == null)
						return null;
					return new SingleExpression<Number>(Number.class) {
						@Override
						protected @Nullable Number compute(ExecContext context) {
							WorldPos a = (WorldPos) converted(context, left, WorldPos.class);
							WorldPos b = (WorldPos) converted(context, right, WorldPos.class);
							if (a == null || b == null)
								return null;
							double distance = a.distanceTo(b);
							return Double.isNaN(distance) ? null : Math.round(distance * 100) / 100.0;
						}

						@Override
						public String toString() {
							return "distance between " + left + " and " + right;
						}
					};
				});
	}

	private static void commandArgs() {
		SyntaxRegistry.registerExpression(8, "arg-%number%", (parser, pattern, match) ->
				buildArgByIndex(parser, match.slotInputs()[0]));
		SyntaxRegistry.registerExpression(8, "argument %number%", (parser, pattern, match) ->
				buildArgByIndex(parser, match.slotInputs()[0]));
	}

	private static Expression<?> buildArgByIndex(Parser parser, String indexSource) {
		Expression<?> index = parser.parseLiteral(indexSource, Number.class);
		if (!(index instanceof Literal<?> literal)
				|| !(literal.value() instanceof Number numeric))
			return null;
		int ordinal = (int) Math.round(numeric.doubleValue());
		return new SingleExpression<Object>(Object.class) {
			@Override
			protected @Nullable Object compute(ExecContext context) {
				return context.getLocal("\0arg:" + ordinal);
			}

			@Override
			public String toString() {
				return "arg-" + ordinal;
			}
		};
	}

	private static void looping() {
		SyntaxRegistry.registerExpression(11, "loop-value", (parser, pattern, match) ->
				new LoopAccessor(false));
		SyntaxRegistry.registerExpression(11, "loop-index", (parser, pattern, match) ->
				new LoopAccessor(true));
	}

	private static final class LoopAccessor extends SingleExpression<Object> {

		private final boolean index;

		LoopAccessor(boolean index) {
			super(Object.class);
			this.index = index;
		}

		@Override
		protected @Nullable Object compute(ExecContext context) {
			List<LoopState> stack = context.loopStack();
			if (!stack.isEmpty()) {
				LoopState state = stack.getLast();
				return index ? state.currentIndex : state.currentValue;
			}
			return null;
		}

		@Override
		public String toString() {
			return index ? "loop-index" : "loop-value";
		}
	}

	private static void items() {
		SyntaxRegistry.registerExpression(12, "%number% %itemtype%", (parser, pattern, match) -> {
			Expression<?> amount = parser.parseExpression(match.slotInputs()[0], Number.class, false);
			if (amount == null)
				return null;
			Expression<?> item = parser.parseLiteral(match.slotInputs()[1], ItemType.class);
			if (!(item instanceof Literal<?> itemLiteral)
					|| !(itemLiteral.value() instanceof ItemType itemType))
				return null;
			return new SingleExpression<ItemType>(ItemType.class) {
				@Override
				protected ItemType compute(ExecContext context) {
					Object raw = amount.getObjectValue(context);
					if (!(raw instanceof Number count))
						return itemType;
					return ItemType.of(itemType.item(),
							Math.max(0, (int) Math.round(count.doubleValue())) * itemType.count());
				}

				@Override
				public String toString() {
					return amount + " " + itemType;
				}
			};
		});
	}

	private static Object converted(ExecContext context, Expression<?> source, Class<?> target) {
		for (Object value : source.getObjectValues(context)) {
			Object result = Classes.convert(value, target);
			if (result != null)
				return result;
		}
		return null;
	}

	private static double doubleOf(Expression<?> source, ExecContext context, double fallback) {
		Object rawValue = source.getObjectValue(context);
		return rawValue instanceof Number value ? value.doubleValue() : fallback;
	}

	private static long timeSpanOf(Expression<?> source, ExecContext context) {
		Object rawValue = source.getObjectValue(context);
		return rawValue instanceof TimeSpan span ? span.ticks() : 0;
	}

	private static final class CurrentServerHolder {

		static MinecraftServer get() {
			return CurrentServer.get();
		}
	}
}
