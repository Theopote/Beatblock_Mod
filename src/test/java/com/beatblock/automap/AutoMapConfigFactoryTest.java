package com.beatblock.automap;

import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.AutoMapSettingsStore;
import com.beatblock.automap.engine.Complexity;
import com.beatblock.engine.GroupSpec;
import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.test.WithBeatBlockContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@WithBeatBlockContext
class AutoMapConfigFactoryTest {

	@AfterEach
	void resetStoredSettings() {
		AutoMapSettingsStore.resetForTests();
	}

	@Test
	void fromSettingsMapsThreeTargetsToLowMidHigh() {
		AutoMapSettings settings = new AutoMapSettings();
		settings.setComplexity(Complexity.HIGH);
		settings.setTargetObjectIds(List.of("stage-a", "stage-b", "stage-c"));

		AutoMapConfig config = AutoMapConfigFactory.fromSettings(settings);

		assertEquals("stage-a", config.getTargetByNormalizedFeature().get("low"));
		assertEquals("stage-b", config.getTargetByNormalizedFeature().get("mid"));
		assertEquals("stage-c", config.getTargetByNormalizedFeature().get("high"));
	}

	@Test
	void fromSettingsAppliesCustomPerFeatureMinGaps() {
		AutoMapSettings settings = new AutoMapSettings();
		settings.setMinGapLow(0.2);
		settings.setMinGapMid(0.1);
		settings.setMinGapHigh(0.05);

		AutoMapConfig config = AutoMapConfigFactory.fromSettings(settings);

		assertEquals(0.2, config.getRules().get(0).getMinGapSeconds(), 1e-6);
		assertEquals(0.1, config.getRules().get(1).getMinGapSeconds(), 1e-6);
		assertEquals(0.05, config.getRules().get(2).getMinGapSeconds(), 1e-6);
	}

	@Test
	void forToolbarMapsRegisteredStageObjectsInOrder() {
		var engine = com.beatblock.BeatBlock.getContext().blockAnimationEngine();
		StageObjectSystem system = engine.getStageObjectSystem();
		system.clear();
		system.register(new RuntimeStageObject(
			"s-low", "Low", List.of(new BlockPos(0, 64, 0)), Vec3d.ZERO, GroupSpec.manualSnapshot()));
		system.register(new RuntimeStageObject(
			"s-mid", "Mid", List.of(new BlockPos(1, 64, 0)), Vec3d.ZERO, GroupSpec.manualSnapshot()));
		system.register(new RuntimeStageObject(
			"s-high", "High", List.of(new BlockPos(2, 64, 0)), Vec3d.ZERO, GroupSpec.manualSnapshot()));

		AutoMapConfig config = AutoMapConfigFactory.forToolbar();

		assertEquals("s-low", config.getTargetByNormalizedFeature().get("low"));
		assertEquals("s-mid", config.getTargetByNormalizedFeature().get("mid"));
		assertEquals("s-high", config.getTargetByNormalizedFeature().get("high"));
	}

	@Test
	void forToolbarPrefersStoredTargetMappingOverRegistrationOrder() {
		AutoMapSettingsStore.current().setTargetObjectIds(List.of("custom-low", "custom-mid", "custom-high"));

		var engine = com.beatblock.BeatBlock.getContext().blockAnimationEngine();
		StageObjectSystem system = engine.getStageObjectSystem();
		system.register(new RuntimeStageObject(
			"s-low", "Low", List.of(new BlockPos(0, 64, 0)), Vec3d.ZERO, GroupSpec.manualSnapshot()));
		system.register(new RuntimeStageObject(
			"s-mid", "Mid", List.of(new BlockPos(1, 64, 0)), Vec3d.ZERO, GroupSpec.manualSnapshot()));
		system.register(new RuntimeStageObject(
			"s-high", "High", List.of(new BlockPos(2, 64, 0)), Vec3d.ZERO, GroupSpec.manualSnapshot()));

		AutoMapConfig config = AutoMapConfigFactory.forToolbar();

		assertEquals("custom-low", config.getTargetByNormalizedFeature().get("low"));
		assertEquals("custom-mid", config.getTargetByNormalizedFeature().get("mid"));
		assertEquals("custom-high", config.getTargetByNormalizedFeature().get("high"));
	}
}
