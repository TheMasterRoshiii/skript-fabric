package dev.me.master.skript.lang;

import dev.me.master.skript.script.SkriptScript;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ParseState {

	public final SkriptScript script;
	public final Map<String, String> options = new HashMap<>();
	public final SkriptLoggerSink sink;
    public @Nullable String uncancellableEvent;
	private int errorCount;
	private int lastErrorLine = -1;
	private int lineNumber;
	private String lineContent = "";
	private String parsedContent = "";
	private List<String> sourceLines = List.of();
	private int errorOffset;
	private String failureFragment = "";
	private String failureReason = "";
	private String expectedSyntax = "";

	public ParseState(SkriptScript script, SkriptLoggerSink sink) {
		this.script = script;
		this.sink = message -> {
			this.errorCount++;
			this.lastErrorLine = this.lineNumber;
			String location = this.script.file().toAbsolutePath().normalize().toString();
			if (this.lineNumber > 0) {
				location += ":" + this.lineNumber + ":" + (this.errorOffset + 1);
			}
			String source = this.lineContent.isEmpty() ? ""
					: "\n    " + this.lineContent.substring(0, this.errorOffset) + "<--[HERE] "
							+ this.lineContent.substring(this.errorOffset);
			sink.error(location + ": " + message + source);
		};
	}

	public void setSource(List<String> lines) {
		this.sourceLines = List.copyOf(lines);
	}

	public void setLine(int number, String content) {
		if (this.lineNumber != number || !this.parsedContent.equals(content)) {
			this.clearFailure();
		}
		this.lineNumber = number;
		this.parsedContent = content;
		this.lineContent = number > 0 && number <= this.sourceLines.size()
				? this.sourceLines.get(number - 1) : content;
		this.errorOffset = this.contentOffset();
	}

	public int errorCount() {
		return this.errorCount;
	}

    public int lineNumber() {
        return this.lineNumber;
    }

	public boolean hasLineError() {
		return this.lastErrorLine == this.lineNumber;
	}

	public void rememberFailure(String fragment, String reason) {
		int offset = this.parsedContent.indexOf(fragment);
		int previousOffset = this.parsedContent.indexOf(this.failureFragment);
		if (this.failureReason.isEmpty() || offset > previousOffset
				|| (offset == previousOffset && fragment.length() < this.failureFragment.length())) {
			this.failureFragment = fragment;
			this.failureReason = reason;
		}
	}

	public void expectSyntax(String syntax) {
		this.expectedSyntax = syntax;
	}

	public void clearFailure() {
		this.failureFragment = "";
		this.failureReason = "";
		this.expectedSyntax = "";
	}

	public void reportFailure(String fallback) {
		String reason = this.failureReason.isEmpty() ? fallback : this.failureReason;
		if (!this.expectedSyntax.isEmpty()) {
			reason += ". Expected syntax: " + this.expectedSyntax;
		}
		this.errorAt(this.failureFragment, reason);
	}

	public void errorAt(String fragment, String message) {
		int offset = fragment.isEmpty() ? -1 : this.lineContent.indexOf(fragment, this.contentOffset());
		if (offset >= 0 && this.lineContent.indexOf(fragment, offset + 1) < 0) {
			this.errorOffset = offset;
		}
		try {
			this.sink.error(message);
		} finally {
			this.errorOffset = this.contentOffset();
		}
	}

	private int contentOffset() {
		int offset = 0;
		while (offset < this.lineContent.length() && Character.isWhitespace(this.lineContent.charAt(offset))) {
			offset++;
		}
		return offset;
	}

	public boolean hasErrors() {
		return this.errorCount > 0;
	}

	public String substituteOptions(String line) {
		if (!line.contains("{@")) {
			return line;
		}
		String result = line;
		for (Map.Entry<String, String> option : this.options.entrySet()) {
			result = result.replace("{@" + option.getKey() + "}", option.getValue());
		}
		return result;
	}

	@FunctionalInterface
	public interface SkriptLoggerSink {
		void error(String message);
	}
}
