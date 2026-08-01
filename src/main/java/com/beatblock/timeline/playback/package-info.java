@NullMarked
/**
 * 播放会话与编译管线：
 * <ul>
 *   <li>{@link TimelineValidator} / Performance check — Play 前验收（Phase A）</li>
 *   <li>{@link TimelineCompiler} / {@link CompiledTimelineSnapshot} — 不可变演出快照</li>
 *   <li>{@link PlaybackSession} — 时钟与音频协调</li>
 * </ul>
 */
package com.beatblock.timeline.playback;

import org.jspecify.annotations.NullMarked;
