package dev.telmuad.hollowclient.waypoints;

import dev.telmuad.hollowclient.camera.CameraPath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorldDataTest {
	@Test
	void roundTrip() {
		WorldData d = new WorldData();
		Waypoint home = new Waypoint("Home", 10.5, 64, -3.5, "minecraft:overworld", 0xFF22C55E);
		Waypoint death = new Waypoint("Death", 1, 2, 3, "minecraft:the_nether", 0);
		death.death = true;
		death.visible = false;
		d.waypoints.add(home);
		d.waypoints.add(death);
		d.cameraPath.add(new CameraPath.Key(1, 2, 3, 45, -10));

		WorldData back = WorldData.fromJson(d.toJson());
		assertEquals(2, back.waypoints.size());
		Waypoint h = back.waypoints.get(0);
		assertEquals("Home", h.name);
		assertEquals(10.5, h.x);
		assertEquals(0xFF22C55E, h.color);
		assertTrue(h.visible);
		Waypoint x = back.waypoints.get(1);
		assertTrue(x.death);
		assertFalse(x.visible);
		assertEquals("minecraft:the_nether", x.dimension);
		assertEquals(0, x.color, "0 means the theme accent");
		assertEquals(1, back.cameraPath.size());
		assertEquals(45, back.cameraPath.get(0).yaw(), 1e-6);
	}

	@Test
	void brokenEntriesAreSkippedNotFatal() {
		String json = "{\"waypoints\":[{\"name\":\"ok\",\"x\":1,\"y\":2,\"z\":3},{\"name\":\"bad\"},\"nonsense\","
				+ "{\"name\":\"nan\",\"x\":\"NaN\",\"y\":1,\"z\":1}],\"cameraPath\":[[1,2,3,4,5],[1]]}";
		WorldData d = WorldData.fromJson(json);
		assertEquals(1, d.waypoints.size());
		assertEquals("ok", d.waypoints.get(0).name);
		assertEquals("minecraft:overworld", d.waypoints.get(0).dimension);
		assertEquals(1, d.cameraPath.size());
	}

	@Test
	void garbageGivesEmptyData() {
		assertTrue(WorldData.fromJson("not json {").waypoints.isEmpty());
		assertTrue(WorldData.fromJson("[1,2]").waypoints.isEmpty());
	}

	@Test
	void colorsCycleThroughThePalette() {
		Waypoint w = new Waypoint("a", 0, 0, 0, "d", Waypoint.PALETTE[0]);
		for (int i = 1; i < Waypoint.PALETTE.length; i++) {
			w.color = w.nextColor();
			assertEquals(Waypoint.PALETTE[i], w.color);
		}
		assertEquals(Waypoint.PALETTE[0], w.nextColor());
	}
}
