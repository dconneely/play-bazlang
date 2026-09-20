package com.davidconneely.repl;

/**
 * A single foreground text colour, expressed independently of any particular terminal library. The
 * host application builds a palette of these (see {@code TerminalEngine}'s constructor) and {@link
 * LineTokenizer} spans reference one by name - {@code lib-repl} never assigns meaning to a colour
 * itself, only applies it.
 *
 * @param red red component, 0-255.
 * @param green green component, 0-255.
 * @param blue blue component, 0-255.
 */
public record TextStyle(int red, int green, int blue) {}
