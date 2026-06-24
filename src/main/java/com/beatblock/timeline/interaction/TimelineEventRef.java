package com.beatblock.timeline.interaction;

import com.beatblock.timeline.Clip;
import com.beatblock.timeline.TimelineEvent;
import com.beatblock.timeline.Track;

/** 时间线事件在轨道/片段中的位置引用。 */
public record TimelineEventRef(Track track, Clip clip, TimelineEvent event) {}
