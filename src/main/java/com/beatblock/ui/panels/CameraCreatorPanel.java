package com.beatblock.ui.panels;

import com.beatblock.automap.camera.CameraShotFraming;
import com.beatblock.automap.camera.CameraShotMovement;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.notification.ToastNotificationSystem;
import com.beatblock.ui.presenter.CameraCreatorPanelPresenter;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.util.UiNumberFormatter;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImDouble;
import imgui.type.ImInt;

import java.util.List;

/**
 * Camera Creator: StageObject Subject + Framing + Movement (geometry from framing engine).
 * Capture Current View is the pose-first path; Create Shot is the semantic path.
 * Coordinate fine-tune stays in Timeline Properties.
 */
public final class CameraCreatorPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private static final CameraShotFraming[] FRAMINGS = {
		CameraShotFraming.WIDE, CameraShotFraming.MEDIUM, CameraShotFraming.CLOSE, CameraShotFraming.OVERVIEW
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
	private final ImDouble durationBuffer = new ImDouble(3.0);

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
			if (ImGui.button(BBTexts.get("beatblock.camera_creator.create") + "##cameraCreatorCreate")) {
				var outcome = presenter.createShot();
				notify(outcome);
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
			if (ImGui.radioButton(framingLabel(framing) + "##framing_" + framing.name(), state.framing() == framing)) {
				presenter.setFraming(framing);
			}
			if (i < FRAMINGS.length - 1) {
				ImGui.sameLine();
			}
		}
	}

	private void renderMovement(CameraCreatorPanelPresenter.ViewState state) {
		ImGui.text(BBTexts.get("beatblock.camera_creator.movement"));
		for (int i = 0; i < MOVEMENTS.length; i++) {
			CameraShotMovement movement = MOVEMENTS[i];
			if (ImGui.radioButton(movementLabel(movement) + "##move_" + movement.name(), state.movement() == movement)) {
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
			UiNumberFormatter.format(state.playheadSeconds())
		));
		durationBuffer.set(state.durationSeconds());
		ImGui.setNextItemWidth(120f);
		ImGui.inputDouble(BBTexts.get("beatblock.camera_creator.duration") + "##cameraCreatorDuration", durationBuffer);
		if (durationBuffer.get() != state.durationSeconds()) {
			presenter.setDurationSeconds(durationBuffer.get());
		}
	}

	private static String framingLabel(CameraShotFraming framing) {
		return switch (framing) {
			case WIDE -> BBTexts.get("beatblock.camera_creator.framing.wide");
			case MEDIUM -> BBTexts.get("beatblock.camera_creator.framing.medium");
			case CLOSE -> BBTexts.get("beatblock.camera_creator.framing.close");
			case OVERVIEW -> BBTexts.get("beatblock.camera_creator.framing.overview");
		};
	}

	private static String movementLabel(CameraShotMovement movement) {
		return switch (movement) {
			case HOLD -> BBTexts.get("beatblock.camera_creator.movement.hold");
			case PUSH_IN -> BBTexts.get("beatblock.camera_creator.movement.push_in");
			case PULL_OUT -> BBTexts.get("beatblock.camera_creator.movement.pull_out");
			case ORBIT -> BBTexts.get("beatblock.camera_creator.movement.orbit");
			case PAN -> BBTexts.get("beatblock.camera_creator.movement.rise");
			case SHAKE -> BBTexts.get("beatblock.camera_creator.movement.shake");
		};
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
