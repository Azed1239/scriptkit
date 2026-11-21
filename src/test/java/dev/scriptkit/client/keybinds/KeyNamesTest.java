package dev.scriptkit.client.keybinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The key-capture box in the Keybinds tab turns a pressed key code back into a name. */
class KeyNamesTest {
	@Test
	void lettersDigitsAndFunctionKeysRoundTrip() {
		assertEquals("R", KeyNames.nameOf('R'));
		assertEquals("G", KeyNames.nameOf('G'));
		assertEquals("1", KeyNames.nameOf('1'));
		assertEquals("F5", KeyNames.nameOf(294));
	}

	@Test
	void everyNamedKeyMapsBackToItself() {
		for (String name : new String[] {"A", "Z", "0", "9", "F1", "F12", "SPACE", "TAB", "ENTER", "UP", "LEFT_SHIFT", "RIGHT_ALT"}) {
			Integer code = KeyNames.lookup(name).iterator().next();
			assertEquals(name, KeyNames.nameOf(code), "round trip for " + name);
		}
	}

	@Test
	void unsupportedKeysHaveNoName() {
		assertNull(KeyNames.nameOf(999));
		assertNull(KeyNames.nameOf(-1));
	}

	@Test
	void modifierKeysAreNamedButFlaggedAsModifiers() {
		assertNotNull(KeyNames.nameOf(KeyNames.LEFT_CONTROL));
		assertEquals(true, KeyNames.MODIFIERS.contains(KeyNames.LEFT_CONTROL));
	}
}
