package com.beatblock.audio.scheduler;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BeatClock
 * ─────────────────────────────────────────────────────────────────────────────
 * 音频播放时间与游戏 tick 之间的同步桥。
 */
public final class BeatClock {

	private final IAudioPlayer audioPlayer;

	private final AtomicLong positionMs = new AtomicLong(0);
	private final AtomicLong lastPositionMs = new AtomicLong(0);
	private final AtomicBoolean playing = new AtomicBoolean(false);

	private volatile long wallClockStartMs = -1;
	private volatile long audioStartMs = 0;

	public BeatClock(IAudioPlayer audioPlayer) {
		this.audioPlayer = audioPlayer;
	}

	public void tick() {
		if (!playing.get()) return;
		long prev = positionMs.get();
		long now = queryPosition();
		lastPositionMs.set(prev);
		positionMs.set(now);
	}

	public void play() {
		wallClockStartMs = System.currentTimeMillis();
		audioStartMs = audioPlayer.getPlaybackPositionMs();
		playing.set(true);
	}

	public void pause() {
		playing.set(false);
	}

	public void stop() {
		playing.set(false);
		positionMs.set(0);
		lastPositionMs.set(0);
		wallClockStartMs = -1;
	}

	public void seekTo(long ms) {
		audioPlayer.seekTo(ms);
		positionMs.set(ms);
		lastPositionMs.set(ms);
		wallClockStartMs = System.currentTimeMillis();
		audioStartMs = ms;
	}

	public long getPositionMs() {
		return positionMs.get();
	}

	public long getDeltaMs() {
		return positionMs.get() - lastPositionMs.get();
	}

	public boolean isPlaying() {
		return playing.get();
	}

	private long queryPosition() {
		try {
			long pos = audioPlayer.getPlaybackPositionMs();
			if (pos > 0) return pos;
		} catch (Exception ignored) {}

		if (wallClockStartMs >= 0) {
			long elapsed = System.currentTimeMillis() - wallClockStartMs;
			return audioStartMs + elapsed;
		}
		return positionMs.get();
	}

	public interface IAudioPlayer {
		long getPlaybackPositionMs();
		void seekTo(long ms);
	}
}

