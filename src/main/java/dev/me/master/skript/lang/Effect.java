package dev.me.master.skript.lang;

public abstract class Effect extends TriggerItem.Statement {

	protected Effect(int line) {
		super(line);
	}

	@Override
	protected Flow execute(ExecContext context) {
		run(context);
		return Flow.NORMAL;
	}

	protected abstract void run(ExecContext context);
}
