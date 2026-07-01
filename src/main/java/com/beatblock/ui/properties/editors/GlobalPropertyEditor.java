package com.beatblock.ui.properties.editors;

import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.GlobalEventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.EventPropertiesPresenter;
import com.beatblock.ui.presenter.EventPropertiesRef;
import com.beatblock.ui.presenter.PresenterFactories;
import imgui.ImGui;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.function.Supplier;

/** 全局事件属性编辑器。 */
public final class GlobalPropertyEditor {

	private static final int INPUT_BUFFER_SIZE = 128;

	private String boundRefKey;
	private final ImString timeBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImString nameBuffer = new ImString(INPUT_BUFFER_SIZE);
	private final ImInt typeIndex = new ImInt(0);
	private String validationError;

	private final EventPropertiesPresenter presenter;
	private final Supplier<BeatBlockContext> context;

	public GlobalPropertyEditor() {
		this(PresenterFactories.eventPropertiesPresenter(), () -> null);
	}

	GlobalPropertyEditor(EventPropertiesPresenter presenter, Supplier<BeatBlockContext> context) {
		this.presenter = presenter;
		this.context = context;
	}

	public void renderBody(EventPropertiesRef ref, Timeline timeline, TimelineEditor editor) {
		if (ref == null || ref.event() == null) {
			return;
		}

		String rk = EventPropertiesRef.refKey(ref);
		if (!rk.equals(boundRefKey)) {
			bindBuffers(ref);
		}

		ImGui.textDisabled(BBTexts.get("beatblock.event.track"));
		ImGui.sameLine();
		ImGui.text(ref.track().getName().isBlank() ? ref.track().getId() : ref.track().getName());
		ImGui.textDisabled(BBTexts.get("beatblock.event.event_id"));
		ImGui.sameLine();
		ImGui.text(ref.event().getId());
		ImGui.separator();

		boolean trackLocked = presenter.isTrackLocked(timeline, editor, ref.track().getId());
		if (trackLocked) {
			ImGui.textDisabled(BBTexts.get("beatblock.event.track_locked"));
			ImGui.separator();
			ImGui.beginDisabled();
		}

		ImGui.text(BBTexts.get("beatblock.global.type"));
		ImGui.setNextItemWidth(-1f);
		ImGui.combo("##globalType", typeIndex, globalTypeLabels());

		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.global.name") + "##globalName", nameBuffer);

		ImGui.setNextItemWidth(-1f);
		ImGui.inputText(BBTexts.get("beatblock.event.start_time") + "##globalTime", timeBuffer);

		if (validationError != null && !validationError.isBlank()) {
			ImGui.spacing();
			ImGui.textColored(1f, 0.45f, 0.45f, 1f, validationError);
		}

		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.common.apply") + "##globalApply", 120f, 0f)) {
			apply(ref, timeline, editor);
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.common.reset") + "##globalReset", 120f, 0f)) {
			bindBuffers(ref);
			validationError = null;
		}

		if (trackLocked) {
			ImGui.endDisabled();
		}
	}

	private void apply(EventPropertiesRef ref, Timeline timeline, TimelineEditor editor) {
		if (editor == null) {
			validationError = BBTexts.get("beatblock.common.timeline_editor_not_initialized");
			return;
		}
		GlobalEventType type = GlobalEventType.values()[typeIndex.get()];
		var result = presenter.applyGlobalEvent(
			ref,
			timeline,
			editor.getCommandManager(),
			parseDouble(timeBuffer.get(), ref.event().getTimeSeconds()),
			type,
			nameBuffer.get()
		);
		if (result instanceof EventPropertiesPresenter.ApplyResult.Err(String message)) {
			validationError = message;
		} else {
			validationError = null;
		}
	}

	private void bindBuffers(EventPropertiesRef ref) {
		boundRefKey = EventPropertiesRef.refKey(ref);
		var form = presenter.buildGlobalFormSnapshot(ref);
		timeBuffer.set(form.time());
		nameBuffer.set(form.name());
		typeIndex.set(form.typeIndex());
	}

	private static String[] globalTypeLabels() {
		return BBTexts.labels(
			"beatblock.global.type.stage",
			"beatblock.global.type.lighting",
			"beatblock.global.type.special"
		);
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
