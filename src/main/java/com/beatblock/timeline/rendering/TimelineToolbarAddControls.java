package com.beatblock.timeline.rendering;

import com.beatblock.automap.camera.CameraShotMovement;
import com.beatblock.automap.vfx.EnvironmentPreset;
import com.beatblock.automap.vfx.GlobalEffectKind;
import com.beatblock.creator.add.CreatorAddPresenter;
import com.beatblock.creator.timeline.CreatorTimelineProjection;
import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.PresenterFactories;
import imgui.ImGui;

/** Creator 工具栏：+ Add 统一创建菜单。 */
final class TimelineToolbarAddControls {

	private static final String POPUP_ID = "tlCreatorAddPopup";

	private final CreatorAddPresenter presenter;

	TimelineToolbarAddControls() {
		this(PresenterFactories.creatorAddPresenter());
	}

	TimelineToolbarAddControls(CreatorAddPresenter presenter) {
		this.presenter = presenter;
	}

	void render(TimelineEditor editor) {
		if (!CreatorTimelineProjection.isEnabled() || editor == null) {
			return;
		}
		if (ImGui.button(BBTexts.get("beatblock.creator.add.button") + "##tlCreatorAdd")) {
			ImGui.openPopup(POPUP_ID);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.creator.add.tooltip"));
		}
		if (!ImGui.beginPopup(POPUP_ID)) {
			return;
		}

		if (ImGui.beginMenu(BBTexts.get("beatblock.creator.add.animation"))) {
			for (String presetId : CreatorAddPresenter.QUICK_ANIMATION_PRESET_IDS) {
				var preset = BlockInfluencePresets.get(presetId);
				if (preset == null) {
					continue;
				}
				if (ImGui.menuItem(preset.getDisplayName() + "##addAnim" + presetId)) {
					presenter.insertAnimationPreset(presetId);
				}
			}
			ImGui.endMenu();
		}

		if (ImGui.beginMenu(BBTexts.get("beatblock.creator.add.camera"))) {
			for (CameraShotMovement movement : CreatorAddPresenter.QUICK_CAMERA_MOVEMENTS) {
				if (ImGui.menuItem(cameraMovementLabel(movement) + "##addCam" + movement.name())) {
					presenter.insertCameraShot(movement);
				}
			}
			ImGui.endMenu();
		}

		if (ImGui.beginMenu(BBTexts.get("beatblock.creator.add.vfx"))) {
			if (ImGui.menuItem(BBTexts.get("beatblock.creator.add.vfx.flash") + "##addVfxFlash")) {
				presenter.insertVfx(GlobalEffectKind.SCREEN_FLASH);
			}
			if (ImGui.menuItem(BBTexts.get("beatblock.creator.add.vfx.particles") + "##addVfxParticles")) {
				presenter.insertVfx(GlobalEffectKind.PARTICLE_BURST);
			}
			if (ImGui.menuItem(BBTexts.get("beatblock.creator.add.vfx.lighting") + "##addVfxLight")) {
				presenter.insertVfx(GlobalEffectKind.ENVIRONMENT_LIGHTING);
			}
			for (EnvironmentPreset preset : EnvironmentPreset.all()) {
				if (ImGui.menuItem(preset.displayName() + "##addVfxPreset" + preset.id())) {
					presenter.insertVfxPreset(preset.id());
				}
			}
			ImGui.endMenu();
		}

		if (ImGui.menuItem(BBTexts.get("beatblock.creator.add.marker") + "##addMarker")) {
			presenter.insertMarker();
		}

		ImGui.endPopup();
	}

	private static String cameraMovementLabel(CameraShotMovement movement) {
		return switch (movement) {
			case HOLD -> BBTexts.get("beatblock.camera_creator.movement.hold");
			case PUSH_IN -> BBTexts.get("beatblock.camera_creator.movement.push_in");
			case PULL_OUT -> BBTexts.get("beatblock.camera_creator.movement.pull_out");
			case ORBIT -> BBTexts.get("beatblock.camera_creator.movement.orbit");
			case PAN -> BBTexts.get("beatblock.camera_creator.movement.pan");
			case SHAKE -> BBTexts.get("beatblock.camera_creator.movement.shake");
		};
	}
}
