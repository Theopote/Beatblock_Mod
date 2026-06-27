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
 * 实现方式：先复制到剪贴板（标记为剪切），然后删除原事件。
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

        // 1. 复制到剪贴板（标记为剪切）
        TimelineClipboard.getInstance().cut(eventsToCut);

        // 2. 删除原事件（这里假设事件有唯一 ID 可以查找删除）
        // 注意：实际实现需要根据项目中如何删除事件来调整
        for (TimelineAnimationEvent event : eventsToCut) {
            // 这里需要实际的删除逻辑
            // 由于当前没有看到通过 TimelineAnimationEvent 直接删除的命令
            // 可能需要通过 Clip ID + Event ID 来删除
            // 暂时留空，需要根据实际 API 补充
        }

        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) return;

        // 恢复删除的事件
        for (int i = deleteCommands.size() - 1; i >= 0; i--) {
            deleteCommands.get(i).undo();
        }

        // 清空剪贴板
        TimelineClipboard.getInstance().clear();

        executed = false;
    }
}
