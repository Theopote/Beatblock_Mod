package com.beatblock.timeline.interaction;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.IAudioPlayer;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.TimelineClock;

/** 拖动标尺/播放头时同步时钟与音乐进度。 */
public final class TimelinePlaybackSeeker {

	private TimelinePlaybackSeeker() {}

	public static void seek(
		TimelineClock clock,
		double timeSeconds,
		IAudioPlayer audioPlayer,
		MusicPlayer musicPlayer,
		Timeline timeline
	) {
		clock.seek(timeSeconds);
		try {
			if (audioPlayer == null) return;

			if (timeline == null || musicPlayer == null || audioPlayer != musicPlayer) {
				audioPlayer.setCurrentTimeSeconds(clock.getCurrentTimeSeconds());
				return;
			}

			Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
			if (audioTrack == null || audioTrack.getClips().isEmpty()) {
				audioPlayer.setCurrentTimeSeconds(clock.getCurrentTimeSeconds());
				return;
			}

			boolean segmentedTimeline = hasSegmentedClipAudio(timeline, audioTrack);

			Clip targetClip = null;
			double t = clock.getCurrentTimeSeconds();
			for (Clip c : audioTrack.getClips()) {
				if (c == null) continue;
				if (t >= c.getStartTimeSeconds() && t <= c.getEndTimeSeconds()) {
					targetClip = c;
					break;
				}
			}
			if (targetClip == null) {
				if (segmentedTimeline) {
					timeline.setMetadata("activeAudioClipId", null);
					if (audioPlayer.isPlaying()) {
						audioPlayer.pause();
					}
					return;
				}
				audioPlayer.setCurrentTimeSeconds(t);
				return;
			}

			Object pathObj = timeline.getMetadata("clipAudioPath_" + targetClip.getId());
			if (pathObj != null) {
				String path = pathObj.toString();
				String loadedPath = musicPlayer.getLoadedAudioPath();
				if (loadedPath == null || !loadedPath.equals(path)) {
					boolean wasPlaying = musicPlayer.isPlaying();
					musicPlayer.loadAudio(path);
					if (wasPlaying) musicPlayer.play();
				}
				double localTime = Math.max(0.0,
					Math.min(t - targetClip.getStartTimeSeconds(), targetClip.getDurationSeconds()));
				audioPlayer.setCurrentTimeSeconds(localTime);
				timeline.setMetadata("activeAudioClipId", targetClip.getId());
				return;
			}

			audioPlayer.setCurrentTimeSeconds(t);
		} finally {
			com.beatblock.client.camera.TimelineCameraController.getInstance().tick();
		}
	}

	private static boolean hasSegmentedClipAudio(Timeline timeline, Track audioTrack) {
		if (timeline == null || audioTrack == null) return false;
		for (Clip c : audioTrack.getClips()) {
			if (c == null) continue;
			Object pathObj = timeline.getMetadata("clipAudioPath_" + c.getId());
			if (pathObj != null && !pathObj.toString().isBlank()) return true;
		}
		return false;
	}
}
