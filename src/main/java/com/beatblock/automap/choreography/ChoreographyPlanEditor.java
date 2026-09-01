package com.beatblock.automap.choreography;

import com.beatblock.automap.engine.SectionType;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对 {@link ChoreographyPlan} 进行 section-aware 查询与编辑。
 * <p>
 * 短语在构建时会绑定 {@code sectionIndex}；编辑器可按段落筛选、覆盖参数，或重绑段落索引。
 */
public final class ChoreographyPlanEditor {

	private ChoreographyPlanEditor() {}

	public static List<ChoreographyPlan.MotionPhrase> motionPhrasesInSection(
		ChoreographyPlan plan,
		int sectionIndex
	) {
		return filterBySection(plan.motionPhrases(), sectionIndex, ChoreographyPlan.MotionPhrase::sectionIndex);
	}

	public static List<ChoreographyPlan.CameraPhrase> cameraPhrasesInSection(
		ChoreographyPlan plan,
		int sectionIndex
	) {
		return filterBySection(plan.cameraPhrases(), sectionIndex, ChoreographyPlan.CameraPhrase::sectionIndex);
	}

	public static List<ChoreographyVfx> vfxPhrasesInSection(
		ChoreographyPlan plan,
		int sectionIndex
	) {
		return filterBySection(plan.vfxPhrases(), sectionIndex, ChoreographyVfx::sectionIndex);
	}

	public static @Nullable SectionEditProfile editForSection(ChoreographyPlan plan, int sectionIndex) {
		for (SectionEditProfile edit : plan.sectionEdits()) {
			if (edit.sectionIndex() == sectionIndex) return edit;
		}
		return null;
	}

	public static ChoreographyPlan withSectionEdit(ChoreographyPlan plan, SectionEditProfile edit) {
		if (plan == null || edit == null) return plan;
		List<SectionEditProfile> merged = new ArrayList<>(plan.sectionEdits());
		boolean replaced = false;
		for (int i = 0; i < merged.size(); i++) {
			if (merged.get(i).sectionIndex() == edit.sectionIndex()) {
				merged.set(i, edit);
				replaced = true;
				break;
			}
		}
		if (!replaced) merged.add(edit);
		return copyPlan(plan, plan.sections(), plan.stageRoles(), plan.motionPhrases(),
			plan.cameraPhrases(), plan.vfxPhrases(), plan.densityCurve(), merged);
	}

	public static ChoreographyPlan withSectionEditForType(
		ChoreographyPlan plan,
		SectionType sectionType,
		SectionEditProfile template
	) {
		if (plan == null || sectionType == null || template == null) return plan;
		ChoreographyPlan result = plan;
		for (int i = 0; i < plan.sections().size(); i++) {
			if (plan.sections().get(i).sectionType() == sectionType) {
				SectionEditProfile edit = new SectionEditProfile(
					i,
					template.motionEnabled(),
					template.cameraEnabled(),
					template.vfxEnabled(),
					template.motionAnimationTypeOverride(),
					template.densityThresholdOverride(),
					template.timeOffsetSeconds(),
					template.energyScale()
				);
				result = withSectionEdit(result, edit);
			}
		}
		return result;
	}

	/** 拖动段落边界：更新相邻 section 的 start/end，并重绑短语与密度曲线。 */
	public static ChoreographyPlan moveSectionBoundary(
		ChoreographyPlan plan,
		int boundaryIndex,
		double newTimeSeconds
	) {
		if (plan == null || boundaryIndex < 1 || boundaryIndex >= plan.sections().size()) return plan;
		List<ChoreographyPlan.SectionPlan> sections = new ArrayList<>(plan.sections());
		ChoreographyPlan.SectionPlan left = sections.get(boundaryIndex - 1);
		ChoreographyPlan.SectionPlan right = sections.get(boundaryIndex);
		double minTime = left.startSeconds() + MIN_SECTION_DURATION_SECONDS;
		double maxTime = right.endSeconds() - MIN_SECTION_DURATION_SECONDS;
		double clamped = Math.max(minTime, Math.min(maxTime, newTimeSeconds));
		sections.set(boundaryIndex - 1, new ChoreographyPlan.SectionPlan(
			left.startSeconds(), clamped, left.sectionType(), left.label()));
		sections.set(boundaryIndex, new ChoreographyPlan.SectionPlan(
			clamped, right.endSeconds(), right.sectionType(), right.label()));
		DensityCurve density = rebuildDensityCurve(sections);
		return copyPlan(
			plan,
			sections,
			plan.stageRoles(),
			rebindMotionPhrases(plan.motionPhrases(), sections),
			rebindCameraPhrases(plan.cameraPhrases(), sections),
			rebindVfxPhrases(plan.vfxPhrases(), sections),
			density,
			plan.sectionEdits()
		);
	}

	public static final double MIN_SECTION_DURATION_SECONDS = 0.5;

	/** 将段落内短语整体平移（同时更新 section 绑定）。 */
	public static ChoreographyPlan shiftSection(ChoreographyPlan plan, int sectionIndex, double deltaSeconds) {
		if (plan == null || Math.abs(deltaSeconds) < 1e-9) return plan;
		return copyPlan(
			plan,
			plan.sections(),
			plan.stageRoles(),
			transformMotionPhrases(plan, sectionIndex, deltaSeconds, null),
			transformCameraPhrases(plan, sectionIndex, deltaSeconds, null),
			transformVfxPhrases(plan, sectionIndex, deltaSeconds, null),
			plan.densityCurve(),
			plan.sectionEdits()
		);
	}

	/** 按当前 sections 列表重绑所有短语的 sectionIndex。 */
	public static ChoreographyPlan rebindSectionIndices(ChoreographyPlan plan) {
		if (plan == null) return plan;
		List<ChoreographyPlan.SectionPlan> sections = plan.sections();
		return copyPlan(
			plan,
			sections,
			plan.stageRoles(),
			rebindMotionPhrases(plan.motionPhrases(), sections),
			rebindCameraPhrases(plan.cameraPhrases(), sections),
			rebindVfxPhrases(plan.vfxPhrases(), sections),
			plan.densityCurve(),
			plan.sectionEdits()
		);
	}

	/**
	 * 将 section 编辑覆盖烘焙进短语列表（动画类型、时间偏移、能量缩放），返回新计划。
	 * 启用开关与密度门槛仍由 {@link ChoreographyPlanCompiler} 在编译阶段读取 {@code sectionEdits}。
	 */
	public static ChoreographyPlan bakePhraseOverrides(ChoreographyPlan plan) {
		if (plan == null || plan.sectionEdits().isEmpty()) return plan;
		Map<Integer, SectionEditProfile> edits = indexEdits(plan.sectionEdits());
		return copyPlan(
			plan,
			plan.sections(),
			plan.stageRoles(),
			applyMotionOverrides(plan.motionPhrases(), edits),
			applyCameraOverrides(plan.cameraPhrases(), edits),
			applyVfxOverrides(plan.vfxPhrases(), edits),
			plan.densityCurve(),
			plan.sectionEdits()
		);
	}

	static double resolveDensityThreshold(ChoreographyPlan plan, ChoreographyPlan.MotionPhrase phrase, double fallback) {
		SectionEditProfile edit = editForSection(plan, phrase.sectionIndex());
		Double override = edit != null ? edit.densityThresholdOverride() : null;
		if (override != null) {
			return override;
		}
		return fallback;
	}

	static boolean isMotionEnabled(ChoreographyPlan plan, ChoreographyPlan.MotionPhrase phrase) {
		SectionEditProfile edit = editForSection(plan, phrase.sectionIndex());
		return edit == null || edit.motionEnabled();
	}

	static boolean isCameraEnabled(ChoreographyPlan plan, ChoreographyPlan.CameraPhrase phrase) {
		SectionEditProfile edit = editForSection(plan, phrase.sectionIndex());
		return edit == null || edit.cameraEnabled();
	}

	static boolean isVfxEnabled(ChoreographyPlan plan, ChoreographyVfx phrase) {
		SectionEditProfile edit = editForSection(plan, phrase.sectionIndex());
		return edit == null || edit.vfxEnabled();
	}

	private static <T> List<T> filterBySection(
		List<T> phrases,
		int sectionIndex,
		java.util.function.ToIntFunction<T> sectionIndexFn
	) {
		List<T> out = new ArrayList<>();
		for (T phrase : phrases) {
			if (sectionIndexFn.applyAsInt(phrase) == sectionIndex) out.add(phrase);
		}
		return out;
	}

	private static Map<Integer, SectionEditProfile> indexEdits(List<SectionEditProfile> edits) {
		Map<Integer, SectionEditProfile> byIndex = new HashMap<>();
		for (SectionEditProfile edit : edits) {
			byIndex.put(edit.sectionIndex(), edit);
		}
		return byIndex;
	}

	private static List<ChoreographyPlan.MotionPhrase> applyMotionOverrides(
		List<ChoreographyPlan.MotionPhrase> phrases,
		Map<Integer, SectionEditProfile> edits
	) {
		List<ChoreographyPlan.MotionPhrase> out = new ArrayList<>(phrases.size());
		for (ChoreographyPlan.MotionPhrase phrase : phrases) {
			SectionEditProfile edit = edits.get(phrase.sectionIndex());
			if (edit == null) {
				out.add(phrase);
				continue;
			}
			String animationType = edit.motionAnimationTypeOverride() != null
				? edit.motionAnimationTypeOverride()
				: phrase.animationTypeId();
			float energy = Math.min(1f, phrase.energy() * edit.energyScale());
			out.add(new ChoreographyPlan.MotionPhrase(
				phrase.timeSeconds() + edit.timeOffsetSeconds(),
				phrase.trackKey(),
				phrase.normalizedFeatureKey(),
				energy,
				animationType,
				phrase.durationSeconds(),
				phrase.useEnergyForHeight(),
				phrase.heightMultiplier(),
				phrase.minGapSeconds(),
				phrase.sectionIndex()
			));
		}
		return out;
	}

	private static List<ChoreographyPlan.CameraPhrase> applyCameraOverrides(
		List<ChoreographyPlan.CameraPhrase> phrases,
		Map<Integer, SectionEditProfile> edits
	) {
		List<ChoreographyPlan.CameraPhrase> out = new ArrayList<>(phrases.size());
		for (ChoreographyPlan.CameraPhrase phrase : phrases) {
			SectionEditProfile edit = edits.get(phrase.sectionIndex());
			if (edit == null) {
				out.add(phrase);
				continue;
			}
			out.add(new ChoreographyPlan.CameraPhrase(
				phrase.timeSeconds() + edit.timeOffsetSeconds(),
				phrase.action(),
				phrase.sectionIndex()
			));
		}
		return out;
	}

	private static List<ChoreographyVfx> applyVfxOverrides(
		List<ChoreographyVfx> phrases,
		Map<Integer, SectionEditProfile> edits
	) {
		List<ChoreographyVfx> out = new ArrayList<>(phrases.size());
		for (ChoreographyVfx phrase : phrases) {
			SectionEditProfile edit = edits.get(phrase.sectionIndex());
			if (edit == null) {
				out.add(phrase);
				continue;
			}
			out.add(phrase.withTiming(
				phrase.timeSeconds() + edit.timeOffsetSeconds(),
				phrase.sectionIndex()
			));
		}
		return out;
	}

	private static List<ChoreographyPlan.MotionPhrase> transformMotionPhrases(
		ChoreographyPlan plan,
		int sectionIndex,
		double deltaSeconds,
		@Nullable String ignored
	) {
		List<ChoreographyPlan.MotionPhrase> out = new ArrayList<>(plan.motionPhrases().size());
		for (ChoreographyPlan.MotionPhrase phrase : plan.motionPhrases()) {
			if (phrase.sectionIndex() != sectionIndex) {
				out.add(phrase);
				continue;
			}
			out.add(new ChoreographyPlan.MotionPhrase(
				phrase.timeSeconds() + deltaSeconds,
				phrase.trackKey(),
				phrase.normalizedFeatureKey(),
				phrase.energy(),
				phrase.animationTypeId(),
				phrase.durationSeconds(),
				phrase.useEnergyForHeight(),
				phrase.heightMultiplier(),
				phrase.minGapSeconds(),
				resolveSectionIndex(plan.sections(), phrase.timeSeconds() + deltaSeconds)
			));
		}
		return out;
	}

	private static List<ChoreographyPlan.CameraPhrase> transformCameraPhrases(
		ChoreographyPlan plan,
		int sectionIndex,
		double deltaSeconds,
		@Nullable String ignored
	) {
		List<ChoreographyPlan.CameraPhrase> out = new ArrayList<>(plan.cameraPhrases().size());
		for (ChoreographyPlan.CameraPhrase phrase : plan.cameraPhrases()) {
			if (phrase.sectionIndex() != sectionIndex) {
				out.add(phrase);
				continue;
			}
			double time = phrase.timeSeconds() + deltaSeconds;
			out.add(new ChoreographyPlan.CameraPhrase(
				time,
				phrase.action(),
				resolveSectionIndex(plan.sections(), time)
			));
		}
		return out;
	}

	private static List<ChoreographyVfx> transformVfxPhrases(
		ChoreographyPlan plan,
		int sectionIndex,
		double deltaSeconds,
		@Nullable String ignored
	) {
		List<ChoreographyVfx> out = new ArrayList<>(plan.vfxPhrases().size());
		for (ChoreographyVfx phrase : plan.vfxPhrases()) {
			if (phrase.sectionIndex() != sectionIndex) {
				out.add(phrase);
				continue;
			}
			double time = phrase.timeSeconds() + deltaSeconds;
			out.add(phrase.withTiming(time, resolveSectionIndex(plan.sections(), time)));
		}
		return out;
	}

	private static List<ChoreographyPlan.MotionPhrase> rebindMotionPhrases(
		List<ChoreographyPlan.MotionPhrase> phrases,
		List<ChoreographyPlan.SectionPlan> sections
	) {
		List<ChoreographyPlan.MotionPhrase> out = new ArrayList<>(phrases.size());
		for (ChoreographyPlan.MotionPhrase phrase : phrases) {
			out.add(new ChoreographyPlan.MotionPhrase(
				phrase.timeSeconds(),
				phrase.trackKey(),
				phrase.normalizedFeatureKey(),
				phrase.energy(),
				phrase.animationTypeId(),
				phrase.durationSeconds(),
				phrase.useEnergyForHeight(),
				phrase.heightMultiplier(),
				phrase.minGapSeconds(),
				resolveSectionIndex(sections, phrase.timeSeconds())
			));
		}
		return out;
	}

	private static List<ChoreographyPlan.CameraPhrase> rebindCameraPhrases(
		List<ChoreographyPlan.CameraPhrase> phrases,
		List<ChoreographyPlan.SectionPlan> sections
	) {
		List<ChoreographyPlan.CameraPhrase> out = new ArrayList<>(phrases.size());
		for (ChoreographyPlan.CameraPhrase phrase : phrases) {
			out.add(new ChoreographyPlan.CameraPhrase(
				phrase.timeSeconds(),
				phrase.action(),
				resolveSectionIndex(sections, phrase.timeSeconds())
			));
		}
		return out;
	}

	private static List<ChoreographyVfx> rebindVfxPhrases(
		List<ChoreographyVfx> phrases,
		List<ChoreographyPlan.SectionPlan> sections
	) {
		List<ChoreographyVfx> out = new ArrayList<>(phrases.size());
		for (ChoreographyVfx phrase : phrases) {
			out.add(phrase.withTiming(
				phrase.timeSeconds(),
				resolveSectionIndex(sections, phrase.timeSeconds())
			));
		}
		return out;
	}

	private static int resolveSectionIndex(List<ChoreographyPlan.SectionPlan> sections, double timeSeconds) {
		for (int i = 0; i < sections.size(); i++) {
			ChoreographyPlan.SectionPlan section = sections.get(i);
			boolean withinEnd = i == sections.size() - 1
				? timeSeconds <= section.endSeconds()
				: timeSeconds < section.endSeconds();
			if (timeSeconds >= section.startSeconds() && withinEnd) {
				return i;
			}
		}
		return -1;
	}

	private static DensityCurve rebuildDensityCurve(List<ChoreographyPlan.SectionPlan> sections) {
		if (sections.isEmpty()) return DensityCurve.uniform(1.0);
		List<DensityCurve.Point> points = new ArrayList<>();
		for (ChoreographyPlan.SectionPlan section : sections) {
			double density = switch (section.sectionType()) {
				case INTRO, OUTRO -> 0.25;
				case VERSE, BREAK, BRIDGE -> 0.45;
				case PRE_CHORUS -> 0.55;
				case BUILD -> 0.65;
				case CHORUS -> 0.85;
				case DROP -> 0.95;
			};
			points.add(new DensityCurve.Point(section.startSeconds(), density));
		}
		return DensityCurve.ofPoints(points);
	}

	private static ChoreographyPlan copyPlan(
		ChoreographyPlan source,
		List<ChoreographyPlan.SectionPlan> sections,
		List<ChoreographyPlan.StageRoleAssignment> roles,
		List<ChoreographyPlan.MotionPhrase> motions,
		List<ChoreographyPlan.CameraPhrase> cameras,
		List<ChoreographyVfx> vfx,
		DensityCurve density,
		List<SectionEditProfile> edits
	) {
		return new ChoreographyPlan(sections, roles, motions, cameras, vfx, density, edits, source.musicalStructure());
	}
}
