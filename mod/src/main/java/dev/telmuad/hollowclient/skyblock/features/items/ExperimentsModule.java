package dev.telmuad.hollowclient.skyblock.features.items;

import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import dev.telmuad.hollowclient.skyblock.ui.SkyBlockModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Experimentation Table helpers, display only: Ultrasequencer numbers on the hidden tiles and the next one outlined,
 * the Chronomatron color to press next, Superpairs items remembered on covered tiles. You do every click.
 */
public final class ExperimentsModule extends SkyBlockModule {
	private static ExperimentsModule instance;
	private final Experiments solver = new Experiments();
	private AbstractContainerScreen<?> screen;

	public ExperimentsModule() {
		super("sb_experiments", "Experiment Helpers", "Ultrasequencer, Chronomatron and Superpairs: shows what you saw. Display only; you click.",
				"Items", true);
		instance = this;
	}

	private static Experiments.Game gameFor(String title) {
		Patterns p = Patterns.of("menus");
		if (p.find("ultrasequencer_title", title) != null) return Experiments.Game.ULTRASEQUENCER;
		if (p.find("chronomatron_title", title) != null) return Experiments.Game.CHRONOMATRON;
		if (p.find("superpairs_title", title) != null) return Experiments.Game.SUPERPAIRS;
		return null;
	}

	@Override
	public void onTick() {
		Minecraft mc = Minecraft.getInstance();
		if (!live() || mc.player == null || !(mc.gui.screen() instanceof AbstractContainerScreen<?> s)) {
			screen = null;
			return;
		}
		String title = SbText.clean(s.getTitle().getString());
		Experiments.Game game = gameFor(title);
		if (game == null) {
			screen = null;
			return;
		}
		if (s != screen || solver.game() != game) {
			screen = s;
			solver.reset(game);
		}
		Patterns p = Patterns.of("menus");
		List<Experiments.Tile> board = new ArrayList<>();
		boolean remember = false;
		boolean timer = false;
		for (Slot slot : s.getMenu().slots) {
			if (slot.container == mc.player.getInventory()) continue;
			ItemStack st = slot.getItem();
			String name = SbText.clean(st.getHoverName().getString());
			if (p.find("experiment_remember", name) != null) remember = true;
			if (p.find("experiment_timer", name) != null) timer = true;
			String material = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
			boolean lit = st.hasFoil() || material.endsWith("_terracotta");
			board.add(new Experiments.Tile(slot.index, st.isEmpty() ? "minecraft:air" : material, st.getCount(), st.isEmpty() ? "" : name, lit, color(material)));
		}
		solver.observe(board, remember || !timer);
	}

	static String color(String material) {
		String m = material.substring(material.indexOf(':') + 1);
		for (String suffix : new String[]{"_stained_glass_pane", "_terracotta", "_dye", "_wool", "_stained_glass"}) {
			if (m.endsWith(suffix)) return m.substring(0, m.length() - suffix.length());
		}
		return "";
	}

	/** The player clicked a slot in the open menu (observed; the click itself goes through unchanged). */
	public static void clicked(AbstractContainerScreen<?> s, Slot slot) {
		ExperimentsModule m = instance;
		if (m == null || s != m.screen || slot == null) return;
		m.solver.clicked(slot.index);
	}

	/** Drawn over each slot of the open experiment. */
	public static void overlay(AbstractContainerScreen<?> s, GuiGraphicsExtractor g, Slot slot) {
		ExperimentsModule m = instance;
		if (m == null || s != m.screen || m.solver.game() == null) return;
		var font = Minecraft.getInstance().font;
		switch (m.solver.game()) {
			case ULTRASEQUENCER -> {
				if (m.solver.remembering()) return;
				int n = m.solver.number(slot.index);
				if (n > 0) g.text(font, Integer.toString(n), slot.x + 5, slot.y + 4, 0xFFFFE066, true);
				if (m.solver.nextSlot() == slot.index) outline(g, slot, 0xFF5BD18B);
			}
			case CHRONOMATRON -> {
				String next = m.solver.nextColor();
				if (!next.isEmpty() && next.equals(color(BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString()))) outline(g, slot, 0xFF5BD18B);
			}
			case SUPERPAIRS -> {
				Experiments.Tile t = m.solver.remembered(slot.index);
				if (t == null) return;
				String mat = BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).toString();
				if (!(mat.endsWith("_stained_glass_pane") || slot.getItem().isEmpty())) return; // showing its item right now
				String label = t.name().length() > 6 ? t.name().substring(0, 6) : t.name();
				g.pose().pushMatrix();
				g.pose().translate(slot.x + 1, slot.y + 5);
				g.pose().scale(0.5f, 0.5f);
				g.text(font, label.toLowerCase(Locale.ROOT), 0, 0, 0xFFFFFFFF, true);
				g.pose().popMatrix();
			}
		}
	}

	/** Chronomatron's next color as text (for the player to read). */
	public static String hint() {
		ExperimentsModule m = instance;
		if (m == null || m.screen == null) return "";
		if (m.solver.game() == Experiments.Game.CHRONOMATRON) {
			String c = m.solver.nextColor();
			return c.isEmpty() ? "" : "Next: " + c.replace('_', ' ');
		}
		return "";
	}

	private static void outline(GuiGraphicsExtractor g, Slot slot, int color) {
		g.fill(slot.x - 1, slot.y - 1, slot.x + 17, slot.y, color);
		g.fill(slot.x - 1, slot.y + 16, slot.x + 17, slot.y + 17, color);
		g.fill(slot.x - 1, slot.y, slot.x, slot.y + 16, color);
		g.fill(slot.x + 16, slot.y, slot.x + 17, slot.y + 16, color);
	}
}
