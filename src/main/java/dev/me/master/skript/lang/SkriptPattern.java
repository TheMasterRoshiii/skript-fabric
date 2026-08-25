package dev.me.master.skript.lang;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SkriptPattern {

	private final String source;
	private final Pattern regex;
	private final List<SlotInfo> slots;

	private record SlotInfo(boolean optional, boolean plural) {
	}

	private SkriptPattern(String source, Pattern regex, List<SlotInfo> slots) {
		this.source = source;
		this.regex = regex;
		this.slots = List.copyOf(slots);
	}

	public static SkriptPattern compile(String skriptPattern) {
		StringBuilder b = new StringBuilder();
		List<SlotInfo> slots = new ArrayList<>();
		boolean sawSpace = false;
		int pos = 0;
		int length = skriptPattern.length();
		while (pos < length) {
			char c = skriptPattern.charAt(pos);
			if (Character.isWhitespace(c)) {
				sawSpace = true;
				pos++;
				continue;
			}
			switch (c) {
				case '[' -> {
					if (sawSpace && !b.isEmpty()) {
						b.append("(?:\\s+");
					} else {
						b.append("(?:");
					}
					sawSpace = false;
					pos++;
				}
				case ']' -> {
					b.append(")?");
					sawSpace = false;
					pos++;
				}
				case '(' -> {
					flushSpace(b, sawSpace);
					b.append("(?:");
					sawSpace = false;
					pos++;
				}
				case ')' -> {
					b.append(')');
					sawSpace = false;
					pos++;
				}
				case '|' -> {
					b.append('|');
					sawSpace = false;
					pos++;
				}
				case '%' -> {
					int closing = skriptPattern.indexOf('%', pos + 1);
					if (closing < 0)
						throw new AssertionError("Unclosed %slot% in pattern: " + skriptPattern);
					String marker = skriptPattern.substring(pos + 1, closing);
					boolean optional = marker.contains("-");
					boolean plural = marker.contains("+") || marker.contains("*") || optional;
					flushSpace(b, sawSpace);
					slots.add(new SlotInfo(optional, plural));
					b.append("(.+?)");
					sawSpace = false;
					pos = closing + 1;
				}
				default -> {
					flushSpace(b, sawSpace);
					b.append(Pattern.quote(String.valueOf(c)));
					sawSpace = false;
					pos++;
				}
			}
		}
		Pattern compiled = Pattern.compile(b.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
		return new SkriptPattern(skriptPattern, compiled, slots);
	}

	private static void flushSpace(StringBuilder b, boolean sawSpace) {
		if (!sawSpace || b.isEmpty())
			return;
		String tail = b.substring(Math.max(0, b.length() - 6));
		if (tail.endsWith("(?:") || tail.endsWith("|"))
			return;
		b.append("\\s+");
	}

	public String source() {
		return source;
	}

	public int slotCount() {
		return slots.size();
	}

	public boolean slotPlural(int index) {
		return slots.get(index).plural();
	}

	public boolean slotOptional(int index) {
		return slots.get(index).optional();
	}

	public MatchResult match(String input) {
		Matcher matcher = regex.matcher(input.trim());
		if (!matcher.matches())
			return null;
		String[] values = new String[slots.size()];
		for (int i = 0; i < values.length; i++)
			values[i] = matcher.group(i + 1);
		return new MatchResult(values);
	}

	public record MatchResult(String[] slotInputs) {
	}
}
