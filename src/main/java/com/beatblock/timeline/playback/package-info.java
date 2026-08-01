@NullMarked
/**
 * 播放会话与编译管线（Timeline Compiler 2.0）：
 * <ul>
 *   <li>{@link TimelineValidator} / Performance check — Play 前验收（Phase A）</li>
 *   <li>{@link TimelineCompiler} / {@link CompiledTimelineSnapshot} — 不可变演出程序
 *       （Phase B：stage + camera + build layers + audio + markers + validationReport）</li>
 *   <li>{@link PlaybackSession} — 时钟与音频协调</li>
 * </ul>
 */
package com.beatblock.timeline.playback;

import org.jspecify.annotations.NullMarked;
