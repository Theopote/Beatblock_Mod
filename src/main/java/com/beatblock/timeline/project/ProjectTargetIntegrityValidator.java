package com.beatblock.timeline.project;

import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.StageObjectReferenceService;
import com.beatblock.timeline.Timeline;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 工程打开后校验 Timeline / AutoMap / 编舞等对 StageObject 的引用是否可解析。
 */
public final class ProjectTargetIntegrityValidator {

	public record Report(
		Set<String> missingStageObjectIds,
		StageObjectReferenceService.ReferenceSummary danglingReferences
	) {
		public Report {
			missingStageObjectIds = missingStageObjectIds != null
				? Set.copyOf(missingStageObjectIds) : Set.of();
		}

		public boolean hasBrokenReferences() {
			return !missingStageObjectIds.isEmpty();
		}

		public int danglingReferenceCount() {
			return danglingReferences != null ? danglingReferences.count() : 0;
		}

		public static Report clean() {
			return new Report(Set.of(), new StageObjectReferenceService.ReferenceSummary(java.util.List.of()));
		}
	}

	private ProjectTargetIntegrityValidator() {}

	public static Report validate(Timeline timeline, StageObjectSystem stageObjectSystem) {
		if (timeline == null || stageObjectSystem == null) {
			return Report.clean();
		}
		Set<String> referenced = StageObjectReferenceService.collectReferencedStageObjectIds(timeline);
		Set<String> missing = new LinkedHashSet<>();
		for (String id : referenced) {
			if (id != null && !id.isBlank() && stageObjectSystem.get(id) == null) {
				missing.add(id);
			}
		}
		if (missing.isEmpty()) {
			return Report.clean();
		}
		StageObjectReferenceService.ReferenceSummary dangling =
			StageObjectReferenceService.find(timeline, missing);
		return new Report(missing, dangling);
	}
}
