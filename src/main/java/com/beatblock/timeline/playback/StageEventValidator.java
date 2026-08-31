package com.beatblock.timeline.playback;

import com.beatblock.engine.AnimationDefinition;
import com.beatblock.engine.RuntimeStageObject;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.payload.StageEventPayload;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class StageEventValidator implements TimelineValidationRule {
	@Override
	public void validate(TimelineCompileContext context, DiagnosticCollector diagnostics) {
		Set<String> seenIds = new HashSet<>();
		for (int i = 0; i < context.stageEvents().size(); i++) {
			TimelineAnimationEvent event = context.stageEvents().get(i);
			if (event == null) continue;
			TimelineSourceLocation location = context.stageEventLocations().get(i);
			diagnostics.at(location, () -> validateEvent(event, context, seenIds, diagnostics));
		}
	}

	private static void validateEvent(
		TimelineAnimationEvent event,
		TimelineCompileContext context,
		Set<String> seenIds,
		DiagnosticCollector diagnostics
	) {
		String eventId = event.getEventId();
		double time = event.getTimeSeconds();
		String label = eventId.isBlank() ? "(no-id)" : eventId;
		if (!eventId.isBlank() && !seenIds.add(eventId)) {
			diagnostics.add(TimelineDiagnostic.error(TimelineValidator.RULE_DUPLICATE_EVENT_ID,
				"Duplicate event id: " + eventId, eventId, time));
		}

		double duration = event.getDurationSeconds();
		if (!Double.isFinite(duration)) {
			diagnostics.add(TimelineDiagnostic.error(TimelineValidator.RULE_NON_FINITE_EVENT_DURATION,
				"Non-finite duration for event " + label + ": " + duration, eventId, time));
		} else if (duration <= 0) {
			diagnostics.add(TimelineDiagnostic.error(TimelineValidator.RULE_NON_POSITIVE_EVENT_DURATION,
				"Non-positive duration for event " + label + ": " + duration, eventId, time));
		}

		try {
			StageEventPayload payload = event.getPayload();
			if (payload == null) {
				diagnostics.add(TimelineDiagnostic.error(TimelineValidator.RULE_UNSUPPORTED_PAYLOAD,
					"Null payload for event " + label, eventId, time));
			}
		} catch (RuntimeException error) {
			diagnostics.add(TimelineDiagnostic.error(TimelineValidator.RULE_UNSUPPORTED_PAYLOAD,
				"Unsupported or corrupt payload for event " + label + ": " + error.getMessage(), eventId, time));
		}

		double timelineDuration = context.document().getDurationSeconds();
		if (time < -1e-9) {
			diagnostics.add(TimelineDiagnostic.warning(TimelineValidator.RULE_EVENT_OUTSIDE_TIMELINE,
				"Event " + label + " starts before 0 (" + formatTime(time) + "s)", eventId, time));
		} else if (timelineDuration > 0 && time > timelineDuration + 1e-6) {
			diagnostics.add(TimelineDiagnostic.warning(TimelineValidator.RULE_EVENT_OUTSIDE_TIMELINE,
				"Event " + label + " starts after timeline end (" + formatTime(time) + "s > "
					+ formatTime(timelineDuration) + "s)", eventId, time));
		}

		String targetId = event.getTargetObjectId();
		if (targetId == null || targetId.isBlank()) {
			diagnostics.add(TimelineDiagnostic.warning(TimelineValidator.RULE_UNBOUND_TARGET,
				"Event " + label + " has no RuntimeStageObject target (unbound)", eventId, time));
		} else {
			var engine = context.engine();
			if (engine != null) {
				var stageSystem = engine.getStageObjectSystem();
				if (stageSystem != null) {
					RuntimeStageObject stage = stageSystem.get(targetId);
					if (stage == null) {
						diagnostics.add(TimelineDiagnostic.warning(TimelineValidator.RULE_MISSING_STAGE_OBJECT,
							"Event " + label + " targets missing RuntimeStageObject \"" + targetId + "\"", eventId, time));
					}
				}
			}
		}

		String animationType = event.getAnimationTypeId();
		if (animationType == null || animationType.isBlank()) {
			diagnostics.add(TimelineDiagnostic.error(TimelineValidator.RULE_MISSING_ANIMATION_PRESET,
				"Event " + label + " has empty animation preset id", eventId, time));
		} else {
			var engine = context.engine();
			if (engine != null) {
				var library = engine.getAnimationLibrary();
				if (library != null) {
					AnimationDefinition definition = library.get(animationType);
					if (definition == null) {
						boolean found = library.getAll().keySet().stream()
							.anyMatch(key -> key != null && key.equalsIgnoreCase(animationType));
						if (!found) {
							diagnostics.add(TimelineDiagnostic.error(TimelineValidator.RULE_MISSING_ANIMATION_PRESET,
								"Event " + label + " references unknown animation preset \"" + animationType + "\"",
								eventId, time));
						}
					}
				}
			}
		}
	}

	private static String formatTime(double seconds) {
		return String.format(Locale.ROOT, "%.3f", seconds);
	}
}