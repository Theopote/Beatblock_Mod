package com.beatblock.client;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerMutationBarrierTest {

	@Test
	void awaitAllWaitsForTrackedFutures() {
		AtomicInteger done = new AtomicInteger();
		CompletableFuture<Void> slow = CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			done.incrementAndGet();
		});
		BeatBlockAuthoritativeWorldMutator.ServerMutationBarrier barrier =
			new BeatBlockAuthoritativeWorldMutator.ServerMutationBarrier();
		barrier.track(slow);
		barrier.awaitAll(Duration.ofSeconds(2));
		assertEquals(1, done.get());
		assertTrue(slow.isDone());
	}

	@Test
	void awaitFutureNoopsWhenAlreadyDone() {
		CompletableFuture<Void> done = CompletableFuture.completedFuture(null);
		long start = System.nanoTime();
		BeatBlockAuthoritativeWorldMutator.awaitFuture(done, Duration.ofSeconds(1), "test");
		assertTrue((System.nanoTime() - start) < 500_000_000L);
	}
}
