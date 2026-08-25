package dev.me.master.skript.command;

import dev.me.master.skript.lang.Trigger;

import java.util.List;
import java.util.Locale;

import dev.me.master.skript.lang.ClassInfo;
public final class ScriptCommand {

	private final String name;
	private final List<Arg> arguments;
	private final String description;
	private final String usage;
	private final String permission;
	private final boolean playerOnly;
	private final Trigger trigger;

	public record Arg(String name, ClassInfo<?> type, boolean optional) {
	}

	private ScriptCommand(Builder builder) {
		this.name = builder.name.toLowerCase(Locale.ROOT);
		this.arguments = List.copyOf(builder.arguments);
		this.description = builder.description;
		this.usage = builder.usage;
		this.permission = builder.permission;
		this.playerOnly = builder.playerOnly;
		this.trigger = builder.trigger;
	}

	public String name() {
		return name;
	}

	public List<Arg> arguments() {
		return arguments;
	}

	public String description() {
		return description;
	}

	public String usage() {
		return usage;
	}

	public String permission() {
		return permission;
	}

	public boolean isPlayerOnly() {
		return playerOnly;
	}

	public Trigger trigger() {
		return trigger;
	}

	public static Builder builder(String name) {
		return new Builder(name);
	}

	public static final class Builder {

		private final String name;
		private final List<Arg> arguments = new java.util.ArrayList<>();
		private String description = "";
		private String usage = "";
		private String permission = "";
		private boolean playerOnly = false;
		private Trigger trigger;

		private Builder(String name) {
			this.name = name;
		}

		public Builder addArgument(Arg argument) {
			arguments.add(argument);
			return this;
		}

		public Builder description(String value) {
			if (!value.isBlank())
				description = value;
			return this;
		}

		public Builder usage(String value) {
			if (!value.isBlank())
				usage = value;
			return this;
		}

		public Builder permission(String value) {
			if (!value.isBlank())
				permission = value;
			return this;
		}

		public Builder executableByConsole(boolean allowed) {
			playerOnly = !allowed;
			return this;
		}

		public void trigger(Trigger trigger) {
			this.trigger = trigger;
		}

		public ScriptCommand build() {
			return new ScriptCommand(this);
		}
	}
}
