package com.beatblock.automap.choreography;

import com.beatblock.automap.AutoMapGenerator;
import com.beatblock.automap.camera.CameraShot;
import com.beatblock.automap.camera.CameraShotMovement;
import com.beatblock.automap.engine.SectionType;

import java.util.Locale;

/** 按特征轨 / 镜头 / VFX 类型推荐默认对齐粒度。 */
public final class TimingSnapDefaults {

	private TimingSnapDefaults() {}

	public static ChoreographyTimingSnap forFeatureKey(String featureKey) {
		String normalized = AutoMapGenerator.normalizeFeatureKey(featureKey);
		return switch (normalized) {
			case "low", "kick", "bass" -> ChoreographyTimingSnap.BEAT;
			case "mid", "snare" -> ChoreographyTimingSnap.BEAT;
			case "high", "hihat", "hat" -> ChoreographyTimingSnap.NONE;
			default -> ChoreographyTimingSnap.BEAT;
		};
	}

	public static ChoreographyTimingSnap forTrackKey(String trackKey) {
		if (trackKey == null || trackKey.isBlank()) return ChoreographyTimingSnap.BEAT;
		return forFeatureKey(trackKey);
	}

	public static ChoreographyTimingSnap forCameraShot(CameraShot shot) {
		if (shot == null) return ChoreographyTimingSnap.BAR;
		return switch (shot.movement()) {
			case HOLD, ORBIT, PUSH_IN, PULL_OUT -> ChoreographyTimingSnap.PHRASE;
			case PAN, SHAKE -> ChoreographyTimingSnap.BAR;
		};
	}

	public static ChoreographyTimingSnap forSectionType(SectionType type) {
		if (type == null) return ChoreographyTimingSnap.BAR;
		return switch (type) {
			case DROP, BUILD, CHORUS -> ChoreographyTimingSnap.SECTION;
			case INTRO, OUTRO, PRE_CHORUS -> ChoreographyTimingSnap.PHRASE;
			default -> ChoreographyTimingSnap.BAR;
		};
	}

	public static ChoreographyTimingSnap forVfx(ChoreographyVfx vfx) {
		if (vfx instanceof ChoreographyVfx.ScreenFlash) {
			return ChoreographyTimingSnap.SECTION;
		}
		if (vfx instanceof ChoreographyVfx.ParticleBurst burst) {
			String name = burst.name() != null ? burst.name().toLowerCase(Locale.ROOT) : "";
			if (name.contains("drop") || name.contains("impact")) {
				return ChoreographyTimingSnap.SECTION;
			}
			return ChoreographyTimingSnap.BAR;
		}
		return ChoreographyTimingSnap.BAR;
	}

	public static ChoreographyTimingSnap forCameraMovement(CameraShotMovement movement) {
		if (movement == null) return ChoreographyTimingSnap.BAR;
		return switch (movement) {
			case HOLD, ORBIT, PUSH_IN, PULL_OUT -> ChoreographyTimingSnap.PHRASE;
			case PAN, SHAKE -> ChoreographyTimingSnap.BAR;
		};
	}
}
