package dev.telmuad.hollowclient;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SanityTest {
	@Test
	void modIdIsStable() {
		assertEquals("hollowclient", HollowClient.MOD_ID);
	}
}
