# Hammurabi - Resource Management Game
# Based on the 1968 classic by Doug Dyment
10 PRINT "HAMMURABI"
20 PRINT "========="
30 PRINT
40 PRINT "Rule ancient Sumeria for 10 years"
50 PRINT "Manage land, grain, and population"
60 PRINT
70 REM Initialize game state
80 LET Y = 1
90 LET P = 100
100 LET A = 1000
110 LET G = 3000
120 REM Deaths this year
121 LET D = 0
130 REM Total deaths
131 LET TD = 0
140 REM Immigrants this year
141 LET I = 5
150 REM Population at start of game
151 LET PS = P
160 REM Main game loop
170 IF Y > 10 THEN GOTO 1000
180 PRINT
190 PRINT "Year "; Y; " of your rule"
200 PRINT "=========================="
210 PRINT
220 REM Calculate land price
230 LET LP = INT(RND * 10) + 17
240 REM Report status
250 PRINT "Population: "; P
260 PRINT "Acres owned: "; A
270 PRINT "Bushels in store: "; G
280 PRINT "People starved last year: "; D
290 PRINT "People immigrated last year: "; I
300 PRINT
310 PRINT "Land value: "; LP; " bushels/acre"
320 PRINT
330 REM Buy or sell land
340 PRINT "How many acres to BUY (- to SELL)? ";
350 INPUT B
360 IF B > 0 AND B * LP > G THEN GOTO 800
370 IF B < 0 AND ABS(B) > A THEN GOTO 810
380 LET A = A + B
390 LET G = G - B * LP
400 REM Feed people
410 PRINT "How many bushels to FEED people? ";
420 INPUT F
430 IF F > G THEN GOTO 820
440 LET G = G - F
450 REM Plant crops
460 PRINT "How many acres to PLANT? ";
470 INPUT PL
480 IF PL > A THEN GOTO 830
490 IF PL > G * 2 THEN GOTO 840
500 IF PL > P * 10 THEN GOTO 850
510 LET G = G - INT(PL / 2)
520 REM Calculate harvest
530 LET YLD = INT(RND * 5) + 1
540 LET HRV = PL * YLD
550 LET G = G + HRV
560 PRINT "Harvest: "; YLD; " bushels/acre"
570 REM Calculate rat damage
580 LET RD = INT(RND * 5) + 1
590 LET RL = 0
600 IF RD <> 1 THEN GOTO 630
610 LET RL = INT(G * (RND * 0.3))
620 LET G = G - RL
630 IF RL > 0 THEN PRINT "Rats ate "; RL; " bushels!"
640 REM Calculate deaths from starvation
650 LET D = P - INT(F / 20)
660 IF D < 0 THEN LET D = 0
670 IF D > P * 0.45 THEN GOTO 900
680 LET TD = TD + D
690 LET P = P - D
700 REM Calculate immigration
710 LET I = INT(D / 2 + (5 - YLD) * G / 600 + 1)
720 IF I < 0 THEN LET I = 0
730 LET P = P + I
740 LET Y = Y + 1
750 GOTO 170
800 PRINT "You don't have enough grain!"
805 GOTO 340
810 PRINT "You don't have that much land!"
815 GOTO 340
820 PRINT "You don't have that much grain!"
825 GOTO 410
830 PRINT "You don't have that much land!"
835 GOTO 460
840 PRINT "You need 0.5 bushels per acre to plant!"
845 GOTO 460
850 PRINT "Each person can only plant 10 acres!"
855 GOTO 460
900 REM Impeached for poor performance
910 REM Count the deaths that triggered it
911 LET TD = TD + D
920 PRINT
930 PRINT "*** IMPEACHED ***"
940 PRINT "Over 45% of your people starved!"
950 PRINT "You have been thrown out of office!"
960 GOTO 1090
1000 REM End of game
1010 PRINT
1020 PRINT "=========================="
1030 PRINT "End of 10-Year Rule"
1040 PRINT "=========================="
1090 REM Final Statistics Calculation
1091 IF P < 1 THEN LET P = 1
1092 LET APD = A / P
1093 LET DPC = (TD * 100) / PS
1100 PRINT
1110 PRINT "Final population: "; P
1120 PRINT "Acres per person: "; APD
1130 PRINT "Total death rate: "; DPC; "%"
1140 PRINT
1150 IF DPC > 33 THEN GOTO 1210
1160 IF DPC > 10 THEN GOTO 1240
1170 IF APD < 9 THEN GOTO 1270
1180 PRINT "*** EXCELLENT ***"
1190 PRINT "You are a great ruler!"
1200 GOTO 1290
1210 PRINT "*** POOR ***"
1220 PRINT "Your people suffered greatly."
1230 GOTO 1290
1240 PRINT "*** FAIR ***"
1250 PRINT "An adequate performance."
1260 GOTO 1290
1270 PRINT "*** GOOD ***"
1280 PRINT "A respectable reign."
1290 PRINT
1300 PRINT "Play again (Y/N)? ";
1310 INPUT R$
1320 IF R$ = "Y" OR R$ = "y" THEN GOTO 10
1330 PRINT "Thanks for playing!"
