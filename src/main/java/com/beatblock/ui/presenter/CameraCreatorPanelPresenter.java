package com.beatblock.ui.presenter;

import com.beatblock.BeatBlock;
import com.beatblock.automap.camera.CameraCollisionPolicy;
import com.beatblock.automap.camera.CameraShot;
import com.beatblock.automap.camera.CameraShotBeatAlignment;
import com.beatblock.automap.camera.CameraShotEasing;
import com.beatblock.automap.camera.CameraShotFraming;
import com.beatblock.automap.camera.CameraShotMovement;
import com.beatblock.automap.camera.CameraShotInsertionService;
import com.beatblock.automap.camera.CameraShotTransition;
import com.beatblock.automap.camera.CameraShotValidator;
import com.beatblock.automap.camera.CameraSubject;
import com.beatblock.automap.camera.CapturedCameraPose;
import com.beatblock.client.camera.CameraCreatorVisualization;
import com.beatblock.client.camera.CameraKeyframeActions;
import com.beatblock.client.camera.CameraViewCaptureService;
import com.beatblock.engine.BlockAnimationEngine;
import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.playback.TimelineDiagnostic;
import com.beatblock.ui.i18n.BBTexts;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Camera Creator: StageObject subject + framing + movement via {@link CameraShot}
 * + {@link CameraShotInsertionService} (Command / one Undo / select clip+segment / Properties / notify).
 */
public final class CameraCreatorPanelPresenter {

	/** Empty id means {@link CameraSubject#allStageObjects()}. */
	public static final String SUBJECT_ALL_ID = "";

	public record SubjectOption(String id, String label) {}

	public record ViewState(
		boolean editorReady,
		double playheadSeconds,
		List<SubjectOption> subjects,
		String selectedSubjectId,
		CameraShotFraming framing,
		CameraShotMovement movement,
		double durationSeconds,
		boolean showCameraPath,
		boolean showFrustum,
		boolean showSubjectBounds,
		String statusMessage
	) {}

	public record CreateOutcome(boolean success, String message) {}

	private final Supplier<Timeline> timeline;
	private final Supplier<TimelineEditor> timelineEditor;
	private final Supplier<StageObjectSystem> stageObjectSystem;
	private final Supplier<BlockAnimationEngine> animationEngine;
	private final Supplier<BuildLayerManager> layerManager;
	private final Supplier<CapturedCameraPose> viewPoseSupplier;

	private String selectedSubjectId = SUBJECT_ALL_ID;
	private CameraShotFraming framing = CameraShotFraming.MEDIUM;
	private CameraShotMovement movement = CameraShotMovement.PUSH_IN;
	private double durationSeconds = 3.0;
	private String statusMessage = "";

	public CameraCreatorPanelPresenter(
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<StageObjectSystem> stageObjectSystem
	) {
		this(timeline, timelineEditor, stageObjectSystem, () -> null, () -> null);
	}

	public CameraCreatorPanelPresenter(
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<StageObjectSystem> stageObjectSystem,
		Supplier<BlockAnimationEngine> animationEngine,
		Supplier<BuildLayerManager> layerManager
	) {
		this(
			timeline,
			timelineEditor,
			stageObjectSystem,
			animationEngine,
			layerManager,
			() -> CameraKeyframeActions.sampleCurrentView().orElse(null)
		);
	}

	public CameraCreatorPanelPresenter(
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<StageObjectSystem> stageObjectSystem,
		Supplier<BlockAnimationEngine> animationEngine,
		Supplier<BuildLayerManager> layerManager,
		Supplier<CapturedCameraPose> viewPoseSupplier
	) {
		this.timeline = timeline;
		this.timelineEditor = timelineEditor;
		this.stageObjectSystem = stageObjectSystem != null ? stageObjectSystem : () -> null;
		this.animationEngine = animationEngine != null ? animationEngine : () -> null;
		this.layerManager = layerManager != null ? layerManager : () -> null;
		this.viewPoseSupplier = viewPoseSupplier != null
			? viewPoseSupplier
			: () -> CameraKeyframeActions.sampleCurrentView().orElse(null);
	}

	public ViewState viewState() {
		TimelineEditor editor = timelineEditor.get();
		List<SubjectOption> subjects = subjectOptions();
		syncSubjectBoundsTarget();
		if (editor == null || timeline.get() == null) {
			return new ViewState(
				false, 0.0, subjects, selectedSubjectId, framing, movement, durationSeconds,
				CameraCreatorVisualization.showCameraPath(),
				CameraCreatorVisualization.showFrustum(),
				CameraCreatorVisualization.showSubjectBounds(),
				statusMessage);
		}
		return new ViewState(
			true,
			editor.getClock().getCurrentTimeSeconds(),
			subjects,
			selectedSubjectId,
			framing,
			movement,
			durationSeconds,
			CameraCreatorVisualization.showCameraPath(),
			CameraCreatorVisualization.showFrustum(),
			CameraCreatorVisualization.showSubjectBounds(),
			statusMessage
		);
	}

	public void setSelectedSubjectId(@Nullable String subjectId) {
		this.selectedSubjectId = subjectId != null ? subjectId : SUBJECT_ALL_ID;
		syncSubjectBoundsTarget();
	}

	public void setShowCameraPath(boolean show) {
		CameraCreatorVisualization.setShowCameraPath(show);
	}

	public void setShowFrustum(boolean show) {
		CameraCreatorVisualization.setShowFrustum(show);
	}

	public void setShowSubjectBounds(boolean show) {
		CameraCreatorVisualization.setShowSubjectBounds(show);
		syncSubjectBoundsTarget();
	}

	private void syncSubjectBoundsTarget() {
		CameraCreatorVisualization.setSubjectForBounds(resolveSubject(selectedSubjectId));
	}

	public void setFraming(@Nullable CameraShotFraming framing) {
		this.framing = framing != null ? framing : CameraShotFraming.MEDIUM;
	}

	public void setMovement(@Nullable CameraShotMovement movement) {
		this.movement = movement != null ? movement : CameraShotMovement.HOLD;
	}

	public void setDurationSeconds(double durationSeconds) {
		this.durationSeconds = Math.max(0.05, durationSeconds);
	}

	public CreateOutcome createShot() {
		Timeline tl = timeline.get();
		TimelineEditor editor = timelineEditor.get();
		if (tl == null || editor == null) {
			return fail(BBTexts.get("beatblock.common.timeline_not_initialized"));
		}

		CameraSubject subject = resolveSubject(selectedSubjectId);
		double start = editor.getClock().getCurrentTimeSeconds();
		CameraShot shot = new CameraShot(
			start,
			durationSeconds,
			subject,
			framing,
			movement,
			null,
			CameraShotTransition.CUT,
			CameraShotEasing.SMOOTH,
			CameraCollisionPolicy.AVOID_BLOCKS,
			CameraShotBeatAlignment.none(),
			-1
		);

		BlockAnimationEngine engine = resolveEngine();
		BuildLayerManager layers = layerManager.get();
		List<TimelineDiagnostic> diagnostics = CameraShotValidator.validate(shot, engine, layers);
		if (CameraShotValidator.hasErrors(diagnostics)) {
			String detail = diagnostics.getFirst().message();
			return fail(detail != null && !detail.isBlank()
				? detail
				: BBTexts.get("beatblock.camera_creator.create_failed"));
		}

		var inserted = CameraShotInsertionService.insertManualShot(tl, editor, shot);
		if (!inserted.written()) {
			return fail(BBTexts.get("beatblock.camera_creator.create_failed"));
		}

		statusMessage = BBTexts.get(
			"beatblock.camera_creator.created",
			movementLabel(movement),
			subject.displayLabel()
		);
		return new CreateOutcome(true, statusMessage);
	}

	/**
	 * Capture current Minecraft view into a PATH keyframe (or new PATH clip if none selected).
	 */
	public CreateOutcome captureCurrentView() {
		Timeline tl = timeline.get();
		TimelineEditor editor = timelineEditor.get();
		if (tl == null || editor == null) {
			return fail(BBTexts.get("beatblock.common.timeline_not_initialized"));
		}
		CapturedCameraPose pose = viewPoseSupplier.get();
		var result = CameraViewCaptureService.captureCurrentView(tl, editor, pose);
		if (!result.success()) {
			return fail(result.message());
		}
		statusMessage = result.message();
		return new CreateOutcome(true, statusMessage);
	}

	private List<SubjectOption> subjectOptions() {
		List<SubjectOption> options = new ArrayList<>();
		options.add(new SubjectOption(
			SUBJECT_ALL_ID,
			BBTexts.get("beatblock.camera_creator.subject.all")
		));
		StageObjectSystem system = stageObjectSystem.get();
		if (system != null) {
			for (RuntimeStageObject obj : system.getAll()) {
				if (obj == null || obj.getId() == null || obj.getId().isBlank()) continue;
				String name = obj.getName() != null && !obj.getName().isBlank() ? obj.getName() : obj.getId();
				options.add(new SubjectOption(obj.getId(), name + " (" + obj.getId() + ")"));
			}
		}
		return List.copyOf(options);
	}

	private static CameraSubject resolveSubject(String subjectId) {
		if (subjectId == null || subjectId.isBlank()) {
			return CameraSubject.allStageObjects();
		}
		return CameraSubject.stageObject(subjectId);
	}

	private @Nullable BlockAnimationEngine resolveEngine() {
		BlockAnimationEngine engine = animationEngine.get();
		if (engine != null) return engine;
		try {
			return BeatBlock.getContext().blockAnimationEngine();
		} catch (Exception ignored) {
			return null;
		}
	}

	private static String movementLabel(CameraShotMovement movement) {
		String key = switch (movement) {
			case HOLD -> "beatblock.camera_creator.movement.hold";
			case PUSH_IN -> "beatblock.camera_creator.movement.push_in";
			case PULL_OUT -> "beatblock.camera_creator.movement.pull_out";
			case ORBIT -> "beatblock.camera_creator.movement.orbit";
			case PAN -> "beatblock.camera_creator.movement.rise";
			case SHAKE -> "beatblock.camera_creator.movement.shake";
		};
		return BBTexts.get(key);
	}

	private CreateOutcome fail(String message) {
		statusMessage = message != null ? message : "";
		return new CreateOutcome(false, statusMessage);
	}
}
