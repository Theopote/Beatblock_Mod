package com.beatblock.automap.choreography;

import com.beatblock.automap.camera.CameraSubject;
import com.beatblock.automap.engine.ParticleEvent;
import com.beatblock.automap.engine.ParticleType;

import java.util.List;

/** 从分析/引擎事件构建 {@link ChoreographyVfx}。 */
public final class ChoreographyVfxFactory {

	private ChoreographyVfxFactory() {}

	public static ChoreographyVfx.ParticleBurst fromParticleEvent(
		ParticleEvent event,
		int sectionIndex,
		List<String> targetObjectIds
	) {
		ParticleType type = event.getType();
		return new ChoreographyVfx.ParticleBurst(
			event.getTimeSeconds(),
			type.name(),
			particleTypeId(type),
			resolveTarget(targetObjectIds),
			particleCount(type),
			0.5,
			0.04,
			sectionIndex
		);
	}

	public static ChoreographyVfx fromLegacyVfxKind(double timeSeconds, String vfxKind, int sectionIndex) {
		String kind = vfxKind != null ? vfxKind.trim().toLowerCase() : "";
		if (kind.startsWith("particle_")) {
			String suffix = kind.substring("particle_".length());
			return new ChoreographyVfx.ParticleBurst(
				timeSeconds,
				suffix,
				legacyParticleTypeId(suffix),
				CameraSubject.allStageObjects(),
				legacyParticleCount(suffix),
				0.5,
				0.04,
				sectionIndex
			);
		}
		return new ChoreographyVfx.ParticleBurst(
			timeSeconds,
			kind.isBlank() ? "poof" : kind,
			"minecraft:poof",
			CameraSubject.allStageObjects(),
			8,
			0.5,
			0.04,
			sectionIndex
		);
	}

	private static CameraSubject resolveTarget(List<String> targetObjectIds) {
		if (targetObjectIds != null) {
			for (String id : targetObjectIds) {
				if (id != null && !id.isBlank()) {
					return CameraSubject.stageObject(id);
				}
			}
		}
		return CameraSubject.allStageObjects();
	}

	private static String particleTypeId(ParticleType type) {
		return switch (type) {
			case SPARK -> "minecraft:crit";
			case DUST -> "minecraft:poof";
			case FLASH -> "minecraft:firework";
		};
	}

	private static int particleCount(ParticleType type) {
		return switch (type) {
			case SPARK -> 12;
			case DUST -> 8;
			case FLASH -> 20;
		};
	}

	private static String legacyParticleTypeId(String suffix) {
		return switch (suffix) {
			case "spark" -> "minecraft:crit";
			case "dust" -> "minecraft:poof";
			case "flash" -> "minecraft:firework";
			default -> "minecraft:poof";
		};
	}

	private static int legacyParticleCount(String suffix) {
		return switch (suffix) {
			case "spark" -> 12;
			case "dust" -> 8;
			case "flash" -> 20;
			default -> 8;
		};
	}
}
