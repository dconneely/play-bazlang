package com.davidconneely.bazlang.play;

import com.davidconneely.bazlang.io.VoiceFrame;

/**
 * Up to 3 simultaneous {@link VoiceFrame}s, valid for {@code durationSeconds} before the caller
 * must ask {@link PlaySource#next} again. {@code finished} signals the whole tune has genuinely
 * ended (every channel halted or exhausted, none looping) - as opposed to just this frame's
 * duration elapsing, which happens at every note boundary regardless.
 *
 * @param a channel A's voice for this frame.
 * @param b channel B's voice for this frame.
 * @param c channel C's voice for this frame.
 * @param durationSeconds how long this frame lasts, in seconds.
 * @param finished whether the whole tune has genuinely ended.
 */
public record PlayFrame(
    VoiceFrame a, VoiceFrame b, VoiceFrame c, double durationSeconds, boolean finished) {}
