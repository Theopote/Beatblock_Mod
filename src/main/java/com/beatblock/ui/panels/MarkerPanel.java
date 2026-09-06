package com.beatblock.ui.panels;

import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.timeline.MarkerEditState;
import com.beatblock.timeline.MarkerOrigin;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.marker.MarkerFocusRequest;
import com.beatblock.ui.i18n.BBTexts;
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

/**
 * 标记与调试面板：时间线 Marker 的列表、编辑、跳转、循环区设置；以及时间线动作执行状态调试信息。
 * 从 ToolPanel 拆分而来，独占左侧下方停靠槽。
 */
public class MarkerPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private static final String POPUP_STRUCTURAL_DELETE = "##MarkerStructuralDelete";
	private static final String POPUP_STRUCTURAL_TYPE = "##MarkerStructuralType";

	private final MarkerPanelPresenter presenter;
	private String selectedMarkerId;
	private final ImString markerNameBuffer = new ImString(128);
	private final ImString markerTimeBuffer = new ImString(32);
	private final ImInt markerTypeIndex = new ImInt(0);
	private int pendingTypeIndex = -1;
	private boolean focusNameField;

	private static final String[] MARKER_TYPE_LABELS = MarkerType.displayNames();

	public MarkerPanel() {
		this(PresenterFactories.markerPanelPresenter());
	}

	MarkerPanel(MarkerPanelPresenter presenter) {
		this.presenter = presenter;
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.markerPanelWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.markerPanelWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			renderLastActionExecutionStatus();
			renderMarkerManager();
			renderStructuralConfirmPopups();
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.markerPanelWindow());
		}
	}

	private void renderLastActionExecutionStatus() {
		ImGui.text(BBTexts.get("beatblock.marker.timeline_actions"));
		ImGui.separator();

		BeatBlockClientDriver.TimelineActionExecutionReport report = presenter.lastActionExecutionReport();
		if (report == null) {
			ImGui.textDisabled(BBTexts.get("beatblock.marker.no_execution_record"));
		} else {
			long ageMs = Math.max(0L, System.currentTimeMillis() - report.timestampMs());
			ImGui.textDisabled(BBTexts.get("beatblock.marker.execution_summary",
				report.actionMode().name(),
				report.status(),
				report.mutationCount(),
				ageMs));
			if (report.detail() != null && !report.detail().isBlank()) {
				ImGui.textWrapped(BBTexts.get("beatblock.marker.detail", report.detail()));
			}
			if (report.targetObjectId() != null && !report.targetObjectId().isBlank()) {
				ImGui.textDisabled(BBTexts.get("beatblock.marker.target", report.targetObjectId()));
			}
			if (report.eventId() != null && !report.eventId().isBlank()) {
				ImGui.textDisabled(BBTexts.get("beatblock.marker.event_id", report.eventId()));
			}
		}
	}

	private void renderMarkerManager() {
		Timeline timeline = presenter.currentTimeline();

		ImGui.spacing();
		ImGui.text(BBTexts.get("beatblock.marker.timeline_markers"));
		ImGui.separator();

		if (timeline == null) {
			ImGui.textDisabled(BBTexts.get("beatblock.marker.no_timeline"));
			return;
		}

		renderCreateRow();
		consumeFocusRequest(timeline);

		if (timeline.getMarkers().isEmpty()) {
			selectedMarkerId = null;
			ImGui.textDisabled(BBTexts.get("beatblock.marker.no_markers_hint"));
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
		ImGui.textDisabled(BBTexts.get("beatblock.marker.selected"));
		ImGui.textDisabled(BBTexts.get(
			"beatblock.marker.provenance",
			originLabel(marker.getOrigin()),
			editStateLabel(marker.getEditState())
		));

		boolean locked = marker.getEditState() == MarkerEditState.LOCKED;
		if (locked) {
			ImGui.textColored(0.95f, 0.55f, 0.35f, 1f, BBTexts.get("beatblock.marker.locked_hint"));
		}

		if (ImGui.button(BBTexts.get("beatblock.common.jump") + "##toolMarkerJump")) {
			presenter.jumpToMarker(marker);
		}
		ImGui.sameLine();
		String lockLabel = locked
			? BBTexts.get("beatblock.marker.unlock")
			: BBTexts.get("beatblock.marker.lock");
		if (ImGui.button(lockLabel + "##toolMarkerLock")) {
			presenter.setMarkerLocked(timeline, selectedMarkerId, !locked);
			TimelineMarker refreshed = presenter.findMarker(timeline, selectedMarkerId);
			if (refreshed != null) {
				applyFormSnapshot(presenter.formSnapshotFor(refreshed));
			}
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(locked
				? BBTexts.get("beatblock.marker.unlock.tooltip")
				: BBTexts.get("beatblock.marker.lock.tooltip"));
		}

		if (locked) {
			ImGui.beginDisabled();
		}
		if (focusNameField) {
			ImGui.setKeyboardFocusHere();
			focusNameField = false;
		}
		ImGui.setNextItemWidth(-1);
		ImGui.inputText(BBTexts.get("beatblock.marker.name") + "##markerName", markerNameBuffer);
		ImGui.setNextItemWidth(-1);
		ImGui.inputText(BBTexts.get("beatblock.marker.time_seconds") + "##markerTime", markerTimeBuffer);
		markerTypeIndex.set(MarkerPanelPresenter.clampTypeIndex(marker.getType().ordinal()));
		if (ImGui.combo(BBTexts.get("beatblock.marker.type") + "##markerType", markerTypeIndex, MARKER_TYPE_LABELS)) {
			if (presenter.requiresTypeChangeConfirm(marker, markerTypeIndex.get())) {
				pendingTypeIndex = markerTypeIndex.get();
				ImGui.openPopup(POPUP_STRUCTURAL_TYPE);
				markerTypeIndex.set(marker.getType().ordinal());
			} else {
				submitMarkerEdits(timeline, selectedMarkerId, false);
			}
		}

		if (ImGui.button(BBTexts.get("beatblock.common.apply") + "##toolMarkerApply")) {
			submitMarkerEdits(timeline, selectedMarkerId, false);
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.common.delete") + "##toolMarkerDelete")) {
			if (presenter.requiresDeleteConfirm(marker)) {
				ImGui.openPopup(POPUP_STRUCTURAL_DELETE);
			} else if (presenter.deleteMarker(timeline, selectedMarkerId, false).ok()) {
				selectedMarkerId = null;
			}
		}
		if (locked) {
			ImGui.endDisabled();
		}

		ImGui.spacing();
		ImGui.textDisabled(BBTexts.get("beatblock.marker.loop_region"));
		if (ImGui.button(BBTexts.get("beatblock.marker.set_in") + "##toolMarkerSetIn")) {
			presenter.setLoopInFromMarker(marker);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.marker.set_in.tooltip"));
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.marker.set_out") + "##toolMarkerSetOut")) {
			presenter.setLoopOutFromMarker(marker);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.marker.set_out.tooltip"));
		}

		MarkerPanelPresenter.MarkerNeighbors neighbors = presenter.neighborsOf(timeline, selectedMarkerId);

		if (ImGui.button(BBTexts.get("beatblock.marker.loop_prev") + "##toolMarkerLoopPrev", 0, 0)) {
			presenter.applyLoopRangeBetween(neighbors.previous(), marker);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(neighbors.previous() != null
				? BBTexts.get("beatblock.marker.loop_prev.tooltip")
				: BBTexts.get("beatblock.marker.loop_prev.none"));
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.marker.loop_next") + "##toolMarkerLoopNext", 0, 0)) {
			presenter.applyLoopRangeBetween(marker, neighbors.next());
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(neighbors.next() != null
				? BBTexts.get("beatblock.marker.loop_next.tooltip")
				: BBTexts.get("beatblock.marker.loop_next.none"));
		}
	}

	private void consumeFocusRequest(Timeline timeline) {
		String pendingId = MarkerFocusRequest.peekMarkerId();
		if (pendingId == null || timeline == null || !presenter.markerExists(timeline, pendingId)) {
			return;
		}
		selectedMarkerId = pendingId;
		TimelineMarker marker = presenter.findMarker(timeline, pendingId);
		if (marker != null) {
			applyFormSnapshot(presenter.formSnapshotFor(marker));
		}
		if (MarkerFocusRequest.consumeFocusName()) {
			focusNameField = true;
		}
		MarkerFocusRequest.clear();
	}

	private void renderCreateRow() {
		if (ImGui.button(BBTexts.get("beatblock.marker.insert_playhead") + "##markerInsertPlayhead")) {
			presenter.insertAtPlayhead(MarkerType.GENERIC, null);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.marker.insert_playhead.tooltip"));
		}
		ImGui.sameLine();
		ImGui.textDisabled(BBTexts.get("beatblock.marker.insert_playhead.hint"));
	}

	private void renderStructuralConfirmPopups() {
		Timeline timeline = presenter.currentTimeline();
		TimelineMarker marker = presenter.findMarker(timeline, selectedMarkerId);

		if (ImGui.beginPopupModal(POPUP_STRUCTURAL_DELETE)) {
			ImGui.textWrapped(BBTexts.get("beatblock.marker.structural_delete_body"));
			if (ImGui.button(BBTexts.get("beatblock.common.confirm_delete") + "##markerStructDelOk", 140, 0)) {
				if (timeline != null && selectedMarkerId != null
					&& presenter.deleteMarker(timeline, selectedMarkerId, true).ok()) {
					selectedMarkerId = null;
				}
				ImGui.closeCurrentPopup();
			}
			ImGui.sameLine();
			if (ImGui.button(BBTexts.get("beatblock.common.cancel") + "##markerStructDelCancel", 120, 0)) {
				ImGui.closeCurrentPopup();
			}
			ImGui.endPopup();
		}

		if (ImGui.beginPopupModal(POPUP_STRUCTURAL_TYPE)) {
			ImGui.textWrapped(BBTexts.get("beatblock.marker.structural_type_body"));
			if (ImGui.button(BBTexts.get("beatblock.common.confirm") + "##markerStructTypeOk", 140, 0)) {
				if (timeline != null && selectedMarkerId != null && pendingTypeIndex >= 0) {
					markerTypeIndex.set(pendingTypeIndex);
					submitMarkerEdits(timeline, selectedMarkerId, true);
				}
				pendingTypeIndex = -1;
				ImGui.closeCurrentPopup();
			}
			ImGui.sameLine();
			if (ImGui.button(BBTexts.get("beatblock.common.cancel") + "##markerStructTypeCancel", 120, 0)) {
				if (marker != null) {
					markerTypeIndex.set(marker.getType().ordinal());
				}
				pendingTypeIndex = -1;
				ImGui.closeCurrentPopup();
			}
			ImGui.endPopup();
		}
	}

	private void submitMarkerEdits(Timeline timeline, String markerId, boolean structuralConfirmed) {
		var outcome = presenter.applyMarkerEdit(
			timeline,
			markerId,
			markerNameBuffer.get(),
			markerTimeBuffer.get(),
			markerTypeIndex.get(),
			structuralConfirmed
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

	private static String originLabel(MarkerOrigin origin) {
		return BBTexts.get(switch (origin) {
			case MANUAL -> "beatblock.marker.origin.manual";
			case AUDIO_ANALYSIS -> "beatblock.marker.origin.audio_analysis";
			case GENERATED -> "beatblock.marker.origin.generated";
			case IMPORTED -> "beatblock.marker.origin.imported";
		});
	}

	private static String editStateLabel(MarkerEditState state) {
		return BBTexts.get(switch (state) {
			case GENERATED -> "beatblock.marker.edit_state.generated";
			case USER_EDITED -> "beatblock.marker.edit_state.user_edited";
			case LOCKED -> "beatblock.marker.edit_state.locked";
		});
	}

	private static float abgrToR(int abgr) { return ((abgr) & 0xFF) / 255f; }
	private static float abgrToG(int abgr) { return ((abgr >> 8) & 0xFF) / 255f; }
	private static float abgrToB(int abgr) { return ((abgr >> 16) & 0xFF) / 255f; }
	private static float abgrToA(int abgr) { return ((abgr >> 24) & 0xFF) / 255f; }
}
