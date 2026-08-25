package dev.me.master.skript.loader;

import java.util.ArrayList;
import java.util.List;

import dev.me.master.skript.lang.ParseState;
public final class ScriptReader {

	private ScriptReader() {
	}

	public sealed interface Node permits Line {
	}

	public record Line(int number, int indent, String content) implements Node {
	}

	public static List<Node> read(List<String> lines, ParseState state) {
		List<Line> flat = new ArrayList<>(lines.size());
		for (int i = 0; i < lines.size(); i++) {
			String cleaned = stripComment(lines.get(i));
			if (cleaned.isBlank())
				continue;
			int[] indent = measureIndent(cleaned);
			String content = cleaned.substring(indent[1]).stripTrailing();
			if (content.isEmpty())
				continue;
			flat.add(new Line(i + 1, indent[0], state.substituteOptions(content)));
		}
		return normalizeIndentation(flat);
	}

	private static int[] measureIndent(String line) {
		int chars = 0;
		int width = 0;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == ' ') {
				chars++;
				width++;
				continue;
			}
			if (c == '\t') {
				chars++;
				width += 4;
				continue;
			}
			break;
		}
		return new int[] {width / 4, chars};
	}

	private static String stripComment(String rawLine) {
		StringBuilder result = new StringBuilder(rawLine.length());
		char openQuote = 0;
		for (int i = 0; i < rawLine.length(); i++) {
			char c = rawLine.charAt(i);
			if (openQuote != 0) {
				result.append(c);
				if (c == openQuote && result.charAt(result.length() - 2) != '\\')
					openQuote = 0;
				continue;
			}
			if (c == '"') {
				openQuote = c;
				result.append(c);
				continue;
			}
			if (c == '#' && (i == 0 || Character.isWhitespace(rawLine.charAt(i - 1))))
				break;
			result.append(c);
		}
		return result.toString();
	}

	private static List<Node> normalizeIndentation(List<Line> lines) {
		List<Node> normalized = new ArrayList<>(lines.size());
		java.util.ArrayDeque<Integer> path = new java.util.ArrayDeque<>();
		path.push(-1);
		for (Line line : lines) {
			while (path.size() > 1 && line.indent <= path.peek())
				path.pop();
			if (line.indent > path.peek())
				path.push(line.indent);
			int level = path.size() - 2;
			normalized.add(new Line(line.number(), level, line.content()));
		}
		return normalized;
	}
}
