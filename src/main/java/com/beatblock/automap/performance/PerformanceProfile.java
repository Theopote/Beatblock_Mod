package com.beatblock.automap.performance;

import com.beatblock.automap.choreography.ChoreographyLayerProfile;
import com.beatblock.automap.engine.AutoMapSettings;
import com.beatblock.automap.engine.AutoMapStyle;
import com.beatblock.automap.engine.Complexity;

import org.jspecify.annotations.Nullable;

/**
 * 表演导演配置：{@link com.beatblock.creator.CreationPreset} 的生成语义，而不只是 UI 开关。
 */
public record PerformanceProfile(
	AutoMapStyle styleHint,
	Complexity complexityHint,
	LayerIntensity accent,
	LayerIntensity phrase,
	LayerIntensity hero,
	LayerIntensity build,
	LayerIntensity vfx,
	CameraTemperament camera,
	EventDensityPolicy density,
	boolean heroSectionEntrancesOnly
) {

	public PerformanceProfile {
		styleHint = styleHint != null ? styleHint : AutoMapStyle.EDM;
		complexityHint = complexityHint != null ? complexityHint : Complexity.MEDIUM;
		accent = accent != null ? accent : LayerIntensity.MEDIUM;
		phrase = phrase != null ? phrase : LayerIntensity.MEDIUM;
		hero = hero != null ? hero : LayerIntensity.OFF;
		build = build != null ? build : LayerIntensity.OFF;
		vfx = vfx != null ? vfx : LayerIntensity.OFF;
		camera = camera != null ? camera : CameraTemperament.OFF;
		density = density != null ? density : EventDensityPolicy.SECTION_AWARE;
	}

	/** 拍点纹理：Accent 主导，几乎无 Phrase/Hero/Camera。 */
	public static PerformanceProfile rhythmPulse() {
		return new PerformanceProfile(
			AutoMapStyle.EDM,
			Complexity.HIGH,
			LayerIntensity.HIGH,
			LayerIntensity.LOW,
			LayerIntensity.OFF,
			LayerIntensity.OFF,
			LayerIntensity.OFF,
			CameraTemperament.OFF,
			EventDensityPolicy.BEAT_HEAVY,
			false
		);
	}

	/** 完整舞台：Phrase/Hero/Camera/VFX 全面。 */
	public static PerformanceProfile fullChoreography() {
		return new PerformanceProfile(
			AutoMapStyle.EDM,
			Complexity.MEDIUM,
			LayerIntensity.MEDIUM,
			LayerIntensity.HIGH,
			LayerIntensity.HIGH,
			LayerIntensity.OFF,
			LayerIntensity.MEDIUM,
			CameraTemperament.ENERGETIC,
			EventDensityPolicy.SECTION_AWARE,
			false
		);
	}

	/** 建造揭示：Build 主轴 + 慢电影镜头，不做粒子轰炸。 */
	public static PerformanceProfile buildReveal() {
		return new PerformanceProfile(
			AutoMapStyle.CINEMATIC,
			Complexity.LOW,
			LayerIntensity.LOW,
			LayerIntensity.LOW,
			LayerIntensity.VERY_LOW,
			LayerIntensity.PRIMARY,
			LayerIntensity.OFF,
			CameraTemperament.SLOW_CINEMATIC,
			EventDensityPolicy.SPARSE_CINEMATIC,
			true
		);
	}

	public boolean wantsCamera() {
		return camera.isEnabled();
	}

	public boolean wantsVfx() {
		return vfx.isEnabled();
	}

	public boolean wantsBuildPrimary() {
		return build.isPrimary();
	}

	public ChoreographyLayerProfile toLayerProfile() {
		if (!phrase.isEnabled() && !hero.isEnabled()) {
			return ChoreographyLayerProfile.ACCENT_ONLY;
		}
		if (!hero.isEnabled() || hero == LayerIntensity.VERY_LOW || hero == LayerIntensity.LOW) {
			return ChoreographyLayerProfile.PHRASE;
		}
		return ChoreographyLayerProfile.HERO_FULL;
	}

	public AutoMapSettings toAutoMapSettings(String objectId, @Nullable String buildLayerId) {
		AutoMapSettings settings = new AutoMapSettings();
		settings.setStyle(styleHint);
		settings.setComplexity(complexityHint);
		settings.setCameraEnabled(wantsCamera());
		settings.setParticlesEnabled(wantsVfx());
		settings.setLayerProfile(toLayerProfile());
		settings.setPerformanceProfile(this);
		if (objectId != null && !objectId.isBlank()) {
			settings.setTargetObjectIds(java.util.List.of(objectId));
		}
		if (wantsBuildPrimary() && buildLayerId != null && !buildLayerId.isBlank()) {
			settings.setBuildLayerId(buildLayerId);
		}
		applyDensityGaps(settings);
		return settings;
	}

	private void applyDensityGaps(AutoMapSettings settings) {
		PatternDensity.Gaps gaps = PatternDensity.gapsFor(density);
		settings.setMinGapLow(gaps.low());
		settings.setMinGapMid(gaps.mid());
		settings.setMinGapHigh(gaps.high());
	}
}
