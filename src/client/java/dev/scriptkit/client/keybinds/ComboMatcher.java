package dev.scriptkit.client.keybinds;

import dev.scriptkit.client.keybinds.KeybindsConfig.Binding;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Exact, edge-triggered matching of key combos. Pure logic: the caller supplies "is this GLFW key
 * code down" once per tick.
 *
 * <ul>
 *   <li><b>Exact:</b> among the keys any binding uses (plus all modifiers), a binding matches only
 *       when exactly its keys are down. Ctrl+G is not matched by plain G, and plain G is not matched
 *       while Ctrl is held. Keys no binding uses (for example W) are ignored, so walking doesn't
 *       block a binding.</li>
 *   <li><b>Edge-triggered:</b> a binding fires on the tick it becomes matched and cannot fire again
 *       until it has stopped matching, so holding the keys never repeats.</li>
 * </ul>
 */
public final class ComboMatcher {
	private final List<Binding> bindings;
	private final Set<Integer> tracked = new HashSet<>(KeyNames.MODIFIERS);
	private final boolean[] wasMatched;

	public ComboMatcher(List<Binding> bindings) {
		this.bindings = List.copyOf(bindings);
		this.wasMatched = new boolean[this.bindings.size()];
		for (Binding b : this.bindings) {
			b.groups().forEach(tracked::addAll);
		}
	}

	/** Bindings that fire on this tick. */
	public List<Binding> update(IntPredicate isDown) {
		Set<Integer> down = new HashSet<>();
		for (int code : tracked) {
			if (isDown.test(code)) {
				down.add(code);
			}
		}
		List<Binding> fired = new ArrayList<>();
		for (int i = 0; i < bindings.size(); i++) {
			boolean matched = matches(bindings.get(i), down);
			if (matched && !wasMatched[i]) {
				fired.add(bindings.get(i));
			}
			wasMatched[i] = matched;
		}
		return fired;
	}

	/**
	 * Record the current key state without firing anything. Used while a screen is open (and right
	 * after a reload) so that keys already held do not fire when play resumes.
	 */
	public void reset(IntPredicate isDown) {
		update(isDown);
	}

	private static boolean matches(Binding b, Set<Integer> down) {
		Set<Integer> allowed = new HashSet<>();
		for (Set<Integer> group : b.groups()) {
			boolean any = false;
			for (int code : group) {
				if (down.contains(code)) {
					any = true;
				}
			}
			if (!any) {
				return false;
			}
			allowed.addAll(group);
		}
		return allowed.containsAll(down); // nothing else tracked may be down
	}

	/** True if any modifier key is currently down. */
	public static boolean anyModifierDown(IntPredicate isDown) {
		for (int code : KeyNames.MODIFIERS) {
			if (isDown.test(code)) {
				return true;
			}
		}
		return false;
	}
}
