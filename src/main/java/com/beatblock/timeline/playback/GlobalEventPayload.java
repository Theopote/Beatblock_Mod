package com.beatblock.timeline.playback;

import com.beatblock.automap.camera.CameraSubject;
import com.beatblock.automap.camera.CameraSubjectKind;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/** Strongly typed payload representing a compiled global / VFX track event. */
public sealed interface GlobalEventPayload {
	/**
	 * Client-only environment lighting presentation (sticky state switch).
	 * {@code transitionSeconds} is fade/transition intent — not an active lifetime.
	 */
	record EnvironmentLighting(String name, double intensity, float r, float g, float b, double transitionSeconds)
		implements GlobalEventPayload {
		public static final EnvironmentLighting NEUTRAL =
			new EnvironmentLighting("Default Lighting", 1.0, 1f, 1f, 1f, 0.0);

		public EnvironmentLighting {
			name = name != null ? name : "";
			transitionSeconds = Math.max(0.0, transitionSeconds);
		}

		public boolean isNeutral() {
			return Math.abs(intensity - 1.0) < 1e-6
				&& Math.abs(r - 1f) < 1e-6
				&& Math.abs(g - 1f) < 1e-6
				&& Math.abs(b - 1f) < 1e-6;
		}
	}
	/** Editor/screen overlay tint; does not modify Minecraft world lighting. */
	record ScreenTint(String name, double intensity, float r, float g, float b, double durationSeconds)
		implements GlobalEventPayload { public ScreenTint { name = name != null ? name : ""; } }
	/** @deprecated Use EnvironmentLighting or ScreenTint to state the intended capability. */
	@Deprecated
	record Lighting(String name, double intensity, float r, float g, float b, double durationSeconds)
		implements GlobalEventPayload { public Lighting { name = name != null ? name : ""; } }
	/** Client-only presentation weather; does not change authoritative or saved world weather. */
	record LocalVisualWeather(String name, String weatherType, double transitionSeconds)
		implements GlobalEventPayload { public LocalVisualWeather { name = name != null ? name : ""; } }
	/**
	 * Sticky clear of environment presentation: lighting → neutral, weather → clear,
	 * screen tint cleared, audio mix → master 1.0. Does not re-fire particles.
	 */
	record EnvironmentReset(String name) implements GlobalEventPayload {
		public EnvironmentReset {
			name = name != null && !name.isBlank() ? name : "Environment Reset";
		}
	}
	record ParticleBurst(
		String name,
		String particleType,
		double x,
		double y,
		double z,
		int count,
		double spread,
		double speed,
		@Nullable CameraSubjectKind followSubjectKind,
		String followSubjectRef
	) implements GlobalEventPayload {
		public static final double DEFAULT_SPREAD = 0.5;
		public static final double DEFAULT_SPEED = 0.04;

		@SuppressWarnings("NullAway")
		public ParticleBurst(
			String name,
			String particleType,
			double x,
			double y,
			double z,
			int count,
			double spread,
			double speed
		) {
			this(name, particleType, x, y, z, count, spread, speed, absentFollowKind(), "");
		}

		private static @Nullable CameraSubjectKind absentFollowKind() {
			return null;
		}

		public ParticleBurst {
			name = name != null ? name : "";
			particleType = particleType != null && !particleType.isBlank() ? particleType : "minecraft:poof";
			count = Math.max(1, count);
			spread = Math.max(0.0, spread);
			speed = Math.max(0.0, speed);
			followSubjectRef = followSubjectRef != null ? followSubjectRef : "";
		}

		public @Nullable CameraSubject followSubject() {
			if (followSubjectKind == null) {
				return null;
			}
			return switch (followSubjectKind) {
				case STAGE_OBJECT -> CameraSubject.stageObject(followSubjectRef);
				case STAGE_GROUP -> CameraSubject.stageGroup(followSubjectRef);
				case BUILD_LAYER -> CameraSubject.buildLayer(followSubjectRef);
				case ANIMATED_TARGET -> CameraSubject.animatedTarget(followSubjectRef);
				case WORLD_POSITION -> CameraSubject.worldPosition(x, y, z);
				case ALL_STAGE_OBJECTS -> CameraSubject.allStageObjects();
			};
		}
	}
	record ScreenFlash(String name, float r, float g, float b, double durationSeconds)
		implements GlobalEventPayload { public ScreenFlash { name = name != null ? name : ""; } }
	record AudioMix(String name, String channel, float volume, double fadeSeconds)
		implements GlobalEventPayload { public AudioMix { name = name != null ? name : ""; } }
	record Generic(String typeName, String name, Map<String, Object> parameters)
		implements GlobalEventPayload {
		public Generic {
			typeName = typeName != null ? typeName : "";
			name = name != null ? name : "";
			parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
		}
	}
}