package com.beatblock.ui.panels;

import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.AutoMapSettingsStore;
import com.beatblock.automap.engine.AutoMapStyle;
import com.beatblock.automap.engine.Complexity;
import com.beatblock.automap.engine.SmartAutoMapEngine;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.AutoMapSettingsPanelPresenter;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.StageTargetOption;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImFloat;
import imgui.type.ImInt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Smart Auto-Map 设置弹窗：风格、复杂度、目标映射、per-feature minGap、镜头/粒子开关。
 */
public final class AutoMapSettingsPanel {

	private final AutoMapSettingsPanelPresenter presenter;
	private final AutoMapSettings settings = AutoMapSettingsStore.current();
	private final ImInt styleIndex = new ImInt(0);
	private final ImInt complexityIndex = new ImInt(1);
	private final ImInt lowTargetIndex = new ImInt(0);
	private final ImInt midTargetIndex = new ImInt(0);
	private final ImInt highTargetIndex = new ImInt(0);
	private final ImFloat minGapLow = new ImFloat(0f);
	private final ImFloat minGapMid = new ImFloat(0f);
	private final ImFloat minGapHigh = new ImFloat(0f);
	private boolean targetsInitialized;

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

	public boolean render(Consumer<SmartAutoMapEngine.AutoMapResult> onResult) {
		if (!ImGui.begin(BBTexts.get("beatblock.automap.title"), ImGuiWindowFlags.AlwaysAutoResize)) {
			ImGui.end();
			return false;
		}
		ensureTargetsInitialized();
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

		renderTargetMapping();
		renderMinGapFields();

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

		syncSettingsFromUi();

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

	private void ensureTargetsInitialized() {
		if (targetsInitialized) return;
		presenter.applyDefaultTargets(settings);
		List<StageTargetOption> targets = presenter.listStageTargets();
		List<String> ids = settings.getTargetObjectIds();
		if (!ids.isEmpty()) {
			lowTargetIndex.set(StageTargetOption.indexOfId(targets, ids.get(0)));
		}
		if (ids.size() > 1) {
			midTargetIndex.set(StageTargetOption.indexOfId(targets, ids.get(1)));
		}
		if (ids.size() > 2) {
			highTargetIndex.set(StageTargetOption.indexOfId(targets, ids.get(2)));
		}
		minGapLow.set((float) settings.getMinGapLow());
		minGapMid.set((float) settings.getMinGapMid());
		minGapHigh.set((float) settings.getMinGapHigh());
		targetsInitialized = true;
	}

	private void renderTargetMapping() {
		List<StageTargetOption> targets = presenter.listStageTargets();
		if (targets.isEmpty()) {
			ImGui.textDisabled(BBTexts.get("beatblock.automap.targets.empty"));
			return;
		}
		ImGui.text(BBTexts.get("beatblock.automap.targets"));
		renderTargetCombo(BBTexts.get("beatblock.automap.target.low"), "##lowTarget", lowTargetIndex, targets);
		renderTargetCombo(BBTexts.get("beatblock.automap.target.mid"), "##midTarget", midTargetIndex, targets);
		renderTargetCombo(BBTexts.get("beatblock.automap.target.high"), "##highTarget", highTargetIndex, targets);
	}

	private static void renderTargetCombo(String label, String id, ImInt index, List<StageTargetOption> targets) {
		int safeIndex = Math.max(0, Math.min(index.get(), targets.size() - 1));
		index.set(safeIndex);
		ImGui.setNextItemWidth(-1f);
		if (ImGui.beginCombo(label + id, targets.get(safeIndex).displayName())) {
			for (int i = 0; i < targets.size(); i++) {
				if (ImGui.selectable(targets.get(i).displayName(), index.get() == i)) {
					index.set(i);
				}
			}
			ImGui.endCombo();
		}
	}

	private void renderMinGapFields() {
		ImGui.text(BBTexts.get("beatblock.automap.min_gap"));
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.automap.min_gap.tooltip"));
		ImGui.inputFloat(BBTexts.get("beatblock.automap.min_gap.low") + "##gapLow", minGapLow, 0.01f, 0.05f, "%.2f");
		ImGui.inputFloat(BBTexts.get("beatblock.automap.min_gap.mid") + "##gapMid", minGapMid, 0.01f, 0.05f, "%.2f");
		ImGui.inputFloat(BBTexts.get("beatblock.automap.min_gap.high") + "##gapHigh", minGapHigh, 0.01f, 0.05f, "%.2f");
	}

	private void syncSettingsFromUi() {
		settings.setStyle(AutoMapStyle.values()[Math.max(0, Math.min(styleIndex.get(), AutoMapStyle.values().length - 1))]);
		settings.setComplexity(Complexity.values()[Math.max(0, Math.min(complexityIndex.get(), Complexity.values().length - 1))]);
		List<StageTargetOption> targets = presenter.listStageTargets();
		if (!targets.isEmpty()) {
			List<String> ids = new ArrayList<>(3);
			ids.add(targets.get(Math.max(0, Math.min(lowTargetIndex.get(), targets.size() - 1))).id());
			ids.add(targets.get(Math.max(0, Math.min(midTargetIndex.get(), targets.size() - 1))).id());
			ids.add(targets.get(Math.max(0, Math.min(highTargetIndex.get(), targets.size() - 1))).id());
			settings.setTargetObjectIds(ids);
		}
		settings.setMinGapLow(minGapLow.get() > 0f ? minGapLow.get() : 0.0);
		settings.setMinGapMid(minGapMid.get() > 0f ? minGapMid.get() : 0.0);
		settings.setMinGapHigh(minGapHigh.get() > 0f ? minGapHigh.get() : 0.0);
	}

	public AutoMapSettings getSettings() {
		syncSettingsFromUi();
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
