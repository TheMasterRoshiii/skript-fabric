package dev.me.master.skript.loader;
import dev.me.master.skript.script.SkriptScript;
import dev.me.master.skript.command.ScriptCommand;
import dev.me.master.skript.events.EventDispatch;
import dev.me.master.skript.lang.ClassInfo;
import dev.me.master.skript.lang.Classes;
import dev.me.master.skript.lang.Condition;
import dev.me.master.skript.lang.ExecContext;
import dev.me.master.skript.lang.Expression;
import dev.me.master.skript.lang.IfSection;
import dev.me.master.skript.lang.LoopSection;
import dev.me.master.skript.lang.ParseState;
import dev.me.master.skript.lang.Parser;
import dev.me.master.skript.lang.SkriptPattern;
import dev.me.master.skript.lang.SyntaxRegistry;
import dev.me.master.skript.lang.Trigger;
import dev.me.master.skript.lang.TriggerItem;
import dev.me.master.skript.lang.WhileSection;
import dev.me.master.skript.lang.function.FunctionDefinition;
import dev.me.master.skript.lang.function.FunctionRegistry;
import dev.me.master.skript.scheduler.PeriodicEvents;
import dev.me.master.skript.lang.TimeSpanLiteral;
import dev.me.master.skript.util.SkriptLogger;
import dev.me.master.skript.variables.Variables;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

import dev.me.master.skript.events.EventHandler;
import dev.me.master.skript.registrations.EventRegistry;
import net.minecraft.server.network.ServerPlayerEntity;
public final class ScriptLoader {

	private static final Pattern FUNCTION_DECL = Pattern.compile(
			"^function ([A-Za-z_][A-Za-z0-9_-]*)\\((.*)\\)(?: :: ([A-Za-z ]+))?:$");
	private static final Pattern COMMAND_DECL = Pattern.compile("^command /([A-Za-z0-9_+-]+)(.*)?:$");
	private static final Pattern EVENT_DECL = Pattern.compile("^(?:on )(.+?):$");
	private static final Pattern PERIODIC_DECL = Pattern.compile("^every ([^:]+):$");

	private final ParseState state;
	private final Parser parser;
	private final List<InitialVariable> initialVariables = new ArrayList<>();
	private final List<RegisteredFunction> registeredFunctions = new ArrayList<>();

	private record InitialVariable(String name, Object value) {
	}

	private record RegisteredFunction(FunctionDefinition current, @Nullable FunctionDefinition previous) {
	}

	public ScriptLoader(ParseState state) {
		this.state = state;
		this.parser = new Parser(state);
	}

	public boolean load(SkriptScript script, List<String> lines) {
		List<ScriptReader.Node> nodes = ScriptReader.read(lines, state);
		primeOptions(nodes);
		nodes = ScriptReader.substituteOptions(nodes, state);
		int index = 0;
		while (index < nodes.size()) {
			ScriptReader.Line line = (ScriptReader.Line) nodes.get(index);
			if (line.indent() != 0) {
				error(line, "Unexpected indentation at top level");
				index++;
				continue;
			}
			int consumed = parseRootStructure(script, line, nodes, index);
			if (consumed < 0) {
				error(line, "Can't understand this structure: '" + line.content() + "'");
				index++;
			} else {
				index += consumed;
			}
		}
		if (state.hasErrors()) {
			rollback(script);
			return false;
		}
		for (InitialVariable variable : initialVariables)
			Variables.setIfAbsent(variable.name(), variable.value());
		return true;
	}

	private void primeOptions(List<ScriptReader.Node> nodes) {
		for (int i = 0; i < nodes.size(); i++) {
			ScriptReader.Line line = (ScriptReader.Line) nodes.get(i);
			if (line.indent() != 0 || !line.content().equalsIgnoreCase("options:"))
				continue;
			for (ScriptReader.Line child : collectChildren(nodes, i))
				registerOption(child);
		}
	}

	private int parseRootStructure(SkriptScript script, ScriptReader.Line line, List<ScriptReader.Node> nodes, int index) {
		String content = line.content();
		List<ScriptReader.Line> children = collectChildren(nodes, index);

		if (content.equalsIgnoreCase("options:")) {
			for (ScriptReader.Line child : children)
				registerOption(child);
			return 1 + children.size();
		}
		if (content.equalsIgnoreCase("variables:") || content.equalsIgnoreCase("local variables:")) {
			for (ScriptReader.Line child : children)
				registerInitialVariable(child);
			return 1 + children.size();
		}

		Matcher eventMatcher = EVENT_DECL.matcher(content);
		if (eventMatcher.matches()) {
			Trigger trigger = buildEventTrigger(script, line, eventMatcher.group(1), children);
			if (trigger != null) {
				script.addTrigger(trigger);
				return 1 + children.size();
			}
		}

		Matcher periodicMatcher = PERIODIC_DECL.matcher(content);
		if (periodicMatcher.matches()) {
			Trigger trigger = buildPeriodicTrigger(script, line, periodicMatcher.group(1), children);
			if (trigger != null) {
				script.addTrigger(trigger);
				return 1 + children.size();
			}
		}

		Matcher commandMatcher = COMMAND_DECL.matcher(content);
		if (commandMatcher.matches()) {
			ScriptCommand command = buildCommand(script, line, commandMatcher.group(1), commandMatcher.group(2), children);
			if (command != null) {
				for (ScriptCommand existing : script.commands()) {
					if (existing.name().equals(command.name())) {
						error(line, "Duplicate command: /" + command.name());
						return -1;
					}
				}
				script.addCommand(command);
				return 1 + children.size();
			}
			return -1;
		}

		Matcher functionMatcher = FUNCTION_DECL.matcher(content);
		if (functionMatcher.matches()) {
			FunctionDefinition function = buildFunction(script, line, functionMatcher, children);
			if (function != null) {
				for (FunctionDefinition declared : script.functions()) {
					if (declared.name().equals(function.name())) {
						error(line, "Duplicate function: " + function.name());
						return -1;
					}
				}
				FunctionDefinition existing = FunctionRegistry.get(function.name());
				if (existing != null && !existing.script().file().equals(script.file())) {
					error(line, "Duplicate function: " + function.name());
					return -1;
				}
				FunctionDefinition previous = FunctionRegistry.register(function);
				registeredFunctions.add(new RegisteredFunction(function, previous));
				script.addFunction(function);
				return 1 + children.size();
			}
			return -1;
		}
		return -1;
	}

	private void registerOption(ScriptReader.Line line) {
		int colon = line.content().indexOf(':');
		String name = colon < 0 ? line.content() : line.content().substring(0, colon);
		String value = colon < 0 ? "" : line.content().substring(colon + 1).trim();
		state.options.put(name.trim().toLowerCase(Locale.ROOT), value);
	}

	private void registerInitialVariable(ScriptReader.Line line) {
		String content = line.content();
		int colon = content.indexOf(':');
		if (colon <= 0 || !content.startsWith("{")) {
			error(line, "Invalid variable entry");
			return;
		}
		String name = content.substring(1, content.lastIndexOf('}') > 0 ? content.lastIndexOf('}') : colon).trim();
		String valueSource = content.substring(colon + 1).trim();
		Object value = null;
		if (!valueSource.isEmpty()) {
			Expression<?> parsed = parser.parseExpression(valueSource, Object.class, false);
			if (parsed == null) {
				error(line, "Can't parse initial variable value: '" + valueSource + "'");
				return;
			}
			value = parsed.getValue(new ExecContext(null));
		}
		if (name.isEmpty()) {
			error(line, "Variable name cannot be empty");
			return;
		}
		initialVariables.add(new InitialVariable(name, value));
	}

	private static List<ScriptReader.Line> collectChildren(List<ScriptReader.Node> nodes, int index) {
		List<ScriptReader.Line> children = new ArrayList<>();
		ScriptReader.Line parent = (ScriptReader.Line) nodes.get(index);
		for (int i = index + 1; i < nodes.size(); i++) {
			ScriptReader.Line candidate = (ScriptReader.Line) nodes.get(i);
			if (candidate.indent() <= parent.indent())
				break;
			children.add(candidate);
		}
		return children;
	}

	private @Nullable Trigger buildEventTrigger(SkriptScript script, ScriptReader.Line line,
			String eventName, List<ScriptReader.Line> childLines) {
		EventHandler handler =
				EventRegistry.match(eventName, parser);
		if (handler == null) {
			error(line, "Can't understand this event: '" + eventName + "'");
			return null;
		}
		List<TriggerItem> body = parseBody(childLines);
		if (body == null)
			return null;
		Trigger trigger = new Trigger(script, "on " + eventName, line.number(), body);
		handler.bind(trigger);
		return trigger;
	}

	private @Nullable Trigger buildPeriodicTrigger(SkriptScript script, ScriptReader.Line line,
			String periodSource, List<ScriptReader.Line> childLines) {
		Expression<?> periodExpression = parser.parseLiteral(periodSource, Object.class);
		if (!(periodExpression instanceof TimeSpanLiteral period)) {
			error(line, "Can't understand this timespan: '" + periodSource + "'");
			return null;
		}
		List<TriggerItem> body = parseBody(childLines);
		if (body == null)
			return null;
		Trigger trigger = new Trigger(script, "every " + periodSource, line.number(), body);
		PeriodicEvents.register(trigger, period.value());
		return trigger;
	}

	private @Nullable ScriptCommand buildCommand(SkriptScript script, ScriptReader.Line headerLine,
			String name, String argumentSpec, List<ScriptReader.Line> childLines) {
		ScriptCommand.Builder builder = ScriptCommand.builder(name);
		boolean optionalSeen = false;
		Set<String> argumentNames = new HashSet<>();
		String normalizedArguments = argumentSpec == null ? "" : argumentSpec.trim();
		for (String rawArgument : normalizedArguments.isEmpty() ? new String[0] : normalizedArguments.split("\\s+")) {
			if (rawArgument.isBlank())
				continue;
			ScriptCommand.Arg argument = parseCommandArgument(rawArgument);
			if (argument == null) {
				error(headerLine, "Can't understand this argument: '" + rawArgument + "'");
				return null;
			}
			if (optionalSeen && !argument.optional()) {
				error(headerLine, "Required command arguments cannot follow an optional argument");
				return null;
			}
			if (!argumentNames.add(argument.name())) {
				error(headerLine, "Duplicate command argument: '" + argument.name() + "'");
				return null;
			}
			optionalSeen |= argument.optional();
			builder.addArgument(argument);
		}
		List<TriggerItem> triggerBody = null;
		int i = 0;
		while (i < childLines.size()) {
			ScriptReader.Line entry = childLines.get(i);
			List<ScriptReader.Line> nested = new ArrayList<>();
			for (int j = i + 1; j < childLines.size(); j++) {
				if (((ScriptReader.Line) childLines.get(j)).indent() <= entry.indent())
					break;
				nested.add(childLines.get(j));
			}
			String content = entry.content();
			if (content.equalsIgnoreCase("trigger:")) {
				triggerBody = parseBody(nested);
			} else if (content.regionMatches(true, 0, "description:", 0, "description:".length())) {
				builder.description(valueAfterColon(content));
			} else if (content.regionMatches(true, 0, "usage:", 0, "usage:".length())) {
				builder.usage(valueAfterColon(content));
			} else if (content.regionMatches(true, 0, "permission:", 0, "permission:".length())) {
				builder.permission(valueAfterColon(content));
			} else if (content.regionMatches(true, 0, "executable by:", 0, "executable by:".length())) {
				String who = valueAfterColon(content).toLowerCase(Locale.ROOT);
				builder.executableByConsole(who.contains("console") || who.contains("players and console"));
			} else if (content.endsWith(":") && !nested.isEmpty()) {
				i += 1 + nested.size();
				continue;
			}
			i += 1 + nested.size();
		}
		if (triggerBody == null) {
			error(headerLine, "Command '" + name + "' has no trigger section");
			return null;
		}
		Trigger trigger = new Trigger(script, "command /" + name, headerLine.number(), triggerBody);
		builder.trigger(trigger);
		return builder.build();
	}

	private static String valueAfterColon(String content) {
		int colon = content.indexOf(':');
		return colon < 0 ? "" : content.substring(colon + 1).trim().replace("\"", "");
	}

	private @Nullable ScriptCommand.Arg parseCommandArgument(String raw) {
		boolean optional = raw.trim().startsWith("[");
		String cleaned = raw.replace("<", "").replace(">", "").replace("[", "").replace("]", "").trim();
		int typeSeparator = cleaned.indexOf(':');
		String argName;
		String typeName = "string";
		if (typeSeparator > 0) {
			argName = cleaned.substring(0, typeSeparator).trim();
			typeName = cleaned.substring(typeSeparator + 1).trim();
		} else {
			argName = cleaned;
		}
		if (argName.isBlank())
			return null;
		ClassInfo<?> type = Classes.byName(typeName);
		if (type == null)
			type = guessTypeFromName(argName);
		if (type == null)
			return null;
		return new ScriptCommand.Arg(argName.toLowerCase(Locale.ROOT), type, optional);
	}

	private @Nullable ClassInfo<?> guessTypeFromName(String argName) {
		String lowered = argName.toLowerCase(Locale.ROOT);
		if (lowered.contains("player")) {
			ClassInfo<?> playerType = Classes.byClass(ServerPlayerEntity.class);
			if (playerType != null)
				return playerType;
		}
		if (lowered.contains("number") || lowered.contains("amount") || lowered.contains("count"))
			return Classes.byName("number");
		if (lowered.contains("text") || lowered.contains("message"))
			return Classes.byClass(String.class);
		return Classes.byClass(String.class);
	}

	private @Nullable FunctionDefinition buildFunction(SkriptScript script, ScriptReader.Line line,
			Matcher matcher, List<ScriptReader.Line> childLines) {
		String name = matcher.group(1);
		List<FunctionDefinition.Parameter> parameters = new ArrayList<>();
		Set<String> parameterNames = new HashSet<>();
		boolean optionalSeen = false;
		String parameterSource = matcher.group(2).trim();
		if (!parameterSource.isEmpty()) {
			for (String chunk : parameterSource.split(",")) {
				String part = chunk.trim();
				if (part.isEmpty()) {
					error(line, "Empty function parameter");
					return null;
				}
				boolean optional = part.contains("=");
				String typeAndName = optional ? part.substring(0, part.indexOf('=')).trim() : part;
				String defaultValueSource = optional ? part.substring(part.indexOf('=') + 1).trim() : null;
				int separator = typeAndName.indexOf(':');
				if (separator <= 0) {
					error(line, "Parameter needs a type: '" + part + "'");
					return null;
				}
				String parameterName = typeAndName.substring(0, separator).trim().toLowerCase(Locale.ROOT);
				if (!parameterNames.add(parameterName)) {
					error(line, "Duplicate function parameter: '" + parameterName + "'");
					return null;
				}
				if (optionalSeen && !optional) {
					error(line, "Required function parameters cannot follow an optional parameter");
					return null;
				}
				optionalSeen |= optional;
				ClassInfo<?> type = Classes.byName(typeAndName.substring(separator + 1).trim());
				if (type == null) {
					error(line, "Unknown parameter type: '" + typeAndName + "'");
					return null;
				}
				Expression<?> defaultValue = null;
				if (defaultValueSource != null) {
					defaultValue = parser.parseExpression(defaultValueSource, type.type(), false);
					if (defaultValue == null) {
						error(line, "Can't parse default value: '" + defaultValueSource + "'");
						return null;
					}
				}
				parameters.add(new FunctionDefinition.Parameter(parameterName, type, defaultValue));
			}
		}
		ClassInfo<?> returnType = null;
		String returnTypeSource = matcher.group(3);
		if (returnTypeSource != null && !returnTypeSource.isBlank()) {
			returnType = Classes.byName(returnTypeSource.trim());
			if (returnType == null) {
				error(line, "Unknown return type: '" + returnTypeSource + "'");
				return null;
			}
		}
		List<TriggerItem> body = parseBody(childLines);
		if (body == null)
			return null;
		return new FunctionDefinition(script, name, parameters, returnType, body, line.number());
	}

	public @Nullable List<TriggerItem> parseBody(List<ScriptReader.Line> lines) {
		List<TriggerItem> items = new ArrayList<>(lines.size());
		int i = 0;
		while (i < lines.size()) {
			ScriptReader.Line line = lines.get(i);
			int consumed = parseStatementInto(items, line, lines, i);
			if (consumed < 0) {
				error(line, "Can't understand this line: '" + line.content() + "'");
				return null;
			}
			i += consumed;
		}
		return items;
	}

	private int parseStatementInto(List<TriggerItem> items, ScriptReader.Line line,
			List<ScriptReader.Line> siblings, int index) {
		String content = line.content();

		if (content.equalsIgnoreCase("else:")) {
			if (!attachElse(items, null, extractChildren(siblings, index)))
				return errorReturn(line, "'else' without matching 'if'");
			return 1 + countChildren(siblings, index);
		}
		if (content.equalsIgnoreCase("else") || content.equalsIgnoreCase("else :")) {
			return errorReturn(line, "'else' must end with ':'");
		}
		String elseIfSource = stripPrefixIgnoreCase(content, "else ");
		if (elseIfSource != null && elseIfSource.endsWith(":")) {
			String conditionSource = stripPrefixIgnoreCase(elseIfSource, "if ");
			if (conditionSource == null || conditionSource.isBlank())
				return errorReturn(line, "'else if' needs a condition");
			if (conditionSource.endsWith(":"))
				conditionSource = conditionSource.substring(0, conditionSource.length() - 1);
			Condition condition = parseCondition(conditionSource.trim());
			if (condition == null)
				return errorReturn(line, "Can't understand this condition: '" + conditionSource.trim() + "'");
			if (!attachElse(items, List.of(condition), extractChildren(siblings, index)))
				return errorReturn(line, "'else if' without matching 'if'");
			return 1 + countChildren(siblings, index);
		}

		if (content.endsWith(":")) {
			String head = content.substring(0, content.length() - 1);
			List<ScriptReader.Line> children = extractChildren(siblings, index);
			int childCount = countChildren(siblings, index);

			if (stripPrefixIgnoreCase(head, "if ") != null) {
				Condition condition = parseCondition(stripPrefixIgnoreCase(head, "if "));
				if (condition == null)
					return errorReturn(line, "Can't understand this condition");
				items.add(new IfSection(line.number(), List.of(new IfSection.Branch(List.of(condition), parseChildren(children)))));
				return 1 + childCount;
			}
			if (stripPrefixIgnoreCase(head, "while ") != null) {
				Condition condition = parseCondition(stripPrefixIgnoreCase(head, "while "));
				if (condition == null)
					return errorReturn(line, "Can't understand this condition");
				items.add(new WhileSection(line.number(), condition, parseChildren(children)));
				return 1 + childCount;
			}
			if (stripPrefixIgnoreCase(head, "loop ") != null) {
				Expression<?> iterable = parser.parseExpression(stripPrefixIgnoreCase(head, "loop "), Object.class, true);
				if (iterable == null)
					return errorReturn(line, "Can't understand this loop expression");
				items.add(new LoopSection(line.number(), iterable, parseChildren(children)));
				return 1 + childCount;
			}
			if (head.equalsIgnoreCase("else")) {
				return errorReturn(line, "Nested 'else' blocks are not valid here");
			}
			Condition trailingCondition = parseCondition(head);
			if (trailingCondition != null) {
				items.add(new IfSection(line.number(), List.of(new IfSection.Branch(List.of(trailingCondition), parseChildren(children)))));
				return 1 + childCount;
			}
		}

		TriggerItem.Statement effect = parseEffect(content, line.number());
		if (effect == null)
			return -1;
		items.add(effect);
		return 1;
	}

	private List<TriggerItem> parseChildren(List<ScriptReader.Line> children) {
		List<TriggerItem> parsed = parseBody(children);
		return parsed == null ? List.of() : parsed;
	}

	private static List<ScriptReader.Line> extractChildren(List<ScriptReader.Line> siblings, int index) {
		List<ScriptReader.Line> children = new ArrayList<>();
		ScriptReader.Line parent = siblings.get(index);
		for (int j = index + 1; j < siblings.size(); j++) {
			if (siblings.get(j).indent() <= parent.indent())
				break;
			children.add(siblings.get(j));
		}
		return children;
	}

	private static int countChildren(List<ScriptReader.Line> siblings, int index) {
		return extractChildren(siblings, index).size();
	}

	private boolean attachElse(List<TriggerItem> items, @Nullable List<Condition> conditions,
			List<ScriptReader.Line> children) {
		if (items.isEmpty() || !(items.getLast() instanceof IfSection previous))
			return false;
		List<IfSection.Branch> branches = new ArrayList<>(previous.branches());
		branches.add(new IfSection.Branch(conditions == null ? List.of() : conditions, parseChildren(children)));
		items.set(items.size() - 1, new IfSection(previous.line(), branches));
		return true;
	}

	private int errorReturn(ScriptReader.Line line, String message) {
		error(line, message);
		return -1;
	}

	private void rollback(SkriptScript script) {
		for (Trigger trigger : script.triggers()) {
			EventDispatch.unbindAll(trigger);
			PeriodicEvents.unregister(trigger);
		}
		for (int i = registeredFunctions.size() - 1; i >= 0; i--) {
			RegisteredFunction function = registeredFunctions.get(i);
			FunctionRegistry.restore(function.current(), function.previous());
		}
		script.triggers().clear();
		script.commands().clear();
		script.functions().clear();
	}

	private static @Nullable String stripPrefixIgnoreCase(String content, String prefix) {
		if (content.length() >= prefix.length() && content.regionMatches(true, 0, prefix, 0, prefix.length()))
			return content.substring(prefix.length());
		return null;
	}

	private @Nullable Condition parseCondition(String source) {
		for (SyntaxRegistry.ConditionEntry entry : SyntaxRegistry.conditions()) {
			SkriptPattern.MatchResult match = entry.pattern().match(source);
			if (match == null)
				continue;
			try {
				Condition built = entry.factory().create(parser, entry.pattern(), match);
				if (built != null)
					return built;
			} catch (RuntimeException e) {
				SkriptLogger.error("Error building condition from '" + source + "'", e);
				return null;
			}
		}
		return null;
	}

	private @Nullable TriggerItem.Statement parseEffect(String source, int lineNumber) {
		for (SyntaxRegistry.EffectEntry entry : SyntaxRegistry.effects()) {
			SkriptPattern.MatchResult match = entry.pattern().match(source);
			if (match == null)
				continue;
			try {
				TriggerItem.Statement built =
						entry.factory().create(lineNumber, parser, entry.pattern(), match);
				if (built != null)
					return built;
			} catch (RuntimeException e) {
				SkriptLogger.error("Error building effect from '" + source + "'", e);
				return null;
			}
		}
		return null;
	}

	private void error(ScriptReader.Line line, String message) {
		state.sink.error(state.script.name() + " line " + line.number() + ": " + message);
	}
}
