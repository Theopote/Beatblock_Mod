package com.beatblock.ui.presenter;

import com.beatblock.audio.MusicPlayer;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import com.beatblock.timeline.TimelineMarker;
import com.beatblock.timeline.TimelineOperations;
import com.beatblock.timeline.rendering.TimelineToolbarState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimelineTransportPresenterTest {

	private Timeline timeline;
	private TimelineEditor editor;
	private TimelineToolbarState toolbarState;
	private AtomicBoolean driving;
	private AtomicBoolean driveStarted;
	private TimelineTransportPresenter presenter;

	@BeforeEach
	void setUp() {
		timeline = Timeline.createDefault();
		timeline.setDurationSeconds(120.0);
		timeline.setMetadata("bpm", 120.0);
		editor = new TimelineEditor(timeline);
		toolbarState = editor.getToolbarState();
		driving = new AtomicBoolean(false);
		driveStarted = new AtomicBoolean(false);
		presenter = new TimelineTransportPresenter(
			() -> editor,
			() -> timeline,
			() -> null,
			() -> null,
			new TimelineTransportPresenter.TimelineDriveControl() {
				@Override
				public boolean isDriving() {
					return driving.get();
				}

				@Override
				public void startDriving() {
					driveStarted.set(true);
					driving.set(true);
				}

				@Override
				public void stopDriving() {
					driving.set(false);
				}
			}
		);
	}

	@Test
	void viewStateComputesBeatSeekStep() {
		var state = presenter.viewState(editor, false);
		assertEquals(0.5, state.seekStep(), 1e-9);
		assertEquals(0.5, state.stepSeek(), 1e-9);
		assertFalse(state.playing());
		assertFalse(state.positionDisplay().isBlank());
	}

	@Test
	void viewStateUsesFiveSecondStepWhenShiftHeld() {
		var state = presenter.viewState(editor, true);
		assertEquals(5.0, state.stepSeek(), 1e-9);
	}

	@Test
	void seekToClampsToDuration() {
		presenter.seekTo(editor, 999);
		assertEquals(120.0, editor.getClock().getCurrentTimeSeconds(), 1e-9);
	}

	@Test
	void playStartsClockAndDrive() {
		presenter.play(editor);
		assertTrue(editor.getClock().isPlaying());
		assertTrue(driveStarted.get());
	}

	@Test
	void stopResetsTimeAndDrive() {
		presenter.seekTo(editor, 10);
		driving.set(true);
		presenter.stop(editor);
		assertEquals(0.0, editor.getClock().getCurrentTimeSeconds(), 1e-9);
		assertFalse(driving.get());
	}

	@Test
	void jumpToNearbyEventUsesMarkers() {
		timeline.addMarker(new TimelineMarker(2.0, "A"));
		timeline.addMarker(new TimelineMarker(8.0, "B"));
		editor.getClock().seek(5.0);
		presenter.jumpToNearbyEvent(editor, true);
		assertEquals(8.0, editor.getClock().getCurrentTimeSeconds(), 1e-9);
	}

	@Test
	void addMarkerAtCurrentTimeAppendsMarker() {
		editor.getClock().seek(3.25);
		assertTrue(presenter.addMarkerAtCurrentTime(editor));
		assertEquals(1, timeline.getMarkers().size());
		assertEquals(3.25, timeline.getMarkers().get(0).getTimeSeconds(), 1e-9);
	}

	@Test
	void setLoopInAtAdjustsOutWhenNeeded() {
		toolbarState.setLoopOutSeconds(1.0);
		presenter.setLoopInAt(toolbarState, 5.0, 0.5);
		assertEquals(5.0, toolbarState.getLoopInSeconds(), 1e-9);
		assertTrue(toolbarState.getLoopOutSeconds() > 5.0);
	}

	@Test
	void resolveSeekStepReturnsOneWhenBpmZero() {
		assertEquals(1.0, TimelineTransportPresenter.resolveSeekStep(0), 1e-9);
	}

	@Test
	void seekByAdvancesClock() {
		editor.getClock().seek(10.0);
		presenter.seekBy(editor, 2.5);
		assertEquals(12.5, editor.getClock().getCurrentTimeSeconds(), 1e-9);
	}

	@Test
	void pauseStopsClock() {
		presenter.play(editor);
		assertTrue(editor.getClock().isPlaying());
		presenter.pause();
		assertFalse(editor.getClock().isPlaying());
	}

	@Test
	void jumpToNearbyEventBackwardUsesMarkers() {
		timeline.addMarker(new TimelineMarker(2.0, "A"));
		timeline.addMarker(new TimelineMarker(8.0, "B"));
		editor.getClock().seek(5.0);
		presenter.jumpToNearbyEvent(editor, false);
		assertEquals(2.0, editor.getClock().getCurrentTimeSeconds(), 1e-9);
	}

	@Test
	void collectNavigationTimesFallsBackToClipEventsWhenNoMarkers() {
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 0.0, 4.0);
		TimelineOperations.addEvent(clip, 1.5, EventType.ANIMATION, Map.of("animationType", "pulse"));
		TimelineOperations.addEvent(clip, 3.0, EventType.ANIMATION, Map.of("animationType", "pulse"));

		assertEquals(List.of(1.5, 3.0), TimelineTransportPresenter.collectNavigationTimes(timeline));
	}

	@Test
	void jumpToNearbyEventUsesClipEventsWhenNoMarkers() {
		var track = timeline.getTrack(Timeline.TRACK_ID_ANIMATION_AUTO);
		var clip = TimelineOperations.addClip(track, 0.0, 4.0);
		TimelineOperations.addEvent(clip, 1.0, EventType.ANIMATION, Map.of("animationType", "pulse"));
		TimelineOperations.addEvent(clip, 3.0, EventType.ANIMATION, Map.of("animationType", "pulse"));
		editor.getClock().seek(2.0);

		presenter.jumpToNearbyEvent(editor, true);
		assertEquals(3.0, editor.getClock().getCurrentTimeSeconds(), 1e-9);
	}

	@Test
	void addMarkerAtCurrentTimeReturnsFalseWhenTimelineMissing() {
		AtomicReference<Timeline> missingTimeline = new AtomicReference<>(timeline);
		var missing = new TimelineTransportPresenter(
			() -> editor,
			missingTimeline::get,
			() -> null,
			() -> null,
			null
		);
		missingTimeline.set(null);
		assertFalse(missing.addMarkerAtCurrentTime(editor));
	}

	@Test
	void setLoopOutAtEnforcesMinimumGapFromLoopIn() {
		toolbarState.setLoopInSeconds(4.0);
		presenter.setLoopOutAt(toolbarState, 4.0, 0.5);
		assertTrue(toolbarState.getLoopOutSeconds() >= 4.5);
	}

	@Test
	void clearLoopRangeClearsToolbarState() {
		toolbarState.setLoopInSeconds(2.0);
		toolbarState.setLoopOutSeconds(8.0);
		presenter.clearLoopRange(toolbarState);
		assertFalse(toolbarState.isLoop());
	}

	@Test
	void resolveDurationPrefersTimelineDuration() {
		timeline.setDurationSeconds(90.0);
		assertEquals(90.0, presenter.resolveDuration(editor), 1e-9);
	}

	@Test
	void seekToSyncsMusicPlayer() {
		MusicPlayer music = new MusicPlayer();
		music.setDurationSeconds(120.0);
		var withMusic = new TimelineTransportPresenter(
			() -> editor,
			() -> timeline,
			() -> music,
			() -> null,
			null
		);
		withMusic.seekTo(editor, 15.0);
		assertEquals(15.0, music.getCurrentTimeSeconds(), 1e-9);
	}

	@Test
	void setPlaybackSpeedSyncsClockAndMusic() {
		MusicPlayer music = new MusicPlayer();
		var withMusic = new TimelineTransportPresenter(
			() -> editor,
			() -> timeline,
			() -> music,
			() -> null,
			null
		);
		withMusic.setPlaybackSpeed(editor, 1.5);
		assertEquals(1.5, editor.getClock().getPlaybackSpeed(), 1e-9);
		assertEquals(1.5, music.getPlaybackSpeed(), 1e-9);
	}

	@Test
	void viewStateNullEditorUsesFallbackDuration() {
		var state = presenter.viewState(null, false);
		assertEquals(60.0, state.durationSeconds(), 1e-9);
		assertFalse(state.hasMusic());
	}

	@Test
	void playInStemModeStartsStemMixerOnly() {
		MusicPlayer music = new MusicPlayer();
		music.setDurationSeconds(120.0);
		RecordingAudioPlayer stems = new RecordingAudioPlayer();
		var stemPresenter = new TimelineTransportPresenter(
			() -> editor,
			() -> timeline,
			() -> music,
			() -> stems,
			null
		);
		stemPresenter.play(editor);
		assertTrue(stems.playInvoked);
		assertFalse(music.isPlaying());
	}

	@Test
	void pauseInStemModePausesStemMixerOnly() {
		MusicPlayer music = new MusicPlayer();
		music.setDurationSeconds(120.0);
		music.play();
		RecordingAudioPlayer stems = new RecordingAudioPlayer();
		stems.playing = true;
		var stemPresenter = new TimelineTransportPresenter(
			() -> editor,
			() -> timeline,
			() -> music,
			() -> stems,
			null
		);
		stemPresenter.pause();
		assertTrue(stems.pauseInvoked);
		assertFalse(music.isPlaying());
	}

	private static final class RecordingAudioPlayer implements com.beatblock.timeline.IAudioPlayer {
		boolean playing;
		boolean playInvoked;
		boolean pauseInvoked;
		double timeSeconds;

		@Override
		public boolean isPlaying() {
			return playing;
		}

		@Override
		public double getCurrentTimeSeconds() {
			return timeSeconds;
		}

		@Override
		public void setCurrentTimeSeconds(double timeSeconds) {
			this.timeSeconds = timeSeconds;
		}

		@Override
		public void play() {
			playInvoked = true;
			playing = true;
		}

		@Override
		public void pause() {
			pauseInvoked = true;
			playing = false;
		}

		@Override
		public void stop() {
			playing = false;
			timeSeconds = 0.0;
		}
	}
}
