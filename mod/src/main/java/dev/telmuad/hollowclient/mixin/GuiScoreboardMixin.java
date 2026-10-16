package dev.telmuad.hollowclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.telmuad.hollowclient.gui.Draw;
import dev.telmuad.hollowclient.gui.Theme;
import dev.telmuad.hollowclient.modules.Modules;
import dev.telmuad.hollowclient.modules.ui.ScoreboardTitlesModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Scoreboard & Titles: the sidebar, boss bars and titles drawn moved, scaled, restyled or not at all. Only the
 * drawing is wrapped; what the server sent is untouched.
 */
@Mixin(Hud.class)
abstract class GuiScoreboardMixin {
	@Unique
	private static int hollowclient$titleTop;
	@Unique
	private static int hollowclient$titleBottom;

	@Unique
	private static ScoreboardTitlesModule hollowclient$module() {
		ScoreboardTitlesModule m = Modules.SCOREBOARD_TITLES;
		return m != null && m.isEnabled() ? m : null;
	}

	// ------------------------------------------------------------------------------------------------ scoreboard

	@WrapOperation(method = "extractScoreboardSidebar", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/Hud;displayScoreboardSidebar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/scores/Objective;)V"))
	private void hollowclient$placeSidebar(Hud self, GuiGraphicsExtractor g, Objective objective, Operation<Void> original) {
		ScoreboardTitlesModule m = hollowclient$module();
		if (m == null) {
			original.call(self, g, objective);
			return;
		}
		if (!m.showScoreboard()) return;
		int w = g.guiWidth();
		int h = g.guiHeight();
		float s = m.scoreboardScale();
		float fromX = w - 1;
		float toX = w - 1;
		if (m.side() == ScoreboardTitlesModule.Side.LEFT) {
			fromX = w - hollowclient$contentWidth(objective, m.numbers()) - 5;
			toX = 2;
		}
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(toX, h / 2f + m.scoreboardOffset());
		pose.scale(s, s);
		pose.translate(-fromX, -h / 2f);
		try {
			original.call(self, g, objective);
		} finally {
			pose.popMatrix();
		}
	}

	/** The sidebar's text width, worked out the same way vanilla does (for placing it on the left). */
	@Unique
	private static int hollowclient$contentWidth(Objective objective, boolean numbers) {
		Font font = Minecraft.getInstance().font;
		Scoreboard scoreboard = objective.getScoreboard();
		NumberFormat format = numbers ? objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT) : BlankFormat.INSTANCE;
		int width = font.width(objective.getDisplayName());
		int colon = font.width(": ");
		int n = 0;
		for (PlayerScoreEntry e : scoreboard.listPlayerScores(objective)) {
			if (e.isHidden()) continue;
			if (n++ >= 15) break;
			PlayerTeam team = scoreboard.getPlayersTeam(e.owner());
			Component name = PlayerTeam.formatNameForTeam(team, e.ownerName());
			int scoreWidth = font.width(e.formatValue(format));
			width = Math.max(width, font.width(name) + (scoreWidth > 0 ? colon + scoreWidth : 0));
		}
		return width;
	}

	@ModifyExpressionValue(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/scores/Objective;numberFormatOrDefault(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/numbers/NumberFormat;"))
	private NumberFormat hollowclient$hideNumbers(NumberFormat original) {
		ScoreboardTitlesModule m = hollowclient$module();
		return m != null && !m.numbers() ? BlankFormat.INSTANCE : original;
	}

	/** Glass look: one rounded panel with an accent line under the title instead of the two flat boxes. */
	@WrapOperation(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", ordinal = 0,
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
	private void hollowclient$titleBox(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
		ScoreboardTitlesModule m = hollowclient$module();
		if (m == null || m.look() != ScoreboardTitlesModule.Look.GLASS) {
			original.call(g, x0, y0, x1, y1, color);
			return;
		}
		hollowclient$titleTop = y0;
		hollowclient$titleBottom = y1;
	}

	@WrapOperation(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", ordinal = 1,
			target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
	private void hollowclient$bodyBox(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
		ScoreboardTitlesModule m = hollowclient$module();
		if (m == null || m.look() != ScoreboardTitlesModule.Look.GLASS) {
			original.call(g, x0, y0, x1, y1, color);
			return;
		}
		int top = hollowclient$titleTop - 2;
		Draw.roundRect(g, x0 - 2, top, x1 - x0 + 4, y1 - top + 2, Theme.radius(4), Theme.GLASS);
		Draw.roundOutline(g, x0 - 2, top, x1 - x0 + 4, y1 - top + 2, Theme.radius(4), Theme.STROKE);
		g.fill(x0 + 2, hollowclient$titleBottom, x1 - 2, hollowclient$titleBottom + 1, Theme.accent());
	}

	// ------------------------------------------------------------------------------------------------ boss bars

	@WrapOperation(method = "extractBossOverlay", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/client/gui/components/BossHealthOverlay;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
	private void hollowclient$bossBars(BossHealthOverlay overlay, GuiGraphicsExtractor g, Operation<Void> original) {
		ScoreboardTitlesModule m = hollowclient$module();
		if (m == null) {
			original.call(overlay, g);
			return;
		}
		if (!m.showBossBars()) return;
		float s = m.bossScale();
		Matrix3x2fStack pose = g.pose();
		pose.pushMatrix();
		pose.translate(g.guiWidth() / 2f, m.bossOffset());
		pose.scale(s, s);
		pose.translate(-g.guiWidth() / 2f, 0);
		try {
			original.call(overlay, g);
		} finally {
			pose.popMatrix();
		}
	}

	// ------------------------------------------------------------------------------------------------ titles

	@Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true)
	private void hollowclient$hideTitles(GuiGraphicsExtractor g, net.minecraft.client.DeltaTracker deltaTracker, CallbackInfo ci) {
		ScoreboardTitlesModule m = hollowclient$module();
		if (m != null && !m.showTitles()) ci.cancel();
	}

	@WrapOperation(method = "extractTitle", at = @At(value = "INVOKE", ordinal = 0, target = "Lorg/joml/Matrix3x2fStack;translate(FF)Lorg/joml/Matrix3x2f;"))
	private Matrix3x2f hollowclient$titlePlace(Matrix3x2fStack pose, float x, float y, Operation<Matrix3x2f> original) {
		ScoreboardTitlesModule m = hollowclient$module();
		return original.call(pose, x, m == null ? y : y + m.titleOffset());
	}

	@WrapOperation(method = "extractTitle", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;scale(FF)Lorg/joml/Matrix3x2f;"))
	private Matrix3x2f hollowclient$titleSize(Matrix3x2fStack pose, float x, float y, Operation<Matrix3x2f> original) {
		ScoreboardTitlesModule m = hollowclient$module();
		float s = m == null ? 1f : m.titleScale();
		return original.call(pose, x * s, y * s);
	}
}
