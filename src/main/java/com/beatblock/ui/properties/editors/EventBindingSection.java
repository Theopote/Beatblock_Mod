package com.beatblock.ui.properties.editors;

import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;

/** Action mode combo (ANIMATE / PLACE / BUILD / CLEAR). */
public final class EventBindingSection implements EventPropertySection {

	@Override
	public Tab tab() {
		return Tab.BASIC;
	}

	@Override
	public int order() {
		return 20;
	}

	@Override
	public boolean supports(EventEditContext context) {
		return true;
	}

	@Override
	public void render(EventEditContext context) {
		AnimationPropertyEditor host = context.editorHost();
		ImGui.spacing();
		ImGui.text(BBTexts.get("beatblock.event.binding"));
		if (ImGui.combo(
			BBTexts.get("beatblock.event.action_mode_combo") + "##eventActionMode",
			context.actionIndex,
			context.actionLabels()
		)) {
			host.validationError = null;
		}
		host.trackLivePreviewEdit();
	}
}
