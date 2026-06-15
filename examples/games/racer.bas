1000 REM ### Racer game ###
1010 REM ### Avoid the walls as the road twists AND turns ###
1020 CLS
1030 RANDOMIZE
1040 REM ### Initialise constants ###
1050 LET term_width = 80 : IF PRINTW < 80 THEN LET term_width = PRINTW
1060 LET term_height = 25 : IF PRINTH < 25 THEN LET term_height = PRINTH
1070 LET offset_x = INT ((PRINTW - term_width) / 2)
1080 LET offset_y = INT ((PRINTH - term_height) / 2)
1090 LET road_width = 20 : LET road_max_left = term_width - road_width - 2
1100 LET road_inner = road_width - 1 : LET mid_width = INT (road_inner / 2)
1110 LET block_full$ = UCHR$ (9608) : LET block_lefth$ = UCHR$ (9612) : LET block_righth$ = UCHR$ (9616)
1120 LET bytes_per_block = LEN (block_full$) : LET wall$ = "" : LET spaces$ = ""
1130 FOR col = 1 TO term_width : LET wall$ = wall$ + block_full$ : LET spaces$ = spaces$ + " " : NEXT col
1140 DIM road_hpos(term_height)
2000 REM ### Re-initialise variables for new game ###
2020 LET road_left = (term_width - road_width) / 2 : LET car_hpos = road_left + road_width / 2 - 1
2030 LET car_vpos = term_height - 5 : LET score = 0 : LET road_curve = 0
2040 FOR row = 1 TO term_height : LET road_hpos(row) = road_left : NEXT row
3000 REM ### Main loop ###
3010 LET k$ = UINKEY$
3020 IF k$ = "a" OR k$ = "A" OR k$ = "h" OR k$ = "H" OR k$ = CHR$ (27) + "[D" OR k$ = CHR$ (27) + "OD" THEN LET car_hpos = car_hpos - 1
3030 IF k$ = "d" OR k$ = "D" OR k$ = "l" OR k$ = "L" OR k$ = CHR$ (27) + "[C" OR k$ = CHR$ (27) + "OC" THEN LET car_hpos = car_hpos + 1
3040 REM ### Update road ###
3050 LET chance = RND
3060 IF chance < 0.15 THEN LET road_curve = road_curve - 0.3
3070 IF chance >= 0.15 AND chance < 0.4 THEN LET road_curve = road_curve - 0.15
3080 IF chance >= 0.6 AND chance < 0.85 THEN LET road_curve = road_curve + 0.15
3090 IF chance >= 0.85 THEN LET road_curve = road_curve + 0.3
3100 IF road_curve < -0.95 THEN LET road_curve = -0.95
3110 IF road_curve > 0.95 THEN LET road_curve = 0.95
3120 LET road_left = road_left + road_curve
3130 IF road_left < 2 THEN LET road_curve = 0 : LET road_left = 2
3140 IF road_left > road_max_left THEN LET road_curve = 0 : LET road_left = road_max_left
3150 REM ### Shift history (scroll down - new road at top) ###
3160 FOR row = term_height TO 2 STEP -1 : LET road_hpos(row) = road_hpos(row - 1) : NEXT row
3170 LET road_hpos(1) = road_left
3180 REM ### Move car up as score increases (less reaction time) ###
3190 LET car_vpos = term_height - 5 - INT (score / 500)
3200 IF car_vpos < 3 THEN LET car_vpos = 3
3210 REM ### Collision check ###
3220 LET road_hpos_now = road_hpos(car_vpos + 1)
3230 IF car_hpos < road_hpos_now OR (car_hpos + 3) > (road_hpos_now + road_width) THEN GO TO 4000
3240 REM ### Draw road (top row, 0, shows the score and instructions) ###
3250 FOR row = 1 TO term_height - 1
3260 LET left_idx = road_hpos(row + 1) : LET int_left = INT (left_idx) : LET fraction = left_idx - int_left
3265 LET c_val = INT(score / 10) + row : LET curb = 2 : IF c_val - 2 * INT(c_val / 2) = 0 THEN LET curb = 7
3270 PRINT AT row + offset_y, offset_x; INK 4; PAPER 0; wall$(1 TO int_left * bytes_per_block); 
3280 IF fraction < 0.5 THEN PRINT PAPER 0; " "; 
3290 IF fraction >= 0.5 THEN PRINT INK 4; PAPER 0; block_lefth$; 
3300 LET dash$ = " " : IF (score + row) / 2 = INT ((score + row) / 2) THEN LET dash$ = "|"
3310 PRINT PAPER curb; " "; PAPER 0; spaces$(1 TO mid_width - 1); INK 6; dash$; INK 4; PAPER 0; spaces$(1 TO road_inner - mid_width - 2); PAPER curb; " "; PAPER 0; 
3320 IF fraction < 0.5 THEN PRINT INK 4; PAPER 0; block_full$; 
3330 IF fraction >= 0.5 THEN PRINT INK 4; PAPER 0; block_righth$; 
3340 PRINT INK 4; PAPER 0; wall$(1 TO (term_width - (int_left + road_inner + 2)) * bytes_per_block); 
3350 NEXT row
3360 REM ### Draw car and score ###
3370 PRINT AT car_vpos + offset_y, car_hpos + offset_x; INK 5; PAPER 0; "|H|"; AT car_vpos + 1 + offset_y, car_hpos + offset_x; " ¯ "; 
3380 PRINT AT offset_y, offset_x + 1; INK 7; PAPER 0; " Score: "; score; " "; AT offset_y, offset_x + term_width - 19; " Use Arrows/AD/HL "; 
3385 PRINT INK 8; PAPER 8; 
3390 LET score = score + 5 + INT (ABS (road_curve) * 5)
3400 PAUSE car_vpos / 5
3410 GO TO 3000
4000 REM ### Game over ###
4010 PRINT AT term_height - 4 + offset_y, offset_x; "Crash! Final score: "; score; "          "
4020 LET msg$ = ""
4030 IF score < 500 THEN LET msg$ = "Did you forget your glasses?"
4040 IF score >= 500 AND score < 1500 THEN LET msg$ = "Not bad for a learner!"
4050 IF score >= 1500 AND score < 2500 THEN LET msg$ = "Getting the hang of it!"
4060 IF score >= 2500 AND score < 3500 THEN LET msg$ = "Vroom vroom! Pro driver!"
4070 IF score >= 3500 AND score < 5000 THEN LET msg$ = "Speed demon! Almost there!"
4080 IF score >= 5000 THEN LET msg$ = "Formula 1 champion!"
4090 PRINT AT term_height - 3 + offset_y, offset_x; msg$; "                                 "
4100 PRINT AT term_height - 2 + offset_y, offset_x; "Play again? (Y/N)                  "; 
4110 LET k$ = INKEY$
4120 IF k$ = "Y" OR k$ = "y" THEN GO TO 2000
4130 IF k$ = "N" OR k$ = "n" THEN STOP
4140 GO TO 4110
