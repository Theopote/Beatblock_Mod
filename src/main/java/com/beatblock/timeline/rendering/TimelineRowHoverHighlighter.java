package com.beatblock.timeline.rendering;

import imgui.ImGui;

import java.util.List;
import java.util.Map;

/** 时间线轨道区 hover 联动高亮（特征轨配对、相机/动画行）。 */
public final class TimelineRowHoverHighlighter {

	private static final int PAIRED_ROW_HOVER_FILL = 0x33_8A_BA_FF;
	private static final int PAIRED_ROW_HOVER_BORDER = 0x99_A0_D0_FF;

	private TimelineRowHoverHighlighter() {
	}

	public static void drawPairedFeatureHoverHighlight(
		TimelineLayout layout,
		List<TrackDefinition> audioSubTracks,
		List<TrackDefinition> animationSubTracks
	) {
		if (layout == null || !isPointerInTrackArea(layout)) return;

		int hoveredRow = layout.findRowAtScreenY(ImGui.getMousePosY());
		if (hoveredRow < 0) return;

		String hoveredFeature = TimelineFeatureLaneIndex.hoveredFeatureKey(
			hoveredRow, audioSubTracks, animationSubTracks);
		if (hoveredFeature == null || hoveredFeature.isBlank()) return;

		Map<String, Integer> audioFeatureRows = TimelineFeatureLaneIndex.audioImpulseRows(audioSubTracks);
		Map<String, Integer> controlFeatureRows = TimelineFeatureLaneIndex.controlFeatureRows(animationSubTracks);
		float x0 = layout.trackHeaderLeft;
		float x1 = layout.contentLeft + layout.contentWidth;

		Integer audioRow = audioFeatureRows.get(hoveredFeature);
		Integer controlRow = controlFeatureRows.get(hoveredFeature);
		if (audioRow != null) drawHoverRowHighlight(layout, audioRow, x0, x1);
		if (controlRow != null) drawHoverRowHighlight(layout, controlRow, x0, x1);
	}

	public static void drawActionCameraHoverHighlight(
		TimelineLayout layout,
		List<TrackDefinition> buildLayerTracks
	) {
		if (layout == null || !isPointerInTrackArea(layout)) return;

		int hoveredRow = layout.findRowAtScreenY(ImGui.getMousePosY());
		if (hoveredRow < 0) return;

		boolean hoveredBuildLayer = TimelineTrackMeta.isBuildLayerSubRow(hoveredRow);
		boolean hoveredActionCamera = hoveredRow == TimelineTrackMeta.ROW_CAMERA
			|| hoveredRow == TimelineTrackMeta.ROW_ANIM_BLOCK
			|| hoveredRow == TimelineTrackMeta.ROW_ANIM_AUTO
			|| hoveredBuildLayer;

		if (!hoveredActionCamera) return;

		float x0 = layout.trackHeaderLeft;
		float x1 = layout.contentLeft + layout.contentWidth;
		drawHoverRowHighlight(layout, hoveredRow, x0, x1);

		if (hoveredRow == TimelineTrackMeta.ROW_CAMERA) {
			drawHoverRowHighlight(layout, TimelineTrackMeta.ROW_ANIM_BLOCK, x0, x1);
			drawHoverRowHighlight(layout, TimelineTrackMeta.ROW_ANIM_AUTO, x0, x1);
			int buildCount = buildLayerTracks != null ? buildLayerTracks.size() : layout.getActiveBuildLayerRowCount();
			for (int slot = 0; slot < buildCount && slot < TimelineTrackMeta.MAX_BUILD_LAYER_ROWS; slot++) {
				drawHoverRowHighlight(layout, TimelineTrackMeta.ROW_BUILD_LAYER_START + slot, x0, x1);
			}
		} else {
			drawHoverRowHighlight(layout, TimelineTrackMeta.ROW_CAMERA, x0, x1);
		}
	}

	private static boolean isPointerInTrackArea(TimelineLayout layout) {
		if (!ImGui.isWindowHovered()) return false;
		float mx = ImGui.getMousePosX();
		float my = ImGui.getMousePosY();
		float x0 = layout.trackHeaderLeft;
		float x1 = layout.contentLeft + layout.contentWidth;
		return mx >= x0 && mx <= x1
			&& my >= layout.contentTop
			&& my <= layout.contentTop + layout.contentHeight;
	}

	private static void drawHoverRowHighlight(TimelineLayout layout, int rowIndex, float x0, float x1) {
		if (layout == null || !layout.isRowVisible(rowIndex)) return;
		float y0 = layout.getRowScreenY(rowIndex);
		float y1 = y0 + layout.getRowHeight(rowIndex);
		if (y0 < 0 || y1 <= y0) return;
		ImGui.getWindowDrawList().addRectFilled(x0, y0, x1, y1, PAIRED_ROW_HOVER_FILL, 2f);
		ImGui.getWindowDrawList().addRect(x0, y0, x1, y1, PAIRED_ROW_HOVER_BORDER, 2f, 0, 1f);
	}
}
