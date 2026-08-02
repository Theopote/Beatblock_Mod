package com.beatblock.timeline.playback;

import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.payload.StageEventPayload;

final class BuildLayerValidator implements TimelineValidationRule {
	@Override
	public void validate(TimelineCompileContext context, DiagnosticCollector diagnostics) {
		if (context.layerManager() == null) return;
		for (int i = 0; i < context.stageEvents().size(); i++) {
			var event = context.stageEvents().get(i);
			if (event == null) continue;
			diagnostics.at(context.stageEventLocations().get(i), () -> {
				try {
					StageEventPayload payload = event.getPayload();
					if (payload.actionMode() == TimelineAnimationActionMode.BUILD
						&& payload instanceof StageEventPayload.Build build) {
						String layerId = build.layerId();
						if (layerId != null && !layerId.isBlank() && context.layerManager().get(layerId) == null) {
							diagnostics.add(TimelineDiagnostic.warning(TimelineValidator.RULE_MISSING_BUILD_LAYER,
								"BUILD event " + event.getEventId() + " references missing layer \"" + layerId + "\"",
								event.getEventId(), event.getTimeSeconds()));
						}
					}
				} catch (RuntimeException ignored) {
					// StageEventValidator owns corrupt-payload diagnostics.
				}
			});
		}
	}
}