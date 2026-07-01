package com.beatblock.timeline.interaction;

import com.beatblock.BeatBlockClient;
import com.beatblock.client.camera.CameraKeyframeActions;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.command.layer.CreateBuildLayerTrackCommand;
import com.beatblock.timeline.command.layer.DeleteBuildLayerTrackCommand;
import com.beatblock.timeline.layer.BuildLayerTrackSupport;
import com.beatblock.timeline.camera.CameraPathMetadata;
import com.beatblock.timeline.camera.CameraSegmentKind;
import com.beatblock.timeline.camera.CameraTrackFactory;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineClock;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import com.beatblock.ui.TimelinePanelVisibility;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.util.UiNumberFormatter;
import imgui.ImGui;

import static com.beatblock.timeline.interaction.TimelineInteractionConstants.POPUP_DELETE_CONFIRM;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.POPUP_EVENT_CONTEXT;
import static com.beatblock.timeline.interaction.TimelineInteractionConstants.POPUP_MARKER_CONTEXT;

/** 时间线 ImGui 右键菜单与删除/标记弹窗。 */
public final class TimelineInteractionPopups {

	private TimelineInteractionPopups() {}

	public static void renderMarkerOnly(
		Timeline timeline,
		TimelineClock clock,
		TimelineInteractionPopupHost host
	) {
		renderMarkerContextPopup(timeline, clock, host);
	}

	public static void renderAll(
		Timeline timeline,
		SelectionState selectionState,
		TimelineTrackListState trackListState,
		TimelineClock clock,
		TimelineInteractionPopupHost host
	) {
		renderContextMenu(timeline, selectionState, trackListState, host);
		renderMarkerContextPopup(timeline, clock, host);
		renderDeleteConfirmPopup(timeline, selectionState, trackListState, host);
	}

	public static void openPropertiesPanel(
		Timeline timeline,
		SelectionState selectionState,
		TimelineTrackListState trackListState,
		TimelineInteractionPopupHost host
	) {
		TimelineSelectionRef target = host.resolvePropertiesSelection(timeline, selectionState);
		if (target == null) {
			return;
		}
		if (TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, target.track().getId())) {
			return;
		}
		TimelinePanelVisibility.openTimelineProperties();
		ImGui.closeCurrentPopup();
	}

	private static void renderContextMenu(
		Timeline timeline,
		SelectionState selectionState,
		TimelineTrackListState trackListState,
		TimelineInteractionPopupHost host
	) {
		if (!ImGui.beginPopup(POPUP_EVENT_CONTEXT)) {
			return;
		}
		TimelineInteractionPopupState state = host.popupState();
		boolean requestDeleteConfirmPopup = false;
		boolean hasSelection = selectionState != null
			&& (!selectionState.getSelectedEvents().isEmpty() || !selectionState.getSelectedClips().isEmpty());
		boolean canDeleteSelection = TimelineInteractionDeleteSupport.hasDeletableSelection(timeline, selectionState, trackListState);
		boolean canDeleteContextClip = host.canDeleteContextClip(timeline, trackListState);
		BeatBlockClient.LOGGER.info("[TimelineInteraction.renderContextMenu] Menu opened: contextClipId={}, contextTrackId={}, canDeleteSelection={}, canDeleteContextClip={}", state.contextClipId, state.contextTrackId, canDeleteSelection, canDeleteContextClip);
		boolean canDeleteAny = canDeleteSelection || canDeleteContextClip;
		boolean hasClipboard = !host.clipboardEvents().isEmpty();
		TimelineSelectionRef propertiesTarget = host.resolvePropertiesSelection(timeline, selectionState);
		boolean canOpenProperties = propertiesTarget != null
			&& !TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, propertiesTarget.track().getId());

		if (ImGui.menuItem(BBTexts.get("beatblock.common.copy"), "Ctrl+C", false, hasSelection)) {
			host.copySelectedEvents(timeline, selectionState);
		}
		if (ImGui.menuItem(BBTexts.get("beatblock.common.paste"), "Ctrl+V", false, hasClipboard)) {
			host.pasteClipboardEvents(timeline, selectionState, state.contextTimeSeconds, trackListState);
		}
		renderBuildLayerTrackMenuItems(timeline, state, host);
		if (timeline != null && Timeline.TRACK_ID_CAMERA.equals(state.contextTrackId)
			&& !TimelineInteractiveTrackSlots.isTrackLocked(timeline, trackListState, state.contextTrackId)) {
			if (state.contextClipId != null) {
				if (ImGui.checkbox(BBTexts.get("beatblock.camera.show_path") + "##camCtxPathVis", state.contextCameraShowPath)) {
					CameraPathMetadata.setPathVisible(timeline, state.contextClipId, state.contextCameraShowPath.get());
				}
			}
			TimelineEventRef ctxEv = state.contextEventId != null
				? TimelineEventRefs.find(timeline, state.contextEventId) : null;
			if (ctxEv != null && ctxEv.event() != null && ctxEv.event().getType() == EventType.CAMERA_KEYFRAME) {
				if (ImGui.menuItem(BBTexts.get("beatblock.camera.delete_keyframe") + "##camDelKf")) {
					TimelineOperations.removeEvent(ctxEv.clip(), ctxEv.event().getId());
					if (selectionState != null) {
						selectionState.deselectEvent(ctxEv.event().getId());
					}
					state.contextEventId = null;
					ImGui.closeCurrentPopup();
				}
			}
			boolean canAddPathKf = false;
			if (state.contextClipId != null) {
				Track camT = timeline.getTrack(Timeline.TRACK_ID_CAMERA);
				Clip ctxClip = camT != null ? camT.getClip(state.contextClipId) : null;
				if (ctxClip != null) {
					TimelineEvent seg = CameraTrackFactory.findSegmentHeadEvent(ctxClip);
					CameraSegmentKind k = seg != null
						? CameraSegmentKind.fromParam(seg.getParameters().get("kind"))
						: CameraSegmentKind.PATH;
					canAddPathKf = k == CameraSegmentKind.PATH;
				}
			}
			if (ImGui.menuItem(BBTexts.get("beatblock.timeline.interaction.add_path_keyframe") + "##camAddKfCtx", null, false, canAddPathKf)) {
				CameraKeyframeActions.addKeyframeAtTime(timeline, state.contextTimeSeconds);
			}
			if (ImGui.beginMenu(BBTexts.get("beatblock.timeline.interaction.add_segment_menu"))) {
				double[] a = TimelineContentHitTest.readCameraAnchorFive();
				if (ImGui.menuItem(BBTexts.get("beatblock.timeline.interaction.custom_path"))) {
					CameraTrackFactory.addPathSegment(timeline, state.contextTimeSeconds, a[0], a[1], a[2], a[3], a[4]);
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.timeline.interaction.dolly"))) {
					CameraTrackFactory.addDollySegment(timeline, state.contextTimeSeconds, a[0], a[1], a[2], a[3], 8.0);
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.timeline.interaction.orbit"))) {
					double[] o = TimelineContentHitTest.readOrbitParamsFromView();
					CameraTrackFactory.addOrbitSegment(timeline, state.contextTimeSeconds,
						o[0], o[1], o[2], o[3], o[4], o[5], o[6]);
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.timeline.interaction.crane"))) {
					CameraTrackFactory.addCraneSegment(timeline, state.contextTimeSeconds, a[0], a[1], a[2], a[3], a[4], 6.0);
				}
				if (ImGui.menuItem(BBTexts.get("beatblock.timeline.interaction.shake"))) {
					CameraTrackFactory.addShakeSegment(timeline, state.contextTimeSeconds, a[0], a[1], a[2], a[3], a[4]);
				}
				ImGui.endMenu();
			}
		}
		String deleteLabel = canDeleteAny
			? BBTexts.get("beatblock.timeline.interaction.delete")
			: BBTexts.get("beatblock.timeline.interaction.delete_locked");
		if (ImGui.menuItem(deleteLabel, "Del", false, canDeleteAny)) {
			if (selectionState != null && canDeleteContextClip && state.contextClipId != null) {
				selectionState.clearEvents();
				selectionState.clearClips();
				selectionState.selectClip(state.contextClipId);
			} else if (selectionState != null && !hasSelection && state.contextClipId != null) {
				selectionState.clearEvents();
				selectionState.clearClips();
				selectionState.selectClip(state.contextClipId);
			}
			requestDeleteConfirmPopup = true;
			ImGui.closeCurrentPopup();
		}
		ImGui.separator();
		String propertiesLabel = propertiesTarget != null && !canOpenProperties
			? BBTexts.get("beatblock.timeline.interaction.properties_locked")
			: BBTexts.get("beatblock.timeline.interaction.properties");
		if (ImGui.menuItem(propertiesLabel, null, false, canOpenProperties)) {
			openPropertiesPanel(timeline, selectionState, trackListState, host);
		}
		ImGui.endPopup();
		if (requestDeleteConfirmPopup) {
			ImGui.openPopup(POPUP_DELETE_CONFIRM);
		}
	}

	private static void renderBuildLayerTrackMenuItems(
		Timeline timeline,
		TimelineInteractionPopupState state,
		TimelineInteractionPopupHost host
	) {
		if (timeline == null || host.timelineEditor() == null) {
			return;
		}
		var commandManager = host.timelineEditor().getCommandManager();

		String contextTrackId = state.contextTrackId;
		boolean onBuildLayerTrack = BuildLayerTrackSupport.isBuildLayerTrackId(contextTrackId);

		boolean canCreate = BuildLayerTrackSupport.canCreateMoreTracks(timeline);
		boolean canDeleteEmpty = false;
		if (onBuildLayerTrack) {
			Track track = timeline.getTrack(contextTrackId);
			canDeleteEmpty = track != null
				&& track.getClips().isEmpty()
				&& BuildLayerTrackSupport.listTracks(timeline).size() > 1;
		}

		if (!canCreate && !canDeleteEmpty) {
			return;
		}

		ImGui.separator();
		if (canCreate
			&& ImGui.menuItem(BBTexts.get("beatblock.timeline.interaction.create_build_layer_track"))) {
			commandManager.execute(new CreateBuildLayerTrackCommand(timeline));
			ImGui.closeCurrentPopup();
		}
		if (canDeleteEmpty
			&& ImGui.menuItem(BBTexts.get("beatblock.timeline.interaction.delete_build_layer_track"))) {
			commandManager.execute(new DeleteBuildLayerTrackCommand(timeline, contextTrackId));
			ImGui.closeCurrentPopup();
		}
	}

	private static void renderDeleteConfirmPopup(
		Timeline timeline,
		SelectionState selectionState,
		TimelineTrackListState trackListState,
		TimelineInteractionPopupHost host
	) {
		if (!ImGui.beginPopupModal(POPUP_DELETE_CONFIRM)) {
			return;
		}
		int selectedEventCount = selectionState != null ? selectionState.getSelectedEvents().size() : 0;
		int selectedClipCount = selectionState != null ? selectionState.getSelectedClips().size() : 0;
		boolean hasDeletable = TimelineInteractionDeleteSupport.hasDeletableSelection(timeline, selectionState, trackListState);
		boolean canDeleteCtxClip = host.canDeleteContextClip(timeline, trackListState);
		boolean canDelete = hasDeletable || canDeleteCtxClip;

		ImGui.text(BBTexts.get("beatblock.timeline.interaction.delete_confirm_title"));
		ImGui.separator();
		ImGui.textWrapped(BBTexts.get("beatblock.timeline.interaction.delete_confirm_body",
			selectedClipCount, selectedEventCount));

		if (containsSelectedAudioTrackClip(timeline, selectionState)) {
			ImGui.spacing();
			ImGui.textColored(1f, 0.45f, 0.45f, 1f,
				BBTexts.get("beatblock.timeline.interaction.delete_audio_warning"));
		}

		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.common.confirm_delete") + "##timelineDeleteConfirm", 150f, 0f)) {
			if (canDelete) {
				host.deleteSelectedEntries(timeline, selectionState, trackListState);
			}
			ImGui.closeCurrentPopup();
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.common.cancel") + "##timelineDeleteCancel", 120f, 0f)) {
			ImGui.closeCurrentPopup();
		}
		ImGui.endPopup();
	}

	private static void renderMarkerContextPopup(
		Timeline timeline,
		TimelineClock clock,
		TimelineInteractionPopupHost host
	) {
		if (!ImGui.beginPopup(POPUP_MARKER_CONTEXT)) {
			return;
		}
		TimelineInteractionPopupState state = host.popupState();
		int markerIndex = timeline != null ? timeline.findMarkerIndexById(state.contextMarkerId) : -1;
		if (timeline == null || markerIndex < 0 || markerIndex >= timeline.getMarkers().size()) {
			state.contextMarkerId = null;
			ImGui.textDisabled(BBTexts.get("beatblock.timeline.interaction.marker_gone"));
			if (ImGui.button(BBTexts.get("beatblock.common.close") + "##markerPopupClose")) {
				ImGui.closeCurrentPopup();
			}
			ImGui.endPopup();
			return;
		}

		TimelineMarker marker = timeline.getMarkers().get(markerIndex);
		ImGui.text(BBTexts.get("beatblock.timeline.interaction.marker"));
		ImGui.textDisabled(UiNumberFormatter.format(marker.getTimeSeconds()) + "s");
		ImGui.setNextItemWidth(180f);
		ImGui.inputText(BBTexts.get("beatblock.marker.name") + "##markerRename", state.markerNameBuffer);

		if (ImGui.button(BBTexts.get("beatblock.timeline.interaction.jump") + "##markerJump")) {
			if (clock != null) {
				host.seekClockAndMusic(clock, marker.getTimeSeconds());
			}
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.timeline.interaction.rename") + "##markerApply")) {
			String newName = state.markerNameBuffer.get() == null ? "" : state.markerNameBuffer.get().trim();
			timeline.updateMarker(state.contextMarkerId, marker.getTimeSeconds(), newName);
			state.contextMarkerId = null;
			ImGui.closeCurrentPopup();
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.common.delete") + "##markerDelete")) {
			timeline.removeMarker(state.contextMarkerId);
			state.contextMarkerId = null;
			ImGui.closeCurrentPopup();
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.common.close") + "##markerClose")) {
			state.contextMarkerId = null;
			ImGui.closeCurrentPopup();
		}
		ImGui.endPopup();
	}

	private static boolean containsSelectedAudioTrackClip(Timeline timeline, SelectionState selectionState) {
		if (timeline == null || selectionState == null || selectionState.getSelectedClips().isEmpty()) {
			return false;
		}
		Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		if (audioTrack == null) {
			return false;
		}
		for (String clipId : selectionState.getSelectedClips()) {
			if (clipId != null && audioTrack.getClip(clipId) != null) {
				return true;
			}
		}
		return false;
	}
}
