package dev.me.master.skript.lang;

import dev.me.master.skript.script.SkriptScript;

import java.util.HashMap;
import java.util.Map;

public final class ParseState {

	public final SkriptScript script;
	public final Map<String, String> options = new HashMap<>();
	public final SkriptLoggerSink sink;
	private int errorCount;

	public ParseState(SkriptScript script, SkriptLoggerSink sink) {
		this.script = script;
		this.sink = message -> {
			errorCount++;
			sink.error(message);
		};
	}

	public boolean hasErrors() {
		return errorCount > 0;
	}

	public String substituteOptions(String line) {
		if (!line.contains("{@"))
			return line;
		String result = line;
		for (Map.Entry<String, String> option : options.entrySet()) {
			result = result.replace("{@" + option.getKey() + "}", option.getValue());
		}
		return result;
	}

	@FunctionalInterface
	public interface SkriptLoggerSink {
		void error(String message);
	}
}
