package com.beatblock.timeline.rendering;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 时间线 UI 状态存储：保存/恢复轨道列表状态（折叠、名称、宽度等）。
 *
 * 存储路径：config/beatblock/ui.json
 */
public final class TimelineUiStateStore {

	private static final Logger LOGGER = LoggerFactory.getLogger("beatblock-timeline-ui-state");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final long SAVE_DEBOUNCE_MS = 350L;

	private final Path filePath;
	private int lastStateHash = Integer.MIN_VALUE;
	private long lastChangeAtMs = 0L;
	private boolean dirty;

	public TimelineUiStateStore() {
		Path configRoot = FabricLoader.getInstance().getConfigDir();
		this.filePath = configRoot.resolve("beatblock").resolve("ui.json");
	}

	/** 初始化时读取并恢复状态。 */
	public void loadTrackListState(TimelineTrackListState state) {
		if (state == null) return;
		if (!Files.exists(filePath)) {
			lastStateHash = hashState(state);
			return;
		}

		try {
			String json = Files.readString(filePath, StandardCharsets.UTF_8);
			UiConfig cfg = GSON.fromJson(json, UiConfig.class);
			if (cfg != null && cfg.timelineTrackList != null) {
				TrackListData d = cfg.timelineTrackList;
				state.applyPersistedState(
					d.trackHeaderWidthPx,
					d.visible,
					d.locked,
					d.customNames,
					d.collapsedGroupRows
				);
			}
		} catch (Exception e) {
			LOGGER.warn("读取 timeline UI 状态失败: {}", filePath, e);
		}

		lastStateHash = hashState(state);
		dirty = false;
	}

	/**
	 * 每帧调用：检测状态变化并在防抖窗口后写盘。
	 */
	public void syncAndFlush(TimelineTrackListState state) {
		if (state == null) return;
		int hash = hashState(state);
		long now = System.currentTimeMillis();
		if (hash != lastStateHash) {
			lastStateHash = hash;
			lastChangeAtMs = now;
			dirty = true;
		}
		if (dirty && now - lastChangeAtMs >= SAVE_DEBOUNCE_MS) {
			writeState(state);
			dirty = false;
		}
	}

	private void writeState(TimelineTrackListState state) {
		try {
			Path parent = filePath.getParent();
			if (parent != null) Files.createDirectories(parent);
			UiConfig cfg = new UiConfig();
			cfg.timelineTrackList = TrackListData.fromState(state);
			String json = GSON.toJson(cfg);
			Files.writeString(filePath, json, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.warn("写入 timeline UI 状态失败: {}", filePath, e);
		}
	}

	private static int hashState(TimelineTrackListState state) {
		int h = Float.hashCode(state.getTrackHeaderWidth());
		h = 31 * h + Arrays.hashCode(state.copyVisibleStates());
		h = 31 * h + Arrays.hashCode(state.copyLockedStates());
		h = 31 * h + state.copyCustomNames().hashCode();
		h = 31 * h + state.copyCollapsedGroupRows().hashCode();
		return h;
	}

	private static final class UiConfig {
		TrackListData timelineTrackList = new TrackListData();
	}

	private static final class TrackListData {
		float trackHeaderWidthPx = TimelineLayout.TRACK_LABEL_WIDTH;
		boolean[] visible = new boolean[TimelineLayout.CONTENT_ROW_COUNT];
		boolean[] locked = new boolean[TimelineLayout.CONTENT_ROW_COUNT];
		Map<Integer, String> customNames = new HashMap<>();
		Set<Integer> collapsedGroupRows = new HashSet<>();

		TrackListData() {
			for (int i = 0; i < visible.length; i++) visible[i] = true;
		}

		static TrackListData fromState(TimelineTrackListState state) {
			TrackListData d = new TrackListData();
			d.trackHeaderWidthPx = state.getTrackHeaderWidth();
			d.visible = state.copyVisibleStates();
			d.locked = state.copyLockedStates();
			d.customNames = state.copyCustomNames();
			d.collapsedGroupRows = state.copyCollapsedGroupRows();
			return d;
		}
	}
}
