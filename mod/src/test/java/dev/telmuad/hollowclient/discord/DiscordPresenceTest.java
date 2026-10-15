package dev.telmuad.hollowclient.discord;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiscordPresenceTest {
	private static final DiscordPresence.State SERVER = new DiscordPresence.State(DiscordPresence.Where.MULTIPLAYER, null, "play.example.net", 1_700_000_000L);
	private static final DiscordPresence.State WORLD = new DiscordPresence.State(DiscordPresence.Where.SINGLEPLAYER, "My Base", null, 1_700_000_000L);

	@Test
	void framesRoundTrip() {
		byte[] frame = DiscordIpc.encode(DiscordIpc.FRAME, "{\"a\":\"é\"}");
		assertEquals(1, frame[0]);
		byte[] header = java.util.Arrays.copyOf(frame, 8);
		byte[] body = java.util.Arrays.copyOfRange(frame, 8, frame.length);
		assertEquals(body.length, frame[4] & 255, "length is little-endian after the opcode");
		DiscordIpc.Frame f = DiscordIpc.decode(header, body);
		assertEquals(DiscordIpc.FRAME, f.op());
		assertEquals("{\"a\":\"é\"}", f.json());
	}

	@Test
	void serverAndWorldAreHiddenByDefault() {
		DiscordPresence.Options defaults = new DiscordPresence.Options(true, false, false, true, "hollow");
		JsonObject a = DiscordPresence.activity(SERVER, defaults);
		assertEquals("Playing multiplayer", a.get("details").getAsString());
		assertFalse(a.has("state"));
		assertFalse(a.toString().contains("example.net"));
		JsonObject b = DiscordPresence.activity(WORLD, defaults);
		assertFalse(b.toString().contains("My Base"));
	}

	@Test
	void showsThemOnlyWhenAllowed() {
		DiscordPresence.Options open = new DiscordPresence.Options(true, true, true, true, "hollow");
		assertEquals("play.example.net", DiscordPresence.activity(SERVER, open).get("state").getAsString());
		assertEquals("My Base", DiscordPresence.activity(WORLD, open).get("state").getAsString());
	}

	@Test
	void activityOffShowsNothingAboutWhatYouDo() {
		DiscordPresence.Options quiet = new DiscordPresence.Options(false, true, true, false, "");
		JsonObject a = DiscordPresence.activity(SERVER, quiet);
		assertFalse(a.has("details"));
		assertFalse(a.has("state"));
		assertFalse(a.has("timestamps"));
		assertFalse(a.has("assets"));
	}

	@Test
	void setActivityFrameShape() {
		JsonObject activity = DiscordPresence.activity(WORLD, new DiscordPresence.Options(true, false, false, true, "hollow"));
		JsonObject root = JsonParser.parseString(DiscordPresence.setActivityFrame(activity, 1234, "n1")).getAsJsonObject();
		assertEquals("SET_ACTIVITY", root.get("cmd").getAsString());
		assertEquals(1234, root.getAsJsonObject("args").get("pid").getAsLong());
		assertEquals(1_700_000_000L, root.getAsJsonObject("args").getAsJsonObject("activity").getAsJsonObject("timestamps").get("start").getAsLong());
		JsonObject clear = JsonParser.parseString(DiscordPresence.setActivityFrame(null, 1, "n2")).getAsJsonObject();
		assertFalse(clear.getAsJsonObject("args").has("activity"), "no activity clears the presence");
	}
}
