package com.beatblock.automap.choreography;

import com.beatblock.engine.BuildSequenceMode;
import com.beatblock.timeline.generation.PacingMode;

import org.jspecify.annotations.Nullable;

/**
 * 建造揭示计划：表达 BUILD / PLACE 语义，而非动画 Phrase。
 * <p>
 * 由 {@link BuildRevealPlanner} 生成，经 {@link ChoreographyPlanCompiler} 编译为
 * BUILD_LAYER 轨道上的 Timeline BUILD 事件（对齐 {@code BindLayerToTrackCommand}）。
 */
public record BuildSequencePlan(
	String layerId,
	String targetObjectId,
	double startSeconds,
	double endSeconds,
	BuildSequenceMode mode,
	PacingMode pacing,
	boolean dissolve,
	@Nullable String trackId,
	int sectionIndex
) {

	public BuildSequencePlan(
		String layerId,
		String targetObjectId,
		double startSeconds,
		double endSeconds,
		BuildSequenceMode mode,
		PacingMode pacing,
		boolean dissolve
	) {
		this(layerId, targetObjectId, startSeconds, endSeconds, mode, pacing, dissolve, null, -1);
	}

	public BuildSequencePlan {
		layerId = layerId != null ? layerId.trim() : "";
		targetObjectId = targetObjectId != null ? targetObjectId.trim() : "";
		if (endSeconds < startSeconds) {
			double tmp = startSeconds;
			startSeconds = endSeconds;
			endSeconds = tmp;
		}
		startSeconds = Math.max(0.0, startSeconds);
		endSeconds = Math.max(startSeconds + 0.01, endSeconds);
		mode = mode != null ? mode : BuildSequenceMode.WALL;
		pacing = pacing != null ? pacing : PacingMode.FIXED_INTERVAL;
		if (trackId != null && trackId.isBlank()) {
			trackId = null;
		}
		sectionIndex = Math.max(-1, sectionIndex);
	}

	public double durationSeconds() {
		return Math.max(0.01, endSeconds - startSeconds);
	}

	public boolean isValid() {
		return !layerId.isBlank();
	}
}
