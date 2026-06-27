package com.beatblock.timeline.rendering;

import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;

final class TimelineToolbarSnapGridControls {

	void renderInline(TimelineToolbarState toolbarState) {
		if (toolbarState == null) return;
		renderSnap(toolbarState, BBTexts.get("beatblock.timeline.snap"), false);
		TimelineToolbarImGui.nextItemInGroup();
		renderBeatSnap(toolbarState, BBTexts.get("beatblock.timeline.beat_snap"), false);
		TimelineToolbarImGui.nextItemInGroup();
		renderBeatGrid(toolbarState, BBTexts.get("beatblock.timeline.beat_grid"), false);
		TimelineToolbarImGui.nextItemInGroup();
		renderMagnet(toolbarState, BBTexts.get("beatblock.timeline.magnet"), false);
		TimelineToolbarImGui.nextGroupOrWrap(0);
		renderLoop(toolbarState, BBTexts.get("beatblock.timeline.loop"), false);
		TimelineToolbarImGui.nextGroupOrWrap(0);
	}

	void renderCompact(TimelineToolbarState toolbarState) {
		if (toolbarState == null) return;
		ImGui.separator();
		ImGui.textDisabled(BBTexts.get("beatblock.timeline.snap_grid"));
		renderSnap(toolbarState, BBTexts.get("beatblock.timeline.snap") + "##tlMoreSnap", true);
		renderBeatSnap(toolbarState, BBTexts.get("beatblock.timeline.beat_snap") + "##tlMoreBeatSnap", true);
		renderBeatGrid(toolbarState, BBTexts.get("beatblock.timeline.beat_grid") + "##tlMoreBeatGrid", true);
		renderMagnet(toolbarState, BBTexts.get("beatblock.timeline.magnet") + "##tlMoreMagnet", true);
		renderLoop(toolbarState, BBTexts.get("beatblock.timeline.loop") + "##tlMoreLoop", true);
	}

	private static void renderSnap(TimelineToolbarState toolbarState, String label, boolean blockLayout) {
		boolean snap = toolbarState.isSnapToGrid();
		if (ImGui.checkbox(label, snap)) toolbarState.setSnapToGrid(!snap);
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.snap.tooltip"));
		if (blockLayout) return;
	}

	private static void renderBeatSnap(TimelineToolbarState toolbarState, String label, boolean blockLayout) {
		boolean beatSnap = toolbarState.isSnapToBeat();
		if (ImGui.checkbox(label, beatSnap)) toolbarState.setSnapToBeat(!beatSnap);
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.beat_snap.tooltip"));
		if (blockLayout) return;
	}

	private static void renderBeatGrid(TimelineToolbarState toolbarState, String label, boolean blockLayout) {
		boolean beatGrid = toolbarState.isBeatGridVisible();
		if (ImGui.checkbox(label, beatGrid)) toolbarState.setBeatGridVisible(!beatGrid);
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.beat_grid.tooltip"));
		if (blockLayout) return;
	}

	private static void renderMagnet(TimelineToolbarState toolbarState, String label, boolean blockLayout) {
		boolean magnet = toolbarState.isMagnetSnap();
		if (ImGui.checkbox(label, magnet)) toolbarState.setMagnetSnap(!magnet);
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.magnet.tooltip"));
		if (blockLayout) return;
	}

	private static void renderLoop(TimelineToolbarState toolbarState, String label, boolean blockLayout) {
		boolean loop = toolbarState.isLoop();
		if (ImGui.checkbox(label, loop)) toolbarState.setLoop(!loop);
		if (ImGui.isItemHovered()) ImGui.setTooltip(BBTexts.get("beatblock.timeline.loop.tooltip"));
		if (blockLayout) return;
	}
}
