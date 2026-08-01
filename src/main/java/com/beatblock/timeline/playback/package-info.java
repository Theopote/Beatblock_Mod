@NullMarked
/**
 * 播放会话与编译管线（Timeline Compiler 2.0）：
 * <ul>
 *   <li>{@link TimelineValidator} / Performance check — Play 前验收（Phase A）</li>
 *   <li>{@link TimelineCompiler} / {@link CompiledTimelineSnapshot} — 不可变演出程序
 *       （Phase B/C：stage + camera + build + audio + markers + global/VFX + report）</li>
 *   <li>{@link PlaybackEngine} — 正式播放只推进 compiled program（Phase C）</li>
 *   <li>{@link PlaybackSession} — 时钟与音频协调</li>
 * </ul>
 */
package com.beatblock.timeline.playback;

import org.jspecify.annotations.NullMarked;
