package dev.telmuad.hollowclient.skyblock;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.telmuad.hollowclient.skyblock.mc.GameReader;
import dev.telmuad.hollowclient.skyblock.record.Simulator;
import dev.telmuad.hollowclient.skyblock.state.SkyBlockState;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.file.Path;

/**
 * {@code /hollow ...} client commands for SkyBlock Mode. They run only on this computer (nothing is sent to the
 * server): {@code sbsim} plays a simulator scenario (singleplayer), {@code sbrec} turns the debug recorder on or off,
 * {@code sbstate} shows what SkyBlock Mode currently knows, {@code sbdump} saves the held item's data.
 */
final class SbCommands {
	private SbCommands() {
	}

	static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(
				ClientCommands.literal("hollow")
						.then(ClientCommands.literal("sbsim")
								.then(ClientCommands.literal("list").executes(SbCommands::list))
								.then(ClientCommands.literal("stop").executes(c -> {
									SkyBlock.stopSimulation();
									return ok(c, "Simulation stopped.");
								}))
								.then(ClientCommands.argument("scenario", StringArgumentType.word())
										.suggests((c, b) -> {
											for (Simulator.Scenario s : Simulator.list(SkyBlock.debugDir())) b.suggest(s.name());
											return b.buildFuture();
										})
										.executes(c -> {
											String name = StringArgumentType.getString(c, "scenario");
											String problem = SkyBlock.simulate(Minecraft.getInstance(), name);
											return problem == null ? ok(c, "Playing scenario \"" + name + "\" (" + SkyBlock.simulator().size()
													+ " steps). /hollow sbsim stop ends it.") : fail(c, problem);
										})))
						.then(ClientCommands.literal("sbrec")
								.then(ClientCommands.literal("on").executes(c -> {
									try {
										Minecraft mc = Minecraft.getInstance();
										SkyBlock.recorder().setNames(() -> GameReader.playerNames(mc),
												mc.player == null ? "" : mc.player.getGameProfile().name());
										SkyBlock.input().setRecorder(SkyBlock.recorder());
										Path f = SkyBlock.recorder().start();
										return ok(c, "Recording SkyBlock data to " + f.getFileName() + " (in hollow-debug). Other players' chat is never saved.");
									} catch (IOException e) {
										return fail(c, "Couldn't start the recorder: " + e.getMessage());
									}
								}))
								.then(ClientCommands.literal("off").executes(c -> {
									Path f = SkyBlock.recorder().stop();
									SkyBlock.input().setRecorder(null);
									return ok(c, f == null ? "The recorder wasn't on." : "Saved " + SkyBlock.recorder().lines() + " lines to " + f.getFileName() + ".");
								})))
						.then(ClientCommands.literal("sbstate").executes(SbCommands::state))
						.then(ClientCommands.literal("sbdump").executes(SbCommands::dump))));
	}

	private static int list(CommandContext<FabricClientCommandSource> c) {
		for (Simulator.Scenario s : Simulator.list(SkyBlock.debugDir())) {
			c.getSource().sendFeedback(Component.literal(" " + s.name()).withStyle(ChatFormatting.AQUA)
					.append(Component.literal(" - " + s.description()).withStyle(ChatFormatting.GRAY)));
		}
		return 1;
	}

	private static int state(CommandContext<FabricClientCommandSource> c) {
		SkyBlockState s = SkyBlock.state();
		String source = s.fromModApi() ? "Hypixel Mod API" : "scoreboard and tab list";
		ok(c, s.onSkyBlock() ? "On SkyBlock (" + source + ")" : "Not on SkyBlock");
		if (!s.onSkyBlock()) return 1;
		info(c, "Island: " + s.island().displayName() + (s.area().isEmpty() ? "" : " / " + s.area()));
		info(c, "Profile: " + (s.profileName().isEmpty() ? "?" : s.profileName()) + " (" + s.gameMode().name().toLowerCase() + ")"
				+ (s.profileId().isEmpty() ? "" : ", id known"));
		info(c, "Purse: " + s.purse() + "   Bits: " + s.bits() + (s.motes() >= 0 ? "   Motes: " + s.motes() : ""));
		for (var row : SkyBlock.session().rows()) info(c, "Session " + row.label() + ": " + row.value());
		return 1;
	}

	private static int dump(CommandContext<FabricClientCommandSource> c) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return fail(c, "No player.");
		ItemStack stack = mc.player.getMainHandItem();
		if (stack.isEmpty()) return fail(c, "Hold the item you want to save.");
		JsonObject data = GameReader.raw(-1, stack).toJson();
		try {
			Path f = SkyBlock.recorder().writeDump("item", data);
			return ok(c, "Saved the item's data to hollow-debug/" + f.getFileName() + ".");
		} catch (IOException e) {
			return fail(c, "Couldn't save: " + e.getMessage());
		}
	}

	private static int ok(CommandContext<FabricClientCommandSource> c, String text) {
		c.getSource().sendFeedback(Component.literal("[Hollow] ").withStyle(ChatFormatting.LIGHT_PURPLE)
				.append(Component.literal(text).withStyle(ChatFormatting.WHITE)));
		return 1;
	}

	private static void info(CommandContext<FabricClientCommandSource> c, String text) {
		c.getSource().sendFeedback(Component.literal("  " + text).withStyle(ChatFormatting.GRAY));
	}

	private static int fail(CommandContext<FabricClientCommandSource> c, String text) {
		c.getSource().sendError(Component.literal(text));
		return 0;
	}
}
