package com.beatblock.automap.camera;

import com.beatblock.automap.cast.StageCast;
import com.beatblock.automap.cast.StageRole;
import com.beatblock.automap.choreography.BuildSequencePlan;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.automap.engine.StructuralSection;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * 按 Section + StageCast + Build + 上次焦点选择镜头主体，替代 sectionIndex 轮转。
 */
public final class CameraSubjectPlanner {

	private CameraSubjectPlanner() {}

	public record Resolution(
		CameraSubject primary,
		CameraSubject framingHintSubject,
		@Nullable String focusedObjectId
	) {}

	public static Resolution resolve(
		@Nullable StructuralSection section,
		int sectionIndex,
		@Nullable StageCast cast,
		@Nullable List<BuildSequencePlan> buildSequences,
		@Nullable String previousFocusedObjectId,
		@Nullable CameraPlanningContext fallbackContext
	) {
		SectionType type = section != null ? section.getType() : SectionType.VERSE;
		StageCast safeCast = cast != null ? cast : StageCast.empty();

		BuildSequencePlan activeBuild = findBuildCovering(section, buildSequences);
		if (activeBuild != null && activeBuild.layerId() != null && !activeBuild.layerId().isBlank()) {
			CameraSubject buildSubject = CameraSubject.buildLayer(activeBuild.layerId());
			return new Resolution(buildSubject, buildSubject, activeBuild.targetObjectId());
		}

		if (type == SectionType.INTRO || type == SectionType.OUTRO) {
			CameraSubject overview = overview(fallbackContext);
			return new Resolution(overview, overview, null);
		}

		String preferred = preferredObjectId(type, safeCast);
		preferred = applyRepetitionPenalty(preferred, previousFocusedObjectId, safeCast, type);
		if (preferred == null || preferred.isBlank()) {
			if (fallbackContext != null) {
				CameraSubject legacy = fallbackContext.subjectForSection(sectionIndex, false);
				return new Resolution(legacy, legacy, fallbackContext.primaryStageObjectId());
			}
			CameraSubject all = CameraSubject.allStageObjects();
			return new Resolution(all, all, null);
		}
		CameraSubject subject = CameraSubject.stageObject(preferred);
		return new Resolution(subject, subject, preferred);
	}

	private static @Nullable String preferredObjectId(SectionType type, StageCast cast) {
		return switch (type) {
			case DROP, CHORUS -> firstNonBlank(
				cast.primaryHeroId(),
				cast.firstIdWithRole(StageRole.LEAD),
				cast.firstIdWithRole(StageRole.SUPPORT)
			);
			case BUILD, PRE_CHORUS -> firstNonBlank(
				cast.primaryHeroId(),
				cast.firstIdWithRole(StageRole.LEAD)
			);
			case VERSE -> firstNonBlank(
				cast.firstIdWithRole(StageRole.LEAD),
				cast.primaryHeroId(),
				cast.firstIdWithRole(StageRole.SUPPORT)
			);
			case BREAK, BRIDGE -> firstNonBlank(
				cast.firstIdWithRole(StageRole.SUPPORT),
				cast.firstIdWithRole(StageRole.ACCENT),
				cast.firstIdWithRole(StageRole.LEAD),
				cast.primaryHeroId()
			);
			default -> cast.primaryHeroId();
		};
	}

	private static @Nullable String applyRepetitionPenalty(
		@Nullable String preferred,
		@Nullable String previous,
		StageCast cast,
		SectionType type
	) {
		if (preferred == null || previous == null || !preferred.equals(previous)) {
			return preferred;
		}
		// Climax sections keep HERO focus even if previous section also used HERO
		if (type == SectionType.DROP || type == SectionType.CHORUS) {
			return preferred;
		}
		// 连续同一焦点：尝试换到同段次选角色
		String alternate = switch (type) {
			case VERSE -> firstNonBlank(
				cast.primaryHeroId(),
				cast.firstIdWithRole(StageRole.SUPPORT)
			);
			case BREAK, BRIDGE -> firstNonBlank(
				cast.firstIdWithRole(StageRole.ACCENT),
				cast.firstIdWithRole(StageRole.LEAD),
				cast.primaryHeroId()
			);
			default -> firstNonBlank(
				cast.firstIdWithRole(StageRole.SUPPORT),
				cast.firstIdWithRole(StageRole.ACCENT)
			);
		};
		if (alternate != null && !alternate.equals(previous)) {
			return alternate;
		}
		return preferred;
	}

	private static @Nullable BuildSequencePlan findBuildCovering(
		@Nullable StructuralSection section,
		@Nullable List<BuildSequencePlan> buildSequences
	) {
		if (section == null || buildSequences == null || buildSequences.isEmpty()) {
			return null;
		}
		double mid = (section.getStartSeconds() + section.getEndSeconds()) * 0.5;
		for (BuildSequencePlan sequence : buildSequences) {
			if (sequence == null || !sequence.isValid()) {
				continue;
			}
			if (mid >= sequence.startSeconds() && mid <= sequence.endSeconds()) {
				return sequence;
			}
		}
		return null;
	}

	private static CameraSubject overview(@Nullable CameraPlanningContext context) {
		return context != null ? context.overviewSubject() : CameraSubject.allStageObjects();
	}

	private static @Nullable String firstNonBlank(String... ids) {
		if (ids == null) {
			return null;
		}
		for (String id : ids) {
			if (id != null && !id.isBlank()) {
				return id;
			}
		}
		return null;
	}
}
