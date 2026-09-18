package com.beatblock.ui.panels;

import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.preferences.UiPreferences;
import imgui.ImGui;
import imgui.ImGuiViewport;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import org.jspecify.annotations.Nullable;

/**
 * BeatBlock 启动首页：新建音乐视频、打开工程、空白高级编辑三条主路径。
 */
public final class CreatorHomePanel {

	private static final float WINDOW_WIDTH = 480f;
	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoResize;

	public record Actions(
		Runnable newMusicVideo,
		Runnable openProject,
		Runnable blankAdvancedEditor
	) {
		public static Actions noop() {
			return new Actions(() -> {}, () -> {}, () -> {});
		}

		public static Actions of(
			@Nullable Runnable newMusicVideo,
			@Nullable Runnable openProject,
			@Nullable Runnable blankAdvancedEditor
		) {
			return new Actions(
				newMusicVideo != null ? newMusicVideo : () -> {},
				openProject != null ? openProject : () -> {},
				blankAdvancedEditor != null ? blankAdvancedEditor : () -> {}
			);
		}
	}

	private final Actions actions;
	private final ImBoolean windowOpen = new ImBoolean(false);
	private final ImBoolean skipOnStartup = new ImBoolean(true);
	private boolean autoOpenTriggered;

	public CreatorHomePanel() {
		this(Actions.noop());
	}

	public CreatorHomePanel(Actions actions) {
		this.actions = actions != null ? actions : Actions.noop();
	}

	public void open() {
		windowOpen.set(true);
	}

	public void close() {
		windowOpen.set(false);
	}

	public boolean isOpen() {
		return windowOpen.get();
	}

	/** 首次进入 BeatBlock 且环境设置已确认时自动展示。 */
	public void onUiOpened(boolean environmentSetupOpen) {
		if (autoOpenTriggered || environmentSetupOpen || windowOpen.get()) {
			return;
		}
		if (!UiPreferences.isPythonSetupAcknowledged()) {
			return;
		}
		if (UiPreferences.isCreatorHomeDismissed()) {
			return;
		}
		autoOpenTriggered = true;
		windowOpen.set(true);
	}

	public void render() {
		if (!windowOpen.get()) {
			return;
		}

		renderBackdrop();

		ImGui.setNextWindowSize(WINDOW_WIDTH, 0, ImGuiCond.Always);
		ImGuiViewport viewport = ImGui.getMainViewport();
		ImGui.setNextWindowPos(
			viewport.getWorkPosX() + viewport.getWorkSizeX() * 0.5f,
			viewport.getWorkPosY() + viewport.getWorkSizeY() * 0.42f,
			ImGuiCond.Always,
			0.5f,
			0.42f
		);

		ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f);
		if (!ImGui.begin(BBTexts.get("beatblock.creator_home.title"), windowOpen, WINDOW_FLAGS)) {
			ImGui.popStyleVar();
			ImGui.end();
			return;
		}
		ImGui.popStyleVar();

		try {
			ImGui.textDisabled(BBTexts.get("beatblock.creator_home.tagline"));
			ImGui.spacing();
			ImGui.textWrapped(BBTexts.get("beatblock.creator_home.desc"));
			ImGui.spacing();
			ImGui.separator();
			ImGui.spacing();

			renderPrimaryButton(
				BBTexts.get("beatblock.creator_home.new_music_video"),
				BBTexts.get("beatblock.creator_home.new_music_video.tooltip"),
				"##creatorHomeNewVideo",
				() -> completeAction(actions.newMusicVideo())
			);
			ImGui.spacing();

			if (ImGui.button(BBTexts.get("beatblock.creator_home.open_project") + "##creatorHomeOpen", -1f, 36f)) {
				completeAction(actions.openProject());
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.creator_home.open_project.tooltip"));
			}

			ImGui.spacing();
			if (ImGui.button(BBTexts.get("beatblock.creator_home.blank_editor") + "##creatorHomeBlank", -1f, 36f)) {
				completeAction(actions.blankAdvancedEditor());
			}
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.creator_home.blank_editor.tooltip"));
			}

			ImGui.spacing();
			ImGui.separator();
			ImGui.spacing();
			ImGui.checkbox(BBTexts.get("beatblock.creator_home.skip_on_startup"), skipOnStartup);

			ImGui.spacing();
			if (ImGui.button(BBTexts.get("beatblock.common.close") + "##creatorHomeClose", -1f, 0f)) {
				dismiss();
			}
		} finally {
			ImGui.end();
		}
	}

	private static void renderPrimaryButton(String label, String tooltip, String id, Runnable action) {
		ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f);
		ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1f);
		ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.15f, 1f);
		if (ImGui.button(label + id, -1f, 40f)) {
			action.run();
		}
		ImGui.popStyleColor(3);
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(tooltip);
		}
	}

	private void completeAction(Runnable action) {
		persistSkipPreference();
		windowOpen.set(false);
		action.run();
	}

	private void dismiss() {
		persistSkipPreference();
		windowOpen.set(false);
	}

	private void persistSkipPreference() {
		if (skipOnStartup.get()) {
			UiPreferences.setCreatorHomeDismissed(true);
		}
	}

	private static void renderBackdrop() {
		ImGuiViewport viewport = ImGui.getMainViewport();
		float x0 = viewport.getWorkPosX();
		float y0 = viewport.getWorkPosY();
		float x1 = x0 + viewport.getWorkSizeX();
		float y1 = y0 + viewport.getWorkSizeY();
		ImGui.getBackgroundDrawList().addRectFilled(x0, y0, x1, y1, 0xB0000000);
	}
}
