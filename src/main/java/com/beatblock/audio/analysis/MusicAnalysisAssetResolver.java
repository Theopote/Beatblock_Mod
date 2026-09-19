package com.beatblock.audio.analysis;

import com.beatblock.audio.assets.AudioAnalysisMode;
import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.audio.beatmap.Beatmap;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.rendering.TimelineAudioFeatureFillSupport;

import org.jspecify.annotations.Nullable;

/**
 * 从 Timeline 绑定音频 / {@link AudioAsset} / engine 瞬态解析统一 {@link MusicAnalysisAsset}。
 */
public final class MusicAnalysisAssetResolver {

	private MusicAnalysisAssetResolver() {}

	public static @Nullable MusicAnalysisAsset resolve(
		@Nullable Timeline timeline,
		@Nullable AudioAnalysisEngine engine
	) {
		AudioAsset asset = resolveTimelineAsset(timeline);
		return resolve(asset, engine);
	}

	public static @Nullable MusicAnalysisAsset resolve(
		@Nullable AudioAsset asset,
		@Nullable AudioAnalysisEngine engine
	) {
		AudioFeatureTimeline cachedOnAsset = asset != null ? asset.getFeatureTimeline() : null;
		Beatmap beatmap = asset != null ? asset.getBeatmap() : null;
		AudioFeatureTimeline adaptedFromBeatmap = (cachedOnAsset == null && beatmap != null)
			? BeatmapFeatureTimelineAdapter.fromBeatmap(beatmap)
			: null;
		AudioFeatureTimeline fromEngine = engine != null ? engine.getLastFeatureTimeline() : null;

		AudioFeatureTimeline features = firstUsable(cachedOnAsset, adaptedFromBeatmap, fromEngine);
		if (features == null) {
			return null;
		}

		MusicAnalysisAsset.Source source;
		if (adaptedFromBeatmap != null && features == adaptedFromBeatmap) {
			source = MusicAnalysisAsset.Source.PYTHON_BEATMAP;
		} else if (cachedOnAsset != null && beatmap != null) {
			source = MusicAnalysisAsset.Source.MERGED;
		} else if (beatmap != null && fromEngine == null) {
			source = MusicAnalysisAsset.Source.PYTHON_BEATMAP;
		} else {
			source = MusicAnalysisAsset.Source.JAVA_FEATURE;
		}

		AudioAnalysisMode mode = AudioAnalysisMode.BASIC;
		if (asset != null) {
			mode = asset.getResolvedAnalysisMode() != null
				? asset.getResolvedAnalysisMode()
				: asset.getRequestedAnalysisMode();
		}
		double confidence = 0.75;
		if (beatmap != null && beatmap.meta != null) {
			confidence = Math.max(0.0, Math.min(1.0, beatmap.meta.bpmConfidence()));
			if (confidence <= 0.0) {
				confidence = 0.75;
			}
		}
		if (source == MusicAnalysisAsset.Source.MERGED) {
			confidence = Math.max(confidence, 0.85);
		}

		MusicAnalysisAsset resolved = new MusicAnalysisAsset(
			buildFingerprint(asset, features, beatmap),
			features,
			beatmap,
			mode,
			confidence,
			source
		);

		if (asset != null && asset.getFeatureTimeline() == null) {
			asset.setFeatureTimeline(features);
		}
		if (engine != null && engine.getLastFeatureTimeline() == null) {
			engine.bindLastFeatureTimeline(features);
		}
		return resolved;
	}

	public static boolean isReady(@Nullable Timeline timeline, @Nullable AudioAnalysisEngine engine) {
		MusicAnalysisAsset asset = resolve(timeline, engine);
		return asset != null && asset.isUsable();
	}

	private static @Nullable AudioAsset resolveTimelineAsset(@Nullable Timeline timeline) {
		String key = TimelineAudioFeatureFillSupport.getTimelineAudioPathKey(timeline);
		if (key == null) {
			return null;
		}
		return TimelineAudioFeatureFillSupport.findAssetByAudioKey(key);
	}

	private static @Nullable AudioFeatureTimeline firstUsable(AudioFeatureTimeline... candidates) {
		if (candidates == null) {
			return null;
		}
		for (AudioFeatureTimeline candidate : candidates) {
			if (candidate != null && candidate.getDurationSeconds() > 0) {
				return candidate;
			}
		}
		return null;
	}

	private static String buildFingerprint(
		@Nullable AudioAsset asset,
		AudioFeatureTimeline features,
		@Nullable Beatmap beatmap
	) {
		if (asset != null && asset.getPath() != null) {
			String key = TimelineAudioFeatureFillSupport.buildAudioAssetKey(asset);
			if (key != null) {
				String stamp = beatmap != null && beatmap.meta != null && beatmap.meta.generatedAt() != null
					? beatmap.meta.generatedAt()
					: Long.toString(Math.round(features.getDurationSeconds() * 1000));
				return key + "|" + stamp;
			}
		}
		return "anon|" + Math.round(features.getDurationSeconds() * 1000) + "|" + features.getBpm();
	}
}
