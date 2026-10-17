package dev.telmuad.hollowclient.skyblock.state;

import dev.telmuad.hollowclient.skyblock.parse.ActionBarParser;
import dev.telmuad.hollowclient.skyblock.parse.SidebarParser;
import dev.telmuad.hollowclient.skyblock.parse.TabListParser;

/**
 * What SkyBlock Mode knows right now. Only {@link SkyBlockInput} writes it (on the
 * client thread); features read it. Numbers are -1 when unknown. Plain Java.
 */
public final class SkyBlockState {
	boolean onSkyBlock;
	boolean fromModApi;
	Location location;
	Island island = Island.UNKNOWN;
	String area = "";
	String serverId = "";
	String profileName = "";
	String profileId = "";
	TabListParser.GameMode gameMode = TabListParser.GameMode.NORMAL;
	boolean coop;
	boolean guest;
	long purse = -1;
	long bits = -1;
	long motes = -1;
	long copper = -1;
	String skyDate = "";
	String skyTime = "";
	SidebarParser.Sidebar sidebar = SidebarParser.Sidebar.EMPTY;
	TabListParser.TabInfo tab = TabListParser.TabInfo.EMPTY;
	ActionBarParser.ActionBar actionBar;
	long actionBarAt;
	long sidebarAt;
	long tabAt;
	long joinedAt;
	java.util.function.LongSupplier clock = System::currentTimeMillis;

	public boolean onSkyBlock() {
		return onSkyBlock;
	}

	/** True when the location comes from the Hypixel Mod API (exact), false when read from the scoreboard/tab list. */
	public boolean fromModApi() {
		return fromModApi;
	}

	public Location location() {
		return location;
	}

	public Island island() {
		return island;
	}

	public boolean in(Island i) {
		return onSkyBlock && island == i;
	}

	/** The sub-area inside the island ("Village", "Royal Mines", ...), from the sidebar. */
	public String area() {
		return area;
	}

	public String serverId() {
		return serverId;
	}

	public String profileName() {
		return profileName;
	}

	public String profileId() {
		return profileId;
	}

	/** The key the per-profile store uses: the profile id when known, else the profile name, else "default". */
	public String profileKey() {
		if (!profileId.isEmpty()) return profileId;
		if (!profileName.isEmpty()) return profileName.toLowerCase(java.util.Locale.ROOT);
		return "default";
	}

	public TabListParser.GameMode gameMode() {
		return gameMode;
	}

	public boolean coop() {
		return coop;
	}

	public boolean guest() {
		return guest;
	}

	public long purse() {
		return purse;
	}

	public long bits() {
		return bits;
	}

	public long motes() {
		return motes;
	}

	public long copper() {
		return copper;
	}

	public String skyDate() {
		return skyDate;
	}

	public String skyTime() {
		return skyTime;
	}

	public SidebarParser.Sidebar sidebar() {
		return sidebar;
	}

	public TabListParser.TabInfo tab() {
		return tab;
	}

	/** The last action bar, or null if none for 3 seconds (it's sent about once a second while it changes). */
	public ActionBarParser.ActionBar actionBar() {
		return actionBar != null && clock.getAsLong() - actionBarAt < 3000 ? actionBar : null;
	}

	/** When the last action bar came in (clock time), so features can tell a new reading from an old one. */
	public long actionBarAt() {
		return actionBarAt;
	}

	public long sidebarAt() {
		return sidebarAt;
	}

	public long tabAt() {
		return tabAt;
	}

	public long joinedAt() {
		return joinedAt;
	}

	/** Back to "not on SkyBlock" (left the server). */
	void reset() {
		onSkyBlock = false;
		fromModApi = false;
		location = null;
		island = Island.UNKNOWN;
		area = "";
		serverId = "";
		guest = false;
		purse = bits = motes = copper = -1;
		skyDate = skyTime = "";
		sidebar = SidebarParser.Sidebar.EMPTY;
		tab = TabListParser.TabInfo.EMPTY;
		actionBar = null;
		joinedAt = 0;
	}
}
