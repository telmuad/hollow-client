package dev.telmuad.hollowclient.skyblock.ui;

import dev.telmuad.hollowclient.gui.Rect;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.gui.Toasts;
import dev.telmuad.hollowclient.gui.Ui;
import dev.telmuad.hollowclient.gui.widget.UiButton;
import dev.telmuad.hollowclient.input.Keybinds;
import dev.telmuad.hollowclient.skyblock.SkyBlock;
import dev.telmuad.hollowclient.skyblock.data.Election;
import dev.telmuad.hollowclient.skyblock.data.Prices;
import dev.telmuad.hollowclient.skyblock.data.SkyCalendar;
import dev.telmuad.hollowclient.skyblock.event.EventBus;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.mc.GameReader;
import dev.telmuad.hollowclient.skyblock.parse.ItemParser;
import dev.telmuad.hollowclient.skyblock.state.Island;
import dev.telmuad.hollowclient.skyblock.state.SessionStats;
import dev.telmuad.hollowclient.skyblock.state.SkyBlockState;
import dev.telmuad.hollowclient.skyblock.text.SbText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The SkyBlock Hub (H): where you are, this session, what's coming up, the held item's value, the mayor, the latest
 * SkyBlock events, and the tools (setup checker, feature health, alerts, compatibility, the recorder). Display only.
 */
public final class SkyBlockHubScreen extends SbScreen {
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

	private final List<UiButton> tools = new ArrayList<>();
	private final UiButton recorder = new UiButton("", this::toggleRecorder);
	private final UiButton refresh = new UiButton("Refresh prices", this::refreshData);
	private final UiButton dump = new UiButton("Save held item", this::dumpHeld);
	private Rect[] cards = new Rect[0];
	private String note = "";

	public SkyBlockHubScreen(Screen parent) {
		super("SkyBlock Hub", parent);
		tools.add(new UiButton("Setup checker", () -> open(new SetupCheckerScreen(this))).style(UiButton.Style.PRIMARY));
		tools.add(new UiButton("Feature health", () -> open(new FeatureHealthScreen(this))));
		tools.add(new UiButton("Alerts", () -> open(new AlertsScreen(this))));
		tools.add(new UiButton("Compatibility", () -> open(new CompatibilityScreen(this))));
		tools.add(new UiButton("API key", () -> open(new ApiKeyScreen(this))));
		tools.add(recorder);
		tools.add(refresh);
		tools.add(dump);
	}

	@Override
	protected void init() {
		super.init();
		SkyBlock.wantData(true);
		// the tools: a row of buttons along the bottom (more rows when they don't fit)
		int n = tools.size();
		int[] xs = new int[n], ws = new int[n], rowOf = new int[n];
		int x = body.x();
		int row = 0;
		for (int i = 0; i < n; i++) {
			int w = Math.max(60, tools.get(i).preferredWidth(font) + 16);
			if (x + w > body.right() && x > body.x()) {
				row++;
				x = body.x();
			}
			xs[i] = x;
			ws[i] = w;
			rowOf[i] = row;
			x += w + 6;
		}
		int toolsH = (row + 1) * 16 + row * 4;
		for (int i = 0; i < n; i++) tools.get(i).bounds(new Rect(xs[i], body.bottom() - toolsH + rowOf[i] * 20, ws[i], 16));
		Rect area = new Rect(body.x(), body.y(), body.w(), body.h() - toolsH - 8);
		int cols = area.w() >= 360 ? 3 : 2;
		int rowsN = (5 + cols - 1) / cols;
		int cw = (area.w() - 8 * (cols - 1)) / cols;
		int ch = (area.h() - 8 * (rowsN - 1)) / rowsN;
		cards = new Rect[5];
		for (int i = 0; i < 5; i++) {
			int c = i % cols, r = i / cols;
			cards[i] = new Rect(area.x() + c * (cw + 8), area.y() + r * (ch + 8), cw, ch);
		}
	}

	@Override
	public void removed() {
		SkyBlock.wantData(false);
		super.removed();
	}

	@Override
	protected String subtitle() {
		SkyBlockState s = SkyBlock.state();
		if (!s.onSkyBlock()) return "Not on SkyBlock right now. Join SkyBlock on Hypixel, or try /hollow sbsim hub in singleplayer.";
		String where = s.island() == Island.UNKNOWN ? "SkyBlock" : s.island().displayName();
		if (!s.area().isEmpty() && !s.area().equals(where)) where += " · " + s.area();
		String date = SkyCalendar.date(System.currentTimeMillis() / 1000).toString();
		return where + "   ·   " + date + (s.profileName().isEmpty() ? "" : "   ·   " + s.profileName()
				+ (s.gameMode().symbol.isEmpty() ? "" : " " + s.gameMode().symbol));
	}

	@Override
	protected String footer() {
		if (!note.isEmpty()) return note;
		Prices p = SkyBlock.data().prices();
		String status = p.loaded() ? "Prices from " + ago(Math.max(p.bazaarAt(), p.lbinAt())) : "Prices not loaded yet";
		status += SkyBlock.data().neu().available() ? " · item repo ✓" : " · item repo not downloaded yet";
		return status + " · H closes · display only";
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, int mx, int my, float a) {
		drawFrame(g, mx, my, a);
		session(g, cards[0], a);
		calendar(g, cards[1], a);
		heldItem(g, cards[2], a);
		mayor(g, cards[3], a);
		events(g, cards[4], a);
		recorder.setLabel(SkyBlock.recorder().active() ? "Stop recording" : "Record session");
		refresh.setEnabled(!SkyBlock.data().refreshing());
		float ta = a * appear(5);
		for (UiButton b : tools) b.render(g, font, mx, my, dt, ta);
	}

	private void session(GuiGraphicsExtractor g, Rect r, float a) {
		float ca = card(g, r, "This session", 0, a);
		if (ca <= 0) return;
		pushCard(g, 0);
		int y = r.y() + 22;
		List<SessionStats.Row> rows = SkyBlock.session().rows();
		if (!SkyBlock.state().onSkyBlock()) {
			dev.telmuad.hollowclient.gui.Text.wrapped(g, font, "Starts when you join SkyBlock.", r.x() + 8, y, r.w() - 16, 3, Theme.fade(Theme.TEXT_FAINT, ca));
		} else {
			for (SessionStats.Row row : rows) {
				if (y > r.bottom() - 12) break;
				row(g, row.label(), row.value(), r.x() + 8, y, r.w() - 16, row.label().equals("Purse") ? GOLD : 0, ca);
				y += 11;
			}
		}
		g.pose().popMatrix();
	}

	private void calendar(GuiGraphicsExtractor g, Rect r, float a) {
		float ca = card(g, r, "Coming up", 1, a);
		if (ca <= 0) return;
		pushCard(g, 1);
		long now = System.currentTimeMillis() / 1000;
		Election.Mayor mayor = SkyBlock.data().election().mayor();
		int y = r.y() + 22;
		for (SkyCalendar.Event e : SkyCalendar.events(now, mayor.allPerks(), 10)) {
			if (y > r.bottom() - 12) break;
			String when = e.running(now) ? SbText.duration((e.end() - now) * 1000) + " left"
					: "in " + SbText.duration((e.start() - now) * 1000);
			row(g, e.name(), when, r.x() + 8, y, r.w() - 16, e.running(now) ? OK : 0, ca);
			y += 11;
		}
		g.pose().popMatrix();
	}

	private void heldItem(GuiGraphicsExtractor g, Rect r, float a) {
		float ca = card(g, r, "Held item", 2, a);
		if (ca <= 0) return;
		pushCard(g, 2);
		int y = r.y() + 22;
		ItemStack stack = minecraft == null || minecraft.player == null ? ItemStack.EMPTY : minecraft.player.getMainHandItem();
		ItemParser.SbItem item = stack.isEmpty() ? null : GameReader.raw(-1, stack).parse();
		if (item == null || !item.isSkyBlock()) {
			dev.telmuad.hollowclient.gui.Text.wrapped(g, font, stack.isEmpty() ? "Hold a SkyBlock item to see its value." : "That isn't a SkyBlock item.",
					r.x() + 8, y, r.w() - 16, 3, Theme.fade(Theme.TEXT_FAINT, ca));
		} else {
			Ui.draw(g, font, Ui.fit(font, item.name(), r.w() - 16), r.x() + 8, y, Theme.fade(Theme.TEXT, ca));
			y += 12;
			Prices.Estimate v = SkyBlock.data().prices().value(item, ItemParser.petLevel(item));
			String value = v.source() == Prices.Source.NONE ? "no price yet" : SbText.shortNumber(v.value() * item.count()) + " coins";
			String source = switch (v.source()) {
				case BAZAAR -> "Value (bazaar sell)";
				case LOWEST_BIN -> "Value (lowest BIN)";
				case NPC -> "Value (NPC)";
				case NONE -> "Value";
			};
			row(g, source, value, r.x() + 8, y, r.w() - 16, GOLD, ca);
			y += 11;
			row(g, "Rarity", item.rarity().name().replace('_', ' ') + (item.recombobulated() ? " (recombobulated)" : ""), r.x() + 8, y, r.w() - 16, 0, ca);
			y += 11;
			if (!item.reforge().isEmpty()) {
				row(g, "Reforge", item.reforge(), r.x() + 8, y, r.w() - 16, 0, ca);
				y += 11;
			}
			if (item.stars() > 0) {
				row(g, "Stars", Integer.toString(item.stars()), r.x() + 8, y, r.w() - 16, 0, ca);
				y += 11;
			}
			if (!item.enchantments().isEmpty() && y < r.bottom() - 12) {
				row(g, "Enchantments", Integer.toString(item.enchantments().size()), r.x() + 8, y, r.w() - 16, 0, ca);
				y += 11;
			}
			if (item.hotPotatoes() > 0 && y < r.bottom() - 12) row(g, "Potato books", Integer.toString(item.hotPotatoes()), r.x() + 8, y, r.w() - 16, 0, ca);
		}
		g.pose().popMatrix();
	}

	private void mayor(GuiGraphicsExtractor g, Rect r, float a) {
		float ca = card(g, r, "Mayor", 3, a);
		if (ca <= 0) return;
		pushCard(g, 3);
		Election.Mayor m = SkyBlock.data().election().mayor();
		int y = r.y() + 22;
		if (m.name().isEmpty()) {
			dev.telmuad.hollowclient.gui.Text.wrapped(g, font, "Loads on SkyBlock (from Hypixel's public API).", r.x() + 8, y, r.w() - 16, 3,
					Theme.fade(Theme.TEXT_FAINT, ca));
		} else {
			Ui.draw(g, font, Ui.bold(m.name()), r.x() + 8, y, Theme.fade(Theme.accent(), ca));
			y += 12;
			for (String p : m.perks()) {
				if (y > r.bottom() - 24) break;
				Ui.draw(g, font, Ui.fit(font, "· " + p, r.w() - 16), r.x() + 8, y, Theme.fade(Theme.TEXT_DIM, ca));
				y += 10;
			}
			if (!m.minister().isEmpty()) {
				Ui.draw(g, font, Ui.fit(font, "Minister " + m.minister() + ": " + m.ministerPerk(), r.w() - 16), r.x() + 8, r.bottom() - 13,
						Theme.fade(Theme.TEXT_FAINT, ca));
			}
		}
		g.pose().popMatrix();
	}

	private void events(GuiGraphicsExtractor g, Rect r, float a) {
		float ca = card(g, r, "Latest SkyBlock events", 4, a);
		if (ca <= 0) return;
		pushCard(g, 4);
		int y = r.y() + 22;
		int shown = 0;
		String previous = null;
		for (EventBus.Logged l : SkyBlock.bus().recent()) {
			String text = describe(l.event());
			if (text == null || text.equals(previous)) continue;
			previous = text;
			if (y > r.bottom() - 12) break;
			String time = LocalTime.ofInstant(Instant.ofEpochMilli(l.time()), ZoneId.systemDefault()).format(CLOCK);
			Ui.draw(g, font, time, r.x() + 8, y, Theme.fade(Theme.TEXT_FAINT, ca));
			Ui.draw(g, font, Ui.fit(font, text, r.w() - 50), r.x() + 40, y, Theme.fade(Theme.TEXT_DIM, ca));
			y += 11;
			shown++;
		}
		if (shown == 0) Ui.draw(g, font, "Nothing yet.", r.x() + 8, y, Theme.fade(Theme.TEXT_FAINT, ca));
		g.pose().popMatrix();
	}

	/** A short line for the event log; null for the noisy ones (every chat line). */
	static String describe(SbEvent e) {
		return switch (e) {
			case SbEvent.JoinedSkyBlock j -> "Joined SkyBlock";
			case SbEvent.LeftSkyBlock l -> "Left SkyBlock";
			case SbEvent.IslandChanged i -> "Island: " + i.to().displayName();
			case SbEvent.AreaChanged ar -> "Area: " + ar.to();
			case SbEvent.ProfileChanged p -> "Profile: " + p.name();
			case SbEvent.RareDrop d -> d.kind() + ": " + d.item() + (d.magicFind() > 0 ? " (+" + d.magicFind() + "% MF)" : "");
			case SbEvent.SlayerQuestStarted s -> "Slayer quest started";
			case SbEvent.SlayerBossSpawned s -> "Boss spawned: " + s.boss();
			case SbEvent.SlayerQuestCompleted s -> "Slayer quest complete";
			case SbEvent.SlayerQuestFailed s -> "Slayer quest failed";
			case SbEvent.SkillLevelUp s -> s.skill() + " " + s.level();
			case SbEvent.VisitorArrived v -> "Visitor: " + v.name();
			case SbEvent.ContainerOpened c -> "Opened " + c.title();
			default -> null;
		};
	}

	private static String ago(long at) {
		if (at <= 0) return "never";
		long ms = System.currentTimeMillis() - at;
		return ms < 60_000 ? "just now" : SbText.duration(ms) + " ago";
	}

	private void toggleRecorder() {
		if (SkyBlock.recorder().active()) {
			Path f = SkyBlock.recorder().stop();
			SkyBlock.input().setRecorder(null);
			note = f == null ? "" : "Saved " + SkyBlock.recorder().lines() + " lines to hollow-debug/" + f.getFileName();
			return;
		}
		try {
			var mc = minecraft;
			SkyBlock.recorder().setNames(() -> GameReader.playerNames(mc), mc.player == null ? "" : mc.player.getGameProfile().name());
			SkyBlock.input().setRecorder(SkyBlock.recorder());
			Path f = SkyBlock.recorder().start();
			note = "Recording to hollow-debug/" + f.getFileName() + ". Other players' chat is never saved.";
		} catch (IOException e) {
			note = "Couldn't start the recorder: " + e.getMessage();
		}
	}

	private void refreshData() {
		var f = SkyBlock.data().refresh(true);
		note = f == null ? "Already refreshing." : "Refreshing prices and the item repository...";
		if (f != null) f.thenRun(() -> Toasts.success("SkyBlock data", "Prices refreshed."));
	}

	private void dumpHeld() {
		if (minecraft == null || minecraft.player == null || minecraft.player.getMainHandItem().isEmpty()) {
			note = "Hold the item you want to save.";
			return;
		}
		try {
			Path f = SkyBlock.recorder().writeDump("item", GameReader.raw(-1, minecraft.player.getMainHandItem()).toJson());
			note = "Saved the item to hollow-debug/" + f.getFileName();
		} catch (IOException e) {
			note = "Couldn't save: " + e.getMessage();
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (closing()) return true;
		if (event.button() == 0) for (UiButton b : tools) if (b.click(event.x(), event.y())) return true;
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (Keybinds.hubKey() != null && Keybinds.hubKey().matches(event)) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}
}
