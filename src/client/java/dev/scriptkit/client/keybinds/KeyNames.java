package dev.scriptkit.client.keybinds;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Maps names used in keybinds.json to GLFW key codes. Kept free of any Minecraft or LWJGL class so
 * the matching logic can be unit-tested. A name maps to a *group* of codes: "CTRL" is either control
 * key, "LEFT_CONTROL" only the left one.
 */
public final class KeyNames {
	public static final int LEFT_SHIFT = 340;
	public static final int LEFT_CONTROL = 341;
	public static final int LEFT_ALT = 342;
	public static final int RIGHT_SHIFT = 344;
	public static final int RIGHT_CONTROL = 345;
	public static final int RIGHT_ALT = 346;

	/** All modifier keys. They are always tracked so Ctrl+G never also fires a plain G binding. */
	public static final Set<Integer> MODIFIERS =
		Set.of(LEFT_SHIFT, LEFT_CONTROL, LEFT_ALT, RIGHT_SHIFT, RIGHT_CONTROL, RIGHT_ALT);

	private static final Map<String, Set<Integer>> NAMES = new HashMap<>();

	static {
		for (char c = 'A'; c <= 'Z'; c++) {
			put(String.valueOf(c), c);
		}
		for (char c = '0'; c <= '9'; c++) {
			put(String.valueOf(c), c);
			put("KP_" + c, 320 + (c - '0'));
		}
		for (int i = 1; i <= 12; i++) {
			put("F" + i, 289 + i);
		}
		put("SPACE", 32);
		put("APOSTROPHE", 39);
		put("COMMA", 44);
		put("MINUS", 45);
		put("PERIOD", 46);
		put("SLASH", 47);
		put("SEMICOLON", 59);
		put("EQUAL", 61);
		put("LEFT_BRACKET", 91);
		put("BACKSLASH", 92);
		put("RIGHT_BRACKET", 93);
		put("GRAVE_ACCENT", 96);
		put("ESCAPE", 256);
		put("ENTER", 257);
		put("TAB", 258);
		put("BACKSPACE", 259);
		put("INSERT", 260);
		put("DELETE", 261);
		put("RIGHT", 262);
		put("LEFT", 263);
		put("DOWN", 264);
		put("UP", 265);
		put("PAGE_UP", 266);
		put("PAGE_DOWN", 267);
		put("HOME", 268);
		put("END", 269);
		put("CAPS_LOCK", 280);
		put("LEFT_SHIFT", LEFT_SHIFT);
		put("LEFT_CONTROL", LEFT_CONTROL);
		put("LEFT_ALT", LEFT_ALT);
		put("RIGHT_SHIFT", RIGHT_SHIFT);
		put("RIGHT_CONTROL", RIGHT_CONTROL);
		put("RIGHT_ALT", RIGHT_ALT);
		NAMES.put("SHIFT", Set.of(LEFT_SHIFT, RIGHT_SHIFT));
		NAMES.put("CTRL", Set.of(LEFT_CONTROL, RIGHT_CONTROL));
		NAMES.put("CONTROL", Set.of(LEFT_CONTROL, RIGHT_CONTROL));
		NAMES.put("ALT", Set.of(LEFT_ALT, RIGHT_ALT));
	}

	private KeyNames() {}

	private static void put(String name, int code) {
		NAMES.put(name, Set.of(code));
	}

	/** Codes for a key name (case-insensitive, optional {@code KEY_} prefix), or null if unknown. */
	public static Set<Integer> lookup(String name) {
		if (name == null) {
			return null;
		}
		String n = name.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		if (n.startsWith("KEY_")) {
			n = n.substring(4);
		}
		return NAMES.get(n);
	}

	public static boolean isModifierGroup(Set<Integer> group) {
		return MODIFIERS.containsAll(group);
	}
}
