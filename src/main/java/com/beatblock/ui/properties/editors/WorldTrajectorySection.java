package com.beatblock.ui.properties.editors;

import com.beatblock.timeline.editing.WorldTrajectoryEventParamsEditor;
import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;

/**
 * 世界轨迹参数 Section：Meteor / RhythmDrop 等需要落点与高度/冲击阈值的动画。
 */
public final class WorldTrajectorySection implements EventPropertySection {

	@Override
	public boolean supports(EventEditContext context) {
		return WorldTrajectoryEventParamsEditor.supports(context.selectedAnimationId());
	}

	@Override
	public void render(EventEditContext context) {
		String animationId = context.selectedAnimationId();
		boolean rhythmDrop = WorldTrajectoryEventParamsEditor.RHYTHM_DROP_ANIMATION_ID.equalsIgnoreCase(animationId);
		AnimationPropertyEditor host = context.editorHost();

		ImGui.spacing();
		ImGui.textDisabled(rhythmDrop ? BBTexts.get("beatblock.event.rhythm_drop") : BBTexts.get("beatblock.event.meteor"));
		ImGui.textWrapped(rhythmDrop
			? BBTexts.get("beatblock.event.rhythm_drop.hint")
			: BBTexts.get("beatblock.event.meteor.hint"));

		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.landing_x") + "##eventSingleBlockX", host.singleBlockXBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.landing_y") + "##eventSingleBlockY", host.singleBlockYBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.landing_z") + "##eventSingleBlockZ", host.singleBlockZBuffer);
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.event.landing.tooltip"));
		}

		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.fall_height") + "##eventMeteorHeight", host.meteorHeightBuffer);
		ImGui.setNextItemWidth(-1f);
		if (rhythmDrop) {
			ImGui.inputText(BBTexts.get("beatblock.event.impact_threshold") + "##eventImpactThreshold", host.impactThresholdBuffer);
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.event.impact_threshold.tooltip"));
			}
		} else {
			ImGui.inputText(BBTexts.get("beatblock.event.scatter") + "##eventMeteorScatter", host.meteorScatterBuffer);
		}
	}
}
