package dev.scriptkit.client.keybinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.scriptkit.client.keybinds.KeybindsConfig.Parsed;
import dev.scriptkit.network.ArmyAction;
import org.junit.jupiter.api.Test;

class KeybindsConfigTest {
	@Test
	void sampleFileParsesWithoutWarnings() {
		Parsed p = KeybindsConfig.parse(KeybindsConfig.SAMPLE);
		assertTrue(p.warnings().isEmpty(), p.warnings().toString());
		assertEquals(2, p.bindings().size());
		assertEquals(ArmyAction.TOGGLE_TEAM, p.bindings().get(0).action());
		assertEquals("red", p.bindings().get(0).team());
		assertEquals("blue", p.bindings().get(1).team());
		assertEquals("red", p.teamSlots().get(0));
		assertEquals("green", p.teamSlots().get(2));
	}

	@Test
	void badTeamNameWarnsAndSkipsOnlyThatBinding() {
		String json = """
			{ "bindings": [
			  { "action": "toggleTeam", "team": "re d!", "keys": ["LEFT_CONTROL", "R"] },
			  { "action": "toggleAll", "keys": ["G"] }
			] }""";
		Parsed p = KeybindsConfig.parse(json);
		assertEquals(1, p.bindings().size());
		assertEquals(ArmyAction.TOGGLE_ALL, p.bindings().get(0).action());
		assertEquals(1, p.warnings().size());
		assertTrue(p.warnings().get(0).contains("'re d!'"), p.warnings().get(0));
		assertTrue(p.warnings().get(0).contains("bindings[0]"), p.warnings().get(0));
	}

	@Test
	void teamActionWithoutTeamWarns() {
		Parsed p = KeybindsConfig.parse("{ \"bindings\": [ { \"action\": \"enableTeam\", \"keys\": [\"F5\"] } ] }");
		assertTrue(p.bindings().isEmpty());
		assertTrue(p.warnings().get(0).contains("needs a \"team\""), p.warnings().get(0));
	}

	@Test
	void reservedTeamNameAllIsRejected() {
		Parsed p = KeybindsConfig.parse("{ \"bindings\": [ { \"action\": \"toggleTeam\", \"team\": \"all\", \"keys\": [\"F5\"] } ] }");
		assertTrue(p.bindings().isEmpty());
		assertFalse(p.warnings().isEmpty());
	}

	@Test
	void unknownActionListsValidOnes() {
		Parsed p = KeybindsConfig.parse("{ \"bindings\": [ { \"action\": \"explode\", \"keys\": [\"G\"] } ] }");
		assertTrue(p.bindings().isEmpty());
		assertTrue(p.warnings().get(0).contains("unknown action 'explode'"), p.warnings().get(0));
		assertTrue(p.warnings().get(0).contains("toggleAll"), p.warnings().get(0));
	}

	@Test
	void unknownKeyWarns() {
		Parsed p = KeybindsConfig.parse("{ \"bindings\": [ { \"action\": \"toggleAll\", \"keys\": [\"CTRL\", \"BANANA\"] } ] }");
		assertTrue(p.bindings().isEmpty());
		assertTrue(p.warnings().get(0).contains("unknown key 'BANANA'"), p.warnings().get(0));
	}

	@Test
	void modifierOnlyComboIsRejected() {
		Parsed p = KeybindsConfig.parse("{ \"bindings\": [ { \"action\": \"toggleAll\", \"keys\": [\"CTRL\", \"SHIFT\"] } ] }");
		assertTrue(p.bindings().isEmpty());
		assertTrue(p.warnings().get(0).contains("non-modifier"), p.warnings().get(0));
	}

	@Test
	void emptyKeysWarns() {
		Parsed p = KeybindsConfig.parse("{ \"bindings\": [ { \"action\": \"toggleAll\", \"keys\": [] } ] }");
		assertTrue(p.bindings().isEmpty());
		assertFalse(p.warnings().isEmpty());
	}

	@Test
	void notJsonDoesNotThrow() {
		Parsed p = KeybindsConfig.parse("this is { not json");
		assertTrue(p.bindings().isEmpty());
		assertTrue(p.warnings().get(0).contains("not valid JSON"), p.warnings().get(0));
	}

	@Test
	void wrongShapesDoNotThrow() {
		assertFalse(KeybindsConfig.parse("[1,2,3]").warnings().isEmpty());
		assertFalse(KeybindsConfig.parse("{ \"bindings\": [ 5, \"x\", null ] }").warnings().isEmpty());
		assertTrue(KeybindsConfig.parse("{}").warnings().isEmpty());
		assertTrue(KeybindsConfig.parse("").bindings().isEmpty());
	}

	@Test
	void badTeamSlotIsEmptiedWithWarning() {
		Parsed p = KeybindsConfig.parse("{ \"teamSlots\": [\"red\", \"b l u e\", \"green\"] }");
		assertEquals("red", p.teamSlots().get(0));
		assertEquals("", p.teamSlots().get(1));
		assertEquals("green", p.teamSlots().get(2));
		assertEquals(1, p.warnings().size());
	}

	@Test
	void keyNamesAreCaseInsensitiveAndAcceptAliases() {
		assertEquals(KeyNames.lookup("ctrl"), KeyNames.lookup("CONTROL"));
		assertEquals(KeyNames.lookup("key_r"), KeyNames.lookup("R"));
		assertEquals(1, KeyNames.lookup("left_control").size());
		assertEquals(2, KeyNames.lookup("Shift").size());
	}

	@Test
	void duplicateKeysInOneComboAreHarmless() {
		Parsed p = KeybindsConfig.parse("{ \"bindings\": [ { \"action\": \"toggleAll\", \"keys\": [\"G\", \"g\", \"CTRL\"] } ] }");
		assertEquals(1, p.bindings().size());
		assertEquals(2, p.bindings().get(0).groups().size());
	}
}
