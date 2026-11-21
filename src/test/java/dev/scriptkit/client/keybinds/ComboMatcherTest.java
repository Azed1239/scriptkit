package dev.scriptkit.client.keybinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.scriptkit.client.keybinds.KeybindsConfig.Binding;
import dev.scriptkit.network.ArmyAction;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ComboMatcherTest {
	private static final int CTRL = KeyNames.LEFT_CONTROL;
	private static final int RCTRL = KeyNames.RIGHT_CONTROL;
	private static final int SHIFT = KeyNames.LEFT_SHIFT;
	private static final int R = 'R';
	private static final int G = 'G';
	private static final int W = 'W';

	/** A tiny fake keyboard: the set of GLFW codes currently down. */
	private final Set<Integer> down = new HashSet<>();

	private void press(int... keys) {
		for (int k : keys) {
			down.add(k);
		}
	}

	private void release(int... keys) {
		for (int k : keys) {
			down.remove(k);
		}
	}

	private static Binding combo(ArmyAction action, String team, Set<Integer>... groups) {
		return new Binding(action, team, List.of(groups), "test");
	}

	private static Binding ctrlR() {
		return combo(ArmyAction.TOGGLE_TEAM, "red", Set.of(CTRL), Set.of((int) 'R'));
	}

	private static Binding plainR() {
		return combo(ArmyAction.TOGGLE_ALL, "", Set.of((int) 'R'));
	}

	@Test
	void ctrlRFiresOnlyWhenBothDown() {
		ComboMatcher m = new ComboMatcher(List.of(ctrlR()));
		press(R);
		assertTrue(m.update(down::contains).isEmpty(), "plain R must not fire Ctrl+R");
		release(R);
		press(CTRL);
		assertTrue(m.update(down::contains).isEmpty(), "Ctrl alone must not fire");
		press(R);
		assertEquals(1, m.update(down::contains).size());
	}

	@Test
	void plainRBindingIsNotFiredByCtrlR() {
		ComboMatcher m = new ComboMatcher(List.of(plainR(), ctrlR()));
		press(CTRL, R);
		List<Binding> fired = m.update(down::contains);
		assertEquals(1, fired.size());
		assertEquals(ArmyAction.TOGGLE_TEAM, fired.get(0).action());
	}

	@Test
	void plainRBindingFiresWhenNoModifier() {
		ComboMatcher m = new ComboMatcher(List.of(plainR(), ctrlR()));
		press(R);
		List<Binding> fired = m.update(down::contains);
		assertEquals(1, fired.size());
		assertEquals(ArmyAction.TOGGLE_ALL, fired.get(0).action());
	}

	@Test
	void extraModifierBlocksTheMatch() {
		ComboMatcher m = new ComboMatcher(List.of(ctrlR()));
		press(CTRL, SHIFT, R);
		assertTrue(m.update(down::contains).isEmpty(), "Ctrl+Shift+R must not fire Ctrl+R");
	}

	@Test
	void untrackedKeysLikeWalkingAreIgnored() {
		ComboMatcher m = new ComboMatcher(List.of(ctrlR()));
		press(W, CTRL, R);
		assertEquals(1, m.update(down::contains).size(), "holding W must not block the combo");
	}

	@Test
	void holdingDoesNotRepeat() {
		ComboMatcher m = new ComboMatcher(List.of(ctrlR()));
		press(CTRL, R);
		assertEquals(1, m.update(down::contains).size());
		for (int tick = 0; tick < 200; tick++) {
			assertTrue(m.update(down::contains).isEmpty(), "must fire once per press, tick " + tick);
		}
	}

	@Test
	void releasingAnyKeyAllowsFiringAgain() {
		ComboMatcher m = new ComboMatcher(List.of(ctrlR()));
		press(CTRL, R);
		assertEquals(1, m.update(down::contains).size());
		release(R);
		assertTrue(m.update(down::contains).isEmpty());
		press(R);
		assertEquals(1, m.update(down::contains).size(), "second press fires again");
		release(CTRL);
		m.update(down::contains);
		press(CTRL);
		assertEquals(1, m.update(down::contains).size(), "re-pressing Ctrl while R is held also counts as a new press");
	}

	@Test
	void ctrlAliasAcceptsEitherSide() {
		Binding either = combo(ArmyAction.TOGGLE_ALL, "", KeyNames.lookup("CTRL"), KeyNames.lookup("G"));
		ComboMatcher m = new ComboMatcher(List.of(either));
		press(RCTRL, G);
		assertEquals(1, m.update(down::contains).size());
	}

	@Test
	void leftControlOnlyRejectsRightControl() {
		ComboMatcher m = new ComboMatcher(List.of(ctrlR()));
		press(RCTRL, R);
		assertTrue(m.update(down::contains).isEmpty(), "LEFT_CONTROL binding must not fire for the right Ctrl key");
	}

	@Test
	void resetSwallowsKeysAlreadyHeld() {
		ComboMatcher m = new ComboMatcher(List.of(ctrlR()));
		press(CTRL, R);
		m.reset(down::contains); // e.g. a screen was open
		assertTrue(m.update(down::contains).isEmpty(), "keys held across a screen must not fire afterwards");
		release(R);
		m.update(down::contains);
		press(R);
		assertEquals(1, m.update(down::contains).size());
	}

	@Test
	void anyModifierDownDetectsEveryModifier() {
		assertFalse(ComboMatcher.anyModifierDown(down::contains));
		for (int mod : KeyNames.MODIFIERS) {
			down.clear();
			down.add(mod);
			assertTrue(ComboMatcher.anyModifierDown(down::contains));
		}
	}
}
