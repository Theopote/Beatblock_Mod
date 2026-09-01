package com.beatblock.timeline.rendering;

import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyPlanEditor;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.automap.choreography.SectionPlanSource;
import com.beatblock.automap.engine.SectionType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.editor.TimelineViewState;
import com.beatblock.timeline.interaction.ChoreographySectionHitTest;
import com.beatblock.ui.i18n.BBTexts;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;

/**
 * 绘制编舞计划的音乐段落色带：标尺区可交互条带 + 轨道内容区背景同步。
 */
public final class ChoreographySectionBandRenderer {

	private static final int RULER_FILL_ALPHA = 0x55;
	private static final int RULER_FILL_ALPHA_HOVER = 0x88;
	private static final int CONTENT_FILL_ALPHA = 0x24;

	private ChoreographySectionBandRenderer() {}

	public static void render(
		Timeline timeline,
		TimelineViewState viewState,
		TimelineLayout layout,
		float rTop,
		float rBot,
		float rLeft
	) {
		if (timeline == null || viewState == null || layout == null) return;
		ChoreographyPlan plan = ChoreographyPlanStore.loadPlan(timeline);
		if (plan == null || plan.sections().isEmpty()) return;

		float bandTop = ChoreographySectionBandLayout.sectionBandTop(layout);
		float clipRight = rLeft + layout.rulerWidth;
		ChoreographySectionHitTest.Hit hit = ChoreographySectionHitTest.hit(
			timeline, viewState, layout, ImGui.getMousePosX(), ImGui.getMousePosY());
		int hoveredSection = hit.isBody() ? hit.sectionIndex() : -1;
		int hoveredBoundary = hit.isBoundary() ? hit.boundaryIndex() : -1;

		drawSections(
			plan,
			viewState,
			rLeft,
			clipRight,
			bandTop,
			rBot - 1,
			hoveredSection,
			hoveredBoundary,
			true,
			true,
			true,
			RULER_FILL_ALPHA,
			RULER_FILL_ALPHA_HOVER
		);
	}

	/** 在轨道内容区绘制与标尺色带对齐的段落背景。 */
	public static void renderContentBackground(
		Timeline timeline,
		TimelineViewState viewState,
		TimelineLayout layout
	) {
		if (timeline == null || viewState == null || layout == null) return;
		ChoreographyPlan plan = ChoreographyPlanStore.loadPlan(timeline);
		if (plan == null || plan.sections().isEmpty()) return;

		float top = layout.contentTop;
		float bottom = layout.contentTop + layout.contentHeight;
		float left = layout.contentLeft;
		float right = left + layout.contentWidth;

		drawSections(
			plan,
			viewState,
			left,
			right,
			top,
			bottom,
			-1,
			-1,
			false,
			false,
			true,
			CONTENT_FILL_ALPHA,
			CONTENT_FILL_ALPHA
		);
	}

	private static void drawSections(
		ChoreographyPlan plan,
		TimelineViewState viewState,
		float regionLeft,
		float regionRight,
		float top,
		float bottom,
		int hoveredSection,
		int hoveredBoundary,
		boolean drawLabels,
		boolean drawBorders,
		boolean drawBoundaryLines,
		int fillAlpha,
		int fillAlphaHover
	) {
		for (int i = 0; i < plan.sections().size(); i++) {
			ChoreographyPlan.SectionPlan section = plan.sections().get(i);
			float xStart = regionLeft + viewState.timeToScreen(section.startSeconds());
			float xEnd = regionLeft + viewState.timeToScreen(section.endSeconds());
			float left = Math.max(regionLeft, Math.min(xStart, xEnd));
			float right = Math.min(regionRight, Math.max(xStart, xEnd));
			if (right <= left) continue;

			boolean hovered = i == hoveredSection;
			int alpha = hovered ? fillAlphaHover : fillAlpha;
			if (section.source() == SectionPlanSource.LOCKED) {
				alpha = Math.min(0xFF, alpha + 0x18);
			} else if (section.confidence() < 0.55) {
				alpha = Math.max(0x22, alpha - 0x18);
			}
			int fillColor = colorForSection(section.sectionType(), alpha);
			ImGui.getWindowDrawList().addRectFilled(left, top, right, bottom, fillColor);

			if (drawBorders) {
				int borderColor = colorForSection(section.sectionType(), 0xCC);
				if (section.source() == SectionPlanSource.LOCKED) {
					borderColor = 0xCC_FF_D7_00;
				} else if (section.source() == SectionPlanSource.USER_EDITED) {
					borderColor = 0xCC_FF_FF_88;
				} else if (section.confidence() < 0.55) {
					borderColor = 0xCC_FF_88_44;
				}
				float borderWidth = section.source() == SectionPlanSource.LOCKED ? 2f : 1f;
				ImGui.getWindowDrawList().addRect(left, top, right, bottom, borderColor, 0f, 0, borderWidth);
			}

			if (drawLabels) {
				String label = section.label().isBlank() ? section.sectionType().name() : section.label();
				if (right - left > 28f) {
					int borderColor = colorForSection(section.sectionType(), 0xCC);
					ImGui.getWindowDrawList().addText(left + 3f, top + 2f, borderColor, label);
				}
			}
		}

		if (!drawBoundaryLines) return;
		for (int boundaryIndex = 1; boundaryIndex < plan.sections().size(); boundaryIndex++) {
			double boundaryTime = plan.sections().get(boundaryIndex).startSeconds();
			float x = regionLeft + viewState.timeToScreen(boundaryTime);
			if (x < regionLeft - 2 || x > regionRight + 2) continue;
			boolean active = boundaryIndex == hoveredBoundary;
			ChoreographyPlan.SectionPlan left = plan.sections().get(boundaryIndex - 1);
			ChoreographyPlan.SectionPlan right = plan.sections().get(boundaryIndex);
			boolean locked = left.source() == SectionPlanSource.LOCKED || right.source() == SectionPlanSource.LOCKED;
			int color = locked ? 0x55_FF_88_44 : (active ? 0xFF_FF_CC_44 : (drawLabels ? 0xAA_FF_FF_FF : 0x55_FF_FF_FF));
			float width = active ? 2f : 1f;
			ImGui.getWindowDrawList().addLine(x, top, x, bottom, color, width);
		}
	}

	public static void renderHoverTooltip(Timeline timeline, ChoreographySectionHitTest.Hit hit) {
		if (timeline == null || hit == null) return;
		ChoreographyPlan plan = ChoreographyPlanStore.loadPlan(timeline);
		if (plan == null) return;

		if (hit.isBoundary()) {
			int leftIndex = hit.boundaryIndex() - 1;
			int rightIndex = hit.boundaryIndex();
			if (leftIndex < 0 || rightIndex >= plan.sections().size()) return;
			ChoreographyPlan.SectionPlan left = plan.sections().get(leftIndex);
			ChoreographyPlan.SectionPlan right = plan.sections().get(rightIndex);
			String leftLabel = sectionLabel(left);
			String rightLabel = sectionLabel(right);
			ImGui.setTooltip(BBTexts.get(
				"beatblock.section_edit.boundary.tooltip",
				leftLabel,
				rightLabel,
				plan.sections().get(hit.boundaryIndex()).startSeconds(),
				ChoreographyPlanEditor.canMoveBoundary(plan, hit.boundaryIndex())
					? BBTexts.get("beatblock.section_edit.boundary.draggable")
					: BBTexts.get("beatblock.section_edit.boundary.locked")
			));
			return;
		}

		if (hit.isBody()) {
			ChoreographyPlan.SectionPlan section = plan.sections().get(hit.sectionIndex());
			int motion = ChoreographyPlanEditor.motionPhrasesInSection(plan, hit.sectionIndex()).size();
			int camera = ChoreographyPlanEditor.cameraPhrasesInSection(plan, hit.sectionIndex()).size();
			int vfx = ChoreographyPlanEditor.vfxPhrasesInSection(plan, hit.sectionIndex()).size();
			ImGui.setTooltip(BBTexts.get(
				"beatblock.section_edit.band.tooltip",
				sectionLabel(section),
				section.sectionType().name(),
				section.startSeconds(),
				section.endSeconds(),
				(int) Math.round(section.confidence() * 100.0),
				sourceLabel(section.source()),
				motion,
				camera,
				vfx
			));
		}
	}

	public static void applyHoverCursor(ChoreographyPlan plan, ChoreographySectionHitTest.Hit hit) {
		if (hit != null && hit.isBoundary()) {
			if (plan != null && ChoreographyPlanEditor.canMoveBoundary(plan, hit.boundaryIndex())) {
				ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
			}
		}
	}

	private static String sourceLabel(SectionPlanSource source) {
		if (source == null) return "";
		return switch (source) {
			case ANALYZED -> BBTexts.get("beatblock.section_edit.source.analyzed");
			case USER_EDITED -> BBTexts.get("beatblock.section_edit.source.user_edited");
			case LOCKED -> BBTexts.get("beatblock.section_edit.source.locked");
		};
	}

	private static String sectionLabel(ChoreographyPlan.SectionPlan section) {
		return section.label().isBlank() ? section.sectionType().name() : section.label();
	}

	static int colorForSection(SectionType type, int alpha) {
		int rgb = switch (type) {
			case INTRO, OUTRO -> 0x88_AA_FF;
			case VERSE, BREAK -> 0x66_CC_FF;
			case PRE_CHORUS, BUILD -> 0x66_FF_CC;
			case CHORUS, DROP -> 0x66_66_FF;
			case BRIDGE -> 0xAA_88_FF;
		};
		return (alpha << 24) | rgb;
	}
}
