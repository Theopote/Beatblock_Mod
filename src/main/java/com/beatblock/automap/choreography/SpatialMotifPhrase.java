package com.beatblock.automap.choreography;

import java.util.List;

/**
 * 编舞计划中的跨对象空间编排短语。
 * <p>
 * 编译时由 {@link SpatialMotifCompiler} 展开为多条 {@link com.beatblock.timeline.TimelineAnimationEvent}。
 */
public record SpatialMotifPhrase(
	double timeSeconds,
	SpatialMotifId motifId,
	List<String> participantIds,
	MotifAxis axis,
	double propagationDelaySeconds,
	String primitiveId,
	MotifPhaseMode phaseMode,
	float energy,
	double durationSeconds,
	boolean useEnergyForHeight,
	float heightMultiplier,
	int sectionIndex,
	ChoreographyTimingSnap timingSnap
) {
	public SpatialMotifPhrase(
		double timeSeconds,
		SpatialMotifId motifId,
		List<String> participantIds,
		MotifAxis axis,
		double propagationDelaySeconds,
		String primitiveId,
		float energy,
		double durationSeconds,
		int sectionIndex
	) {
		this(
			timeSeconds,
			motifId,
			participantIds,
			axis,
			propagationDelaySeconds,
			primitiveId,
			MotifPhaseMode.IN_PHASE,
			energy,
			durationSeconds,
			true,
			4f,
			sectionIndex,
			ChoreographyTimingSnap.BAR
		);
	}

	public SpatialMotifPhrase {
		motifId = motifId != null ? motifId : SpatialMotifId.CASCADE;
		participantIds = participantIds != null ? List.copyOf(participantIds) : List.of();
		axis = axis != null ? axis : MotifAxis.X;
		propagationDelaySeconds = Math.max(0.0, propagationDelaySeconds);
		primitiveId = primitiveId != null ? primitiveId : "pulse";
		phaseMode = phaseMode != null ? phaseMode : MotifPhaseMode.IN_PHASE;
		durationSeconds = Math.max(0.01, durationSeconds);
		heightMultiplier = Math.max(0f, heightMultiplier);
		sectionIndex = Math.max(-1, sectionIndex);
		timingSnap = timingSnap != null ? timingSnap : ChoreographyTimingSnap.BAR;
	}
}
