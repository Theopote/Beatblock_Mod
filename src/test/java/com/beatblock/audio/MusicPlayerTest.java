package com.beatblock.audio;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicPlayerTest {

	@Test
	void loadAudioRejectsBlankPath() {
		MusicPlayer player = new MusicPlayer();

		assertFalse(player.loadAudio(null));
		assertFalse(player.loadAudio("  "));
		assertEquals("未提供音频路径", player.getLastLoadError());
		assertNull(player.getLoadedAudioPath());
	}

	@Test
	void tickAdvancesTimeWhenNoBackendLoaded() {
		MusicPlayer player = new MusicPlayer();
		player.setDurationSeconds(10);
		player.play();

		player.tick(1.5);

		assertEquals(1.5, player.getCurrentTimeSeconds(), 1e-6);
	}

	@Test
	void muteFlagIsTracked() {
		MusicPlayer player = new MusicPlayer();
		assertFalse(player.isMuted());
		player.setMuted(true);
		assertTrue(player.isMuted());
	}

	@Test
	void concurrentPlayPauseTickAndSeekStayConsistent() throws Exception {
		MusicPlayer player = new MusicPlayer();
		player.setDurationSeconds(30.0);
		int iterations = 1_500;
		CountDownLatch start = new CountDownLatch(1);
		AtomicReference<Throwable> failure = new AtomicReference<>();

		Runnable guard = () -> {
			try {
				start.await(5, TimeUnit.SECONDS);
				for (int i = 0; i < iterations; i++) {
					switch (i % 4) {
						case 0 -> player.play();
						case 1 -> player.pause();
						case 2 -> player.tick(0.02);
						default -> player.setCurrentTimeSeconds(i % 30);
					}
					double time = player.getCurrentTimeSeconds();
					if (time < 0 || time > 30.0) {
						throw new AssertionError("time out of bounds: " + time);
					}
				}
			} catch (Throwable t) {
				failure.compareAndSet(null, t);
			}
		};

		Thread t1 = new Thread(guard, "music-player-1");
		Thread t2 = new Thread(guard, "music-player-2");
		Thread t3 = new Thread(guard, "music-player-3");
		t1.start();
		t2.start();
		t3.start();
		start.countDown();
		t1.join(10_000);
		t2.join(10_000);
		t3.join(10_000);

		if (failure.get() != null) {
			throw new AssertionError("concurrent MusicPlayer access failed", failure.get());
		}
		assertTrue(player.getCurrentTimeSeconds() >= 0);
		assertTrue(player.getCurrentTimeSeconds() <= 30.0);
	}
}
