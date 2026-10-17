package dev.telmuad.hollowclient.skyblock.state;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.skyblock.event.EventBus;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.parse.ActionBarParser;
import dev.telmuad.hollowclient.skyblock.parse.ChatParser;
import dev.telmuad.hollowclient.skyblock.parse.ItemParser;
import dev.telmuad.hollowclient.skyblock.parse.SidebarParser;
import dev.telmuad.hollowclient.skyblock.parse.TabListParser;
import dev.telmuad.hollowclient.skyblock.text.SbText;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * The one way game text gets into SkyBlock Mode. The game adapter, the simulator and the tests all call these methods
 * with plain strings, so everything after this point runs the same way in all three. Each call (1) goes to the debug
 * recorder when it's on, (2) is parsed, (3) updates {@link SkyBlockState} and (4) posts {@link SbEvent}s.
 * Call it on the client thread. Plain Java.
 */
public final class SkyBlockInput {
	/** Receives every input as one JSON object ({@code type} plus its data), for the debug recorder. */
	public interface Recorder {
		void record(JsonObject entry);
	}

	/** The open container: its title and the parsed items by slot. */
	public record Container(String title, List<RawItem> raw, List<ItemParser.SbItem> items) {
	}

	private final SkyBlockState state;
	private final EventBus bus;
	private final LongSupplier clock;
	private Recorder recorder;
	private Container container;
	private List<String> lastSidebar = List.of();
	private String lastSidebarTitle = "";
	private List<String> lastTab = List.of();

	public SkyBlockInput(SkyBlockState state, EventBus bus, LongSupplier clock) {
		this.state = state;
		this.bus = bus;
		this.clock = clock;
		state.clock = clock;
	}

	public SkyBlockState state() {
		return state;
	}

	public EventBus bus() {
		return bus;
	}

	public void setRecorder(Recorder recorder) {
		this.recorder = recorder;
	}

	public Container container() {
		return container;
	}

	private void record(String type, JsonObject data) {
		Recorder r = recorder;
		if (r == null) return;
		data.addProperty("type", type);
		r.record(data);
	}

	// ------------------------------------------------------------------------------------------------ location

	/** From the Hypixel Mod API (or the simulator): exact server type and island. */
	public void location(Location loc) {
		JsonObject j = new JsonObject();
		j.addProperty("serverName", loc.serverName());
		j.addProperty("serverType", loc.serverType());
		j.addProperty("lobbyName", loc.lobbyName());
		j.addProperty("mode", loc.mode());
		j.addProperty("map", loc.map());
		record("location", j);
		state.location = loc;
		state.fromModApi = true;
		setOnSkyBlock(loc.isSkyBlock());
		if (loc.isSkyBlock()) setIsland(loc.island());
		if (!loc.serverName().isEmpty()) state.serverId = loc.serverName();
	}

	/** Disconnected from the server. */
	public void left() {
		JsonObject j = new JsonObject();
		record("left", j);
		boolean was = state.onSkyBlock;
		state.reset();
		lastSidebar = List.of();
		lastTab = List.of();
		container = null;
		if (was) bus.post(new SbEvent.LeftSkyBlock());
	}

	private void setOnSkyBlock(boolean on) {
		if (on == state.onSkyBlock) return;
		state.onSkyBlock = on;
		if (on) {
			state.joinedAt = clock.getAsLong();
			bus.post(new SbEvent.JoinedSkyBlock());
		} else {
			Island old = state.island;
			state.island = Island.UNKNOWN;
			state.area = "";
			if (old != Island.UNKNOWN) bus.post(new SbEvent.IslandChanged(old, Island.UNKNOWN));
			bus.post(new SbEvent.LeftSkyBlock());
		}
	}

	private void setIsland(Island island) {
		if (island == state.island) return;
		Island old = state.island;
		state.island = island;
		bus.post(new SbEvent.IslandChanged(old, island));
	}

	// ------------------------------------------------------------------------------------------------ sidebar and tab

	/** The scoreboard sidebar, top to bottom. Only changes are recorded and parsed. */
	public void sidebar(String title, List<String> lines) {
		long now = clock.getAsLong();
		state.sidebarAt = now;
		if (title.equals(lastSidebarTitle) && lines.equals(lastSidebar)) return;
		lastSidebarTitle = title;
		lastSidebar = List.copyOf(lines);
		JsonObject j = new JsonObject();
		j.addProperty("title", title);
		j.add("lines", array(lines));
		record("sidebar", j);

		SidebarParser.Sidebar sb = SidebarParser.parse(title, lines);
		state.sidebar = sb;
		if (!state.fromModApi) setOnSkyBlock(sb.skyblock());
		if (!sb.skyblock()) return;
		state.guest = sb.guest();
		if (!sb.serverId().isEmpty() && !state.fromModApi) state.serverId = sb.serverId();
		state.skyDate = sb.skyDate();
		state.skyTime = sb.time();
		if (!sb.area().isEmpty() && !sb.area().equals(state.area)) {
			String old = state.area;
			state.area = sb.area();
			bus.post(new SbEvent.AreaChanged(old, sb.area()));
		}
		if (!state.fromModApi && sb.rift()) setIsland(Island.THE_RIFT);
		if (!state.fromModApi && sb.dungeon() != null && !sb.dungeon().floor().isEmpty()) setIsland(Island.DUNGEON);
		if (!state.fromModApi && !sb.kuudraTier().isEmpty()) setIsland(Island.KUUDRA);
		if (sb.purse() >= 0) {
			long old = state.purse;
			state.purse = sb.purse();
			if (old >= 0 && old != sb.purse()) bus.post(new SbEvent.PurseChanged(sb.purse(), sb.purse() - old));
		}
		if (sb.bits() >= 0) {
			long old = state.bits;
			state.bits = sb.bits();
			if (old >= 0 && old != sb.bits()) bus.post(new SbEvent.BitsChanged(sb.bits(), sb.bits() - old));
		}
		if (sb.motes() >= 0) state.motes = sb.motes();
		if (sb.copper() >= 0) state.copper = sb.copper();
		SidebarParser.Slayer s = sb.slayer();
		if (s != null && s.bossUp() && (prevSlayer == null || !prevSlayer.bossUp())) {
			bus.post(new SbEvent.SlayerBossSpawned(s.boss(), s.tier()));
		}
		prevSlayer = s;
	}

	private SidebarParser.Slayer prevSlayer;

	/** The tab list widgets in display order. Only changes are recorded and parsed. */
	public void tabList(List<String> lines) {
		state.tabAt = clock.getAsLong();
		if (lines.equals(lastTab)) return;
		lastTab = List.copyOf(lines);
		JsonObject j = new JsonObject();
		j.add("lines", array(lines));
		record("tab", j);

		TabListParser.TabInfo tab = TabListParser.parse(lines);
		state.tab = tab;
		if (!state.onSkyBlock) return;
		if (!state.fromModApi && !tab.area().isEmpty()) {
			Island i = Island.fromName(tab.area());
			if (i != Island.UNKNOWN) setIsland(i);
		}
		if (!tab.profile().isEmpty() && (!tab.profile().equals(state.profileName) || tab.gameMode() != state.gameMode)) {
			state.profileName = tab.profile();
			state.gameMode = tab.gameMode();
			state.coop = tab.coop();
			bus.post(new SbEvent.ProfileChanged(state.profileName, state.profileId, state.gameMode));
		}
	}

	// ------------------------------------------------------------------------------------------------ messages

	public void chat(String line) {
		String clean = SbText.clean(line);
		if (clean.isEmpty()) return;
		boolean fromPlayer = ChatParser.isFromAPlayer(clean);
		if (!fromPlayer) {
			JsonObject j = new JsonObject();
			j.addProperty("text", line);
			record("chat", j);
		}
		if (fromPlayer) return;
		for (ChatParser.ChatMatch m : ChatParser.parse(line)) {
			bus.post(new SbEvent.ChatMatched(m, clean));
			switch (m.name()) {
				case "profile_playing" -> {
					if (!m.get("name").equals(state.profileName)) {
						state.profileName = m.get("name");
						state.profileId = "";
						bus.post(new SbEvent.ProfileChanged(state.profileName, "", state.gameMode));
					}
				}
				case "profile_id" -> {
					String id = m.get("id").toLowerCase(java.util.Locale.ROOT);
					if (!id.equals(state.profileId)) {
						state.profileId = id;
						bus.post(new SbEvent.ProfileChanged(state.profileName, id, state.gameMode));
					}
				}
				case "rare_drop" -> bus.post(new SbEvent.RareDrop(m.get("kind"), m.get("item"),
						m.get("count").isEmpty() ? 1 : Integer.parseInt(m.get("count")),
						(int) SbText.longOr(m.get("mf"), 0)));
				case "slayer_started" -> bus.post(new SbEvent.SlayerQuestStarted());
				case "slayer_complete" -> {
					SidebarParser.Slayer s = state.sidebar.slayer();
					bus.post(new SbEvent.SlayerQuestCompleted(s == null ? "" : s.boss(), s == null ? 0 : s.tier()));
				}
				case "slayer_failed" -> bus.post(new SbEvent.SlayerQuestFailed());
				case "skill_level_up" -> bus.post(new SbEvent.SkillLevelUp(m.get("skill"), SbText.roman(m.get("to"))));
				case "visitor_arrived" -> bus.post(new SbEvent.VisitorArrived(m.get("name")));
				default -> {
				}
			}
		}
	}

	private ActionBarParser.SkillGain lastSkill;

	private String lastActionBar = "";

	public void actionBar(String text) {
		if (!text.equals(lastActionBar)) {
			lastActionBar = text;
			JsonObject j = new JsonObject();
			j.addProperty("text", text);
			record("actionbar", j);
		}
		ActionBarParser.ActionBar ab = ActionBarParser.parse(text);
		state.actionBar = ab;
		state.actionBarAt = clock.getAsLong();
		// the same gain is shown for a few seconds; only a changed one counts
		if (ab.skill() != null && !ab.skill().equals(lastSkill)) {
			bus.post(new SbEvent.SkillXpGained(ab.skill().skill(), ab.skill().gained(), ab.skill().progress()));
		}
		lastSkill = ab.skill();
	}

	public void title(String title, String subtitle) {
		JsonObject j = new JsonObject();
		j.addProperty("title", title);
		j.addProperty("subtitle", subtitle);
		record("title", j);
		bus.post(new SbEvent.TitleShown(SbText.clean(title), SbText.clean(subtitle)));
	}

	/** A container screen's title and its non-empty slots (player inventory excluded). */
	public void container(String title, List<RawItem> items) {
		JsonObject j = new JsonObject();
		j.addProperty("title", title);
		JsonArray a = new JsonArray();
		for (RawItem i : items) a.add(i.toJson());
		j.add("items", a);
		record("container", j);
		List<ItemParser.SbItem> parsed = new ArrayList<>();
		for (RawItem i : items) parsed.add(i.parse());
		boolean changedTitle = container == null || !Objects.equals(container.title(), title);
		container = new Container(SbText.clean(title), List.copyOf(items), parsed);
		if (changedTitle) bus.post(new SbEvent.ContainerOpened(container.title(), items.size()));
	}

	public void containerClosed() {
		if (container == null) return;
		record("container_closed", new JsonObject());
		container = null;
	}

	private static JsonArray array(List<String> lines) {
		JsonArray a = new JsonArray();
		for (String s : lines) a.add(s);
		return a;
	}
}
