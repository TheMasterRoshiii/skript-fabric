package dev.me.master.skript.lang;

import java.util.List;

@FunctionalInterface
public interface Condition {

	Boolean check(ExecContext context);

	static boolean checkList(List<Condition> conditions, ExecContext context) {
		for (Condition condition : conditions) {
			if (!Boolean.TRUE.equals(condition.check(context)))
				return false;
		}
		return true;
	}
}
