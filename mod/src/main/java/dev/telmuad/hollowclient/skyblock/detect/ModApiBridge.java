package dev.telmuad.hollowclient.skyblock.detect;

import dev.telmuad.hollowclient.skyblock.state.Location;
import net.hypixel.data.type.ServerType;
import net.hypixel.modapi.HypixelModAPI;
import net.hypixel.modapi.packet.impl.clientbound.ClientboundPartyInfoPacket;
import net.hypixel.modapi.packet.impl.clientbound.event.ClientboundLocationPacket;
import net.hypixel.modapi.packet.impl.serverbound.ServerboundPartyInfoPacket;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The official Hypixel Mod API (MIT, its own mod "hypixel-mod-api"): Hypixel tells the client exactly which server,
 * game and island it's on, and (when asked) who's in your party. This class is only loaded when that mod is
 * installed; without it SkyBlock Mode reads the scoreboard and tab list instead (see {@link LocationSource}).
 */
public final class ModApiBridge implements LocationSource {
	private Consumer<Location> onLocation = l -> {
	};
	private Consumer<List<UUID>> onParty = p -> {
	};

	@Override
	public String name() {
		return "Hypixel Mod API";
	}

	@Override
	public void start(Consumer<Location> onLocation, Consumer<List<UUID>> onParty) {
		this.onLocation = onLocation;
		this.onParty = onParty;
		HypixelModAPI api = HypixelModAPI.getInstance();
		api.subscribeToEventPacket(ClientboundLocationPacket.class);
		api.createHandler(ClientboundLocationPacket.class, p -> this.onLocation.accept(new Location(
				p.getServerName() == null ? "" : p.getServerName(),
				p.getServerType().map(ServerType::name).orElse(""),
				p.getLobbyName().orElse(""), p.getMode().orElse(""), p.getMap().orElse(""))));
		api.createHandler(ClientboundPartyInfoPacket.class, p -> this.onParty.accept(
				p.isInParty() ? List.copyOf(p.getMembers()) : List.of()));
	}

	/** Asks Hypixel who's in your party (one request; the answer arrives through the party callback). */
	@Override
	public boolean requestParty() {
		return HypixelModAPI.getInstance().sendPacket(new ServerboundPartyInfoPacket());
	}
}
