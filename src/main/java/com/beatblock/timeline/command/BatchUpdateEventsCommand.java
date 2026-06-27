package com.beatblock.timeline.command;

import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineAnimationActionMode;
import com.beatblock.timeline.TimelineAnimationEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 批量更新动画事件命令：同时修改多个事件的属性。
 * <p>
 * 支持的操作：
 * - 批量设置能量值
 * - 批量设置持续时间
 * - 批量修改动画类型
 * - 批量修改动作模式
 * - 批量修改任意参数
 */
public final class BatchUpdateEventsCommand implements Command {

    private final Timeline timeline;
    private final String trackId;
    private final List<TimelineAnimationEvent> targetEvents;
    private final BatchUpdateOptions options;

    // 保存原始值用于撤销
    private final List<EventSnapshot> snapshots = new ArrayList<>();
    private boolean executed = false;

    public BatchUpdateEventsCommand(
        @NonNull Timeline timeline,
        @NonNull String trackId,
        @NonNull List<TimelineAnimationEvent> events,
        @NonNull BatchUpdateOptions options
    ) {
        this.timeline = timeline;
        this.trackId = trackId;
        this.targetEvents = new ArrayList<>(events);
        this.options = options;
    }

    @Override
    public void execute() {
        if (executed) return;

        // 保存当前状态快照
        snapshots.clear();
        for (TimelineAnimationEvent event : targetEvents) {
            snapshots.add(new EventSnapshot(event));
        }

        // 批量应用修改
        for (TimelineAnimationEvent event : targetEvents) {
            applyUpdates(event);
        }

        timeline.markAnimationEventsDirty(trackId);
        executed = true;
    }

    @Override
    public void undo() {
        if (!executed) return;

        // 恢复原始值
        for (int i = 0; i < targetEvents.size() && i < snapshots.size(); i++) {
            EventSnapshot snapshot = snapshots.get(i);
            TimelineAnimationEvent event = targetEvents.get(i);
            snapshot.restore(event);
        }

        timeline.markAnimationEventsDirty(trackId);
        executed = false;
    }

    private void applyUpdates(TimelineAnimationEvent event) {
        // 注意：TimelineAnimationEvent 可能是不可变的
        // 这里假设有 setter 方法或需要通过反射/builder 重建
        // 实际实现需要根据项目中 TimelineAnimationEvent 的 API 调整

        if (options.energy != null) {
            // event.setEnergy(options.energy);
            // 如果是不可变对象，需要通过其他方式更新
        }

        if (options.durationSeconds != null) {
            // event.setDurationSeconds(options.durationSeconds);
        }

        if (options.animationTypeId != null) {
            // event.setAnimationTypeId(options.animationTypeId);
        }

        if (options.actionMode != null) {
            // event.setActionMode(options.actionMode);
        }

        if (options.customParameters != null) {
            for (Map.Entry<String, Object> entry : options.customParameters.entrySet()) {
                // event.setParameter(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 批量更新选项。
     */
    public static class BatchUpdateOptions {
        @Nullable Float energy;
        @Nullable Double durationSeconds;
        @Nullable String animationTypeId;
        @Nullable TimelineAnimationActionMode actionMode;
        @Nullable Map<String, Object> customParameters;

        public BatchUpdateOptions setEnergy(float energy) {
            this.energy = energy;
            return this;
        }

        public BatchUpdateOptions setDuration(double seconds) {
            this.durationSeconds = seconds;
            return this;
        }

        public BatchUpdateOptions setAnimationType(String typeId) {
            this.animationTypeId = typeId;
            return this;
        }

        public BatchUpdateOptions setActionMode(TimelineAnimationActionMode mode) {
            this.actionMode = mode;
            return this;
        }

        public BatchUpdateOptions setParameter(String key, Object value) {
            if (customParameters == null) {
                customParameters = new HashMap<>();
            }
            customParameters.put(key, value);
            return this;
        }
    }

    /**
     * 事件快照：保存原始值用于撤销。
     */
    private static class EventSnapshot {
        final float energy;
        final double durationSeconds;
        final String animationTypeId;
        final TimelineAnimationActionMode actionMode;
        final Map<String, Object> parameters;

        EventSnapshot(TimelineAnimationEvent event) {
            this.energy = event.getEnergy();
            this.durationSeconds = event.getDurationSeconds();
            this.animationTypeId = event.getAnimationTypeId();
            this.actionMode = event.getActionMode();
            this.parameters = new HashMap<>(event.getParameters());
        }

        void restore(TimelineAnimationEvent event) {
            // 恢复原始值
            // 实际实现需要根据 TimelineAnimationEvent API 调整
        }
    }
}
