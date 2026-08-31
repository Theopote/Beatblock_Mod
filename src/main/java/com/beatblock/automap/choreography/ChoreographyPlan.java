package com.beatblock.automap.choreography;

import com.beatblock.automap.engine.SectionType;

import java.util.List;

/**
 * 编舞计划：音频分析/结构段落与 Timeline 草稿之间的中间表示。
 * <p>
 * 流程：Audio Analysis → Musical Structure → {@link ChoreographyPlan} → Timeline Draft → 用户编辑。
 */
public record ChoreographyPlan(
	List<SectionPlan> sections,
	List<StageRoleAssignment> stageRoles,
	List<MotionPhrase> motionPhrases,
	List<CameraPhrase> cameraPhrases,
	List<VfxPhrase> vfxPhrases,
	DensityCurve densityCurve,
	List<SectionEditProfile> sectionEdits
) {

	public ChoreographyPlan(
		List<SectionPlan> sections,
		List<StageRoleAssignment> stageRoles,
		List<MotionPhrase> motionPhrases,
		List<CameraPhrase> cameraPhrases,
		List<VfxPhrase> vfxPhrases,
		DensityCurve densityCurve
	) {
		this(sections, stageRoles, motionPhrases, cameraPhrases, vfxPhrases, densityCurve, List.of());
	}

	public ChoreographyPlan {
		sections = sections != null ? List.copyOf(sections) : List.of();
		stageRoles = stageRoles != null ? List.copyOf(stageRoles) : List.of();
		motionPhrases = motionPhrases != null ? List.copyOf(motionPhrases) : List.of();
		cameraPhrases = cameraPhrases != null ? List.copyOf(cameraPhrases) : List.of();
		vfxPhrases = vfxPhrases != null ? List.copyOf(vfxPhrases) : List.of();
		densityCurve = densityCurve != null ? densityCurve : DensityCurve.uniform(1.0);
		sectionEdits = sectionEdits != null ? List.copyOf(sectionEdits) : List.of();
	}

	public static ChoreographyPlan empty() {
		return new ChoreographyPlan(
			List.of(), List.of(), List.of(), List.of(), List.of(), DensityCurve.uniform(1.0), List.of());
	}

	public record SectionPlan(
		double startSeconds,
		double endSeconds,
		SectionType sectionType,
		String label
	) {
		public SectionPlan {
			label = label != null ? label : "";
		}

		public double durationSeconds() {
			return Math.max(0.0, endSeconds - startSeconds);
		}
	}

	public record StageRoleAssignment(String normalizedFeatureKey, String targetObjectId) {}

	public record MotionPhrase(
		double timeSeconds,
		String trackKey,
		String normalizedFeatureKey,
		float energy,
		String animationTypeId,
		double durationSeconds,
		boolean useEnergyForHeight,
		float heightMultiplier,
		int sectionIndex
	) {
		public MotionPhrase(
			double timeSeconds,
			String trackKey,
			String normalizedFeatureKey,
			float energy,
			String animationTypeId,
			double durationSeconds,
			boolean useEnergyForHeight,
			float heightMultiplier
		) {
			this(
				timeSeconds,
				trackKey,
				normalizedFeatureKey,
				energy,
				animationTypeId,
				durationSeconds,
				useEnergyForHeight,
				heightMultiplier,
				-1
			);
		}

		public MotionPhrase {
			trackKey = trackKey != null ? trackKey : "";
			normalizedFeatureKey = normalizedFeatureKey != null ? normalizedFeatureKey : "";
			animationTypeId = animationTypeId != null ? animationTypeId : "";
			durationSeconds = Math.max(0.01, durationSeconds);
			heightMultiplier = Math.max(0f, heightMultiplier);
			sectionIndex = Math.max(-1, sectionIndex);
		}
	}

	public record CameraPhrase(double timeSeconds, String action, int sectionIndex) {
		public CameraPhrase(double timeSeconds, String action) {
			this(timeSeconds, action, -1);
		}

		public CameraPhrase {
			action = action != null ? action : "";
			sectionIndex = Math.max(-1, sectionIndex);
		}
	}

	public record VfxPhrase(double timeSeconds, String vfxKind, int sectionIndex) {
		public VfxPhrase(double timeSeconds, String vfxKind) {
			this(timeSeconds, vfxKind, -1);
		}

		public VfxPhrase {
			vfxKind = vfxKind != null ? vfxKind : "";
			sectionIndex = Math.max(-1, sectionIndex);
		}
	}
}
