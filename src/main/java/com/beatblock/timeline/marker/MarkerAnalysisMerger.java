package com.beatblock.timeline.marker;

import com.beatblock.timeline.MarkerEditPolicy;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.TimelineMarker;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Audio Analysis 重跑时合并 SECTION markers：
 * <ul>
 *   <li>保留非 SECTION，以及不可替换的 SECTION（MANUAL / USER_EDITED / LOCKED）</li>
 *   <li>丢弃可替换的 GENERATED SECTION</li>
 *   <li>新分析段落若与受保护 SECTION 时间邻近，则跳过（避免重复）</li>
 * </ul>
 */
public final class MarkerAnalysisMerger {

	/** 受保护 SECTION 抑制邻近新段落的窗口（秒）。 */
	public static final double PROXIMITY_SECONDS = 0.5;

	public record AnalyzedSection(double startSeconds, @Nullable String name) {
		public AnalyzedSection {
			startSeconds = Math.max(0.0, startSeconds);
			name = name != null ? name : "";
		}
	}

	private MarkerAnalysisMerger() {
	}

	public static List<TimelineMarker> merge(
		@Nullable List<TimelineMarker> existing,
		@Nullable List<AnalyzedSection> analyzed
	) {
		List<TimelineMarker> current = existing != null ? existing : List.of();
		List<AnalyzedSection> incoming = analyzed != null ? analyzed : List.of();

		List<TimelineMarker> preserved = new ArrayList<>();
		List<TimelineMarker> protectedSections = new ArrayList<>();
		for (TimelineMarker marker : current) {
			if (marker == null) {
				continue;
			}
			if (marker.getType() != MarkerType.SECTION) {
				preserved.add(marker);
				continue;
			}
			if (!MarkerEditPolicy.isReplaceableByAudioAnalysis(marker)) {
				preserved.add(marker);
				protectedSections.add(marker);
			}
		}

		if (incoming.isEmpty()) {
			return sortCopy(preserved);
		}

		for (AnalyzedSection section : incoming) {
			if (section == null) {
				continue;
			}
			if (hasProtectedNear(protectedSections, section.startSeconds())) {
				continue;
			}
			preserved.add(TimelineMarker.audioAnalysisSection(section.startSeconds(), section.name()));
		}
		return sortCopy(preserved);
	}

	static boolean hasProtectedNear(List<TimelineMarker> protectedSections, double timeSeconds) {
		for (TimelineMarker marker : protectedSections) {
			if (marker != null && Math.abs(marker.getTimeSeconds() - timeSeconds) <= PROXIMITY_SECONDS) {
				return true;
			}
		}
		return false;
	}

	private static List<TimelineMarker> sortCopy(List<TimelineMarker> markers) {
		List<TimelineMarker> out = new ArrayList<>(markers);
		out.sort(Comparator.comparingDouble(TimelineMarker::getTimeSeconds));
		return List.copyOf(out);
	}
}
