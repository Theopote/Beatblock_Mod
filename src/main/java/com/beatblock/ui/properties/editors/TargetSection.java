package com.beatblock.ui.properties.editors;

import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;

/** RuntimeStageObject target combo. */
public final class TargetSection implements EventPropertySection {

	@Override
	public Tab tab() {
		return Tab.BASIC;
	}

	@Override
	public int order() {
		return 50;
	}

	@Override
	public boolean supports(EventEditContext context) {
		return true;
	}

	@Override
	public void render(EventEditContext context) {
		AnimationPropertyEditor host = context.editorHost();
		if (ImGui.combo(
			BBTexts.get("beatblock.event.target") + "##eventTarget",
			context.targetIndex,
			context.targetLabels()
		)) {
			host.validationError = null;
		}
	}
}
