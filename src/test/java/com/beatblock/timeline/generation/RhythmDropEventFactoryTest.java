package com.beatblock.timeline.generation;

import com.beatblock.timeline.TimelineAnimationEvent;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class RhythmDropEventFactoryTest {

	@Test
	void buildCreatesPreciseLandingEvents() {
		List<BlockPos> positions = List.of(
			new BlockPos(1, 64, 2),
			new BlockPos(3, 64, 4)
		);
		List<Double> times = List.of(2.0, 3.0);

		List<TimelineAnimationEvent> events = RhythmDropEventFactory.build(
			positions, times, "anchor", 1.0, 6.0);

		assertEquals(2, events.size());

		TimelineAnimationEvent first = events.get(0);
		assertEquals("RhythmDrop", first.getAnimationTypeId());
		assertEquals("anchor", first.getTargetObjectId());
		assertEquals(1.0, first.getTimeSeconds(), 1e-9);
		assertEquals(1.0, first.getDurationSeconds(), 1e-9);

		Map<String, Object> params = first.getParameters();
		assertEquals(1, params.get("singleBlockX"));
		assertEquals(64, params.get("singleBlockY"));
		assertEquals(2, params.get("singleBlockZ"));
		assertEquals(6.0, params.get("meteorHeight"));
		assertEquals(0.0, params.get("meteorScatter"));
		assertEquals("rhythm_impact", params.get("impactVfxKind"));

		TimelineAnimationEvent second = events.get(1);
		assertEquals(2.0, second.getTimeSeconds(), 1e-9);
	}

	@Test
	void buildSkipsNullEntries() {
		List<BlockPos> positions = new java.util.ArrayList<>();
		positions.add(new BlockPos(0, 0, 0));
		positions.add(null);
		List<Double> times = new java.util.ArrayList<>();
		times.add(1.0);
		times.add(null);

		List<TimelineAnimationEvent> events = RhythmDropEventFactory.build(
			positions, times, "anchor");
		assertEquals(1, events.size());
		assertFalse(events.getFirst().getEventId().isBlank());
	}
}
