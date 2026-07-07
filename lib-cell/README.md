# Cell buffer library (`lib-cell`)

`lib-cell` is a lightweight, pure-Java library that provides a high-performance grid buffer for
character-cell based displays, supporting advanced styling, true color (24-bit RGB), and sub-character
pixel mapping.

This library is used by the BazLang interpreter to implement screen rendering and pixel drawing
(`PLOT`, `DRAW`), but it has no dependencies on the BazLang runtime and is fully reusable for other
terminal user interfaces (TUIs).

## Core features

- **Structure-of-arrays layout:** For maximum cache efficiency and rendering speed, the cell grid
  data (codepoints and style/colour attributes) is stored in a Structure-of-Arrays (SoA) layout.
- **Packed attributes:** Foreground colors, background colors, and formatting styles (bold,
  blink, italic, strikethrough, etc.) are packed into a single 64-bit `long` per cell.
- **Pluggable graphics modes (`PixelMode`):** Provides sub-character rendering by mapping multiple
  virtual "pixels" onto single Unicode block/Braille characters:
  - `1x1` (Cell mode - space or full block █)
  - `1x2` (Half-block mode - upper ▀ / lower ▄)
  - `2x2` (Quadrant mode - four sub-pixels per cell)
  - `2x3` (Sextant mode - six sub-pixels per cell)
  - `2x4` (Braille pattern mode - eight sub-pixels per cell using Unicode Braille Patterns)
 - **Logical plot space:** Automatically scales coordinates between character cell space (rows and
   columns) and virtual pixel space depending on the active `PixelMode`.
