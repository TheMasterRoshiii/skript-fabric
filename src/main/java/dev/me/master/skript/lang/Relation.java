package dev.me.master.skript.lang;

public enum Relation {
	EQUAL,
	NOT_EQUAL,
	GREATER,
	LESSER;

	public boolean satisfies(Relation expected) {
		if (expected == EQUAL)
			return this == EQUAL;
		if (expected == NOT_EQUAL)
			return this != EQUAL;
		if (expected == GREATER)
			return this == GREATER;
		return this == LESSER;
	}

	public Relation mirrored() {
		return switch (this) {
			case EQUAL -> EQUAL;
			case NOT_EQUAL -> NOT_EQUAL;
			case GREATER -> LESSER;
			case LESSER -> GREATER;
		};
	}
}
