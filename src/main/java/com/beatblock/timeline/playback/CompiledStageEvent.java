package com.beatblock.timeline.playback;

import com.beatblock.engine.AnimationDefinition;
import com.beatblock.timeline.TimelineAnimationEvent;
import org.jspecify.annotations.Nullable;

/** A timeline event with playback-catalog references resolved at compile time. */
public record CompiledStageEvent(
	TimelineAnimationEvent event,
	@Nullable AnimationDefinition animationDefinition,
	@Nullable CompiledStageTarget target
) {}
