package com.beatblock.client;

import com.beatblock.automap.vfx.ActiveGlobalEffectState;
import com.beatblock.automap.vfx.EnvironmentLightingRuntime;
import com.beatblock.client.export.ExportVfxState;
import com.beatblock.client.render.GlobalVisualEffectOverlay;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.playback.CompiledGlobalEvent;
import com.beatblock.timeline.playback.CompiledTimelineSnapshot;
import com.beatblock.timeline.playback.GlobalEventExecutor;
import com.beatblock.timeline.playback.GlobalEventPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Client presentation surface for global VFX / weather / stem mix.
 * Separated from {@link BeatBlockClientDriver} so playback orchestration does not own overlay details.
 */
public final class PresentationStateCoordinator {

	private final Supplier<BeatBlockContext> contextSource;
	private final BooleanSupplier exportIsolated;
	private final GlobalEventExecutor globalEventExecutor;

	public PresentationStateCoordinator(
		Supplier<BeatBlockContext> contextSource,
		BooleanSupplier exportIsolated
	) {
		this.contextSource = contextSource != null ? contextSource : () -> null;
		this.exportIsolated = exportIsolated != null ? exportIsolated : () -> false;
		this.globalEventExecutor = createGlobalEventExecutor();
	}

	public GlobalEventExecutor globalEventExecutor() {
		return globalEventExecutor;
	}

	/** Clear screen overlays + environment lighting before reconstruct. */
	public void clear() {
		if (exportIsolated.getAsBoolean()) {
			return;
		}
		GlobalVisualEffectOverlay.clear();
		EnvironmentLightingRuntime.clear();
	}

	/**
	 * Sample-at-time sync for continuous/envelope screen VFX and sticky environment lighting
	 * (seek + forward expiry). Particle impulses are never reconstructed here.
	 */
	public void syncStateful(@Nullable CompiledTimelineSnapshot program, double timeSeconds) {
		if (exportIsolated.getAsBoolean()) {
			return;
		}
		if (program == null) {
			GlobalVisualEffectOverlay.clearScreenTint();
			GlobalVisualEffectOverlay.clearScreenFlash();
			EnvironmentLightingRuntime.clear();
			return;
		}
		ActiveGlobalEffectState active = ActiveGlobalEffectState.resolve(program.globalEvents(), timeSeconds);
		CompiledGlobalEvent lightingEvent = active.environmentLighting();
		if (lightingEvent != null && lightingEvent.payload() instanceof GlobalEventPayload.EnvironmentLighting lighting) {
			EnvironmentLightingRuntime.sync(lighting);
			GlobalVisualEffectOverlay.syncEnvironmentLighting(lighting);
		} else if (lightingEvent != null && lightingEvent.payload() instanceof GlobalEventPayload.Lighting legacy) {
			var lighting = new GlobalEventPayload.EnvironmentLighting(
				legacy.name(), legacy.intensity(), legacy.r(), legacy.g(), legacy.b(), 0);
			EnvironmentLightingRuntime.sync(lighting);
			GlobalVisualEffectOverlay.syncEnvironmentLighting(lighting);
		} else {
			EnvironmentLightingRuntime.clear();
			GlobalVisualEffectOverlay.syncEnvironmentLighting(null);
		}
		CompiledGlobalEvent tintEvent = active.screenTint();
		if (tintEvent != null && tintEvent.payload() instanceof GlobalEventPayload.ScreenTint tint) {
			GlobalVisualEffectOverlay.syncScreenTint(tint);
		} else {
			GlobalVisualEffectOverlay.clearScreenTint();
		}
		CompiledGlobalEvent flashEvent = active.screenFlash();
		if (flashEvent != null && flashEvent.payload() instanceof GlobalEventPayload.ScreenFlash flash) {
			GlobalVisualEffectOverlay.syncScreenFlash(flash, flashEvent.timeSeconds(), timeSeconds);
		} else {
			GlobalVisualEffectOverlay.clearScreenFlash();
		}
	}

	public GlobalEventExecutor.ExecutionResult executeCompiledGlobal(@Nullable CompiledGlobalEvent event) {
		if (event == null || exportIsolated.getAsBoolean()) {
			return new GlobalEventExecutor.ExecutionResult(false, "skipped", "export-isolated-or-null");
		}
		return globalEventExecutor.execute(event);
	}

	public void restoreExportVfxOverlays(@Nullable ExportVfxState vfx, double timelineTimeSeconds) {
		GlobalVisualEffectOverlay.clearScreenTint();
		GlobalVisualEffectOverlay.clearScreenFlash();
		if (vfx == null) {
			return;
		}
		if (vfx.activeTint() != null
			&& vfx.activeTint().payload() instanceof GlobalEventPayload.ScreenTint tint) {
			GlobalVisualEffectOverlay.syncScreenTint(tint);
		}
		if (vfx.activeFlash() != null
			&& vfx.activeFlash().payload() instanceof GlobalEventPayload.ScreenFlash flash) {
			GlobalVisualEffectOverlay.syncScreenFlash(
				flash,
				vfx.activeFlash().timeSeconds(),
				timelineTimeSeconds
			);
		}
	}

	public void restoreClientWeather(float rainGradient, float thunderGradient) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) {
			return;
		}
		client.world.setRainGradient(Math.max(0f, Math.min(1f, rainGradient)));
		client.world.setThunderGradient(Math.max(0f, Math.min(1f, thunderGradient)));
	}

	public void syncEnvironmentLighting(GlobalEventPayload.EnvironmentLighting lighting) {
		EnvironmentLightingRuntime.sync(lighting);
		GlobalVisualEffectOverlay.syncEnvironmentLighting(lighting);
	}

	private GlobalEventExecutor createGlobalEventExecutor() {
		return new GlobalEventExecutor(new GlobalEventExecutor.Backend() {
			@Override
			public boolean applyEnvironmentLighting(GlobalEventPayload.@NotNull EnvironmentLighting payload) {
				boolean ok = EnvironmentLightingRuntime.apply(payload);
				GlobalVisualEffectOverlay.syncEnvironmentLighting(payload);
				return ok;
			}

			@Override
			public boolean applyScreenTint(GlobalEventPayload.@NotNull ScreenTint payload) {
				return GlobalVisualEffectOverlay.applyScreenTint(payload);
			}

			@Override
			public boolean applyLocalVisualWeather(GlobalEventPayload.@NotNull LocalVisualWeather payload) {
				return applyClientVisualWeather(payload);
			}

			@Override
			public boolean emitParticleBurst(GlobalEventPayload.@NotNull ParticleBurst payload) {
				return emitGlobalParticles(payload);
			}

			@Override
			public boolean applyScreenFlash(GlobalEventPayload.@NotNull ScreenFlash payload) {
				return GlobalVisualEffectOverlay.applyScreenFlash(payload);
			}

			@Override
			public boolean applyAudioMix(GlobalEventPayload.@NotNull AudioMix payload) {
				return applyGlobalAudioMix(payload);
			}

			@Override
			public boolean applyEnvironmentReset(GlobalEventPayload.@NotNull EnvironmentReset payload) {
				return applyEnvironmentResetPresentation(payload);
			}
		});
	}

	private boolean applyClientVisualWeather(GlobalEventPayload.LocalVisualWeather payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) return false;
		String weather = payload.weatherType().toLowerCase(Locale.ROOT);
		boolean rain = "rain".equals(weather) || "thunder".equals(weather) || "storm".equals(weather);
		boolean thunder = "thunder".equals(weather) || "storm".equals(weather);
		client.world.setRainGradient(rain ? 1.0f : 0.0f);
		client.world.setThunderGradient(thunder ? 1.0f : 0.0f);
		return true;
	}

	private boolean applyEnvironmentResetPresentation(GlobalEventPayload.EnvironmentReset payload) {
		EnvironmentLightingRuntime.clear();
		GlobalVisualEffectOverlay.syncEnvironmentLighting(null);
		GlobalVisualEffectOverlay.clearScreenTint();
		applyClientVisualWeather(new GlobalEventPayload.LocalVisualWeather(
			payload != null ? payload.name() : "Clear", "clear", 0));
		BeatBlockContext ctx = contextSource.get();
		var mixer = ctx != null ? ctx.stemMixer() : null;
		if (mixer != null) {
			mixer.setStemVolume("master", 1f);
		}
		return true;
	}

	private boolean emitGlobalParticles(GlobalEventPayload.ParticleBurst payload) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null || client.world == null) return false;
		net.minecraft.util.math.Vec3d origin = com.beatblock.automap.vfx.VfxParticleSubjectSupport.emissionOrigin(payload);
		ParticleEffect particle = switch (payload.particleType().toLowerCase(Locale.ROOT)) {
			case "flame", "minecraft:flame" -> ParticleTypes.FLAME;
			case "crit", "minecraft:crit" -> ParticleTypes.CRIT;
			case "firework", "minecraft:firework" -> ParticleTypes.FIREWORK;
			case "end_rod", "minecraft:end_rod" -> ParticleTypes.END_ROD;
			default -> ParticleTypes.POOF;
		};
		for (int i = 0; i < payload.count(); i++) {
			double angle = i * 2.399963229728653;
			double radius = payload.spread() * (0.5 + (i % 4) * 0.125);
			double px = origin.x + Math.cos(angle) * radius;
			double py = origin.y + payload.spread() * 0.05 * (i % 3);
			double pz = origin.z + Math.sin(angle) * radius;
			double vx = Math.cos(angle) * payload.speed();
			double vy = payload.speed() * (0.4 + (i % 5) * 0.12);
			double vz = Math.sin(angle) * payload.speed();
			client.world.addParticleClient(particle, px, py, pz, vx, vy, vz);
		}
		return true;
	}

	private boolean applyGlobalAudioMix(GlobalEventPayload.AudioMix payload) {
		BeatBlockContext ctx = contextSource.get();
		var mixer = ctx != null ? ctx.stemMixer() : null;
		return mixer != null && mixer.setStemVolume(payload.channel(), payload.volume());
	}
}
