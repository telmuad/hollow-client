package dev.telmuad.hollowclient.modules.hud;

import dev.telmuad.hollowclient.hud.HudPosition.Anchor;
import dev.telmuad.hollowclient.hud.TextHudModule;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Locale;

public final class CoordinatesHud extends TextHudModule {
	private final BooleanSetting oneLine = add(new BooleanSetting("oneLine", "Single line", false));
	private final BooleanSetting decimals = add(new BooleanSetting("decimals", "Show decimals", false));
	private final BooleanSetting facing = add(new BooleanSetting("facing", "Show facing", true));
	private final BooleanSetting biome = add(new BooleanSetting("biome", "Show biome", true));
	private final BooleanSetting nether = add(new BooleanSetting("netherCoords", "Show Nether/Overworld coords", false));

	public CoordinatesHud() {
		super("coordinates", "Coordinates", "Your position, the direction you face and the biome you are in.", true,
				at(Anchor.START, 2, Anchor.START, 20));
		nether.describe("Adds the matching coordinates in the other dimension (x8 or /8).");
	}

	@Override
	protected void lines(Minecraft mc, boolean preview, List<Line> out) {
		Player p = mc.player;
		double x, y, z;
		Direction dir;
		String biomeName;
		boolean inNether = false;
		if (p == null || mc.level == null) {
			x = 128.5;
			y = 64;
			z = -256.25;
			dir = Direction.NORTH;
			biomeName = "Plains";
		} else {
			x = p.getX();
			y = p.getY();
			z = p.getZ();
			dir = p.getDirection();
			biomeName = biomeName(mc, p.blockPosition());
			inNether = mc.level.dimension() == net.minecraft.world.level.Level.NETHER;
		}
		String fx = fmt(x), fy = fmt(y), fz = fmt(z);
		if (oneLine.on()) {
			out.add(Line.of("XYZ: " + fx + " / " + fy + " / " + fz));
		} else {
			out.add(Line.of("X: " + fx));
			out.add(Line.of("Y: " + fy));
			out.add(Line.of("Z: " + fz));
		}
		if (nether.on()) {
			double factor = inNether ? 8 : 1 / 8.0;
			out.add(new Line((inNether ? "Overworld: " : "Nether: ") + fmt(x * factor) + ", " + fmt(z * factor), 0xFFB0B3BD));
		}
		if (facing.on()) out.add(Line.of("Facing: " + facingText(dir)));
		if (biome.on()) out.add(Line.of("Biome: " + biomeName));
	}

	private String fmt(double v) {
		return decimals.on() ? String.format(Locale.ROOT, "%.1f", v) : Integer.toString((int) Math.floor(v));
	}

	static String facingText(Direction dir) {
		return switch (dir) {
			case NORTH -> "North (-Z)";
			case SOUTH -> "South (+Z)";
			case EAST -> "East (+X)";
			case WEST -> "West (-X)";
			case UP -> "Up";
			case DOWN -> "Down";
		};
	}

	private static String biomeName(Minecraft mc, BlockPos pos) {
		return mc.level.getBiome(pos).unwrapKey()
				.map(key -> {
					Identifier id = key.identifier();
					return Component.translatable(Util.makeDescriptionId("biome", id)).getString();
				})
				.orElse("Unknown");
	}
}
