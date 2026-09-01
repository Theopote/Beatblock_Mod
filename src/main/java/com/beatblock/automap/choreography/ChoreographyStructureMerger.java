package com.beatblock.automap.choreography;

import com.beatblock.automap.engine.SectionType;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 重分析时合并音乐结构：保留 {@link SectionPlanSource#USER_EDITED} 与 {@link SectionPlanSource#LOCKED} 段落，
 * 其余时间区间用最新算法结果填充。
 */
public final class ChoreographyStructureMerger {

	private ChoreographyStructureMerger() {}

	public static ChoreographyPlan merge(@Nullable ChoreographyPlan existing, ChoreographyPlan analyzed) {
		if (analyzed == null) return existing != null ? existing : ChoreographyPlan.empty();
		if (existing == null) return analyzed;

		List<ChoreographyPlan.SectionPlan> mergedSections = mergeSections(
			existing.sections(), analyzed.sections());
		return new ChoreographyPlan(
			mergedSections,
			existing.stageRoles().isEmpty() ? analyzed.stageRoles() : existing.stageRoles(),
			analyzed.motionPhrases(),
			analyzed.cameraPhrases(),
			analyzed.vfxPhrases(),
			rebuildDensityCurve(mergedSections),
			existing.sectionEdits(),
			analyzed.musicalStructure()
		);
	}

	public static ChoreographyPlan mergeStructureOnly(@Nullable ChoreographyPlan existing, ChoreographyPlan analyzed) {
		if (analyzed == null) return existing != null ? existing : ChoreographyPlan.empty();
		if (existing == null) return analyzed;

		List<ChoreographyPlan.SectionPlan> mergedSections = mergeSections(
			existing.sections(), analyzed.sections());
		return new ChoreographyPlan(
			mergedSections,
			existing.stageRoles().isEmpty() ? analyzed.stageRoles() : existing.stageRoles(),
			existing.motionPhrases(),
			existing.cameraPhrases(),
			existing.vfxPhrases(),
			rebuildDensityCurve(mergedSections),
			existing.sectionEdits(),
			analyzed.musicalStructure()
		);
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
}
