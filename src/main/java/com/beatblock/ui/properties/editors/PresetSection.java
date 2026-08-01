package com.beatblock.ui.properties.editors;

import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.imgui.PresetChannelPreview;
import imgui.ImGui;

/** Animation preset picker, channel preview, and preview popup trigger. */
public final class PresetSection implements EventPropertySection {

	@Override
	public Tab tab() {
		return Tab.BASIC;
	}

	@Override
	public int order() {
		return 30;
	}

	@Override
	public boolean supports(EventEditContext context) {
		return true;
	}

	@Override
	public void render(EventEditContext context) {
		AnimationPropertyEditor host = context.editorHost();
		if (ImGui.combo(
			BBTexts.get("beatblock.event.animation_preset") + "##eventAnimation",
			context.animationIndex,
			context.animationLabels()
		)) {
			host.validationError = null;
		}
		host.trackLivePreviewEdit();

		String selectedAnimationId = context.selectedAnimationId();
		PresetChannelPreview.renderCollapsible(
			BBTexts.get("beatblock.event.preset_channels") + "##eventPresetChannels",
			BlockInfluencePresets.get(selectedAnimationId)
		);
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.event.preview_animation") + "##eventAnimPreview")) {
			host.openAnimationPreview(selectedAnimationId);
		}
	}
}
