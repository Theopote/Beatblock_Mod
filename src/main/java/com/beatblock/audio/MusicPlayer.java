package com.beatblock.audio;

/**
 * 音乐播放与进度控制，与 BeatScheduler 同步驱动时间轴。
 */
public class MusicPlayer {

	private boolean playing;
	private double currentTimeSeconds;
	private double durationSeconds;

	public MusicPlayer() {
		this.playing = false;
		this.currentTimeSeconds = 0;
		this.durationSeconds = 0;
	}

	public boolean isPlaying() {
		return playing;
	}

	public void setPlaying(boolean playing) {
		this.playing = playing;
	}

	public void play() {
		playing = true;
	}

	public void pause() {
		playing = false;
	}

	public void stop() {
		playing = false;
		currentTimeSeconds = 0;
	}

	public double getCurrentTimeSeconds() {
		return currentTimeSeconds;
	}

	public void setCurrentTimeSeconds(double currentTimeSeconds) {
		this.currentTimeSeconds = Math.max(0, Math.min(currentTimeSeconds, durationSeconds));
	}

	public double getDurationSeconds() {
		return durationSeconds;
	}

	public void setDurationSeconds(double durationSeconds) {
		this.durationSeconds = Math.max(0, durationSeconds);
	}

	/**
	 * 每帧调用，推进播放进度（仅当 playing 时）。
	 */
	public void tick(double deltaSeconds) {
		if (playing && durationSeconds > 0) {
			currentTimeSeconds = Math.min(currentTimeSeconds + deltaSeconds, durationSeconds);
			if (currentTimeSeconds >= durationSeconds) {
				playing = false;
			}
		}
	}
}
