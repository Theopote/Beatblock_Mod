package com.beatblock.ui.panels;

import com.beatblock.ui.i18n.BBTexts;
import com.beatblock.ui.notification.ToastNotificationSystem;
import com.beatblock.ui.preferences.UiPreferences;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.QuickStartWizardPresenter;
import com.beatblock.ui.util.AudioFilePicker;
import imgui.ImGui;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.jspecify.annotations.Nullable;

/**
 * 快速开始向导：引导新用户 5 分钟内完成第一个作品。
 */
public final class QuickStartWizardPanel {

	private static final int PATH_CAPACITY = 512;
	private static final float WINDOW_WIDTH = 520f;
	private static final int WINDOW_FLAGS = ImGuiWindowFlags.NoCollapse;

	/**
	 * DONE 页导航：全部接到正式 Creator 路径（Play / Timeline / AutoMap / Save），
	 * 不在向导内实现独立保存或播放逻辑。
	 */
	public record DoneActions(
		Runnable playPreview,
		Runnable editTimeline,
		Runnable editChoreography,
		Runnable saveProject
	) {
		public static DoneActions noop() {
			return new DoneActions(() -> {}, () -> {}, () -> {}, () -> {});
		}

		public static DoneActions of(
			@Nullable Runnable playPreview,
			@Nullable Runnable editTimeline,
			@Nullable Runnable editChoreography,
			@Nullable Runnable saveProject
		) {
			return new DoneActions(
				playPreview != null ? playPreview : () -> {},
				editTimeline != null ? editTimeline : () -> {},
				editChoreography != null ? editChoreography : () -> {},
				saveProject != null ? saveProject : () -> {}
			);
		}
	}

	private final QuickStartWizardPresenter presenter;
	private final DoneActions doneActions;
	private final ImString musicPath = new ImString(PATH_CAPACITY);
	private final ImString stageObjectName = new ImString(64);
	private final ImInt creationTypeIndex = new ImInt(3);
	private final ImBoolean windowOpen = new ImBoolean(false);
	private boolean autoOpenTriggered;
	private boolean skippedImportOnOpen;
	private boolean stageObjectNameSynced;

	public QuickStartWizardPanel() {
		this(PresenterFactories.quickStartWizardPresenter(), DoneActions.noop());
	}

	public QuickStartWizardPanel(Runnable onPlayPreview) {
		this(PresenterFactories.quickStartWizardPresenter(), DoneActions.of(onPlayPreview, null, null, null));
	}

	public QuickStartWizardPanel(DoneActions doneActions) {
		this(PresenterFactories.quickStartWizardPresenter(), doneActions);
	}

	QuickStartWizardPanel(QuickStartWizardPresenter presenter) {
		this(presenter, DoneActions.noop());
	}

	QuickStartWizardPanel(QuickStartWizardPresenter presenter, Runnable onPlayPreview) {
		this(presenter, DoneActions.of(onPlayPreview, null, null, null));
	}

	QuickStartWizardPanel(QuickStartWizardPresenter presenter, DoneActions doneActions) {
		this.presenter = presenter;
		this.doneActions = doneActions != null ? doneActions : DoneActions.noop();
	}

	public void open() {
		var session = presenter.prepareOpen();
		musicPath.set(session.audioPath());
		creationTypeIndex.set(presenter.indexForCreationType(presenter.viewState().creationType()));
		stageObjectName.set("");
		stageObjectNameSynced = false;
		skippedImportOnOpen = session.skippedImport();
		windowOpen.set(true);
	}

	public void onUiOpened(boolean environmentSetupOpen) {
		if (autoOpenTriggered || environmentSetupOpen) {
			return;
		}
		if (UiPreferences.isQuickStartWizardAcknowledged()) {
			return;
		}
		if (!UiPreferences.isPythonSetupAcknowledged()) {
			return;
		}
		autoOpenTriggered = true;
		open();
	}

	public void render() {
		if (!windowOpen.get()) {
			return;
		}

		ImGui.setNextWindowSize(WINDOW_WIDTH, 0, ImGuiCond.Always);
		ImGui.setNextWindowPos(ImGui.getIO().getDisplaySizeX() * 0.5f, ImGui.getIO().getDisplaySizeY() * 0.35f,
			ImGuiCond.FirstUseEver, 0.5f, 0.35f);

		if (!ImGui.begin(BBTexts.get("beatblock.wizard.title"), windowOpen, WINDOW_FLAGS)) {
			ImGui.end();
			return;
		}

		float contentWidth = WINDOW_WIDTH - ImGui.getStyle().getWindowPaddingX() * 2f;
		ImGui.pushItemWidth(contentWidth);

		try {
			if (!windowOpen.get()) {
				presenter.reset();
				return;
			}

			ImGui.textDisabled(BBTexts.get("beatblock.wizard.tagline"));
			ImGui.spacing();
			renderStepIndicator(presenter.viewState().step());
			renderAnalysisBanner();
			ImGui.separator();
			ImGui.spacing();

			switch (presenter.viewState().step()) {
				case IMPORT -> renderImportStep();
				case CHOOSE_TYPE -> renderTypeStep();
				case SELECT_BLOCKS -> renderSelectStep();
				case GENERATING -> renderGeneratingStep();
				case GENERATE, DONE -> renderGenerateStep();
			}

			if (!presenter.viewState().statusMessage().isBlank()) {
				ImGui.spacing();
				ImGui.textWrapped(presenter.viewState().statusMessage());
			}
		} finally {
			ImGui.popItemWidth();
			ImGui.end();
		}
	}

	private void renderStepIndicator(QuickStartWizardPresenter.Step current) {
		String[] labels = BBTexts.labels(
			"beatblock.wizard.step.import",
			"beatblock.wizard.step.type",
			"beatblock.wizard.step.select",
			"beatblock.wizard.step.generate"
		);
		int currentIndex = switch (current) {
			case IMPORT -> 0;
			case CHOOSE_TYPE -> 1;
			case SELECT_BLOCKS -> 2;
			case GENERATE, GENERATING, DONE -> 3;
		};
		for (int i = 0; i < labels.length; i++) {
			if (i > 0) {
				ImGui.sameLine(0f, ImGui.getStyle().getItemInnerSpacingX());
			}
			String mark = i < currentIndex ? "✓ " : i == currentIndex ? "▶ " : "";
			String label = mark + (i + 1) + " " + labels[i];
			float[] color = i < currentIndex
				? new float[]{0.4f, 1f, 0.4f, 1f}
				: i == currentIndex
					? new float[]{0.4f, 0.8f, 1f, 1f}
					: new float[]{0.55f, 0.55f, 0.55f, 1f};
			ImGui.textColored(color[0], color[1], color[2], color[3], label);
		}
	}

	/**
	 * 向导顶部常驻分析感知：用户在选类型 / 框选时可并行看到后台进度，避免到 Generate 才困惑。
	 */
	private void renderAnalysisBanner() {
		var analysis = presenter.analysisViewState();
		if (analysis.state() == QuickStartWizardPresenter.WizardAnalysisState.NONE) {
			return;
		}

		ImGui.spacing();
		String song = presenter.currentAudioFileName();
		if (song == null || song.isBlank()) {
			song = BBTexts.get("beatblock.wizard.analysis.banner_audio_fallback");
		}

		switch (analysis.state()) {
			case QUEUED -> {
				ImGui.textColored(1f, 0.75f, 0.3f, 1f,
					BBTexts.get("beatblock.wizard.analysis.banner_queued", song));
				ImGui.progressBar(0f, -1f, 6f, "");
			}
			case ANALYZING -> {
				ImGui.textColored(1f, 0.75f, 0.3f, 1f,
					BBTexts.get("beatblock.wizard.analysis.banner_analyzing", song, analysis.percent()));
				float fraction = Math.max(0f, Math.min(1f, analysis.percent() / 100f));
				ImGui.progressBar(fraction, -1f, 6f, "");
				if (analysis.message() != null && !analysis.message().isBlank()) {
					ImGui.textDisabled(analysis.message());
				}
			}
			case READY -> ImGui.textColored(0.45f, 0.9f, 0.45f, 1f,
				BBTexts.get("beatblock.wizard.analysis.banner_ready", song));
			case FAILED -> ImGui.textColored(1f, 0.4f, 0.4f, 1f,
				BBTexts.get("beatblock.wizard.analysis.banner_failed", song));
			case MISSING_AUDIO -> ImGui.textColored(1f, 0.4f, 0.4f, 1f,
				BBTexts.get("beatblock.wizard.analysis.banner_missing"));
			case NONE -> {
				// already returned
			}
		}
	}

	private void renderImportStep() {
		if (skippedImportOnOpen) {
			ImGui.textWrapped(BBTexts.get("beatblock.wizard.music_already_loaded"));
			ImGui.spacing();
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f);
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1f);
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.15f, 1f);
			if (ImGui.button(BBTexts.get("beatblock.wizard.continue") + "##wizardContinueLoaded", -1f, 32f)) {
				presenter.continueWithLoadedMusic();
				skippedImportOnOpen = false;
			}
			ImGui.popStyleColor(3);
			ImGui.spacing();
			renderWizardFooter(true);
			return;
		}

		ImGui.textWrapped(BBTexts.get("beatblock.wizard.import.desc"));
		ImGui.spacing();

		ImGui.text(BBTexts.get("beatblock.wizard.import.path"));
		ImGui.setNextItemWidth(-120f);
		ImGui.inputText("##wizardMusicPath", musicPath);
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.wizard.browse") + "##wizardBrowse")) {
			String chosen = AudioFilePicker.choose(musicPath, msg -> presenter.goToStep(QuickStartWizardPresenter.Step.IMPORT));
			if (chosen != null && !chosen.isBlank()) {
				musicPath.set(chosen);
			}
		}

		ImGui.spacing();
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.15f, 1f);
		if (ImGui.button(BBTexts.get("beatblock.wizard.import.button") + "##wizardImport", -1f, 32f)) {
			presenter.importMusic(musicPath.get());
			if (presenter.viewState().step() != QuickStartWizardPresenter.Step.IMPORT) {
				ToastNotificationSystem.showSuccess(BBTexts.get("beatblock.toast.wizard.music_imported"));
			} else if (!presenter.viewState().statusMessage().isBlank()) {
				ToastNotificationSystem.showError(presenter.viewState().statusMessage());
			}
		}
		ImGui.popStyleColor(3);

		ImGui.spacing();
		renderWizardFooter(true);
	}

	private void renderTypeStep() {
		ImGui.textWrapped(BBTexts.get("beatblock.wizard.type.desc"));
		ImGui.spacing();

		String[] typeLabels = BBTexts.labels(
			"beatblock.wizard.style.cinematic",
			"beatblock.wizard.style.rhythmic",
			"beatblock.wizard.style.drop",
			"beatblock.wizard.style.full"
		);
		ImGui.setNextItemWidth(-1f);
		if (ImGui.combo(BBTexts.get("beatblock.wizard.type.label") + "##wizardType", creationTypeIndex, typeLabels)) {
			presenter.setCreationType(switch (creationTypeIndex.get()) {
				case 0 -> QuickStartWizardPresenter.CreationType.CINEMATIC_BUILD;
				case 1 -> QuickStartWizardPresenter.CreationType.RHYTHMIC_PERFORMANCE;
				case 2 -> QuickStartWizardPresenter.CreationType.DROP_IMPACT;
				default -> QuickStartWizardPresenter.CreationType.FULL_CHOREOGRAPHY;
			});
		}

		String tooltip = switch (creationTypeIndex.get()) {
			case 0 -> BBTexts.get("beatblock.wizard.style.cinematic.tooltip");
			case 1 -> BBTexts.get("beatblock.wizard.style.rhythmic.tooltip");
			case 2 -> BBTexts.get("beatblock.wizard.style.drop.tooltip");
			default -> BBTexts.get("beatblock.wizard.style.full.tooltip");
		};
		if (ImGui.isItemHovered()) ImGui.setTooltip(tooltip);

		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.wizard.back") + "##wizardBackType")) {
			presenter.goToStep(QuickStartWizardPresenter.Step.IMPORT);
			skippedImportOnOpen = presenter.viewState().musicLoaded();
		}
		ImGui.sameLine();
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.15f, 1f);
		if (ImGui.button(BBTexts.get("beatblock.wizard.next") + "##wizardNextType")) {
			presenter.advanceFromTypeStep();
		}
		ImGui.popStyleColor(3);
		ImGui.spacing();
		renderWizardFooter(false);
	}

	private void renderSelectStep() {
		var guide = presenter.selectionGuideState();

		if (guide.phase() == QuickStartWizardPresenter.SelectionPhase.IDLE) {
			ImGui.textWrapped(BBTexts.get("beatblock.wizard.select.idle.desc"));
			ImGui.spacing();
			ImGui.textColored(1f, 0.75f, 0.3f, 1f, BBTexts.get("beatblock.wizard.select.idle.hint"));
			ImGui.spacing();

			ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f);
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1f);
			ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.15f, 1f);
			if (ImGui.button(BBTexts.get("beatblock.wizard.select.start") + "##wizardStartSelect", -1f, 36f)) {
				presenter.startSelecting();
			}
			ImGui.popStyleColor(3);
			if (ImGui.isItemHovered()) {
				ImGui.setTooltip(BBTexts.get("beatblock.wizard.select.start.tooltip"));
			}

			ImGui.spacing();
			if (ImGui.button(BBTexts.get("beatblock.wizard.back") + "##wizardBackSelect")) {
				presenter.goToStep(QuickStartWizardPresenter.Step.CHOOSE_TYPE);
			}
			ImGui.spacing();
			renderWizardFooter(false);
			return;
		}

		ImGui.textWrapped(BBTexts.get("beatblock.wizard.select.selecting.desc"));
		ImGui.spacing();

		if (guide.blockCount() > 0) {
			ImGui.textColored(0.4f, 1f, 0.4f, 1f,
				BBTexts.get("beatblock.wizard.select.current_count", guide.blockCount()));
			if (guide.hasBounds()) {
				ImGui.text(BBTexts.get("beatblock.wizard.select.bounds",
					guide.sizeX(), guide.sizeY(), guide.sizeZ()));
			}
		} else {
			ImGui.textColored(1f, 0.75f, 0.3f, 1f, BBTexts.get("beatblock.wizard.select.selecting.hint"));
		}

		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.wizard.select.clear") + "##wizardClearSelect")) {
			presenter.clearSelection();
		}
		ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.wizard.select.reselect") + "##wizardReselect")) {
			presenter.reselect();
		}

		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.wizard.back") + "##wizardBackSelect")) {
			presenter.goToStep(QuickStartWizardPresenter.Step.CHOOSE_TYPE);
		}
		ImGui.sameLine();
		boolean canContinue = guide.canContinue();
		if (!canContinue) ImGui.beginDisabled();
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.15f, 1f);
		if (ImGui.button(BBTexts.get("beatblock.wizard.select.continue") + "##wizardNextSelect")) {
			presenter.advanceFromSelectStep();
			stageObjectNameSynced = false;
		}
		ImGui.popStyleColor(3);
		if (!canContinue) ImGui.endDisabled();
		if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled) && !canContinue) {
			ImGui.setTooltip(BBTexts.get("beatblock.wizard.select_blocks_hint"));
		}
		ImGui.spacing();
		renderWizardFooter(false);
	}

	private void renderGenerateStep() {
		var state = presenter.viewState();
		if (state.step() == QuickStartWizardPresenter.Step.DONE) {
			renderDoneStep();
			return;
		}

		ImGui.textWrapped(BBTexts.get("beatblock.wizard.generate.desc"));
		ImGui.spacing();

		var plan = presenter.generationPlan();
		if (!stageObjectNameSynced) {
			stageObjectName.set(plan.objectName());
			stageObjectNameSynced = true;
		}

		ImGui.text(BBTexts.get("beatblock.wizard.plan.selected", plan.selectionCount()));
		ImGui.spacing();
		ImGui.textDisabled(BBTexts.get("beatblock.wizard.plan.will_create"));
		ImGui.spacing();

		ImGui.setNextItemWidth(-1f);
		if (ImGui.inputText(BBTexts.get("beatblock.wizard.plan.object_name") + "##wizardObjectName", stageObjectName)) {
			presenter.setStageObjectName(stageObjectName.get());
		}
		if (ImGui.isItemDeactivatedAfterEdit()) {
			presenter.setStageObjectName(stageObjectName.get());
		}

		ImGui.text(BBTexts.get("beatblock.wizard.plan.style", plan.styleLabel()));
		ImGui.text(BBTexts.get("beatblock.wizard.plan.animation", plan.animationSummary()));
		ImGui.text(BBTexts.get("beatblock.wizard.plan.camera", plan.cameraSummary()));
		ImGui.text(BBTexts.get("beatblock.wizard.plan.vfx", plan.vfxSummary()));

		renderAnalysisStatus();

		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.wizard.back") + "##wizardBackGenerate")) {
			presenter.goToStep(QuickStartWizardPresenter.Step.SELECT_BLOCKS);
			stageObjectNameSynced = false;
		}
		ImGui.sameLine();
		boolean canGenerate = presenter.canGenerate();
		if (!canGenerate) ImGui.beginDisabled();
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.15f, 1f);
		if (ImGui.button(BBTexts.get("beatblock.wizard.generate.button") + "##wizardGenerate", -1f, 32f)) {
			presenter.setStageObjectName(stageObjectName.get());
			presenter.beginGenerate();
			if (presenter.step() != QuickStartWizardPresenter.Step.GENERATING
				&& !presenter.viewState().statusMessage().isBlank()) {
				ToastNotificationSystem.showError(presenter.viewState().statusMessage());
			}
		}
		ImGui.popStyleColor(3);
		if (!canGenerate) ImGui.endDisabled();
		if (ImGui.isItemHovered(ImGuiHoveredFlags.AllowWhenDisabled)) {
			var analysis = presenter.analysisViewState();
			ImGui.setTooltip(canGenerate
				? BBTexts.get("beatblock.wizard.generate.tooltip")
				: analysis.message() != null && !analysis.message().isBlank()
					? analysis.message()
					: BBTexts.get("beatblock.wizard.analysis_pending"));
		}
		ImGui.spacing();
		renderWizardFooter(false);
	}

	private void renderDoneStep() {
		var summary = presenter.doneSummary();
		ImGui.textColored(0.4f, 1f, 0.4f, 1f, BBTexts.get("beatblock.wizard.done.title"));
		ImGui.textWrapped(BBTexts.get("beatblock.wizard.done.desc"));
		ImGui.spacing();

		if (!summary.objectName().isBlank()) {
			ImGui.textDisabled(summary.objectName());
			ImGui.spacing();
		}
		ImGui.bulletText(BBTexts.get("beatblock.wizard.done.stat.blocks", summary.blockCount()));
		ImGui.bulletText(BBTexts.get("beatblock.wizard.done.stat.animation", summary.animationEvents()));
		ImGui.bulletText(BBTexts.get("beatblock.wizard.done.stat.camera", summary.cameraShots()));
		ImGui.bulletText(BBTexts.get("beatblock.wizard.done.stat.vfx", summary.vfxEvents()));
		ImGui.spacing();

		ImGui.pushStyleColor(imgui.flag.ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonHovered, 0.3f, 0.7f, 0.3f, 1f);
		ImGui.pushStyleColor(imgui.flag.ImGuiCol.ButtonActive, 0.15f, 0.5f, 0.15f, 1f);
		if (ImGui.button(BBTexts.get("beatblock.wizard.done.play") + "##wizardPlayPreview", -1f, 32f)) {
			doneActions.playPreview().run();
		}
		ImGui.popStyleColor(3);

		if (ImGui.button(BBTexts.get("beatblock.wizard.done.edit_timeline") + "##wizardEditTimeline", -1f, 0f)) {
			doneActions.editTimeline().run();
			closeWizard(true);
		}
		if (ImGui.button(BBTexts.get("beatblock.wizard.done.edit_choreography") + "##wizardEditChoreography", -1f, 0f)) {
			doneActions.editChoreography().run();
			closeWizard(true);
		}
		if (ImGui.button(BBTexts.get("beatblock.wizard.done.save_project") + "##wizardSaveProject", -1f, 0f)) {
			doneActions.saveProject().run();
		}

		ImGui.spacing();
		if (ImGui.button(BBTexts.get("beatblock.wizard.done.finish") + "##wizardFinish", -1f, 32f)) {
			closeWizard(true);
		}
	}

	private void renderGeneratingStep() {
		var progress = presenter.generationProgress();
		ImGui.textColored(0.4f, 0.8f, 1f, 1f, BBTexts.get("beatblock.wizard.generating.title"));
		ImGui.spacing();
		ImGui.textWrapped(progress.message().isBlank()
			? BBTexts.get("beatblock.wizard.generating.working")
			: progress.message());
		ImGui.spacing();
		ImGui.progressBar(progress.fraction(), -1f, 18f, "");

		ImGui.spacing();
		ImGui.textDisabled(BBTexts.get("beatblock.wizard.generating.hint"));

		// 渲染本帧进度后再推进一阶段，保证用户至少看到当前文案一帧
		var outcome = presenter.tickGenerate();
		if (outcome != null) {
			if (outcome.result().ok()) {
				ToastNotificationSystem.showSuccess(presenter.viewState().statusMessage());
			} else if (!presenter.viewState().statusMessage().isBlank()) {
				ToastNotificationSystem.showError(presenter.viewState().statusMessage());
			}
		}
	}

	private void renderAnalysisStatus() {
		var analysis = presenter.analysisViewState();
		// QUEUED / ANALYZING 已由顶部 banner 展示，此处避免重复进度条
		switch (analysis.state()) {
			case READY -> ImGui.textColored(0.4f, 1f, 0.4f, 1f,
				analysis.message() != null ? analysis.message() : BBTexts.get("beatblock.wizard.music_imported"));
			case QUEUED, ANALYZING -> ImGui.textDisabled(BBTexts.get("beatblock.wizard.analysis.banner_hint"));
			case FAILED -> {
				String title = analysis.message() != null && !analysis.message().isBlank()
					? analysis.message()
					: BBTexts.get("beatblock.wizard.analysis.failed");
				ImGui.textColored(1f, 0.35f, 0.35f, 1f, title);
				ImGui.spacing();
				if (analysis.canRetry()) {
					if (ImGui.button(BBTexts.get("beatblock.wizard.analysis.retry") + "##wizardRetryAnalysis")) {
						var result = presenter.retryAnalysis();
						if (result.ok()) {
							ToastNotificationSystem.showSuccess(result.messageOrEmpty());
						} else if (!result.messageOrEmpty().isBlank()) {
							ToastNotificationSystem.showError(result.messageOrEmpty());
						}
					}
					ImGui.sameLine();
				}
				if (ImGui.button(BBTexts.get("beatblock.wizard.analysis.choose_another") + "##wizardChooseAudio")) {
					presenter.chooseAnotherAudio();
				}
			}
			case MISSING_AUDIO -> {
				ImGui.textColored(1f, 0.35f, 0.35f, 1f, BBTexts.get("beatblock.wizard.analysis.missing_audio"));
				ImGui.spacing();
				if (ImGui.button(BBTexts.get("beatblock.wizard.analysis.locate") + "##wizardLocateAudio")) {
					presenter.chooseAnotherAudio();
				}
			}
			case NONE -> ImGui.textColored(1f, 0.6f, 0.2f, 1f, BBTexts.get("beatblock.wizard.analysis_pending"));
		}
	}

	private void renderWizardFooter(boolean showSkip) {
		if (showSkip && ImGui.button(BBTexts.get("beatblock.wizard.skip_wizard") + "##wizardSkip")) {
			dismissWizard();
		}
		if (showSkip) ImGui.sameLine();
		if (ImGui.button(BBTexts.get("beatblock.wizard.close_wizard") + "##wizardCloseFooter")) {
			closeWizard(false);
		}
	}

	private void closeWizard(boolean completed) {
		windowOpen.set(false);
		presenter.reset();
		stageObjectNameSynced = false;
		skippedImportOnOpen = false;
		if (completed) {
			UiPreferences.setQuickStartWizardAcknowledged(true);
		}
	}

	private void dismissWizard() {
		UiPreferences.setQuickStartWizardAcknowledged(true);
		closeWizard(false);
	}
}
