package com.beatblock.ui.presenter;

import com.beatblock.automap.vfx.EnvironmentPreset;
import com.beatblock.automap.vfx.GlobalEffectKind;
import com.beatblock.automap.vfx.GlobalEventCreationRequest;
import com.beatblock.automap.vfx.GlobalEventInsertionService;
import com.beatblock.automap.vfx.VfxEffectCategory;
import com.beatblock.automap.vfx.VfxParticlePositionResolver;
import com.beatblock.automap.vfx.VfxParticleSubjectSupport;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.playback.GlobalEventPayload;
import com.beatblock.timeline.util.MusicalDurationUnit;
import com.beatblock.ui.i18n.BBTexts;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Environment & VFX Creator: typed {@link GlobalEventPayload} insert at playhead.
 */
public final class VfxCreatorPanelPresenter {

	public record ViewState(
		boolean editorReady,
		double playheadSeconds,
		double bpm,
		VfxEffectCategory category,
		GlobalEffectKind kind,
		String name,
		float intensity,
		float r,
		float g,
		float b,
		double durationSeconds,
		MusicalDurationUnit durationUnit,
		String weatherType,
		double transitionSeconds,
		MusicalDurationUnit transitionUnit,
		String particleType,
		String particlePositionLabel,
		boolean particlePositionManual,
		double particleX,
		double particleY,
		double particleZ,
		int particleCount,
		double particleSpread,
		double particleSpeed,
		String audioChannel,
		float audioVolume,
		double audioFadeSeconds,
		MusicalDurationUnit audioFadeUnit,
		String summaryLine,
		String statusMessage
	) {}

	public record InsertOutcome(boolean success, String message) {}

	private final Supplier<Timeline> timeline;
	private final Supplier<TimelineEditor> timelineEditor;
	private final Supplier<StageObjectSystem> stageObjectSystem;
	private final Supplier<BuildLayerManager> buildLayerManager;
	private final Supplier<Optional<net.minecraft.util.math.Vec3d>> crosshairSupplier;

	private VfxEffectCategory category = VfxEffectCategory.SCREEN_TINT;
	private GlobalEffectKind kind = GlobalEffectKind.SCREEN_TINT;
	private String name = "";
	private float intensity = 0.65f;
	private float r = 1f;
	private float g = 1f;
	private float b = 1f;
	private double durationSeconds = 2.0;
	private MusicalDurationUnit durationUnit = MusicalDurationUnit.SECONDS;
	private String weatherType = "clear";
	private double transitionSeconds = 1.0;
	private MusicalDurationUnit transitionUnit = MusicalDurationUnit.SECONDS;
	private String particleType = "minecraft:poof";
	private VfxParticlePositionResolver.Source particlePositionSource = VfxParticlePositionResolver.Source.CROSSHAIR;
	private String particleAnchorRef = "";
	private String particlePositionLabel = "";
	private boolean particlePositionManual = false;
	private double particleX = 0;
	private double particleY = 64;
	private double particleZ = 0;
	private int particleCount = 24;
	private double particleSpread = GlobalEventPayload.ParticleBurst.DEFAULT_SPREAD;
	private double particleSpeed = GlobalEventPayload.ParticleBurst.DEFAULT_SPEED;
	private String audioChannel = "master";
	private float audioVolume = 1f;
	private double audioFadeSeconds = 0.5;
	private MusicalDurationUnit audioFadeUnit = MusicalDurationUnit.SECONDS;
	private String statusMessage = "";

	public VfxCreatorPanelPresenter(
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor
	) {
		this(timeline, timelineEditor, () -> null, () -> null, () -> Optional.empty());
	}

	public VfxCreatorPanelPresenter(
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<StageObjectSystem> stageObjectSystem,
		Supplier<BuildLayerManager> buildLayerManager,
		Supplier<Optional<net.minecraft.util.math.Vec3d>> crosshairSupplier
	) {
		this.timeline = timeline;
		this.timelineEditor = timelineEditor;
		this.stageObjectSystem = stageObjectSystem != null ? stageObjectSystem : () -> null;
		this.buildLayerManager = buildLayerManager != null ? buildLayerManager : () -> null;
		this.crosshairSupplier = crosshairSupplier != null
			? crosshairSupplier
			: () -> Optional.empty();
	}

	public ViewState viewState() {
		if (kind == GlobalEffectKind.PARTICLE_BURST && !particlePositionManual) {
			applyResolvedPosition(resolveParticlePosition());
		}
		TimelineEditor editor = timelineEditor.get();
		Timeline tl = timeline.get();
		double bpm = resolveBpm(tl);
		boolean ready = editor != null && tl != null;
		double playhead = ready ? editor.getClock().getCurrentTimeSeconds() : 0.0;
		return new ViewState(
			ready,
			playhead,
			bpm,
			category,
			kind,
			resolvedName(),
			intensity,
			r, g, b,
			durationSeconds,
			durationUnit,
			weatherType,
			transitionSeconds,
			transitionUnit,
			particleType,
			particlePositionLabel,
			particlePositionManual,
			particleX, particleY, particleZ,
			particleCount,
			particleSpread,
			particleSpeed,
			audioChannel,
			audioVolume,
			audioFadeSeconds,
			audioFadeUnit,
			buildSummary(playhead),
			statusMessage
		);
	}

	public void setCategory(@Nullable VfxEffectCategory category) {
		if (category == null) {
			return;
		}
		this.category = category;
		if (!category.contains(kind)) {
			setKind(category.defaultKind());
		}
	}

	public void setKind(@Nullable GlobalEffectKind kind) {
		if (kind == null) {
			return;
		}
		this.kind = kind;
		this.category = VfxEffectCategory.forKind(kind);
		if (name.isBlank()) {
			name = kind.defaultName();
		}
		if (kind == GlobalEffectKind.PARTICLE_BURST && !particlePositionManual) {
			applyResolvedPosition(resolveParticlePosition());
		}
	}

	public void setName(@Nullable String name) {
		this.name = name != null ? name : "";
	}

	public void setIntensity(float intensity) {
		this.intensity = Math.max(0f, intensity);
	}

	public void setColor(float red, float green, float blue) {
		this.r = red;
		this.g = green;
		this.b = blue;
	}

	public void setDurationSeconds(double durationSeconds) {
		this.durationSeconds = Math.max(0.05, durationSeconds);
	}

	public void setDurationUnit(@Nullable MusicalDurationUnit unit) {
		this.durationUnit = unit != null ? unit : MusicalDurationUnit.SECONDS;
	}

	public void setWeatherType(@Nullable String weatherType) {
		this.weatherType = weatherType != null ? weatherType : "clear";
	}

	public void setTransitionSeconds(double transitionSeconds) {
		this.transitionSeconds = Math.max(0.0, transitionSeconds);
	}

	public void setTransitionUnit(@Nullable MusicalDurationUnit unit) {
		this.transitionUnit = unit != null ? unit : MusicalDurationUnit.SECONDS;
	}

	public void setParticleType(@Nullable String particleType) {
		this.particleType = particleType != null ? particleType : "minecraft:poof";
	}

	public void setParticlePosition(double x, double y, double z) {
		this.particleX = x;
		this.particleY = y;
		this.particleZ = z;
		this.particlePositionManual = true;
		this.particlePositionSource = VfxParticlePositionResolver.Source.MANUAL;
		this.particleAnchorRef = "";
		this.particlePositionLabel = VfxParticlePositionResolver.labelForPayload(
			new GlobalEventPayload.ParticleBurst("", particleType, x, y, z, 1, 0.5, 0.04),
			stageObjectSystem.get(),
			buildLayerManager.get()
		);
	}

	public void setParticleCount(int particleCount) {
		this.particleCount = Math.max(1, particleCount);
	}

	public void setParticleSpread(double particleSpread) {
		this.particleSpread = Math.max(0.0, particleSpread);
	}

	public void setParticleSpeed(double particleSpeed) {
		this.particleSpeed = Math.max(0.0, particleSpeed);
	}

	public void setAudioChannel(@Nullable String audioChannel) {
		this.audioChannel = audioChannel != null ? audioChannel : "master";
	}

	public void setAudioVolume(float audioVolume) {
		this.audioVolume = Math.max(0f, audioVolume);
	}

	public void setAudioFadeSeconds(double audioFadeSeconds) {
		this.audioFadeSeconds = Math.max(0.0, audioFadeSeconds);
	}

	public void setAudioFadeUnit(@Nullable MusicalDurationUnit unit) {
		this.audioFadeUnit = unit != null ? unit : MusicalDurationUnit.SECONDS;
	}

	public void refreshParticlePositionFromSelection() {
		particlePositionManual = false;
		applyResolvedPosition(resolveParticlePosition());
	}

	public void enableManualParticlePosition() {
		particlePositionManual = true;
		particlePositionSource = VfxParticlePositionResolver.Source.MANUAL;
		particleAnchorRef = "";
		particlePositionLabel = VfxParticlePositionResolver.labelForPayload(
			new GlobalEventPayload.ParticleBurst("", particleType, particleX, particleY, particleZ, 1, 0.5, 0.04),
			stageObjectSystem.get(),
			buildLayerManager.get()
		);
	}

	public InsertOutcome insertAtPlayhead() {
		Timeline tl = timeline.get();
		TimelineEditor editor = timelineEditor.get();
		if (tl == null || editor == null) {
			return fail(BBTexts.get("beatblock.common.timeline_not_initialized"));
		}
		if (kind == GlobalEffectKind.PARTICLE_BURST && !particlePositionManual) {
			applyResolvedPosition(resolveParticlePosition());
		}
		double time = editor.getClock().getCurrentTimeSeconds();
		GlobalEventPayload payload = buildPayload();
		var result = GlobalEventInsertionService.insertManual(
			tl, editor, new GlobalEventCreationRequest(time, payload));
		if (!result.written()) {
			return fail(BBTexts.get("beatblock.vfx_creator.insert_failed"));
		}
		statusMessage = BBTexts.get("beatblock.vfx_creator.inserted", kindLabel(kind));
		return new InsertOutcome(true, statusMessage);
	}

	/** Apply a multi-cue environment preset at playhead — one Undo for all components. */
	public InsertOutcome applyPreset(@Nullable String presetId) {
		Timeline tl = timeline.get();
		TimelineEditor editor = timelineEditor.get();
		if (tl == null || editor == null) {
			return fail(BBTexts.get("beatblock.common.timeline_not_initialized"));
		}
		var preset = EnvironmentPreset.find(presetId);
		if (preset.isEmpty()) {
			return fail(BBTexts.get("beatblock.vfx_creator.preset.apply_failed"));
		}
		double time = editor.getClock().getCurrentTimeSeconds();
		var result = GlobalEventInsertionService.applyPreset(tl, editor, preset.get(), time);
		if (!result.written()) {
			return fail(BBTexts.get("beatblock.vfx_creator.preset.apply_failed"));
		}
		statusMessage = BBTexts.get(
			"beatblock.vfx_creator.preset.applied",
			preset.get().displayName(),
			result.writtenCount()
		);
		return new InsertOutcome(true, statusMessage);
	}

	private GlobalEventPayload buildPayload() {
		String resolved = resolvedName();
		return switch (kind) {
			case ENVIRONMENT_LIGHTING -> new GlobalEventPayload.EnvironmentLighting(
				resolved, intensity, r, g, b, transitionSeconds);
			case SCREEN_TINT -> new GlobalEventPayload.ScreenTint(
				resolved, intensity, r, g, b, durationSeconds);
			case WEATHER -> new GlobalEventPayload.LocalVisualWeather(
				resolved, weatherType, transitionSeconds);
			case PARTICLE_BURST -> buildParticlePayload(resolved);
			case SCREEN_FLASH -> new GlobalEventPayload.ScreenFlash(
				resolved, r, g, b, durationSeconds);
			case AUDIO_MIX -> new GlobalEventPayload.AudioMix(
				resolved, audioChannel, audioVolume, audioFadeSeconds);
		};
	}

	private GlobalEventPayload.ParticleBurst buildParticlePayload(String resolved) {
		var base = new GlobalEventPayload.ParticleBurst(
			resolved, particleType,
			particleX, particleY, particleZ,
			particleCount, particleSpread, particleSpeed);
		var follow = switch (particlePositionSource) {
			case STAGE_OBJECT -> VfxParticleSubjectSupport.resolveSubject(particleAnchorRef);
			case BUILD_LAYER -> com.beatblock.automap.camera.CameraSubject.buildLayer(particleAnchorRef);
			case CROSSHAIR, MANUAL -> null;
		};
		if (follow != null) {
			return VfxParticleSubjectSupport.anchorToSubject(base, follow);
		}
		return base;
	}

	private VfxParticlePositionResolver.Resolved resolveParticlePosition() {
		VfxParticlePositionResolver.Resolved manual = new VfxParticlePositionResolver.Resolved(
			VfxParticlePositionResolver.Source.MANUAL,
			VfxParticlePositionResolver.labelForPayload(
				new GlobalEventPayload.ParticleBurst("", particleType, particleX, particleY, particleZ, 1, 0.5, 0.04),
				stageObjectSystem.get(),
				buildLayerManager.get()
			),
			particleX, particleY, particleZ,
			null
		);
		return VfxParticlePositionResolver.resolve(
			buildLayerManager.get(),
			stageObjectSystem.get(),
			crosshairSupplier,
			manual
		);
	}

	private void applyResolvedPosition(VfxParticlePositionResolver.Resolved resolved) {
		if (resolved == null) {
			return;
		}
		particlePositionSource = resolved.source();
		particleAnchorRef = resolved.anchorRef() != null ? resolved.anchorRef() : "";
		particlePositionLabel = resolved.displayLabel();
		particleX = resolved.x();
		particleY = resolved.y();
		particleZ = resolved.z();
	}

	private String resolvedName() {
		if (name != null && !name.isBlank()) {
			return name.trim();
		}
		return kind.defaultName();
	}

	private String buildSummary(double playhead) {
		return BBTexts.get("beatblock.vfx_creator.summary",
			categoryLabel(category) + " / " + kindLabel(kind),
			resolvedName(),
			String.format(Locale.ROOT, "%.2f", playhead));
	}

	private static double resolveBpm(@Nullable Timeline timeline) {
		if (timeline == null) {
			return MusicalDurationUnit.FALLBACK_BPM;
		}
		return MusicalDurationUnit.effectiveBpm(timeline.getBpm());
	}

	public static String categoryLabel(VfxEffectCategory category) {
		return switch (category) {
			case ENVIRONMENT_LIGHTING -> BBTexts.get("beatblock.vfx_creator.category.environment_lighting");
			case SCREEN_TINT -> BBTexts.get("beatblock.vfx_creator.category.screen_tint");
			case SCREEN_FLASH -> BBTexts.get("beatblock.vfx_creator.category.screen_flash");
			case WEATHER -> BBTexts.get("beatblock.vfx_creator.category.weather");
			case PARTICLES -> BBTexts.get("beatblock.vfx_creator.category.particles");
			case AUDIO -> BBTexts.get("beatblock.vfx_creator.category.audio");
		};
	}

	public static String kindLabel(GlobalEffectKind kind) {
		return switch (kind) {
			case ENVIRONMENT_LIGHTING -> BBTexts.get("beatblock.vfx_creator.kind.environment_lighting");
			case SCREEN_TINT -> BBTexts.get("beatblock.vfx_creator.kind.screen_tint");
			case WEATHER -> BBTexts.get("beatblock.vfx_creator.kind.weather");
			case PARTICLE_BURST -> BBTexts.get("beatblock.vfx_creator.kind.particles");
			case SCREEN_FLASH -> BBTexts.get("beatblock.vfx_creator.kind.screen_flash");
			case AUDIO_MIX -> BBTexts.get("beatblock.vfx_creator.kind.audio_mix");
		};
	}

	private InsertOutcome fail(String message) {
		statusMessage = message != null ? message : "";
		return new InsertOutcome(false, statusMessage);
	}
}
