package com.beatblock.ui.presenter;

import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.generation.AnimationDropTargetResolver;
import com.beatblock.timeline.generation.AnimationMultiTargetDropPrompt;
import com.beatblock.timeline.generation.TimelineDraftWriter;
import com.beatblock.timeline.editor.SelectionState;
import com.beatblock.ui.eventlibrary.EventTemplate;
import com.beatblock.ui.eventlibrary.EventTemplateStore;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.notification.ToastNotificationSystem;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class EventLibraryPanelPresenter {

	public record ViewState(
		boolean editorReady,
		boolean hasSelection,
		String selectedEventSummary,
		List<EventTemplate> templates,
		String statusMessage
	) {}

	public record ApplyOutcome(boolean success, String message) {}

	private final EventPropertiesPresenter eventPropertiesPresenter;
	private final Supplier<Timeline> timeline;
	private final Supplier<TimelineEditor> timelineEditor;
	private final Supplier<StageObjectSystem> stageObjectSystem;
	private final Supplier<BuildLayerManager> layerManager;

	private String statusMessage = "";

	public EventLibraryPanelPresenter(
		EventPropertiesPresenter eventPropertiesPresenter,
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<StageObjectSystem> stageObjectSystem
	) {
		this(eventPropertiesPresenter, timeline, timelineEditor, stageObjectSystem, () -> null);
	}

	public EventLibraryPanelPresenter(
		EventPropertiesPresenter eventPropertiesPresenter,
		Supplier<Timeline> timeline,
		Supplier<TimelineEditor> timelineEditor,
		Supplier<StageObjectSystem> stageObjectSystem,
		Supplier<BuildLayerManager> layerManager
	) {
		this.eventPropertiesPresenter = eventPropertiesPresenter;
		this.timeline = timeline;
		this.timelineEditor = timelineEditor;
		this.stageObjectSystem = stageObjectSystem;
		this.layerManager = layerManager != null ? layerManager : () -> null;
	}

	public ViewState viewState() {
		Timeline tl = timeline.get();
		TimelineEditor editor = timelineEditor.get();
		if (tl == null || editor == null) {
			return new ViewState(false, false, "", EventTemplateStore.all(), statusMessage);
		}
		EventPropertiesRef ref = eventPropertiesPresenter.resolvePropertiesRef(tl, editor.getSelectionState());
		boolean hasSelection = ref != null && ref.event() != null && ref.event().getType() == EventType.ANIMATION;
		String summary = hasSelection ? summarize(ref) : "";
		return new ViewState(true, hasSelection, summary, EventTemplateStore.all(), statusMessage);
	}

	public ApplyOutcome saveFromSelection(String name) {
		Timeline tl = timeline.get();
		TimelineEditor editor = timelineEditor.get();
		if (tl == null || editor == null) {
			return fail(BBTexts.get("beatblock.common.timeline_not_initialized"));
		}
		EventPropertiesRef ref = eventPropertiesPresenter.resolvePropertiesRef(tl, editor.getSelectionState());
		if (ref == null || ref.event() == null || ref.event().getType() != EventType.ANIMATION) {
			return fail(BBTexts.get("beatblock.event_library.no_selection"));
		}
		TimelineAnimationEvent animationEvent = findAnimationEvent(tl, ref.event().getId());
		if (animationEvent == null) {
			return fail(BBTexts.get("beatblock.event_library.no_selection"));
		}
		EventTemplate template = EventTemplate.fromAnimationEvent(animationEvent, name);
		EventTemplateStore.add(template);
		statusMessage = BBTexts.get("beatblock.event_library.saved", template.name());
		return new ApplyOutcome(true, statusMessage);
	}

	public ApplyOutcome applyTemplate(String templateId) {
		Timeline tl = timeline.get();
		TimelineEditor editor = timelineEditor.get();
		if (tl == null || editor == null) {
			return fail(BBTexts.get("beatblock.common.timeline_not_initialized"));
		}
		EventTemplate template = EventTemplateStore.find(templateId).orElse(null);
		if (template == null) {
			return fail(BBTexts.get("beatblock.event_library.template_missing"));
		}
		AnimationDropTargetResolver.Result targets = resolveDropTargets(editor.getSelectionState(), tl);
		double timeSeconds = editor.getClock().getCurrentTimeSeconds();

		if (targets.mode() == AnimationDropTargetResolver.Mode.MULTI) {
			final String templateName = template.name();
			AnimationMultiTargetDropPrompt.request(new AnimationMultiTargetDropPrompt.Pending(
				templateName,
				targets.targetObjectIds(),
				chosenTargets -> {
					int written = writeTemplateEvents(tl, editor, template, timeSeconds, chosenTargets);
					if (written <= 0) {
						ToastNotificationSystem.showError(BBTexts.get("beatblock.event_library.apply_failed"));
						return 0;
					}
					String msg = written > 1
						? BBTexts.get("beatblock.event_library.applied_multi", templateName, written)
						: BBTexts.get("beatblock.event_library.applied", templateName);
					ToastNotificationSystem.showSuccess(msg);
					return written;
				}
			));
			statusMessage = BBTexts.get("beatblock.animation_library.multi_target.pending");
			return new ApplyOutcome(true, statusMessage);
		}

		int written = writeTemplateEvents(tl, editor, template, timeSeconds, targets.targetsForEventCreation());
		if (written <= 0) {
			return fail(BBTexts.get("beatblock.event_library.apply_failed"));
		}
		if (targets.isUnbound()) {
			statusMessage = BBTexts.get("beatblock.event_library.applied_unbound", template.name());
		} else if (written > 1) {
			statusMessage = BBTexts.get("beatblock.event_library.applied_multi", template.name(), written);
		} else {
			statusMessage = BBTexts.get("beatblock.event_library.applied", template.name());
		}
		return new ApplyOutcome(true, statusMessage);
	}

	private static int writeTemplateEvents(
		Timeline tl,
		TimelineEditor editor,
		EventTemplate template,
		double timeSeconds,
		List<String> targetObjectIds
	) {
		ArrayList<TimelineAnimationEvent> events = new ArrayList<>(targetObjectIds.size());
		for (String targetObjectId : targetObjectIds) {
			events.add(template.toTimelineEvent(timeSeconds, targetObjectId));
		}
		var inserted = TimelineDraftWriter.insertManualEvents(
			tl,
			Timeline.TRACK_ID_ANIMATION_BLOCK,
			events
		);
		if (inserted.written() > 0) {
			SelectionState selection = editor.getSelectionState();
			if (selection != null) {
				selection.clearEvents();
				selection.clearClips();
				for (String eventId : inserted.eventIds()) {
					selection.selectEvent(eventId);
				}
				selection.setRangeAnchorEventId(inserted.eventIds().getFirst());
			}
			editor.syncClockDuration();
		}
		return inserted.written();
	}

	public ApplyOutcome deleteTemplate(String templateId) {
		if (!EventTemplateStore.remove(templateId)) {
			return fail(BBTexts.get("beatblock.event_library.template_missing"));
		}
		statusMessage = BBTexts.get("beatblock.event_library.deleted");
		return new ApplyOutcome(true, statusMessage);
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
		return null;
	}

	private static String summarize(EventPropertiesRef ref) {
		var params = ref.event().getParameters();
		Object animationType = params.get("animationType");
		return String.valueOf(animationType != null ? animationType : ref.event().getType().name())
			+ " @ " + String.format("%.2fs", ref.event().getTimeSeconds());
	}

	private ApplyOutcome fail(String message) {
		statusMessage = message;
		return new ApplyOutcome(false, message);
	}
}
