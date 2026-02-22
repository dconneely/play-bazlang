# Maze Escape
# Navigate through a 5x5 maze to reach the exit
10 PRINT "MAZE ESCAPE"
20 PRINT "==========="
30 PRINT
40 DIM M(25)
50 GOSUB 1000
60 LET X = 1
70 LET Y = 1
80 LET MOVES = 0
90 REM Main game loop
100 GOSUB 500
110 IF X = 5 AND Y = 5 THEN GOTO 400
120 PRINT "Direction (N/S/E/W/Q)? ";
130 INPUT D$
140 IF D$ = "Q" OR D$ = "q" THEN GOTO 450
150 LET MOVES = MOVES + 1
160 LET NX = X
170 LET NY = Y
180 IF D$ = "N" OR D$ = "n" THEN LET NY = Y - 1
190 IF D$ = "S" OR D$ = "s" THEN LET NY = Y + 1
200 IF D$ = "E" OR D$ = "e" THEN LET NX = X + 1
210 IF D$ = "W" OR D$ = "w" THEN LET NX = X - 1
220 IF NX < 1 OR NX > 5 THEN GOTO 300
230 IF NY < 1 OR NY > 5 THEN GOTO 300
240 LET P = (NY - 1) * 5 + NX
250 IF M(P) = 1 THEN GOTO 300
260 LET X = NX
270 LET Y = NY
280 GOTO 100
300 PRINT "Wall! Can't go that way."
310 PRINT
320 GOTO 100
400 PRINT
410 PRINT "*** CONGRATULATIONS! ***"
420 PRINT "You escaped in "; MOVES; " moves!"
430 GOTO 460
450 PRINT "Giving up already?"
460 PRINT
470 PRINT "Play again (Y/N)? ";
480 INPUT A$
490 IF A$ = "Y" OR A$ = "y" THEN GOTO 50
495 PRINT "Thanks for playing!"
496 STOP
500 REM Display maze and position
510 PRINT
520 FOR R = 1 TO 5
530   FOR C = 1 TO 5
540     LET P = (R - 1) * 5 + C
545     LET CH = 0
550     IF R = Y AND C = X THEN LET CH = 1
560     IF R = Y AND C = X THEN PRINT "@";
565     IF CH = 0 THEN IF M(P) = 1 THEN PRINT "#";
570     IF CH = 0 THEN IF M(P) = 0 THEN PRINT ".";
580     PRINT " ";
590   NEXT C
600   PRINT
610 NEXT R
620 PRINT
630 PRINT "Position: ("; X; ","; Y; ")"
640 PRINT "Moves: "; MOVES
650 PRINT "Goal: (5,5)"
660 RETURN
1000 REM Initialise maze (0=path, 1=wall)
1010 FOR I = 1 TO 25
1020   LET M(I) = 0
1030 NEXT I
1040 REM Add walls
1050 LET M(2) = 1
1070 LET M(7) = 1
1080 LET M(9) = 1
1100 LET M(14) = 1
1110 LET M(16) = 1
1120 LET M(17) = 1
1130 LET M(19) = 1
1150 LET M(24) = 1
1160 RETURN
