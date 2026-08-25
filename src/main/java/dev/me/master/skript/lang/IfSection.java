package dev.me.master.skript.lang;

import java.util.List;

public final class IfSection extends TriggerItem.Section {

	private final List<Branch> branches;

	public record Branch(List<Condition> conditions, List<TriggerItem> body) {
	}

	public IfSection(int line, List<Branch> branches) {
		super(line, List.of());
		this.branches = List.copyOf(branches);
	}

	public List<Branch> branches() {
		return branches;
	}

	@Override
	protected List<TriggerItem> selectChildren(ExecContext context) {
		for (Branch branch : branches) {
			if (Condition.checkList(branch.conditions(), context))
				return branch.body();
		}
		return List.of();
	}
}
