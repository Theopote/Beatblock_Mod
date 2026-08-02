package com.beatblock.timeline.playback;

import com.beatblock.engine.AnimationDefinition;
import com.beatblock.timeline.TimelineAnimationEvent;
import org.jspecify.annotations.Nullable;

/** A timeline event with playback-catalog references resolved at compile time. */
public record CompiledStageEvent(
	TimelineAnimationEvent event,
	@Nullable AnimationDefinition animationDefinition,
	@Nullable CompiledStageTarget target,
	long stableSequence
) {
	public CompiledStageEvent(
		TimelineAnimationEvent event,
		@Nullable AnimationDefinition animationDefinition,
		@Nullable CompiledStageTarget target
	) {
		this(event, animationDefinition, target, 0L);
	}

	public PlaybackSemantics semantics() {
		if (event == null) {
			return PlaybackSemantics.TRANSIENT;
		}
		PlaybackSemantics explicit = PlaybackSemantics.fromValue(
			event.getParameters().get("playbackSemantics")).orElse(null);
		if (explicit != null) {
			return explicit;
		}
		if (animationDefinition != null) {
			PlaybackSemantics presetDefault = animationDefinition.getPlaybackSemantics().orElse(null);
			if (presetDefault != null) {
				return presetDefault;
			}
		}
		var mode = event.getActionMode();
		if (mode == com.beatblock.timeline.TimelineAnimationActionMode.PLACE
			|| mode == com.beatblock.timeline.TimelineAnimationActionMode.CLEAR) {
			return PlaybackSemantics.IDEMPOTENT;
		} else if (mode == com.beatblock.timeline.TimelineAnimationActionMode.BUILD) {
			return PlaybackSemantics.STATEFUL;
		} else {
			return PlaybackSemantics.TRANSIENT;
		}
	}
}
