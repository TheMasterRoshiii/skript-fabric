package dev.me.master.skript.lang;

import java.util.ArrayList;
import java.util.List;

public final class SyntaxRegistry {

	private static final List<ExpressionEntry> EXPRESSIONS = new ArrayList<>();
	private static final List<EffectEntry> EFFECTS = new ArrayList<>();
	private static final List<ConditionEntry> CONDITIONS = new ArrayList<>();
	private static List<ExpressionEntry> frozenExpressions;
	private static List<EffectEntry> frozenEffects;
	private static List<ConditionEntry> frozenConditions;

	public record ExpressionEntry(SkriptPattern pattern, ExpressionFactory factory) {
	}

	@FunctionalInterface
	public interface ExpressionFactory {
		Expression<?> create(Parser parser, SkriptPattern pattern, SkriptPattern.MatchResult match);
	}

	public record EffectEntry(SkriptPattern pattern, EffectFactory factory) {
	}

	@FunctionalInterface
	public interface EffectFactory {
		TriggerItem.Statement create(int line, Parser parser, SkriptPattern pattern, SkriptPattern.MatchResult match);
	}

	public record ConditionEntry(SkriptPattern pattern, ConditionFactory factory) {
	}

	@FunctionalInterface
	public interface ConditionFactory {
		Condition create(Parser parser, SkriptPattern pattern, SkriptPattern.MatchResult match);
	}

	private SyntaxRegistry() {
	}

	public static void registerExpression(int priority, String pattern, ExpressionFactory factory) {
		requireUnfrozen();
		EXPRESSIONS.add(new ExpressionEntry(SkriptPattern.compile(pattern), factory));
	}

	public static void registerEffect(int priority, String[] patterns, EffectFactory factory) {
		requireUnfrozen();
		for (String pattern : patterns)
			EFFECTS.add(new EffectEntry(SkriptPattern.compile(pattern), factory));
	}

	public static void registerCondition(int priority, String[] patterns, ConditionFactory factory) {
		requireUnfrozen();
		for (String pattern : patterns)
			CONDITIONS.add(new ConditionEntry(SkriptPattern.compile(pattern), factory));
	}

	public static void freeze() {
		if (frozenExpressions != null)
			throw new AssertionError("SyntaxRegistry frozen twice");
		frozenExpressions = List.copyOf(EXPRESSIONS);
		frozenEffects = List.copyOf(EFFECTS);
		frozenConditions = List.copyOf(CONDITIONS);
	}

	private static void requireUnfrozen() {
		if (frozenExpressions != null)
			throw new AssertionError("SyntaxRegistry modified after freeze()");
	}

	public static List<ExpressionEntry> expressions() {
		requireFrozen(frozenExpressions);
		return frozenExpressions;
	}

	public static List<EffectEntry> effects() {
		requireFrozen(frozenEffects);
		return frozenEffects;
	}

	public static List<ConditionEntry> conditions() {
		requireFrozen(frozenConditions);
		return frozenConditions;
	}

	private static void requireFrozen(List<?> frozen) {
		if (frozen == null)
			throw new AssertionError("SyntaxRegistry accessed before freeze()");
	}
}
