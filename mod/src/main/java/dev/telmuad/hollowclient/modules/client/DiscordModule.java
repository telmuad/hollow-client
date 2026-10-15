package dev.telmuad.hollowclient.modules.client;

import dev.telmuad.hollowclient.discord.DiscordPresence;
import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.StringSetting;

/**
 * Discord Rich Presence. Needs a (free) Discord application ID of your own; see USER_GUIDE.md. Privacy first: the
 * server address and world name are only shown if you switch them on.
 */
public final class DiscordModule extends Module {
	/** The Hollow logo from the public repository, used as the presence picture by default. */
	public static final String DEFAULT_IMAGE = "https://raw.githubusercontent.com/telmuad/hollow-client/main/launcher/assets/logo.png";

	private final StringSetting appId = add(new StringSetting("appId", "Discord application ID", "", 32));
	private final BooleanSetting activity = add(new BooleanSetting("activity", "Show what I'm doing (menus / singleplayer / multiplayer)", true));
	private final BooleanSetting server = add(new BooleanSetting("server", "Show the server address", false));
	private final BooleanSetting world = add(new BooleanSetting("world", "Show the singleplayer world name", false));
	private final BooleanSetting time = add(new BooleanSetting("time", "Show time played", true));
	private final StringSetting image = add(new StringSetting("image", "Picture (asset name or https link)", DEFAULT_IMAGE, 256));

	public DiscordModule() {
		super("discord", "Discord Presence", "Shows Hollow Client on your Discord profile. Server and world are hidden unless you allow them.",
				Category.CLIENT, false);
		server.visibleWhen(activity::get);
		world.visibleWhen(activity::get);
	}

	public String appId() {
		return appId.get();
	}

	public DiscordPresence.Options options() {
		return new DiscordPresence.Options(activity.on(), server.on(), world.on(), time.on(), image.get());
	}

	@Override
	protected void onDisable() {
		DiscordPresence.stop();
	}
}
