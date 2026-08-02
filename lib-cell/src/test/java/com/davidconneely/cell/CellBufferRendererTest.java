package com.davidconneely.cell;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CellBufferRendererTest {

  @Test
  void testRenderContentRows() {
    CellBuffer buffer = new CellBuffer(2, 2, CellMode.INSTANCE);

    buffer.setCell(
        0,
        0,
        'A',
        CellAttributes.rgb(0xFF0000),
        CellAttributes.index(42),
        CellAttributes.STYLE_BOLD | CellAttributes.STYLE_UNDERLINE);
    buffer.setCell(0, 1, 'B', CellAttributes.COLOUR_DEFAULT, CellAttributes.COLOUR_DEFAULT, 0);

    CellBufferRenderer renderer = new CellBufferRenderer();
    StringBuilder out = new StringBuilder();
    renderer.renderContentRows(out, buffer, 1, 2);

    String output = out.toString();
    // \033[1;1H sets cursor to row 1, col 1
    // \033[1;4m sets bold and underline
    // \033[38;2;255;0;0m sets foreground RGB
    // \033[48;5;42m sets background index
    // Then 'A'
    // \033[0m sets default (because next cell is default)
    // Then 'B'
    // \033[K clears to end of line
    assertEquals("\033[1;1H\033[1;4;38;2;255;0;0;48;5;42mA\033[0mB\033[K", output);
  }
}
