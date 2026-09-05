package com.beatblock.automap.choreography;

import com.beatblock.automap.engine.SectionType;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 重分析时合并音乐结构：保留 {@link SectionPlanSource#USER_EDITED} 与 {@link SectionPlanSource#LOCKED} 段落，
 * 其余时间区间用最新算法结果填充；同时对齐 {@link ChoreographyPlan.MusicalStructure}
 *（beat/bar 用新分析，protected 区间内的 musical phrase 保留旧结果）。
 */
public final class ChoreographyStructureMerger {

	private ChoreographyStructureMerger() {}

	public static ChoreographyPlan merge(@Nullable ChoreographyPlan existing, ChoreographyPlan analyzed) {
		if (analyzed == null) return existing != null ? existing : ChoreographyPlan.empty();
		if (existing == null) return analyzed;

		List<ChoreographyPlan.SectionPlan> mergedSections = mergeSections(
			existing.sections(), analyzed.sections());
		ChoreographyPlan.MusicalStructure mergedMusic = mergeMusicalStructure(
			existing.musicalStructure(),
			analyzed.musicalStructure(),
			existing.sections(),
			mergedSections
		);
		ChoreographyPlan merged = new ChoreographyPlan(
			mergedSections,
			existing.stageRoles().isEmpty() ? analyzed.stageRoles() : existing.stageRoles(),
			mergeMotionPhrases(existing, analyzed, mergedSections),
			mergeCameraPhrases(existing, analyzed, mergedSections),
			mergeVfxPhrases(existing, analyzed, mergedSections),
			rebuildDensityCurve(mergedSections),
			existing.sectionEdits(),
			mergedMusic,
			mergeSpatialMotifPhrases(existing, analyzed, mergedSections),
			mergeChoreographyPhrases(existing, analyzed, mergedSections)
		);
		return ChoreographyPlanEditor.rebindSectionIndices(merged);
	}

	public static ChoreographyPlan mergeStructureOnly(@Nullable ChoreographyPlan existing, ChoreographyPlan analyzed) {
		if (analyzed == null) return existing != null ? existing : ChoreographyPlan.empty();
		if (existing == null) return analyzed;

		List<ChoreographyPlan.SectionPlan> mergedSections = mergeSections(
			existing.sections(), analyzed.sections());
		ChoreographyPlan.MusicalStructure mergedMusic = mergeMusicalStructure(
			existing.musicalStructure(),
			analyzed.musicalStructure(),
			existing.sections(),
			mergedSections
		);
		return new ChoreographyPlan(
			mergedSections,
			existing.stageRoles().isEmpty() ? analyzed.stageRoles() : existing.stageRoles(),
			existing.motionPhrases(),
			existing.cameraPhrases(),
			existing.vfxPhrases(),
			rebuildDensityCurve(mergedSections),
			existing.sectionEdits(),
			mergedMusic,
			existing.spatialMotifPhrases(),
			existing.choreographyPhrases()
		);
	}

	/**
	 * 将分析得到的 MusicalStructure 与 protected Section 对齐：
	 * <ul>
	 *   <li>beatTimes / bars：采用新分析结果，并按 {@code mergedSections} 重绑 sectionIndex</li>
	 *   <li>phrases：落在 LOCKED / USER_EDITED 区间内的保留旧短语，其余用新分析；再重绑 sectionIndex 并重建 RepeatGroup</li>
	 * </ul>
	 * 保证 {@link ChoreographyPlan#sections()} 与 {@code musicalStructure} 的 section 归属不互相矛盾。
	 */
	static ChoreographyPlan.MusicalStructure mergeMusicalStructure(
		ChoreographyPlan.@Nullable MusicalStructure existing,
		ChoreographyPlan.@Nullable MusicalStructure analyzed,
		List<ChoreographyPlan.SectionPlan> existingSections,
		List<ChoreographyPlan.SectionPlan> mergedSections
	) {
		ChoreographyPlan.MusicalStructure existingMusic =
			existing != null ? existing : ChoreographyPlan.MusicalStructure.empty();
		ChoreographyPlan.MusicalStructure analyzedMusic =
			analyzed != null ? analyzed : ChoreographyPlan.MusicalStructure.empty();

		if (analyzedMusic.isEmpty()) {
			return rebindMusicalStructure(existingMusic, mergedSections);
		}
		if (existingMusic.isEmpty() || !hasProtectedSection(existingSections)) {
			return rebindMusicalStructure(analyzedMusic, mergedSections);
		}

		List<ChoreographyPlan.BarPlan> bars = rebindBars(analyzedMusic.bars(), mergedSections);
		List<ChoreographyPlan.MusicalPhrasePlan> phrases = mergeMusicalPhrases(
			existingMusic.phrases(),
			analyzedMusic.phrases(),
			existingSections,
			mergedSections
		);
		List<ChoreographyPlan.MusicalPhrasePlan> annotated = RepeatGroupBuilder.annotateRepeatAnchors(phrases);
		List<ChoreographyPlan.RepeatGroup> repeats = RepeatGroupBuilder.buildFromAnnotated(annotated);
		return new ChoreographyPlan.MusicalStructure(bars, annotated, repeats, analyzedMusic.beatTimes());
	}

	private static ChoreographyPlan.MusicalStructure rebindMusicalStructure(
		ChoreographyPlan.MusicalStructure structure,
		List<ChoreographyPlan.SectionPlan> mergedSections
	) {
		if (structure == null || structure.isEmpty()) {
			return ChoreographyPlan.MusicalStructure.empty();
		}
		List<ChoreographyPlan.BarPlan> bars = rebindBars(structure.bars(), mergedSections);
		List<ChoreographyPlan.MusicalPhrasePlan> phrases = rebindMusicalPhrases(structure.phrases(), mergedSections);
		List<ChoreographyPlan.MusicalPhrasePlan> annotated = RepeatGroupBuilder.annotateRepeatAnchors(phrases);
		List<ChoreographyPlan.RepeatGroup> repeats = RepeatGroupBuilder.buildFromAnnotated(annotated);
		return new ChoreographyPlan.MusicalStructure(bars, annotated, repeats, structure.beatTimes());
	}

	private static List<ChoreographyPlan.BarPlan> rebindBars(
		List<ChoreographyPlan.BarPlan> bars,
		List<ChoreographyPlan.SectionPlan> mergedSections
	) {
		if (bars == null || bars.isEmpty()) return List.of();
		List<ChoreographyPlan.BarPlan> out = new ArrayList<>(bars.size());
		for (ChoreographyPlan.BarPlan bar : bars) {
			double mid = (bar.startSeconds() + bar.endSeconds()) * 0.5;
			out.add(new ChoreographyPlan.BarPlan(
				bar.startSeconds(),
				bar.endSeconds(),
				bar.barIndex(),
				MusicalStructureMapper.resolveSectionIndex(mergedSections, mid)
			));
		}
		return out;
	}

	private static List<ChoreographyPlan.MusicalPhrasePlan> mergeMusicalPhrases(
		List<ChoreographyPlan.MusicalPhrasePlan> existingPhrases,
		List<ChoreographyPlan.MusicalPhrasePlan> analyzedPhrases,
		List<ChoreographyPlan.SectionPlan> existingSections,
		List<ChoreographyPlan.SectionPlan> mergedSections
	) {
		List<double[]> protectedRanges = protectedTimeRanges(existingSections);
		List<ChoreographyPlan.MusicalPhrasePlan> merged = new ArrayList<>();
		for (ChoreographyPlan.MusicalPhrasePlan phrase : existingPhrases) {
			if (phraseMidInProtectedRange(phrase, protectedRanges)) {
				merged.add(phrase);
			}
		}
		for (ChoreographyPlan.MusicalPhrasePlan phrase : analyzedPhrases) {
			if (!phraseMidInProtectedRange(phrase, protectedRanges)) {
				merged.add(phrase);
			}
		}
		merged.sort(Comparator.comparingDouble(ChoreographyPlan.MusicalPhrasePlan::startSeconds));
		return reindexAndRebindMusicalPhrases(merged, mergedSections);
	}

	private static List<ChoreographyPlan.MusicalPhrasePlan> rebindMusicalPhrases(
		List<ChoreographyPlan.MusicalPhrasePlan> phrases,
		List<ChoreographyPlan.SectionPlan> mergedSections
	) {
		if (phrases == null || phrases.isEmpty()) return List.of();
		return reindexAndRebindMusicalPhrases(phrases, mergedSections);
	}

	private static List<ChoreographyPlan.MusicalPhrasePlan> reindexAndRebindMusicalPhrases(
		List<ChoreographyPlan.MusicalPhrasePlan> phrases,
		List<ChoreographyPlan.SectionPlan> mergedSections
	) {
		List<ChoreographyPlan.MusicalPhrasePlan> out = new ArrayList<>(phrases.size());
		for (int i = 0; i < phrases.size(); i++) {
			ChoreographyPlan.MusicalPhrasePlan phrase = phrases.get(i);
			double mid = (phrase.startSeconds() + phrase.endSeconds()) * 0.5;
			out.add(new ChoreographyPlan.MusicalPhrasePlan(
				phrase.startSeconds(),
				phrase.endSeconds(),
				i,
				MusicalStructureMapper.resolveSectionIndex(mergedSections, mid),
				phrase.repetitionScore(),
				-1
			));
		}
		return out;
	}

	private static boolean phraseMidInProtectedRange(
		ChoreographyPlan.MusicalPhrasePlan phrase,
		List<double[]> protectedRanges
	) {
		double mid = (phrase.startSeconds() + phrase.endSeconds()) * 0.5;
		return timeInProtectedRange(mid, protectedRanges);
	}

	static List<ChoreographyPlan.SectionPlan> mergeSections(
		List<ChoreographyPlan.SectionPlan> existing,
		List<ChoreographyPlan.SectionPlan> analyzed
	) {
		if (analyzed == null || analyzed.isEmpty()) {
			return existing != null ? List.copyOf(existing) : List.of();
		}
		if (existing == null || existing.isEmpty()) {
			return List.copyOf(analyzed);
		}

		List<ChoreographyPlan.SectionPlan> preserved = existing.stream()
			.filter(ChoreographyPlan.SectionPlan::isProtected)
			.sorted(Comparator.comparingDouble(ChoreographyPlan.SectionPlan::startSeconds))
			.toList();
		if (preserved.isEmpty()) {
			return List.copyOf(analyzed);
		}

		double timelineEnd = Math.max(maxEnd(existing), maxEnd(analyzed));
		List<ChoreographyPlan.SectionPlan> merged = new ArrayList<>();
		double cursor = 0.0;
		for (ChoreographyPlan.SectionPlan protectedSection : preserved) {
			appendAnalyzedInRange(merged, analyzed, cursor, protectedSection.startSeconds());
			merged.add(protectedSection);
			cursor = Math.max(cursor, protectedSection.endSeconds());
		}
		appendAnalyzedInRange(merged, analyzed, cursor, timelineEnd);
		return normalize(merged);
	}

	private static void appendAnalyzedInRange(
		List<ChoreographyPlan.SectionPlan> out,
		List<ChoreographyPlan.SectionPlan> analyzed,
		double rangeStart,
		double rangeEnd
	) {
		if (rangeEnd - rangeStart < ChoreographyPlanEditor.MIN_SECTION_DURATION_SECONDS) return;
		for (ChoreographyPlan.SectionPlan section : analyzed) {
			double clipStart = Math.max(rangeStart, section.startSeconds());
			double clipEnd = Math.min(rangeEnd, section.endSeconds());
			if (clipEnd - clipStart < ChoreographyPlanEditor.MIN_SECTION_DURATION_SECONDS) continue;
			out.add(new ChoreographyPlan.SectionPlan(
				clipStart,
				clipEnd,
				section.sectionType(),
				section.label(),
				section.confidence(),
				SectionPlanSource.ANALYZED
			));
		}
	}

	private static List<ChoreographyPlan.SectionPlan> normalize(List<ChoreographyPlan.SectionPlan> sections) {
		if (sections.isEmpty()) return List.of();
		List<ChoreographyPlan.SectionPlan> sorted = new ArrayList<>(sections);
		sorted.sort(Comparator.comparingDouble(ChoreographyPlan.SectionPlan::startSeconds));
		List<ChoreographyPlan.SectionPlan> out = new ArrayList<>();
		for (ChoreographyPlan.SectionPlan section : sorted) {
			if (section.endSeconds() - section.startSeconds() < ChoreographyPlanEditor.MIN_SECTION_DURATION_SECONDS) {
				continue;
			}
			if (!out.isEmpty()) {
				ChoreographyPlan.SectionPlan last = out.getLast();
				if (sectionsOverlap(last, section)) {
					if (last.isProtected() && !section.isProtected()) continue;
					if (!last.isProtected() && section.isProtected()) {
						out.set(out.size() - 1, section);
						continue;
					}
					if (last.isProtected() && section.isProtected()) continue;
				}
			}
			out.add(section);
		}
		return List.copyOf(out);
	}

	private static boolean sectionsOverlap(
		ChoreographyPlan.SectionPlan left,
		ChoreographyPlan.SectionPlan right
	) {
		return left.startSeconds() < right.endSeconds() && right.startSeconds() < left.endSeconds();
	}

	private static double maxEnd(List<ChoreographyPlan.SectionPlan> sections) {
		double max = 0.0;
		for (ChoreographyPlan.SectionPlan section : sections) {
			max = Math.max(max, section.endSeconds());
		}
		return max;
	}

	private static List<ChoreographyPlan.MotionPhrase> mergeMotionPhrases(
		ChoreographyPlan existing,
		ChoreographyPlan analyzed,
		List<ChoreographyPlan.SectionPlan> mergedSections
	) {
		return mergePhrases(
			existing.sections(),
			existing.motionPhrases(),
			analyzed.motionPhrases(),
			ChoreographyPlan.MotionPhrase::sectionIndex,
			ChoreographyPlan.MotionPhrase::timeSeconds
		);
	}

	private static List<ChoreographyPlan.CameraPhrase> mergeCameraPhrases(
		ChoreographyPlan existing,
		ChoreographyPlan analyzed,
		List<ChoreographyPlan.SectionPlan> mergedSections
	) {
		return mergePhrases(
			existing.sections(),
			existing.cameraPhrases(),
			analyzed.cameraPhrases(),
			ChoreographyPlan.CameraPhrase::sectionIndex,
			ChoreographyPlan.CameraPhrase::timeSeconds
		);
	}

	private static List<ChoreographyVfx> mergeVfxPhrases(
		ChoreographyPlan existing,
		ChoreographyPlan analyzed,
		List<ChoreographyPlan.SectionPlan> mergedSections
	) {
		return mergePhrases(
			existing.sections(),
			existing.vfxPhrases(),
			analyzed.vfxPhrases(),
			ChoreographyVfx::sectionIndex,
			ChoreographyVfx::timeSeconds
		);
	}

	private static List<SpatialMotifPhrase> mergeSpatialMotifPhrases(
		ChoreographyPlan existing,
		ChoreographyPlan analyzed,
		List<ChoreographyPlan.SectionPlan> mergedSections
	) {
		return mergePhrases(
			existing.sections(),
			existing.spatialMotifPhrases(),
			analyzed.spatialMotifPhrases(),
			SpatialMotifPhrase::sectionIndex,
			SpatialMotifPhrase::timeSeconds
		);
	}

	private static List<com.beatblock.automap.choreography.grammar.ChoreographyPhrase> mergeChoreographyPhrases(
		ChoreographyPlan existing,
		ChoreographyPlan analyzed,
		List<ChoreographyPlan.SectionPlan> mergedSections
	) {
		return mergeGrammarPhrasesBySection(
			existing.sections(),
			existing.choreographyPhrases(),
			analyzed.sections(),
			analyzed.choreographyPhrases()
		);
	}

	/**
	 * Grammar Phrase 没有单点时间，不能用 fake {@code 0.0s} 做 protected 判断。
	 * 按 {@code sectionIndex}：保留落在 existing protected section 的旧短语；
	 * 丢弃其 analyzed section 与 protected 时间区间重叠的新短语。
	 */
	static List<com.beatblock.automap.choreography.grammar.ChoreographyPhrase> mergeGrammarPhrasesBySection(
		List<ChoreographyPlan.SectionPlan> existingSections,
		List<com.beatblock.automap.choreography.grammar.ChoreographyPhrase> existingPhrases,
		List<ChoreographyPlan.SectionPlan> analyzedSections,
		List<com.beatblock.automap.choreography.grammar.ChoreographyPhrase> analyzedPhrases
	) {
		List<ChoreographyPlan.SectionPlan> existing =
			existingSections != null ? existingSections : List.of();
		List<com.beatblock.automap.choreography.grammar.ChoreographyPhrase> keepExisting =
			existingPhrases != null ? existingPhrases : List.of();
		List<ChoreographyPlan.SectionPlan> analyzed =
			analyzedSections != null ? analyzedSections : List.of();
		List<com.beatblock.automap.choreography.grammar.ChoreographyPhrase> keepAnalyzed =
			analyzedPhrases != null ? analyzedPhrases : List.of();

		if (!hasProtectedSection(existing)) {
			return List.copyOf(keepAnalyzed);
		}

		List<double[]> protectedRanges = protectedTimeRanges(existing);
		List<com.beatblock.automap.choreography.grammar.ChoreographyPhrase> merged = new ArrayList<>();
		for (com.beatblock.automap.choreography.grammar.ChoreographyPhrase phrase : keepExisting) {
			if (phraseInProtectedSection(
				phrase, existing, com.beatblock.automap.choreography.grammar.ChoreographyPhrase::sectionIndex
			)) {
				merged.add(phrase);
			}
		}
		for (com.beatblock.automap.choreography.grammar.ChoreographyPhrase phrase : keepAnalyzed) {
			if (!analyzedGrammarPhraseConflictsWithProtected(phrase, analyzed, protectedRanges)) {
				merged.add(phrase);
			}
		}
		return List.copyOf(merged);
	}

	private static boolean analyzedGrammarPhraseConflictsWithProtected(
		com.beatblock.automap.choreography.grammar.ChoreographyPhrase phrase,
		List<ChoreographyPlan.SectionPlan> analyzedSections,
		List<double[]> protectedRanges
	) {
		int index = phrase.sectionIndex();
		if (index < 0 || index >= analyzedSections.size()) {
			return false;
		}
		ChoreographyPlan.SectionPlan section = analyzedSections.get(index);
		return section != null && sectionOverlapsProtectedRange(section, protectedRanges);
	}

	private static boolean sectionOverlapsProtectedRange(
		ChoreographyPlan.SectionPlan section,
		List<double[]> protectedRanges
	) {
		for (double[] range : protectedRanges) {
			if (section.startSeconds() < range[1] && range[0] < section.endSeconds()) {
				return true;
			}
		}
		return false;
	}

	private static <T> List<T> mergePhrases(
		List<ChoreographyPlan.SectionPlan> existingSections,
		List<T> existingPhrases,
		List<T> analyzedPhrases,
		java.util.function.ToIntFunction<T> sectionIndexFn,
		java.util.function.ToDoubleFunction<T> timeFn
	) {
		List<ChoreographyPlan.SectionPlan> sections = existingSections != null ? existingSections : List.of();
		List<T> existing = existingPhrases != null ? existingPhrases : List.of();
		List<T> analyzed = analyzedPhrases != null ? analyzedPhrases : List.of();
		if (!hasProtectedSection(sections)) {
			return List.copyOf(analyzed);
		}

		List<double[]> protectedRanges = protectedTimeRanges(sections);
		List<T> merged = new ArrayList<>();
		for (T phrase : existing) {
			if (phraseInProtectedSection(phrase, sections, sectionIndexFn)) {
				merged.add(phrase);
			}
		}
		for (T phrase : analyzed) {
			if (!timeInProtectedRange(timeFn.applyAsDouble(phrase), protectedRanges)) {
				merged.add(phrase);
			}
		}
		return List.copyOf(merged);
	}

	private static boolean hasProtectedSection(List<ChoreographyPlan.SectionPlan> sections) {
		for (ChoreographyPlan.SectionPlan section : sections) {
			if (section != null && section.isProtected()) return true;
		}
		return false;
	}

	private static List<double[]> protectedTimeRanges(List<ChoreographyPlan.SectionPlan> sections) {
		List<double[]> ranges = new ArrayList<>();
		for (ChoreographyPlan.SectionPlan section : sections) {
			if (section != null && section.isProtected()) {
				ranges.add(new double[] { section.startSeconds(), section.endSeconds() });
			}
		}
		return ranges;
	}

	private static <T> boolean phraseInProtectedSection(
		T phrase,
		List<ChoreographyPlan.SectionPlan> sections,
		java.util.function.ToIntFunction<T> sectionIndexFn
	) {
		int index = sectionIndexFn.applyAsInt(phrase);
		if (index < 0 || index >= sections.size()) return false;
		ChoreographyPlan.SectionPlan section = sections.get(index);
		return section != null && section.isProtected();
	}

	private static boolean timeInProtectedRange(double timeSeconds, List<double[]> protectedRanges) {
		for (double[] range : protectedRanges) {
			if (timeSeconds >= range[0] && timeSeconds < range[1]) {
				return true;
			}
		}
		return false;
	}

	private static DensityCurve rebuildDensityCurve(List<ChoreographyPlan.SectionPlan> sections) {
		if (sections.isEmpty()) return DensityCurve.uniform(1.0);
		List<DensityCurve.Point> points = new ArrayList<>();
		for (ChoreographyPlan.SectionPlan section : sections) {
			double density = ChoreographyBudget.sectionVisualDensity(section.sectionType());
			points.add(new DensityCurve.Point(section.startSeconds(), density));
		}
		return DensityCurve.ofPoints(points);
	}
}
