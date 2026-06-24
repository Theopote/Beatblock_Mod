package com.beatblock.timeline.rendering;

import com.beatblock.audio.assets.AudioAsset;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;

/** {@link TimelineAudioDropHandler} 所需宿主回调。 */
public interface TimelineAudioDropHost {

	BeatBlockContext context();

	void setAudioGroupDropHighlight(boolean highlight);

	void resetBeatmapAutoApplySignature();

	void requestDenseFeatureEnrichment(Timeline timeline, AudioAsset asset);

	void bindStemAudioIfDemucs(com.beatblock.audio.beatmap.Beatmap beatmap);

	String resolveDefaultTargetObjectId();

	void syncClockDuration();
}
