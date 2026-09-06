package com.beatblock.automap.vfx;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.generation.TimelineGenerationMetadata;
import com.beatblock.timeline.generation.TimelineGenerationMetadataSupport;
import com.beatblock.timeline.playback.GlobalEventPayloadCodec;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Compiles {@link GlobalEventCreationRequest} into Timeline global-track clip + event (one-way).
 */
public final class GlobalEventTimelineWriter {

	public record WriteResult(@Nullable String clipId, @Nullable String eventId) {
		public static final WriteResult EMPTY = new WriteResult(null, null);

		public boolean written() {
			return clipId != null && !clipId.isBlank() && eventId != null && !eventId.isBlank();
		}
	}

	private GlobalEventTimelineWriter() {
	}

	public static WriteResult write(
		Timeline timeline,
		GlobalEventCreationRequest request,
		TimelineGenerationMetadata metadata
	) {
		if (timeline == null || request == null || request.payload() == null) {
			return WriteResult.EMPTY;
		}
		Track track = timeline.getTrack(Timeline.TRACK_ID_GLOBAL);
		if (track == null) {
			return WriteResult.EMPTY;
		}
		Set<String> before = clipIds(track);
		double time = request.timeSeconds();
		Clip clip = TimelineOperations.addClip(track, time, time + 0.1);
		if (clip == null) {
			return WriteResult.EMPTY;
		}
		Map<String, Object> params = GlobalEventPayloadCodec.encode(request.payload());
		TimelineGenerationMetadata meta = metadata != null
			? metadata
			: TimelineGenerationMetadata.manual();
		TimelineEvent event = TimelineOperations.addEvent(
			clip,
			time,
			EventType.GLOBAL,
			TimelineGenerationMetadataSupport.apply(params, meta)
		);
		if (event == null) {
			track.removeClip(clip.getId());
			return WriteResult.EMPTY;
		}
		String clipId = findNewClipId(track, before);
		return new WriteResult(
			clipId != null ? clipId : clip.getId(),
			event.getId()
		);
	}

	private static Set<String> clipIds(Track track) {
		Set<String> ids = new HashSet<>();
		for (Clip clip : track.getClips()) {
			if (clip != null && clip.getId() != null) {
				ids.add(clip.getId());
			}
		}
		return ids;
	}

	private static @Nullable String findNewClipId(Track track, Set<String> before) {
		for (Clip clip : track.getClips()) {
			if (clip != null && clip.getId() != null && !before.contains(clip.getId())) {
				return clip.getId();
			}
		}
		return null;
	}
}
