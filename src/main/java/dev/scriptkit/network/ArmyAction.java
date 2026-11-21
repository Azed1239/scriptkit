package dev.scriptkit.network;

import java.util.Locale;

/** What a keybind (or any client) can ask the server to do to an army. */
public enum ArmyAction {
	TOGGLE_ALL("toggleAll", false, null),
	TOGGLE_TEAM("toggleTeam", true, null),
	ENABLE_ALL("enableAll", false, Boolean.TRUE),
	DISABLE_ALL("disableAll", false, Boolean.FALSE),
	ENABLE_TEAM("enableTeam", true, Boolean.TRUE),
	DISABLE_TEAM("disableTeam", true, Boolean.FALSE);

	private final String id;
	private final boolean needsTeam;
	private final Boolean state;

	ArmyAction(String id, boolean needsTeam, Boolean state) {
		this.id = id;
		this.needsTeam = needsTeam;
		this.state = state;
	}

	/** Name used in keybinds.json and on the wire. */
	public String id() {
		return id;
	}

	public boolean needsTeam() {
		return needsTeam;
	}

	/** Target AI state; null means "toggle". */
	public Boolean state() {
		return state;
	}

	/** Case-insensitive lookup by {@link #id()}; null if unknown. */
	public static ArmyAction parse(String id) {
		if (id == null) {
			return null;
		}
		String lower = id.toLowerCase(Locale.ROOT);
		for (ArmyAction a : values()) {
			if (a.id.toLowerCase(Locale.ROOT).equals(lower)) {
				return a;
			}
		}
		return null;
	}

	public static String validIds() {
		StringBuilder sb = new StringBuilder();
		for (ArmyAction a : values()) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(a.id);
		}
		return sb.toString();
	}

	/** Team names must be plain scoreboard-safe words; "all" is reserved. */
	public static boolean isValidTeamName(String team) {
		return team != null && team.matches("[A-Za-z0-9_.+\\-]{1,64}") && !team.equalsIgnoreCase("all");
	}
}
