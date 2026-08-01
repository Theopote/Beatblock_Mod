package com.beatblock.ui.properties.editors;

import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;

/** Inherit group spatial, spatial mode/delay, and PLACE block id. */
public final class SpatialDispatchSection implements EventPropertySection {

	private static String[] spatialModeLabels() {
		return BBTexts.labels(
			"beatblock.event.spatial.all",
			"beatblock.event.spatial.sequential",
			"beatblock.event.spatial.radial",
			"beatblock.event.spatial.random",
			"beatblock.event.spatial.spiral"
		);
	}

	@Override
	public Tab tab() {
		return Tab.SPATIAL;
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
		if (ImGui.checkbox(
			BBTexts.get("beatblock.event.inherit_spatial") + "##eventInheritGroupSpatial",
			context.inheritGroupSpatial
		)) {
			host.validationError = null;
		}
		if (!context.inheritGroupSpatial.get()) {
			if (ImGui.combo(
				BBTexts.get("beatblock.event.spatial_mode") + "##eventSpatialMode",
				context.spatialModeIndex,
				spatialModeLabels()
			)) {
				host.validationError = null;
			}
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.spatial_delay") + "##eventSpatialDelay", host.spatialDelayBuffer);
		}
		if (context.selectedActionMode() == TimelineAnimationActionMode.PLACE) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.event.place_block") + "##eventPlaceBlock", host.placeBlockBuffer);
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.event.place_block.tooltip"));
			}
		}
	}
}
