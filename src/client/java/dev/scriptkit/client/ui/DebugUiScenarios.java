package dev.scriptkit.client.ui;

import dev.scriptkit.ScriptKitMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Extra smoke-test steps: the item picker sub-screen (open, search, close). */
final class DebugUiScenarios {
	private DebugUiScenarios() {}

	static void openPicker(ScriptKitScreen menu) {
		Minecraft mc = Minecraft.getInstance();
		mc.setScreen(new ItemPickerScreen(menu, "Main hand (smoke test)", id -> ScriptKitMod.LOGGER.info("UI smoke: picked {}", id)));
		ScriptKitMod.LOGGER.info("UI smoke: item picker opened ({} items)", net.minecraft.core.registries.BuiltInRegistries.ITEM.size());
	}

	static void typeInPicker() {
		Screen s = Minecraft.getInstance().screen;
		if (s instanceof ItemPickerScreen) {
			// Typing goes through the real EditBox responder, so the filter code runs.
			s.charTyped(new net.minecraft.client.input.CharacterEvent('s', 0));
			s.charTyped(new net.minecraft.client.input.CharacterEvent('w', 0));
			s.charTyped(new net.minecraft.client.input.CharacterEvent('o', 0));
			ScriptKitMod.LOGGER.info("UI smoke: typed a search into the item picker");
		} else {
			ScriptKitMod.LOGGER.warn("UI smoke: expected the item picker to be open, but screen is {}", s);
		}
	}

	static void closePicker(ScriptKitScreen menu) {
		Minecraft.getInstance().setScreen(menu);
		ScriptKitMod.LOGGER.info("UI smoke: back on the menu");
	}
}
