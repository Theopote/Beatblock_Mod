package com.beatblock.timeline.clipboard;

import com.beatblock.timeline.TimelineAnimationEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 时间轴剪贴板：支持复制/剪切/粘贴事件。
 * <p>
 * 特性：
 * - 记录原始时间戳用于相对粘贴
 * - 支持多个事件同时复制
 * - 区分复制和剪切操作
 */
public final class TimelineClipboard {

    private static final TimelineClipboard INSTANCE = new TimelineClipboard();

    private List<TimelineAnimationEvent> copiedEvents = Collections.emptyList();
    private double referenceTimeSeconds = 0.0;
    private boolean isCut = false;

    private TimelineClipboard() {}

    public static TimelineClipboard getInstance() {
        return INSTANCE;
    }

    /**
     * 复制事件到剪贴板。
     *
     * @param events 要复制的事件列表
     */
    public void copy(List<TimelineAnimationEvent> events) {
        if (events == null || events.isEmpty()) {
            clear();
            return;
        }
        this.copiedEvents = new ArrayList<>(events);
        this.referenceTimeSeconds = findEarliestTime(events);
        this.isCut = false;
    }

    /**
     * 剪切事件到剪贴板（标记为剪切，实际删除由调用方处理）。
     *
     * @param events 要剪切的事件列表
     */
    public void cut(List<TimelineAnimationEvent> events) {
        if (events == null || events.isEmpty()) {
            clear();
            return;
        }
        this.copiedEvents = new ArrayList<>(events);
        this.referenceTimeSeconds = findEarliestTime(events);
        this.isCut = true;
    }

    /**
     * 粘贴事件，在指定时间点创建副本。
     *
     * @param targetTimeSeconds 粘贴的目标时间（最早事件的新时间）
     * @return 创建的事件副本列表
     */
    public List<TimelineAnimationEvent> paste(double targetTimeSeconds) {
        if (copiedEvents.isEmpty()) {
            return Collections.emptyList();
        }

        List<TimelineAnimationEvent> pasted = new ArrayList<>(copiedEvents.size());
        double timeOffset = targetTimeSeconds - referenceTimeSeconds;

        for (TimelineAnimationEvent original : copiedEvents) {
            TimelineAnimationEvent copy = new TimelineAnimationEvent(
                "",
                original.getTimeSeconds() + timeOffset,
                original.getDurationSeconds(),
                original.getAnimationTypeId(),
                original.getTargetObjectId(),
                original.getEnergy(),
                new java.util.HashMap<>(original.getParameters())
            );
            pasted.add(copy);
        }

        // 粘贴后如果是剪切操作，清空剪贴板（避免重复粘贴）
        if (isCut) {
            clear();
        }

        return pasted;
    }

    /**
     * 是否有内容可粘贴。
     */
    public boolean hasContent() {
        return !copiedEvents.isEmpty();
    }

    /**
     * 是否为剪切操作。
     */
    public boolean isCut() {
        return isCut;
    }

    /**
     * 获取剪贴板中的事件数量。
     */
    public int getEventCount() {
        return copiedEvents.size();
    }

    /**
     * 获取剪贴板中的事件列表（只读）。
     */
    public List<TimelineAnimationEvent> getEvents() {
        return Collections.unmodifiableList(copiedEvents);
    }

    /**
     * 清空剪贴板。
     */
    public void clear() {
        this.copiedEvents = Collections.emptyList();
        this.referenceTimeSeconds = 0.0;
        this.isCut = false;
    }

    /**
     * 找到事件列表中最早的时间。
     */
    private double findEarliestTime(List<TimelineAnimationEvent> events) {
        if (events.isEmpty()) {
            return 0.0;
        }
        double earliest = Double.MAX_VALUE;
        for (TimelineAnimationEvent event : events) {
            if (event.getTimeSeconds() < earliest) {
                earliest = event.getTimeSeconds();
            }
        }
        return earliest;
    }
}
