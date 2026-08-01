package com.beatblock.ui.properties.editors;

import com.beatblock.client.BeatBlockClientDriver;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.rendering.TrackRegistry;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.AnimationEditorViewState;
import com.beatblock.ui.presenter.EventPropertiesRef;
import imgui.ImGui;

/** Read-only event identity, mapping metadata, and last runtime execution report. */
public final class EventDiagnosticsSection implements EventPropertySection {

	@Override
	public Tab tab() {
		return Tab.INFO;
	}

	@Override
	public int order() {
		return 10;
	}

	@Override
	public boolean supports(EventEditContext context) {
		return context.ref() != null && context.ref().event() != null;
	}

	@Override
	public void render(EventEditContext context) {
		EventPropertiesRef ref = context.ref();
		AnimationEditorViewState viewState = context.viewState();

		ImGui.textDisabled(BBTexts.get("beatblock.event.track"));
		ImGui.sameLine();
		ImGui.text(ref.track().getName().isBlank() ? ref.track().getId() : ref.track().getName());

		ImGui.textDisabled(BBTexts.get("beatblock.event.event_id"));
		ImGui.sameLine();
		ImGui.text(ref.event().getId());

		if (Timeline.isBlockAnimationFeatureTrackId(ref.track().getId())) {
			ImGui.textDisabled(BBTexts.get("beatblock.event.feature_lane"));
			ImGui.sameLine();
			ImGui.text(TrackRegistry.localizedName(Timeline.blockAnimationFeatureKeyFromTrackId(ref.track().getId())));
		}
		String sourceFeature = viewState.sourceFeature();
		if (!sourceFeature.isBlank()) {
			ImGui.textDisabled(BBTexts.get("beatblock.event.source_feature"));
			ImGui.sameLine();
			ImGui.text(TrackRegistry.localizedName(sourceFeature));
		}
		String generatedBy = viewState.generatedBy();
		if (!generatedBy.isBlank()) {
			ImGui.textDisabled(BBTexts.get("beatblock.event.generated_by"));
			ImGui.sameLine();
			ImGui.text(generatedBy);
		}
		ImGui.textDisabled(BBTexts.get("beatblock.event.action_mode"));
		ImGui.sameLine();
		ImGui.text(TimelineAnimationActionMode.fromValue(viewState.actionMode()).name());

		ImGui.spacing();
		ImGui.text(BBTexts.get("beatblock.event.metadata"));
		ImGui.textDisabled(BBTexts.get("beatblock.event.mapping", viewState.mappingProfile()));
		ImGui.textDisabled(BBTexts.get("beatblock.event.source_stem", viewState.sourceStem()));

		String eventId = ref.event().getId();
		if (eventId == null || eventId.isBlank()) {
			return;
		}
		BeatBlockClientDriver.TimelineActionExecutionReport report =
			BeatBlockClientDriver.getTimelineActionExecutionReport(eventId);
		if (report == null) {
			return;
		}
		long ageMs = Math.max(0L, System.currentTimeMillis() - report.timestampMs());
		ImGui.textDisabled(BBTexts.get(
			"beatblock.event.runtime_status",
			report.actionMode().name(),
			report.status(),
			report.mutationCount(),
			ageMs
		));
		if (report.detail() != null && !report.detail().isBlank()) {
			ImGui.textDisabled(BBTexts.get("beatblock.event.runtime_detail", report.detail()));
		}
	}
}
