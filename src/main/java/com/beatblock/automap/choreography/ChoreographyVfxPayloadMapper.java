package com.beatblock.automap.choreography;

import com.beatblock.automap.camera.CameraSubjectResolver;
import com.beatblock.automap.camera.CameraSubjectRole;
import com.beatblock.timeline.playback.GlobalEventPayload;
import net.minecraft.util.math.Vec3d;

/** Choreography VFX → 播放层 {@link GlobalEventPayload}。 */
public final class ChoreographyVfxPayloadMapper {

	private ChoreographyVfxPayloadMapper() {}

	public static GlobalEventPayload toPayload(ChoreographyVfx vfx) {
		if (vfx == null) {
			return new GlobalEventPayload.Generic("SPECIAL", "", java.util.Map.of());
		}
		return switch (vfx) {
			case ChoreographyVfx.ParticleBurst particle -> {
				Vec3d position = CameraSubjectResolver.resolveRequired(particle.target(), CameraSubjectRole.SUBJECT);
				yield new GlobalEventPayload.ParticleBurst(
					particle.name(),
					particle.particleType(),
					position.x,
					position.y,
					position.z,
					particle.count()
				);
			}
			case ChoreographyVfx.ScreenFlash flash -> new GlobalEventPayload.ScreenFlash(
				flash.name(), flash.r(), flash.g(), flash.b(), flash.durationSeconds());
			case ChoreographyVfx.ScreenTint tint -> new GlobalEventPayload.ScreenTint(
				tint.name(), tint.intensity(), tint.r(), tint.g(), tint.b(), tint.durationSeconds());
			case ChoreographyVfx.EnvironmentLighting lighting -> new GlobalEventPayload.EnvironmentLighting(
				lighting.name(),
				lighting.intensity(),
				lighting.r(),
				lighting.g(),
				lighting.b(),
				lighting.durationSeconds());
			case ChoreographyVfx.AudioAccent accent -> new GlobalEventPayload.AudioMix(
				accent.name(), accent.channel(), accent.volume(), accent.fadeSeconds());
		};
	}
}
