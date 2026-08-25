package dev.me.master.skript.script;

import dev.me.master.skript.command.ScriptCommand;
import dev.me.master.skript.lang.Trigger;
import dev.me.master.skript.lang.function.FunctionDefinition;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class SkriptScript {

	private final Path file;
	private final String name;
	private final List<Trigger> triggers = new ArrayList<>();
	private final List<ScriptCommand> commands = new ArrayList<>();
	private final List<FunctionDefinition> functions = new ArrayList<>();
	private boolean enabled = true;

	public SkriptScript(Path file) {
		this.file = file;
		this.name = file.getFileName().toString();
	}

	public Path file() {
		return file;
	}

	public String name() {
		return name;
	}

	public List<Trigger> triggers() {
		return triggers;
	}

	public List<ScriptCommand> commands() {
		return commands;
	}

	public List<FunctionDefinition> functions() {
		return functions;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void addTrigger(Trigger trigger) {
		triggers.add(trigger);
	}

	public void addCommand(ScriptCommand command) {
		commands.add(command);
	}

	public void addFunction(FunctionDefinition function) {
		functions.add(function);
	}
}
