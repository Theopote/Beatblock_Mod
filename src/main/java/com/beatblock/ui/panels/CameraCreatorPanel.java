package com.beatblock.ui.panels;

import com.beatblock.automap.camera.CameraShotAngle;
import com.beatblock.automap.camera.CameraShotFraming;
import com.beatblock.automap.camera.CameraShotMovement;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.notification.ToastNotificationSystem;
import com.beatblock.ui.presenter.CameraCreatorPanelPresenter;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.util.MusicalDurationField;
import imgui.ImGui;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;

import java.util.List;

/**
 * Camera Creator: Subject + Framing + Angle + Movement + musical duration.
 * Capture Current View creates a new PATH shot; Add Keyframe targets the selected PATH clip.
 */
public final class CameraCreatorPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private static final CameraShotFraming[] FRAMINGS = {
		CameraShotFraming.WIDE, CameraShotFraming.MEDIUM, CameraShotFraming.CLOSE, CameraShotFraming.OVERVIEW
	};
	private static final CameraShotAngle[] ANGLES = {
		CameraShotAngle.FRONT,
		CameraShotAngle.FRONT_THREE_QUARTER,
		CameraShotAngle.SIDE,
		CameraShotAngle.REAR_THREE_QUARTER,
		CameraShotAngle.TOP,
		CameraShotAngle.LOW,
		CameraShotAngle.HIGH
	};
	private static final CameraShotMovement[] MOVEMENTS = {
		CameraShotMovement.HOLD,
		CameraShotMovement.PUSH_IN,
		CameraShotMovement.PULL_OUT,
		CameraShotMovement.ORBIT,
		CameraShotMovement.PAN,
		CameraShotMovement.SHAKE
	};

	private final CameraCreatorPanelPresenter presenter;
	private final ImInt subjectIndex = new ImInt(0);
	private final MusicalDurationField durationField = new MusicalDurationField();

	public CameraCreatorPanel() {
		this(PresenterFactories.cameraCreatorPanelPresenter());
	}

	CameraCreatorPanel(CameraCreatorPanelPresenter presenter) {
		this.presenter = presenter;
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.cameraCreatorWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.cameraCreatorWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			var state = presenter.viewState();
			ImGui.text(BBTexts.get("beatblock.camera_creator.title"));
			ImGui.separator();
			renderVisualizationToolbar(state);
			ImGui.separator();
			ImGui.textWrapped(state.summaryLine());
			ImGui.separator();
			ImGui.textWrapped(BBTexts.get("beatblock.camera_creator.hint"));

			if (!state.editorReady()) {
				ImGui.spacing();
				ImGui.textDisabled(BBTexts.get("beatblock.common.timeline_not_initialized"));
				return;
			}

			renderSubject(state);
			ImGui.spacing();
			renderFraming(state);
			ImGui.spacing();
			renderAngle(state);
			ImGui.spacing();
			renderMovement(state);
			ImGui.spacing();
			renderTiming(state);
			ImGui.spacing();
			ImGui.textDisabled(BBTexts.get("beatblock.camera_creator.framing_engine_note"));
			ImGui.spacing();
			if (ImGui.button(BBTexts.get("beatblock.camera_creator.capture") + "##cameraCreatorCapture")) {
				notify(presenter.captureCurrentView());
			}
			ImGui.sameLine();
			if (!state.canAddKeyframe()) {
				ImGui.beginDisabled();
			}
			if (ImGui.button(BBTexts.get("beatblock.camera_creator.add_keyframe") + "##cameraCreatorAddKf")) {
				notify(presenter.addKeyframeAtPlayhead());
			}
			if (!state.canAddKeyframe()) {
				ImGui.endDisabled();
				if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
					ImGui.setTooltip(BBTexts.get("beatblock.camera_creator.capture_need_path"));
				}
			}
			ImGui.sameLine();
			if (ImGui.button(BBTexts.get("beatblock.camera_creator.create") + "##cameraCreatorCreate")) {
				notify(presenter.createShot());
			}
			if (!state.statusMessage().isBlank()) {
				ImGui.spacing();
				ImGui.textWrapped(state.statusMessage());
			}
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.cameraCreatorWindow());
		}
	}

	private void renderVisualizationToolbar(CameraCreatorPanelPresenter.ViewState state) {
		ImGui.textDisabled(BBTexts.get("beatblock.camera_creator.viz_toolbar"));
		ImBoolean path = new ImBoolean(state.showCameraPath());
		if (ImGui.checkbox(BBTexts.get("beatblock.camera_creator.show_path") + "##camCreatorShowPath", path)) {
			presenter.setShowCameraPath(path.get());
		}
		ImGui.sameLine();
		ImBoolean frustum = new ImBoolean(state.showFrustum());
		if (ImGui.checkbox(BBTexts.get("beatblock.camera_creator.show_frustum") + "##camCreatorShowFrustum", frustum)) {
			presenter.setShowFrustum(frustum.get());
		}
		ImGui.sameLine();
		ImBoolean bounds = new ImBoolean(state.showSubjectBounds());
		if (ImGui.checkbox(BBTexts.get("beatblock.camera_creator.show_subject_bounds") + "##camCreatorShowBounds", bounds)) {
			presenter.setShowSubjectBounds(bounds.get());
		}
	}

	private void renderSubject(CameraCreatorPanelPresenter.ViewState state) {
		ImGui.text(BBTexts.get("beatblock.camera_creator.subject"));
		List<CameraCreatorPanelPresenter.SubjectOption> subjects = state.subjects();
		String[] labels = new String[subjects.size()];
		int selected = 0;
		for (int i = 0; i < subjects.size(); i++) {
			labels[i] = subjects.get(i).label();
			if (subjects.get(i).id().equals(state.selectedSubjectId())) {
				selected = i;
			}
		}
		subjectIndex.set(selected);
		ImGui.setNextItemWidth(-1f);
		if (ImGui.combo("##cameraCreatorSubject", subjectIndex, labels)) {
			int idx = subjectIndex.get();
			if (idx >= 0 && idx < subjects.size()) {
				presenter.setSelectedSubjectId(subjects.get(idx).id());
			}
		}
	}

	private void renderFraming(CameraCreatorPanelPresenter.ViewState state) {
		ImGui.text(BBTexts.get("beatblock.camera_creator.framing"));
		for (int i = 0; i < FRAMINGS.length; i++) {
			CameraShotFraming framing = FRAMINGS[i];
			if (ImGui.radioButton(
				CameraCreatorPanelPresenter.framingLabel(framing) + "##framing_" + framing.name(),
				state.framing() == framing
			)) {
				presenter.setFraming(framing);
			}
			if (i < FRAMINGS.length - 1) {
				ImGui.sameLine();
			}
		}
	}

	private void renderAngle(CameraCreatorPanelPresenter.ViewState state) {
		ImGui.text(BBTexts.get("beatblock.camera_creator.angle"));
		for (int i = 0; i < ANGLES.length; i++) {
			CameraShotAngle angle = ANGLES[i];
			if (ImGui.radioButton(
				CameraCreatorPanelPresenter.angleLabel(angle) + "##angle_" + angle.name(),
				state.angle() == angle
			)) {
				presenter.setAngle(angle);
			}
			if (i < ANGLES.length - 1 && (i + 1) % 4 != 0) {
				ImGui.sameLine();
			}
		}
	}

	private void renderMovement(CameraCreatorPanelPresenter.ViewState state) {
		ImGui.text(BBTexts.get("beatblock.camera_creator.movement"));
		for (int i = 0; i < MOVEMENTS.length; i++) {
			CameraShotMovement movement = MOVEMENTS[i];
			if (ImGui.radioButton(
				CameraCreatorPanelPresenter.movementLabel(movement) + "##move_" + movement.name(),
				state.movement() == movement
			)) {
				presenter.setMovement(movement);
			}
			if (i < MOVEMENTS.length - 1 && (i + 1) % 3 != 0) {
				ImGui.sameLine();
			}
		}
	}

	private void renderTiming(CameraCreatorPanelPresenter.ViewState state) {
		ImGui.text(BBTexts.get("beatblock.camera_creator.timing"));
		ImGui.textDisabled(BBTexts.get(
			"beatblock.camera_creator.playhead",
			String.format(java.util.Locale.ROOT, "%.2f", state.playheadSeconds())
		));
		durationField.setFromSeconds(state.durationSeconds(), state.durationUnit(), state.bpm());
		if (durationField.render("cameraCreatorDuration", BBTexts.get("beatblock.camera_creator.duration"), state.bpm())) {
			presenter.setDurationSeconds(durationField.seconds());
			presenter.setDurationUnit(durationField.unit());
		}
	}

	private static void notify(CameraCreatorPanelPresenter.CreateOutcome outcome) {
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
