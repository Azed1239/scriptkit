package dev.scriptkit.client.keybinds;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import dev.scriptkit.ScriptKitMod;
import dev.scriptkit.client.keybinds.KeybindsConfig.Binding;
import dev.scriptkit.client.platform.ClientNetworking;
import dev.scriptkit.client.ui.ClientMenu;
import dev.scriptkit.client.ui.ScriptKitScreen;
import dev.scriptkit.network.ArmyAction;
import dev.scriptkit.network.ArmyActionPayload;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.IntPredicate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Client keybinds. Two sources feed the same action packet:
 * <ul>
 *   <li>vanilla {@link KeyMapping}s (rebindable in Controls > ScriptKit): "Toggle all armies" on G and
 *       three unbound team slots whose teams come from keybinds.json;</li>
 *   <li>combos from keybinds.json, matched exactly and edge-triggered by {@link ComboMatcher}.</li>
 * </ul>
 * Nothing here decides what happens: it only sends a request, and the server checks permission and
 * replies with the real result on the action bar.
 */
public final class ClientKeybinds {
	private static final int G = 71;
	private static final int K = 75;

	private static KeyMapping toggleAll;
	private static KeyMapping[] teamSlotKeys;
	private static KeyMapping openMenu;
	private static final boolean[] VANILLA_WAS_DOWN = new boolean[2 + KeybindsConfig.TEAM_SLOTS];

	private static ComboMatcher matcher = new ComboMatcher(List.of());
	private static List<String> teamSlots = List.of("", "", "");

	private ClientKeybinds() {}

	public static void init() {
		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ScriptKitMod.MOD_ID, "main"));
		toggleAll = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.scriptkit.toggle_all", InputConstants.Type.KEYSYM, G, category));
		openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.scriptkit.open_menu", InputConstants.Type.KEYSYM, K, category));
		ClientNetworking.registerMenuReceivers(ScriptKitScreen::open, ClientMenu::onStatePacket);
		teamSlotKeys = new KeyMapping[KeybindsConfig.TEAM_SLOTS];
		for (int i = 0; i < teamSlotKeys.length; i++) {
			teamSlotKeys[i] = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.scriptkit.toggle_team_" + (i + 1), InputConstants.Type.KEYSYM, InputConstants.UNKNOWN.getValue(), category));
		}
		ClientNetworking.registerReloadReceiver(() -> {
			int warnings = reload();
			Minecraft mc = Minecraft.getInstance();
			if (warnings > 0 && mc.player != null) {
				mc.gui.setOverlayMessage(Component.literal("keybinds.json has " + warnings + " warning(s) - see the game log")
					.withStyle(ChatFormatting.YELLOW), false);
			}
		});
		reload();
		ClientTickEvents.END_CLIENT_TICK.register(ClientKeybinds::tick);
	}

	/** The text of keybinds.json, writing the sample file first if there isn't one. */
	public static String readFileText() throws IOException {
		Path path = file();
		if (!Files.exists(path)) {
			Files.createDirectories(path.getParent());
			Files.writeString(path, KeybindsConfig.SAMPLE, StandardCharsets.UTF_8);
		}
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	public static void writeFileText(String text) throws IOException {
		Path path = file();
		Files.createDirectories(path.getParent());
		Files.writeString(path, text, StandardCharsets.UTF_8);
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("scriptkit").resolve("keybinds.json");
	}

	/** (Re)reads keybinds.json, writing the sample file first if it doesn't exist. Returns the number of warnings. */
	public static int reload() {
		Path path = file();
		String json;
		try {
			if (!Files.exists(path)) {
				Files.createDirectories(path.getParent());
				Files.writeString(path, KeybindsConfig.SAMPLE, StandardCharsets.UTF_8);
				ScriptKitMod.LOGGER.info("Wrote sample keybinds to {}", path);
			}
			json = Files.readString(path, StandardCharsets.UTF_8);
		} catch (IOException e) {
			ScriptKitMod.LOGGER.error("Could not read {}; no keybind combos loaded", path, e);
			matcher = new ComboMatcher(List.of());
			return 1;
		}
		KeybindsConfig.Parsed parsed = KeybindsConfig.parse(json);
		for (String warning : parsed.warnings()) {
			ScriptKitMod.LOGGER.warn("keybinds.json: {}", warning);
		}
		matcher = new ComboMatcher(parsed.bindings());
		teamSlots = parsed.teamSlots();
		matcher.reset(ClientKeybinds::isDown);
		ScriptKitMod.LOGGER.info("Loaded {} keybind combo(s) from {}", parsed.bindings().size(), path);
		return parsed.warnings().size();
	}

	private static boolean isDown(int glfwKey) {
		// The window doesn't exist yet while mods initialize, so treat every key as released until it does.
		Window window = Minecraft.getInstance().getWindow();
		return window != null && InputConstants.isKeyDown(window, glfwKey);
	}

	private static void tick(Minecraft mc) {
		IntPredicate down = ClientKeybinds::isDown;
		if (mc.player == null || mc.screen != null) {
			// Chat, inventory, menus: nothing fires, and keys already held won't fire when play resumes.
			matcher.reset(down);
			java.util.Arrays.fill(VANILLA_WAS_DOWN, true);
			return;
		}

		boolean modifier = ComboMatcher.anyModifierDown(down);
		fireVanilla(0, toggleAll, modifier, () -> send(mc, ArmyAction.TOGGLE_ALL, ""));
		fireVanilla(1 + KeybindsConfig.TEAM_SLOTS, openMenu, modifier, () -> ScriptKitScreen.open(""));
		for (int i = 0; i < teamSlotKeys.length; i++) {
			int slot = i;
			fireVanilla(1 + i, teamSlotKeys[i], modifier, () -> {
				String team = teamSlots.get(slot);
				if (team.isEmpty()) {
					mc.gui.setOverlayMessage(Component.literal("Team slot " + (slot + 1) + " has no team - set \"teamSlots\" in keybinds.json")
						.withStyle(ChatFormatting.YELLOW), false);
				} else {
					send(mc, ArmyAction.TOGGLE_TEAM, team);
				}
			});
		}

		for (Binding b : matcher.update(down)) {
			send(mc, b.action(), b.team());
		}
	}

	/** Vanilla mappings are single-key: fire once per press, and never while a modifier is held (that is a combo). */
	private static void fireVanilla(int index, KeyMapping mapping, boolean modifierDown, Runnable action) {
		boolean pressed = mapping.isDown();
		if (pressed && !VANILLA_WAS_DOWN[index] && !modifierDown) {
			action.run();
		}
		VANILLA_WAS_DOWN[index] = pressed;
	}

	private static void send(Minecraft mc, ArmyAction action, String team) {
		if (!ClientNetworking.serverHasScriptKit()) {
			mc.gui.setOverlayMessage(Component.literal("This server doesn't have ScriptKit - use /army instead")
				.withStyle(ChatFormatting.RED), false);
			return;
		}
		ClientNetworking.send(ArmyActionPayload.of(action, team));
	}
}
