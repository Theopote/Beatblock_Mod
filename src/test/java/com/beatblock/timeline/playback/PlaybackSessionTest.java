package com.beatblock.timeline.playback;

import com.beatblock.timeline.IAudioPlayer;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.editor.TimelineClock;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackSessionTest {

	private Timeline timeline;
	private TimelineClock clock;
	private TimelineToolbarState toolbar;
	private FakeAudio audio;
	private PlaybackSession session;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		timeline.setDurationSeconds(30.0);
		clock = new TimelineClock();
		clock.setDurationSeconds(30.0);
		toolbar = new TimelineToolbarState();
		audio = new FakeAudio();
		session = new PlaybackSession(clock, timeline, toolbar, null, audio);
	}

	@Test
	void seekUpdatesClockAndAudio() {
		session.seek(12.5);
		assertEquals(12.5, clock.getCurrentTimeSeconds(), 1e-9);
		assertEquals(12.5, audio.time, 1e-9);
		assertEquals(12.5, session.currentTimeSeconds(), 1e-9);
	}

	@Test
	void seekClampsToDuration() {
		session.seek(999);
		assertEquals(30.0, session.currentTimeSeconds(), 1e-9);
	}

	@Test
	void isPlayingFollowsAudioWhenPresent() {
		assertFalse(session.isPlaying());
		session.play();
		assertTrue(audio.playing);
		assertTrue(session.isPlaying());
		assertTrue(clock.isPlaying());

		session.pause();
		assertFalse(audio.playing);
		assertFalse(session.isPlaying());
		assertFalse(clock.isPlaying());
	}

	@Test
	void currentTimeFollowsAudioWhilePlaying() {
		session.play();
		audio.time = 7.0;
		assertEquals(7.0, session.currentTimeSeconds(), 1e-9);
		// sync pulls into clock
		session.syncFromAudio(audio);
		assertEquals(7.0, clock.getCurrentTimeSeconds(), 1e-9);
	}

	@Test
	void loopRegionWrapsAudioAndClock() {
		toolbar.setLoop(true);
		toolbar.setLoopInSeconds(2.0);
		toolbar.setLoopOutSeconds(5.0);
		session.play();
		audio.time = 5.0;
		session.syncFromAudio(audio);
		assertEquals(2.0, audio.time, 1e-9);
		assertEquals(2.0, clock.getCurrentTimeSeconds(), 1e-9);
	}

	@Test
	void driveControlStartedOnPlay() {
		AtomicBoolean driving = new AtomicBoolean(false);
		AtomicInteger starts = new AtomicInteger();
		session.setDriveControl(new PlaybackSession.DriveControl() {
			@Override
			public boolean isDriving() {
				return driving.get();
			}

			@Override
			public void startDriving() {
				starts.incrementAndGet();
				driving.set(true);
			}

			@Override
			public void stopDriving() {
				driving.set(false);
			}
		});
		session.play();
		assertEquals(1, starts.get());
		assertTrue(driving.get());
		session.stop();
		assertFalse(driving.get());
		assertEquals(0.0, session.currentTimeSeconds(), 1e-9);
	}

	@Test
	void editorExposesPlaybackSession() {
		TimelineEditor editor = new TimelineEditor(timeline);
		assertEquals(0.0, editor.getPlaybackSession().currentTimeSeconds(), 1e-9);
		editor.getPlaybackSession().seek(3.0);
		assertEquals(3.0, editor.getClock().getCurrentTimeSeconds(), 1e-9);
	}

	static final class FakeAudio implements IAudioPlayer {
		boolean playing;
		double time;

		@Override
		public boolean isPlaying() {
			return playing;
		}

		@Override
		public double getCurrentTimeSeconds() {
			return time;
		}

		@Override
		public void setCurrentTimeSeconds(double timeSeconds) {
			time = timeSeconds;
		}

		@Override
		public void play() {
			playing = true;
		}

		@Override
		public void pause() {
			playing = false;
		}

		@Override
		public void stop() {
			playing = false;
			time = 0;
		}
	}
}
