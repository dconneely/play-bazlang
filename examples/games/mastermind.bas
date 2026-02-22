# Mastermind
# Guess the secret 4-digit code (digits 1-6)
# Black pegs = correct digit in correct position
# White pegs = correct digit in wrong position
5 RAND 0
10 PRINT "MASTERMIND"
20 PRINT "=========="
30 PRINT
40 PRINT "Crack the secret code!"
50 PRINT "4 digits, each from 1-6"
60 PRINT "Duplicates allowed"
70 PRINT
80 PRINT "Black peg = right digit, right place"
90 PRINT "White peg = right digit, wrong place"
100 PRINT
110 REM Generate secret code
120 DIM S(4)
130 DIM G(4)
140 DIM U(4)
150 DIM V(4)
160 FOR I = 1 TO 4
170   LET S(I) = INT(RND * 6) + 1
180 NEXT I
190 LET TURN = 1
200 REM Main game loop
210 IF TURN > 10 THEN GOTO 600
220 PRINT "Turn "; TURN; " of 10"
230 PRINT "Enter 4 digits (1-6): ";
240 INPUT G$
250 IF LEN G$ <> 4 THEN GOTO 220
260 REM Parse guess
270 FOR I = 1 TO 4
280   LET G(I) = VAL(G$(I))
290   IF G(I) < 1 OR G(I) > 6 THEN GOTO 220
300 NEXT I
310 REM Calculate key pegs
320 LET BLACK = 0
330 LET WHITE = 0
340 REM Mark used positions
350 FOR I = 1 TO 4
360   LET U(I) = 0
370   LET V(I) = 0
380 NEXT I
390 REM Count black pegs (exact matches)
400 FOR I = 1 TO 4
410   IF G(I) <> S(I) THEN GOTO 450
420   LET BLACK = BLACK + 1
430   LET U(I) = 1
440   LET V(I) = 1
450 NEXT I
460 REM Count white pegs (wrong position)
470 FOR I = 1 TO 4
480   IF U(I) = 1 THEN GOTO 540
490   FOR J = 1 TO 4
500     IF V(J) = 1 THEN GOTO 530
510     IF G(I) <> S(J) THEN GOTO 530
520     LET WHITE = WHITE + 1
525     LET V(J) = 1
526     GOTO 540
530   NEXT J
540 NEXT I
550 REM Display result
560 PRINT "Guess: "; G$
570 PRINT "Black pegs: "; BLACK; "  White pegs: "; WHITE
580 IF BLACK = 4 THEN GOTO 650
590 LET TURN = TURN + 1
595 PRINT
596 GOTO 210
600 REM Game over - failed
610 PRINT
620 PRINT "Game Over! You've used all 10 turns."
630 PRINT "The code was: "; S(1); S(2); S(3); S(4)
640 GOTO 700
650 REM Game over - won
660 PRINT
670 PRINT "*** CONGRATULATIONS! ***"
680 PRINT "You cracked the code in "; TURN; " turns!"
690 PRINT "The code was: "; S(1); S(2); S(3); S(4)
700 PRINT
710 PRINT "Play again (Y/N)? ";
720 INPUT A$
730 IF A$ = "Y" OR A$ = "y" THEN GOTO 160
740 PRINT "Thanks for playing!"