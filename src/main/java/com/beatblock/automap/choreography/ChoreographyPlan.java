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
	List<ChoreographyVfx> vfxPhrases,
	DensityCurve densityCurve,
	List<SectionEditProfile> sectionEdits,
	MusicalStructure musicalStructure
) {

	public ChoreographyPlan(
		List<SectionPlan> sections,
		List<StageRoleAssignment> stageRoles,
		List<MotionPhrase> motionPhrases,
		List<CameraPhrase> cameraPhrases,
		List<ChoreographyVfx> vfxPhrases,
		DensityCurve densityCurve
	) {
		this(sections, stageRoles, motionPhrases, cameraPhrases, vfxPhrases, densityCurve, List.of());
	}

	public ChoreographyPlan(
		List<SectionPlan> sections,
		List<StageRoleAssignment> stageRoles,
		List<MotionPhrase> motionPhrases,
		List<CameraPhrase> cameraPhrases,
		List<ChoreographyVfx> vfxPhrases,
		DensityCurve densityCurve,
		List<SectionEditProfile> sectionEdits
	) {
		this(sections, stageRoles, motionPhrases, cameraPhrases, vfxPhrases, densityCurve, sectionEdits, MusicalStructure.empty());
	}

	public ChoreographyPlan {
		sections = sections != null ? List.copyOf(sections) : List.of();
		stageRoles = stageRoles != null ? List.copyOf(stageRoles) : List.of();
		motionPhrases = motionPhrases != null ? List.copyOf(motionPhrases) : List.of();
		cameraPhrases = cameraPhrases != null ? List.copyOf(cameraPhrases) : List.of();
		vfxPhrases = vfxPhrases != null ? List.copyOf(vfxPhrases) : List.of();
		densityCurve = densityCurve != null ? densityCurve : DensityCurve.uniform(1.0);
		sectionEdits = sectionEdits != null ? List.copyOf(sectionEdits) : List.of();
		musicalStructure = musicalStructure != null ? musicalStructure : MusicalStructure.empty();
	}

	public static ChoreographyPlan empty() {
		return new ChoreographyPlan(
			List.of(), List.of(), List.of(), List.of(), List.of(), DensityCurve.uniform(1.0), List.of(),
			MusicalStructure.empty());
	}

	/** Bar / Phrase / Section / Repeat hierarchy attached to this plan (structure v2). */
	public record MusicalStructure(
		List<BarPlan> bars,
		List<MusicalPhrasePlan> phrases,
		List<RepeatGroup> repeats
	) {
		public MusicalStructure {
			bars = bars != null ? List.copyOf(bars) : List.of();
			phrases = phrases != null ? List.copyOf(phrases) : List.of();
			repeats = repeats != null ? List.copyOf(repeats) : List.of();
		}

		public static MusicalStructure empty() {
			return new MusicalStructure(List.of(), List.of(), List.of());
		}

		public boolean isEmpty() {
			return bars.isEmpty() && phrases.isEmpty() && repeats.isEmpty();
		}
	}

	public record BarPlan(double startSeconds, double endSeconds, int barIndex, int sectionIndex) {
		public BarPlan {
			sectionIndex = Math.max(-1, sectionIndex);
		}

		public double durationSeconds() {
			return Math.max(0.0, endSeconds - startSeconds);
		}
	}

	/** Musical phrase (not choreography {@link MotionPhrase}). */
	public record MusicalPhrasePlan(
		double startSeconds,
		double endSeconds,
		int phraseIndex,
		int sectionIndex,
		double repetitionScore,
		int repeatAnchorPhraseIndex
	) {
		public MusicalPhrasePlan {
			phraseIndex = Math.max(0, phraseIndex);
			sectionIndex = Math.max(-1, sectionIndex);
			repetitionScore = Math.max(0.0, Math.min(1.0, repetitionScore));
			repeatAnchorPhraseIndex = Math.max(-1, repeatAnchorPhraseIndex);
		}

		public double durationSeconds() {
			return Math.max(0.0, endSeconds - startSeconds);
		}
	}

	public record RepeatGroup(
		int repeatGroupId,
		int anchorPhraseIndex,
		List<Integer> phraseIndices,
		double similarityScore
	) {
		public RepeatGroup {
			anchorPhraseIndex = Math.max(0, anchorPhraseIndex);
			phraseIndices = phraseIndices != null ? List.copyOf(phraseIndices) : List.of();
			similarityScore = Math.max(0.0, Math.min(1.0, similarityScore));
		}
	}

	public int barIndexAt(double timeSeconds) {
		for (ChoreographyPlan.BarPlan bar : musicalStructure.bars()) {
			boolean withinEnd = bar.barIndex() == musicalStructure.bars().size() - 1
				? timeSeconds <= bar.endSeconds()
				: timeSeconds < bar.endSeconds();
			if (timeSeconds >= bar.startSeconds() && withinEnd) {
				return bar.barIndex();
			}
		}
		return -1;
	}

	public int musicalPhraseIndexAt(double timeSeconds) {
		for (ChoreographyPlan.MusicalPhrasePlan phrase : musicalStructure.phrases()) {
			boolean withinEnd = phrase.phraseIndex() == musicalStructure.phrases().size() - 1
				? timeSeconds <= phrase.endSeconds()
				: timeSeconds < phrase.endSeconds();
			if (timeSeconds >= phrase.startSeconds() && withinEnd) {
				return phrase.phraseIndex();
			}
		}
		return -1;
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

	public record CameraPhrase(
		double timeSeconds,
		String action,
		int sectionIndex,
		String subjectKind,
		String subjectRef,
		double durationSeconds,
		String framing,
		String movement,
		String easing,
		boolean beatAligned
	) {
		public CameraPhrase(double timeSeconds, String action) {
			this(timeSeconds, action, -1);
		}

		public CameraPhrase(double timeSeconds, String action, int sectionIndex) {
			this(timeSeconds, action, sectionIndex, "", "", 3.0, "", "", "", false);
		}

		public CameraPhrase {
			action = action != null ? action : "";
			subjectKind = subjectKind != null ? subjectKind : "";
			subjectRef = subjectRef != null ? subjectRef : "";
			durationSeconds = durationSeconds > 0 ? durationSeconds : 3.0;
			framing = framing != null ? framing : "";
			movement = movement != null ? movement : "";
			easing = easing != null ? easing : "";
			sectionIndex = Math.max(-1, sectionIndex);
		}
	}
}

