package com.beatblock.automap.choreography;

import com.beatblock.automap.engine.SectionType;
import com.beatblock.engine.BuildSequenceMode;
import com.beatblock.timeline.generation.PacingMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildSequenceProtectedMergeTest {

	@Test
	void overlapsProtectedRangeWhenWindowCoversLockedSection() {
		List<double[]> protectedRanges = List.of(new double[] {20.0, 25.0});
		assertTrue(ChoreographyStructureMerger.overlapsProtectedRange(10.0, 30.0, protectedRanges));
	}

	@Test
	void mergeKeepsOverlappingExistingBuildSequence() {
		ChoreographyPlan existing = planWith(
			List.of(
				new ChoreographyPlan.SectionPlan(0, 20, SectionType.VERSE, "verse"),
				new ChoreographyPlan.SectionPlan(20, 25, SectionType.DROP, "drop")
					.markedLocked()
			),
			List.of(new BuildSequencePlan(
				"layer-1", "stage-1", 10.0, 30.0,
				BuildSequenceMode.WALL, PacingMode.FIXED_INTERVAL, false, null, 0
			))
		);
		ChoreographyPlan analyzed = planWith(
			List.of(
				new ChoreographyPlan.SectionPlan(0, 20, SectionType.VERSE, "verse"),
				new ChoreographyPlan.SectionPlan(20, 25, SectionType.DROP, "drop")
			),
			List.of(new BuildSequencePlan(
				"layer-1", "stage-1", 12.0, 28.0,
				BuildSequenceMode.WALL, PacingMode.FIXED_INTERVAL, false, null, 0
			))
		);

		List<BuildSequencePlan> merged = ChoreographyStructureMerger.mergeBuildSequences(existing, analyzed);
		assertEquals(1, merged.size());
		assertEquals(10.0, merged.getFirst().startSeconds(), 1e-9);
		assertEquals(30.0, merged.getFirst().endSeconds(), 1e-9);
	}

	private static ChoreographyPlan planWith(
		List<ChoreographyPlan.SectionPlan> sections,
		List<BuildSequencePlan> sequences
	) {
		return new ChoreographyPlan(
			sections,
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(0.5),
			List.of(),
			ChoreographyPlan.MusicalStructure.empty(),
			List.of(),
			List.of(),
			sequences
		);
	}
}
