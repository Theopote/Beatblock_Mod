package com.beatblock.ui.properties.editors;

import com.beatblock.BeatBlock;
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

/** 音频轨道片段属性编辑器。 */
public final class AudioClipPropertyEditor {

	private static final int INPUT_BUFFER_SIZE = 256;

	private String boundRefKey;
	private final ImString labelBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString pathBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString startBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString endBuffer = new ImString(INPUT_BUFFER_SIZE);
	private String validationError;

	private final EventPropertiesPresenter presenter;
	private final Supplier<BeatBlockContext> context;

	public AudioClipPropertyEditor() {
		this(PresenterFactories.eventPropertiesPresenter(), BeatBlock::getContext);
	}

	AudioClipPropertyEditor(EventPropertiesPresenter presenter, Supplier<BeatBlockContext> context) {
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

		ImGui.textDisabled(BBTexts.get("beatblock.audio.clip_id"));
		ImGui.sameLine();
		ImGui.text(ref.clip().getId());
		ImGui.separator();

		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.audio.label") + "##audioLabel", labelBuffer);

		ImGui.setNextItemWidth(-1f);
		ImGui.beginDisabled();
		ImGui.inputText(BBTexts.get("beatblock.audio.path") + "##audioPath", pathBuffer);
		ImGui.endDisabled();

		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.properties.clip.start") + "##audioStart", startBuffer);
		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.properties.clip.end") + "##audioEnd", endBuffer);

		if (validationError != null && !validationError.isBlank()) {
			ImGui.spacing();
			ImGui.textColored(1f, 0.45f, 0.45f, 1f, validationError);
		}

		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.common.apply") + "##audioApply", 120f, 0f)) {
			apply(ref, timeline, editor);
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.common.reset") + "##audioReset", 120f, 0f)) {
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
		var result = presenter.applyAudioClipProperties(
			ref,
			timeline,
			editor.getCommandManager(),
			start,
			end,
			labelBuffer.get()
		);
		if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
			validationError = message;
		} else {
			validationError = null;
			bindBuffers(ref, timeline);
		}
	}

	private void bindBuffers(EventPropertiesRef ref, Timeline timeline) {
		boundRefKey = EventPropertiesRef.refKey(ref);
		var form = presenter.buildAudioClipFormSnapshot(ref, timeline);
		labelBuffer.set(form.label());
		pathBuffer.set(form.path());
		startBuffer.set(form.start());
		endBuffer.set(form.end());
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
