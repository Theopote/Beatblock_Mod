package com.beatblock.timeline.rendering;

import com.beatblock.BeatBlock;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.binding.AnimationBindingRule;
import com.beatblock.ui.icons.Icons;
import com.beatblock.ui.imgui.IconButtonStyle;
import com.beatblock.ui.presenter.PresenterFactories;
import com.beatblock.ui.presenter.TimelineBindingEditorPresenter;
import com.beatblock.ui.presenter.TimelineToolbarActionsPresenter;
import com.beatblock.ui.presenter.TimelineToolbarConfigPresenter;
import com.beatblock.ui.presenter.TimelineToolbarViewPresenter;
import com.beatblock.ui.presenter.TimelineTransportPresenter;
import imgui.ImGui;
import imgui.type.ImBoolean;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 时间线顶部工具栏：播放控制、吸附选项、Beat 网格、Auto Map。
 * 参考专业 DCC（Blender / Unreal Sequencer）的 transport + 吸附条。
 */
public final class TimelineToolbar {

	private static final String TOOLTIP_PLAY = "播放 (空格)";
	private static final String TOOLTIP_PAUSE = "暂停";
	private static final String TOOLTIP_STOP = "停止并回到起点";
	private static final String TOOLTIP_TO_START = "回到开头";
	private static final String TOOLTIP_TO_END = "跳到结尾";
	private static final String TOOLTIP_BACK_BEAT = "后退 1 拍（无 BPM 时后退 1 秒）；按住 Shift 后退 5 秒";
	private static final String TOOLTIP_FWD_BEAT = "前进 1 拍（无 BPM 时前进 1 秒）；按住 Shift 前进 5 秒";
	private static final String TOOLTIP_PREV_EVENT = "跳到上一事件点";
	private static final String TOOLTIP_NEXT_EVENT = "跳到下一事件点";
	private static final String TOOLTIP_ADD_MARKER = "在当前时间创建 Marker；也可双击标尺空白处";
	private static final String TOOLTIP_LOOP_IN = "将当前时间设为循环起点；也可 Alt+左键点击标尺";
	private static final String TOOLTIP_LOOP_OUT = "将当前时间设为循环终点；也可 Alt+右键点击标尺";
	private static final String TOOLTIP_LOOP_CLEAR = "清除循环区间（保留 Loop 开关）";
	private static final String TOOLTIP_SPEED = "播放速度";
	private static final String TOOLTIP_SNAP = "拖拽事件时吸附到网格";
	private static final String TOOLTIP_BEAT_SNAP = "拖拽事件时吸附到节拍";
	private static final String TOOLTIP_BEAT_GRID = "显示节拍网格线";
	private static final String TOOLTIP_MAGNET = "吸附到其他事件/关键帧";
	private static final String TOOLTIP_AUTO_MAP = "根据频段事件自动生成动画事件（需先导入音乐）";
	private static final String TOOLTIP_BAKE_STEP = "将 dispatchModel=STEP 的事件烘焙为 N 个带绝对时间的普通 BURST 事件（可 Undo）；需 StageObject 与参考节拍";
	private static final String TOOLTIP_LOOP = "循环播放";
	private static final String TOOLTIP_FIT = "缩放至整段时长可见";
	private static final String TOOLTIP_ZOOM = "时间线横向缩放";
	private static final String TOOLTIP_TRACK_HEIGHT = "调整音频轨（波形/低中高频）高度，便于看清节奏细节";
	private static final String TOOLTIP_TRACK_HEIGHT_RESET = "恢复音频轨默认高度";
	private static final String TOOLTIP_DEMUCS_PRESET = "Demucs 映射预设：Drive=更强律动，Detail=更细节，Balanced=平衡";
	private static final String TOOLTIP_CLIP_GENERATION_MODE = "控制轨片段生成策略：Trigger=逐点短片段，Sustain=持续分段，Mixed=按特征自动混合";
	private static final String TOOLTIP_DEMUCS_ADVANCED = "高级参数：时长/能量阈值/最小间隔";
	private static final String TOOLTIP_ACTION_ROLLBACK = "PLACE/CLEAR 预览回滚策略：Preview 会在停止/回退时恢复方块；Persistent 会保留写入结果";
	private static final String TOOLTIP_ACTION_ROLLBACK_STATUS = "当前 PLACE/CLEAR 执行策略状态";
	private static final String TOOLTIP_BINDING_MAP = "按绑定规则将音频特征批量转换为动画事件；无规则时自动创建默认规则";
	private static final String TOOLTIP_BINDING_EDITOR = "编辑特征绑定规则：来源特征、动作、目标对象、阈值和冷却";
	private static final String TOOLTIP_BINDING_TEMPLATE = "规则模板：可覆盖（Replace）或合并（Append）到当前规则集";

	private static final String DEMUCS_ADVANCED_POPUP_ID = "tlDemucsMappingAdvanced";
	private static final String BINDING_EDITOR_POPUP_ID = "tlBindingEditor";
	private static final float TOOLBAR_ITEM_SPACING = 4f;
	private static final float TOOLBAR_GROUP_SPACING = 8f;

	/** 上次 Auto Map 生成数量，用于提示 */
	private int lastAutoMapCount = -1;
	/** 上次 Binding Map 生成数量，用于提示 */
	private int lastBindingMapCount = -1;
	/** Zoom 下拉当前选中索引（由 Combo 更新） */
	private final ImInt zoomComboIndex = new ImInt(2); // 默认 1x
	private final ImInt speedComboIndex = new ImInt(2); // 默认 1x
	private final TimelineTransportPresenter transport;
	private final TimelineToolbarActionsPresenter actions;
	private final TimelineToolbarConfigPresenter config;
	private final TimelineBindingEditorPresenter binding;
	private final ImInt demucsPresetComboIndex = new ImInt(1); // 默认 balanced
	private final ImInt clipGenerationModeComboIndex = new ImInt(0); // 默认 mixed
	private final ImInt actionRollbackComboIndex = new ImInt(0); // 默认 preview
	private final ImInt bindingTemplateComboIndex = new ImInt(0);
	private String lastToolActionFeedback = "";
	private long lastToolActionFeedbackAtMs = 0L;
	private boolean lastToolActionFeedbackSuccess = false;
	private String lastTemplateApplyFeedback = "";
	private long lastTemplateApplyFeedbackAtMs = 0L;
	private boolean lastTemplateApplyFeedbackSuccess = false;

	public TimelineToolbar() {
		this(
			PresenterFactories.timelineTransportPresenter(),
			PresenterFactories.timelineToolbarActionsPresenter(),
			PresenterFactories.timelineToolbarConfigPresenter(),
			PresenterFactories.timelineBindingEditorPresenter()
		);
	}

	TimelineToolbar(
		TimelineTransportPresenter transport,
		TimelineToolbarActionsPresenter actions,
		TimelineToolbarConfigPresenter config,
		TimelineBindingEditorPresenter binding
	) {
		this.transport = transport;
		this.actions = actions;
		this.config = config;
		this.binding = binding;
	}

	public void render(TimelineEditor editor, TimelineToolbarState toolbarState) {
		if (editor == null) return;

		// ----- 1. 播放控制 -----
		boolean shiftHeld = ImGui.getIO().getKeyShift();
		var transportState = transport.viewState(editor, shiftHeld);
		double seekStep = transportState.seekStep();
		double stepSeek = transportState.stepSeek();
		boolean playing = transportState.playing();

		// 图标按钮：与轨道行同高、零内边距，字形尽量铺满并居中
		final float tBtn = TimelineLayout.ROW_HEIGHT;
		String transportTooltip;
		IconButtonStyle.pushBeatBlockIconButton();
		if (ImGui.button(Icons.Play.REWIND_START + "##tlToStart", tBtn, tBtn)) {
			transport.seekTo(editor, 0);
		}
		transportTooltip = hoveredTooltip(null, TOOLTIP_TO_START);
		nextItemInGroup();
		if (ImGui.button(Icons.Play.REWIND + "##tlBackBeat", tBtn, tBtn)) {
			transport.seekBy(editor, -stepSeek);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_BACK_BEAT);
		nextItemInGroup();
		if (playing) {
			if (ImGui.button(Icons.Play.PAUSE + "##tlPause", tBtn, tBtn)) {
				transport.pause();
			}
			transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_PAUSE);
		} else {
			if (ImGui.button(Icons.Play.PLAY + "##tlPlay", tBtn, tBtn)) {
				transport.play(editor);
			}
			transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_PLAY);
		}
		nextItemInGroup();
		if (ImGui.button(Icons.Play.STOP + "##tlStop", tBtn, tBtn)) {
			transport.stop(editor);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_STOP);
		nextItemInGroup();
		if (ImGui.button(Icons.Play.FORWARD + "##tlFwdBeat", tBtn, tBtn)) {
			transport.seekBy(editor, stepSeek);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_FWD_BEAT);
		nextItemInGroup();
		if (ImGui.button(Icons.Play.FORWARD_END + "##tlToEnd", tBtn, tBtn)) {
			transport.seekTo(editor, transportState.durationSeconds());
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_TO_END);
		nextGroup();
		if (ImGui.button(Icons.Action.ARROW_LEFT + "##tlPrevEvt", tBtn, tBtn)) {
			transport.jumpToNearbyEvent(editor, false);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_PREV_EVENT);
		nextItemInGroup();
		if (ImGui.button(Icons.Action.ARROW_RIGHT + "##tlNextEvt", tBtn, tBtn)) {
			transport.jumpToNearbyEvent(editor, true);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_NEXT_EVENT);
		nextItemInGroup();
		if (ImGui.button(Icons.Timeline.MARKER + "##tlAddMarker", tBtn, tBtn)) {
			transport.addMarkerAtCurrentTime(editor);
		}
		transportTooltip = hoveredTooltip(transportTooltip, TOOLTIP_ADD_MARKER);
		IconButtonStyle.popBeatBlockIconButton();
		if (transportTooltip != null) {
			ImGui.setTooltip(transportTooltip);
		}

		// ----- 时间显示（始终可见）-----
		{
			nextGroup();
			ImGui.textDisabled(transportState.positionDisplay());
		}

		nextGroupOrWrap(0);

		// ----- 1.5 循环区（In/Out）与速度 -----
		double now = transportState.currentTimeSeconds();
		if (ImGui.button("In")) {
			transport.setLoopInAt(toolbarState, now, seekStep);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_IN);
		nextItemInGroup();
		if (ImGui.button("Out")) {
			transport.setLoopOutAt(toolbarState, now, seekStep);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_OUT);
		nextItemInGroup();
		if (ImGui.button("Clr")) {
			transport.clearLoopRange(toolbarState);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_CLEAR);
		nextItemInGroup();

		speedComboIndex.set(TimelineToolbarViewPresenter.indexOfClosestSpeed(transport.currentPlaybackSpeed(editor)));
		ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarViewPresenter.SPEED_LABELS));
		if (ImGui.combo("Speed", speedComboIndex, TimelineToolbarViewPresenter.SPEED_LABELS)) {
			TimelineToolbarViewPresenter.applySpeedPreset(editor, transport, speedComboIndex.get());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_SPEED);
		nextItemInGroup();
		renderActionRollbackControl(false);
		nextGroupOrWrap(0);

		// ----- 2. 吸附与网格 -----
		if (toolbarState != null) {
			boolean snap = toolbarState.isSnapToGrid();
			if (ImGui.checkbox("Snap", snap)) {
				toolbarState.setSnapToGrid(!snap);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_SNAP);
			nextItemInGroup();

			boolean beatSnap = toolbarState.isSnapToBeat();
			if (ImGui.checkbox("Beat Snap", beatSnap)) {
				toolbarState.setSnapToBeat(!beatSnap);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BEAT_SNAP);
			nextItemInGroup();

			boolean beatGrid = toolbarState.isBeatGridVisible();
			if (ImGui.checkbox("Beat Grid", beatGrid)) {
				toolbarState.setBeatGridVisible(!beatGrid);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BEAT_GRID);
			nextItemInGroup();

			boolean magnet = toolbarState.isMagnetSnap();
			if (ImGui.checkbox("Magnet", magnet)) {
				toolbarState.setMagnetSnap(!magnet);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_MAGNET);
			nextGroupOrWrap(0);

			boolean loop = toolbarState.isLoop();
			if (ImGui.checkbox("Loop", loop)) {
				toolbarState.setLoop(!loop);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP);
			nextGroupOrWrap(0);
		}

		// ----- 3. 视图：Zoom 下拉 + Fit -----
		zoomComboIndex.set(TimelineToolbarViewPresenter.indexOfClosestZoom(editor.getViewState().getZoom()));
		ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarViewPresenter.ZOOM_PRESET_LABELS));
		if (ImGui.combo("Zoom", zoomComboIndex, TimelineToolbarViewPresenter.ZOOM_PRESET_LABELS)) {
			TimelineToolbarViewPresenter.applyZoomPreset(editor, zoomComboIndex.get());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ZOOM);
		nextItemInGroup();
		if (ImGui.button("Fit")) {
			TimelineToolbarViewPresenter.fitToDuration(
				editor, BeatBlock.timeline, ImGui.getContentRegionAvailX() - 130f);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_FIT);
		nextItemInGroup();
		renderTrackHeightControl(editor, false);
		nextGroupOrWrap(0);

		// ----- 4. Auto Map -----
		{
			int objCount = BeatBlock.blockAnimationEngine != null
				? BeatBlock.blockAnimationEngine.getStageObjectSystem().size() : 0;
			if (objCount == 0) {
				ImGui.textColored(0.95f, 0.65f, 0.30f, 1f, "无对象");
				if (ImGui.isItemHovered()) ImGui.setTooltip("请先在工具面板中创建 StageObject（选区→创建），否则 Binding Map 无法生成事件");
				nextItemInGroup();
			}
		}
		if (ImGui.button("Binding Map")) {
			var outcome = actions.runBindingMap();
			lastBindingMapCount = outcome.count();
			setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_MAP);
		nextItemInGroup();
		if (ImGui.button("Bindings...##tlBindingEditorOpen")) {
			ImGui.openPopup(BINDING_EDITOR_POPUP_ID);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_EDITOR);
		renderBindingEditorPopup();
		nextItemInGroup();
		if (ImGui.button("Auto Map")) {
			var outcome = actions.runAutoMap();
			lastAutoMapCount = outcome.count();
			setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_AUTO_MAP);
		nextItemInGroup();
		if (ImGui.button("烘焙 STEP##tlBakeStep")) {
			var outcome = actions.runBakeStepSequences();
			setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BAKE_STEP);
		nextItemInGroup();
		renderToolActionFeedback();

		nextGroupOrWrap(0);
		renderDemucsMappingPresetControl(false);

	}

	private void renderOverflowMenu(TimelineEditor editor, TimelineToolbarState toolbarState, double seekStep) {
		if (ImGui.button("More##tlMore")) {
			ImGui.openPopup("tlMorePopup");
		}
		if (!ImGui.beginPopup("tlMorePopup")) return;

		double now = editor.getClock().getCurrentTimeSeconds();
		ImGui.textDisabled("Loop & Speed");
		if (ImGui.button("In##tlMoreIn")) {
			transport.setLoopInAt(toolbarState, now, seekStep);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_IN);
		ImGui.sameLine();
		if (ImGui.button("Out##tlMoreOut")) {
			transport.setLoopOutAt(toolbarState, now, seekStep);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_OUT);
		ImGui.sameLine();
		if (ImGui.button("Clr##tlMoreClr")) {
			transport.clearLoopRange(toolbarState);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP_CLEAR);

		speedComboIndex.set(TimelineToolbarViewPresenter.indexOfClosestSpeed(transport.currentPlaybackSpeed(editor)));
		ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarViewPresenter.SPEED_LABELS));
		if (ImGui.combo("Speed##tlMoreSpeed", speedComboIndex, TimelineToolbarViewPresenter.SPEED_LABELS)) {
			TimelineToolbarViewPresenter.applySpeedPreset(editor, transport, speedComboIndex.get());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_SPEED);
		renderActionRollbackControl(true);

		ImGui.separator();
		ImGui.textDisabled("Snap & Grid");
		boolean snap = toolbarState.isSnapToGrid();
		if (ImGui.checkbox("Snap##tlMoreSnap", snap)) {
			toolbarState.setSnapToGrid(!snap);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_SNAP);

		boolean beatSnap = toolbarState.isSnapToBeat();
		if (ImGui.checkbox("Beat Snap##tlMoreBeatSnap", beatSnap)) {
			toolbarState.setSnapToBeat(!beatSnap);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BEAT_SNAP);

		boolean beatGrid = toolbarState.isBeatGridVisible();
		if (ImGui.checkbox("Beat Grid##tlMoreBeatGrid", beatGrid)) {
			toolbarState.setBeatGridVisible(!beatGrid);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BEAT_GRID);

		boolean magnet = toolbarState.isMagnetSnap();
		if (ImGui.checkbox("Magnet##tlMoreMagnet", magnet)) {
			toolbarState.setMagnetSnap(!magnet);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_MAGNET);

		boolean loop = toolbarState.isLoop();
		if (ImGui.checkbox("Loop##tlMoreLoop", loop)) {
			toolbarState.setLoop(!loop);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_LOOP);

		ImGui.separator();
		ImGui.textDisabled("View");
		zoomComboIndex.set(TimelineToolbarViewPresenter.indexOfClosestZoom(editor.getViewState().getZoom()));
		ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarViewPresenter.ZOOM_PRESET_LABELS));
		if (ImGui.combo("Zoom##tlMoreZoom", zoomComboIndex, TimelineToolbarViewPresenter.ZOOM_PRESET_LABELS)) {
			TimelineToolbarViewPresenter.applyZoomPreset(editor, zoomComboIndex.get());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ZOOM);
		if (ImGui.button("Fit##tlMoreFit")) {
			TimelineToolbarViewPresenter.fitToDuration(
				editor, BeatBlock.timeline, ImGui.getContentRegionAvailX() - 16f);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_FIT);
		renderTrackHeightControl(editor, true);

		ImGui.separator();
		ImGui.textDisabled("Tools");
		if (ImGui.button("Binding Map##tlMoreBindingMap")) {
			var outcome = actions.runBindingMap();
			lastBindingMapCount = outcome.count();
			setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_MAP);
		ImGui.sameLine();
		if (ImGui.button("Bindings...##tlMoreBindingEditorOpen")) {
			ImGui.openPopup(BINDING_EDITOR_POPUP_ID);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_EDITOR);
		renderBindingEditorPopup();
		if (ImGui.button("Auto Map##tlMoreAutoMap")) {
			var outcome = actions.runAutoMap();
			lastAutoMapCount = outcome.count();
			setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_AUTO_MAP);
		if (ImGui.button("烘焙 STEP##tlMoreBakeStep")) {
			var outcome = actions.runBakeStepSequences();
			setToolActionFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BAKE_STEP);
		renderToolActionFeedback();

		renderDemucsMappingPresetControl(true);

		ImGui.endPopup();
	}

	private void renderActionRollbackControl(boolean compactMode) {
		config.ensureActionExecutionConfigLoaded();
		actionRollbackComboIndex.set(TimelineToolbarConfigPresenter.indexOfActionRollbackValue(config.readActionRollbackMode()));
		if (compactMode) {
			ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarConfigPresenter.ACTION_ROLLBACK_LABELS));
			if (ImGui.combo("Rollback##tlMoreActionRollback", actionRollbackComboIndex, TimelineToolbarConfigPresenter.ACTION_ROLLBACK_LABELS)) {
				config.writeActionRollbackMode(TimelineToolbarConfigPresenter.ACTION_ROLLBACK_VALUES[actionRollbackComboIndex.get()]);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ACTION_ROLLBACK);
			return;
		}

		ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarConfigPresenter.ACTION_ROLLBACK_LABELS));
		if (ImGui.combo("Rollback", actionRollbackComboIndex, TimelineToolbarConfigPresenter.ACTION_ROLLBACK_LABELS)) {
			config.writeActionRollbackMode(TimelineToolbarConfigPresenter.ACTION_ROLLBACK_VALUES[actionRollbackComboIndex.get()]);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ACTION_ROLLBACK);
	}

	private void renderActionRollbackStatus() {
		ImGui.textDisabled(config.actionRollbackViewState().statusLabel());
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_ACTION_ROLLBACK_STATUS);
	}

	private static float buttonWidth(String label) {
		return ImGui.calcTextSize(label).x + 18f;
	}

	private static float checkboxWidth(String label) {
		return ImGui.calcTextSize(label).x + 28f;
	}

	private static float comboTotalWidth(String label, String[] values) {
		return comboWidthForLabels(values) + ImGui.calcTextSize(label).x + 10f;
	}

	private static float sliderTotalWidth(float sliderWidth) {
		return sliderWidth + ImGui.calcTextSize("Track H").x + 10f;
	}

	private static void renderTrackHeightControl(TimelineEditor editor, boolean compactMode) {
		if (editor == null) return;
		TimelineTrackListState trackState = editor.getTrackListState();
		float min = trackState.getAudioRowHeightMin();
		float max = trackState.getAudioRowHeightMax();
		float[] v = new float[] { trackState.getAudioRowHeight() };

		if (compactMode) {
			ImGui.separator();
			ImGui.textDisabled("Track Height");
			ImGui.setNextItemWidth(180f);
			if (ImGui.sliderFloat("Track H##tlMoreTrackH", v, min, max, "%.0f px")) {
				trackState.setAudioRowHeight(v[0]);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TRACK_HEIGHT);
			if (ImGui.button("Reset##tlMoreTrackHReset")) {
				trackState.resetAudioRowHeight();
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TRACK_HEIGHT_RESET);
			return;
		}

		ImGui.setNextItemWidth(120f);
		if (ImGui.sliderFloat("Track H", v, min, max, "%.0f px")) {
			trackState.setAudioRowHeight(v[0]);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TRACK_HEIGHT);
		nextItemInGroup();
		if (ImGui.button("Reset##tlTrackHReset")) {
			trackState.resetAudioRowHeight();
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_TRACK_HEIGHT_RESET);
	}

	private void renderDemucsMappingPresetControl(boolean compactMode) {
		if (!config.isDemucsSeparationActive()) return;
		config.ensureDemucsMappingConfigLoaded();

		demucsPresetComboIndex.set(TimelineToolbarConfigPresenter.indexOfDemucsPresetValue(config.readDemucsPreset()));
		clipGenerationModeComboIndex.set(TimelineToolbarConfigPresenter.indexOfClipGenerationMode(config.readClipGenerationMode()));

		if (compactMode) {
			ImGui.separator();
			ImGui.textDisabled("Demucs Mapping");
			ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarConfigPresenter.DEMUCS_PRESET_LABELS));
			if (ImGui.combo("Preset##tlMoreDemucsPreset", demucsPresetComboIndex, TimelineToolbarConfigPresenter.DEMUCS_PRESET_LABELS)) {
				config.writeDemucsPreset(TimelineToolbarConfigPresenter.DEMUCS_PRESET_VALUES[demucsPresetComboIndex.get()]);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_DEMUCS_PRESET);
			ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarConfigPresenter.CLIP_GENERATION_MODE_LABELS));
			if (ImGui.combo("Clip Mode##tlMoreClipMode", clipGenerationModeComboIndex, TimelineToolbarConfigPresenter.CLIP_GENERATION_MODE_LABELS)) {
				config.writeClipGenerationMode(TimelineToolbarConfigPresenter.CLIP_GENERATION_MODE_VALUES[clipGenerationModeComboIndex.get()]);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_CLIP_GENERATION_MODE);
			if (ImGui.button("Advanced##tlMoreDemucsAdvanced")) {
				ImGui.openPopup(DEMUCS_ADVANCED_POPUP_ID);
			}
			if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_DEMUCS_ADVANCED);
			renderDemucsAdvancedPopup();
			return;
		}

		ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarConfigPresenter.DEMUCS_PRESET_LABELS));
		if (ImGui.combo("Demucs", demucsPresetComboIndex, TimelineToolbarConfigPresenter.DEMUCS_PRESET_LABELS)) {
			config.writeDemucsPreset(TimelineToolbarConfigPresenter.DEMUCS_PRESET_VALUES[demucsPresetComboIndex.get()]);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_DEMUCS_PRESET);
		nextItemInGroup();
		ImGui.setNextItemWidth(comboWidthForLabels(TimelineToolbarConfigPresenter.CLIP_GENERATION_MODE_LABELS));
		if (ImGui.combo("Clip Mode", clipGenerationModeComboIndex, TimelineToolbarConfigPresenter.CLIP_GENERATION_MODE_LABELS)) {
			config.writeClipGenerationMode(TimelineToolbarConfigPresenter.CLIP_GENERATION_MODE_VALUES[clipGenerationModeComboIndex.get()]);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_CLIP_GENERATION_MODE);
		nextItemInGroup();
		if (ImGui.button("Map...##tlDemucsAdvanced")) {
			ImGui.openPopup(DEMUCS_ADVANCED_POPUP_ID);
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_DEMUCS_ADVANCED);
		renderDemucsAdvancedPopup();
	}

	private void renderBindingEditorPopup() {
		if (!ImGui.beginPopup(BINDING_EDITOR_POPUP_ID)) return;
		Timeline timeline = binding.currentTimeline();
		if (timeline == null) {
			ImGui.textDisabled("Timeline 未初始化");
			ImGui.endPopup();
			return;
		}

		List<AnimationBindingRule> rules = new ArrayList<>(binding.loadRules(timeline));
		var lists = binding.loadEditorLists(timeline);
		List<String> featureKeys = lists.featureKeys();
		List<String> targetDisplays = lists.targetDisplays();
		Map<String, String> targetDisplayToId = lists.targetDisplayToId();
		List<String> animationIds = lists.animationIds();
		List<String> sectionFilters = lists.sectionFilters();

		ImGui.textDisabled("Binding Rules");
		ImGui.sameLine();
		ImGui.text("(" + rules.size() + ")");

		if (ImGui.button("Create Defaults##bindingCreateDefaults")) {
			rules = new ArrayList<>(binding.createDefaultRules(timeline));
		}
		ImGui.sameLine();
		if (ImGui.button("Add Rule##bindingAddRule")) {
			rules = binding.tryAddRule(timeline, rules, lists);
		}

		ImGui.setNextItemWidth(comboWidthForLabels(TimelineBindingEditorPresenter.TEMPLATE_LABELS));
		ImGui.combo("Template##bindingTemplate", bindingTemplateComboIndex, TimelineBindingEditorPresenter.TEMPLATE_LABELS);
		if (ImGui.isItemHovered()) ImGui.setTooltip(TOOLTIP_BINDING_TEMPLATE);
		ImGui.sameLine();
		if (ImGui.button("Replace##bindingApplyTemplate")) {
			var outcome = binding.replaceWithTemplate(timeline, rules, bindingTemplateComboIndex.get());
			rules = outcome.rules();
			setTemplateApplyFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip("覆盖当前规则集");
		ImGui.sameLine();
		if (ImGui.button("Append##bindingAppendTemplate")) {
			var outcome = binding.appendTemplate(timeline, rules, bindingTemplateComboIndex.get());
			rules = outcome.rules();
			setTemplateApplyFeedback(outcome.message(), outcome.success());
		}
		if (ImGui.isItemHovered()) ImGui.setTooltip("保留现有规则并追加模板（自动去重）");
		renderTemplateApplyFeedback();

		if (featureKeys.isEmpty()) {
			ImGui.textDisabled("当前没有可用特征轨，请先导入并分析音频。\n");
		}

		if (rules.isEmpty()) {
			ImGui.textDisabled("没有规则，可点击 Create Defaults 或 Add Rule。\n");
		}

		int removeIndex = -1;
		boolean changedAny = false;
		for (int i = 0; i < rules.size(); i++) {
			AnimationBindingRule rule = rules.get(i);
			String nodeLabel = rule.name() + "##bindingRuleNode_" + rule.id();
			if (!ImGui.treeNode(nodeLabel)) continue;

			ImGui.pushID("binding_rule_" + rule.id());
			boolean changed = false;

			boolean[] enabled = new boolean[] { rule.enabled() };
			if (ImGui.checkbox("Enabled", enabled[0])) changed = true;

			ImString nameBuf = new ImString(rule.name(), 128);
			if (ImGui.inputText("Name", nameBuf)) changed = true;

			if (!featureKeys.isEmpty()) {
				int featureIndex = TimelineBindingEditorPresenter.indexOfValue(featureKeys, rule.sourceFeatureKey());
				ImInt featureCombo = new ImInt(Math.max(0, featureIndex));
				if (ImGui.combo("Feature", featureCombo, TimelineBindingEditorPresenter.toComboArray(featureKeys))) changed = true;
				featureIndex = featureCombo.get();
				if (featureIndex < 0 || featureIndex >= featureKeys.size()) featureIndex = 0;

				int animationIndex = TimelineBindingEditorPresenter.indexOfValue(animationIds, rule.animationTypeId());
				ImInt animationCombo = new ImInt(Math.max(0, animationIndex));
				if (ImGui.combo("Animation", animationCombo, TimelineBindingEditorPresenter.toComboArray(animationIds))) changed = true;
				animationIndex = animationCombo.get();
				if (animationIndex < 0 || animationIndex >= animationIds.size()) animationIndex = 0;

				int actionIndex = TimelineBindingEditorPresenter.indexOfValue(TimelineBindingEditorPresenter.ACTION_VALUES, rule.actionMode().name());
				ImInt actionCombo = new ImInt(Math.max(0, actionIndex));
				if (ImGui.combo("Action", actionCombo, TimelineBindingEditorPresenter.ACTION_LABELS)) changed = true;
				actionIndex = actionCombo.get();
				if (actionIndex < 0 || actionIndex >= TimelineBindingEditorPresenter.ACTION_VALUES.length) actionIndex = 0;

				int spatialIndex = TimelineBindingEditorPresenter.indexOfValue(TimelineBindingEditorPresenter.SPATIAL_VALUES, rule.spatialMode().name());
				ImInt spatialCombo = new ImInt(Math.max(0, spatialIndex));
				if (ImGui.combo("Spatial", spatialCombo, TimelineBindingEditorPresenter.SPATIAL_LABELS)) changed = true;
				spatialIndex = spatialCombo.get();
				if (spatialIndex < 0 || spatialIndex >= TimelineBindingEditorPresenter.SPATIAL_VALUES.length) spatialIndex = 0;

				int targetIndex = TimelineBindingEditorPresenter.indexOfTargetDisplay(targetDisplays, targetDisplayToId, rule.targetObjectId());
				ImInt targetCombo = new ImInt(Math.max(0, targetIndex));
				if (!targetDisplays.isEmpty() && ImGui.combo("Target", targetCombo, TimelineBindingEditorPresenter.toComboArray(targetDisplays))) changed = true;
				targetIndex = targetCombo.get();
				if (targetIndex < 0 || targetIndex >= targetDisplays.size()) targetIndex = 0;

				int sectionIndex = TimelineBindingEditorPresenter.indexOfSectionFilter(sectionFilters, rule.sectionFilter());
				ImInt sectionCombo = new ImInt(Math.max(0, sectionIndex));
				if (!sectionFilters.isEmpty() && ImGui.combo("Section", sectionCombo, TimelineBindingEditorPresenter.toComboArray(sectionFilters))) changed = true;
				sectionIndex = sectionCombo.get();
				if (sectionIndex < 0 || sectionIndex >= sectionFilters.size()) sectionIndex = 0;

				float[] threshold = new float[] { rule.energyThreshold() };
				if (ImGui.sliderFloat("Threshold", threshold, 0f, 1f, "%.2f")) changed = true;

				float[] scale = new float[] { rule.energyScale() };
				if (ImGui.sliderFloat("Energy Scale", scale, 0f, 2f, "%.2f")) changed = true;

				float[] duration = new float[] { (float) rule.durationSeconds() };
				if (ImGui.sliderFloat("Duration", duration, 0.05f, 4f, "%.2f s")) changed = true;

				float[] cooldown = new float[] { (float) rule.cooldownSeconds() };
				if (ImGui.sliderFloat("Cooldown", cooldown, 0f, 1.5f, "%.2f s")) changed = true;

				float[] probability = new float[] { rule.probability() };
				if (ImGui.sliderFloat("Probability", probability, 0f, 1f, "%.2f")) changed = true;

				float[] seqDelay = new float[] { (float) rule.sequentialDelaySeconds() };
				if (ImGui.sliderFloat("Step Delay", seqDelay, 0f, 0.5f, "%.2f s")) changed = true;

				Map<String, Object> extraCopy = new HashMap<>(rule.extraParams());
				String uiAnimation = animationIds.isEmpty() ? rule.animationTypeId() : animationIds.get(animationIndex);
				if ("WaveMotion".equalsIgnoreCase(uiAnimation)) {
					float[] waveAmp = new float[] { (float) TimelineBindingEditorPresenter.extraParamAsDouble(extraCopy, "waveAmplitude", 0.5) };
					float[] wavePhase = new float[] { (float) TimelineBindingEditorPresenter.extraParamAsDouble(extraCopy, "wavePhaseOffset", 0.5) };
					if (ImGui.sliderFloat("Wave Amp", waveAmp, 0f, 3f, "%.2f")) changed = true;
					if (ImGui.sliderFloat("Wave Phase", wavePhase, 0f, 3f, "%.2f")) changed = true;
					extraCopy.put("waveAmplitude", waveAmp[0]);
					extraCopy.put("wavePhaseOffset", wavePhase[0]);
				} else if ("BlockExplosion".equalsIgnoreCase(uiAnimation)) {
					float[] impactRadius = new float[] { (float) TimelineBindingEditorPresenter.extraParamAsDouble(extraCopy, "impactRadius", 4.0) };
					float[] impactBurst = new float[] { (float) TimelineBindingEditorPresenter.extraParamAsDouble(extraCopy, "impactBurst", 1.0) };
					if (ImGui.sliderFloat("Impact Radius", impactRadius, 1f, 16f, "%.1f")) changed = true;
					if (ImGui.sliderFloat("Impact Burst", impactBurst, 0f, 3f, "%.2f")) changed = true;
					extraCopy.put("impactRadius", impactRadius[0]);
					extraCopy.put("impactBurst", impactBurst[0]);
				} else if ("BlockDrop".equalsIgnoreCase(uiAnimation)) {
					float[] meteorHeight = new float[] { (float) TimelineBindingEditorPresenter.extraParamAsDouble(extraCopy, "meteorHeight", 8.0) };
					float[] meteorScatter = new float[] { (float) TimelineBindingEditorPresenter.extraParamAsDouble(extraCopy, "meteorScatter", 2.0) };
					if (ImGui.sliderFloat("Meteor Height", meteorHeight, 2f, 32f, "%.1f")) changed = true;
					if (ImGui.sliderFloat("Meteor Scatter", meteorScatter, 0f, 8f, "%.1f")) changed = true;
					extraCopy.put("meteorHeight", meteorHeight[0]);
					extraCopy.put("meteorScatter", meteorScatter[0]);
				}

				String uiAction = TimelineBindingEditorPresenter.ACTION_VALUES[actionIndex];
				if ("BUILD".equalsIgnoreCase(uiAction)) {
					String[] buildModeLabels = { "WALL", "BRIDGE", "TOWER", "DISSOLVE" };
					int bmIdx = TimelineBindingEditorPresenter.indexOfValue(buildModeLabels, String.valueOf(extraCopy.getOrDefault("buildMode", "WALL")));
					ImInt bmCombo = new ImInt(Math.max(0, bmIdx));
					if (ImGui.combo("Build Mode", bmCombo, buildModeLabels)) changed = true;
					extraCopy.put("buildMode", buildModeLabels[Math.max(0, Math.min(bmCombo.get(), buildModeLabels.length - 1))]);

					ImString blockBuf = new ImString(128);
					blockBuf.set(String.valueOf(extraCopy.getOrDefault("placeBlock", "minecraft:diamond_block")));
					if (ImGui.inputText("Block ID##buildBlockId", blockBuf)) changed = true;
					extraCopy.put("placeBlock", blockBuf.get().trim());

					ImBoolean dissolveFlag = new ImBoolean(
						"true".equalsIgnoreCase(String.valueOf(extraCopy.getOrDefault("buildDissolve", "false"))));
					if (ImGui.checkbox("Dissolve (reverse)", dissolveFlag)) changed = true;
					extraCopy.put("buildDissolve", String.valueOf(dissolveFlag.get()));
				}

				if (changed) {
					String selectedFeature = featureKeys.get(featureIndex);
					String selectedAnimation = animationIds.isEmpty() ? rule.animationTypeId() : animationIds.get(animationIndex);
					String selectedTargetDisplay = targetDisplays.isEmpty() ? "" : targetDisplays.get(targetIndex);
					String selectedTargetId = targetDisplayToId.getOrDefault(selectedTargetDisplay, rule.targetObjectId());
					String selectedSection = sectionFilters.isEmpty()
						? TimelineBindingEditorPresenter.SECTION_ALL
						: sectionFilters.get(sectionIndex);
					rules.set(i, TimelineBindingEditorPresenter.buildUpdatedRule(rule,
						new TimelineBindingEditorPresenter.BindingRuleEditRequest(
							enabled[0],
							nameBuf.get(),
							selectedFeature,
							selectedAnimation,
							TimelineBindingEditorPresenter.ACTION_VALUES[actionIndex],
							spatialIndex,
							selectedTargetId,
							selectedSection,
							threshold[0],
							scale[0],
							duration[0],
							cooldown[0],
							probability[0],
							seqDelay[0],
							extraCopy
						)));
					changedAny = true;
				}
			}

			if (ImGui.button("Delete##bindingDelete_" + i)) {
				removeIndex = i;
			}

			ImGui.popID();
			ImGui.treePop();
		}

		if (removeIndex >= 0) {
			rules = binding.removeRule(rules, removeIndex);
			changedAny = true;
		}

		if (changedAny) {
			binding.saveRules(timeline, rules);
		}

		ImGui.separator();
		if (ImGui.button("Apply To Block Track##bindingApplyBlock")) {
			var outcome = binding.applyToBlockTrack();
			lastBindingMapCount = outcome.count();
			setTemplateApplyFeedback(outcome.message(), outcome.success());
		}
		ImGui.sameLine();
		if (ImGui.button("Apply To Auto Track##bindingApplyAuto")) {
			var outcome = binding.applyToAutoTrack();
			lastBindingMapCount = outcome.count();
			setTemplateApplyFeedback(outcome.message(), outcome.success());
		}

		ImGui.endPopup();
	}

	private void setTemplateApplyFeedback(String message, boolean success) {
		lastTemplateApplyFeedback = message != null ? message : "";
		lastTemplateApplyFeedbackSuccess = success;
		lastTemplateApplyFeedbackAtMs = System.currentTimeMillis();
	}

	private void setToolActionFeedback(String message, boolean success) {
		lastToolActionFeedback = message != null ? message : "";
		lastToolActionFeedbackSuccess = success;
		lastToolActionFeedbackAtMs = System.currentTimeMillis();
	}

	private void renderToolActionFeedback() {
		renderFadingFeedback(
			lastToolActionFeedback,
			lastToolActionFeedbackSuccess,
			lastToolActionFeedbackAtMs,
			msg -> lastToolActionFeedback = msg
		);
	}

	private void renderTemplateApplyFeedback() {
		renderFadingFeedback(
			lastTemplateApplyFeedback,
			lastTemplateApplyFeedbackSuccess,
			lastTemplateApplyFeedbackAtMs,
			msg -> lastTemplateApplyFeedback = msg
		);
	}

	private void renderFadingFeedback(String message, boolean success, long messageAtMs, java.util.function.Consumer<String> clearSink) {
		if (message == null || message.isBlank()) return;
		final long now = System.currentTimeMillis();
		final long ageMs = Math.max(0L, now - messageAtMs);
		final long holdMs = 1700L;
		final long fadeMs = 1300L;
		final long ttlMs = holdMs + fadeMs;
		if (ageMs >= ttlMs) {
			if (clearSink != null) clearSink.accept("");
			return;
		}

		float alpha = 1.0f;
		if (ageMs > holdMs) {
			float t = (ageMs - holdMs) / (float) fadeMs;
			alpha = Math.max(0f, 1.0f - t);
		}

		if (success) {
			ImGui.textColored(0.55f, 0.92f, 0.62f, alpha, message);
		} else {
			ImGui.textColored(0.95f, 0.80f, 0.42f, alpha, message);
		}
	}

	private void renderDemucsAdvancedPopup() {
		if (!ImGui.beginPopup(DEMUCS_ADVANCED_POPUP_ID)) return;
		ImGui.textDisabled("Demucs Mapping Advanced");

		var scales = config.readGlobalScales();
		float[] durationScale = new float[] { (float) scales.durationScale() };
		float[] energyScale = new float[] { (float) scales.energyScale() };
		float[] gapScale = new float[] { (float) scales.gapScale() };

		boolean changed = false;
		ImGui.setNextItemWidth(220f);
		changed |= ImGui.sliderFloat("Duration Scale##demucsDur", durationScale,
			(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MIN,
			(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MAX, "%.2f");
		ImGui.setNextItemWidth(220f);
		changed |= ImGui.sliderFloat("Energy Threshold##demucsEnergy", energyScale,
			(float) TimelineToolbarConfigPresenter.DEMUCS_ENERGY_SCALE_MIN,
			(float) TimelineToolbarConfigPresenter.DEMUCS_ENERGY_SCALE_MAX, "%.2f");
		ImGui.setNextItemWidth(220f);
		changed |= ImGui.sliderFloat("Min Gap Scale##demucsGap", gapScale,
			(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MIN,
			(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MAX, "%.2f");

		if (changed) {
			config.writeGlobalScales(durationScale[0], energyScale[0], gapScale[0]);
		}

		if (ImGui.button("Reset to 1.0##demucsScaleReset")) {
			config.resetGlobalScalesToDefault();
		}

		ImGui.separator();
		if (ImGui.treeNode("Per-Feature Overrides##demucsFeatureOverrides")) {
			for (int i = 0; i < TimelineToolbarConfigPresenter.DEMUCS_FEATURE_KEYS.length; i++) {
				String featureKey = TimelineToolbarConfigPresenter.DEMUCS_FEATURE_KEYS[i];
				String label = TimelineToolbarConfigPresenter.DEMUCS_FEATURE_LABELS[i];
				if (ImGui.treeNode(label + "##demucsFeatureNode_" + featureKey)) {
					boolean nodeChanged = false;
					float[] fDur = new float[] { (float) config.readFeatureScale(featureKey, "duration") };
					float[] fEnergy = new float[] { (float) config.readFeatureEnergyScale(featureKey) };
					float[] fGap = new float[] { (float) config.readFeatureScale(featureKey, "gap") };

					ImGui.setNextItemWidth(220f);
					nodeChanged |= ImGui.sliderFloat("Duration##demucsFeatDur_" + featureKey, fDur,
						(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MIN,
						(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MAX, "%.2f");
					ImGui.setNextItemWidth(220f);
					nodeChanged |= ImGui.sliderFloat("Energy##demucsFeatEnergy_" + featureKey, fEnergy,
						(float) TimelineToolbarConfigPresenter.DEMUCS_ENERGY_SCALE_MIN,
						(float) TimelineToolbarConfigPresenter.DEMUCS_ENERGY_SCALE_MAX, "%.2f");
					ImGui.setNextItemWidth(220f);
					nodeChanged |= ImGui.sliderFloat("Gap##demucsFeatGap_" + featureKey, fGap,
						(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MIN,
						(float) TimelineToolbarConfigPresenter.DEMUCS_SCALE_MAX, "%.2f");

					if (nodeChanged) {
						config.writeFeatureScales(featureKey, fDur[0], fEnergy[0], fGap[0]);
					}

					ImGui.treePop();
				}
			}

			if (ImGui.button("Reset Feature Overrides##demucsFeatReset")) {
				config.resetAllFeatureOverrides();
			}

			ImGui.treePop();
		}

		ImGui.endPopup();
	}

	private static void nextItemInGroup() {
		ImGui.sameLine(0f, TOOLBAR_ITEM_SPACING);
	}

	private static void nextGroup() {
		ImGui.sameLine(0f, TOOLBAR_GROUP_SPACING);
	}

	/**
	 * 组间换行检测：如果当前行剩余空间不够放下一个元素组，就不调 sameLine，让 ImGui 自动换行。
	 * @param estimatedNextGroupWidth 下一组控件的近似宽度，0 表示使用默认阈值。
	 */
	private static void nextGroupOrWrap(float estimatedNextGroupWidth) {
		float threshold = estimatedNextGroupWidth > 0 ? estimatedNextGroupWidth : 80f;
		// 先 sameLine 把光标移到上一个控件右侧，再检查剩余空间
		ImGui.sameLine(0f, TOOLBAR_GROUP_SPACING);
		if (ImGui.getContentRegionAvailX() < threshold) {
			ImGui.newLine();
		}
	}

	private static float comboWidthForLabels(String[] labels) {
		float maxText = 0f;
		for (String label : labels) {
			if (label == null) continue;
			maxText = Math.max(maxText, ImGui.calcTextSize(label).x);
		}
		return maxText + 40f;
	}

	private static String hoveredTooltip(String current, String text) {
		if (current == null && ImGui.isItemHovered()) {
			return text;
		}
		return current;
	}
}
