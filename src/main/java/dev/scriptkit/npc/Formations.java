package dev.scriptkit.npc;

import dev.scriptkit.npc.ArmyManager.Formation;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pure formation maths (no Minecraft types, so it is unit-tested). A layout is a list of local offsets
 * {lateral, depth} centred on 0: lateral is "to the right of where the army faces", depth is "ahead of
 * the centre" (negative = behind). {@link #toWorld} rotates them by a yaw into world x/z offsets.
 */
public final class Formations {
	/** Spacing choices the Command Stick cycles through (blocks between neighbours). */
	public static final double[] SPACINGS = {1.0, 1.5, 2.0, 3.0, 4.0};
	public static final double DEFAULT_SPACING = 1.5;

	private Formations() {}

	public static List<double[]> layout(Formation shape, int n, double spacing) {
		List<double[]> out = new ArrayList<>(n);
		if (n <= 0) {
			return out;
		}
		if (n == 1) {
			out.add(new double[] {0, 0});
			return out;
		}
		switch (shape) {
			case LINE -> {
				for (int i = 0; i < n; i++) {
					out.add(new double[] {(i - (n - 1) / 2.0) * spacing, 0});
				}
			}
			case COLUMN -> {
				for (int i = 0; i < n; i++) {
					out.add(new double[] {0, ((n - 1) / 2.0 - i) * spacing});
				}
			}
			case GRID -> {
				int cols = (int) Math.ceil(Math.sqrt(n));
				int rows = (int) Math.ceil(n / (double) cols);
				for (int i = 0; i < n; i++) {
					out.add(new double[] {(i % cols - (cols - 1) / 2.0) * spacing, ((rows - 1) / 2.0 - i / cols) * spacing});
				}
			}
			case CIRCLE -> {
				double radius = Math.max(2.0, n * spacing / (2 * Math.PI));
				for (int i = 0; i < n; i++) {
					double a = 2 * Math.PI * i / n;
					out.add(new double[] {Math.cos(a) * radius, Math.sin(a) * radius});
				}
			}
			case WEDGE -> {
				// Tip in front, then pairs fanning out behind it.
				for (int i = 0; i < n; i++) {
					int rank = (i + 1) / 2;
					int side = i == 0 ? 0 : (i % 2 == 1 ? -1 : 1);
					out.add(new double[] {side * rank * spacing, -rank * spacing});
				}
				recentre(out);
			}
			case SCATTER -> {
				Random rnd = new Random(12345);
				double spread = Math.max(2.0, Math.sqrt(n) * spacing * 1.2);
				double minDist = spacing * 0.8;
				for (int i = 0; i < n; i++) {
					double[] p = null;
					for (int tries = 0; tries < 40; tries++) {
						double a = rnd.nextDouble() * 2 * Math.PI;
						double r = Math.sqrt(rnd.nextDouble()) * spread;
						double[] c = {Math.cos(a) * r, Math.sin(a) * r};
						if (p == null || farFromAll(out, c, minDist)) {
							p = c;
							if (farFromAll(out, c, minDist)) {
								break;
							}
						}
					}
					out.add(p);
				}
				recentre(out);
			}
		}
		return out;
	}

	private static boolean farFromAll(List<double[]> placed, double[] c, double minDist) {
		for (double[] o : placed) {
			if (Math.hypot(o[0] - c[0], o[1] - c[1]) < minDist) {
				return false;
			}
		}
		return true;
	}

	private static void recentre(List<double[]> pts) {
		double cx = 0;
		double cy = 0;
		for (double[] p : pts) {
			cx += p[0];
			cy += p[1];
		}
		cx /= pts.size();
		cy /= pts.size();
		for (double[] p : pts) {
			p[0] -= cx;
			p[1] -= cy;
		}
	}

	/** Rotates a local {lateral, depth} offset by a Minecraft yaw (degrees) into world {x, z}. */
	public static double[] toWorld(double lateral, double depth, float yawDegrees) {
		double y = Math.toRadians(yawDegrees);
		double sin = Math.sin(y);
		double cos = Math.cos(y);
		// forward = (-sin, cos), right = (-cos, -sin)
		return new double[] {-cos * lateral - sin * depth, -sin * lateral + cos * depth};
	}

	public static Formation next(Formation current) {
		Formation[] all = Formation.values();
		return all[(current.ordinal() + 1) % all.length];
	}

	public static int nextSpacingIndex(int index) {
		return (index + 1) % SPACINGS.length;
	}

	public static int spacingIndex(double spacing) {
		int best = 0;
		for (int i = 1; i < SPACINGS.length; i++) {
			if (Math.abs(SPACINGS[i] - spacing) < Math.abs(SPACINGS[best] - spacing)) {
				best = i;
			}
		}
		return best;
	}
}
