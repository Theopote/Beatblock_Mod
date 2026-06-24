package com.beatblock.timeline.generation;

import com.beatblock.BeatBlock;
import com.beatblock.engine.StageObject;
import com.beatblock.engine.StageObjectSystem;
import com.beatblock.timeline.Clip;
import com.beatblock.timeline.EventType;
import com.beatblock.timeline.ReferenceBeatResolver;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.TimelineEventOrigin;
import com.beatblock.timeline.Track;
import com.beatblock.timeline.TrackType;
import com.beatblock.timeline.command.AddTimelineAnimationEventCommand;
import com.beatblock.timeline.command.Command;
import com.beatblock.timeline.command.CommandManager;
import com.beatblock.timeline.command.CompositeCommand;
import com.beatblock.timeline.command.DeleteEventCommand;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 扫描 Timeline 上的 STEP 事件，烘焙为 N 个普通 BURST 事件（可 Undo）。
 */
public final class StepSequenceBaker {

	public record BakeResult(int stepEventsBaked, int burstEventsCreated, int stepEventsSkipped) {}

	private record StepEventRef(String trackId, String clipId, Clip clip, TimelineEvent event) {}

	private StepSequenceBaker() {}

	public static BakeResult bake(Timeline timeline, CommandManager commandManager, Vec3d runtimeCameraPosition) {
		return bake(timeline, commandManager, runtimeCameraPosition, stageObjectsFromContext());
	}

	public static BakeResult bake(
		Timeline timeline,
		CommandManager commandManager,
		Vec3d runtimeCameraPosition,
		StageObjectSystem stageObjects
	) {
		if (timeline == null) return new BakeResult(0, 0, 0);

		double[] beats = ReferenceBeatResolver.resolveBeatTimesSeconds(timeline);
		double bpm = timeline.getBpm() > 0 ? timeline.getBpm() : 120.0;

		List<Command> batch = new ArrayList<>();
		int stepEventsBaked = 0;
		int burstEventsCreated = 0;
		int stepEventsSkipped = 0;

		for (StepEventRef ref : collectStepEvents(timeline)) {
			TimelineAnimationEvent stepEvent = toAnimationEvent(ref);
			if (stepEvent == null || !StepBurstEventFactory.isStepDispatch(stepEvent.getParameters())) {
				stepEventsSkipped++;
				continue;
			}

			StageObject target = stageObjects != null
				? stageObjects.get(stepEvent.getTargetObjectId())
				: null;
			if (target == null || target.getBlocks().isEmpty()) {
				stepEventsSkipped++;
				continue;
			}

			List<TimelineAnimationEvent> burstEvents = StepBurstEventFactory.expand(
				stepEvent, target, beats, bpm, runtimeCameraPosition);
			if (burstEvents.isEmpty()) {
				stepEventsSkipped++;
				continue;
			}

			TimelineEventOrigin origin = stepEvent.getEventOrigin();
			batch.add(new DeleteEventCommand(timeline, ref.trackId(), ref.clipId(), ref.event()));
			for (TimelineAnimationEvent burst : burstEvents) {
				batch.add(new AddTimelineAnimationEventCommand(
					timeline,
					ref.trackId(),
					TimelineDraftWriter.withOrigin(burst, origin)
				));
			}
			stepEventsBaked++;
			burstEventsCreated += burstEvents.size();
		}

		if (batch.isEmpty()) {
			return new BakeResult(0, 0, stepEventsSkipped);
		}

		CompositeCommand composite = new CompositeCommand(batch);
		if (commandManager != null) {
			commandManager.execute(composite);
		} else {
			composite.execute();
		}
		timeline.sortAll();
		return new BakeResult(stepEventsBaked, burstEventsCreated, stepEventsSkipped);
	}

	private static StageObjectSystem stageObjectsFromContext() {
		var engine = BeatBlock.getContext().blockAnimationEngine();
		return engine != null ? engine.getStageObjectSystem() : null;
	}

	private static List<StepEventRef> collectStepEvents(Timeline timeline) {
		List<StepEventRef> refs = new ArrayList<>();
		for (Track track : timeline.getTracks()) {
			if (track.getType() != TrackType.ANIMATION) continue;
			for (Clip clip : track.getClips()) {
				for (TimelineEvent event : clip.getEvents()) {
					if (event == null || event.getType() != EventType.ANIMATION) continue;
					if (!StepBurstEventFactory.isStepDispatch(event.getParameters())) continue;
					refs.add(new StepEventRef(track.getId(), clip.getId(), clip, event));
				}
			}
		}
		return refs;
	}

	private static TimelineAnimationEvent toAnimationEvent(StepEventRef ref) {
		if (ref == null || ref.event() == null) return null;
		Map<String, Object> params = new HashMap<>(ref.event().getParameters());
		Object durObj = params.get("durationSeconds");
		double duration = durObj instanceof Number
			? ((Number) durObj).doubleValue()
			: Math.max(0.01, ref.clip().getEndTimeSeconds() - ref.clip().getStartTimeSeconds());
		String animId = String.valueOf(params.getOrDefault("animationType", "bounce"));
		String target = String.valueOf(params.getOrDefault("targetObject", ""));
		float energy = params.get("energy") instanceof Number
			? ((Number) params.get("energy")).floatValue()
			: 1f;
		return new TimelineAnimationEvent(
			ref.event().getId(),
			ref.event().getTimeSeconds(),
			duration,
			animId,
			target,
			energy,
			params
		);
	}
}
