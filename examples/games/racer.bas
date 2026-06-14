980 REM ### Racer game ###
990 REM ### Avoid the walls as the road twists AND turns ###
1000 CLS
1010 RAND
1500 REM ### Initialise constants ###
1510 LET term_width = 80 : IF PRINTW < 80 THEN LET term_width = PRINTW
1512 LET term_height = 25 : IF PRINTH < 25 THEN LET term_height = PRINTH
1514 LET offset_x = INT((PRINTW - term_width) / 2)
1516 LET offset_y = INT((PRINTH - term_height) / 2)
1520 LET road_width = 20 : LET road_max_left = term_width - road_width - 2
1530 LET road_inner = road_width - 1 : LET mid_width = INT(road_inner / 2)
1540 LET block_full$ = UCHR$(9608) : LET block_lefth$ = UCHR$(9612) : LET block_righth$ = UCHR$(9616)
1550 LET bytes_per_block = LEN(block_full$) : LET wall$ = "" : LET spaces$ = ""
1560 FOR col = 1 TO term_width : LET wall$ = wall$ + block_full$ : LET spaces$ = spaces$ + " " : NEXT col
1570 DIM road_hpos(term_height)
2000 REM ### Re-initialise variables for new game ###
2020 LET road_left = (term_width - road_width) / 2 : LET car_hpos = road_left + road_width / 2 - 1
2030 LET car_vpos = term_height - 5 : LET score = 0 : LET road_curve = 0
2040 FOR row = 1 TO term_height : LET road_hpos(row) = road_left : NEXT row
2050 REM ### Main loop ###
2060 LET k$ = INKEY$
2070 IF k$ = "a" OR k$ = "A" THEN LET car_hpos = car_hpos - 1
2080 IF k$ = "d" OR k$ = "D" THEN LET car_hpos = car_hpos + 1
2090 REM ### Update road ###
2100 LET chance = RND
2110 IF chance < 0.15 THEN LET road_curve = road_curve - 0.3
2120 IF chance >= 0.15 AND chance < 0.4 THEN LET road_curve = road_curve - 0.15
2130 IF chance >= 0.6 AND chance < 0.85 THEN LET road_curve = road_curve + 0.15
2140 IF chance >= 0.85 THEN LET road_curve = road_curve + 0.3
2150 IF road_curve < -0.95 THEN LET road_curve = -0.95
2160 IF road_curve > 0.95 THEN LET road_curve = 0.95
2170 LET road_left = road_left + road_curve
2180 IF road_left < 2 THEN LET road_curve = 0 : LET road_left = 2
2190 IF road_left > road_max_left THEN LET road_curve = 0 : LET road_left = road_max_left
2200 REM ### Shift history (scroll down - new road at top) ###
2210 FOR row = term_height TO 2 STEP -1 : LET road_hpos(row) = road_hpos(row-1) : NEXT row
2220 LET road_hpos(1) = road_left
2230 REM ### Move car up as score increases (less reaction time) ###
2240 LET car_vpos = term_height - 5 - INT(score / 500)
2250 IF car_vpos < 3 THEN LET car_vpos = 3
2260 REM ### Collision check ###
2270 LET road_hpos_now = road_hpos(car_vpos + 1)
2280 IF car_hpos < road_hpos_now OR (car_hpos + 3) > (road_hpos_now + road_width) THEN GOTO 2470
2290 REM ### Draw road (top row, 0, shows the score and instructions) ###
2300 FOR row = 1 TO term_height - 1
2310 LET left_idx = road_hpos(row+1) : LET int_left = INT(left_idx) : LET fraction = left_idx - int_left
2320 PRINT AT row + offset_y, offset_x; wall$(1 TO int_left * bytes_per_block);
2330 IF fraction < 0.5 THEN PRINT " ";
2340 IF fraction >= 0.5 THEN PRINT block_lefth$;
2350 LET dash$ = " " : IF (score + row) / 2 = INT((score + row) / 2) THEN LET dash$ = "|"
2360 PRINT spaces$(1 TO mid_width); dash$; spaces$(1 TO road_inner - mid_width - 1);
2370 IF fraction < 0.5 THEN PRINT block_full$;
2380 IF fraction >= 0.5 THEN PRINT block_righth$;
2390 PRINT wall$(1 TO (term_width - (int_left + road_inner + 2)) * bytes_per_block);
2400 NEXT row
2410 REM ### Draw car and score ###
2420 PRINT AT car_vpos + offset_y, car_hpos + offset_x; "|H|"; AT car_vpos + 1 + offset_y, car_hpos + offset_x; " ¯ ";
2430 PRINT AT offset_y, offset_x + 1; " Score: "; score; " "; AT offset_y, offset_x + term_width - 19; " Steer with A & D ";
2440 LET score = score + 5 + INT(ABS(road_curve) * 5)
2450 PAUSE car_vpos / 5
2460 GOTO 2050
2470 REM ### Game over ###
2480 PRINT AT term_height - 4 + offset_y, offset_x; "Crash! Final score: "; score; "          "
2490 LET msg$ = ""
2500 IF score < 500 THEN LET msg$ = "Did you forget your glasses?"
2510 IF score >= 500 AND score < 1500 THEN LET msg$ = "Not bad for a learner!"
2520 IF score >= 1500 AND score < 2500 THEN LET msg$ = "Getting the hang of it!"
2530 IF score >= 2500 AND score < 3500 THEN LET msg$ = "Vroom vroom! Pro driver!"
2540 IF score >= 3500 AND score < 5000 THEN LET msg$ = "Speed demon! Almost there!"
2545 IF score >= 5000 THEN LET msg$ = "Formula 1 champion!"
2550 PRINT AT term_height - 3 + offset_y, offset_x; msg$; "                                 "
2560 PRINT AT term_height - 2 + offset_y, offset_x; "Play again? (Y/N)                  ";
2570 LET k$ = INKEY$
2580 IF k$ = "Y" OR k$ = "y" THEN GOTO 2000
2590 IF k$ = "N" OR k$ = "n" THEN STOP
2600 GOTO 2570
