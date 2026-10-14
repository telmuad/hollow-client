package dev.telmuad.hollowclient;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HollowClient implements ClientModInitializer {
	public static final String MOD_ID = "hollowclient";
	public static final Logger LOG = LoggerFactory.getLogger("Hollow Client");

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitializeClient() {
		LOG.info("Hollow Client starting");
	}
}
