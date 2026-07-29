package com.beatblock.timeline.rendering;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.Track;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 单帧时间线轨模型快照：音频子轨 / 动画特征轨 / 建造图层轨定义。
 * <p>
 * 由 {@link TimelineEditor} 每帧构建一次，Renderer 与布局共用，避免双路 {@link TrackRegistry} 分配。
 */
public final class TimelineFrameTrackSnapshot {

	private static final TimelineFrameTrackSnapshot EMPTY = new TimelineFrameTrackSnapshot(
		List.of(), List.of(), List.of(),
		new AudioCacheKey(false, false, Set.of()),
		Set.of(),
		List.of()
	);

	private final List<TrackDefinition> audioSubTracks;
	private final List<TrackDefinition> animationSubTracks;
	private final List<TrackDefinition> buildLayerTracks;
	private final AudioCacheKey audioKey;
	private final Set<String> animationTrackIds;
	private final List<String> buildLayerTrackIds;

	private TimelineFrameTrackSnapshot(
		List<TrackDefinition> audioSubTracks,
		List<TrackDefinition> animationSubTracks,
		List<TrackDefinition> buildLayerTracks,
		AudioCacheKey audioKey,
		Set<String> animationTrackIds,
		List<String> buildLayerTrackIds
	) {
		this.audioSubTracks = audioSubTracks;
		this.animationSubTracks = animationSubTracks;
		this.buildLayerTracks = buildLayerTracks;
		this.audioKey = audioKey;
		this.animationTrackIds = animationTrackIds;
		this.buildLayerTrackIds = buildLayerTrackIds;
	}

	public static @NonNull TimelineFrameTrackSnapshot empty() {
		return EMPTY;
	}

	/**
	 * 相对上一帧复用未变列表；有变更时仅重建变化的轨组。
	 */
	public static @NonNull TimelineFrameTrackSnapshot build(
		@Nullable Timeline timeline,
		@Nullable TimelineFrameTrackSnapshot previous
	) {
		if (timeline == null) {
			return EMPTY;
		}
		TimelineFrameTrackSnapshot prev = previous != null ? previous : EMPTY;

		AudioCacheKey audioKey = AudioCacheKey.from(timeline);
		List<TrackDefinition> audio = prev.audioSubTracks;
		if (!audioKey.equals(prev.audioKey)) {
			audio = TrackRegistry.buildAudioSubTracks(timeline);
		}

		Set<String> animIds = collectAnimationFeatureTrackIds(timeline);
		List<TrackDefinition> anim = prev.animationSubTracks;
		if (!animIds.equals(prev.animationTrackIds)) {
			anim = TrackRegistry.buildBlockAnimationControlTracks(timeline);
		}

		List<String> buildIds = collectBuildLayerTrackIds(timeline);
		List<TrackDefinition> build = prev.buildLayerTracks;
		if (!buildIds.equals(prev.buildLayerTrackIds)) {
			build = TrackRegistry.buildBuildLayerTracks(timeline);
		}

		if (audio == prev.audioSubTracks
			&& anim == prev.animationSubTracks
			&& build == prev.buildLayerTracks) {
			return prev;
		}
		return new TimelineFrameTrackSnapshot(audio, anim, build, audioKey, animIds, buildIds);
	}

	public @NonNull List<TrackDefinition> audioSubTracks() {
		return audioSubTracks;
	}

	public @NonNull List<TrackDefinition> animationSubTracks() {
		return animationSubTracks;
	}

	public @NonNull List<TrackDefinition> buildLayerTracks() {
		return buildLayerTracks;
	}

	private static Set<String> collectAnimationFeatureTrackIds(Timeline timeline) {
		Set<String> ids = new LinkedHashSet<>();
		for (Track track : timeline.getTracks()) {
			if (Timeline.isBlockAnimationFeatureTrackId(track.getId())) {
				ids.add(track.getId());
			}
		}
		return ids.isEmpty() ? Set.of() : Collections.unmodifiableSet(ids);
	}

	private static List<String> collectBuildLayerTrackIds(Timeline timeline) {
		List<String> ids = new java.util.ArrayList<>();
		for (Track track : timeline.getTracks()) {
			if (com.beatblock.timeline.layer.BuildLayerTrackSupport.isBuildLayerTrack(track)) {
				ids.add(track.getId());
			}
		}
		return ids.isEmpty() ? List.of() : List.copyOf(ids);
	}

	private record AudioCacheKey(boolean hasWaveform, boolean hasStemWaveforms, Set<String> featureKeys) {
		static AudioCacheKey from(Timeline timeline) {
			Set<String> keys = timeline.getFeatureTracks().keySet();
			// 不 Set.copyOf：keySet 视图 + size/contains 用于 equals 时可能不稳定；
			// feature key 集合用 LinkedHashSet 快照，仅在 from 时分配一次。
			Set<String> snapshot = keys.isEmpty() ? Set.of() : Set.copyOf(keys);
			return new AudioCacheKey(
				timeline.getWaveform() != null,
				timeline.hasStemWaveforms(),
				snapshot
			);
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (!(o instanceof AudioCacheKey that)) return false;
			return hasWaveform == that.hasWaveform
				&& hasStemWaveforms == that.hasStemWaveforms
				&& Objects.equals(featureKeys, that.featureKeys);
		}

		@Override
		public int hashCode() {
			return Objects.hash(hasWaveform, hasStemWaveforms, featureKeys);
		}
	}
}
