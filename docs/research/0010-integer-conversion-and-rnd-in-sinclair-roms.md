# How do the ZX81 and ZX Spectrum ROMs convert numbers to integers, and how does `RND` work?

<!-- Confidence levels and what counts as research are in ../../DOC-MAP.md. -->

**Confidence:** high for the ZX Spectrum (read directly from an annotated ROM disassembly); medium
for the ZX81's `RND` (a disassembly and an independent description agree, but the ZX81 ROM itself
was not read line by line here).

## Finding

**Integer conversion rounds to nearest, halves up.** Wherever Spectrum BASIC needs a whole number -
line numbers for `GO TO`/`GO SUB`/`RUN`/`RESTORE`, array subscripts, `DIM` sizes, colours, `PLOT`
coordinates, `CHR$` codes - it converts through `FP-TO-BC` (0x2DA2) or `FP-TO-A` (0x2DD5, which
calls `FP-TO-BC`). `FP-TO-BC` computes `INT(x + 0.5)`, where `INT` is floor, so `1.5` gives `2`,
`2.5` gives `3` and `-1.5` gives `-1`. That is exactly Java's `Math.round(double)`, which BazLang
uses via `ExpressionEvaluator.toInt`. `FIND-INT1`/`FIND-INT2` (0x1E94/0x1E99) wrap these and raise
`B Integer out of range` on overflow (more than 255 or 65535) or on a negative result.

**`RND` is a Lehmer (multiplicative congruential) generator, not a shift register.** Each call sets
`SEED = ((SEED + 1) * 75) mod 65537 - 1` and returns `SEED / 65536`, a value in `[0, 1)`. `SEED` is
a 16-bit system variable at 23670 (0x5C76), so the sequence has period 65536 and every value is a
multiple of 1/65536. The ZX81 uses the same constants (75 and 65537).

**`RANDOMIZE n`** sets `SEED` to `n` directly (via `FIND-INT2`, so `n` is rounded and must be in
`0..65535`). `RANDOMIZE` or `RANDOMIZE 0` copies the low two bytes of `FRAMES` into `SEED` instead.
Consequently a fixed `RANDOMIZE n` reproduces the same `RND` sequence on every run.

BazLang currently uses `java.util.Random` for `RND` and a 64-bit seed for `RANDOMIZE` - listed as a
divergence in [language.md](../spec/language.md#divergences). Switching to the ROM's generator would
make seeded sequences match real hardware exactly, at the cost of the short period.

## Evidence

- Skoolkid's annotated Spectrum ROM disassembly, `FP-TO-BC` at 0x2DA2
  (<https://skoolkid.github.io/rom/asm/2DA2.html>): the calculator sequence is `stk_half`,
  `addition`, `int`, with the comment that it rounds the last value to the nearest integer.
- Same source, `FIND-INT1`/`FIND-INT2` at 0x1E94 (<https://skoolkid.github.io/rom/asm/1E94.html>):
  both call `FP-TO-A`/`FP-TO-BC`, jump to `REPORT-B` on carry (overflow), and return only for
  positive in-range values.
- Same source, `S-RND` at 0x25F8 (<https://skoolkid.github.io/rom/asm/25F8.html>): fetches `SEED`,
  computes `((SEED + 1) * 75) mod 65537 - 1`, stores it back, and divides by 65536 by decrementing
  the exponent.
- Same source, `RANDOMIZE` at 0x1E4F (<https://skoolkid.github.io/rom/asm/1E4F.html>): `FIND-INT2`,
  then `FRAMES` low bytes if the operand is zero, stored to `SEED` at 0x5C76.
- ZX81: _The Complete Timex TS1000 / Sinclair ZX81 ROM Disassembly_ (Logan and O'Hara) describes the
  same sequence with 75 and 65537; the comp.sys.sinclair thread "Fast RND generator" states the same
  recurrence.

## Dead ends

- The idea that the Sinclair `RND` is a linear feedback shift register. No ROM source supports it;
  every disassembly consulted shows the congruential recurrence above.

## Open questions

- The ZX80 (4K ROM) has integer-only BASIC with its own `RND n`; it was not checked here.
- Whether BazLang should adopt the ROM generator (and a 16-bit `SEED`) is a design decision, not
  settled by this note.
