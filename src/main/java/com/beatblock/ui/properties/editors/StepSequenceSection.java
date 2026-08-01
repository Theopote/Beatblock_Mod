package com.beatblock.ui.properties.editors;

import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;

/**
 * Step 序列 Section：Step Dispatch、Pacing 模式、Camera Adaptive 等（不含 Phase，见 {@link PhaseAnimationSection}）。
 */
public final class StepSequenceSection implements EventPropertySection {

	private static String[] stepStartModeLabels() {
		return BBTexts.labels(
			"beatblock.event.step_start.next_beat",
			"beatblock.event.step_start.immediate"
		);
	}

	private static String[] stepCompletionLabels() {
		return BBTexts.labels(
			"beatblock.event.step_completion.keep",
			"beatblock.event.step_completion.loop"
		);
	}

	private static String[] pacingModeLabels() {
		return BBTexts.labels(
			"beatblock.event.pacing.beat_grid",
			"beatblock.event.pacing.fixed_interval",
			"beatblock.event.pacing.distance"
		);
	}

	@Override
	public Tab tab() {
		return Tab.ADVANCED;
	}

	@Override
	public int order() {
		return 10;
	}

	@Override
	public boolean supports(EventEditContext context) {
		TimelineAnimationActionMode mode = context.selectedActionMode();
		return mode == TimelineAnimationActionMode.ANIMATE || mode == TimelineAnimationActionMode.BUILD;
	}

	@Override
	public void render(EventEditContext context) {
		AnimationPropertyEditor host = context.editorHost();

		if (ImGui.checkbox(BBTexts.get("beatblock.event.step_dispatch") + "##eventDispatchStep", context.stepDispatch)) {
			host.validationError = null;
		}
		if (!context.stepDispatch.get()) {
			return;
		}

		if (ImGui.combo(BBTexts.get("beatblock.event.pacing_mode") + "##eventPacingMode",
			context.pacingModeIndex, pacingModeLabels())) {
			host.validationError = null;
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.event.pacing_mode.tooltip"));
		}
		boolean distancePacing = "DISTANCE".equals(AnimationPropertyEditor.PACING_MODE_VALUES[context.pacingModeIndex.get()]);
		if (!distancePacing) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.blocks_per_beat") + "##eventBlocksPerBeat", host.blocksPerBeatBuffer);
		}
		if (distancePacing) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.distance_pace") + "##eventDistancePaceSeconds", host.distancePaceSecondsBuffer);
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.min_gap") + "##eventDistancePaceMinGap", host.distancePaceMinGapBuffer);
		}

		if (ImGui.combo(BBTexts.get("beatblock.event.step_start") + "##eventStepStartMode",
			context.stepStartModeIndex, stepStartModeLabels())) {
			host.validationError = null;
		}
		if (ImGui.combo(BBTexts.get("beatblock.event.step_completion") + "##eventStepCompletionMode",
			context.stepCompletionIndex, stepCompletionLabels())) {
			host.validationError = null;
		}

		if (ImGui.checkbox(BBTexts.get("beatblock.event.camera_adaptive") + "##eventCameraAdaptiveStep", context.cameraAdaptiveStep)) {
			host.validationError = null;
		}
		if (context.cameraAdaptiveStep.get()) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.near_distance") + "##eventCameraNearDistance", host.cameraNearDistanceBuffer);
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.far_distance") + "##eventCameraFarDistance", host.cameraFarDistanceBuffer);
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.near_scale") + "##eventCameraNearScale", host.cameraNearScaleBuffer);
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.far_scale") + "##eventCameraFarScale", host.cameraFarScaleBuffer);
		}

		if (ImGui.checkbox(BBTexts.get("beatblock.event.frustum_gating") + "##eventCameraFrustumGating", context.cameraFrustumGating)) {
			host.validationError = null;
		}
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.edge_priority") + "##eventCameraEdgePriority", host.cameraEdgePriorityBuffer);
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.event.edge_priority.tooltip"));
		}
	}
}
