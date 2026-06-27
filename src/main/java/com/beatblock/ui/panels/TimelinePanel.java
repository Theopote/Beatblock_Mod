package com.beatblock.ui.panels;

import com.beatblock.BeatBlock;
import com.beatblock.runtime.BeatBlockContext;
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

import java.util.function.Supplier;

/**
 * 底部通栏时间线面板：固定工具栏 + 固定时间刻度，轨道区在可滚动子窗口中一行一行显示。
 */
public class TimelinePanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;
	private final TimelineToolbar toolbar = new TimelineToolbar();
	private final TimelinePanelPresenter presenter;
	private final Supplier<BeatBlockContext> context;

	public TimelinePanel() {
		this(PresenterFactories.timelinePanelPresenter(), BeatBlock::getContext);
	}

	TimelinePanel(TimelinePanelPresenter presenter, Supplier<BeatBlockContext> context) {
		this.presenter = presenter;
		this.context = context;
	}

	private BeatBlockContext runtime() {
		return context.get();
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.timelinePanelWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.timelinePanelWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			double musicDuration = runtime().musicPlayer() != null
				? runtime().musicPlayer().getDurationSeconds()
				: 0.0;
			TimelinePanelPresenter.TimelinePanelViewState viewState = presenter.viewState(
				runtime().timeline(),
				runtime().timelineEditor(),
				musicDuration
			);
			if (!viewState.timelineLoaded()) {
				ImGui.text("时间线（未加载模型）");
				return;
			}

			TimelineEditor editor = runtime().timelineEditor();

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
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.timelinePanelWindow());
		}
	}
}
