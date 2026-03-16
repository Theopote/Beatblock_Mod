package com.beatblock.audio.scheduler;

import com.beatblock.audio.beatmap.AnchorType;
import com.beatblock.audio.beatmap.BeatEvent;
import com.beatblock.audio.beatmap.Beatmap;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * AnimationScheduler
 * ─────────────────────────────────────────────────────────────────────────────
 * 每 tick 从 BeatClock 读取当前音频时间，派发到期的 BeatEvent 给执行层。
 */
public final class AnimationScheduler {

	private static final long LOOKAHEAD_MS = 1000L;

	private long dispatchedUpToMs = -1L;
	private final Set<Long> lookaheadDispatched = new HashSet<>();
	private Beatmap beatmap;
	private final List<Consumer<ScheduledEvent>> listeners = new CopyOnWriteArrayList<>();

	public void load(Beatmap beatmap) {
		this.beatmap = beatmap;
		reset();
	}

	public void reset() {
		dispatchedUpToMs = -1L;
		lookaheadDispatched.clear();
	}

	public void tick(long nowMs) {
		if (beatmap == null || beatmap.beats.isEmpty()) return;
		dispatchDepartEvents(nowMs);
		dispatchArriveEventsLookahead(nowMs);
		if (nowMs > dispatchedUpToMs) {
			dispatchedUpToMs = nowMs;
		}
	}

	private void dispatchDepartEvents(long nowMs) {
		long from = dispatchedUpToMs + 1;
		if (from > nowMs) return;
		for (BeatEvent event : beatmap.beats) {
			if (event.anchor() != AnchorType.DEPART) continue;
			if (event.timeMs() < from) continue;
			if (event.timeMs() > nowMs) break;
			notify(new ScheduledEvent(event, event.timeMs(), 0L));
		}
	}

	private void dispatchArriveEventsLookahead(long nowMs) {
		long windowEnd = nowMs + LOOKAHEAD_MS;
		for (BeatEvent event : beatmap.beats) {
			if (event.anchor() != AnchorType.ARRIVE) continue;
			if (event.timeMs() < nowMs) continue;
			if (event.timeMs() > windowEnd) break;
			if (lookaheadDispatched.contains(event.timeMs())) continue;
			lookaheadDispatched.add(event.timeMs());
			long timeUntilLanding = event.timeMs() - nowMs;
			notify(new ScheduledEvent(event, nowMs, timeUntilLanding));
		}
	}

	private void notify(ScheduledEvent se) {
		for (Consumer<ScheduledEvent> listener : listeners) {
			try {
				listener.accept(se);
			} catch (Exception e) {
				System.err.println("[AnimationScheduler] 监听器异常: " + e.getMessage());
			}
		}
	}

	public void addListener(Consumer<ScheduledEvent> listener) {
		listeners.add(listener);
	}

	public void removeListener(Consumer<ScheduledEvent> listener) {
		listeners.remove(listener);
	}

	public record ScheduledEvent(
		BeatEvent event,
		long dispatchMs,
		long timeUntilAnchorMs
	) {
		public boolean needsLookahead() {
			return event.anchor() == AnchorType.ARRIVE && timeUntilAnchorMs > 0;
		}
	}
}

