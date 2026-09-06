package com.beatblock.ui.panels;

import com.beatblock.automap.vfx.EnvironmentPreset;
import com.beatblock.automap.vfx.VfxEffectCategory;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.notification.ToastNotificationSystem;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.VfxCreatorPanelPresenter;
import com.beatblock.ui.util.MusicalDurationField;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImDouble;
import imgui.type.ImInt;
import imgui.type.ImString;

/**
 * Environment & VFX Creator: category tabs, musical duration, particle position from selection.
 */
public final class VfxCreatorPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private static final VfxEffectCategory[] CATEGORIES = VfxEffectCategory.values();

	private final VfxCreatorPanelPresenter presenter;
	private final ImInt categoryIndex = new ImInt(0);
	private final ImString nameBuffer = new ImString(64);
	private final float[] intensity = new float[]{0.65f};
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
	private final ImDouble particleSpread = new ImDouble(0.5);
	private final ImDouble particleSpeed = new ImDouble(0.04);
	private final ImString audioChannel = new ImString(32);
	private final float[] audioVolume = new float[]{1f};

	public VfxCreatorPanel() {
		this(PresenterFactories.vfxCreatorPanelPresenter());
	}

	VfxCreatorPanel(VfxCreatorPanelPresenter presenter) {
		this.presenter = presenter;
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.vfxCreatorWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.vfxCreatorWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			var state = presenter.viewState();
			ImGui.text(BBTexts.get("beatblock.vfx_creator.title"));
			ImGui.separator();
			ImGui.textWrapped(state.summaryLine());
			ImGui.spacing();
			ImGui.textDisabled(BBTexts.get("beatblock.vfx_creator.scope"));
			ImGui.sameLine();
			ImGui.textWrapped(com.beatblock.automap.vfx.GlobalEffectPayloadUi.scopeLabel(state.kind()));
			ImGui.separator();
			ImGui.textWrapped(BBTexts.get("beatblock.vfx_creator.hint"));

			if (!state.editorReady()) {
				ImGui.spacing();
				ImGui.textDisabled(BBTexts.get("beatblock.common.timeline_not_initialized"));
				return;
			}

			renderPresets();
			ImGui.separator();
			renderCategoryTabs(state);
			ImGui.spacing();
			nameBuffer.set(state.name());
			ImGui.setNextItemWidth(-1f);
			ImGui.inputText(BBTexts.get("beatblock.global.name") + "##vfxCreatorName", nameBuffer);
			if (!nameBuffer.get().equals(state.name())) {
				presenter.setName(nameBuffer.get());
			}
			ImGui.spacing();
			renderKindFields(state);
			ImGui.spacing();
			if (ImGui.button(BBTexts.get("beatblock.vfx_creator.insert") + "##vfxCreatorInsert")) {
				syncFieldsToPresenter(state);
				notify(presenter.insertAtPlayhead());
			}
			if (!state.statusMessage().isBlank()) {
				ImGui.spacing();
				ImGui.textWrapped(state.statusMessage());
			}
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.vfxCreatorWindow());
		}
	}

	private void renderPresets() {
		ImGui.spacing();
		ImGui.text(BBTexts.get("beatblock.vfx_creator.presets"));
		ImGui.textDisabled(BBTexts.get("beatblock.vfx_creator.presets.hint"));
		ImGui.spacing();
		boolean first = true;
		for (EnvironmentPreset preset : EnvironmentPreset.all()) {
			if (!first) {
				ImGui.sameLine();
			}
			first = false;
			String label = BBTexts.get("beatblock.vfx_creator.preset.apply", preset.displayName())
				+ "##vfxPreset_" + preset.id();
			if (ImGui.button(label)) {
				notify(presenter.applyPreset(preset.id()));
			}
		}
	}

	private void renderCategoryTabs(VfxCreatorPanelPresenter.ViewState state) {
		for (int i = 0; i < CATEGORIES.length; i++) {
			if (state.category() == CATEGORIES[i]) {
				categoryIndex.set(i);
				break;
			}
		}
		if (ImGui.beginTabBar("##vfxCategoryTabs")) {
			for (int i = 0; i < CATEGORIES.length; i++) {
				VfxEffectCategory category = CATEGORIES[i];
				if (ImGui.beginTabItem(VfxCreatorPanelPresenter.categoryLabel(category) + "##vfxCat_" + category.name())) {
					if (state.category() != category) {
						presenter.setCategory(category);
					}
					categoryIndex.set(i);
					ImGui.endTabItem();
				}
			}
			ImGui.endTabBar();
		}
	}

	private void renderKindFields(VfxCreatorPanelPresenter.ViewState state) {
		switch (state.kind()) {
			case ENVIRONMENT_LIGHTING -> renderEnvironmentLightingFields(state);
			case SCREEN_TINT -> renderScreenTintFields(state);
			case SCREEN_FLASH -> renderFlashFields(state);
			case WEATHER -> renderWeatherFields(state);
			case PARTICLE_BURST -> renderParticleFields(state);
			case AUDIO_MIX -> renderAudioFields(state);
		}
	}

	private void renderEnvironmentLightingFields(VfxCreatorPanelPresenter.ViewState state) {
		intensity[0] = state.intensity();
		color[0] = state.r(); color[1] = state.g(); color[2] = state.b();
		ImGui.dragFloat(BBTexts.get("beatblock.vfx_creator.intensity") + "##vfxIntensity", intensity, 0.01f, 0f, 2f);
		if (intensity[0] != state.intensity()) {
			presenter.setIntensity(intensity[0]);
		}
		ImGui.text(BBTexts.get("beatblock.vfx_creator.color"));
		if (ImGui.colorEdit3("##vfxColor", color)) {
			presenter.setColor(color[0], color[1], color[2]);
		}
		transitionField.setFromSeconds(state.transitionSeconds(), state.transitionUnit(), state.bpm());
		if (transitionField.render("vfxEnvTransition", BBTexts.get("beatblock.vfx_creator.transition"), state.bpm())) {
			presenter.setTransitionSeconds(transitionField.seconds());
			presenter.setTransitionUnit(transitionField.unit());
		}
	}

	private void renderScreenTintFields(VfxCreatorPanelPresenter.ViewState state) {
		intensity[0] = state.intensity();
		color[0] = state.r(); color[1] = state.g(); color[2] = state.b();
		ImGui.text(BBTexts.get("beatblock.vfx_creator.color"));
		if (ImGui.colorEdit3("##vfxTintColor", color)) {
			presenter.setColor(color[0], color[1], color[2]);
		}
		ImGui.dragFloat(BBTexts.get("beatblock.vfx_creator.intensity") + "##vfxTintIntensity", intensity, 0.01f, 0f, 2f);
		if (intensity[0] != state.intensity()) {
			presenter.setIntensity(intensity[0]);
		}
		renderDurationField(state, "vfxTintDuration");
	}

	private void renderDurationField(VfxCreatorPanelPresenter.ViewState state, String id) {
		durationField.setFromSeconds(state.durationSeconds(), state.durationUnit(), state.bpm());
		if (durationField.render(id, BBTexts.get("beatblock.vfx_creator.duration"), state.bpm())) {
			presenter.setDurationSeconds(durationField.seconds());
			presenter.setDurationUnit(durationField.unit());
		}
	}

	private void renderFlashFields(VfxCreatorPanelPresenter.ViewState state) {
		color[0] = state.r(); color[1] = state.g(); color[2] = state.b();
		ImGui.text(BBTexts.get("beatblock.vfx_creator.color"));
		if (ImGui.colorEdit3("##vfxFlashColor", color)) {
			presenter.setColor(color[0], color[1], color[2]);
		}
		durationField.setFromSeconds(state.durationSeconds(), state.durationUnit(), state.bpm());
		if (durationField.render("vfxFlashDuration", BBTexts.get("beatblock.vfx_creator.duration"), state.bpm())) {
			presenter.setDurationSeconds(durationField.seconds());
			presenter.setDurationUnit(durationField.unit());
		}
	}

	private void renderWeatherFields(VfxCreatorPanelPresenter.ViewState state) {
		weatherType.set(state.weatherType());
		ImGui.inputText(BBTexts.get("beatblock.vfx_creator.weather_type") + "##vfxWeather", weatherType);
		if (!weatherType.get().equals(state.weatherType())) {
			presenter.setWeatherType(weatherType.get());
		}
		transitionField.setFromSeconds(state.transitionSeconds(), state.transitionUnit(), state.bpm());
		if (transitionField.render("vfxWeatherTrans", BBTexts.get("beatblock.vfx_creator.transition"), state.bpm())) {
			presenter.setTransitionSeconds(transitionField.seconds());
			presenter.setTransitionUnit(transitionField.unit());
		}
	}

	private void renderParticleFields(VfxCreatorPanelPresenter.ViewState state) {
		ImGui.text(BBTexts.get("beatblock.vfx_creator.position"));
		ImGui.sameLine();
		ImGui.textWrapped(state.particlePositionLabel());
		if (ImGui.button(BBTexts.get("beatblock.vfx_creator.position.refresh") + "##vfxParticleRefresh")) {
			presenter.refreshParticlePositionFromSelection();
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.vfx_creator.position.manual_edit") + "##vfxParticleManual")) {
			presenter.enableManualParticlePosition();
		}
		if (!state.particlePositionManual()) {
			ImGui.textDisabled(BBTexts.get("beatblock.vfx_creator.position.hint"));
		}
		if (state.particlePositionManual()) {
			particleX.set(state.particleX());
			particleY.set(state.particleY());
			particleZ.set(state.particleZ());
			ImGui.inputDouble("X##vfxParticleX", particleX);
			ImGui.inputDouble("Y##vfxParticleY", particleY);
			ImGui.inputDouble("Z##vfxParticleZ", particleZ);
			if (particleX.get() != state.particleX()
				|| particleY.get() != state.particleY()
				|| particleZ.get() != state.particleZ()) {
				presenter.setParticlePosition(particleX.get(), particleY.get(), particleZ.get());
			}
		}
		ImGui.spacing();
		particleType.set(state.particleType());
		ImGui.inputText(BBTexts.get("beatblock.vfx_creator.particle_type") + "##vfxParticleType", particleType);
		if (!particleType.get().equals(state.particleType())) {
			presenter.setParticleType(particleType.get());
		}
		particleCount.set(state.particleCount());
		ImGui.inputInt(BBTexts.get("beatblock.vfx_creator.count") + "##vfxParticleCount", particleCount);
		if (particleCount.get() != state.particleCount()) {
			presenter.setParticleCount(particleCount.get());
		}
		particleSpread.set(state.particleSpread());
		ImGui.inputDouble(BBTexts.get("beatblock.vfx_creator.spread") + "##vfxParticleSpread", particleSpread);
		if (particleSpread.get() != state.particleSpread()) {
			presenter.setParticleSpread(particleSpread.get());
		}
		particleSpeed.set(state.particleSpeed());
		ImGui.inputDouble(BBTexts.get("beatblock.vfx_creator.speed") + "##vfxParticleSpeed", particleSpeed);
		if (particleSpeed.get() != state.particleSpeed()) {
			presenter.setParticleSpeed(particleSpeed.get());
		}
	}

	private void renderAudioFields(VfxCreatorPanelPresenter.ViewState state) {
		audioChannel.set(state.audioChannel());
		audioVolume[0] = state.audioVolume();
		ImGui.inputText(BBTexts.get("beatblock.vfx_creator.channel") + "##vfxAudioChannel", audioChannel);
		if (!audioChannel.get().equals(state.audioChannel())) {
			presenter.setAudioChannel(audioChannel.get());
		}
		ImGui.dragFloat(BBTexts.get("beatblock.vfx_creator.volume") + "##vfxAudioVolume", audioVolume, 0.01f, 0f, 2f);
		if (audioVolume[0] != state.audioVolume()) {
			presenter.setAudioVolume(audioVolume[0]);
		}
		audioFadeField.setFromSeconds(state.audioFadeSeconds(), state.audioFadeUnit(), state.bpm());
		if (audioFadeField.render("vfxAudioFade", BBTexts.get("beatblock.vfx_creator.fade"), state.bpm())) {
			presenter.setAudioFadeSeconds(audioFadeField.seconds());
			presenter.setAudioFadeUnit(audioFadeField.unit());
		}
	}

	private void syncFieldsToPresenter(VfxCreatorPanelPresenter.ViewState state) {
		presenter.setName(nameBuffer.get());
		presenter.setKind(state.kind());
		switch (state.kind()) {
			case ENVIRONMENT_LIGHTING -> {
				presenter.setIntensity(intensity[0]);
				presenter.setColor(color[0], color[1], color[2]);
				presenter.setTransitionSeconds(transitionField.seconds());
				presenter.setTransitionUnit(transitionField.unit());
			}
			case SCREEN_TINT -> {
				presenter.setIntensity(intensity[0]);
				presenter.setColor(color[0], color[1], color[2]);
				presenter.setDurationSeconds(durationField.seconds());
				presenter.setDurationUnit(durationField.unit());
			}
			case SCREEN_FLASH -> {
				presenter.setColor(color[0], color[1], color[2]);
				presenter.setDurationSeconds(durationField.seconds());
				presenter.setDurationUnit(durationField.unit());
			}
			case WEATHER -> {
				presenter.setWeatherType(weatherType.get());
				presenter.setTransitionSeconds(transitionField.seconds());
				presenter.setTransitionUnit(transitionField.unit());
			}
			case PARTICLE_BURST -> {
				presenter.setParticleType(particleType.get());
				if (state.particlePositionManual()) {
					presenter.setParticlePosition(particleX.get(), particleY.get(), particleZ.get());
				}
				presenter.setParticleCount(particleCount.get());
				presenter.setParticleSpread(particleSpread.get());
				presenter.setParticleSpeed(particleSpeed.get());
			}
			case AUDIO_MIX -> {
				presenter.setAudioChannel(audioChannel.get());
				presenter.setAudioVolume(audioVolume[0]);
				presenter.setAudioFadeSeconds(audioFadeField.seconds());
				presenter.setAudioFadeUnit(audioFadeField.unit());
			}
		}
	}

	private static void notify(VfxCreatorPanelPresenter.InsertOutcome outcome) {
		if (outcome.message() == null || outcome.message().isBlank()) {
			return;
		}
		if (outcome.success()) {
			ToastNotificationSystem.showSuccess(outcome.message());
		} else {
			ToastNotificationSystem.showError(outcome.message());
		}
	}
}
