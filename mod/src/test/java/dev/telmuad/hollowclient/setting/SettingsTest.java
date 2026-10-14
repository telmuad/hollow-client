package dev.telmuad.hollowclient.setting;

import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
	enum Mode {FIRST_ONE, SECOND}

	@Test
	void numberClampsAndSnapsToStep() {
		NumberSetting n = new NumberSetting("n", "N", 1, 0.5, 3, 0.1);
		n.set(2.04);
		assertEquals(2.0, n.get());
		n.set(9.0);
		assertEquals(3.0, n.get());
		n.set(-4.0);
		assertEquals(0.5, n.get());
		n.set(Double.NaN);
		assertEquals(1.0, n.get());
		n.setFraction(0.5);
		assertEquals(1.8, n.get(), 1e-9);
		assertEquals("1.8", n.display());
	}

	@Test
	void numberFromBadJsonResets() {
		NumberSetting n = new NumberSetting("n", "N", 4, 2, 50, 0.5);
		n.set(10.0);
		n.fromJson(new JsonPrimitive("ten"));
		assertEquals(4.0, n.get());
		n.fromJson(new JsonPrimitive(7.5));
		assertEquals(7.5, n.get());
	}

	@Test
	void listenerOnlyFiresOnRealChange() {
		AtomicInteger calls = new AtomicInteger();
		BooleanSetting b = new BooleanSetting("b", "B", false);
		b.setListener(s -> calls.incrementAndGet());
		b.set(false);
		assertEquals(0, calls.get());
		b.toggle();
		assertEquals(1, calls.get());
		b.fromJson(JsonNull.INSTANCE);
		assertFalse(b.get());
		assertEquals(2, calls.get());
	}

	@Test
	void colorReadsHexAndNumbers() {
		ColorSetting c = new ColorSetting("c", "C", 0xFFFFFFFF);
		c.fromJson(new JsonPrimitive("#80112233"));
		assertEquals(0x80112233, c.get());
		c.fromJson(new JsonPrimitive("#abc"));
		assertEquals(0xFFAABBCC, c.get());
		c.fromJson(new JsonPrimitive("rainbow"));
		assertEquals(0xFFFFFFFF, c.get());
		assertEquals("\"#FFFFFFFF\"", c.toJson().toString());
	}

	@Test
	void opaqueColorIgnoresAlpha() {
		ColorSetting c = new ColorSetting("c", "C", 0x00FF0000, false);
		assertEquals(0xFFFF0000, c.get());
		c.set(0x10123456);
		assertEquals(0xFF123456, c.get());
	}

	@Test
	void enumReadsNamesIgnoringCaseAndCycles() {
		EnumSetting<Mode> e = new EnumSetting<>("e", "E", Mode.FIRST_ONE);
		e.fromJson(new JsonPrimitive("second"));
		assertEquals(Mode.SECOND, e.get());
		e.cycle(1);
		assertEquals(Mode.FIRST_ONE, e.get());
		e.cycle(-1);
		assertEquals(Mode.SECOND, e.get());
		e.fromJson(new JsonPrimitive("third"));
		assertEquals(Mode.FIRST_ONE, e.get());
		assertEquals("First one", EnumSetting.labelOf(Mode.FIRST_ONE));
		assertEquals("\"first_one\"", e.toJson().toString());
	}

	@Test
	void stringIsCleaned() {
		StringSetting s = new StringSetting("s", "S", "", 5);
		s.set("a\u0000b§cdefgh");
		assertEquals("abcde", s.get());
	}
}
