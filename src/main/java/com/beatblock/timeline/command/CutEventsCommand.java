package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.clipboard.TimelineClipboard;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 剪切事件命令：将选中的事件移动到剪贴板。
 * <p>
 * 实现方式：先复制到剪贴板（标记为剪切），然后删除原事件。删除复用
 * {@link DeleteEventCommand}；每个待剪切的 {@link TimelineAnimationEvent} 先用
 * {@link AnimationEventLocator} 定位到它真正所在的 clipId 和底层可写的
 * {@link com.beatblock.timeline.TimelineEvent}，再交给 {@code DeleteEventCommand} 执行。
 */
public final class CutEventsCommand implements Command {

	private final Timeline timeline;
	private final String trackId;
	private final List<TimelineAnimationEvent> eventsToCut;
	private final List<Command> deleteCommands = new ArrayList<>();
	private boolean executed = false;

	public CutEventsCommand(
		@NonNull Timeline timeline,
		@NonNull String trackId,
		@NonNull List<TimelineAnimationEvent> events
	) {
		this.timeline = timeline;
		this.trackId = trackId;
		this.eventsToCut = new ArrayList<>(events);
	}

	@Override
	public void execute() {
		if (executed) return;

		TimelineClipboard.getInstance().cut(eventsToCut);

		deleteCommands.clear();
		for (TimelineAnimationEvent event : eventsToCut) {
			AnimationEventLocator.Located located =
				AnimationEventLocator.locate(timeline, trackId, event.getEventId());
			if (located == null) continue;
			DeleteEventCommand deleteCmd = new DeleteEventCommand(
				timeline, trackId, located.clipId(), located.event());
			deleteCmd.execute();
			deleteCommands.add(deleteCmd);
		}
		timeline.markAnimationEventsDirty(trackId);

		executed = true;
	}

	@Override
	public void undo() {
		if (!executed) return;

		for (int i = deleteCommands.size() - 1; i >= 0; i--) {
			deleteCommands.get(i).undo();
		}
		timeline.markAnimationEventsDirty(trackId);

		TimelineClipboard.getInstance().clear();

		executed = false;
	}
}
