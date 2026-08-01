package com.beatblock.runtime;

import com.beatblock.BeatBlock;
import com.beatblock.audio.AudioAnalysisService;
import com.beatblock.audio.AudioConversionService;
import com.beatblock.audio.MusicPlayer;
import com.beatblock.audio.StemMixer;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEditor;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BeatBlockContextTest {

	@Test
	void builderWiresServicesForTests() {
		Timeline timeline = Timeline.createDefault();
		MusicPlayer musicPlayer = new MusicPlayer();
		TimelineEditor editor = new TimelineEditor(timeline, musicPlayer);

		BeatBlockContext context = BeatBlockContext.builder()
			.timeline(timeline)
			.timelineEditor(editor)
			.musicPlayer(musicPlayer)
			.build();

		assertSame(timeline, context.timeline());
		assertSame(editor, context.timelineEditor());
		assertSame(musicPlayer, context.activeAudioPlayer());
		assertSame(editor.getCommandManager(), context.commandManager());
	}

	@Test
	void activeAudioPlayerFallsBackToMusicPlayerWithoutStems() {
		MusicPlayer musicPlayer = new MusicPlayer();
		StemMixer stemMixer = new StemMixer();

		BeatBlockContext context = BeatBlockContext.builder()
			.musicPlayer(musicPlayer)
			.stemMixer(stemMixer)
			.build();

		assertSame(musicPlayer, context.activeAudioPlayer());
		assertFalse(context.usesStemPlayback());
	}

	@Test
	void getContextThrowsWhenModNotInitialized() {
		BeatBlock.resetContext();
		assertThrows(IllegalStateException.class, BeatBlock::getContext);
	}

	@Test
	void musicPlayerDurationAccessibleThroughContext() {
		MusicPlayer musicPlayer = new MusicPlayer();
		musicPlayer.setDurationSeconds(42.0);

		BeatBlockContext context = BeatBlockContext.builder()
			.musicPlayer(musicPlayer)
			.build();

		assertEquals(42.0, context.musicPlayer().getDurationSeconds(), 1e-9);
	}

	@Test
	void closeShutsDownOwnedBackgroundServicesAndIsIdempotent() {
		AudioAnalysisService analysis = AudioAnalysisService.createForTesting();
		AudioConversionService conversion = AudioConversionService.createForTesting();
		BeatBlockContext context = BeatBlockContext.builder()
			.externalAudioAnalyzer(analysis)
			.audioConversionService(conversion)
			.build();

		context.close();
		context.close();

		assertThrows(RejectedExecutionException.class, () -> analysis.analyze(
			Path.of("after-close.mp3"),
			(step, percent) -> {},
			beatmap -> {},
			error -> {}
		));
		assertThrows(RejectedExecutionException.class, () -> conversion.convertToMp3Async(
			Path.of("after-close.wav"),
			null,
			path -> {},
			error -> {}
		));
	}
}
