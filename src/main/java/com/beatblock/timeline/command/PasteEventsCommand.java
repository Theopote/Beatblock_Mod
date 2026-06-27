package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.clipboard.TimelineClipboard;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 粘贴事件命令：从剪贴板粘贴事件到指定时间点。
 */
public final class PasteEventsCommand implements Command {

    private final Timeline timeline;
    private final String trackId;
    private final double targetTimeSeconds;
    private final List<Command> addCommands = new ArrayList<>();
    private boolean executed = false;

    public PasteEventsCommand(
        @NonNull Timeline timeline,
        @NonNull String trackId,
        double targetTimeSeconds
    ) {
        this.timeline = timeline;
        this.trackId = trackId;
        this.targetTimeSeconds = targetTimeSeconds;
    }

    @Override
    public void execute() {
        if (executed) return;

        TimelineClipboard clipboard = TimelineClipboard.getInstance();
        if (!clipboard.hasContent()) return;

        List<TimelineAnimationEvent> pastedEvents = clipboard.paste(targetTimeSeconds);

        for (TimelineAnimationEvent event : pastedEvents) {
            AddTimelineAnimationEventCommand addCmd = new AddTimelineAnimationEventCommand(
                timeline,
                trackId,
                event
            );
            addCmd.execute();
            addCommands.add(addCmd);
        }

        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) return;

        // 逆序撤销
        for (int i = addCommands.size() - 1; i >= 0; i--) {
            addCommands.get(i).undo();
        }

        executed = false;
    }
}
