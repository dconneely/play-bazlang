# Racer
# Avoid the walls as the road twists and turns
5 RAND 0
10 REM Racer Game Enhanced (Renumbered)
20 DIM P(24)
30 LET FULL$ = CHR$(9608)
40 LET L_EDGE$ = CHR$(9612)
50 LET R_EDGE$ = CHR$(9616)
60 LET WALL$ = ""
70 FOR K = 1 TO 40
80 LET WALL$ = WALL$ + FULL$
90 NEXT K
100 LET SP$ = "                                                                                "
110 LET L = 10
120 LET W = 12
130 FOR I = 1 TO 24
140 LET P(I) = L
150 NEXT I
160 LET C = L + W / 2 - 1
170 LET R_CAR = 15
180 LET S = 0
185 LET B = 0
190 REM Main Loop
200 LET K$ = INKEY$
210 IF K$ = "a" OR K$ = "A" THEN LET C = C - 1
220 IF K$ = "d" OR K$ = "D" THEN LET C = C + 1
230 REM Update Road
240 LET R = RND
242 IF R < 0.15 THEN LET B = B - 0.3
243 IF R >= 0.15 AND R < 0.4 THEN LET B = B - 0.15
244 IF R > 0.6 AND R <= 0.85 THEN LET B = B + 0.15
245 IF R > 0.85 THEN LET B = B + 0.3
246 IF B < -0.9 THEN LET B = -0.9
248 IF B > 0.9 THEN LET B = 0.9
250 LET L = L + B
252 IF L < 2 THEN LET B = 0
254 IF L < 2 THEN LET L = 2
256 IF L > 24 THEN LET B = 0
258 IF L > 24 THEN LET L = 24
270 REM Shift History
280 FOR I = 1 TO 23
290 LET P(I) = P(I+1)
300 NEXT I
310 LET P(24) = L
320 REM Collision Check
330 LET P_NOW = P(R_CAR + 1)
340 IF C < P_NOW OR (C + 3) > (P_NOW + W) THEN GOTO 560
350 REM Draw
360 FOR I = 0 TO 23
370 LET LI = P(I+1)
380 LET IL = INT(LI)
390 LET FR = LI - IL
400 PRINT AT I, 0; WALL$(1 TO IL);
410 IF FR < 0.5 THEN PRINT " ";
420 IF FR >= 0.5 THEN PRINT L_EDGE$;
430 LET RW = W - 1
440 LET DASH$ = " "
450 IF (S + I) / 2 = INT((S + I) / 2) THEN LET DASH$ = "|"
460 LET MW = INT(RW / 2)
470 PRINT SP$(1 TO MW); DASH$; SP$(1 TO RW - MW - 1);
480 IF FR < 0.5 THEN PRINT FULL$;
490 IF FR >= 0.5 THEN PRINT R_EDGE$;
500 PRINT WALL$(1 TO 40 - (IL + W));
510 NEXT I
520 PRINT AT R_CAR, C; " _ "; AT R_CAR + 1, C; "|H|";
525 PRINT AT 0, 1; " SCORE: "; S; " "; AT 0, 22; " STEER WITH A & D ";
530 LET S = S + 5 + INT(ABS(B) * 5)
540 PAUSE 0
550 GOTO 190
560 PRINT AT 20, 0; "CRASH! FINAL SCORE: "; S
570 IF S < 500 THEN PRINT "Did you forget your glasses?"
580 IF S >= 500 AND S < 1500 THEN PRINT "Not bad for a learner!"
585 IF S >= 1500 AND S < 2500 THEN PRINT "Getting the hang of it!"
590 IF S >= 2500 AND S < 3500 THEN PRINT "Vroom vroom! Professional driver!"
595 IF S >= 3500 AND S < 5000 THEN PRINT "Speed demon! Almost there!"
600 IF S >= 5000 THEN PRINT "Formula 1 Champion!"
610 STOP