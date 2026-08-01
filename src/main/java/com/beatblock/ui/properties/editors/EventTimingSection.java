package com.beatblock.ui.properties.editors;

import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;

/** Start time, duration, energy, energy threshold. */
public final class EventTimingSection implements EventPropertySection {

	@Override
	public Tab tab() {
		return Tab.BASIC;
	}

	@Override
	public int order() {
		return 10;
	}

	@Override
	public boolean supports(EventEditContext context) {
		return true;
	}

	@Override
	public void render(EventEditContext context) {
		AnimationPropertyEditor host = context.editorHost();
		ImGui.text(BBTexts.get("beatblock.event.timing"));
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.start_time") + "##eventTime", host.timeBuffer);
		host.trackLivePreviewEdit();
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.duration") + "##eventDuration", host.durationBuffer);
		host.trackLivePreviewEdit();
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.energy") + "##eventEnergy", host.energyBuffer);
		host.trackLivePreviewEdit();
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.energy_threshold") + "##eventEnergyThreshold", host.energyThresholdBuffer);
		host.trackLivePreviewEdit();
	}
}
