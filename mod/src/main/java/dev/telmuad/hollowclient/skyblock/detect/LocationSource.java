package dev.telmuad.hollowclient.skyblock.detect;

import dev.telmuad.hollowclient.skyblock.state.Location;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/** Where exact location (and party) information comes from. Today: the Hypixel Mod API, when installed. */
public interface LocationSource {
	String name();

	/** Callbacks may arrive on any thread; the caller moves them to the client thread. */
	void start(Consumer<Location> onLocation, Consumer<List<UUID>> onParty);

	/** Asks for the party once. False if the request couldn't be sent. */
	boolean requestParty();
}
