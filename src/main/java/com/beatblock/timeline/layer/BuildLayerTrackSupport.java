package com.beatblock.timeline.layer;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TrackType;
import com.beatblock.timeline.rendering.TimelineTrackMeta;
import com.beatblock.ui.i18n.BBTexts;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** 建造图层时间线轨道：ID 规范、行槽映射、旧轨迁移。 */
public final class BuildLayerTrackSupport {

	public static final String TRACK_ID_PREFIX = "build_layer_";
	public static final String LEGACY_TRACK_ID = "build_reverse";
	public static final String DEFAULT_FIRST_TRACK_ID = "build_layer_1";
	public static final int MAX_TRACKS = 16;

	private BuildLayerTrackSupport() {
	}

	public static boolean isBuildLayerTrackId(String trackId) {
		if (trackId == null || trackId.isBlank()) {
			return false;
		}
		return trackId.startsWith(TRACK_ID_PREFIX) || LEGACY_TRACK_ID.equals(trackId);
	}

	public static List<Track> listTracks(Timeline timeline) {
		if (timeline == null) {
			return List.of();
		}
		List<Track> result = new ArrayList<>();
		for (Track track : timeline.getTracks()) {
			if (isBuildLayerTrack(track)) {
				result.add(track);
			}
		}
		return List.copyOf(result);
	}

	public static boolean isBuildLayerTrack(Track track) {
		if (track == null) {
			return false;
		}
		if (track.getType() == TrackType.BUILD_LAYER) {
			return true;
		}
		return isBuildLayerTrackId(track.getId());
	}

	public static int rowForSlot(int slot) {
		return TimelineTrackMeta.ROW_BUILD_LAYER_START + slot;
	}

	public static int slotForRow(int rowIndex) {
		if (!TimelineTrackMeta.isBuildLayerSubRow(rowIndex)) {
			return -1;
		}
		return rowIndex - TimelineTrackMeta.ROW_BUILD_LAYER_START;
	}

	public static String trackIdForRow(Timeline timeline, int rowIndex) {
		int slot = slotForRow(rowIndex);
		if (slot < 0) {
			return null;
		}
		List<Track> tracks = listTracks(timeline);
		if (slot >= tracks.size()) {
			return null;
		}
		return tracks.get(slot).getId();
	}

	public static int rowForTrackId(Timeline timeline, String trackId) {
		if (timeline == null || trackId == null) {
			return -1;
		}
		List<Track> tracks = listTracks(timeline);
		for (int i = 0; i < tracks.size(); i++) {
			if (trackId.equals(tracks.get(i).getId())) {
				return rowForSlot(i);
			}
		}
		return -1;
	}

	public static boolean canCreateMoreTracks(Timeline timeline) {
		return listTracks(timeline).size() < MAX_TRACKS;
	}

	public static String nextTrackId(Timeline timeline) {
		for (int i = 1; i <= MAX_TRACKS + 4; i++) {
			String candidate = TRACK_ID_PREFIX + i;
			if (timeline == null || timeline.getTrack(candidate) == null) {
				return candidate;
			}
		}
		return TRACK_ID_PREFIX + UUID.randomUUID().toString().substring(0, 8);
	}

	public static String nextDefaultTrackName(Timeline timeline) {
		int n = listTracks(timeline).size() + 1;
		return displayNameForSlot(n);
	}

	/** 解析轨道显示名：兼容存为 i18n 键的旧数据。 */
	public static String displayNameFor(Track track, int slotIndex) {
		if (track == null) {
			return displayNameForSlot(slotIndex + 1);
		}
		String name = track.getName();
		if (name == null || name.isBlank()) {
			return displayNameForSlot(slotIndex + 1);
		}
		if (name.startsWith("beatblock.")) {
			if (name.contains("numbered")) {
				return displayNameForSlot(slotIndex + 1);
			}
			String translated = BBTexts.get(name);
			if (!translated.equals(name)) {
				return translated;
			}
			return displayNameForSlot(slotIndex + 1);
		}
		return name;
	}

	private static String displayNameForSlot(int slotNumber) {
		return BBTexts.get("beatblock.track.default.build_layer_numbered", slotNumber);
	}

	public static Track ensureDefaultTrack(Timeline timeline) {
		if (timeline == null) {
			return null;
		}
		migrateLegacyTrack(timeline);
		List<Track> existing = listTracks(timeline);
		if (!existing.isEmpty()) {
			return existing.getFirst();
		}
		Track track = new Track(DEFAULT_FIRST_TRACK_ID, displayNameForSlot(1), TrackType.BUILD_LAYER);
		timeline.addTrack(track);
		return track;
	}

	public static void migrateLegacyTrack(Timeline timeline) {
		if (timeline == null) {
			return;
		}
		Track legacy = timeline.getTrack(LEGACY_TRACK_ID);
		if (legacy == null) {
			return;
		}
		Track target = timeline.getTrack(DEFAULT_FIRST_TRACK_ID);
		if (target == null) {
			target = new Track(
				DEFAULT_FIRST_TRACK_ID,
				legacy.getName() != null && !legacy.getName().isBlank()
					? legacy.getName()
					: BBTexts.get("beatblock.track.default.build_reverse"),
				TrackType.BUILD_LAYER
			);
			timeline.addTrack(target);
		} else if (target.getType() != TrackType.BUILD_LAYER) {
			target.setType(TrackType.BUILD_LAYER);
		}
		List<com.beatblock.timeline.Clip> toMove = new ArrayList<>(legacy.getClips());
		for (var clip : toMove) {
			if (clip == null) {
				continue;
			}
			legacy.removeClip(clip.getId());
			target.addClip(clip);
		}
		timeline.removeTrack(LEGACY_TRACK_ID);
		timeline.markAnimationEventsDirty();
	}

	public static void normalizeLoadedTracks(Timeline timeline) {
		if (timeline == null) {
			return;
		}
		migrateLegacyTrack(timeline);
		List<Track> tracks = listTracks(timeline);
		for (int i = 0; i < tracks.size(); i++) {
			Track track = tracks.get(i);
			if (track.getType() != TrackType.BUILD_LAYER) {
				track.setType(TrackType.BUILD_LAYER);
			}
			String name = track.getName();
			if (name != null && name.startsWith("beatblock.")) {
				track.setName(displayNameFor(track, i));
			}
		}
	}
}
