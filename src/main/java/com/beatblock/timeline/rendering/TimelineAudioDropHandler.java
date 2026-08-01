package com.beatblock.timeline.rendering;

import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.assets.AudioAssetManager;
import com.beatblock.audio.assets.AudioAssetStatus;
import com.beatblock.engine.influence.BlockInfluencePreset;
import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.timeline.AnimationEventParams;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.InteractionState;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.generation.AnimationDropTargetResolver;
import com.beatblock.timeline.generation.AnimationMultiTargetDropPrompt;
import com.beatblock.timeline.generation.AnimationPresetEventWriter;
import com.beatblock.timeline.generation.TimelineDraftWriter;
import com.beatblock.timeline.interaction.DragController;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.notification.ToastNotificationSystem;
import imgui.ImGui;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static com.beatblock.timeline.rendering.TimelineAudioFeatureFillSupport.buildAudioAssetKey;
import static com.beatblock.timeline.rendering.TimelineAudioFeatureFillSupport.computeNextClipStartOffset;
import static com.beatblock.timeline.rendering.TimelineAudioFeatureFillSupport.restoreFeatureEvents;
import static com.beatblock.timeline.rendering.TimelineAudioFeatureFillSupport.saveFeatureEvents;
import static com.beatblock.timeline.rendering.TimelineAudioFeatureFillSupport.shiftFeatureEventsByOffset;

/**
 * 音频资产与动画预设拖放到时间线：ImGui 目标区与 beatmap/特征轨回填。
 * <p>
 * 动画轨道（block / auto）同时接收 {@value #ANIMATION_PRESET_PAYLOAD_TYPE} 载荷，
 * 把 {@link BlockInfluencePreset} 直接实例化为新的动画事件。
 */
public final class TimelineAudioDropHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(TimelineAudioDropHandler.class);

	public static final String ANIMATION_PRESET_PAYLOAD_TYPE = "BB_ANIMATION_PRESET_ID";

	private TimelineAudioDropHandler() {}

	public static void renderAudioGroupDropTarget(
		TimelineAudioDropHost host,
		int rowIndex,
		float rowHeight,
		Timeline timeline,
		TimelineLayout layout,
		TimelineTrackListState trackListState
	) {
		float screenY = layout.getRowScreenY(rowIndex);
		if (screenY < 0) return;
		ImGui.setCursorScreenPos(layout.contentLeft, screenY);
		ImGui.invisibleButton("##AudioDropTarget_" + rowIndex, layout.contentWidth, rowHeight);
		boolean audioGroupLocked = trackListState != null && trackListState.isLocked(TimelineTrackMeta.ROW_AUDIO_GROUP);

		if (!audioGroupLocked && ImGui.isItemHovered() && host != null) {
			host.setAudioGroupDropHighlight(true);
		}

		if (!audioGroupLocked) {
			acceptAudioAssetDrop(host, timeline, -1);
		}
	}

	private static void acceptAudioAssetDrop(TimelineAudioDropHost host, Timeline timeline, int dropTargetRowIndex) {
		if (!ImGui.beginDragDropTarget()) {
			return;
		}
		try {
			byte[] payload = ImGui.acceptDragDropPayload("BB_AUDIO_ASSET_ID");
			if (payload == null) {
				return;
			}
			String assetId = new String(payload, StandardCharsets.UTF_8).trim();
			AudioAsset asset = AudioAssetManager.getInstance().findById(assetId);
			if (asset == null) {
				asset = AudioAssetManager.getInstance().getCurrentDragAsset();
			}
			handleDroppedAudioAsset(host, timeline, asset, dropTargetRowIndex);
		} finally {
			ImGui.endDragDropTarget();
		}
	}

	public static void renderAnimationTrackDropTarget(
		TimelineAudioDropHost host,
		int rowIndex,
		float rowHeight,
		Timeline timeline,
		TimelineLayout layout,
		TimelineViewState viewState,
		TimelineToolbarState toolbarState,
		InteractionState interactionState,
		SelectionState selectionState
	) {
		float screenY = layout.getRowScreenY(rowIndex);
		if (screenY < 0) return;
		ImGui.setCursorScreenPos(layout.contentLeft, screenY);
		ImGui.invisibleButton("##AnimDropTarget_" + rowIndex, layout.contentWidth, rowHeight);
		acceptDrops(host, timeline, rowIndex, layout, viewState, toolbarState, interactionState, selectionState);
	}

	private static void acceptDrops(
		TimelineAudioDropHost host,
		Timeline timeline,
		int dropTargetRowIndex,
		TimelineLayout layout,
		TimelineViewState viewState,
		TimelineToolbarState toolbarState,
		InteractionState interactionState,
		SelectionState selectionState
	) {
		if (!ImGui.beginDragDropTarget()) {
			return;
		}
		byte[] audioPayload = ImGui.acceptDragDropPayload("BB_AUDIO_ASSET_ID");
		if (audioPayload != null) {
			String assetId = new String(audioPayload, StandardCharsets.UTF_8).trim();
			AudioAsset asset = AudioAssetManager.getInstance().findById(assetId);
			if (asset == null) {
				asset = AudioAssetManager.getInstance().getCurrentDragAsset();
			}
			handleDroppedAudioAsset(host, timeline, asset, dropTargetRowIndex);
		} else {
			Object presetPayload = ImGui.acceptDragDropPayload(ANIMATION_PRESET_PAYLOAD_TYPE);
			String presetId = decodeStringPayload(presetPayload);
			if (!presetId.isBlank()) {
				handleDroppedAnimationPreset(
					host,
					timeline,
					dropTargetRowIndex,
					layout,
					viewState,
					toolbarState,
					interactionState,
					selectionState,
					presetId
				);
			}
		}
		ImGui.endDragDropTarget();
	}

	private static void handleDroppedAnimationPreset(
		TimelineAudioDropHost host,
		Timeline timeline,
		int dropTargetRowIndex,
		TimelineLayout layout,
		TimelineViewState viewState,
		TimelineToolbarState toolbarState,
		InteractionState interactionState,
		SelectionState selectionState,
		String presetId
	) {
		if (host == null || timeline == null || presetId == null || presetId.isBlank()) {
			return;
		}
		BlockInfluencePreset preset = BlockInfluencePresets.get(presetId);
		if (preset == null) {
			ToastNotificationSystem.showError(BBTexts.get("beatblock.animation_library.preset_missing"));
			return;
		}
		String trackId = resolveTrackIdForRow(dropTargetRowIndex);
		if (trackId == null) {
			return;
		}
		// Preset + Target + Time = StageEvent（见 docs/animation-library-drag-ux.md）
		AnimationDropTargetResolver.Result targets = resolveDropTargets(selectionState, timeline, host);
		float mouseX = ImGui.getMousePosX();
		double rawTime = viewState.screenToTime(mouseX - layout.contentLeft);
		double dropTime = DragController.snapTime(rawTime, null, timeline, toolbarState, viewState, interactionState);
		dropTime = Math.max(0.0, dropTime);

		if (targets.mode() == AnimationDropTargetResolver.Mode.MULTI) {
			// Ask user: primary vs all (Group Event deferred)
			final double time = dropTime;
			final String track = trackId;
			final String pid = presetId;
			final String displayName = preset.getDisplayName();
			AnimationMultiTargetDropPrompt.request(new AnimationMultiTargetDropPrompt.Pending(
				displayName,
				targets.targetObjectIds(),
				chosenTargets -> {
					var result = AnimationPresetEventWriter.writePresetEvents(
						timeline, track, pid, time, chosenTargets);
					if (result.written() > 0) {
						host.syncClockDuration();
					}
					AnimationPresetEventWriter.toastWriteResult(displayName, result);
					return result.written();
				}
			));
			return;
		}

		var result = AnimationPresetEventWriter.writePresetEvents(
			timeline,
			trackId,
			presetId,
			dropTime,
			targets.targetsForEventCreation()
		);
		if (result.written() > 0) {
			host.syncClockDuration();
		}
		AnimationPresetEventWriter.toastWriteResult(preset.getDisplayName(), result);
	}

	private static @Nullable String resolveTrackIdForRow(int rowIndex) {
		if (rowIndex == TimelineTrackMeta.ROW_ANIM_BLOCK) {
			return Timeline.TRACK_ID_ANIMATION_BLOCK;
		}
		if (rowIndex == TimelineTrackMeta.ROW_ANIM_AUTO) {
			return Timeline.TRACK_ID_ANIMATION_AUTO;
		}
		return null;
	}

	/**
	 * Preferred: host world/layer selection → selected events' targets → single registered StageObject → unbound.
	 * Does not use fake {@code "default"} ids.
	 */
	static AnimationDropTargetResolver.Result resolveDropTargets(
		SelectionState selectionState,
		Timeline timeline,
		TimelineAudioDropHost host
	) {
		List<String> preferred = host != null ? host.resolvePreferredStageObjectIds() : List.of();
		List<String> fromEvents = collectTargetsFromSelectedEvents(selectionState, timeline);
		List<String> registered = host != null ? host.resolveRegisteredStageObjectIds() : List.of();
		return AnimationDropTargetResolver.resolve(preferred, fromEvents, registered);
	}

	private static List<String> collectTargetsFromSelectedEvents(
		SelectionState selectionState,
		Timeline timeline
	) {
		List<String> targets = new ArrayList<>();
		if (selectionState == null || timeline == null || selectionState.getSelectedEvents().isEmpty()) {
			return targets;
		}
		for (String eventId : selectionState.getSelectedEvents()) {
			TimelineAnimationEvent event = findAnimationEvent(timeline, eventId);
			if (event != null && !event.isUnboundTarget()) {
				targets.add(event.getTargetObjectId());
			}
		}
		return targets;
	}

	private static @Nullable TimelineAnimationEvent findAnimationEvent(Timeline timeline, String eventId) {
		if (timeline == null || eventId == null || eventId.isBlank()) {
			return null;
		}
		for (TimelineAnimationEvent event : timeline.getStageEvents()) {
			if (eventId.equals(event.getEventId())) {
				return event;
			}
		}
		return null;
	}

	private static String decodeStringPayload(@Nullable Object payload) {
		if (payload instanceof String s) {
			String trimmed = s.trim();
			if (!trimmed.isBlank()) {
				return trimmed;
			}
		}
		if (payload instanceof byte[] raw) {
			String decoded = new String(raw, StandardCharsets.UTF_8).trim();
			if (!decoded.isBlank()) {
				return decoded;
			}
		}
		return "";
	}

	public static void handleDroppedAudioAsset(
		TimelineAudioDropHost host,
		Timeline timeline,
		AudioAsset asset,
		int dropTargetRowIndex
	) {
		if (host == null || asset == null || timeline == null || host.context().audioAnalysisEngine() == null) return;

		double startOffset = computeNextClipStartOffset(timeline);
		bindDroppedAudioToPlayback(host, timeline, asset);
		upsertAudioRootClip(timeline, asset);
		String droppedAudioKey = buildAudioAssetKey(asset);
		if (startOffset > 0 && droppedAudioKey != null) {
			timeline.setMetadata("audioClipOffset_" + droppedAudioKey, startOffset);
		}
		host.resetBeatmapAutoApplySignature();

		boolean canUseBeatmapNow = asset.getStatus() == AudioAssetStatus.COMPLETED && asset.getBeatmap() != null;
		if (canUseBeatmapNow && !isBeatmapReadyForImmediateApply(asset.getBeatmap())) {
			canUseBeatmapNow = false;
			LOGGER.info(
				"BeatBlock Timeline: cached beatmap has unreadable/missing demucs stems, scheduling re-analysis path={}",
				asset.getPath());
			AudioAssetManager.getInstance().startAnalysis(asset);
		}
		if (canUseBeatmapNow) {
			timeline.setMetadata("awaitingAnalyzedBeatmap", null);
			double prevDuration = timeline.getDurationSeconds();
			var savedFeatureEvents = saveFeatureEvents(timeline);
			host.context().audioAnalysisEngine().fillTimelineFromBeatmap(timeline, asset.getBeatmap());
			shiftFeatureEventsByOffset(timeline, startOffset);
			restoreFeatureEvents(timeline, savedFeatureEvents);
			timeline.setDurationSeconds(prevDuration);
			host.requestDenseFeatureEnrichment(timeline, asset);
			host.bindStemAudioIfDemucs(asset.getBeatmap());
		} else if (asset.getFeatureTimeline() != null) {
			timeline.setMetadata("awaitingAnalyzedBeatmap", droppedAudioKey);
			LOGGER.info(
				"BeatBlock Timeline: dropped asset not completed yet, using feature timeline temporarily path={} status={}",
				asset.getPath(), asset.getStatus());
			double prevDuration = timeline.getDurationSeconds();
			var savedFeatureEvents = saveFeatureEvents(timeline);
			host.context().audioAnalysisEngine().fillTimelineFromFeature(
				timeline, asset.getFeatureTimeline(), asset.getSampleRate());
			shiftFeatureEventsByOffset(timeline, startOffset);
			restoreFeatureEvents(timeline, savedFeatureEvents);
			timeline.setDurationSeconds(prevDuration);
		} else {
			timeline.setMetadata("awaitingAnalyzedBeatmap", droppedAudioKey);
			LOGGER.info(
				"BeatBlock Timeline: dropped asset pending analysis, waiting for auto-apply path={} status={}",
				asset.getPath(), asset.getStatus());
			host.requestDenseFeatureEnrichment(timeline, asset);
		}

		if (dropTargetRowIndex == TimelineTrackMeta.ROW_ANIM_BLOCK
			|| dropTargetRowIndex == TimelineTrackMeta.ROW_ANIM_AUTO) {
			TimelineAnimationFeatureMapper.populateFromAudioFeatures(
				timeline, dropTargetRowIndex, host::resolveDefaultTargetObjectId);
		}

		host.syncClockDuration();
	}

	public static boolean isBeatmapReadyForImmediateApply(com.beatblock.audio.beatmap.Beatmap beatmap) {
		if (beatmap == null || beatmap.meta == null) return false;
		if (!beatmap.meta.hasStemSeparation()) return true;
		if (beatmap.beatmapFilePath == null || beatmap.meta.stems() == null) return false;
		Path beatmapDir = beatmap.beatmapFilePath.getParent();
		if (beatmapDir == null) return false;

		for (Map.Entry<String, String> entry : beatmap.meta.stems().entrySet()) {
			String relPath = entry.getValue();
			if (relPath == null || relPath.isBlank()) return false;
			Path stemPath = beatmapDir.resolve(relPath).normalize();
			try {
				if (!java.nio.file.Files.isRegularFile(stemPath) || java.nio.file.Files.size(stemPath) <= 1024) {
					return false;
				}
			} catch (IOException e) {
				return false;
			}
		}
		return true;
	}

	public static double resolveAssetDurationSeconds(AudioAsset asset, Timeline timeline) {
		double duration = asset != null ? asset.getDurationSeconds() : 0.0;
		if (duration > 0) return duration;
		if (asset != null && asset.getBeatmap() != null && asset.getBeatmap().meta != null) {
			double beatmapDuration = asset.getBeatmap().meta.durationMs() / 1000.0;
			if (beatmapDuration > 0) return beatmapDuration;
		}
		if (asset != null && asset.getFeatureTimeline() != null && asset.getFeatureTimeline().getDurationSeconds() > 0) {
			return asset.getFeatureTimeline().getDurationSeconds();
		}
		double fallback = timeline != null ? timeline.getDurationSeconds() : 0.0;
		return Math.max(0.1, fallback > 0 ? fallback : 1.0);
	}

	private static void bindDroppedAudioToPlayback(TimelineAudioDropHost host, Timeline timeline, AudioAsset asset) {
		String audioPath = asset.getPath().toAbsolutePath().normalize().toString();
		timeline.setMetadata("audioPath", audioPath);
		if (host.context().musicPlayer() != null) {
			boolean loaded = host.context().musicPlayer().loadAudio(audioPath);
			host.context().musicPlayer().setCurrentTimeSeconds(0);
			if (loaded) {
				LOGGER.info("BeatBlock Timeline: dropped audio asset bound to playback path={}", audioPath);
			} else {
				LOGGER.warn(
					"BeatBlock Timeline: dropped audio asset failed to bind path={} reason={}",
					audioPath, host.context().musicPlayer().getLastLoadError());
			}
		} else {
			LOGGER.warn("BeatBlock Timeline: dropped audio asset has no MusicPlayer instance path={}", audioPath);
		}
	}

	private static void upsertAudioRootClip(Timeline timeline, AudioAsset asset) {
		if (timeline == null || asset == null) return;
		Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		if (audioTrack == null) return;

		double start = 0.0;
		for (Clip c : audioTrack.getClips()) {
			if (c == null) continue;
			start = Math.max(start, c.getEndTimeSeconds());
		}

		double duration = resolveAssetDurationSeconds(asset, timeline);
		double end = start + duration;

		Clip rootClip = TimelineOperations.addClip(audioTrack, start, end);
		if (rootClip != null) {
			timeline.setDurationSeconds(Math.max(timeline.getDurationSeconds(), end));
			timeline.setMetadata("audioRootClipId", rootClip.getId());
			timeline.setMetadata("audioAssetId", asset.getId());
			String fn = asset.getPath().getFileName().toString();
			int dot = fn.lastIndexOf('.');
			if (dot > 0) fn = fn.substring(0, dot);
			timeline.setMetadata("clipLabel_" + rootClip.getId(), fn);
			timeline.setMetadata("clipAudioPath_" + rootClip.getId(), asset.getPath().toAbsolutePath().normalize().toString());
			String clipAudioKey = buildAudioAssetKey(asset);
			if (clipAudioKey != null) {
				timeline.setMetadata("clipAudioKey_" + rootClip.getId(), clipAudioKey);
			}
		}
	}
}
