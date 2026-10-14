package dev.telmuad.hollowclient.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColorUtilTest {
	@Test
	void hexRoundTrip() {
		for (int c : new int[]{0, 0xFFFFFFFF, 0x80FF00AA, 0x12345678, 0xFF000001}) {
			assertEquals(c, ColorUtil.parseHex(ColorUtil.toHex(c)));
		}
	}

	@Test
	void parseFormats() {
		assertEquals(0xFFFF0000, ColorUtil.parseHex("#F00"));
		assertEquals(0xFF00FF00, ColorUtil.parseHex("00ff00"));
		assertEquals(0x7F0000FF, ColorUtil.parseHex(" #7F0000FF "));
		assertNull(ColorUtil.parseHex("#12345"));
		assertNull(ColorUtil.parseHex("#GGGGGG"));
		assertNull(ColorUtil.parseHex(null));
		assertNull(ColorUtil.parseHex("#-12345"));
	}

	@Test
	void hsvRoundTrip() {
		for (int c : new int[]{0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFF7B3FE4, 0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFFFFC107}) {
			float[] hsv = ColorUtil.toHsv(c);
			int back = ColorUtil.fromHsv(hsv[0], hsv[1], hsv[2]);
			assertEquals(c, back, () -> Integer.toHexString(c) + " -> " + Integer.toHexString(back));
		}
	}

	@Test
	void mixAndFade() {
		assertEquals(0xFF7F7F7F, ColorUtil.mix(0xFF000000, 0xFFFEFEFE, 0.5f));
		assertEquals(0x80FFFFFF, ColorUtil.fade(0xFFFFFFFF, 128 / 255f));
		assertEquals(0x00123456, ColorUtil.withAlpha(0xFF123456, 0));
	}
}
