package dev.telmuad.hollowclient.skyblock;

import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.skyblock.data.Http;
import dev.telmuad.hollowclient.skyblock.data.SkyBlockData;
import dev.telmuad.hollowclient.skyblock.detect.LocationSource;
import dev.telmuad.hollowclient.skyblock.event.EventBus;
import dev.telmuad.hollowclient.skyblock.event.SbEvent;
import dev.telmuad.hollowclient.skyblock.mc.GameReader;
import dev.telmuad.hollowclient.skyblock.mc.LegacyText;
import dev.telmuad.hollowclient.skyblock.parse.Patterns;
import dev.telmuad.hollowclient.skyblock.record.DebugRecorder;
import dev.telmuad.hollowclient.skyblock.record.Simulator;
import dev.telmuad.hollowclient.skyblock.state.ProfileStore;
import dev.telmuad.hollowclient.skyblock.state.RawItem;
import dev.telmuad.hollowclient.skyblock.state.SessionStats;
import dev.telmuad.hollowclient.skyblock.state.SkyBlockInput;
import dev.telmuad.hollowclient.skyblock.state.SkyBlockState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * SkyBlock Mode's core: the state, the event bus, the input router, the game reader, the debug recorder, the
 * simulator, per-profile files and the session totals. Everything here only reads what the server shows; nothing is
 * sent to the server except what the Hypixel Mod API itself asks for.
 */
public final class SkyBlock {
	private static SkyBlockState state;
	private static EventBus bus;
	private static SkyBlockInput input;
	private static GameReader reader;
	private static DebugRecorder recorder;
	private static Simulator simulator;
	private static ProfileStore store;
	private static SessionStats session;
	private static SkyBlockData data;
	private static dev.telmuad.hollowclient.skyblock.data.ApiKeyStore apiKeys;
	/** Set while a SkyBlock screen (Hub, setup checker) wants fresh data even off SkyBlock. */
	private static boolean dataWanted;
	private static LocationSource locationSource;
	private static List<UUID> party = List.of();
	private static boolean clientOverlay;
	/** A scenario was started and not stopped: the simulated state stays (for previews) until /hollow sbsim stop. */
	private static boolean simulating;
	private static int ticks;
	private static long lastSessionSave;

	private SkyBlock() {
	}

	/** {@code config/hollowclient/skyblock/}. */
	public static Path dir() {
		return HollowClient.dataDir().resolve("skyblock");
	}

	/** {@code hollow-debug/} in the game folder (recordings and item dumps). */
	public static Path debugDir() {
		return FabricLoader.getInstance().getGameDir().resolve("hollow-debug");
	}

	public static void init() {
		Patterns.setOverrideDir(dir().resolve("patterns"));
		state = new SkyBlockState();
		bus = new EventBus();
		bus.setErrorSink(t -> HollowClient.LOG.error("SkyBlock listener failed", t));
		input = new SkyBlockInput(state, bus, System::currentTimeMillis);
		reader = new GameReader();
		recorder = new DebugRecorder(debugDir(), System::currentTimeMillis);
		simulator = new Simulator();
		simulator.setWorld(SkyBlock::simWorld);
		store = new ProfileStore(dir().resolve("profiles"));
		session = new SessionStats(state, bus, System::currentTimeMillis);
		bus.on(SbEvent.LeftSkyBlock.class, e -> saveSession());
		apiKeys = new dev.telmuad.hollowclient.skyblock.data.ApiKeyStore(dir().resolve("api_key.dat"),
				dev.telmuad.hollowclient.skyblock.data.ApiKeyStore.systemProtector());
		data = new SkyBlockData(dir(), Http.javaFetcher(), System::currentTimeMillis);
		data.setErrorSink(t -> HollowClient.LOG.warn("SkyBlock data refresh failed: {}", t.toString()));
		java.util.concurrent.CompletableFuture.runAsync(data::loadCached);

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
			if (simulating) stopSimulation();
			input.left();
		}));
		if (FabricLoader.getInstance().isModLoaded("hypixel-mod-api")) {
			try {
				LocationSource src = (LocationSource) Class.forName("dev.telmuad.hollowclient.skyblock.detect.ModApiBridge")
						.getDeclaredConstructor().newInstance();
				Minecraft mc = Minecraft.getInstance();
				src.start(loc -> mc.execute(() -> {
					if (!simulating) input.location(loc);
				}), members -> mc.execute(() -> party = List.copyOf(members)));
				locationSource = src;
				HollowClient.LOG.info("SkyBlock Mode: using the Hypixel Mod API for location");
			} catch (ReflectiveOperationException | LinkageError | RuntimeException e) {
				HollowClient.LOG.warn("SkyBlock Mode: the Hypixel Mod API couldn't be used ({}); reading the scoreboard instead", e.toString());
			}
		}
		SbCommands.register();
		dev.telmuad.hollowclient.skyblock.alerts.SkyBlockAlerts.init();
	}

	public static SkyBlockState state() {
		return state;
	}

	public static EventBus bus() {
		return bus;
	}

	public static SkyBlockInput input() {
		return input;
	}

	public static DebugRecorder recorder() {
		return recorder;
	}

	public static Simulator simulator() {
		return simulator;
	}

	public static ProfileStore store() {
		return store;
	}

	public static SessionStats session() {
		return session;
	}

	/** The optional Hypixel API key (encrypted with Windows DPAPI). */
	public static dev.telmuad.hollowclient.skyblock.data.ApiKeyStore apiKeys() {
		return apiKeys;
	}

	/** Prices and the NEU item repository. */
	public static SkyBlockData data() {
		return data;
	}

	/** Screens that show prices call this while open, so data loads even off SkyBlock. */
	public static void wantData(boolean wanted) {
		dataWanted = wanted;
	}

	/** The Hypixel Mod API bridge, or null when it isn't installed. */
	public static LocationSource locationSource() {
		return locationSource;
	}

	/** Party members from the Hypixel Mod API's last answer (empty when unknown or not in a party). */
	public static List<UUID> party() {
		return party;
	}

	/** True while on SkyBlock (or in a simulation) with SkyBlock Mode on. SkyBlock features do nothing otherwise. */
	public static boolean active() {
		var mode = dev.telmuad.hollowclient.modules.Modules.SKYBLOCK_MODE;
		return state != null && state.onSkyBlock() && (mode == null || mode.isEnabled());
	}

	public static void tick(Minecraft mc) {
		if (state == null) return;
		ticks++;
		long now = System.currentTimeMillis();
		if (simulating) {
			simulator.tick(now, input, SkyBlock::give);
		} else {
			reader.tick(mc, input);
		}
		if (ticks % 20 == 0) recorder.flush();
		var mode = dev.telmuad.hollowclient.modules.Modules.SKYBLOCK_MODE;
		boolean downloads = mode == null || mode.downloads();
		data.tick(downloads && (active() || dataWanted));
		if (state.onSkyBlock() && now - lastSessionSave > 60_000 && session.playedMs() > 30_000) saveSession();
	}

	private static void saveSession() {
		lastSessionSave = System.currentTimeMillis();
		if (session.playedMs() < 30_000) return;
		try {
			store.write(state.profileKey(), "session_stats", session.toJson());
		} catch (IOException e) {
			HollowClient.LOG.warn("Couldn't save the SkyBlock session totals: {}", e.toString());
		}
	}

	// ------------------------------------------------------------------------------------------------ game hooks

	/** Chat from the server (the chat mixin). */
	public static void onChat(Component message) {
		if (input != null && !simulating) input.chat(LegacyText.of(message));
	}

	/** Action bar from the server (the HUD mixin); Hollow's own overlay messages are skipped. */
	public static void onActionBar(Component message) {
		if (input != null && !clientOverlay && !simulating) input.actionBar(LegacyText.of(message));
	}

	private static String subtitle = "";

	public static void onSubtitle(Component sub) {
		if (clientOverlay) return;
		subtitle = sub == null ? "" : LegacyText.of(sub);
	}

	public static void onTitle(Component title) {
		if (clientOverlay) return;
		if (input != null && !simulating) input.title(LegacyText.of(title), subtitle);
		subtitle = "";
	}

	/** Shows Hollow's own title (alerts) without SkyBlock Mode reading it as the server's. */
	public static void showTitle(Minecraft mc, Component title, Component sub) {
		clientOverlay = true;
		try {
			mc.gui.hud.setTimes(4, 36, 10);
			mc.gui.hud.setSubtitle(sub);
			mc.gui.hud.setTitle(title);
		} finally {
			clientOverlay = false;
		}
	}

	/** Shows Hollow's own message above the hotbar without SkyBlock Mode reading it as the server's action bar. */
	public static void showOverlay(Minecraft mc, Component message) {
		clientOverlay = true;
		try {
			mc.gui.hud.setOverlayMessage(message, false);
		} finally {
			clientOverlay = false;
		}
	}

	// ------------------------------------------------------------------------------------------------ simulator

	/** Starts a scenario. Only in singleplayer: on a real server the simulator would mix fake data into real data. */
	public static String simulate(Minecraft mc, String name) {
		if (!mc.hasSingleplayerServer()) return "The simulator only runs in singleplayer.";
		try {
			if (!simulator.load(name, debugDir(), System.currentTimeMillis())) return "No scenario called \"" + name + "\".";
		} catch (IOException | RuntimeException e) {
			return "Couldn't read the scenario: " + e.getMessage();
		}
		reader.setPaused(true);
		simulating = true;
		input.left();
		return null;
	}

	public static void stopSimulation() {
		simWorld(clearSummons());
		simulator.stop();
		simulating = false;
		reader.setPaused(false);
		input.left();
	}

	private static com.google.gson.JsonObject clearSummons() {
		com.google.gson.JsonObject o = new com.google.gson.JsonObject();
		o.addProperty("type", "clear_summons");
		return o;
	}

	/**
	 * The simulator's "summon" steps: an invisible marker armor stand with a visible name tag, {@code dx/dy/dz} from
	 * you (singleplayer only), for features that read name tags (deployables, damage numbers).
	 */
	private static void simWorld(com.google.gson.JsonObject e) {
		Minecraft mc = Minecraft.getInstance();
		MinecraftServer server = mc.getSingleplayerServer();
		if (server == null || mc.player == null) return;
		String type = e.get("type").getAsString();
		if (type.equals("clear_summons")) {
			server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
					"kill @e[type=armor_stand,tag=hollow_sim]"));
			return;
		}
		double x = mc.player.getX() + (e.has("dx") ? e.get("dx").getAsDouble() : 2);
		double y = mc.player.getY() + (e.has("dy") ? e.get("dy").getAsDouble() : 1);
		double z = mc.player.getZ() + (e.has("dz") ? e.get("dz").getAsDouble() : 2);
		String name = e.has("name") ? e.get("name").getAsString() : "";
		String snbt = "\"" + name.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
		String cmd = String.format(java.util.Locale.ROOT, "summon minecraft:armor_stand %.2f %.2f %.2f {CustomName:%s,CustomNameVisible:1b,"
				+ "Invisible:1b,NoGravity:1b,Marker:1b,Tags:[\"hollow_sim\"]}", x, y, z, snbt);
		server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd));
	}

	/** True from starting a scenario until it's stopped (its data stays on screen after the last step). */
	public static boolean simulating() {
		return simulating;
	}

	/** Puts an item into the singleplayer inventory (the simulator's "give" steps). */
	private static void give(RawItem raw) {
		Minecraft mc = Minecraft.getInstance();
		MinecraftServer server = mc.getSingleplayerServer();
		if (server == null || mc.player == null) return;
		UUID id = mc.player.getUUID();
		server.execute(() -> {
			ServerPlayer sp = server.getPlayerList().getPlayer(id);
			if (sp == null) return;
			ItemStack stack = GameReader.stack(raw);
			if (!sp.getInventory().add(stack)) sp.drop(stack, false);
		});
	}
}
