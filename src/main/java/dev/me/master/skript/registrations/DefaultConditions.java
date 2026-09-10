package dev.me.master.skript.registrations;
import dev.me.master.skript.lang.Classes;
import dev.me.master.skript.lang.Condition;
import dev.me.master.skript.lang.ExecContext;
import dev.me.master.skript.lang.Expression;
import dev.me.master.skript.lang.Literal;
import dev.me.master.skript.lang.Parser;
import dev.me.master.skript.lang.Relation;
import dev.me.master.skript.lang.SkriptPattern;
import dev.me.master.skript.lang.SyntaxRegistry;
import dev.me.master.skript.types.ItemType;
import java.util.List;
import java.util.Locale;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

public final class DefaultConditions {

	private DefaultConditions() {
	}

	public static void register() {
		registerComparisons();
		registerPredicates();
	}

	private static void registerComparisons() {
		SyntaxRegistry.registerCondition(5,
				new String[] {"%objects% (is|are) [(equal to|the same as)] %objects%"},
				(parser, pattern, match) -> comparison(parser, pattern, match, Relation.EQUAL));
		SyntaxRegistry.registerCondition(5,
				new String[] {"%objects% (isn't|is not|aren't|are not) %objects%",
						"%objects% (isn't|is not|aren't|are not) [(equal to|the same as)] %objects%"},
				(parser, pattern, match) -> comparison(parser, pattern, match, Relation.NOT_EQUAL));
		SyntaxRegistry.registerCondition(4,
				new String[] {"%objects% (is|are) (greater|more|bigger|larger) than %objects%",
						"%objects% > %objects%"},
				(parser, pattern, match) -> comparison(parser, pattern, match, Relation.GREATER));
		SyntaxRegistry.registerCondition(4,
				new String[] {"%objects% (is|are) (less|smaller|fewer) than %objects%",
						"%objects% < %objects%"},
				(parser, pattern, match) -> comparison(parser, pattern, match, Relation.LESSER));
		SyntaxRegistry.registerCondition(4,
				new String[] {"%objects% (is|are) greater than or equal to %objects%",
						"%objects% is at least %objects%", "%objects% >= %objects%"},
				(parser, pattern, match) -> atLeastOrAtMost(parser, pattern, match, true));
		SyntaxRegistry.registerCondition(4,
				new String[] {"%objects% (is|are) less than or equal to %objects%",
						"%objects% is at most %objects%", "%objects% <= %objects%"},
				(parser, pattern, match) -> atLeastOrAtMost(parser, pattern, match, false));
	}

	private static void registerPredicates() {
		SyntaxRegistry.registerCondition(6,
				new String[] {"%objects% contains %objects%"},
				(parser, pattern, match) -> {
					Expression<?> haystack = parser.parseExpression(match.slotInputs()[0], Object.class, true);
					Expression<?> needle = parser.parseExpression(match.slotInputs()[1], Object.class, true);
					if (haystack == null || needle == null)
						return null;
					return context -> contains(haystack.getObjectValues(context), needle.getObjectValues(context));
				});
		SyntaxRegistry.registerCondition(6,
				new String[] {"%player% (has|have) [the] permission[s] %string%",
						"%player% (has|have) permission[s]? to use %string%"},
				(parser, pattern, match) -> {
					Expression<?> player = parser.parseExpression(match.slotInputs()[0], ServerPlayerEntity.class, true);
					Expression<?> permission = parser.parseExpression(match.slotInputs()[1], String.class, false);
					if (player == null || permission == null)
						return null;
					return context -> allPlayers(player.getObjectValues(context), candidate ->
							candidate.hasPermissionLevel(2)
									|| hasCustomPermission(candidate, stringOf(permission, context)));
				});
		SyntaxRegistry.registerCondition(6,
				new String[] {"%entities% exist[s]", "%entities% (is|are) set", "%objects% exist[s]"},
				(parser, pattern, match) -> {
					Expression<?> value = parser.parseExpression(match.slotInputs()[0], Object.class, true);
					if (value == null)
						return null;
					return context -> !value.getObjectValues(context).isEmpty();
				});
		SyntaxRegistry.registerCondition(6,
				new String[] {"%players% is sneaking", "%players% (is|are) sneaking"},
				(parser, pattern, match) -> {
					Expression<?> player = parser.parseExpression(match.slotInputs()[0], ServerPlayerEntity.class, true);
					if (player == null)
						return null;
					return context -> allPlayers(player.getObjectValues(context), ServerPlayerEntity::isSneaking);
				});
		SyntaxRegistry.registerCondition(6,
				new String[] {"%players% (is|are) holding %itemtype%"},
				(parser, pattern, match) -> {
					Expression<?> player = parser.parseExpression(match.slotInputs()[0], ServerPlayerEntity.class, true);
					Expression<?> item = parser.parseLiteral(match.slotInputs()[1], ItemType.class);
					if (player == null || !(item instanceof Literal<?> literal))
						return null;
					ItemType itemType = (ItemType) literal.value();
					return context -> allPlayers(player.getObjectValues(context), p ->
							p.getMainHandStack().getItem() == itemType.item()
									|| p.getOffHandStack().getItem() == itemType.item());
				});
		SyntaxRegistry.registerCondition(7,
				new String[] {"%number% chance"},
				(parser, pattern, match) -> {
					Expression<?> chance = parser.parseExpression(match.slotInputs()[0], Number.class, false);
						if (chance == null)
							return null;
						return context -> {
							Object rawPercent = chance.getObjectValue(context);
						return rawPercent instanceof Number percent
								&& Math.random() * 100 < percent.doubleValue();
						};
				});
	}

	@FunctionalInterface
	private interface PlayerPredicate {
		boolean test(ServerPlayerEntity player);
	}

	private static boolean hasCustomPermission(ServerPlayerEntity player, String permission) {
		String tag = "skript.perm." + permission.toLowerCase(Locale.ROOT).replace(' ', '.');
		return player.getCommandTags().contains(tag);
	}

	private static Boolean contains(List<Object> haystack, List<Object> needles) {
		if (haystack.isEmpty())
			return false;
		for (Object wanted : needles) {
			boolean found = false;
			for (Object candidate : haystack) {
				if (matchesLoosely(candidate, wanted)) {
					found = true;
					break;
				}
			}
			if (!found) {
				for (Object candidate : haystack) {
					if (candidate instanceof String text && wanted instanceof String fragment
							&& text.toLowerCase().contains(fragment.toLowerCase())) {
						found = true;
						break;
					}
				}
				if (!found)
					return false;
			}
		}
		return true;
	}

	private static boolean matchesLoosely(Object left, Object right) {
		if (left.equals(right))
			return true;
		Relation relation = Classes.compare(left, right);
		return relation == Relation.EQUAL;
	}

	private static boolean allPlayers(List<Object> players, PlayerPredicate predicate) {
		if (players.isEmpty())
			return false;
		for (Object candidate : players) {
			if (!(candidate instanceof ServerPlayerEntity player) || !predicate.test(player))
				return false;
		}
		return true;
	}

	private static String stringOf(Expression<?> source, ExecContext context) {
		Object value = source.getObjectValue(context);
		return value == null ? "" : Classes.toStringValue(value);
	}

	private static Condition comparison(Parser parser,
			SkriptPattern pattern,
			SkriptPattern.MatchResult match,
			Relation expected) {
		Expression<?> left = parser.parseExpression(match.slotInputs()[0], Object.class, true);
		Expression<?> right = parser.parseExpression(match.slotInputs()[1], Object.class, true);
		if (left == null || right == null)
			return null;
		return context -> compareLists(left.getObjectValues(context), right.getObjectValues(context), expected);
	}

	private static Condition atLeastOrAtMost(Parser parser,
			SkriptPattern pattern,
			SkriptPattern.MatchResult match,
			boolean atLeast) {
		Expression<?> left = parser.parseExpression(match.slotInputs()[0], Object.class, true);
		Expression<?> right = parser.parseExpression(match.slotInputs()[1], Object.class, true);
		if (left == null || right == null)
			return null;
		return context -> compareOrdered(left.getObjectValues(context), right.getObjectValues(context), atLeast);
	}

	private static Boolean compareLists(List<Object> lefts, List<Object> rights, Relation expected) {
		if (lefts.isEmpty() || rights.isEmpty())
			return false;
		for (Object right : rights) {
			boolean anyMatch = false;
			for (Object left : lefts) {
				Boolean result = comparePair(left, right, expected);
				if (result == null)
					return null;
				if (result) {
					anyMatch = true;
					break;
				}
			}
			if (!anyMatch)
				return false;
		}
		return true;
	}

	private static @Nullable Boolean comparePair(Object left, Object right, Relation expected) {
		Relation relation = Classes.compare(left, right);
		if (relation == null)
			relation = Classes.compare(String.valueOf(left), String.valueOf(right));
		if (relation == null)
			return null;
		return relation.satisfies(expected);
	}

	private static Boolean compareOrdered(List<Object> lefts, List<Object> rights, boolean atLeast) {
		if (lefts.size() != 1 || rights.size() != 1)
			return false;
		double a = numberOf(lefts.getFirst());
		double b = numberOf(rights.getFirst());
		if (Double.isNaN(a) || Double.isNaN(b))
			return null;
		return atLeast ? a >= b : a <= b;
	}

	private static double numberOf(Object value) {
		if (value instanceof Number number)
			return number.doubleValue();
		try {
			return Double.parseDouble(String.valueOf(value));
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
	}
}
