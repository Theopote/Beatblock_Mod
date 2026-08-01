package com.beatblock.timeline.rendering;

import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;

import java.util.List;

/** {@link TimelineAudioDropHandler} 所需宿主回调。 */
public interface TimelineAudioDropHost {

	BeatBlockContext context();

	void setAudioGroupDropHighlight(boolean highlight);

	void setBuildLayerDropHighlightRow(int rowIndex);

	void resetBeatmapAutoApplySignature();

	void requestDenseFeatureEnrichment(Timeline timeline, AudioAsset asset);

	void bindStemAudioIfDemucs(com.beatblock.audio.beatmap.Beatmap beatmap);

	/**
	 * Fallback used by audio-feature mappers when they need any target string.
	 * Prefer {@link #resolvePreferredStageObjectIds()} / {@link #resolveRegisteredStageObjectIds()}
	 * for Animation Library drops (may return empty → unbound).
	 */
	String resolveDefaultTargetObjectId();

	/**
	 * Explicit world / layer selection of StageObjects for animation preset drops.
	 * Empty means “no preferred selection”.
	 */
	default List<String> resolvePreferredStageObjectIds() {
		return List.of();
	}

	/** All registered StageObject ids (for the single-object auto-bind shortcut). */
	default List<String> resolveRegisteredStageObjectIds() {
		return List.of();
	}

	void syncClockDuration();
}
