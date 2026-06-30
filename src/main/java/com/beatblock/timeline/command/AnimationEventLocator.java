package com.beatblock.timeline.command;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.Timeline;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * 在 trackId 下按 eventId 定位真正存储数据的可变 {@link TimelineEvent}。
 * <p>
 * {@link com.beatblock.timeline.TimelineAnimationEvent} 只是
 * {@code Timeline.getAnimationEvents(trackId)} 每次调用时临时构造出来的只读快照
 * （见 {@code Timeline.rebuildAnimationCache}），它本身不能"写回去"——真正持久存储的是
 * {@code Clip} 里的 {@link TimelineEvent}，两者通过 {@code eventId == TimelineEvent.getId()}
 * 一一对应。任何想要"修改一个已存在事件的属性"的命令（批量编辑、剪切等），都必须先用本类
 * 定位到对应的 {@link TimelineEvent}，在它身上调用 {@code setParameters(...)}，
 * 再调用 {@link Timeline#markAnimationEventsDirty(String)} 让只读缓存重新构建，
 * 而不是试图修改 {@code TimelineAnimationEvent} 本身（它的字段全部是 final，没有 setter）。
 */
public final class AnimationEventLocator {

	/** 定位结果：事件所在的 clipId，连同那个真正可写的 {@link TimelineEvent} 引用。 */
	public record Located(@NonNull String clipId, @NonNull TimelineEvent event) {}

	private AnimationEventLocator() {}

	/** 在指定轨道下查找 eventId 对应的真实事件；找不到返回 null。 */
	public static @Nullable Located locate(@Nullable Timeline timeline, @Nullable String trackId, @Nullable String eventId) {
		if (timeline == null || trackId == null || eventId == null || eventId.isEmpty()) return null;
		Track track = timeline.getTrack(trackId);
		if (track == null) return null;
		for (Clip clip : track.getClips()) {
			TimelineEvent event = clip.getEvent(eventId);
			if (event != null) {
				return new Located(clip.getId(), event);
			}
		}
		return null;
	}

	/**
	 * 用给定的参数 map 整体替换该事件的参数（调用方负责传入完整的新参数集合，
	 * 通常是 {@code AnimationEventParams.toParameterMap()} 的结果），然后标记该轨道缓存失效。
	 */
	public static void applyParameters(
		@NonNull Timeline timeline,
		@NonNull String trackId,
		@NonNull TimelineEvent event,
		@NonNull Map<String, Object> newParameters
	) {
		event.setParameters(newParameters);
		timeline.markAnimationEventsDirty(trackId);
	}
}
