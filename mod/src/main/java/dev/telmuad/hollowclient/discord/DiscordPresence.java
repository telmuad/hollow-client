package dev.telmuad.hollowclient.discord;

import com.google.gson.JsonObject;
import dev.telmuad.hollowclient.HollowClient;
import dev.telmuad.hollowclient.gui.Toasts;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.client.DiscordModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

/**
 * Discord Rich Presence: shows "Hollow Client" and (only if you allow it) what you're doing on your Discord profile.
 * The server address and world name are hidden unless switched on. Talks only to the Discord app on this computer,
 * on a background thread; if Discord isn't running it quietly retries now and then.
 */
public final class DiscordPresence {
	public enum Where {MENU, SINGLEPLAYER, MULTIPLAYER}

	/** What can be shown, before the privacy settings are applied. */
	public record State(Where where, String world, String server, long startEpochSeconds) {
	}

	/** The privacy and look settings. */
	public record Options(boolean activity, boolean server, boolean world, boolean time, String image) {
	}

	private static volatile String desired;
	private static volatile String appId = "";
	private static volatile boolean running;
	private static Thread thread;
	private static Where lastWhere;
	private static String lastPlace;
	private static long startedAt;
	private static volatile String status = "Off";

	private DiscordPresence() {
	}

	public static String status() {
		return status;
	}

	/** The activity JSON for a state, with the privacy settings applied. */
	public static JsonObject activity(State state, Options o) {
		JsonObject a = new JsonObject();
		if (o.activity()) {
			a.addProperty("details", text(switch (state.where()) {
				case MENU -> "In the menus";
				case SINGLEPLAYER -> "Playing singleplayer";
				case MULTIPLAYER -> "Playing multiplayer";
			}));
			String place = null;
			if (state.where() == Where.SINGLEPLAYER && o.world() && state.world() != null) place = state.world();
			if (state.where() == Where.MULTIPLAYER && o.server() && state.server() != null) place = state.server();
			if (place != null && !place.isBlank()) a.addProperty("state", text(place));
		}
		if (o.time() && state.startEpochSeconds() > 0) {
			JsonObject t = new JsonObject();
			t.addProperty("start", state.startEpochSeconds());
			a.add("timestamps", t);
		}
		if (o.image() != null && !o.image().isBlank()) {
			JsonObject assets = new JsonObject();
			assets.addProperty("large_image", o.image().trim());
			assets.addProperty("large_text", "Hollow Client");
			a.add("assets", assets);
		}
		return a;
	}

	/** Discord wants 2-128 characters. */
	private static String text(String s) {
		String t = s.length() > 128 ? s.substring(0, 125) + "..." : s;
		return t.length() < 2 ? t + " " : t;
	}

	public static String setActivityFrame(JsonObject activity, long pid, String nonce) {
		JsonObject args = new JsonObject();
		args.addProperty("pid", pid);
		if (activity != null) args.add("activity", activity);
		JsonObject root = new JsonObject();
		root.addProperty("cmd", "SET_ACTIVITY");
		root.add("args", args);
		root.addProperty("nonce", nonce);
		return root.toString();
	}

	// ------------------------------------------------------------------------------------------------ client side

	/** Every client tick: work out what to show; start or stop the background connection. */
	public static void tick(Minecraft mc) {
		DiscordModule m = Modules.DISCORD;
		boolean on = m != null && m.isEnabled() && !m.appId().isBlank();
		if (!on) {
			if (running) stop();
			if (m != null && m.isEnabled()) status = "Add your Discord application ID in the settings";
			return;
		}
		Where where;
		String world = null;
		String server = null;
		if (mc.level == null) {
			where = Where.MENU;
		} else if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
			where = Where.SINGLEPLAYER;
			world = mc.getSingleplayerServer().getWorldData().getLevelName();
		} else {
			where = Where.MULTIPLAYER;
			ServerData data = mc.getCurrentServer();
			server = data == null ? null : data.isRealm() ? "Realms" : data.ip;
		}
		String place = where + "|" + world + "|" + server;
		if (!place.equals(lastPlace)) {
			lastPlace = place;
			if (where != lastWhere || startedAt == 0) startedAt = System.currentTimeMillis() / 1000;
			lastWhere = where;
		}
		desired = activity(new State(where, world, server, startedAt), m.options()).toString();
		if (!Objects.equals(appId, m.appId().trim()) || !running) {
			stop();
			appId = m.appId().trim();
			start();
		}
	}

	private static synchronized void start() {
		running = true;
		Thread t = new Thread(DiscordPresence::run, "Hollow Discord presence");
		t.setDaemon(true);
		thread = t;
		t.start();
	}

	public static synchronized void stop() {
		running = false;
		Thread t = thread;
		thread = null;
		if (t != null) t.interrupt();
		status = "Off";
	}

	private static void run() {
		DiscordIpc ipc = null;
		String sent = null;
		long nextTry = 0;
		boolean toldConnected = false;
		String lastFailure = null;
		long pid = ProcessHandle.current().pid();
		String id = appId;
		try {
			while (running && !Thread.currentThread().isInterrupted()) {
				long now = System.currentTimeMillis();
				if (ipc == null && now >= nextTry) {
					try {
						ipc = DiscordIpc.connect();
						JsonObject hello = new JsonObject();
						hello.addProperty("v", 1);
						hello.addProperty("client_id", id);
						ipc.send(DiscordIpc.HANDSHAKE, hello.toString());
						DiscordIpc.Frame reply = ipc.receive();
						if (reply.op() == DiscordIpc.CLOSE || !reply.json().contains("READY")) {
							throw new IOException(reason(reply.json()));
						}
						sent = null;
						status = "Connected";
						lastFailure = null;
						if (!toldConnected) {
							toldConnected = true;
							Minecraft.getInstance().execute(() -> Toasts.success("Discord presence is on", "Your Discord profile shows Hollow Client."));
						}
					} catch (IOException e) {
						close(ipc);
						ipc = null;
						nextTry = now + 20_000;
						String why = e.getMessage() == null ? "Discord isn't running" : e.getMessage();
						if (why.contains("cannot find") || why.contains("No such file") || why.contains("isn't running")) why = "Discord isn't running";
						status = why;
						if (!why.equals(lastFailure)) {
							lastFailure = why;
							String shown = why;
							if (!why.equals("Discord isn't running")) {
								Minecraft.getInstance().execute(() -> Toasts.warning("Discord presence", shown));
							}
						}
					}
				}
				String want = desired;
				if (ipc != null && want != null && !want.equals(sent)) {
					try {
						ipc.send(DiscordIpc.FRAME, setActivityFrame(com.google.gson.JsonParser.parseString(want).getAsJsonObject(), pid,
								UUID.randomUUID().toString()));
						DiscordIpc.Frame reply = ipc.receive();
						if (reply.json().contains("\"evt\":\"ERROR\"")) {
							status = reason(reply.json());
							HollowClient.LOG.warn("Discord didn't accept the presence: {}", status);
						}
						sent = want;
					} catch (IOException e) {
						close(ipc);
						ipc = null;
						nextTry = now + 5_000;
						status = "Lost the connection to Discord";
					}
				}
				Thread.sleep(1000);
			}
		} catch (InterruptedException e) {
			// stopping
		} finally {
			if (ipc != null) {
				try {
					ipc.send(DiscordIpc.FRAME, setActivityFrame(null, pid, UUID.randomUUID().toString()));
				} catch (IOException ignored) {
					// Discord clears it anyway when we disconnect
				}
				close(ipc);
			}
		}
	}

	private static String reason(String json) {
		try {
			JsonObject o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
			if (o.has("message")) return o.get("message").getAsString();
			if (o.has("data") && o.getAsJsonObject("data").has("message")) return o.getAsJsonObject("data").get("message").getAsString();
		} catch (RuntimeException ignored) {
			// fall through
		}
		return "Discord refused the connection";
	}

	private static void close(DiscordIpc ipc) {
		if (ipc == null) return;
		try {
			ipc.close();
		} catch (IOException ignored) {
			// nothing to do
		}
	}
}
