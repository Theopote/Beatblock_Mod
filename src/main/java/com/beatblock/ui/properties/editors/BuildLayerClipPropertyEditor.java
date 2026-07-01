package com.beatblock.ui.properties.editors;

import com.beatblock.BeatBlock;
import com.beatblock.engine.layer.BuildLayerManager;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.presenter.EventPropertiesRef;
import com.beatblock.ui.presenter.PresenterFactories;
import imgui.ImGui;
import imgui.type.ImString;

import java.util.function.Supplier;

/** 建造图层轨道片段属性编辑器（片段选中、无事件焦点时）。 */
public final class BuildLayerClipPropertyEditor {

	private static final int INPUT_BUFFER_SIZE = 128;

	private String boundRefKey;
	private final ImString startBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString endBuffer = new ImString(INPUT_BUFFER_SIZE);
	private String validationError;

	private final EventPropertiesPresenter presenter;
	private final Supplier<BeatBlockContext> context;

	public BuildLayerClipPropertyEditor() {
		this(PresenterFactories.eventPropertiesPresenter(), BeatBlock::getContext);
	}

	BuildLayerClipPropertyEditor(EventPropertiesPresenter presenter, Supplier<BeatBlockContext> context) {
		this.presenter = presenter;
		this.context = context;
	}

	public void renderBody(EventPropertiesRef ref, Timeline timeline, TimelineEditor editor) {
		if (ref == null || ref.clip() == null) {
			return;
		}

		String rk = EventPropertiesRef.refKey(ref);
		if (!rk.equals(boundRefKey)) {
			bindBuffers(ref, timeline);
		}

		var summary = presenter.buildBuildLayerClipSummary(ref, timeline, layerManager());

		ImGui.textDisabled(BBTexts.get("beatblock.build_layer.track"));
		ImGui.sameLine();
		ImGui.text(summary.trackLabel());
		ImGui.textDisabled(BBTexts.get("beatblock.build_layer.clip_id"));
		ImGui.sameLine();
		ImGui.text(ref.clip().getId());

		if (summary.layerName() != null && !summary.layerName().isBlank()) {
			ImGui.textDisabled(BBTexts.get("beatblock.build_layer.bound_layer"));
			ImGui.sameLine();
			ImGui.text(summary.layerName());
		} else {
			ImGui.textDisabled(BBTexts.get("beatblock.build_layer.no_bound_layer"));
		}

		if (summary.eventCount() > 0) {
			ImGui.textDisabled(BBTexts.get("beatblock.build_layer.event_count"));
			ImGui.sameLine();
			ImGui.text(String.valueOf(summary.eventCount()));
		}

		ImGui.separator();
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.properties.clip.start") + "##buildLayerStart", startBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.properties.clip.end") + "##buildLayerEnd", endBuffer);

		if (validationError != null && !validationError.isBlank()) {
			ImGui.spacing();
			ImGui.textColored(1f, 0.45f, 0.45f, 1f, validationError);
		}

		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.common.apply") + "##buildLayerApply", 120f, 0f)) {
			apply(ref, timeline, editor);
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.common.reset") + "##buildLayerReset", 120f, 0f)) {
			bindBuffers(ref, timeline);
			validationError = null;
		}
	}

	private void apply(EventPropertiesRef ref, Timeline timeline, TimelineEditor editor) {
		if (editor == null) {
			validationError = BBTexts.get("beatblock.common.timeline_editor_not_initialized");
			return;
		}
		double start = parseDouble(startBuffer.get(), ref.clip().getStartTimeSeconds());
		double end = parseDouble(endBuffer.get(), ref.clip().getEndTimeSeconds());
		var result = presenter.applyClipTiming(
			ref,
			timeline,
			editor.getCommandManager(),
			start,
			end,
			null
		);
		if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
			validationError = message;
		} else {
			validationError = null;
		}
	}

	private void bindBuffers(EventPropertiesRef ref, Timeline timeline) {
		boundRefKey = EventPropertiesRef.refKey(ref);
		var form = presenter.buildClipTimingFormSnapshot(ref, timeline);
		startBuffer.set(form.start());
		endBuffer.set(form.end());
	}

	private BuildLayerManager layerManager() {
		BeatBlockContext runtime = context.get();
		return runtime != null ? runtime.buildLayerManager() : null;
	}

	private static double parseDouble(String raw, double fallback) {
		if (raw == null || raw.isBlank()) {
			return fallback;
		}
		try {
			return Double.parseDouble(raw.trim());
		} catch (NumberFormatException ex) {
			return fallback;
		}
	}
}
