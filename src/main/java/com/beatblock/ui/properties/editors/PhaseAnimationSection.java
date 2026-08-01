package com.beatblock.ui.properties.editors;

import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;

/** Entry / idle / exit phase durations (when step dispatch enables phase animation). */
public final class PhaseAnimationSection implements EventPropertySection {

	@Override
	public Tab tab() {
		return Tab.ADVANCED;
	}

	@Override
	public int order() {
		return 20;
	}

	@Override
	public boolean supports(EventEditContext context) {
		TimelineAnimationActionMode mode = context.selectedActionMode();
		if (mode != TimelineAnimationActionMode.ANIMATE && mode != TimelineAnimationActionMode.BUILD) {
			return false;
		}
		return context.stepDispatch.get();
	}

	@Override
	public void render(EventEditContext context) {
		AnimationPropertyEditor host = context.editorHost();
		if (ImGui.checkbox(
			BBTexts.get("beatblock.event.phase_animation") + "##eventUsePhaseAnimation",
			context.usePhaseAnimation
		)) {
			host.validationError = null;
		}
		if (!context.usePhaseAnimation.get()) {
			return;
		}
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.entry_phase") + "##eventEntryDuration", host.entryDurationBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.idle_phase") + "##eventIdleDuration", host.idleDurationBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.exit_phase") + "##eventExitDuration", host.exitDurationBuffer);
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.event.phase.tooltip"));
		}
	}
}
