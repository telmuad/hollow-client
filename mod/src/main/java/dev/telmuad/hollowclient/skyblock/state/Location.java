package dev.telmuad.hollowclient.skyblock.state;

/**
 * Where the player is on Hypixel, as the Hypixel Mod API's location packet says (or the simulator). Empty strings for
 * what wasn't sent. {@code serverType} is the game, e.g. "SKYBLOCK"; {@code mode} the island id, e.g. "hub".
 */
public record Location(String serverName, String serverType, String lobbyName, String mode, String map) {
	public boolean isSkyBlock() {
		return "SKYBLOCK".equalsIgnoreCase(serverType);
	}

	public Island island() {
		Island i = Island.fromMode(mode);
		return i != Island.UNKNOWN ? i : Island.fromName(map);
	}
}
