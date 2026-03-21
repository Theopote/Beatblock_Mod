package com.beatblock.audio.scheduler;

import com.beatblock.audio.beatmap.AnchorType;
import com.beatblock.audio.beatmap.BeatEvent;
import com.beatblock.audio.beatmap.Beatmap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * AnimationScheduler
 * ─────────────────────────────────────────────────────────────────────────────
 * 每 tick 从 BeatClock 读取当前音频时间，派发到期的 BeatEvent 给执行层。
 */
public final class AnimationScheduler {

	private static final FlightTimeEstimator DEFAULT_FLIGHT_ESTIMATOR = event -> {
		double energy = Math.max(0.0, Math.min(1.0, event.energy()));
		long baseMs = switch (event.band()) {
			case LOW -> 620L;
			case MID -> 520L;
			case HIGH -> 420L;
		};
		double energyScale = 1.15 - (energy * 0.30);
		return Math.max(120L, Math.round(baseMs * energyScale));
	};

	private Beatmap beatmap;
	private List<PreparedEvent> prepared = List.of();
	private int nextDispatchIndex;
	private long lastTickMs = -1L;
	private FlightTimeEstimator flightTimeEstimator = DEFAULT_FLIGHT_ESTIMATOR;
	private final List<Consumer<ScheduledEvent>> listeners = new CopyOnWriteArrayList<>();

	public void load(Beatmap beatmap) {
		this.beatmap = beatmap;
		this.prepared = prepareEvents(beatmap);
		reset();
	}

	public void setFlightTimeEstimator(FlightTimeEstimator flightTimeEstimator) {
		this.flightTimeEstimator = flightTimeEstimator != null
			? flightTimeEstimator
			: DEFAULT_FLIGHT_ESTIMATOR;
		if (beatmap != null) {
			this.prepared = prepareEvents(beatmap);
			reset();
		}
	}

	public void reset() {
		nextDispatchIndex = 0;
		lastTickMs = -1L;
	}

	public void tick(long nowMs) {
		if (prepared.isEmpty()) return;

		if (lastTickMs >= 0 && nowMs < lastTickMs) {
			nextDispatchIndex = lowerBoundDispatchIndex(nowMs);
		}

		while (nextDispatchIndex < prepared.size()) {
			PreparedEvent pe = prepared.get(nextDispatchIndex);
			if (pe.dispatchMs() > nowMs) break;
			notify(new ScheduledEvent(
				pe.event(),
				pe.dispatchMs(),
				Math.max(0L, pe.anchorMs() - nowMs)
			));
			nextDispatchIndex++;
		}

		lastTickMs = nowMs;
	}

	private List<PreparedEvent> prepareEvents(Beatmap beatmap) {
		if (beatmap == null || beatmap.beats.isEmpty()) return List.of();
		List<PreparedEvent> list = new ArrayList<>(beatmap.beats.size());
		for (BeatEvent event : beatmap.beats) {
			long anchorMs = event.timeMs();
			long dispatchMs = anchorMs;
			if (event.anchor() == AnchorType.ARRIVE) {
				long flightMs = Math.max(0L, flightTimeEstimator.estimateFlightMs(event));
				dispatchMs = Math.max(0L, anchorMs - flightMs);
			}
			list.add(new PreparedEvent(event, dispatchMs, anchorMs));
		}
		list.sort(Comparator
			.comparingLong(PreparedEvent::dispatchMs)
			.thenComparingLong(PreparedEvent::anchorMs)
			.thenComparingInt(pe -> pe.event().band().ordinal()));
		return List.copyOf(list);
	}

	private int lowerBoundDispatchIndex(long nowMs) {
		int lo = 0;
		int hi = prepared.size();
		while (lo < hi) {
			int mid = (lo + hi) >>> 1;
			if (prepared.get(mid).dispatchMs() < nowMs) {
				lo = mid + 1;
			} else {
				hi = mid;
			}
		}
		return lo;
	}

	private record PreparedEvent(
		BeatEvent event,
		long dispatchMs,
		long anchorMs
	) {}

	@FunctionalInterface
	public interface FlightTimeEstimator {
		long estimateFlightMs(BeatEvent event);
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
			return event.anchor() == AnchorType.ARRIVE;
		}
	}
}

