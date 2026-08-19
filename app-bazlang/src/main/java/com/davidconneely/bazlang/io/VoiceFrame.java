package com.davidconneely.bazlang.io;

/**
 * One AY-chip-style voice's audio parameters for the next slice of {@code PLAY}/{@code APLAY}
 * playback. Deliberately audio-domain-only vocabulary (Hz/amplitude/on-off flags) — nothing about
 * BASIC syntax (no "M value", no "W value") crosses this boundary, matching how {@code
 * VirtualSpeaker#beep} already takes a plain frequency/duration pair rather than a BASIC string.
 *
 * @param frequencyHz the tone frequency, ignored if {@code toneOn} is {@code false}
 * @param amplitude volume, 0.0 (silent) to 1.0 (maximum) — already resolved from either a plain
 *     volume level or an envelope-generator value by the caller
 * @param toneOn whether this voice's square-wave tone generator is enabled
 * @param noiseOn whether this voice mixes in the shared noise generator
 */
public record VoiceFrame(double frequencyHz, double amplitude, boolean toneOn, boolean noiseOn) {
  public static final VoiceFrame SILENT = new VoiceFrame(0, 0, false, false);
}
