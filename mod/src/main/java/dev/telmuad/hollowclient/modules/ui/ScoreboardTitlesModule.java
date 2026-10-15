package dev.telmuad.hollowclient.modules.ui;

import dev.telmuad.hollowclient.module.Category;
import dev.telmuad.hollowclient.module.Module;
import dev.telmuad.hollowclient.setting.BooleanSetting;
import dev.telmuad.hollowclient.setting.EnumSetting;
import dev.telmuad.hollowclient.setting.NumberSetting;

/** Move, resize, restyle or hide the scoreboard sidebar, boss bars and big titles. Only changes how they look. */
public final class ScoreboardTitlesModule extends Module {
	public enum Side {RIGHT, LEFT}

	public enum Look {VANILLA, GLASS}

	private final BooleanSetting showScoreboard = add(new BooleanSetting("scoreboard", "Show the scoreboard", true));
	private final EnumSetting<Look> look = add(new EnumSetting<>("look", "Scoreboard look", Look.GLASS));
	private final EnumSetting<Side> side = add(new EnumSetting<>("side", "Scoreboard side", Side.RIGHT));
	private final NumberSetting scoreboardScale = add(new NumberSetting("scoreboardScale", "Scoreboard size", 100, 50, 150, 5).format(0, "%"));
	private final NumberSetting scoreboardOffset = add(new NumberSetting("scoreboardOffset", "Scoreboard up / down", 0, -120, 120, 2).format(0, ""));
	private final BooleanSetting numbers = add(new BooleanSetting("numbers", "Show the red score numbers", true));
	private final BooleanSetting showBoss = add(new BooleanSetting("bossBars", "Show boss bars", true));
	private final NumberSetting bossScale = add(new NumberSetting("bossScale", "Boss bar size", 100, 50, 150, 5).format(0, "%"));
	private final NumberSetting bossOffset = add(new NumberSetting("bossOffset", "Boss bars down", 0, 0, 120, 2).format(0, ""));
	private final BooleanSetting showTitles = add(new BooleanSetting("titles", "Show big titles", true));
	private final NumberSetting titleScale = add(new NumberSetting("titleScale", "Title size", 100, 40, 150, 5).format(0, "%"));
	private final NumberSetting titleOffset = add(new NumberSetting("titleOffset", "Title up / down", 0, -120, 120, 2).format(0, ""));

	public ScoreboardTitlesModule() {
		super("scoreboard_titles", "Scoreboard & Titles", "Move, resize, restyle or hide the scoreboard, boss bars and big titles.",
				Category.INTERFACE, false);
		for (var s : new NumberSetting[]{scoreboardScale, scoreboardOffset}) s.visibleWhen(showScoreboard::get);
		look.visibleWhen(showScoreboard::get);
		side.visibleWhen(showScoreboard::get);
		numbers.visibleWhen(showScoreboard::get);
		bossScale.visibleWhen(showBoss::get);
		bossOffset.visibleWhen(showBoss::get);
		titleScale.visibleWhen(showTitles::get);
		titleOffset.visibleWhen(showTitles::get);
	}

	public boolean showScoreboard() {
		return showScoreboard.on();
	}

	public Look look() {
		return look.get();
	}

	public Side side() {
		return side.get();
	}

	public float scoreboardScale() {
		return scoreboardScale.getFloat() / 100f;
	}

	public float scoreboardOffset() {
		return scoreboardOffset.getFloat();
	}

	public boolean numbers() {
		return numbers.on();
	}

	public boolean showBossBars() {
		return showBoss.on();
	}

	public float bossScale() {
		return bossScale.getFloat() / 100f;
	}

	public float bossOffset() {
		return bossOffset.getFloat();
	}

	public boolean showTitles() {
		return showTitles.on();
	}

	public float titleScale() {
		return titleScale.getFloat() / 100f;
	}

	public float titleOffset() {
		return titleOffset.getFloat();
	}
}
