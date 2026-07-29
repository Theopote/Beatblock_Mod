package com.beatblock.timeline.rendering;

import com.beatblock.timeline.TimelineEditor;
import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;

final class TimelineToolbarOverflowMenu {

	static final String POPUP_ID = "tlMorePopup";

	private final TimelineToolbarLoopSpeedControls loopSpeed;
	private final TimelineToolbarSnapGridControls snapGrid;
	private final TimelineToolbarViewControls view;
	private final TimelineToolbarRecordControls recordControls;
	private final TimelineDemucsMappingControls demucsControls;

	TimelineToolbarOverflowMenu(
		TimelineToolbarLoopSpeedControls loopSpeed,
		TimelineToolbarSnapGridControls snapGrid,
		TimelineToolbarViewControls view,
		TimelineToolbarRecordControls recordControls,
		TimelineDemucsMappingControls demucsControls
	) {
		this.loopSpeed = loopSpeed;
		this.snapGrid = snapGrid;
		this.view = view;
		this.recordControls = recordControls;
		this.demucsControls = demucsControls;
	}

	void renderButtonAndPopup(TimelineEditor editor, TimelineToolbarState toolbarState, double seekStep) {
		if (ImGui.button(BBTexts.get("beatblock.timeline.more") + "##tlMore")) {
			ImGui.openPopup(POPUP_ID);
		}
		if (!ImGui.beginPopup(POPUP_ID)) return;

		double now = editor.getClock().getCurrentTimeSeconds();
		loopSpeed.renderCompact(editor, toolbarState, seekStep, now);
		snapGrid.renderCompact(toolbarState);
		view.renderCompact(editor);
		recordControls.renderCompact(editor, toolbarState, editor.getClock().isPlaying());
		demucsControls.render(true, null);

		ImGui.endPopup();
	}
}
