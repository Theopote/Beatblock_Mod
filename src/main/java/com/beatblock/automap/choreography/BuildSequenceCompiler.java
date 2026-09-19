package com.beatblock.automap.choreography;

import com.beatblock.BeatBlock;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.layer.BuildLayer;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.engine.layer.LayerVisibilityState;
import com.beatblock.timeline.AnimationEventParams;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.generation.GenerationSession;
import com.beatblock.timeline.generation.TimelineGenerationMetadataSupport;
import com.beatblock.timeline.layer.BuildLayerTrackSupport;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * 将 {@link BuildSequencePlan} 编译为 BUILD_LAYER 轨道 Clip + BUILD 事件，并绑定图层。
 * <p>
 * 语义对齐 {@link com.beatblock.timeline.command.layer.BindLayerToTrackCommand}，
 * 但不经 CommandManager（与其他 Choreography 编译路径一致）。
 */
public final class BuildSequenceCompiler {

	private BuildSequenceCompiler() {}

		public static int compile(Timeline timeline, ChoreographyPlan plan) {
			return compile(timeline, plan, resolveLayerManager(), null);
		}

		public static int compile(
			Timeline timeline,
			ChoreographyPlan plan,
			@Nullable BuildLayerManager layerManager
		) {
			return compile(timeline, plan, layerManager, null);
		}

		public static int compile(
			Timeline timeline,
			ChoreographyPlan plan,
			@Nullable BuildLayerManager layerManager,
			@Nullable GenerationSession session
		) {
			if (timeline == null || plan == null || plan.buildSequences().isEmpty()) {
				return 0;
			}
			if (layerManager == null) {
				return 0;
			}
			int count = 0;
			int phraseIndex = 0;
			for (BuildSequencePlan sequence : plan.buildSequences()) {
				if (sequence != null && sequence.isValid()
					&& compileOne(timeline, layerManager, sequence, session, phraseIndex++)) {
					count++;
				}
			}
			return count;
		}

		private static boolean compileOne(
			Timeline timeline,
			BuildLayerManager layerManager,
			BuildSequencePlan sequence,
			@Nullable GenerationSession session,
			int phraseIndex
		) {
			BuildLayer layer = layerManager.get(sequence.layerId());
			if (layer == null) {
				return false;
			}

			prepareLayerForBind(timeline, layerManager, layer);

			if (!layer.canBindToTrack()) {
				return false;
			}

			Track track = resolveTrack(timeline, sequence.trackId());
			if (track == null) {
				return false;
			}

			double start = sequence.startSeconds();
			double duration = sequence.durationSeconds();
			double end = start + duration;

			String clipId = "clip_build_" + UUID.randomUUID().toString().substring(0, 8);
			String eventId = "evt_build_" + UUID.randomUUID().toString().substring(0, 8);

			Clip clip = new Clip(clipId, start, end);
			track.addClip(clip);

			String targetObjectId = !sequence.targetObjectId().isBlank()
				? sequence.targetObjectId()
				: layer.getStageObjectId();

			Map<String, Object> params = new AnimationEventParams(
				TimelineAnimationActionMode.BUILD,
				"",
				targetObjectId,
				1f,
				duration,
				TimelineEventOrigin.GENERATED,
				Map.of(
					"layerId", layer.getId(),
					"buildMode", sequence.mode().name(),
					"buildDissolve", String.valueOf(sequence.dissolve()),
					"layerBound", "true",
					"pacingMode", sequence.pacing().name()
				)
			).toParameterMap();
			if (session != null) {
				params = TimelineGenerationMetadataSupport.apply(
					params,
					session.forPhrase(sequence.sectionIndex(), phraseIndex)
				);
			}

			TimelineEvent event = new TimelineEvent(eventId, start, EventType.ANIMATION, params);
			clip.addEvent(event);

			layerManager.bindToClip(layer, clipId);
			timeline.markAnimationEventsDirty(track.getId());
			timeline.setDurationSeconds(Math.max(timeline.getDurationSeconds(), end));
			return true;
		}

	/**
	 * 若图层已绑定旧 Clip，先解绑并移除旧 Clip，恢复为可绑定的 FREE_HIDDEN。
	 */
	private static void prepareLayerForBind(
		Timeline timeline,
		BuildLayerManager layerManager,
		BuildLayer layer
	) {
		if (layer.getState() != LayerVisibilityState.BOUND_TO_TRACK) {
			return;
		}
		String boundClipId = layer.getBoundClipId();
		layerManager.unbindFromClip(layer);
		if (boundClipId == null || boundClipId.isBlank()) {
			return;
		}
		for (Track track : BuildLayerTrackSupport.listTracks(timeline)) {
			Clip clip = track.getClip(boundClipId);
			if (clip == null) {
				continue;
			}
			track.removeClip(boundClipId);
			timeline.markAnimationEventsDirty(track.getId());
			return;
		}
	}

	private static @Nullable Track resolveTrack(Timeline timeline, @Nullable String trackId) {
		BuildLayerTrackSupport.normalizeLoadedTracks(timeline);
		if (trackId != null && !trackId.isBlank()) {
			Track explicit = timeline.getTrack(trackId);
			if (explicit != null && BuildLayerTrackSupport.isBuildLayerTrack(explicit)) {
				return explicit;
			}
			return null;
		}
		return BuildLayerTrackSupport.ensureDefaultTrack(timeline);
	}

	private static @Nullable BuildLayerManager resolveLayerManager() {
		try {
			BlockAnimationEngine engine = BeatBlock.getContext().blockAnimationEngine();
			return engine != null ? engine.getBuildLayerManager() : null;
		} catch (RuntimeException ignored) {
			return null;
		}
	}
}
