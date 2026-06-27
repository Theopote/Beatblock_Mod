package com.beatblock.timeline.rendering;

import com.beatblock.BeatBlock;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.TimelineToolbarActionsPresenter;
import com.beatblock.ui.presenter.TimelineToolbarFeedbackPresenter;
import imgui.ImGui;

final class TimelineToolbarToolsControls {

	private static String tooltipBindingMap() {
		return BBTexts.get("beatblock.timeline.binding_map.tooltip");
	}

	private static String tooltipBindingEditor() {
		return BBTexts.get("beatblock.timeline.binding_editor.tooltip");
	}

	private static String tooltipAutoMap() {
		return BBTexts.get("beatblock.timeline.auto_map.tooltip");
	}

	private static String tooltipBakeStep() {
		return BBTexts.get("beatblock.timeline.bake_step.tooltip");
	}

	private static String tooltipRhythmDrop() {
		return BBTexts.get("beatblock.timeline.rhythm_drop.tooltip");
	}

	private final TimelineToolbarActionsPresenter actions;
	private final TimelineToolbarFeedbackPresenter feedback;
	private final TimelineBindingEditorPopup bindingEditorPopup;

	TimelineToolbarToolsControls(
		TimelineToolbarActionsPresenter actions,
		TimelineToolbarFeedbackPresenter feedback,
		TimelineBindingEditorPopup bindingEditorPopup
	) {
		this.actions = actions;
		this.feedback = feedback;
		this.bindingEditorPopup = bindingEditorPopup;
	}

	void renderInline() {
		renderStageObjectWarning(true);
		renderBindingMap(BBTexts.get("beatblock.timeline.binding_map"), "");
		TimelineToolbarImGui.nextItemInGroup();
		renderBindingEditorOpen(BBTexts.get("beatblock.timeline.bindings") + "##tlBindingEditorOpen");
		bindingEditorPopup.renderIfOpen();
		TimelineToolbarImGui.nextItemInGroup();
		renderAutoMap(BBTexts.get("beatblock.timeline.auto_map"), "");
		TimelineToolbarImGui.nextItemInGroup();
		renderBakeStep(BBTexts.get("beatblock.timeline.bake_step") + "##tlBakeStep", "");
		TimelineToolbarImGui.nextItemInGroup();
		renderRhythmDrop(BBTexts.get("beatblock.timeline.rhythm_drop_short") + "##tlRhythmDrop", "");
		TimelineToolbarImGui.nextItemInGroup();
		TimelineToolbarImGui.renderFeedback(feedback.viewToolActionFeedback());
	}

	void renderCompact() {
		ImGui.separator();
		ImGui.textDisabled(BBTexts.get("beatblock.timeline.tools"));
		renderBindingMap(BBTexts.get("beatblock.timeline.binding_map") + "##tlMoreBindingMap", tooltipBindingMap());
		ImGui.sameLine();
		renderBindingEditorOpen(BBTexts.get("beatblock.timeline.bindings") + "##tlMoreBindingEditorOpen");
		bindingEditorPopup.renderIfOpen();
		renderAutoMap(BBTexts.get("beatblock.timeline.auto_map") + "##tlMoreAutoMap", tooltipAutoMap());
		renderBakeStep(BBTexts.get("beatblock.timeline.bake_step") + "##tlMoreBakeStep", tooltipBakeStep());
		renderRhythmDrop(BBTexts.get("beatblock.timeline.rhythm_drop_btn") + "##tlMoreRhythmDrop", tooltipRhythmDrop());
		TimelineToolbarImGui.renderFeedback(feedback.viewToolActionFeedback());
	}

	private void renderStageObjectWarning(boolean inlineSpacing) {
		int objCount = BeatBlock.getContext().blockAnimationEngine() != null
			? BeatBlock.getContext().blockAnimationEngine().getStageObjectSystem().size() : 0;
		if (objCount != 0) return;
		ImGui.textColored(0.95f, 0.65f, 0.30f, 1f, BBTexts.get("beatblock.timeline.no_stage_object"));
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.timeline.no_stage_object.tooltip"));
		}
		if (inlineSpacing) TimelineToolbarImGui.nextItemInGroup();
	}

	private void renderBindingMap(String label, String tooltipOverride) {
		if (ImGui.button(label)) {
			var outcome = actions.runBindingMap();
			feedback.setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(tooltipOverride.isEmpty() ? tooltipBindingMap() : tooltipOverride);
	}

	private void renderBindingEditorOpen(String label) {
		if (ImGui.button(label)) {
			ImGui.openPopup(TimelineBindingEditorPopup.POPUP_ID);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(tooltipBindingEditor());
	}

	private void renderAutoMap(String label, String tooltipOverride) {
		if (ImGui.button(label)) {
			var outcome = actions.runAutoMap();
			feedback.setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(tooltipOverride.isEmpty() ? tooltipAutoMap() : tooltipOverride);
	}

	private void renderBakeStep(String label, String tooltipOverride) {
		if (ImGui.button(label)) {
			var outcome = actions.runBakeStepSequences();
			feedback.setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(tooltipOverride.isEmpty() ? tooltipBakeStep() : tooltipOverride);
	}

	private void renderRhythmDrop(String label, String tooltipOverride) {
		if (ImGui.button(label)) {
			var outcome = actions.runGenerateRhythmDrops();
			feedback.setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(tooltipOverride.isEmpty() ? tooltipRhythmDrop() : tooltipOverride);
	}
}
