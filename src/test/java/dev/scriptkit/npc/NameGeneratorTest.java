package dev.scriptkit.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NameGeneratorTest {
	@Test
	void everyNameLooksLikeAMinecraftUsername() {
		Random rnd = new Random(1);
		for (int i = 0; i < 20_000; i++) {
			String name = NameGenerator.generate(rnd, n -> false);
			assertTrue(name.matches("[A-Za-z0-9_]{3,16}"), "bad name: " + name);
		}
	}

	@Test
	void namesAreVariedAndTypical() {
		Random rnd = new Random(2);
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < 2_000; i++) {
			seen.add(NameGenerator.generate(rnd, n -> false));
		}
		assertTrue(seen.size() > 1_200, "expected lots of variety, got " + seen.size());
	}

	@Test
	void hundredArmyMembersAreAllUnique() {
		Random rnd = new Random(3);
		Set<String> taken = new HashSet<>();
		for (int i = 0; i < 100; i++) {
			String name = NameGenerator.generate(rnd, n -> taken.contains(n.toLowerCase()));
			assertTrue(taken.add(name.toLowerCase()), "duplicate: " + name);
		}
		assertEquals(100, taken.size());
	}

	@Test
	void stillTerminatesWhenAlmostEverythingIsTaken() {
		Random rnd = new Random(4);
		// Everything that does not end in a digit is "taken": forces the digit fallback.
		String name = NameGenerator.generate(rnd, n -> !Character.isDigit(n.charAt(n.length() - 1)) || n.length() > 16);
		assertTrue(NameGenerator.valid(name), name);
	}

	@Test
	void showsAFewExamples() {
		Random rnd = new Random(5);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < 12; i++) {
			sb.append(NameGenerator.generate(rnd, n -> false)).append(' ');
		}
		System.out.println("Sample names: " + sb);
		assertTrue(sb.length() > 0);
	}
}
