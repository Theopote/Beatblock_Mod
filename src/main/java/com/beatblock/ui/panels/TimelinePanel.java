package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.TimelinePanelPresenter;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.rendering.TimelineToolbar;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

/**
 * 底部通栏时间线面板：固定工具栏 + 固定时间刻度，轨道区在可滚动子窗口中一行一行显示。
 */
public class TimelinePanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private final TimelineToolbar toolbar = new TimelineToolbar();
	private final TimelinePanelPresenter presenter;

	public TimelinePanel() {
		this(PresenterFactories.timelinePanelPresenter());
	}

	TimelinePanel(TimelinePanelPresenter presenter) {
		this.presenter = presenter;
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.TIMELINE_PANEL_WINDOW);
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.TIMELINE_PANEL_WINDOW, pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			double musicDuration = BeatBlock.musicPlayer != null
				? BeatBlock.musicPlayer.getDurationSeconds()
				: 0.0;
			TimelinePanelPresenter.TimelinePanelViewState viewState = presenter.viewState(
				BeatBlock.timeline,
				BeatBlock.timelineEditor,
				musicDuration
			);
			if (!viewState.timelineLoaded()) {
				ImGui.text("时间线（未加载模型）");
				return;
			}

			TimelineEditor editor = BeatBlock.timelineEditor;

			if (editor != null) {
				toolbar.render(editor, editor.getToolbarState());
			}
			String timeDisplay = viewState.positionDisplay();
			ImVec2 timeSize = ImGui.calcTextSize(timeDisplay);
			float rightPadding = 12f;
			float targetX = Math.max(ImGui.getCursorPosX(), ImGui.getWindowContentRegionMaxX() - timeSize.x - rightPadding);
			ImGui.sameLine(targetX);
			ImGui.textDisabled(timeDisplay);

			ImGui.separator();
			if (editor != null) {
				editor.beginFrameLayout();
				editor.renderRulerOnly();
				editor.handleRulerInteraction();
				editor.tryBeginTimelineDividerDragOnRuler();
			}

			if (ImGui.beginChild("##TimelineTracks", 0, -1, false)) {
				if (editor != null) {
					editor.renderTrackArea();
				}
			}
			ImGui.endChild();
			if (editor != null) {
				editor.renderPlayheadOverlay();
			}
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.TIMELINE_PANEL_WINDOW);
		}
	}
}
