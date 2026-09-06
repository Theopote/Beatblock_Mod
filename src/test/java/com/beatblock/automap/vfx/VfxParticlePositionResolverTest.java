package com.beatblock.automap.vfx;

import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.ui.i18n.BBTexts;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VfxParticlePositionResolverTest {

	private StageObjectSystem stageObjects;
	private BuildLayerManager layers;

	@BeforeEach
	void setUp() {
		stageObjects = new StageObjectSystem();
		layers = new BuildLayerManager(stageObjects);
	}

	@Test
	void prefersSelectedStageObjectCenterOverCrosshair() {
		var layer = layers.createFromSelection("Main Building", List.of(new BlockPos(0, 64, 0), new BlockPos(2, 64, 0)));
		assertNotNull(layer);
		layers.setSelectionTo(layer.getId());

		var resolved = VfxParticlePositionResolver.resolve(
			layers,
			stageObjects,
			() -> Optional.of(new Vec3d(99, 99, 99)),
			null
		);

		assertEquals(VfxParticlePositionResolver.Source.STAGE_OBJECT, resolved.source());
		assertEquals(BBTexts.get("beatblock.vfx_creator.position.center", "Main Building"), resolved.displayLabel());
		assertEquals(1.5, resolved.x(), 1e-6);
		assertEquals(64.5, resolved.y(), 1e-6);
		assertEquals(0.5, resolved.z(), 1e-6);
	}

	@Test
	void fallsBackToCrosshairThenManual() {
		var crosshair = VfxParticlePositionResolver.resolve(
			layers,
			stageObjects,
			() -> Optional.of(new Vec3d(5, 66, -2)),
			null
		);
		assertEquals(VfxParticlePositionResolver.Source.CROSSHAIR, crosshair.source());
		assertEquals(BBTexts.get("beatblock.vfx_creator.position.crosshair"), crosshair.displayLabel());

		var manual = VfxParticlePositionResolver.resolve(
			layers,
			stageObjects,
			Optional::empty,
			new VfxParticlePositionResolver.Resolved(
				VfxParticlePositionResolver.Source.MANUAL,
				"fallback",
				1, 2, 3,
				null
			)
		);
		assertEquals(VfxParticlePositionResolver.Source.MANUAL, manual.source());
		assertEquals(1.0, manual.x(), 1e-6);
	}
}
