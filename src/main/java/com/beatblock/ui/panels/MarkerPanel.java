package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.Locale;

/**
 * 标记与调试面板：时间线 Marker 的列表、编辑、跳转、循环区设置；以及时间线动作执行状态调试信息。
 * 从 ToolPanel 拆分而来，独占左侧下方停靠槽。
 */
public class MarkerPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	private String selectedMarkerId;
	private final ImString markerNameBuffer = new ImString(128);
	private final ImString markerTimeBuffer = new ImString(32);
	private final ImInt markerTypeIndex = new ImInt(0);

	private static final String[] MARKER_TYPE_LABELS = MarkerType.displayNames();

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.MARKER_PANEL_WINDOW);
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.MARKER_PANEL_WINDOW, pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			renderLastActionExecutionStatus();
			renderMarkerManager();
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.MARKER_PANEL_WINDOW);
		}
	}

	private void renderLastActionExecutionStatus() {
		ImGui.text("时间线动作（调试）");
		ImGui.separator();

		BeatBlockClientDriver.TimelineActionExecutionReport report = BeatBlockClientDriver.getLastTimelineActionExecutionReport();
		if (report == null) {
			ImGui.textDisabled("暂无执行记录。");
		} else {
			long ageMs = Math.max(0L, System.currentTimeMillis() - report.timestampMs());
			ImGui.textDisabled(String.format(Locale.ROOT,
				"%s | %s | mutations=%d | %dms ago",
				report.actionMode().name(),
				report.status(),
				report.mutationCount(),
				ageMs));
			if (report.detail() != null && !report.detail().isBlank()) {
				ImGui.textWrapped("detail: " + report.detail());
			}
			if (report.targetObjectId() != null && !report.targetObjectId().isBlank()) {
				ImGui.textDisabled("target: " + report.targetObjectId());
			}
			if (report.eventId() != null && !report.eventId().isBlank()) {
				ImGui.textDisabled("event: " + report.eventId());
			}
		}
	}

	private void renderMarkerManager() {
		Timeline timeline = BeatBlock.timeline;

		ImGui.spacing();
		ImGui.text("时间线 Marker");
		ImGui.separator();

		if (timeline == null) {
			ImGui.textDisabled("当前无时间线。");
			return;
		}

		if (timeline.getMarkers().isEmpty()) {
			selectedMarkerId = null;
			ImGui.textDisabled("暂无 Marker。可在工具条创建，或双击标尺空白处创建。");
			return;
		}

		if (selectedMarkerId != null && timeline.findMarkerIndexById(selectedMarkerId) < 0) {
			selectedMarkerId = null;
		}

		if (ImGui.beginChild("##MarkerList", 0, 110, true)) {
			for (TimelineMarker marker : timeline.getMarkers()) {
				if (marker == null) continue;
				String markerId = marker.getId();
				boolean selected = markerId.equals(selectedMarkerId);
				String label = String.format(Locale.ROOT, "[%s] %.2fs  %s##%s",
					marker.getType().getDisplayName(),
					marker.getTimeSeconds(),
					marker.getName() == null || marker.getName().isBlank() ? "(unnamed)" : marker.getName(),
					markerId);
				int abgr = marker.getType().getColorAbgr();
				ImGui.pushStyleColor(ImGuiCol.Text, abgrToR(abgr), abgrToG(abgr), abgrToB(abgr), abgrToA(abgr));
				if (ImGui.selectable(label, selected)) {
					selectedMarkerId = markerId;
					markerNameBuffer.set(marker.getName());
					markerTimeBuffer.set(String.format(Locale.ROOT, "%.3f", marker.getTimeSeconds()));
					markerTypeIndex.set(marker.getType().ordinal());
				}
				ImGui.popStyleColor();
			}
		}
		ImGui.endChild();

		int markerIndex = selectedMarkerId != null ? timeline.findMarkerIndexById(selectedMarkerId) : -1;
		if (markerIndex < 0) return;

		TimelineMarker marker = timeline.getMarkers().get(markerIndex);
		ImGui.textDisabled("选中 Marker");
		ImGui.setNextItemWidth(-1);
		ImGui.inputText("名称##markerName", markerNameBuffer);
		ImGui.setNextItemWidth(-1);
		ImGui.inputText("时间(秒)##markerTime", markerTimeBuffer);
		markerTypeIndex.set(Math.max(0, Math.min(marker.getType().ordinal(), MARKER_TYPE_LABELS.length - 1)));
		if (ImGui.combo("类型##markerType", markerTypeIndex, MARKER_TYPE_LABELS)) {
			applyMarkerEdits(timeline, selectedMarkerId, marker);
		}

		if (ImGui.button("Jump##toolMarkerJump")) {
			jumpToMarker(marker);
		}
		ImGui.sameLine();
		if (ImGui.button("Apply##toolMarkerApply")) {
			applyMarkerEdits(timeline, selectedMarkerId, marker);
		}
		ImGui.sameLine();
		if (ImGui.button("Delete##toolMarkerDelete")) {
			timeline.removeMarker(selectedMarkerId);
			selectedMarkerId = null;
		}

		ImGui.spacing();
		ImGui.textDisabled("循环区");
		if (ImGui.button("Set In##toolMarkerSetIn")) {
			setLoopIn(marker.getTimeSeconds());
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("将当前 Marker 设为循环起点");
		}
		ImGui.sameLine();
		if (ImGui.button("Set Out##toolMarkerSetOut")) {
			setLoopOut(marker.getTimeSeconds());
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("将当前 Marker 设为循环终点");
		}

		TimelineMarker prevMarker = markerIndex > 0 ? timeline.getMarkers().get(markerIndex - 1) : null;
		TimelineMarker nextMarker = markerIndex + 1 < timeline.getMarkers().size() ? timeline.getMarkers().get(markerIndex + 1) : null;

		if (ImGui.button("Prev->This##toolMarkerLoopPrev", 0, 0)) {
			applyLoopRange(prevMarker, marker);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(prevMarker != null ? "将上一个 Marker 到当前 Marker 设为循环区" : "没有上一个 Marker");
		}
		ImGui.sameLine();
		if (ImGui.button("This->Next##toolMarkerLoopNext", 0, 0)) {
			applyLoopRange(marker, nextMarker);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(nextMarker != null ? "将当前 Marker 到下一个 Marker 设为循环区" : "没有下一个 Marker");
		}
	}

	private void jumpToMarker(TimelineMarker marker) {
		if (marker == null || BeatBlock.timelineEditor == null) return;
		double t = marker.getTimeSeconds();
		BeatBlock.timelineEditor.getClock().seek(t);
		if (BeatBlock.musicPlayer != null) {
			BeatBlock.musicPlayer.setCurrentTimeSeconds(t);
		}
	}

	private void applyMarkerEdits(Timeline timeline, String markerId, TimelineMarker marker) {
		if (timeline == null || markerId == null || marker == null) return;
		String name = markerNameBuffer.get() == null ? "" : markerNameBuffer.get().trim();
		double timeSeconds = marker.getTimeSeconds();
		MarkerType type = MarkerType.values()[Math.max(0, Math.min(markerTypeIndex.get(), MarkerType.values().length - 1))];
		try {
			String raw = markerTimeBuffer.get();
			if (raw != null && !raw.isBlank()) {
				timeSeconds = Math.max(0, Double.parseDouble(raw.trim()));
			}
		} catch (NumberFormatException ignored) {
			markerTimeBuffer.set(String.format(Locale.ROOT, "%.3f", marker.getTimeSeconds()));
			return;
		}
		timeline.updateMarker(markerId, timeSeconds, name, type);
		int newIndex = timeline.findMarkerIndexById(markerId);
		if (newIndex >= 0) {
			TimelineMarker updated = timeline.getMarkers().get(newIndex);
			markerNameBuffer.set(updated.getName());
			markerTimeBuffer.set(String.format(Locale.ROOT, "%.3f", updated.getTimeSeconds()));
			markerTypeIndex.set(updated.getType().ordinal());
		}
	}

	private void setLoopIn(double timeSeconds) {
		if (BeatBlock.timelineEditor == null) return;
		var toolbarState = BeatBlock.timelineEditor.getToolbarState();
		toolbarState.setLoopInSeconds(timeSeconds);
		if (toolbarState.getLoopOutSeconds() <= timeSeconds) {
			toolbarState.setLoopOutSeconds(timeSeconds + 0.1);
		}
		toolbarState.setLoop(true);
	}

	private void setLoopOut(double timeSeconds) {
		if (BeatBlock.timelineEditor == null) return;
		var toolbarState = BeatBlock.timelineEditor.getToolbarState();
		double loopIn = toolbarState.getLoopInSeconds();
		toolbarState.setLoopOutSeconds(Math.max(timeSeconds, loopIn + 0.1));
		toolbarState.setLoop(true);
	}

	private void applyLoopRange(TimelineMarker startMarker, TimelineMarker endMarker) {
		if (startMarker == null || endMarker == null || BeatBlock.timelineEditor == null) return;
		double start = Math.min(startMarker.getTimeSeconds(), endMarker.getTimeSeconds());
		double end = Math.max(startMarker.getTimeSeconds(), endMarker.getTimeSeconds());
		if (end <= start) return;
		var toolbarState = BeatBlock.timelineEditor.getToolbarState();
		toolbarState.setLoopInSeconds(start);
		toolbarState.setLoopOutSeconds(end);
		toolbarState.setLoop(true);
		BeatBlock.timelineEditor.getClock().seek(start);
		if (BeatBlock.musicPlayer != null) {
			BeatBlock.musicPlayer.setCurrentTimeSeconds(start);
		}
	}

	private static float abgrToR(int abgr) { return ((abgr) & 0xFF) / 255f; }
	private static float abgrToG(int abgr) { return ((abgr >> 8) & 0xFF) / 255f; }
	private static float abgrToB(int abgr) { return ((abgr >> 16) & 0xFF) / 255f; }
	private static float abgrToA(int abgr) { return ((abgr >> 24) & 0xFF) / 255f; }
}
