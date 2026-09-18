package com.beatblock.creator.add;

import com.beatblock.automap.camera.CameraShotMovement;
import com.beatblock.automap.vfx.GlobalEffectKind;
import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.influence.BlockInfluencePresets;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.timeline.MarkerType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.generation.AnimationDropTargetResolver;
import com.beatblock.timeline.generation.AnimationPresetEventWriter;
import com.beatblock.timeline.generation.AnimationMultiTargetDropPrompt;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.notification.ToastNotificationSystem;
import com.beatblock.ui.presenter.CameraCreatorPanelPresenter;
import com.beatblock.ui.presenter.MarkerPanelPresenter;
import com.beatblock.ui.presenter.VfxCreatorPanelPresenter;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 统一「在播放头添加」入口：复用 Animation / Camera / VFX / Marker 既有 presenter 与服务。
 */
public final class CreatorAddPresenter {

	public record Outcome(boolean success, String message) {}

	public static final List<String> QUICK_ANIMATION_PRESET_IDS = List.of(
		"Pulse", "BlockJump", "Meteor", "WaveMotion", "BlockExplosion"
	);

	public static final List<CameraShotMovement> QUICK_CAMERA_MOVEMENTS = List.of(
		CameraShotMovement.HOLD,
		CameraShotMovement.PUSH_IN,
		CameraShotMovement.ORBIT,
		CameraShotMovement.PAN
	);

	private final Supplier<Timeline> timeline;
	private final Supplier<TimelineEditor> timelineEditor;
	private final Supplier<BuildLayerManager> layerManager;
	private final Supplier<StageObjectSystem> stageObjectSystem;
	private final CameraCreatorPanelPresenter cameraPresenter;
	private final VfxCreatorPanelPresenter vfxPresenter;
	private final MarkerPanelPresenter markerPresenter;

	public CreatorAddPresenter(
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<BuildLayerManager> layerManager,
		Supplier<StageObjectSystem> stageObjectSystem,
		CameraCreatorPanelPresenter cameraPresenter,
		VfxCreatorPanelPresenter vfxPresenter,
		MarkerPanelPresenter markerPresenter
	) {
		this.timeline = timeline;
		this.timelineEditor = timelineEditor;
		this.layerManager = layerManager != null ? layerManager : () -> null;
		this.stageObjectSystem = stageObjectSystem != null ? stageObjectSystem : () -> null;
		this.cameraPresenter = cameraPresenter;
		this.vfxPresenter = vfxPresenter;
		this.markerPresenter = markerPresenter;
	}

	public Outcome insertAnimationPreset(String presetId) {
		Timeline tl = timeline.get();
		TimelineEditor editor = timelineEditor.get();
		if (tl == null || editor == null) {
			return fail(BBTexts.get("beatblock.common.timeline_not_initialized"));
		}
		if (presetId == null || presetId.isBlank() || BlockInfluencePresets.get(presetId) == null) {
			return fail(BBTexts.get("beatblock.animation_library.preset_missing"));
		}
		double time = editor.getClock().getCurrentTimeSeconds();
		AnimationDropTargetResolver.Result targets = resolveDropTargets(editor.getSelectionState(), tl);
		if (targets.mode() == AnimationDropTargetResolver.Mode.MULTI) {
			String displayName = BlockInfluencePresets.get(presetId).getDisplayName();
			AnimationMultiTargetDropPrompt.request(new AnimationMultiTargetDropPrompt.Pending(
				displayName,
				targets.targetObjectIds(),
				chosenTargets -> {
					var result = AnimationPresetEventWriter.writePresetEvents(
						tl, Timeline.TRACK_ID_ANIMATION_BLOCK, presetId, time, chosenTargets);
					if (result.written() > 0) {
						AnimationPresetEventWriter.selectCreatedEvents(editor.getSelectionState(), result);
						editor.syncClockDuration();
					}
					AnimationPresetEventWriter.toastWriteResult(displayName, result);
					return result.written();
				}
			));
			return new Outcome(true, BBTexts.get("beatblock.animation_library.multi_target.pending"));
		}
		var result = AnimationPresetEventWriter.writePresetEvents(
			tl,
			Timeline.TRACK_ID_ANIMATION_BLOCK,
			presetId,
			time,
			targets.targetsForEventCreation()
		);
		if (result.written() <= 0) {
			return fail(BBTexts.get("beatblock.creator.add.animation_failed"));
		}
		AnimationPresetEventWriter.selectCreatedEvents(editor.getSelectionState(), result);
		editor.syncClockDuration();
		String message = BBTexts.get(
			"beatblock.creator.add.animation_inserted",
			BlockInfluencePresets.get(presetId).getDisplayName()
		);
		ToastNotificationSystem.showSuccess(message);
		return new Outcome(true, message);
	}

	public Outcome insertCameraShot(CameraShotMovement movement) {
		cameraPresenter.setMovement(movement);
		var result = cameraPresenter.createShot();
		if (!result.success()) {
			return fail(result.message());
		}
		ToastNotificationSystem.showSuccess(result.message());
		return new Outcome(true, result.message());
	}

	public Outcome insertVfx(GlobalEffectKind kind) {
		vfxPresenter.setKind(kind);
		var result = vfxPresenter.insertAtPlayhead();
		if (!result.success()) {
			return fail(result.message());
		}
		ToastNotificationSystem.showSuccess(result.message());
		return new Outcome(true, result.message());
	}

	public Outcome insertVfxPreset(String presetId) {
		var result = vfxPresenter.applyPreset(presetId);
		if (!result.success()) {
			return fail(result.message());
		}
		ToastNotificationSystem.showSuccess(result.message());
		return new Outcome(true, result.message());
	}

	public Outcome insertMarker() {
		var result = markerPresenter.insertAtPlayhead(MarkerType.GENERIC, null);
		if (!result.ok()) {
			return fail(result.messageOrEmpty());
		}
		String message = result.messageOrEmpty();
		if (!message.isBlank()) {
			ToastNotificationSystem.showSuccess(message);
		}
		return new Outcome(true, message);
	}

	private AnimationDropTargetResolver.Result resolveDropTargets(
		@Nullable SelectionState selection,
		Timeline timeline
	) {
		List<String> preferred = List.of();
		BuildLayerManager layers = layerManager.get();
		if (layers != null) {
			preferred = layers.getSelectedStageObjectIds();
		}
		List<String> fromEvents = new ArrayList<>();
		if (selection != null) {
			for (String eventId : selection.getSelectedEvents()) {
				TimelineAnimationEvent event = findAnimationEvent(timeline, eventId);
				if (event != null && !event.isUnboundTarget()) {
					fromEvents.add(event.getTargetObjectId());
				}
			}
		}
		List<String> registered = new ArrayList<>();
		StageObjectSystem system = stageObjectSystem.get();
		if (system != null) {
			for (RuntimeStageObject obj : system.getAll()) {
				if (obj != null && obj.getId() != null && !obj.getId().isBlank()) {
					registered.add(obj.getId());
				}
			}
		}
		return AnimationDropTargetResolver.resolve(preferred, fromEvents, registered);
	}

	private static @Nullable TimelineAnimationEvent findAnimationEvent(Timeline timeline, String eventId) {
		if (eventId == null || eventId.isBlank()) {
			return null;
		}
		for (TimelineAnimationEvent event : timeline.getBlockAnimationEvents()) {
			if (eventId.equals(event.getEventId())) {
				return event;
			}
		}
		for (TimelineAnimationEvent event : timeline.getAutoAnimationEvents()) {
			if (eventId.equals(event.getEventId())) {
				return event;
			}
		}
		return null;
	}

	private static Outcome fail(String message) {
		ToastNotificationSystem.showError(message);
		return new Outcome(false, message);
	}
}
