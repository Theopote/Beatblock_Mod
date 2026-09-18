package com.beatblock.timeline.rendering;

import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.presenter.TimelineTransportPresenter;
import imgui.ImGui;

/** Creator 工具栏：预览当前时间线演出。 */
final class TimelineToolbarPreviewControls {

	private final TimelineTransportPresenter transport;

	TimelineToolbarPreviewControls(TimelineTransportPresenter transport) {
		this.transport = transport;
	}

	void render(TimelineEditor editor) {
		if (editor == null) {
			return;
		}
		boolean playing = transport.viewState(editor, ImGui.getIO().getKeyShift()).playing();
		String label = playing
			? BBTexts.get("beatblock.timeline.preview_stop")
			: BBTexts.get("beatblock.timeline.preview");
		if (ImGui.button(label + "##tlPreview")) {
			if (playing) {
				transport.stop(editor);
			} else {
				transport.play(editor);
			}
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.timeline.preview.tooltip"));
		}
	}
}
