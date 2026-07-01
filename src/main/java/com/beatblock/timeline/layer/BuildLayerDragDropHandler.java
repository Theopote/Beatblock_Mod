package com.beatblock.timeline.layer;

import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.ReferenceBeatResolver;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.command.layer.BindLayerToTrackCommand;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.generation.PacingRequest;
import com.beatblock.timeline.generation.PacingStrategy;
import com.beatblock.timeline.interaction.DragController;
import com.beatblock.timeline.rendering.TimelineAudioDropHost;
import com.beatblock.timeline.rendering.TimelineLayout;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import com.beatblock.timeline.rendering.TimelineTrackListState;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.notification.ToastNotificationSystem;
import imgui.ImGui;
import imgui.flag.ImGuiDragDropFlags;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 建造图层拖入时间线建造轨道：校验、吸附、绑定命令与反馈。 */
public final class BuildLayerDragDropHandler {

	public static final String PAYLOAD_TYPE = "BB_BUILD_LAYER_ID";

	private BuildLayerDragDropHandler() {
	}

	public static void renderDropTarget(
		TimelineAudioDropHost host,
		int rowIndex,
		float rowHeight,
		Timeline timeline,
		TimelineLayout layout,
		TimelineViewState viewState,
		TimelineToolbarState toolbarState,
		InteractionState interactionState,
		SelectionState selectionState,
		String targetTrackId,
		TimelineTrackListState trackListState
	) {
		float screenY = layout.getRowScreenY(rowIndex);
		if (screenY < 0 || targetTrackId == null || targetTrackId.isBlank()) {
			return;
		}
		if (trackListState != null && trackListState.isLocked(rowIndex)) {
			return;
		}

		ImGui.setCursorScreenPos(layout.contentLeft, screenY);
		ImGui.invisibleButton("##BuildLayerDrop" + rowIndex, layout.contentWidth, rowHeight);
		if (!ImGui.beginDragDropTarget()) {
			return;
		}

		if (host != null) {
			host.setBuildLayerDropHighlightRow(rowIndex);
		}

		byte[] payload = ImGui.acceptDragDropPayload(
			PAYLOAD_TYPE,
			ImGuiDragDropFlags.AcceptBeforeDelivery | ImGuiDragDropFlags.AcceptNoDrawDefaultRect
		);
		if (payload != null) {
			handleDrop(
				host,
				timeline,
				layout,
				viewState,
				toolbarState,
				interactionState,
				selectionState,
				targetTrackId,
				decodeLayerId(payload)
			);
		}
		ImGui.endDragDropTarget();
	}

	public static void handleDrop(
		TimelineAudioDropHost host,
		Timeline timeline,
		TimelineLayout layout,
		TimelineViewState viewState,
		TimelineToolbarState toolbarState,
		InteractionState interactionState,
		SelectionState selectionState,
		String targetTrackId,
		String layerId
	) {
		BeatBlockContext context = host != null ? host.context() : null;
		if (context == null || context.blockAnimationEngine() == null || context.timelineEditor() == null) {
			ToastNotificationSystem.showError(BBTexts.get("beatblock.message.editor_unavailable"));
			return;
		}
		if (timeline == null || layerId == null || layerId.isBlank()) {
			return;
		}

		BuildLayerManager layerManager = context.blockAnimationEngine().getBuildLayerManager();
		BuildLayer layer = layerManager != null ? layerManager.get(layerId) : null;
		String validationError = validateLayerForBind(layer);
		if (validationError != null) {
			ToastNotificationSystem.showError(validationError);
			return;
		}

		double dropTime = viewState != null && layout != null
			? viewState.screenToTime(ImGui.getMousePosX() - layout.contentLeft)
			: 0.0;
		dropTime = Math.max(0.0, dropTime);
		dropTime = DragController.snapTime(
			dropTime,
			null,
			timeline,
			toolbarState,
			viewState,
			interactionState
		);

		double clipDuration = computeClipDuration(layer, timeline);
		var cmd = new BindLayerToTrackCommand(
			timeline,
			layerManager,
			layerId,
			targetTrackId,
			dropTime,
			clipDuration
		);
		TimelineEditor editor = context.timelineEditor();
		editor.getCommandManager().execute(cmd);
		if (!cmd.isApplied()) {
			ToastNotificationSystem.showError(BBTexts.get("beatblock.message.layer_bind_failed"));
			return;
		}

		if (selectionState != null) {
			selectionState.clearAll();
			if (cmd.getCreatedClipId() != null) {
				selectionState.selectClip(cmd.getCreatedClipId());
			}
			if (cmd.getCreatedEventId() != null) {
				selectionState.selectEvent(cmd.getCreatedEventId());
			}
		}
		editor.syncClockDuration();
		ToastNotificationSystem.showSuccess(
			BBTexts.get("beatblock.message.layer_bound_to_track", layer.getName(), formatTime(dropTime))
		);
	}

	public static String validateLayerForBind(BuildLayer layer) {
		if (layer == null) {
			return BBTexts.get("beatblock.message.layer_not_found");
		}
		if (layer.getState() == LayerVisibilityState.BOUND_TO_TRACK) {
			return BBTexts.get("beatblock.layer.already_bound");
		}
		if (!layer.canBindToTrack()) {
			return BBTexts.get("beatblock.layer.hide_first");
		}
		return null;
	}

	public static double computeClipDuration(BuildLayer layer, Timeline timeline) {
		if (layer == null || layer.getStageObject() == null) {
			return BindLayerToTrackCommand.DEFAULT_CLIP_DURATION_SECONDS;
		}
		int blockCount = layer.getStageObject().getBlocks().size();
		if (blockCount <= 0) {
			return BindLayerToTrackCommand.DEFAULT_CLIP_DURATION_SECONDS;
		}

		double bpm = timeline != null && timeline.getBpm() > 0 ? timeline.getBpm() : 120.0;
		double beatSec = 60.0 / bpm;
		if (timeline == null) {
			return Math.max(BindLayerToTrackCommand.DEFAULT_CLIP_DURATION_SECONDS, blockCount * beatSec * 0.5);
		}

		double[] beats = ReferenceBeatResolver.resolveBeatTimesSeconds(timeline);
		PacingRequest request = new PacingRequest(
			blockCount,
			0.0,
			true,
			beats,
			bpm,
			beatSec
		);
		List<Double> timestamps = PacingStrategy.beatGrid().computeTimestamps(request);
		if (timestamps == null || timestamps.isEmpty()) {
			return Math.max(BindLayerToTrackCommand.DEFAULT_CLIP_DURATION_SECONDS, blockCount * beatSec * 0.5);
		}
		double lastReveal = timestamps.getLast();
		return Math.max(BindLayerToTrackCommand.DEFAULT_CLIP_DURATION_SECONDS, lastReveal + beatSec);
	}

	public static String decodeLayerId(byte[] raw) {
		if (raw == null || raw.length == 0) {
			return "";
		}
		int end = raw.length;
		while (end > 0 && raw[end - 1] == 0) {
			end--;
		}
		return new String(raw, 0, end, StandardCharsets.UTF_8).trim();
	}

	private static String formatTime(double seconds) {
		if (seconds < 0) {
			seconds = 0;
		}
		return String.format("%.2fs", seconds);
	}
}
