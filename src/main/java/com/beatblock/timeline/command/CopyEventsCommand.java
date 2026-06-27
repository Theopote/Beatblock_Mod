package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationEvent;
import com.beatblock.timeline.clipboard.TimelineClipboard;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 复制事件命令：将选中的事件复制到剪贴板。
 * <p>
 * 注意：复制操作本身不修改时间轴，因此不需要撤销。
 * 但为了与命令系统集成，仍实现 Command 接口。
 */
public final class CopyEventsCommand implements Command {

    private final List<TimelineAnimationEvent> eventsToCopy;

    public CopyEventsCommand(@NonNull List<TimelineAnimationEvent> events) {
        this.eventsToCopy = new ArrayList<>(events);
    }

    @Override
    public void execute() {
        TimelineClipboard.getInstance().copy(eventsToCopy);
    }

    @Override
    public void undo() {
        // 复制操作不需要撤销
    }
}
