package com.beatblock.ui.presenter;

import com.beatblock.automap.AutoMapConfig;
import com.beatblock.automap.choreography.ChoreographyPlan;
import com.beatblock.automap.choreography.ChoreographyCompileOptions;
import com.beatblock.automap.choreography.ChoreographyPlanCompiler;
import com.beatblock.automap.choreography.ChoreographyPlanEditor;
import com.beatblock.automap.choreography.ChoreographyPlanStore;
import com.beatblock.automap.choreography.SectionEditProfile;
import com.beatblock.runtime.BeatBlockContext;
import com.beatblock.timeline.Timeline;
import com.beatblock.ui.i18n.BBTexts;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 时间轴 section 编舞编辑业务逻辑。
 */
public final class TimelineSectionEditPresenter {

	public static final String[] MOTION_ANIMATION_IDS = {
		"bounce", "slide", "pulse", "spin", "fade"
	};

	public record SectionView(
		int index,
		String label,
		double startSeconds,
		double endSeconds,
		int motionCount,
		int cameraCount,
		int vfxCount
	) {}

	public record ApplyOutcome(PresenterResult result, int animationEvents, int cameraEvents, int vfxEvents) {}

	private final Supplier<BeatBlockContext> context;

	public TimelineSectionEditPresenter(Supplier<BeatBlockContext> context) {
		this.context = context;
	}

	public Timeline currentTimeline() {
		return context.get().timeline();
	}

	public String unavailableReason() {
		Timeline timeline = currentTimeline();
		if (timeline == null) {
			return BBTexts.get("beatblock.message.timeline_unavailable");
		}
		if (!ChoreographyPlanStore.hasPlan(timeline)) {
			return BBTexts.get("beatblock.section_edit.no_plan");
		}
		return null;
	}

	public boolean canEdit() {
		return unavailableReason() == null;
	}

	public List<SectionView> listSections() {
		ChoreographyPlan plan = loadPlan();
		if (plan == null) return List.of();
		List<SectionView> views = new ArrayList<>(plan.sections().size());
		for (int i = 0; i < plan.sections().size(); i++) {
			var section = plan.sections().get(i);
			views.add(new SectionView(
				i,
				section.label().isBlank() ? section.sectionType().name() : section.label(),
				section.startSeconds(),
				section.endSeconds(),
				ChoreographyPlanEditor.motionPhrasesInSection(plan, i).size(),
				ChoreographyPlanEditor.cameraPhrasesInSection(plan, i).size(),
				ChoreographyPlanEditor.vfxPhrasesInSection(plan, i).size()
			));
		}
		return views;
	}

	public SectionEditProfile loadEditProfile(int sectionIndex) {
		ChoreographyPlan plan = loadPlan();
		if (plan == null) return SectionEditProfile.defaults(sectionIndex);
		SectionEditProfile existing = ChoreographyPlanEditor.editForSection(plan, sectionIndex);
		return existing != null ? existing : SectionEditProfile.defaults(sectionIndex);
	}

	public ApplyOutcome applySectionEdit(int sectionIndex, SectionEditProfile edit) {
		String blocked = unavailableReason();
		if (blocked != null) {
			return new ApplyOutcome(PresenterResult.failure(blocked), 0, 0, 0);
		}
		Timeline timeline = currentTimeline();
		ChoreographyPlan plan = loadPlan();
		AutoMapConfig config = loadConfig();
		if (timeline == null || plan == null || config == null) {
			return new ApplyOutcome(PresenterResult.failure(BBTexts.get("beatblock.section_edit.no_plan")), 0, 0, 0);
		}

		plan = ChoreographyPlanEditor.withSectionEdit(plan, edit.withSectionIndex(sectionIndex));
		plan = ChoreographyPlanEditor.bakePhraseOverrides(plan);
		ChoreographyPlanStore.save(timeline, plan, config);

		var compiled = ChoreographyPlanCompiler.compileAll(
			timeline, plan, config, ChoreographyCompileOptions.smartAutoMap());
		var editor = context.get().timelineEditor();
		if (editor != null) {
			editor.syncClockDuration();
		}
		return new ApplyOutcome(
			PresenterResult.success(BBTexts.get("beatblock.section_edit.applied")),
			compiled.animationEvents(),
			compiled.cameraEvents(),
			compiled.vfxEvents()
		);
	}

	private ChoreographyPlan loadPlan() {
		return ChoreographyPlanStore.loadPlan(currentTimeline());
	}

	private AutoMapConfig loadConfig() {
		AutoMapConfig config = ChoreographyPlanStore.loadConfig(currentTimeline());
		return config != null ? config : AutoMapConfig.createDefault();
	}
}
