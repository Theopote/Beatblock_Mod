package com.beatblock.timeline.generation;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEventOrigin;

import java.util.UUID;

/** 单次生成批次：同一 {@link #generationId()} 写入的一组自动草稿。 */
public record GenerationSession(
	String generatorId,
	String generationId,
	String sourcePlanId
) {
	public GenerationSession {
		generatorId = generatorId != null ? generatorId : "";
		generationId = generationId != null ? generationId : "";
		sourcePlanId = sourcePlanId != null ? sourcePlanId : "";
	}

	public static GenerationSession create(String generatorId, Timeline timeline) {
		String planId = "";
		if (timeline != null) {
			Object projectId = timeline.getMetadata().get("projectId");
			if (projectId != null && !String.valueOf(projectId).isBlank()) {
				planId = String.valueOf(projectId).trim();
			} else {
				Object projectPath = timeline.getMetadata().get("projectPath");
				if (projectPath != null && !String.valueOf(projectPath).isBlank()) {
					planId = String.valueOf(projectPath).trim();
				}
			}
		}
		String batchId = "gen-" + UUID.randomUUID();
		return new GenerationSession(generatorId, batchId, planId);
	}

	public TimelineGenerationMetadata forPhrase(int sectionIndex, int phraseIndex) {
		return new TimelineGenerationMetadata(
			TimelineEventOrigin.GENERATED,
			generatorId,
			generationId,
			sectionIndex,
			phraseIndex,
			sourcePlanId
		);
	}
}
