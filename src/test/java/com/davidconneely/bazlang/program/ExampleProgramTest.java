package com.davidconneely.bazlang.program;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExampleProgramTest extends BaseProgramTest {

  @Test
  void test100Doors() {
    String source =
        """
        10 DIM D(100)
        20 FOR P=1 TO 100
        30 FOR I=P TO 100 STEP P
        40 IF D(I)=0 THEN GOTO 70
        50 LET D(I)=0
        60 GOTO 80
        70 LET D(I)=1
        80 NEXT I
        90 NEXT P
        100 PRINT "OPEN DOORS:"
        110 FOR I=1 TO 100
        120 IF D(I)=1 THEN PRINT I; " ";
        130 NEXT I
        140 PRINT
        """;
    String expected =
        "OPEN DOORS:"
            + System.lineSeparator()
            + "1 4 9 16 25 36 49 64 81 100 "
            + System.lineSeparator();
    String output = runProgramCapture(source);
    assertEquals(expected, output);
  }

  @Test
  void testCirclePlotter() {
    String source =
        """
        10 LET R=5
        20 LET CX=10
        30 LET CY=10
        40 FOR X=0 TO R
        50 LET Y=INT (SQR (R*R-X*X)+0.5)
        60 GOSUB 100
        70 NEXT X
        80 GOTO 200
        100 PLOT CX+X, CY+Y
        110 PLOT CX+X, CY-Y
        120 PLOT CX-X, CY+Y
        130 PLOT CX-X, CY-Y
        140 UNPLOT CX, CY
        150 RETURN
        200 PRINT "CIRCLE DRAWN"
        """;
    String expected = "████ ████ ████ ████ ████ ████ CIRCLE DRAWN" + System.lineSeparator();
    String output = runProgramCapture(source);
    assertEquals(expected, output);
  }

  @Test
  void testFibonacci() {
    String source =
        """
        10 LET A=0
        20 LET B=1
        30 PRINT A; " "; B; " ";
        40 FOR I=1 TO 10
        50 LET C=A+B
        60 PRINT C; " ";
        70 LET A=B
        80 LET B=C
        90 NEXT I
        100 PRINT
        """;
    String expected = "0 1 1 2 3 5 8 13 21 34 55 89 " + System.lineSeparator();
    String output = runProgramCapture(source);
    assertEquals(expected, output);
  }

  @Test
  void testMandelbrot() {
    // A small 16x16 Mandelbrot ASCII renderer to stress loop nesting and float math
    String source =
        """
        10 LET XMIN=-2
        20 LET XMAX=0.5
        30 LET YMIN=-1.25
        40 LET YMAX=1.25
        50 LET W=16
        60 LET H=16
        70 LET DX=(XMAX-XMIN)/W
        80 LET DY=(YMAX-YMIN)/H
        90 FOR Y=0 TO H
        100 LET CY=YMIN+Y*DY
        110 FOR X=0 TO W
        120 LET CX=XMIN+X*DX
        130 LET ZX=0
        140 LET ZY=0
        150 FOR I=1 TO 15
        160 LET X2=ZX*ZX
        170 LET Y2=ZY*ZY
        180 IF X2+Y2>4 THEN GOTO 240
        190 LET T=X2-Y2+CX
        200 LET ZY=2*ZX*ZY+CY
        210 LET ZX=T
        220 NEXT I
        230 PRINT "*";
        235 GOTO 250
        240 PRINT " ";
        250 NEXT X
        260 PRINT
        270 NEXT Y
        """;
    String expected =
        "                 "
            + System.lineSeparator()
            + "                 "
            + System.lineSeparator()
            + "                 "
            + System.lineSeparator()
            + "            *    "
            + System.lineSeparator()
            + "         * ***   "
            + System.lineSeparator()
            + "         ******  "
            + System.lineSeparator()
            + "     *   ******* "
            + System.lineSeparator()
            + "     *********** "
            + System.lineSeparator()
            + "***************  "
            + System.lineSeparator()
            + "     *********** "
            + System.lineSeparator()
            + "     *   ******* "
            + System.lineSeparator()
            + "         ******  "
            + System.lineSeparator()
            + "         * ***   "
            + System.lineSeparator()
            + "            *    "
            + System.lineSeparator()
            + "                 "
            + System.lineSeparator()
            + "                 "
            + System.lineSeparator()
            + "                 "
            + System.lineSeparator();
    String output = runProgramCapture(source);
    assertEquals(expected, output);
  }

  @Test
  void testStringArrayBubbleSort() {
    String source =
        """
        10 DIM A$(5, 10)
        20 LET A$(1)="ZIGGY"
        30 LET A$(2)="DAVID"
        40 LET A$(3)="ALBERT"
        50 LET A$(4)="YORICK"
        60 LET A$(5)="CHARLIE"
        70 FOR I=1 TO 4
        80 FOR J=1 TO 5-I
        90 IF A$(J)<=A$(J+1) THEN GOTO 130
        100 LET T$=A$(J)
        110 LET A$(J)=A$(J+1)
        120 LET A$(J+1)=T$
        130 NEXT J
        140 NEXT I
        150 PRINT "SORTED NAMES:"
        160 FOR I=1 TO 5
        170 PRINT A$(I)
        180 NEXT I
        """;
    String expected =
        "SORTED NAMES:"
            + System.lineSeparator()
            + "ALBERT    "
            + System.lineSeparator()
            + "CHARLIE   "
            + System.lineSeparator()
            + "DAVID     "
            + System.lineSeparator()
            + "YORICK    "
            + System.lineSeparator()
            + "ZIGGY     "
            + System.lineSeparator();
    String output = runProgramCapture(source);
    assertEquals(expected, output);
  }
}
