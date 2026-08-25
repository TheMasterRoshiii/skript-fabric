package dev.me.master.skript.registrations;
import dev.me.master.skript.lang.EventValues;
import dev.me.master.skript.lang.ExecContext;
import dev.me.master.skript.types.CurrentServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

final class Support {

	private Support() {
	}

	static @Nullable MinecraftServer server() {
		return CurrentServer.get();
	}

	static @Nullable ServerWorld eventWorld(ExecContext context) {
		if (context.hasEvent()) {
			ServerWorld world = EventValues.get(context.event(), ServerWorld.class);
			if (world != null)
				return world;
		}
		return CurrentServer.overworld();
	}

	static final class Arithmetic {

		private Arithmetic() {
		}

		static Object add(Object left, Object right) {
			if (left instanceof Number number && right instanceof Number other) {
				if (isIntegral(number) && isIntegral(other))
					return Math.addExact(number.longValue(), other.longValue());
				return number.doubleValue() + other.doubleValue();
			}
			if (left == null)
				return right;
			return left.toString().concat(String.valueOf(right));
		}

		static Object subtract(Object left, Object right) {
			if (left instanceof Number number && right instanceof Number other) {
				if (isIntegral(number) && isIntegral(other))
					return Math.subtractExact(number.longValue(), other.longValue());
				return number.doubleValue() - other.doubleValue();
			}
			if (left instanceof String text && right != null)
				return text.replace(String.valueOf(right), "");
			return left;
		}

		private static boolean isIntegral(Number number) {
			return number instanceof Long || number instanceof Integer
					|| number instanceof Short || number instanceof Byte;
		}
	}
}
