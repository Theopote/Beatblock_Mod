package com.beatblock.timeline.generation;

import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Modal: when a drop/apply resolves to multiple StageObjects, ask how to bind.
 * <p>
 * Options: primary only / all selected. Group Event is listed as coming soon (disabled).
 */
public final class AnimationMultiTargetDropPrompt {

	public static final String POPUP_ID = "##BB_AnimMultiTargetDrop";

	/**
	 * @param applyChosen receives expanded target ids; return written event count (&gt;0 = success)
	 */
	public record Pending(
		String itemDisplayName,
		List<String> candidateTargetIds,
		Function<List<String>, Integer> applyChosen
	) {
		public Pending {
			Objects.requireNonNull(itemDisplayName, "itemDisplayName");
			candidateTargetIds = List.copyOf(candidateTargetIds != null ? candidateTargetIds : List.of());
			Objects.requireNonNull(applyChosen, "applyChosen");
		}
	}

	private static @Nullable Pending pending;
	private static boolean openRequested;
	/** 0 = PRIMARY, 1 = ALL (recommended default). */
	private static final ImInt selectedOption = new ImInt(1);

	private AnimationMultiTargetDropPrompt() {}

	public static void request(Pending next) {
		if (next == null || next.candidateTargetIds().size() < 2) {
			return;
		}
		pending = next;
		selectedOption.set(1);
		openRequested = true;
	}

	public static boolean hasPending() {
		return pending != null;
	}

	public static void cancel() {
		pending = null;
		openRequested = false;
	}

	/** Call once per frame from the main UI loop (e.g. next to toasts). */
	public static void render() {
		if (openRequested && pending != null) {
			ImGui.openPopup(POPUP_ID);
			openRequested = false;
		}
		if (pending == null) {
			return;
		}

		ImGui.setNextWindowSize(420f, 0f, ImGuiCond.Appearing);
		if (!ImGui.beginPopupModal(POPUP_ID, ImGuiWindowFlags.AlwaysAutoResize)) {
			if (!ImGui.isPopupOpen(POPUP_ID)) {
				pending = null;
			}
			return;
		}

		Pending current = pending;
		List<String> candidates = current.candidateTargetIds();

		ImGui.text(BBTexts.get("beatblock.animation_library.multi_target.title"));
		ImGui.separator();
		ImGui.textWrapped(BBTexts.get(
			"beatblock.animation_library.multi_target.body",
			current.itemDisplayName(),
			candidates.size()
		));
		ImGui.spacing();

		if (ImGui.radioButton(
			BBTexts.get("beatblock.animation_library.multi_target.primary") + "##animMultiPrimary",
			selectedOption.get() == 0
		)) {
			selectedOption.set(0);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get(
				"beatblock.animation_library.multi_target.primary.tooltip",
				candidates.getFirst()
			));
		}

		if (ImGui.radioButton(
			BBTexts.get("beatblock.animation_library.multi_target.all") + "##animMultiAll",
			selectedOption.get() == 1
		)) {
			selectedOption.set(1);
		}
		if (ImGui.isItemHovered()) {
			ImGui.setTooltip(BBTexts.get("beatblock.animation_library.multi_target.all.tooltip"));
		}

		ImGui.beginDisabled();
		ImGui.radioButton(
			BBTexts.get("beatblock.animation_library.multi_target.group") + "##animMultiGroup",
			false
		);
		ImGui.endDisabled();
		if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
			ImGui.setTooltip(BBTexts.get("beatblock.animation_library.multi_target.group.tooltip"));
		}

		ImGui.spacing();
		ImGui.separator();
		ImGui.spacing();

		if (ImGui.button(BBTexts.get("beatblock.common.confirm") + "##animMultiOk", 140f, 0f)) {
			AnimationMultiTargetChoice choice = selectedOption.get() == 0
				? AnimationMultiTargetChoice.PRIMARY
				: AnimationMultiTargetChoice.ALL;
			List<String> expanded = AnimationMultiTargetChoice.expand(candidates, choice);
			try {
				current.applyChosen().apply(expanded);
			} finally {
				pending = null;
				ImGui.closeCurrentPopup();
			}
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.common.cancel") + "##animMultiCancel", 120f, 0f)) {
			pending = null;
			ImGui.closeCurrentPopup();
		}

		ImGui.endPopup();
	}
}
