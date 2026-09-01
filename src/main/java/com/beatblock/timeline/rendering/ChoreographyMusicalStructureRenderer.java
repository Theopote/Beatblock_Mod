package com.beatblock.timeline.rendering;

import java.util.HashMap;
import java.util.Map;

import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.automap.choreography.MusicalStructureMapper;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;

/**
 * 绘制编舞计划的音乐结构层：标尺区乐句色带 + 内容区小节竖线 / 乐句背景。
 */
public final class ChoreographyMusicalStructureRenderer {

	private static final int PHRASE_FILL_ALPHA = 0x44;
	private static final int PHRASE_FILL_ALPHA_REPEAT = 0x55;
	private static final int PHRASE_BORDER_ALPHA = 0xAA;
	private static final int PHRASE_CONTENT_ALPHA = 0x14;
	private static final int BAR_LINE_COLOR = 0x55_55_99_DD;
	private static final int BAR_LABEL_COLOR = 0xAA_88_BB_FF;
	private static final float MIN_BAR_PX = 8f;
	private static final float MIN_PHRASE_LABEL_PX = 24f;
	private static final int[] REPEAT_GROUP_RGB = {
		0xFF_AA_66,
		0x66_CC_FF,
		0xFF_66_AA,
		0xAA_FF_66,
		0xCC_88_FF,
		0xFF_CC_44,
	};

	private ChoreographyMusicalStructureRenderer() {}

	public static void renderPhraseBand(
		Timeline timeline,
		TimelineViewState viewState,
		TimelineLayout layout,
		float regionLeft,
		float regionRight
	) {
		ChoreographyPlan plan = loadPlan(timeline);
		if (plan == null || plan.musicalStructure().phrases().isEmpty()) return;

		float top = ChoreographySectionBandLayout.phraseBandTop(layout);
		float bottom = ChoreographySectionBandLayout.phraseBandBottom(layout);
		int hoveredPhrase = findPhraseIndexAt(plan, viewState, layout, ImGui.getMousePosX(), ImGui.getMousePosY());
		Map<Integer, Integer> repeatGroupByPhrase = indexRepeatGroups(plan);

		for (ChoreographyPlan.MusicalPhrasePlan phrase : plan.musicalStructure().phrases()) {
			float left = timeToScreenX(viewState, regionLeft, phrase.startSeconds());
			float right = timeToScreenX(viewState, regionLeft, phrase.endSeconds());
			float x0 = Math.max(regionLeft, Math.min(left, right));
			float x1 = Math.min(regionRight, Math.max(left, right));
			if (x1 <= x0) continue;

			int repeatGroupId = repeatGroupByPhrase.getOrDefault(phrase.phraseIndex(), -1);
			boolean inRepeatGroup = repeatGroupId >= 0;
			boolean repeatMember = phrase.repeatAnchorPhraseIndex() >= 0;
			boolean hovered = phrase.phraseIndex() == hoveredPhrase;
			int fillAlpha = hovered ? PHRASE_FILL_ALPHA + 0x22 : (inRepeatGroup ? PHRASE_FILL_ALPHA_REPEAT : PHRASE_FILL_ALPHA);
			int fill = colorForPhrase(phrase, fillAlpha);
			ImGui.getWindowDrawList().addRectFilled(x0, top, x1, bottom, fill);

			int border = borderColorForPhrase(plan, phrase, repeatGroupId, PHRASE_BORDER_ALPHA);
			ImGui.getWindowDrawList().addRect(x0, top, x1, bottom, border, 0f, 0, inRepeatGroup ? 1.5f : 1f);

			if (x1 - x0 > MIN_PHRASE_LABEL_PX) {
				String label = repeatMember
					? "P" + phrase.phraseIndex() + " \u21BB"
					: "P" + phrase.phraseIndex();
				ImGui.getWindowDrawList().addText(x0 + 3f, top + 1f, border, label);
			}
		}
	}

	public static void renderPhraseContentBackground(
		Timeline timeline,
		TimelineViewState viewState,
		TimelineLayout layout
	) {
		ChoreographyPlan plan = loadPlan(timeline);
		if (plan == null || plan.musicalStructure().phrases().isEmpty()) return;

		float top = layout.contentTop;
		float bottom = layout.contentTop + layout.contentHeight;
		float left = layout.contentLeft;
		float right = left + layout.contentWidth;

		for (ChoreographyPlan.MusicalPhrasePlan phrase : plan.musicalStructure().phrases()) {
			float x0 = timeToScreenX(viewState, left, phrase.startSeconds());
			float x1 = timeToScreenX(viewState, left, phrase.endSeconds());
			float regionLeft = Math.max(left, Math.min(x0, x1));
			float regionRight = Math.min(right, Math.max(x0, x1));
			if (regionRight <= regionLeft) continue;
			ImGui.getWindowDrawList().addRectFilled(
				regionLeft, top, regionRight, bottom, colorForPhrase(phrase, PHRASE_CONTENT_ALPHA));
		}
	}

	public static void renderBarGrid(Timeline timeline, TimelineViewState viewState, TimelineLayout layout) {
		ChoreographyPlan plan = loadPlan(timeline);
		if (plan == null || plan.musicalStructure().bars().isEmpty()) return;

		float top = layout.contentTop;
		float bottom = layout.contentTop + layout.contentHeight;
		float left = layout.contentLeft;
		float right = left + layout.contentWidth;

		for (ChoreographyPlan.BarPlan bar : plan.musicalStructure().bars()) {
			float x = timeToScreenX(viewState, left, bar.startSeconds());
			if (x < left - 2 || x > right + 2) continue;

			float barPx = (float) ((bar.endSeconds() - bar.startSeconds()) * viewState.getZoom());
			if (barPx < MIN_BAR_PX) continue;

			ImGui.getWindowDrawList().addLine(x, top, x, bottom, BAR_LINE_COLOR, 1f);

			if (barPx >= 28f) {
				ImGui.getWindowDrawList().addText(x + 2f, top + 2f, BAR_LABEL_COLOR, "B" + (bar.barIndex() + 1));
			}
		}
	}

	public static void applyPhraseHoverCursor(
		ChoreographyPlan plan,
		TimelineViewState viewState,
		TimelineLayout layout,
		float mouseX,
		float mouseY
	) {
		if (findPhraseIndexAt(plan, viewState, layout, mouseX, mouseY) >= 0) {
			ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
		}
	}

	/**
	 * 乐句色带点击时解析所属段落索引，供段落编舞弹窗预选。
	 *
	 * @return section index, or -1 when not over a phrase or no plan
	 */
	public static int resolveSectionIndexForPhraseClick(
		Timeline timeline,
		TimelineViewState viewState,
		TimelineLayout layout,
		float mouseX,
		float mouseY
	) {
		ChoreographyPlan plan = loadPlan(timeline);
		if (plan == null) return -1;
		int phraseIndex = findPhraseIndexAt(plan, viewState, layout, mouseX, mouseY);
		if (phraseIndex < 0) return -1;
		return resolveSectionIndexForPhrase(plan, phraseIndex);
	}

	static int resolveSectionIndexForPhrase(ChoreographyPlan plan, int phraseIndex) {
		for (ChoreographyPlan.MusicalPhrasePlan phrase : plan.musicalStructure().phrases()) {
			if (phrase.phraseIndex() != phraseIndex) continue;
			if (phrase.sectionIndex() >= 0 && phrase.sectionIndex() < plan.sections().size()) {
				return phrase.sectionIndex();
			}
			double mid = (phrase.startSeconds() + phrase.endSeconds()) * 0.5;
			return MusicalStructureMapper.resolveSectionIndex(plan.sections(), mid);
		}
		return -1;
	}

	public static void renderPhraseHoverTooltip(
		Timeline timeline,
		TimelineViewState viewState,
		TimelineLayout layout,
		float mouseX,
		float mouseY
	) {
		if (!ChoreographySectionBandLayout.isInPhraseBand(mouseY, layout)) return;
		ChoreographyPlan plan = loadPlan(timeline);
		if (plan == null) return;

		int phraseIndex = findPhraseIndexAt(plan, viewState, layout, mouseX, mouseY);
		if (phraseIndex < 0) return;

		ChoreographyPlan.MusicalPhrasePlan phrase = plan.musicalStructure().phrases().stream()
			.filter(p -> p.phraseIndex() == phraseIndex)
			.findFirst()
			.orElse(null);
		if (phrase == null) return;

		String sectionLabel = phrase.sectionIndex() >= 0 && phrase.sectionIndex() < plan.sections().size()
			? plan.sections().get(phrase.sectionIndex()).label()
			: "-";
		int repeatGroupId = repeatGroupIdForPhrase(plan, phrase.phraseIndex());
		if (repeatGroupId >= 0) {
			ImGui.setTooltip(BBTexts.get(
				"beatblock.musical_structure.phrase.repeat_tooltip",
				phrase.phraseIndex(),
				phrase.startSeconds(),
				phrase.endSeconds(),
				phrase.repetitionScore() * 100.0,
				sectionLabel,
				repeatGroupId,
				phrase.repeatAnchorPhraseIndex()
			));
			return;
		}
		ImGui.setTooltip(BBTexts.get(
			"beatblock.musical_structure.phrase.tooltip",
			phrase.phraseIndex(),
			phrase.startSeconds(),
			phrase.endSeconds(),
			phrase.repetitionScore() * 100.0,
			sectionLabel,
			phrase.repeatAnchorPhraseIndex()
		));
	}

	static int findPhraseIndexAt(
		ChoreographyPlan plan,
		TimelineViewState viewState,
		TimelineLayout layout,
		float mouseX,
		float mouseY
	) {
		if (plan == null || viewState == null || layout == null) return -1;
		if (!ChoreographySectionBandLayout.isInPhraseBand(mouseY, layout)) return -1;

		double time = viewState.screenToTime(mouseX - layout.contentLeft);
		for (ChoreographyPlan.MusicalPhrasePlan phrase : plan.musicalStructure().phrases()) {
			boolean withinEnd = phrase.phraseIndex() == plan.musicalStructure().phrases().size() - 1
				? time <= phrase.endSeconds()
				: time < phrase.endSeconds();
			if (time >= phrase.startSeconds() && withinEnd) {
				return phrase.phraseIndex();
			}
		}
		return -1;
	}

	private static ChoreographyPlan loadPlan(Timeline timeline) {
		if (timeline == null) return null;
		return ChoreographyPlanStore.loadPlan(timeline);
	}

	private static float timeToScreenX(TimelineViewState viewState, float regionLeft, double timeSeconds) {
		return regionLeft + viewState.timeToScreen(timeSeconds);
	}

	static int colorForPhrase(ChoreographyPlan.MusicalPhrasePlan phrase, int alpha) {
		int rgb = 0x66_CC_AA;
		if (phrase.sectionIndex() >= 0) {
			rgb = switch (phrase.sectionIndex() % 4) {
				case 0 -> 0x66_CC_AA;
				case 1 -> 0x88_AA_FF;
				case 2 -> 0xCC_AA_88;
				default -> 0xAA_88_CC;
			};
		}
		return (alpha << 24) | rgb;
	}

	static int repeatGroupIdForPhrase(ChoreographyPlan plan, int phraseIndex) {
		if (plan == null) return -1;
		for (ChoreographyPlan.RepeatGroup group : plan.musicalStructure().repeats()) {
			if (group.phraseIndices().contains(phraseIndex)) {
				return group.repeatGroupId();
			}
		}
		return -1;
	}

	static Map<Integer, Integer> indexRepeatGroups(ChoreographyPlan plan) {
		Map<Integer, Integer> byPhrase = new HashMap<>();
		if (plan == null) return byPhrase;
		for (ChoreographyPlan.RepeatGroup group : plan.musicalStructure().repeats()) {
			for (int phraseIndex : group.phraseIndices()) {
				byPhrase.put(phraseIndex, group.repeatGroupId());
			}
		}
		return byPhrase;
	}

	static int colorForRepeatGroup(int repeatGroupId, int alpha) {
		int rgb = REPEAT_GROUP_RGB[Math.floorMod(repeatGroupId, REPEAT_GROUP_RGB.length)];
		return (alpha << 24) | rgb;
	}

	static int borderColorForPhrase(
		ChoreographyPlan plan,
		ChoreographyPlan.MusicalPhrasePlan phrase,
		int repeatGroupId,
		int alpha
	) {
		if (repeatGroupId >= 0) {
			return colorForRepeatGroup(repeatGroupId, alpha);
		}
		return colorForPhrase(phrase, alpha);
	}
}
