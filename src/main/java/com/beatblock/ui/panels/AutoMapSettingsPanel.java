package com.beatblock.ui.panels;

import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.AutoMapStyle;
import com.beatblock.automap.engine.Complexity;
import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.AutoMapSettingsPanelPresenter;
import com.beatblock.ui.presenter.PresenterFactories;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;

import java.util.function.Consumer;

/**
 * Smart Auto-Map 设置弹窗：风格、复杂度、镜头/粒子开关，点击 Generate 执行编排并关闭。
 */
public final class AutoMapSettingsPanel {

	private final AutoMapSettingsPanelPresenter presenter;
	private final AutoMapSettings settings = new AutoMapSettings();
	private final ImInt styleIndex = new ImInt(0);
	private final ImInt complexityIndex = new ImInt(1);

	public AutoMapSettingsPanel() {
		this(PresenterFactories.autoMapSettingsPanelPresenter());
	}

	AutoMapSettingsPanel(AutoMapSettingsPanelPresenter presenter) {
		this.presenter = presenter;
	}

	private static String[] styleLabels() {
		return BBTexts.labels(
			"beatblock.automap.style.edm",
			"beatblock.automap.style.cinematic",
			"beatblock.automap.style.ambient",
			"beatblock.automap.style.chaos",
			"beatblock.automap.style.minimal"
		);
	}

	private static String[] complexityLabels() {
		return BBTexts.labels(
			"beatblock.automap.complexity.low",
			"beatblock.automap.complexity.medium",
			"beatblock.automap.complexity.high",
			"beatblock.automap.complexity.extreme"
		);
	}

	/**
	 * 渲染弹窗。若返回 true 表示已执行生成并关闭，onResult 已被调用。
	 */
	public boolean render(Consumer<SmartAutoMapEngine.AutoMapResult> onResult) {
		if (!ImGui.begin(BBTexts.get("beatblock.automap.title"), ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.end();
			return false;
		}
		ImGui.text(BBTexts.get("beatblock.automap.description"));
		ImGui.spacing();

		ImGui.text(BBTexts.get("beatblock.automap.style"));
		if (ImGui.combo("##style", styleIndex, styleLabels())) {
			int i = Math.max(0, Math.min(styleIndex.get(), AutoMapStyle.values().length - 1));
			settings.setStyle(AutoMapStyle.values()[i]);
		}
		ImGui.sameLine();
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.automap.style.tooltip"));

		ImGui.text(BBTexts.get("beatblock.automap.complexity"));
		if (ImGui.combo("##complexity", complexityIndex, complexityLabels())) {
			int i = Math.max(0, Math.min(complexityIndex.get(), Complexity.values().length - 1));
			settings.setComplexity(Complexity.values()[i]);
		}
		ImGui.sameLine();
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.automap.complexity.tooltip"));

		boolean cam = settings.isCameraEnabled();
		if (ImGui.checkbox(BBTexts.get("beatblock.automap.camera"), cam)) settings.setCameraEnabled(!cam);
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.automap.camera.tooltip"));
		ImGui.sameLine();
		boolean part = settings.isParticlesEnabled();
		if (ImGui.checkbox(BBTexts.get("beatblock.automap.particles"), part)) settings.setParticlesEnabled(!part);
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.automap.particles.tooltip"));

		ImGui.spacing();
		ImGui.separator();
		ImGui.spacing();

		settings.setStyle(AutoMapStyle.values()[Math.max(0, Math.min(styleIndex.get(), AutoMapStyle.values().length - 1))]);
		settings.setComplexity(Complexity.values()[Math.max(0, Math.min(complexityIndex.get(), Complexity.values().length - 1))]);

		boolean generated = false;
		if (ImGui.button(BBTexts.get("beatblock.automap.generate"), 120, 0)) {
			var outcome = presenter.generate(settings);
			if (outcome.result().ok()) {
				if (onResult != null) {
					onResult.accept(outcome.autoMapResult());
				}
				generated = true;
			}
		}
		if (ImGui.isItemHovered() && !presenter.canGenerate()) {
			String reason = presenter.generateBlockedReason();
			if (reason != null) {
				ImGui.setTooltip(reason);
			}
		}

		ImGui.end();
		return generated;
	}

	public AutoMapSettings getSettings() {
		return settings;
	}

	public void setStyleIndex(int index) {
		styleIndex.set(Math.max(0, Math.min(index, AutoMapStyle.values().length - 1)));
		settings.setStyle(AutoMapStyle.values()[styleIndex.get()]);
	}

	public void setComplexityIndex(int index) {
		complexityIndex.set(Math.max(0, Math.min(index, Complexity.values().length - 1)));
		settings.setComplexity(Complexity.values()[complexityIndex.get()]);
	}
}
