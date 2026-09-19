package com.beatblock.automap.choreography;

import com.beatblock.automap.engine.SectionType;
import com.beatblock.engine.BuildSequenceMode;
import com.beatblock.timeline.generation.PacingMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class BuildRevealPlannerTest {

	@Test
	void prefersBuildSectionWindow() {
		ChoreographyPlan plan = planWithSections(
			new ChoreographyPlan.SectionPlan(0, 8, SectionType.VERSE, "verse"),
			new ChoreographyPlan.SectionPlan(8, 16, SectionType.BUILD, "build"),
			new ChoreographyPlan.SectionPlan(16, 24, SectionType.DROP, "drop")
		);

		BuildSequencePlan sequence = BuildRevealPlanner.plan(plan, "layer-1", "stage-1");

		assertNotNull(sequence);
		assertEquals("layer-1", sequence.layerId());
		assertEquals("stage-1", sequence.targetObjectId());
		assertEquals(8.0, sequence.startSeconds(), 1e-9);
		assertEquals(16.0, sequence.endSeconds(), 1e-9);
		assertEquals(1, sequence.sectionIndex());
		assertEquals(BuildSequenceMode.WALL, sequence.mode());
		assertEquals(PacingMode.FIXED_INTERVAL, sequence.pacing());
	}

	@Test
	void usesDropLeadInWhenNoBuildSection() {
		ChoreographyPlan plan = planWithSections(
			new ChoreographyPlan.SectionPlan(0, 20, SectionType.VERSE, "verse"),
			new ChoreographyPlan.SectionPlan(20, 28, SectionType.DROP, "drop")
		);

		BuildSequencePlan sequence = BuildRevealPlanner.plan(plan, "layer-1", "stage-1");

		assertNotNull(sequence);
		assertEquals(12.0, sequence.startSeconds(), 1e-9);
		assertEquals(20.0, sequence.endSeconds(), 1e-9);
		assertEquals(1, sequence.sectionIndex());
	}

	@Test
	void returnsNullWhenLayerIdMissing() {
		ChoreographyPlan plan = planWithSections(
			new ChoreographyPlan.SectionPlan(0, 8, SectionType.BUILD, "build")
		);
		assertNull(BuildRevealPlanner.plan(plan, "  ", "stage-1"));
		assertNull(BuildRevealPlanner.plan(plan, null, "stage-1"));
	}

	private static ChoreographyPlan planWithSections(ChoreographyPlan.SectionPlan... sections) {
		return new ChoreographyPlan(
			List.of(sections),
			List.of(),
			List.of(),
			List.of(),
			List.of(),
			DensityCurve.uniform(1.0)
		);
	}
}
