package dev.scriptkit.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.scriptkit.npc.ArmyManager.Formation;
import java.util.List;
import org.junit.jupiter.api.Test;

class FormationsTest {
	@Test
	void everyShapeGivesOneSlotPerNpcCentredOnZero() {
		for (Formation f : Formation.values()) {
			for (int n : new int[] {1, 2, 5, 12, 40}) {
				List<double[]> l = Formations.layout(f, n, 1.5);
				assertEquals(n, l.size(), f + " " + n);
				double cx = 0;
				double cy = 0;
				for (double[] p : l) {
					cx += p[0];
					cy += p[1];
				}
				assertEquals(0.0, cx / n, 0.9, f + " " + n);
				assertEquals(0.0, cy / n, 0.9, f + " " + n);
			}
		}
	}

	@Test
	void noTwoNpcsShareASpot() {
		for (Formation f : Formation.values()) {
			List<double[]> l = Formations.layout(f, 30, 1.5);
			for (int i = 0; i < l.size(); i++) {
				for (int j = i + 1; j < l.size(); j++) {
					assertTrue(Math.hypot(l.get(i)[0] - l.get(j)[0], l.get(i)[1] - l.get(j)[1]) > 0.5, f + " " + i + "," + j);
				}
			}
		}
	}

	@Test
	void lineIsSideBySideAndColumnIsOneBehindTheOther() {
		for (double[] p : Formations.layout(Formation.LINE, 6, 2.0)) {
			assertEquals(0.0, p[1], 1e-9);
		}
		for (double[] p : Formations.layout(Formation.COLUMN, 6, 2.0)) {
			assertEquals(0.0, p[0], 1e-9);
		}
		List<double[]> line = Formations.layout(Formation.LINE, 4, 2.0);
		assertEquals(2.0, line.get(1)[0] - line.get(0)[0], 1e-9);
	}

	@Test
	void rotationFollowsMinecraftYaw() {
		// Yaw 0 faces +Z: "ahead" is +Z, "right" is -X.
		double[] ahead = Formations.toWorld(0, 1, 0);
		assertEquals(0.0, ahead[0], 1e-9);
		assertEquals(1.0, ahead[1], 1e-9);
		double[] right = Formations.toWorld(1, 0, 0);
		assertEquals(-1.0, right[0], 1e-9);
		// Yaw 90 faces -X.
		double[] ahead90 = Formations.toWorld(0, 1, 90);
		assertEquals(-1.0, ahead90[0], 1e-9);
		assertEquals(0.0, ahead90[1], 1e-6);
	}

	@Test
	void cyclingVisitsEveryShapeAndSpacing() {
		Formation f = Formation.LINE;
		for (int i = 0; i < Formation.values().length; i++) {
			f = Formations.next(f);
		}
		assertEquals(Formation.LINE, f);
		int s = 0;
		for (int i = 0; i < Formations.SPACINGS.length; i++) {
			s = Formations.nextSpacingIndex(s);
		}
		assertEquals(0, s);
		assertEquals(1, Formations.spacingIndex(1.5));
	}
}
