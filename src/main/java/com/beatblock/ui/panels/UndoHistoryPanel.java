package com.beatblock.ui.panels;

import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.layout.BeatBlockDockPanelBegin;
import com.beatblock.ui.layout.BeatBlockDockSpaceLayoutBuilder;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.UndoHistoryPanelPresenter;
import imgui.ImGui;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;

import java.util.List;

/** 撤销/重做历史面板：展示可撤销与可重做命令列表。 */
public final class UndoHistoryPanel {

	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	private final UndoHistoryPanelPresenter presenter;

	public UndoHistoryPanel() {
		this(PresenterFactories.undoHistoryPanelPresenter());
	}

	UndoHistoryPanel(UndoHistoryPanelPresenter presenter) {
		this.presenter = presenter;
	}

	public void render(ImBoolean pOpen) {
		if (!pOpen.get()) {
			BeatBlockDockPanelBegin.markClosed(BeatBlockDockSpaceLayoutBuilder.undoHistoryWindow());
			return;
		}
		if (!BeatBlockDockPanelBegin.begin(BeatBlockDockSpaceLayoutBuilder.undoHistoryWindow(), pOpen, WINDOW_FLAGS)) {
			return;
		}
		try {
			var state = presenter.viewState();
			ImGui.text(BBTexts.get("beatblock.undo_history.title"));
			ImGui.separator();

			if (!state.editorReady()) {
				ImGui.textDisabled(BBTexts.get("beatblock.common.timeline_not_initialized"));
				return;
			}

			if (ImGui.button(BBTexts.get("beatblock.menu.undo") + "##undoHistUndo")) {
				presenter.undo();
			}
			ImGui.sameLine();
			if (ImGui.button(BBTexts.get("beatblock.menu.redo") + "##undoHistRedo")) {
				presenter.redo();
			}

			ImGui.spacing();
			ImGui.textColored(0.4f, 0.8f, 1f, 1f,
				BBTexts.get("beatblock.undo_history.undo_section", state.undoCount()));
			renderCommandList(state.undoDescriptions());

			ImGui.spacing();
			ImGui.textColored(0.7f, 0.9f, 0.7f, 1f,
				BBTexts.get("beatblock.undo_history.redo_section", state.redoCount()));
			renderCommandList(state.redoDescriptions());

			if (state.undoCount() == 0 && state.redoCount() == 0) {
				ImGui.textDisabled(BBTexts.get("beatblock.undo_history.empty"));
			}
		} finally {
			BeatBlockDockPanelBegin.endWithRecord(BeatBlockDockSpaceLayoutBuilder.undoHistoryWindow());
		}
	}

	private static void renderCommandList(List<String> descriptions) {
		if (descriptions.isEmpty()) {
			ImGui.textDisabled(BBTexts.get("beatblock.undo_history.none"));
			return;
		}
		if (ImGui.beginChild("##undoList" + descriptions.hashCode(), 0, Math.min(descriptions.size() * 22f + 8f, 160f), true)) {
			for (int i = 0; i < descriptions.size(); i++) {
				ImGui.text((i == 0 ? "▶ " : "  ") + descriptions.get(i));
			}
		}
		ImGui.endChild();
	}
}
