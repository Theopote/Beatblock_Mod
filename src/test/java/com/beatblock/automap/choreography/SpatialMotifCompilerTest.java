package com.beatblock.automap.choreography;

import com.beatblock.test.WithBeatBlockContext;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@WithBeatBlockContext
class SpatialMotifCompilerTest {

	@Test
	void cascadeDelaysAlongXAxisOrder() {
		SpatialMotifPhrase phrase = new SpatialMotifPhrase(
			1.0,
			SpatialMotifId.CASCADE,
			List.of("left", "center", "right"),
			MotifAxis.X,
			0.1,
			"pulse",
			0.8f,
			0.5,
			0
		);
		SpatialMotifLayout layout = new SpatialMotifLayout(Map.of(
			"left", new Vec3d(-4, 0, 0),
			"center", new Vec3d(0, 0, 0),
			"right", new Vec3d(4, 0, 0)
		));

		List<SpatialMotifCompiler.ExpandedEvent> events = SpatialMotifCompiler.expand(phrase, layout);

		assertEquals(3, events.size());
		assertEquals("left", events.get(0).targetObjectId());
		assertEquals(1.0, events.get(0).timeSeconds(), 1e-9);
		assertEquals("center", events.get(1).targetObjectId());
		assertEquals(1.1, events.get(1).timeSeconds(), 1e-9);
		assertEquals("right", events.get(2).targetObjectId());
		assertEquals(1.2, events.get(2).timeSeconds(), 1e-9);
	}

	@Test
	void convergeTriggersOuterObjectsBeforeCenter() {
		SpatialMotifPhrase phrase = new SpatialMotifPhrase(
			2.0,
			SpatialMotifId.CONVERGE,
			List.of("center", "outer-a", "outer-b"),
			MotifAxis.RADIAL,
			0.08,
			"pulse",
			0.7f,
			0.4,
			0
		);
		SpatialMotifLayout layout = new SpatialMotifLayout(Map.of(
			"center", new Vec3d(0, 0, 0),
			"outer-a", new Vec3d(8, 0, 0),
			"outer-b", new Vec3d(-6, 0, 0)
		));

		List<SpatialMotifCompiler.ExpandedEvent> events = SpatialMotifCompiler.expand(phrase, layout);

		assertEquals(3, events.size());
		assertEquals("outer-a", events.get(0).targetObjectId());
		assertEquals("outer-b", events.get(1).targetObjectId());
		assertEquals("center", events.get(2).targetObjectId());
		assertTrue(events.get(0).timeSeconds() < events.get(2).timeSeconds());
	}

	@Test
	void alternateOffsetsOddParticipants() {
		SpatialMotifPhrase phrase = new SpatialMotifPhrase(
			0.0,
			SpatialMotifId.ALTERNATE,
			List.of("a", "b", "c"),
			MotifAxis.X,
			0.12,
			"pulse",
			MotifPhaseMode.ALTERNATE,
			1.0f,
			0.5,
			true,
			4f,
			0,
			ChoreographyTimingSnap.NONE
		);
		SpatialMotifLayout layout = SpatialMotifLayout.synthetic(List.of("a", "b", "c"), MotifAxis.X);

		List<SpatialMotifCompiler.ExpandedEvent> events = SpatialMotifCompiler.expand(phrase, layout);

		assertEquals(0.0, events.get(0).timeSeconds(), 1e-9);
		assertEquals(0.12, events.get(1).timeSeconds(), 1e-9);
		assertEquals(0.0, events.get(2).timeSeconds(), 1e-9);
		assertEquals(0.75f, events.get(1).energy(), 1e-6f);
	}

	@Test
	void requiresAtLeastTwoParticipants() {
		SpatialMotifPhrase phrase = new SpatialMotifPhrase(
			1.0,
			SpatialMotifId.CASCADE,
			List.of("solo"),
			MotifAxis.X,
			0.1,
			"pulse",
			0.8f,
			0.5,
			0
		);

		assertTrue(SpatialMotifCompiler.expand(phrase, SpatialMotifLayout.synthetic(List.of("solo"), MotifAxis.X)).isEmpty());
	}

	@Test
	void rippleExpandsFromCenterOutwardWithDistanceBasedDelay() {
		SpatialMotifPhrase phrase = new SpatialMotifPhrase(
			0.0,
			SpatialMotifId.RIPPLE,
			List.of("center", "mid", "outer"),
			MotifAxis.RADIAL,
			0.1,
			"pulse",
			1.0f,
			0.5,
			0
		);
		SpatialMotifLayout layout = new SpatialMotifLayout(Map.of(
			"center", new Vec3d(5, 0, 0),
			"mid", new Vec3d(2, 0, 0),
			"outer", new Vec3d(8, 0, 0)
		));

		List<SpatialMotifCompiler.ExpandedEvent> events = SpatialMotifCompiler.expand(phrase, layout);

		assertEquals("center", events.get(0).targetObjectId());
		assertEquals("outer", events.get(2).targetObjectId());
		assertEquals(0.0, events.get(0).timeSeconds(), 1e-9);
		assertTrue(events.get(2).timeSeconds() > events.get(0).timeSeconds());
		assertTrue(events.get(2).energy() < events.get(0).energy());
	}

	@Test
	void sweepUsesAxisSpanForUnevenSpacing() {
		SpatialMotifPhrase phrase = new SpatialMotifPhrase(
			1.0,
			SpatialMotifId.SWEEP,
			List.of("near", "far"),
			MotifAxis.X,
			0.2,
			"pulse",
			0.8f,
			0.5,
			0
		);
		SpatialMotifLayout layout = new SpatialMotifLayout(Map.of(
			"near", new Vec3d(0, 0, 0),
			"far", new Vec3d(10, 0, 0)
		));

		List<SpatialMotifCompiler.ExpandedEvent> events = SpatialMotifCompiler.expand(phrase, layout);

		assertEquals("near", events.get(0).targetObjectId());
		assertEquals(1.0, events.get(0).timeSeconds(), 1e-9);
		assertEquals(1.2, events.get(1).timeSeconds(), 1e-9);
	}

	@Test
	void chaseKeepsLeaderFirstThenFollowsAlongAxis() {
		SpatialMotifPhrase phrase = new SpatialMotifPhrase(
			0.0,
			SpatialMotifId.CHASE,
			List.of("leader", "follower-b", "follower-a"),
			MotifAxis.X,
			0.1,
			"pulse",
			1.0f,
			0.5,
			0
		);
		SpatialMotifLayout layout = new SpatialMotifLayout(Map.of(
			"leader", new Vec3d(5, 0, 0),
			"follower-a", new Vec3d(1, 0, 0),
			"follower-b", new Vec3d(8, 0, 0)
		));

		List<SpatialMotifCompiler.ExpandedEvent> events = SpatialMotifCompiler.expand(phrase, layout);

		assertEquals("leader", events.get(0).targetObjectId());
		assertEquals("follower-a", events.get(1).targetObjectId());
		assertEquals("follower-b", events.get(2).targetObjectId());
		assertTrue(events.get(2).timeSeconds() > events.get(1).timeSeconds());
	}

	@Test
	void spiralOrdersByAngleAroundCentroid() {
		SpatialMotifPhrase phrase = new SpatialMotifPhrase(
			0.0,
			SpatialMotifId.SPIRAL,
			List.of("east", "north", "west"),
			MotifAxis.RADIAL,
			0.1,
			"pulse",
			0.8f,
			0.5,
			0
		);
		SpatialMotifLayout layout = new SpatialMotifLayout(Map.of(
			"east", new Vec3d(4, 0, 0),
			"north", new Vec3d(0, 0, -4),
			"west", new Vec3d(-4, 0, 0)
		));

		List<SpatialMotifCompiler.ExpandedEvent> events = SpatialMotifCompiler.expand(phrase, layout);

		assertEquals(3, events.size());
		assertEquals("east", events.get(0).targetObjectId());
		assertEquals("north", events.get(1).targetObjectId());
		assertEquals("west", events.get(2).targetObjectId());
	}

	@Test
	void explodeBoostsEnergyOnOutermostParticipant() {
		SpatialMotifPhrase phrase = new SpatialMotifPhrase(
			0.0,
			SpatialMotifId.EXPLODE,
			List.of("center", "outer"),
			MotifAxis.RADIAL,
			0.1,
			"pulse",
			0.8f,
			0.5,
			0
		);
		SpatialMotifLayout layout = new SpatialMotifLayout(Map.of(
			"center", new Vec3d(0, 0, 0),
			"outer", new Vec3d(6, 0, 0)
		));

		List<SpatialMotifCompiler.ExpandedEvent> events = SpatialMotifCompiler.expand(phrase, layout);

		assertEquals("center", events.get(0).targetObjectId());
		assertEquals("outer", events.get(1).targetObjectId());
		assertTrue(events.get(1).energy() > events.get(0).energy());
	}
}
