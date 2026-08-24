package com.davidconneely.bazlang.play;

/**
 * The AY chip's chip-wide state: one mixer register, one envelope generator, and tempo - matches
 * real hardware's single-instance-per-chip registers exactly (confirmed via the ROM disassembly and
 * independently by the ZEsarUX/FUSE AY-3-8912 sources; see {@code localonly-BAZLANG-ROADMAP.md}),
 * not per-channel: an {@code M}/{@code W}/{@code X}/{@code T} command in any one channel string
 * affects all three channels from that point on.
 *
 * <p>{@code mixerMask} starts unset ({@code null}) rather than defaulting to some guessed
 * power-on-reset bit pattern (no source pins one): unset means "a channel with a note plays its
 * tone normally, no noise" - the natural behaviour for a {@code PLAY} string that never issues an
 * {@code M} command at all, which is the common case.
 */
final class SharedRegisters {
  private Integer mixerMask;
  private int envelopeShape;
  private int envelopePeriodTicks =
      -1; // -1 = unset -> a generous default period, see PlaySequencer
  private int tempoBpm = 120;

  Integer mixerMask() {
    return mixerMask;
  }

  void setMixerMask(int mask) {
    this.mixerMask = mask;
  }

  int envelopeShape() {
    return envelopeShape;
  }

  void setEnvelopeShape(int shape) {
    this.envelopeShape = shape;
  }

  int envelopePeriodTicks() {
    return envelopePeriodTicks;
  }

  void setEnvelopePeriodTicks(int ticks) {
    this.envelopePeriodTicks = ticks;
  }

  int tempoBpm() {
    return tempoBpm;
  }

  void setTempoBpm(int bpm) {
    this.tempoBpm = bpm;
  }
}
