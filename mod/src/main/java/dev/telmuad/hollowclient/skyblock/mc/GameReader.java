package dev.telmuad.hollowclient.skyblock.mc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.telmuad.hollowclient.skyblock.state.RawItem;
import dev.telmuad.hollowclient.skyblock.state.SkyBlockInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.TeamColor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reads what the server shows (scoreboard sidebar, tab list, the open container) and hands it to {@link SkyBlockInput}
 * as strings. Only reading: nothing here sends anything or changes what the game shows.
 */
public final class GameReader {
	private static final Comparator<PlayerScoreEntry> SIDEBAR_ORDER = Comparator.comparing(PlayerScoreEntry::value)
			.reversed().thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
	/** The same order as vanilla's tab list. */
	private static final Comparator<PlayerInfo> TAB_ORDER = Comparator.<PlayerInfo>comparingInt(p -> -p.getTabListOrder())
			.thenComparingInt(p -> p.getGameMode() == GameType.SPECTATOR ? 1 : 0)
			.thenComparing(p -> p.getTeam() == null ? "" : p.getTeam().getName())
			.thenComparing(p -> p.getProfile().name(), String::compareToIgnoreCase);

	private int ticks;
	private boolean containerOpen;
	private String lastContainerKey = "";
	private boolean paused;

	/** While the simulator runs, the real scoreboard and tab list (empty in singleplayer) are ignored. */
	public void setPaused(boolean paused) {
		this.paused = paused;
	}

	public void tick(Minecraft mc, SkyBlockInput input) {
		if (paused || mc.player == null || mc.level == null) return;
		ticks++;
		if (ticks % 10 == 0) {
			String[] title = {""};
			List<String> lines = sidebar(mc, title);
			input.sidebar(title[0], lines);
		}
		if (ticks % 20 == 5) input.tabList(tabList(mc));
		if (ticks % 5 == 0) container(mc, input);
	}

	// ------------------------------------------------------------------------------------------------ sidebar

	public static List<String> sidebar(Minecraft mc, String[] titleOut) {
		Scoreboard scoreboard = mc.level.getScoreboard();
		Objective objective = null;
		PlayerTeam team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
		if (team != null) {
			Optional<TeamColor> color = team.getColor();
			if (color.isPresent()) objective = scoreboard.getDisplayObjective(color.get().displaySlot());
		}
		if (objective == null) objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
		List<String> out = new ArrayList<>();
		if (objective == null) return out;
		titleOut[0] = LegacyText.of(objective.getDisplayName());
		List<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective).stream()
				.filter(e -> !e.isHidden()).sorted(SIDEBAR_ORDER).limit(15).toList();
		for (PlayerScoreEntry e : entries) {
			Component name = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(e.owner()), e.ownerName());
			out.add(LegacyText.of(name));
		}
		return out;
	}

	// ------------------------------------------------------------------------------------------------ tab list

	public static List<String> tabList(Minecraft mc) {
		ClientPacketListener connection = mc.getConnection();
		List<String> out = new ArrayList<>();
		if (connection == null) return out;
		for (PlayerInfo info : connection.getListedOnlinePlayers().stream().sorted(TAB_ORDER).limit(80).toList()) {
			Component name = info.getTabListDisplayName() != null ? info.getTabListDisplayName()
					: PlayerTeam.formatNameForTeam(info.getTeam(), Component.literal(info.getProfile().name()));
			out.add(LegacyText.of(name));
		}
		return out;
	}

	/** Real player names in the tab list (for the debug recorder to replace with "Player1", "Player2", ...). */
	public static List<String> playerNames(Minecraft mc) {
		List<String> out = new ArrayList<>();
		ClientPacketListener connection = mc.getConnection();
		if (connection == null) return out;
		for (PlayerInfo info : connection.getOnlinePlayers()) {
			String n = info.getProfile().name();
			if (n != null && n.matches("[A-Za-z0-9_]{3,16}")) out.add(n);
		}
		if (mc.player != null) out.add(mc.player.getGameProfile().name());
		return out;
	}

	// ------------------------------------------------------------------------------------------------ containers

	private void container(Minecraft mc, SkyBlockInput input) {
		if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)
				|| screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) {
			if (containerOpen) {
				containerOpen = false;
				lastContainerKey = "";
				input.containerClosed();
			}
			return;
		}
		List<RawItem> items = new ArrayList<>();
		for (Slot slot : screen.getMenu().slots) {
			if (slot.container == mc.player.getInventory() || !slot.hasItem()) continue;
			items.add(raw(slot.index, slot.getItem()));
		}
		String title = LegacyText.of(screen.getTitle());
		StringBuilder key = new StringBuilder(title);
		for (RawItem i : items) key.append('|').append(i.slot()).append(i.name()).append(i.count()).append(i.lore().size());
		containerOpen = true;
		if (key.toString().equals(lastContainerKey)) return;
		lastContainerKey = key.toString();
		input.container(title, items);
	}

	// ------------------------------------------------------------------------------------------------ items

	public static RawItem raw(int slot, ItemStack stack) {
		String material = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
		CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
		JsonObject custom = cd == null ? new JsonObject() : toJson(cd.copyTag());
		ItemLore lore = stack.get(DataComponents.LORE);
		List<String> lines = new ArrayList<>();
		if (lore != null) for (Component c : lore.lines()) lines.add(LegacyText.of(c));
		return new RawItem(slot, material, stack.getCount(), LegacyText.of(stack.getHoverName()), lines, custom);
	}

	public static JsonObject toJson(CompoundTag tag) {
		JsonElement e = NbtOps.INSTANCE.convertTo(JsonOps.INSTANCE, tag);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
	}

	/** Builds an item from plain data (the simulator's "give" steps, in singleplayer only). */
	public static ItemStack stack(RawItem raw) {
		var item = BuiltInRegistries.ITEM.getValue(Identifier.tryParse(raw.material()));
		ItemStack stack = new ItemStack(item, Math.max(1, raw.count()));
		Tag tag = JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, raw.customData());
		if (tag instanceof CompoundTag ct && !ct.isEmpty()) stack.set(DataComponents.CUSTOM_DATA, CustomData.of(ct));
		if (!raw.name().isBlank()) stack.set(DataComponents.CUSTOM_NAME, LegacyText.parse(raw.name()));
		if (!raw.lore().isEmpty()) {
			List<Component> lines = new ArrayList<>();
			for (String l : raw.lore()) lines.add(LegacyText.parse(l));
			stack.set(DataComponents.LORE, new ItemLore(lines));
		}
		return stack;
	}
}
