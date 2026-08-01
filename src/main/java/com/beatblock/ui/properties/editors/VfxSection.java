package com.beatblock.ui.properties.editors;

import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.engine.influence.InfluenceDimension;
import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;

/** VFX toggle and optional flash block id for appearance channels. */
public final class VfxSection implements EventPropertySection {

	@Override
	public Tab tab() {
		return Tab.BASIC;
	}

	@Override
	public int order() {
		return 40;
	}

	@Override
	public boolean supports(EventEditContext context) {
		return true;
	}

	@Override
	public void render(EventEditContext context) {
		AnimationPropertyEditor host = context.editorHost();
		if (ImGui.checkbox(BBTexts.get("beatblock.event.vfx") + "##eventVfxEnabled", context.vfxEnabled)) {
			host.validationError = null;
		}
		BlockInfluencePreset selectedPreset = BlockInfluencePresets.get(context.selectedAnimationId());
		if (selectedPreset != null && !selectedPreset.channelsFor(InfluenceDimension.APPEARANCE).isEmpty()) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.flash_block") + "##eventFlashBlock", host.flashBlockBuffer);
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.event.vfx.tooltip"));
			}
		}
	}
}
