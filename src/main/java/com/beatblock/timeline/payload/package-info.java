/**
 * 舞台事件强类型载荷（StageEventPayload）及其 Map 编解码。
 * <p>
 * 第 2 层 Timeline 持久化仍用参数 Map；播放/引擎优先通过
 * {@link com.beatblock.timeline.TimelineAnimationEvent#getPayload()} 读取类型安全字段。
 */
package com.beatblock.timeline.payload;
