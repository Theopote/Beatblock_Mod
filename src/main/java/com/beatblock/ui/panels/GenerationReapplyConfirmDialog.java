package com.beatblock.ui.panels;

import com.beatblock.timeline.generation.GenerationReapplyGuard;
import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;
import org.jspecify.annotations.Nullable;

/**
 * 覆盖已有自动生成内容前的二次确认弹窗。
 */
public final class GenerationReapplyConfirmDialog {

	private static final String POPUP_ID = "##GenerationReapplyConfirm";

	private boolean openRequested;
	private GenerationReapplyGuard.@Nullable Kind kind;
	private int affectedCount;
	private @Nullable Runnable onConfirm;

	public void request(GenerationReapplyGuard.Kind kind, int affectedCount, Runnable onConfirm) {
		if (kind == null || onConfirm == null) {
			return;
		}
		this.kind = kind;
		this.affectedCount = Math.max(0, affectedCount);
		this.onConfirm = onConfirm;
		this.openRequested = true;
	}

	public void render() {
		if (openRequested) {
			ImGui.openPopup(POPUP_ID);
			openRequested = false;
		}
		if (!ImGui.beginPopupModal(POPUP_ID)) {
			return;
		}

		ImGui.text(BBTexts.get("beatblock.generation.reapply.confirm_title"));
		ImGui.separator();
		if (kind != null) {
			ImGui.textWrapped(BBTexts.get(GenerationReapplyGuard.messageKey(kind), affectedCount));
		}
		ImGui.spacing();

		if (ImGui.button(BBTexts.get("beatblock.common.confirm") + "##generationReapplyConfirm", 150f, 0f)) {
			if (onConfirm != null) {
				onConfirm.run();
			}
			clear();
			ImGui.closeCurrentPopup();
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.common.cancel") + "##generationReapplyCancel", 120f, 0f)) {
			clear();
			ImGui.closeCurrentPopup();
		}
		ImGui.endPopup();
	}

	private void clear() {
		kind = null;
		affectedCount = 0;
		onConfirm = null;
	}
}
