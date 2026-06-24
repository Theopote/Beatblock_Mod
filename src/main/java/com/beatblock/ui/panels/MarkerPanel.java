package com.beatblock.ui.panels;

import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.presenter.MarkerPanelPresenter;
import com.beatblock.ui.presenter.PresenterFactories;
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

	private final MarkerPanelPresenter presenter;
	private String selectedMarkerId;
	private final ImString markerNameBuffer = new ImString(128);
	private final ImString markerTimeBuffer = new ImString(32);
	private final ImInt markerTypeIndex = new ImInt(0);

	private static final String[] MARKER_TYPE_LABELS = MarkerType.displayNames();

	public MarkerPanel() {
		this(PresenterFactories.markerPanelPresenter());
	}

	MarkerPanel(MarkerPanelPresenter presenter) {
		this.presenter = presenter;
	}

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

		BeatBlockClientDriver.TimelineActionExecutionReport report = presenter.lastActionExecutionReport();
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
		Timeline timeline = presenter.currentTimeline();

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

		if (selectedMarkerId != null && !presenter.markerExists(timeline, selectedMarkerId)) {
			selectedMarkerId = null;
		}

		if (ImGui.beginChild("##MarkerList", 0, 110, true)) {
			for (MarkerPanelPresenter.MarkerListItem item : presenter.listMarkers(timeline)) {
				boolean selected = item.id().equals(selectedMarkerId);
				int abgr = item.colorAbgr();
				ImGui.pushStyleColor(ImGuiCol.Text, abgrToR(abgr), abgrToG(abgr), abgrToB(abgr), abgrToA(abgr));
				if (ImGui.selectable(item.listLabel() + "##" + item.id(), selected)) {
					selectedMarkerId = item.id();
					applyFormSnapshot(presenter.formSnapshotFor(presenter.findMarker(timeline, item.id())));
				}
				ImGui.popStyleColor();
			}
		}
		ImGui.endChild();

		TimelineMarker marker = presenter.findMarker(timeline, selectedMarkerId);
		if (marker == null) return;
		ImGui.textDisabled("选中 Marker");
		ImGui.setNextItemWidth(-1);
		ImGui.inputText("名称##markerName", markerNameBuffer);
		ImGui.setNextItemWidth(-1);
		ImGui.inputText("时间(秒)##markerTime", markerTimeBuffer);
		markerTypeIndex.set(MarkerPanelPresenter.clampTypeIndex(marker.getType().ordinal()));
		if (ImGui.combo("类型##markerType", markerTypeIndex, MARKER_TYPE_LABELS)) {
			submitMarkerEdits(timeline, selectedMarkerId);
		}

		if (ImGui.button("Jump##toolMarkerJump")) {
			presenter.jumpToMarker(marker);
		}
		ImGui.sameLine();
		if (ImGui.button("Apply##toolMarkerApply")) {
			submitMarkerEdits(timeline, selectedMarkerId);
		}
		ImGui.sameLine();
		if (ImGui.button("Delete##toolMarkerDelete")) {
			if (presenter.deleteMarker(timeline, selectedMarkerId).ok()) {
				selectedMarkerId = null;
			}
		}

		ImGui.spacing();
		ImGui.textDisabled("循环区");
		if (ImGui.button("Set In##toolMarkerSetIn")) {
			presenter.setLoopInFromMarker(marker);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("将当前 Marker 设为循环起点");
		}
		ImGui.sameLine();
		if (ImGui.button("Set Out##toolMarkerSetOut")) {
			presenter.setLoopOutFromMarker(marker);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip("将当前 Marker 设为循环终点");
		}

		MarkerPanelPresenter.MarkerNeighbors neighbors = presenter.neighborsOf(timeline, selectedMarkerId);

		if (ImGui.button("Prev->This##toolMarkerLoopPrev", 0, 0)) {
			presenter.applyLoopRangeBetween(neighbors.previous(), marker);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(neighbors.previous() != null
				? "将上一个 Marker 到当前 Marker 设为循环区"
				: "没有上一个 Marker");
		}
		ImGui.sameLine();
		if (ImGui.button("This->Next##toolMarkerLoopNext", 0, 0)) {
			presenter.applyLoopRangeBetween(marker, neighbors.next());
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(neighbors.next() != null
				? "将当前 Marker 到下一个 Marker 设为循环区"
				: "没有下一个 Marker");
		}
	}

	private void submitMarkerEdits(Timeline timeline, String markerId) {
		var outcome = presenter.applyMarkerEdit(
			timeline,
			markerId,
			markerNameBuffer.get(),
			markerTimeBuffer.get(),
			markerTypeIndex.get()
		);
		if (outcome.formSnapshot() != null) {
			applyFormSnapshot(outcome.formSnapshot());
		}
	}

	private void applyFormSnapshot(MarkerPanelPresenter.MarkerFormSnapshot snapshot) {
		markerNameBuffer.set(snapshot.name());
		markerTimeBuffer.set(snapshot.timeText());
		markerTypeIndex.set(snapshot.typeIndex());
	}

	private static float abgrToR(int abgr) { return ((abgr) & 0xFF) / 255f; }
	private static float abgrToG(int abgr) { return ((abgr >> 8) & 0xFF) / 255f; }
	private static float abgrToB(int abgr) { return ((abgr >> 16) & 0xFF) / 255f; }
	private static float abgrToA(int abgr) { return ((abgr >> 24) & 0xFF) / 255f; }
}
