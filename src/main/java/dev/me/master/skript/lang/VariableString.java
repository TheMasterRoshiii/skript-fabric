package dev.me.master.skript.lang;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.me.master.skript.lang.Parser;
public final class VariableString {

	private sealed interface Part permits TextPart, ExpressionPart {
	}

	private record TextPart(String value) implements Part {
	}

	private record ExpressionPart(Expression<?> expression) implements Part {
	}

	private static final Map<String, String> COLOR_TAGS = Map.ofEntries(
			Map.entry("<black>", "§0"),
			Map.entry("<dark_blue>", "§1"),
			Map.entry("<dark_green>", "§2"),
			Map.entry("<dark_aqua>", "§3"),
			Map.entry("<dark_red>", "§4"),
			Map.entry("<dark_purple>", "§5"),
			Map.entry("<gold>", "§6"),
			Map.entry("<gray>", "§7"),
			Map.entry("<dark_gray>", "§8"),
			Map.entry("<blue>", "§9"),
			Map.entry("<green>", "§a"),
			Map.entry("<aqua>", "§b"),
			Map.entry("<red>", "§c"),
			Map.entry("<light_purple>", "§d"),
			Map.entry("<yellow>", "§e"),
			Map.entry("<white>", "§f"),
			Map.entry("<bold>", "§l"),
			Map.entry("<italic>", "§o"),
			Map.entry("<underlined>", "§n"),
			Map.entry("<strikethrough>", "§m"),
			Map.entry("<obfuscated>", "§k"),
			Map.entry("<reset>", "§r"));

	private static final String LEGACY_CODES = "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx";

	private final List<Part> parts;
	private final boolean constant;

	private VariableString(List<Part> parts) {
		this.parts = List.copyOf(parts);
		boolean allConstant = true;
		for (Part part : this.parts) {
			if (part instanceof ExpressionPart) {
				allConstant = false;
				break;
			}
		}
		this.constant = allConstant;
	}

	public boolean isConstant() {
		return constant;
	}

	public static VariableString parse(String raw, Parser parser) {
		List<Part> parts = new ArrayList<>();
		StringBuilder literal = new StringBuilder();
		int i = 0;
		while (i < raw.length()) {
			char c = raw.charAt(i);
			if (c == '%' && i + 1 < raw.length()) {
				int closing = findClosingPercent(raw, i + 1);
				if (closing >= 0) {
					String expressionSource = raw.substring(i + 1, closing);
					if (!expressionSource.isEmpty()) {
						Expression<?> parsed = parser.parseExpression(expressionSource, Object.class, true);
						if (parsed != null) {
							flush(parts, literal);
							parts.add(new ExpressionPart(parsed));
							i = closing + 1;
							continue;
						}
						parser.state.sink.error("Can't understand this expression: '" + expressionSource + "'");
					}
				}
				literal.append('%');
				i++;
				continue;
			}
			literal.append(c);
			i++;
		}
		flush(parts, literal);
		return new VariableString(parts);
	}

	private static int findClosingPercent(String raw, int start) {
		boolean inQuotes = false;
		for (int i = start; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '"' && (i == 0 || raw.charAt(i - 1) != '\\'))
				inQuotes = !inQuotes;
			else if (c == '%' && !inQuotes)
				return i;
		}
		return -1;
	}

	private static void flush(List<Part> parts, StringBuilder literal) {
		if (!literal.isEmpty()) {
			parts.add(new TextPart(literal.toString()));
			literal.setLength(0);
		}
	}

	public String render(ExecContext context) {
		StringBuilder result = new StringBuilder();
		for (Part part : parts) {
			switch (part) {
				case TextPart(String value) -> result.append(value);
				case ExpressionPart(Expression<?> expression) -> appendValue(result, expression, context);
			}
		}
		return colorize(result.toString());
	}

	private static void appendValue(StringBuilder result, Expression<?> expression, ExecContext context) {
		List<?> values = expression.getValues(context);
		for (int i = 0; i < values.size(); i++) {
			if (i > 0)
				result.append(", ");
			result.append(Classes.toStringValue(values.get(i)));
		}
	}

	@Nullable
	public Text toText(ExecContext context) {
		String rendered = render(context);
		return rendered.isEmpty() ? null : Text.literal(rendered);
	}

	public static String colorize(String input) {
		String tagged = input;
		for (Map.Entry<String, String> tag : COLOR_TAGS.entrySet())
			tagged = tagged.replace(tag.getKey(), tag.getValue());
		StringBuilder result = new StringBuilder(tagged.length());
		for (int i = 0; i < tagged.length(); i++) {
			char c = tagged.charAt(i);
			char next = i + 1 < tagged.length() ? tagged.charAt(i + 1) : 0;
			if ((c == '&' || c == '§') && LEGACY_CODES.indexOf(next) >= 0) {
				result.append('§').append(Character.toLowerCase(next));
				i++;
				continue;
			}
			result.append(c);
		}
		return result.toString();
	}

	public String renderConstant() {
		return render(new ExecContext(null));
	}

	public MutableText toMutableText(ExecContext context) {
		return Text.literal(render(context));
	}
}
