package com.beatblock.timeline.playback;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.IAudioPlayer;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.editor.TimelineClock;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * 播放会话：时间轴时钟与音频源的<strong>唯一协调门面</strong>。
 * <p>
 * 规则：
 * <ul>
 *   <li>{@link #isPlaying()} 以活跃音频源为准；无音频时回退 {@link TimelineClock}</li>
 *   <li>{@link #currentTimeSeconds()} 播放中优先跟音频；暂停/scrub 时以时钟为准</li>
 *   <li>{@link #seek(double)} 同时更新时钟与音频（含分段音频路径）</li>
 *   <li>{@link #syncFromAudio()} 每帧在 UI 中把音频进度拉回时钟，并处理循环区</li>
 * </ul>
 * {@link TimelineClock} 仍作本地缓存（标尺/面板读时间），但不应再被业务代码单独 play/pause。
 */
public final class PlaybackSession {

	/** 驱动 ClientDriver 舞台回放（可空）。 */
	public interface DriveControl {
		boolean isDriving();
		void startDriving();
		void stopDriving();
	}

	private final TimelineClock clock;
	private final Timeline timeline;
	private final TimelineToolbarState toolbarState;
	private final @Nullable MusicPlayer musicPlayer;
	private final @Nullable IAudioPlayer boundAudioPlayer;
	private Supplier<@Nullable IAudioPlayer> activeAudioSupplier;
	private DriveControl driveControl = new DriveControl() {
		@Override public boolean isDriving() { return false; }
		@Override public void startDriving() {}
		@Override public void stopDriving() {}
	};

	public PlaybackSession(
		@NonNull TimelineClock clock,
		@NonNull Timeline timeline,
		@NonNull TimelineToolbarState toolbarState,
		@Nullable MusicPlayer musicPlayer,
		@Nullable IAudioPlayer boundAudioPlayer
	) {
		this.clock = clock;
		this.timeline = timeline;
		this.toolbarState = toolbarState;
		this.musicPlayer = musicPlayer;
		this.boundAudioPlayer = boundAudioPlayer;
		this.activeAudioSupplier = () -> boundAudioPlayer;
	}

	/** 分轨模式下切换活跃音频源（StemMixer / MusicPlayer）。 */
	public void setActiveAudioSupplier(@Nullable Supplier<@Nullable IAudioPlayer> supplier) {
		this.activeAudioSupplier = supplier != null ? supplier : () -> boundAudioPlayer;
	}

	public void setDriveControl(@Nullable DriveControl driveControl) {
		this.driveControl = driveControl != null ? driveControl : new DriveControl() {
			@Override public boolean isDriving() { return false; }
			@Override public void startDriving() {}
			@Override public void stopDriving() {}
		};
	}

	public @NonNull TimelineClock clock() {
		return clock;
	}

	public @Nullable IAudioPlayer activeAudio() {
		IAudioPlayer active = activeAudioSupplier.get();
		if (active != null) return active;
		return boundAudioPlayer;
	}

	/**
	 * 是否正在播放。有音频源时以音频为准；仅时钟存在时用 clock 标志
	 * （无音乐预览 / 测试）。
	 */
	public boolean isPlaying() {
		IAudioPlayer audio = activeAudio();
		if (audio != null) {
			return audio.isPlaying();
		}
		return clock.isPlaying();
	}

	/**
	 * 当前时间（秒）。
	 * 音频正在播放时跟音频；否则跟时钟（scrub / 暂停后的权威位置）。
	 */
	public double currentTimeSeconds() {
		IAudioPlayer audio = activeAudio();
		if (audio != null && audio.isPlaying() && !isSegmentedMusicPath()) {
			return clampTime(audio.getCurrentTimeSeconds());
		}
		return clampTime(clock.getCurrentTimeSeconds());
	}

	public double durationSeconds() {
		double d = timeline.getDurationSeconds();
		if (d > 0) return d;
		if (musicPlayer != null && musicPlayer.getDurationSeconds() > 0) {
			return musicPlayer.getDurationSeconds();
		}
		double cd = clock.getDurationSeconds();
		return cd > 0 ? cd : 60.0;
	}

	public void seek(double timeSeconds) {
		double t = clampTime(timeSeconds);
		clock.seek(t);
		IAudioPlayer audio = activeAudio();
		if (audio == null) {
			notifyCameraTick();
			return;
		}
		applyAudioSeek(audio, t);
		// 分轨时仍同步全曲混音进度，便于切换
		if (musicPlayer != null && audio != musicPlayer) {
			musicPlayer.setCurrentTimeSeconds(t);
		}
		notifyCameraTick();
	}

	public void play() {
		ensureMusicBound();
		pauseFullMixIfStem();
		IAudioPlayer audio = activeAudio();
		if (audio != null) {
			audio.play();
		}
		clock.play();
		if (!driveControl.isDriving()) {
			driveControl.startDriving();
		}
	}

	public void pause() {
		IAudioPlayer audio = activeAudio();
		if (audio != null) {
			audio.pause();
		}
		pauseFullMixIfStem();
		clock.pause();
	}

	public void stop() {
		IAudioPlayer audio = activeAudio();
		if (audio != null) {
			audio.stop();
		}
		if (isStemMode() && musicPlayer != null) {
			musicPlayer.pause();
		}
		clock.pause();
		driveControl.stopDriving();
		seek(0);
	}

	public double getPlaybackSpeed() {
		if (musicPlayer != null) {
			return musicPlayer.getPlaybackSpeed();
		}
		return clock.getPlaybackSpeed();
	}

	public void setPlaybackSpeed(double speed) {
		clock.setPlaybackSpeed(speed);
		if (musicPlayer != null) {
			musicPlayer.setPlaybackSpeed(speed);
		}
	}

	/**
	 * 每帧调用：音频播放中把进度写入时钟，并处理循环区。
	 *
	 * @param preferredAudio 面板传入的活跃播放器（可与 supplier 一致）；null 则用 supplier
	 */
	public void syncFromAudio(@Nullable IAudioPlayer preferredAudio) {
		IAudioPlayer playback = preferredAudio != null ? preferredAudio : activeAudio();
		if (playback == null || !playback.isPlaying()) {
			// 音频已停但 clock 仍标 playing → 对齐
			if (!playbackPlaying(playback) && clock.isPlaying() && playback != null) {
				clock.pause();
			}
			return;
		}
		// 确保 clock 与音频一致
		if (!clock.isPlaying()) {
			clock.play();
		}

		if (playback == musicPlayer && syncSegmentedMusicPlayback()) {
			return;
		}

		double t = playback.getCurrentTimeSeconds();
		double dur = timeline.getDurationSeconds();
		if (toolbarState != null && toolbarState.isLoop()) {
			double loopIn = Math.max(0, toolbarState.getLoopInSeconds());
			double loopOut = toolbarState.hasLoopRange() ? toolbarState.getLoopOutSeconds() : dur;
			if (loopOut <= loopIn) loopOut = dur;
			if (loopOut > 0) {
				if (t >= loopOut) {
					playback.setCurrentTimeSeconds(loopIn);
					clock.seek(loopIn);
					return;
				}
				if (t < loopIn) {
					playback.setCurrentTimeSeconds(loopIn);
					clock.seek(loopIn);
					return;
				}
			}
		}
		clock.setCurrentTimeSeconds(t);
	}

	// ── internal ─────────────────────────────────────────────────────

	private boolean playbackPlaying(@Nullable IAudioPlayer playback) {
		return playback != null && playback.isPlaying();
	}

	private double clampTime(double t) {
		double dur = durationSeconds();
		if (dur <= 0) return Math.max(0, t);
		return Math.max(0, Math.min(t, dur));
	}

	private boolean isStemMode() {
		IAudioPlayer active = activeAudio();
		return active != null && musicPlayer != null && active != musicPlayer;
	}

	private void pauseFullMixIfStem() {
		if (!isStemMode() || musicPlayer == null) return;
		if (musicPlayer.isPlaying()) {
			musicPlayer.pause();
		}
	}

	private boolean isSegmentedMusicPath() {
		if (musicPlayer == null) return false;
		IAudioPlayer active = activeAudio();
		if (active != musicPlayer) return false;
		return hasSegmentedClipAudio();
	}

	private void applyAudioSeek(IAudioPlayer audio, double timelineTime) {
		if (musicPlayer == null || audio != musicPlayer || !hasSegmentedClipAudio()) {
			audio.setCurrentTimeSeconds(timelineTime);
			return;
		}

		Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		if (audioTrack == null || audioTrack.getClips().isEmpty()) {
			audio.setCurrentTimeSeconds(timelineTime);
			return;
		}

		Clip targetClip = null;
		for (Clip c : audioTrack.getClips()) {
			if (c == null) continue;
			if (timelineTime >= c.getStartTimeSeconds() && timelineTime <= c.getEndTimeSeconds()) {
				targetClip = c;
				break;
			}
		}
		if (targetClip == null) {
			timeline.setMetadata("activeAudioClipId", null);
			if (audio.isPlaying()) {
				audio.pause();
				clock.pause();
			}
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
				Math.min(timelineTime - targetClip.getStartTimeSeconds(), targetClip.getDurationSeconds()));
			audio.setCurrentTimeSeconds(localTime);
			timeline.setMetadata("activeAudioClipId", targetClip.getId());
			return;
		}
		audio.setCurrentTimeSeconds(timelineTime);
	}

	private boolean syncSegmentedMusicPlayback() {
		if (musicPlayer == null) return false;
		IAudioPlayer active = activeAudio();
		if (active != musicPlayer) return false;
		Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		if (audioTrack == null || audioTrack.getClips().isEmpty()) return false;
		if (!hasSegmentedClipAudio()) return false;

		double clockTime = clock.getCurrentTimeSeconds();
		Clip activeClip = null;
		for (Clip c : audioTrack.getClips()) {
			if (c == null) continue;
			if (clockTime >= c.getStartTimeSeconds() && clockTime <= c.getEndTimeSeconds()) {
				activeClip = c;
				break;
			}
		}
		if (activeClip == null) {
			if (musicPlayer.isPlaying()) {
				musicPlayer.pause();
			}
			clock.pause();
			return true;
		}

		Object pathObj = timeline.getMetadata("clipAudioPath_" + activeClip.getId());
		if (pathObj == null) return false;
		String targetPath = pathObj.toString();
		String loadedPath = musicPlayer.getLoadedAudioPath();
		if (loadedPath == null || !loadedPath.equals(targetPath)) {
			musicPlayer.loadAudio(targetPath);
			musicPlayer.play();
			double local = Math.max(0.0,
				Math.min(clockTime - activeClip.getStartTimeSeconds(), activeClip.getDurationSeconds()));
			musicPlayer.setCurrentTimeSeconds(local);
		}

		double globalTime = activeClip.getStartTimeSeconds() + musicPlayer.getCurrentTimeSeconds();
		if (globalTime >= activeClip.getEndTimeSeconds()) {
			Clip next = null;
			for (Clip c : audioTrack.getClips()) {
				if (c != null && c.getStartTimeSeconds() >= activeClip.getEndTimeSeconds()) {
					if (next == null || c.getStartTimeSeconds() < next.getStartTimeSeconds()) {
						next = c;
					}
				}
			}
			if (next != null) {
				Object nextPathObj = timeline.getMetadata("clipAudioPath_" + next.getId());
				if (nextPathObj != null) {
					String nextPath = nextPathObj.toString();
					if (!nextPath.equals(musicPlayer.getLoadedAudioPath())) {
						musicPlayer.loadAudio(nextPath);
					}
					musicPlayer.setCurrentTimeSeconds(0);
					musicPlayer.play();
					globalTime = next.getStartTimeSeconds();
				}
			}
		}
		clock.setCurrentTimeSeconds(globalTime);
		return true;
	}

	private boolean hasSegmentedClipAudio() {
		Track audioTrack = timeline.getTrack(Timeline.TRACK_ID_AUDIO);
		if (audioTrack == null) return false;
		for (Clip c : audioTrack.getClips()) {
			if (c == null) continue;
			Object pathObj = timeline.getMetadata("clipAudioPath_" + c.getId());
			if (pathObj != null && !pathObj.toString().isBlank()) return true;
		}
		return false;
	}

	private void ensureMusicBound() {
		if (musicPlayer == null) return;
		Object audioPath = timeline.getMetadata("audioPath");
		if (audioPath instanceof String path && !path.isBlank()) {
			String loaded = musicPlayer.getLoadedAudioPath();
			if (!path.equals(loaded)) {
				musicPlayer.loadAudio(path);
			}
		}
		if (musicPlayer.getDurationSeconds() <= 0) {
			double d = durationSeconds();
			if (d > 0) {
				musicPlayer.setDurationSeconds(d);
			}
		}
	}

	private static void notifyCameraTick() {
		try {
			com.beatblock.client.camera.TimelineCameraController.getInstance().tick();
		} catch (Throwable ignored) {
			// 单元测试无客户端环境
		}
	}
}
