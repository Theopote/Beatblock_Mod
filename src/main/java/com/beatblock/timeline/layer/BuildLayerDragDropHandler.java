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
import com.beatblock.timeline.rendering.TimelineTrackMeta;
import com.beatblock.timeline.rendering.TrackDefinition;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.notification.ToastNotificationSystem;
import imgui.ImGui;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 建造图层拖入时间线建造轨道（对齐 Director 容器拖放模式）。
 *
 * <p>时间线侧使用覆盖整个轨道子窗口的 invisibleButton + beginDragDropTarget，
 * 根据鼠标 Y 解析目标行；图层面板侧在列表行上 beginDragDropSource 并写入 String 载荷。
 */
public final class BuildLayerDragDropHandler {

	public static final String PAYLOAD_TYPE = "BB_BUILD_LAYER_ID";

	private static String lastSourceLayerId = "";

	private BuildLayerDragDropHandler() {
	}

	public static void rememberSourceLayerId(String layerId) {
		if (layerId != null && !layerId.isBlank()) {
			lastSourceLayerId = layerId.trim();
		}
	}

	/**
	 * 在轨道子窗口全部绘制完成后调用：单块画布接收拖放（Director {@code ##TimelineCanvas} 模式）。
	 *
	 * @return 当前悬停的有效建造图层行索引，无则 -1
	 */
	public static int handleCanvasDragDropTarget(
		TimelineAudioDropHost host,
		Timeline timeline,
		TimelineLayout layout,
		TimelineViewState viewState,
		TimelineToolbarState toolbarState,
		InteractionState interactionState,
		SelectionState selectionState,
		TimelineTrackListState trackListState,
		List<TrackDefinition> buildLayerTracks
	) {
		if (timeline == null || layout == null) {
			return -1;
		}

		ImGui.setCursorPos(0f, 0f);
		float canvasW = ImGui.getWindowContentRegionMax().x;
		float canvasH = Math.max(
			layout.trackHeaderHeight + layout.contentHeight + 16f,
			ImGui.getWindowContentRegionMax().y
		);
		ImGui.invisibleButton("##TimelineBuildLayerCanvas", canvasW, canvasH);

		int dropRow = -1;
		if (!ImGui.beginDragDropTarget()) {
			return -1;
		}

		float mouseY = ImGui.getMousePosY();
		int rowIndex = layout.findRowAtScreenY(mouseY);
		String targetTrackId = resolveTrackIdForRow(rowIndex, buildLayerTracks);
		boolean validRow = targetTrackId != null
			&& (trackListState == null || !trackListState.isLocked(rowIndex));

		if (validRow) {
			dropRow = rowIndex;
			if (host != null) {
				host.setBuildLayerDropHighlightRow(rowIndex);
			}
		}

		Object payload = ImGui.acceptDragDropPayload(PAYLOAD_TYPE);
		if (payload != null) {
			dropRow = -1;
			String layerId = resolveLayerId(payload);
			if (layerId.isBlank()) {
				ImGui.endDragDropTarget();
				return -1;
			}
			if (!validRow || targetTrackId == null) {
				ToastNotificationSystem.showError(BBTexts.get("beatblock.message.layer_bind_wrong_track"));
			} else {
				handleDrop(
					host,
					timeline,
					layout,
					viewState,
					toolbarState,
					interactionState,
					selectionState,
					targetTrackId,
					layerId
				);
			}
		}

		ImGui.endDragDropTarget();
		return dropRow;
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
		if (context == null) {
			ToastNotificationSystem.showError(BBTexts.get("beatblock.message.editor_unavailable"));
			return;
		}
		if (timeline == null || layerId == null || layerId.isBlank()) {
			return;
		}

		BuildLayerManager layerManager = context.buildLayerManager();
		if (layerManager == null) {
			ToastNotificationSystem.showError(BBTexts.get("beatblock.message.editor_unavailable"));
			return;
		}

		BuildLayer layer = layerManager.get(layerId);
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

		var commandManager = context.commandManager();
		TimelineEditor editor = context.timelineEditor();
		if (commandManager == null) {
			ToastNotificationSystem.showError(BBTexts.get("beatblock.message.editor_unavailable"));
			return;
		}
		commandManager.execute(cmd);
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
		if (editor != null) {
			editor.syncClockDuration();
		}
		ToastNotificationSystem.showSuccess(
			BBTexts.get("beatblock.message.layer_bound_to_track", layer.getName(), formatTime(dropTime))
		);
	}

	public static String resolveTrackIdForRow(int rowIndex, List<TrackDefinition> buildLayerTracks) {
		if (!TimelineTrackMeta.isBuildLayerSubRow(rowIndex) || buildLayerTracks == null) {
			return null;
		}
		int slot = TimelineTrackMeta.buildLayerSubRowSlot(rowIndex);
		if (slot < 0 || slot >= buildLayerTracks.size()) {
			return null;
		}
		String trackId = buildLayerTracks.get(slot).getKey();
		return trackId != null && !trackId.isBlank() ? trackId : null;
	}

	public static String resolveLayerId(Object payload) {
		if (payload instanceof String text) {
			String trimmed = text.trim();
			if (!trimmed.isEmpty()) {
				return trimmed;
			}
		}
		if (payload instanceof byte[] raw) {
			String decoded = decodeLayerId(raw);
			if (!decoded.isBlank()) {
				return decoded;
			}
		}
		return lastSourceLayerId != null ? lastSourceLayerId : "";
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
