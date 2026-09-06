package com.beatblock.automap.choreography;

import com.beatblock.automap.camera.CameraSubject;

/**
 * 编舞计划中的强类型 VFX 短语，编译为 {@link com.beatblock.timeline.playback.GlobalEventPayload}。
 */
public sealed interface ChoreographyVfx permits
	ChoreographyVfx.ParticleBurst,
	ChoreographyVfx.ScreenFlash,
	ChoreographyVfx.ScreenTint,
	ChoreographyVfx.EnvironmentLighting,
	ChoreographyVfx.AudioAccent {

	double timeSeconds();

	int sectionIndex();

	ChoreographyVfx withTiming(double timeSeconds, int sectionIndex);

	record ParticleBurst(
		double timeSeconds,
		String name,
		String particleType,
		CameraSubject target,
		int count,
		double spread,
		double speed,
		int sectionIndex
	) implements ChoreographyVfx {
		public ParticleBurst {
			name = name != null ? name : "";
			particleType = particleType != null ? particleType : "minecraft:poof";
			target = target != null ? target : CameraSubject.allStageObjects();
			count = Math.max(1, count);
			spread = Math.max(0.0, spread);
			speed = Math.max(0.0, speed);
			sectionIndex = Math.max(-1, sectionIndex);
		}

		@Override
		public ChoreographyVfx withTiming(double newTimeSeconds, int newSectionIndex) {
			return new ParticleBurst(
				newTimeSeconds, name, particleType, target, count, spread, speed, newSectionIndex);
		}
	}

	record ScreenFlash(
		double timeSeconds,
		String name,
		float r,
		float g,
		float b,
		double durationSeconds,
		int sectionIndex
	) implements ChoreographyVfx {
		public ScreenFlash {
			name = name != null ? name : "";
			durationSeconds = durationSeconds > 0 ? durationSeconds : 0.1;
			sectionIndex = Math.max(-1, sectionIndex);
		}

		@Override
		public ChoreographyVfx withTiming(double newTimeSeconds, int newSectionIndex) {
			return new ScreenFlash(newTimeSeconds, name, r, g, b, durationSeconds, newSectionIndex);
		}
	}

	record ScreenTint(
		double timeSeconds,
		String name,
		double intensity,
		float r,
		float g,
		float b,
		double durationSeconds,
		int sectionIndex
	) implements ChoreographyVfx {
		public ScreenTint {
			name = name != null ? name : "";
			intensity = Math.max(0.0, intensity);
			durationSeconds = Math.max(0.0, durationSeconds);
			sectionIndex = Math.max(-1, sectionIndex);
		}

		@Override
		public ChoreographyVfx withTiming(double newTimeSeconds, int newSectionIndex) {
			return new ScreenTint(newTimeSeconds, name, intensity, r, g, b, durationSeconds, newSectionIndex);
		}
	}

	record EnvironmentLighting(
		double timeSeconds,
		String name,
		double intensity,
		float r,
		float g,
		float b,
		double transitionSeconds,
		int sectionIndex
	) implements ChoreographyVfx {
		public EnvironmentLighting {
			name = name != null ? name : "";
			intensity = Math.max(0.0, intensity);
			transitionSeconds = Math.max(0.0, transitionSeconds);
			sectionIndex = Math.max(-1, sectionIndex);
		}

		@Override
		public ChoreographyVfx withTiming(double newTimeSeconds, int newSectionIndex) {
			return new EnvironmentLighting(
				newTimeSeconds, name, intensity, r, g, b, transitionSeconds, newSectionIndex);
		}
	}

	record AudioAccent(
		double timeSeconds,
		String name,
		String channel,
		float volume,
		double fadeSeconds,
		int sectionIndex
	) implements ChoreographyVfx {
		public AudioAccent {
			name = name != null ? name : "";
			channel = channel != null && !channel.isBlank() ? channel : "master";
			volume = Math.max(0.0f, volume);
			fadeSeconds = Math.max(0.0, fadeSeconds);
			sectionIndex = Math.max(-1, sectionIndex);
		}

		@Override
		public ChoreographyVfx withTiming(double newTimeSeconds, int newSectionIndex) {
			return new AudioAccent(newTimeSeconds, name, channel, volume, fadeSeconds, newSectionIndex);
		}
	}
}
