package com.beatblock.ui.properties.editors;

import com.beatblock.BeatBlock;
import com.beatblock.automap.vfx.GlobalEffectPayloadUi;
import com.beatblock.automap.vfx.VfxParticlePositionResolver;
import com.beatblock.automap.vfx.VfxParticleSubjectSupport;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.playback.GlobalEventPayload;
import com.beatblock.timeline.util.MusicalDurationUnit;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.presenter.EventPropertiesRef;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.util.MusicalDurationField;
import imgui.ImGui;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.jspecify.annotations.Nullable;

/**
 * Global / VFX event property editor — decodes {@link GlobalEventPayload} and renders
 * a type-specific form (no unified kind switcher).
 */
public final class GlobalPropertyEditor {

	private static final int INPUT_BUFFER_SIZE = 128;

	private String boundRefKey;
	private GlobalEventPayload payloadTemplate = new GlobalEventPayload.ScreenTint("", 0.65, 1f, 1f, 1f, 2.0);
	private final ImString timeBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString nameBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final float[] intensity = new float[]{1f};
	private final float[] color = new float[]{1f, 1f, 1f};
	private final MusicalDurationField durationField = new MusicalDurationField();
	private final MusicalDurationField transitionField = new MusicalDurationField();
	private final MusicalDurationField audioFadeField = new MusicalDurationField();
	private final ImString weatherType = new ImString(32);
	private final ImString particleType = new ImString(64);
	private final ImDouble particleX = new ImDouble(0);
	private final ImDouble particleY = new ImDouble(64);
	private final ImDouble particleZ = new ImDouble(0);
	private final ImInt particleCount = new ImInt(24);
	private final ImDouble particleSpread = new ImDouble(GlobalEventPayload.ParticleBurst.DEFAULT_SPREAD);
	private final ImDouble particleSpeed = new ImDouble(GlobalEventPayload.ParticleBurst.DEFAULT_SPEED);
	private String particlePositionLabel = "";
	private boolean particlePositionManual = true;
	private VfxParticlePositionResolver.Source particlePositionSource = VfxParticlePositionResolver.Source.MANUAL;
	private String particleAnchorRef = "";
	private final ImString audioChannel = new ImString(32);
	private final float[] audioVolume = new float[]{1f};
	private String validationError;

	private final EventPropertiesPresenter presenter;

	public GlobalPropertyEditor() {
		this(PresenterFactories.eventPropertiesPresenter());
	}

	public GlobalPropertyEditor(EventPropertiesPresenter presenter) {
		this.presenter = presenter;
	}

	public void renderBody(EventPropertiesRef ref, Timeline timeline, TimelineEditor editor) {
		if (ref == null || ref.event() == null) {
			return;
		}

		String rk = EventPropertiesRef.refKey(ref);
		if (!rk.equals(boundRefKey)) {
			bindBuffers(ref);
		}

		double bpm = MusicalDurationUnit.effectiveBpm(timeline != null ? timeline.getBpm() : 0.0);

		ImGui.textDisabled(BBTexts.get("beatblock.event.track"));
		ImGui.sameLine();
		ImGui.text(ref.track().getName().isBlank() ? ref.track().getId() : ref.track().getName());
		ImGui.textDisabled(BBTexts.get("beatblock.event.event_id"));
		ImGui.sameLine();
		ImGui.text(ref.event().getId());
		ImGui.separator();

		boolean trackLocked = presenter.isTrackLocked(timeline, editor, ref.track().getId());
		if (trackLocked) {
			ImGui.textDisabled(BBTexts.get("beatblock.event.track_locked"));
			ImGui.separator();
			ImGui.beginDisabled();
		}

		renderPayloadHeader(payloadTemplate);
		ImGui.separator();

		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.global.name") + "##globalName", nameBuffer);

		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.start_time") + "##globalTime", timeBuffer);

		renderPayloadFields(bpm);

		if (validationError != null && !validationError.isBlank()) {
			ImGui.spacing();
			ImGui.textColored(1f, 0.45f, 0.45f, 1f, validationError);
		}

		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.common.apply") + "##globalApply", 120f, 0f)) {
			apply(ref, timeline, editor);
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.common.reset") + "##globalReset", 120f, 0f)) {
			bindBuffers(ref);
			validationError = null;
		}

		if (trackLocked) {
			ImGui.endDisabled();
		}
	}

	private static void renderPayloadHeader(GlobalEventPayload payload) {
		ImGui.textDisabled(BBTexts.get("beatblock.vfx_creator.payload_type"));
		ImGui.sameLine();
		ImGui.text(GlobalEffectPayloadUi.payloadTypeLabel(payload));
		ImGui.textDisabled(BBTexts.get("beatblock.vfx_creator.scope"));
		ImGui.sameLine();
		ImGui.textWrapped(GlobalEffectPayloadUi.scopeLabel(payload));
	}

	private void renderPayloadFields(double bpm) {
		switch (payloadTemplate) {
			case GlobalEventPayload.EnvironmentLighting ignored -> renderEnvironmentLighting(bpm);
			case GlobalEventPayload.Lighting ignored -> renderEnvironmentLighting(bpm);
			case GlobalEventPayload.EnvironmentReset ignored -> ImGui.textWrapped(
				BBTexts.get("beatblock.vfx_creator.payload.environment_reset_hint"));
			case GlobalEventPayload.ScreenTint ignored -> renderScreenTint(bpm);
			case GlobalEventPayload.LocalVisualWeather ignored -> renderWeather(bpm);
			case GlobalEventPayload.ParticleBurst ignored -> renderParticleBurst();
			case GlobalEventPayload.ScreenFlash ignored -> renderScreenFlash(bpm);
			case GlobalEventPayload.AudioMix ignored -> renderAudioMix(bpm);
			case GlobalEventPayload.Generic ignored -> ImGui.textWrapped(
				BBTexts.get("beatblock.vfx_creator.payload.generic_hint"));
		}
	}

	private void renderEnvironmentLighting(double bpm) {
		ImGui.setNextItemWidth(-1f);
		ImGui.dragFloat(BBTexts.get("beatblock.vfx_creator.intensity") + "##globalIntensity", intensity, 0.01f, 0f, 2f);
		ImGui.text(BBTexts.get("beatblock.vfx_creator.color"));
		ImGui.setNextItemWidth(-1f);
		ImGui.colorEdit3("##globalEnvColor", color);
		transitionField.render("globalEnvTransition", BBTexts.get("beatblock.vfx_creator.transition"), bpm);
	}

	private void renderScreenTint(double bpm) {
		ImGui.text(BBTexts.get("beatblock.vfx_creator.color"));
		ImGui.setNextItemWidth(-1f);
		ImGui.colorEdit3("##globalTintColor", color);
		ImGui.setNextItemWidth(-1f);
		ImGui.dragFloat(BBTexts.get("beatblock.vfx_creator.intensity") + "##globalTintIntensity", intensity, 0.01f, 0f, 2f);
		durationField.render("globalTintDuration", BBTexts.get("beatblock.vfx_creator.duration"), bpm);
	}

	private void renderScreenFlash(double bpm) {
		ImGui.text(BBTexts.get("beatblock.vfx_creator.color"));
		ImGui.setNextItemWidth(-1f);
		ImGui.colorEdit3("##globalFlashColor", color);
		durationField.render("globalFlashDuration", BBTexts.get("beatblock.vfx_creator.duration"), bpm);
	}

	private void renderWeather(double bpm) {
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.vfx_creator.weather_type") + "##globalWeather", weatherType);
		transitionField.render("globalWeatherTrans", BBTexts.get("beatblock.vfx_creator.transition"), bpm);
	}

	private void renderParticleBurst() {
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.vfx_creator.particle_type") + "##globalParticleType", particleType);
		ImGui.text(BBTexts.get("beatblock.vfx_creator.position"));
		ImGui.sameLine();
		ImGui.textWrapped(particlePositionLabel);
		if (ImGui.button(BBTexts.get("beatblock.vfx_creator.position.refresh") + "##globalParticleRefresh")) {
			applyResolvedParticlePosition(VfxParticlePositionResolver.resolve(
				layerManagerOrNull(), stageObjectSystemOrNull()));
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.vfx_creator.position.manual_edit") + "##globalParticleManual")) {
			particlePositionManual = true;
			particlePositionSource = VfxParticlePositionResolver.Source.MANUAL;
			particleAnchorRef = "";
			refreshParticlePositionLabel();
		}
		if (particlePositionManual) {
			ImGui.setNextItemWidth(-1f);
			ImGui.inputDouble("X##globalParticleX", particleX);
			ImGui.setNextItemWidth(-1f);
			ImGui.inputDouble("Y##globalParticleY", particleY);
			ImGui.setNextItemWidth(-1f);
			ImGui.inputDouble("Z##globalParticleZ", particleZ);
			refreshParticlePositionLabel();
		} else {
			ImGui.textDisabled(BBTexts.get("beatblock.vfx_creator.position.hint"));
		}
		ImGui.setNextItemWidth(-1f);
		ImGui.inputInt(BBTexts.get("beatblock.vfx_creator.count") + "##globalParticleCount", particleCount);
		particleCount.set(Math.max(1, particleCount.get()));
		ImGui.setNextItemWidth(-1f);
		ImGui.inputDouble(BBTexts.get("beatblock.vfx_creator.spread") + "##globalParticleSpread", particleSpread);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputDouble(BBTexts.get("beatblock.vfx_creator.speed") + "##globalParticleSpeed", particleSpeed);
	}

	private void applyResolvedParticlePosition(VfxParticlePositionResolver.Resolved resolved) {
		if (resolved == null) {
			return;
		}
		particlePositionManual = resolved.source() == VfxParticlePositionResolver.Source.MANUAL;
		particlePositionSource = resolved.source();
		particleAnchorRef = resolved.anchorRef() != null ? resolved.anchorRef() : "";
		particleX.set(resolved.x());
		particleY.set(resolved.y());
		particleZ.set(resolved.z());
		particlePositionLabel = resolved.displayLabel();
	}

	private void refreshParticlePositionLabel() {
		if (!particlePositionManual
			&& (particlePositionSource == VfxParticlePositionResolver.Source.STAGE_OBJECT
			|| particlePositionSource == VfxParticlePositionResolver.Source.BUILD_LAYER)) {
			particlePositionLabel = VfxParticlePositionResolver.labelForPayload(
				new GlobalEventPayload.ParticleBurst(
					"", particleType.get(),
					particleX.get(), particleY.get(), particleZ.get(),
					1, 0.5, 0.04,
					particlePositionSource == VfxParticlePositionResolver.Source.STAGE_OBJECT
						? com.beatblock.automap.camera.CameraSubjectKind.STAGE_OBJECT
						: com.beatblock.automap.camera.CameraSubjectKind.BUILD_LAYER,
					particleAnchorRef
				),
				stageObjectSystemOrNull(),
				layerManagerOrNull()
			);
			return;
		}
		if (!particlePositionManual && particlePositionSource == VfxParticlePositionResolver.Source.CROSSHAIR) {
			particlePositionLabel = BBTexts.get("beatblock.vfx_creator.position.crosshair");
			return;
		}
		particlePositionLabel = VfxParticlePositionResolver.labelForPayload(
			new GlobalEventPayload.ParticleBurst(
				"", particleType.get(),
				particleX.get(), particleY.get(), particleZ.get(),
				1, 0.5, 0.04),
			stageObjectSystemOrNull(),
			layerManagerOrNull()
		);
	}

	private void renderAudioMix(double bpm) {
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.vfx_creator.channel") + "##globalAudioChannel", audioChannel);
		ImGui.setNextItemWidth(-1f);
		ImGui.dragFloat(BBTexts.get("beatblock.vfx_creator.volume") + "##globalAudioVolume", audioVolume, 0.01f, 0f, 2f);
		audioFadeField.render("globalAudioFade", BBTexts.get("beatblock.vfx_creator.fade"), bpm);
	}

	private void apply(EventPropertiesRef ref, Timeline timeline, TimelineEditor editor) {
		if (editor == null) {
			validationError = BBTexts.get("beatblock.common.timeline_editor_not_initialized");
			return;
		}
		GlobalEventPayload payload = buildPayload();
		var result = presenter.applyGlobalPayloadEvent(
			ref,
			timeline,
			editor.getCommandManager(),
			parseDouble(timeBuffer.get(), ref.event().getTimeSeconds()),
			payload
		);
		if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
			validationError = message;
		} else {
			validationError = null;
			payloadTemplate = payload;
		}
	}

	private void bindBuffers(EventPropertiesRef ref) {
		boundRefKey = EventPropertiesRef.refKey(ref);
		var form = presenter.buildGlobalPayloadFormSnapshot(ref);
		payloadTemplate = form.payload();
		timeBuffer.set(String.valueOf(ref.event().getTimeSeconds()));
		double bpm = resolveBpm();
		bindPayload(payloadTemplate, bpm);
	}

	private void bindPayload(GlobalEventPayload payload, double bpm) {
		switch (payload) {
			case GlobalEventPayload.EnvironmentLighting v -> {
				nameBuffer.set(v.name());
				intensity[0] = (float) v.intensity();
				color[0] = v.r(); color[1] = v.g(); color[2] = v.b();
				transitionField.setFromSeconds(v.transitionSeconds(), MusicalDurationUnit.SECONDS, bpm);
			}
			case GlobalEventPayload.ScreenTint v -> {
				nameBuffer.set(v.name());
				intensity[0] = (float) v.intensity();
				color[0] = v.r(); color[1] = v.g(); color[2] = v.b();
				durationField.setFromSeconds(v.durationSeconds(), MusicalDurationUnit.SECONDS, bpm);
			}
			case GlobalEventPayload.Lighting v -> {
				nameBuffer.set(v.name());
				intensity[0] = (float) v.intensity();
				color[0] = v.r(); color[1] = v.g(); color[2] = v.b();
				transitionField.setFromSeconds(v.durationSeconds(), MusicalDurationUnit.SECONDS, bpm);
			}
			case GlobalEventPayload.EnvironmentReset v -> nameBuffer.set(v.name());
			case GlobalEventPayload.LocalVisualWeather v -> {
				nameBuffer.set(v.name());
				weatherType.set(v.weatherType());
				transitionField.setFromSeconds(v.transitionSeconds(), MusicalDurationUnit.SECONDS, bpm);
			}
			case GlobalEventPayload.ParticleBurst v -> {
				nameBuffer.set(v.name());
				particleType.set(v.particleType());
				particleX.set(v.x()); particleY.set(v.y()); particleZ.set(v.z());
				particleCount.set(v.count());
				particleSpread.set(v.spread());
				particleSpeed.set(v.speed());
				bindParticlePositionFromPayload(v);
			}
			case GlobalEventPayload.ScreenFlash v -> {
				nameBuffer.set(v.name());
				color[0] = v.r(); color[1] = v.g(); color[2] = v.b();
				durationField.setFromSeconds(v.durationSeconds(), MusicalDurationUnit.SECONDS, bpm);
			}
			case GlobalEventPayload.AudioMix v -> {
				nameBuffer.set(v.name());
				audioChannel.set(v.channel());
				audioVolume[0] = v.volume();
				audioFadeField.setFromSeconds(v.fadeSeconds(), MusicalDurationUnit.SECONDS, bpm);
			}
			case GlobalEventPayload.Generic v -> nameBuffer.set(v.name());
		}
	}

	private GlobalEventPayload buildPayload() {
		String name = nameBuffer.get().trim();
		return switch (payloadTemplate) {
			case GlobalEventPayload.EnvironmentLighting ignored ->
				new GlobalEventPayload.EnvironmentLighting(
					name, intensity[0], color[0], color[1], color[2], transitionField.seconds());
			case GlobalEventPayload.Lighting ignored ->
				new GlobalEventPayload.EnvironmentLighting(
					name, intensity[0], color[0], color[1], color[2], transitionField.seconds());
			case GlobalEventPayload.EnvironmentReset ignored ->
				new GlobalEventPayload.EnvironmentReset(name);
			case GlobalEventPayload.ScreenTint ignored ->
				new GlobalEventPayload.ScreenTint(
					name, intensity[0], color[0], color[1], color[2], durationField.seconds());
			case GlobalEventPayload.LocalVisualWeather ignored ->
				new GlobalEventPayload.LocalVisualWeather(
					name, weatherType.get().trim(), transitionField.seconds());
			case GlobalEventPayload.ParticleBurst ignored -> buildParticlePayload(name);
			case GlobalEventPayload.ScreenFlash ignored ->
				new GlobalEventPayload.ScreenFlash(
					name, color[0], color[1], color[2], durationField.seconds());
			case GlobalEventPayload.AudioMix ignored ->
				new GlobalEventPayload.AudioMix(
					name, audioChannel.get().trim(), audioVolume[0], audioFadeField.seconds());
			case GlobalEventPayload.Generic generic ->
				new GlobalEventPayload.Generic(generic.typeName(), name, generic.parameters());
		};
	}

	private GlobalEventPayload.ParticleBurst buildParticlePayload(String name) {
		var base = new GlobalEventPayload.ParticleBurst(
			name, particleType.get().trim(),
			particleX.get(), particleY.get(), particleZ.get(),
			Math.max(1, particleCount.get()),
			Math.max(0, particleSpread.get()),
			Math.max(0, particleSpeed.get()));
		return switch (particlePositionSource) {
			case STAGE_OBJECT -> VfxParticleSubjectSupport.anchorToSubject(
				base, VfxParticleSubjectSupport.resolveSubject(particleAnchorRef));
			case BUILD_LAYER -> VfxParticleSubjectSupport.anchorToSubject(
				base, com.beatblock.automap.camera.CameraSubject.buildLayer(particleAnchorRef));
			case CROSSHAIR, MANUAL -> base;
		};
	}

	private void bindParticlePositionFromPayload(GlobalEventPayload.ParticleBurst burst) {
		particlePositionLabel = VfxParticlePositionResolver.labelForPayload(
			burst, stageObjectSystemOrNull(), layerManagerOrNull());
		if (burst.followSubjectKind() == null) {
			particlePositionManual = true;
			particlePositionSource = VfxParticlePositionResolver.Source.MANUAL;
			particleAnchorRef = "";
			return;
		}
		particlePositionManual = false;
		particleAnchorRef = burst.followSubjectRef() != null ? burst.followSubjectRef() : "";
		particlePositionSource = switch (burst.followSubjectKind()) {
			case STAGE_OBJECT -> VfxParticlePositionResolver.Source.STAGE_OBJECT;
			case BUILD_LAYER -> VfxParticlePositionResolver.Source.BUILD_LAYER;
			default -> VfxParticlePositionResolver.Source.MANUAL;
		};
		if (particlePositionSource == VfxParticlePositionResolver.Source.MANUAL) {
			particlePositionManual = true;
		}
	}

	private static @Nullable StageObjectSystem stageObjectSystemOrNull() {
		try {
			var ctx = BeatBlock.getContext();
			if (ctx != null && ctx.blockAnimationEngine() != null) {
				return ctx.blockAnimationEngine().getStageObjectSystem();
			}
		} catch (Exception ignored) {
		}
		return null;
	}

	private static @Nullable BuildLayerManager layerManagerOrNull() {
		try {
			var ctx = BeatBlock.getContext();
			return ctx != null ? ctx.buildLayerManager() : null;
		} catch (Exception ignored) {
		}
		return null;
	}

	private static double resolveBpm() {
		try {
			var ctx = BeatBlock.getContext();
			if (ctx != null && ctx.timeline() != null) {
				return MusicalDurationUnit.effectiveBpm(ctx.timeline().getBpm());
			}
		} catch (Exception ignored) {
		}
		return MusicalDurationUnit.FALLBACK_BPM;
	}

	private static double parseDouble(String raw, double fallback) {
		if (raw == null || raw.isBlank()) {
			return fallback;
		}
		try {
			return Double.parseDouble(raw.trim());
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}
}
