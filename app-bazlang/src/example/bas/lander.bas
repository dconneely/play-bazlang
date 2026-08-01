1000 REM ### Lunar Lander ###
1010 REM ### Retro graphical landing simulation under PLOTMODE 8 ###
1020 PLOTMODE 8
1030 INK -1 : PAPER -1
1040 LET w = PLOTW : LET h = PLOTH
1050 LET n = 8
1060 DIM tx(8) : DIM ty(8) : REM Terrain nodes (tx, ty) generated per level
1070 LET score = 0 : LET level = 1
2000 REM ### Game start / Reset lander ###
2010 IF level = 9 THEN GO TO 6500
2020 LET limit_level = level : IF limit_level > 8 THEN LET limit_level = 8
2030 REM ### Moon data: 8000 + limit_level * 10 -> 8010 (PHOBOS) .. 8080 (IO) ###
2040 RESTORE 8000 + limit_level * 10
2050 READ p$, d1$, d2$, s_ink, p_ink, s_bright, rg$
2060 LET px = w * 0.1
2070 LET py = h * 0.8
2080 LET vx = -0.01 + 0.04 * limit_level
2090 LET vy = 0
2100 LET fuel = 655 - 35 * limit_level
2110 LET theta = 1.5708
2120 LET g = 0.001 + 0.003 * limit_level
2130 LET thrust = 0.045
2140 LET max_vy = 2.23 - 0.18 * limit_level
2150 LET max_vx = 1.22 - 0.10 * limit_level
2160 LET max_ang = 22.5 - 2.0 * (limit_level - 1)
2170 REM ### Terrain: 7000 + limit_level * 10 -> 7010 (PHOBOS) .. 7080 (IO) ###
2180 RESTORE 7000 + limit_level * 10
2190 FOR i = 1 TO n
2200 READ px_pct, py_pct
2210 LET tx(i) = px_pct * w / 100
2220 LET ty(i) = py_pct * h / 100
2230 NEXT i
3000 REM ### Main game loop ###
3010 LET k$ = INKEY$
3020 LET thrusting = 0
3030 IF k$ = " " AND fuel > 0 THEN LET thrusting = 1
3040 IF thrusting = 0 THEN GO TO 3080
3050 LET vx = vx + thrust * COS (theta)
3060 LET vy = vy + thrust * SIN (theta)
3070 LET fuel = fuel - 2 : IF fuel <= 0 THEN LET fuel = 0 : LET thrusting = 0
3080 IF k$ = "o" OR k$ = "O" THEN LET theta = theta + 0.1
3090 IF k$ = "p" OR k$ = "P" THEN LET theta = theta - 0.1
3100 IF theta > 6.28318 THEN LET theta = theta - 6.28318
3110 IF theta < 0 THEN LET theta = theta + 6.28318
3120 REM ### Apply gravity and update position ###
3130 LET vy = vy - g
3140 LET px = px + vx : LET py = py + vy
3150 REM ### Boundary check ###
3160 IF px < 0 OR px > w OR py > h THEN GO TO 6000
3170 REM ### Landscape collision detection ###
3180 LET seg = 1
3190 FOR i = 1 TO n - 1
3200 IF px >= tx(i) AND px <= tx(i + 1) THEN LET seg = i
3210 NEXT i
3220 LET x_diff = tx(seg + 1) - tx(seg)
3230 LET y_diff = ty(seg + 1) - ty(seg)
3240 LET terrain_y = ty(seg) + (px - tx(seg)) * y_diff / x_diff
3250 IF py - 4 <= terrain_y THEN GO TO 5000
3260 REM ### Drawing phase ###
3270 FAST
3280 CLS
3290 REM ### Draw terrain ###
3300 BRIGHT s_bright
3310 FOR i = 1 TO n - 1
3320 INK s_ink : IF i = 4 THEN INK p_ink
3330 PLOT tx(i), ty(i) : DRAW tx(i + 1) - tx(i), ty(i + 1) - ty(i)
3340 NEXT i
3350 BRIGHT 1
3360 REM ### Draw landing pad text indicator ###
3370 PRINT AT TEXTH - 3, INT ((tx(4) + tx(5)) / 4) - 4; INK -1; "== PAD =="
3380 REM ### Draw Lander ###
3390 INK 5
3400 LET c_th = COS (theta) : LET s_th = SIN (theta)
3410 LET tip_x = px + 6 * c_th : LET tip_y = py + 6 * s_th
3420 LET left_x = px - 4 * c_th - 3 * s_th : LET left_y = py - 4 * s_th + 3 * c_th
3430 LET right_x = px - 4 * c_th + 3 * s_th : LET right_y = py - 4 * s_th - 3 * c_th
3440 PLOT left_x, left_y : DRAW tip_x - left_x, tip_y - left_y
3450 PLOT right_x, right_y : DRAW tip_x - right_x, tip_y - right_y
3460 INK 5 : IF thrusting = 1 THEN INK 6
3470 PLOT left_x, left_y : DRAW right_x - left_x, right_y - left_y
3480 REM ### Draw thrust flame if active ###
3490 IF thrusting = 0 THEN GO TO 3540
3510 LET base_x = px - 4 * c_th : LET base_y = py - 4 * s_th
3520 INK 2 : PLOT base_x, base_y : DRAW -6 * c_th, -6 * s_th
3530 INK 6 : PLOT base_x, base_y : DRAW -3 * c_th, -3 * s_th
3540 REM ### HUD ###
3550 LET t_deg = INT ((theta - 1.5708) * 57.3)
3560 IF t_deg < -180 THEN LET t_deg = t_deg + 360
3570 IF t_deg > 180 THEN LET t_deg = t_deg - 360
3580 LET hud_col = TEXTW - 26 : IF hud_col < 0 THEN LET hud_col = 0
3590 LET hud_w = 36
3600 PRINT AT 1, 2 + INT ((hud_w - 17 - LEN (p$)) / 2); INK 5; "-- MISSION TO "; p$; " --"
3610 PRINT AT 2, 2 + INT ((hud_w - 18) / 2); INK 5; "Level:"; INK 7; level; INK 5; "  Score:"; INK 7; score
3620 PRINT AT 3, 2 + INT ((hud_w - LEN (p$) - 2 - LEN (d1$)) / 2); INK 6; BRIGHT 1; p$; BRIGHT 0; INK 7; ": "; d1$
3630 PRINT AT 4, 2 + INT ((hud_w - LEN (d2$)) / 2); INK 7; d2$
3640 LET v_col = 4 : IF ABS (vy) > max_vy THEN LET v_col = 2
3650 PRINT AT 1, hud_col + 4; INK 5; "--- TELEMETRY ---"
3660 PRINT AT 2, hud_col; INK 5; "ALT: "; INK 7; INT ((py - 4 - terrain_y) * 10) / 10; AT 2, hud_col + 11; INK 5; "m"
3670 PRINT AT 3, hud_col; INK 5; "Vy:  "; INK v_col; INT (vy * 100) / 100; AT 3, hud_col + 11; INK 5; "ms⁻¹"; AT 3, hud_col + 16; "/ -"; INT (max_vy * 100) / 100
3680 LET x_col = 4 : IF ABS (vx) > max_vx THEN LET x_col = 2
3690 PRINT AT 4, hud_col; INK 5; "Vx:  "; INK x_col; INT (vx * 100) / 100; AT 4, hud_col + 11; INK 5; "ms⁻¹"; AT 4, hud_col + 16; "/  "; INT (max_vx * 100) / 100
3700 LET a_col = 4 : IF ABS (t_deg) > max_ang THEN LET a_col = 2
3710 PRINT AT 5, hud_col; INK 5; "ATT: "; INK a_col; t_deg; AT 5, hud_col + 11; INK 5; "°"; AT 5, hud_col + 16; "/  "; INT (max_ang * 10) / 10; "°"
3720 LET thr$ = "Idle" : LET t_col = 7 : IF thrusting = 1 THEN LET thr$ = "Active" : LET t_col = 4
3730 PRINT AT 6, hud_col; INK 5; "THR: "; INK t_col; thr$
3740 LET f_col = 4 : IF fuel <= 150 THEN LET f_col = 2
3750 PRINT AT 7, hud_col; INK 5; "FUEL:"; INK f_col; fuel; AT 7, hud_col + 11; INK 5; "u"
3760 PRINT AT 8, hud_col; INK 5; "G:   "; INK 7; rg$
3770 SLOW
3780 PAUSE 2
3790 GO TO 3000
5000 REM ### Landing / Crash logic ###
5010 SLOW
5020 CLS
5030 LET landing_ok = 1
5040 IF seg <> 4 THEN LET landing_ok = 0
5050 IF ABS (vy) > max_vy THEN LET landing_ok = 0
5060 IF ABS (vx) > max_vx THEN LET landing_ok = 0
5070 IF ABS (t_deg) > max_ang THEN LET landing_ok = 0
5080 IF fuel <= 0 THEN LET landing_ok = 0
5090 LET tbl_col = INT ((TEXTW - 60) / 2) : IF tbl_col < 0 THEN LET tbl_col = 0
5100 PRINT AT 1, tbl_col + 17; INK -1; "--- TOUCHDOWN TELEMETRY ---"
5110 PRINT AT 3, tbl_col; INK -1; "Parameter             | Value        | Limit        | Status"
5120 PRINT AT 4, tbl_col; INK -1; "----------------------+--------------+--------------+-------"
5130 PRINT AT 5, tbl_col; INK -1; "Altitude"; AT 5, tbl_col + 22; "| 0.0 m"; AT 5, tbl_col + 37; "| 0.0 m"; AT 5, tbl_col + 52; "| "; INK 4; "SAFE"
5140 LET stat$ = "SAFE" : LET c_ink = 4 : IF ABS (vy) > max_vy THEN LET stat$ = "CRASH" : LET c_ink = 2
5150 PRINT AT 6, tbl_col; INK -1; "Vertical Descent Speed"; AT 6, tbl_col + 22; "| "; INT (vy * 100) / 100; " ms⁻¹"; AT 6, tbl_col + 37; "| <= "; INT (max_vy * 100) / 100; " ms⁻¹"; AT 6, tbl_col + 52; "| "; INK c_ink; stat$
5160 LET stat$ = "SAFE" : LET c_ink = 4 : IF ABS (vx) > max_vx THEN LET stat$ = "CRASH" : LET c_ink = 2
5170 PRINT AT 7, tbl_col; INK -1; "Horizontal Slide Speed"; AT 7, tbl_col + 22; "| "; INT (vx * 100) / 100; " ms⁻¹"; AT 7, tbl_col + 37; "| <= "; INT (max_vx * 100) / 100; " ms⁻¹"; AT 7, tbl_col + 52; "| "; INK c_ink; stat$
5180 LET stat$ = "SAFE" : LET c_ink = 4 : IF ABS (t_deg) > max_ang THEN LET stat$ = "CRASH" : LET c_ink = 2
5190 PRINT AT 8, tbl_col; INK -1; "Attitude / Tilt Angle"; AT 8, tbl_col + 22; "| "; ABS (t_deg); " °"; AT 8, tbl_col + 37; "| <= "; INT (max_ang * 10) / 10; " °"; AT 8, tbl_col + 52; "| "; INK c_ink; stat$
5200 LET thr$ = "Idle" : IF thrusting = 1 THEN LET thr$ = "Active"
5210 PRINT AT 9, tbl_col; INK -1; "Engine Thrust"; AT 9, tbl_col + 22; "| "; thr$; AT 9, tbl_col + 37; "| (N/A)"; AT 9, tbl_col + 52; "| (N/A)"
5220 LET stat$ = "SAFE" : LET c_ink = 4 : IF fuel <= 0 THEN LET stat$ = "CRASH" : LET c_ink = 2
5230 PRINT AT 10, tbl_col; INK -1; "Remaining Fuel"; AT 10, tbl_col + 22; "| "; fuel; " u"; AT 10, tbl_col + 37; "| > 0 u"; AT 10, tbl_col + 52; "| "; INK c_ink; stat$
5240 PRINT AT 11, tbl_col; INK -1; "Local Gravity"; AT 11, tbl_col + 22; "| "; rg$; AT 11, tbl_col + 37; "| (N/A)"; AT 11, tbl_col + 52; "| (N/A)"
5250 LET stat$ = "SAFE" : LET c_ink = 4 : LET zone$ = "Pad" : IF seg <> 4 THEN LET stat$ = "CRASH" : LET c_ink = 2 : LET zone$ = "Off-pad"
5260 PRINT AT 12, tbl_col; INK -1; "Landing Zone"; AT 12, tbl_col + 22; "| "; zone$; AT 12, tbl_col + 37; "| Pad (Seg 4)"; AT 12, tbl_col + 52; "| "; INK c_ink; stat$
5270 PRINT AT 13, tbl_col; INK -1; "------------------------------------------------------------"
5280 IF landing_ok = 0 THEN GO TO 5340
5290 PRINT AT 15, tbl_col + 10; INK 4; "SUCCESSFUL LANDING! BONUS SCORE +100"
5300 LET score = score + 100 : LET level = level + 1
5310 LET np$ = "VICTORY" : IF level <= 8 THEN RESTORE 8000 + level * 10 : READ np$
5330 GO TO 5360
5340 PRINT AT 15, tbl_col + 10; INK 2; "CRASHED! MODULE EXPLODED! SCORE RESET!"
5350 LET score = (level - 1) * 100
5360 LET p_len = 33 + LEN (p$) : IF landing_ok = 1 THEN LET p_len = 32 + LEN (np$)
5370 LET p_col = tbl_col + INT ((60 - p_len) / 2) : IF p_col < 0 THEN LET p_col = 0
5380 IF landing_ok = 1 THEN PRINT AT 17, p_col; INK 7; "Proceed to the next moon "; INK 6; BRIGHT 1; np$; BRIGHT 0; INK 7; " (Y/N)?"
5390 IF landing_ok = 0 THEN PRINT AT 17, p_col; INK 7; "Attempt the "; INK 6; BRIGHT 1; p$; BRIGHT 0; INK 7; " landing again (Y/N)?"
5400 LET k$ = INKEY$
5410 IF k$ = "y" OR k$ = "Y" THEN GO TO 2000
5420 IF k$ = "n" OR k$ = "N" THEN GO TO 6600
5430 GO TO 5400
6000 REM ### Out of bounds crash ###
6010 SLOW
6020 CLS
6030 PRINT AT 5, 5; INK 2; "MODULE LOST IN DEEP SPACE!"
6040 PRINT AT 7, 5; INK -1; "Score Reset!"
6050 LET score = (level - 1) * 100 : LET landing_ok = 0
6060 GO TO 5360
6500 REM ### Grand Campaign Complete ###
6510 SLOW
6520 CLS
6530 PRINT AT 4, 5; INK 4; "GRAND CONQUEST COMPLETE!"
6540 PRINT AT 6, 5; INK -1; "Congratulations, Commander!"
6550 PRINT AT 8, 5; INK -1; "You have successfully landed on"
6560 PRINT AT 9, 5; INK -1; "all 8 major moons of our solar system!"
6570 PRINT AT 11, 5; INK -1; "Final Score: "; score
6580 PRINT AT 13, 5; INK -1; "Thank you for playing!"
6590 STOP
6600 REM ### Exit Game ###
6610 SLOW
6620 CLS
6630 LET ex_col = INT ((TEXTW - 22) / 2) : IF ex_col < 0 THEN LET ex_col = 0
6640 PRINT AT 10, ex_col; INK -1; "Thank you for playing!"
6650 STOP
7000 REM ### Moon Terrain DATA: 8 terrain points (x%, y%) per moon ###
7010 DATA 0, 5, 20, 25, 35, 15, 50, 10, 70, 10, 80, 20, 90, 15, 100, 5
7020 DATA 0, 5, 10, 15, 20, 18, 40, 10, 58, 10, 70, 15, 85, 20, 100, 5
7030 DATA 0, 15, 15, 5, 30, 20, 42, 10, 58, 10, 75, 25, 88, 10, 100, 15
7040 DATA 0, 5, 15, 20, 30, 15, 38, 10, 52, 10, 70, 25, 85, 15, 100, 5
7050 DATA 0, 5, 15, 25, 30, 15, 44, 10, 56, 10, 75, 30, 88, 15, 100, 5
7060 DATA 0, 5, 20, 15, 35, 25, 48, 10, 58, 10, 78, 20, 88, 15, 100, 5
7070 DATA 0, 10, 15, 25, 35, 15, 56, 10, 64, 10, 85, 20, 92, 15, 100, 5
7080 DATA 0, 5, 10, 15, 18, 25, 32, 20, 38, 20, 65, 10, 85, 15, 100, 5
8000 REM ### Moon DATA: name, desc1, desc2, s_ink, p_ink, s_bright, rg$ ###
8010 DATA "PHOBOS", "Largest moon of Mars", "Dark carbonaceous regolith", 2, 7, 1, "0.006 ms⁻²"
8020 DATA "ENCELADUS", "Moon of Saturn", "White geyser-active water ice crust", 7, 6, 1, "0.11 ms⁻²"
8030 DATA "CHARON", "Moon of Pluto", "Water ice and reddish tholin plains", 3, 7, 0, "0.29 ms⁻²"
8040 DATA "TRITON", "Largest moon of Neptune", "Frozen nitrogen ice crust", 3, 5, 1, "0.78 ms⁻²"
8050 DATA "TITAN", "Largest moon of Saturn", "Water ice crust and methane lakes", 6, 1, 1, "1.35 ms⁻²"
8060 DATA "GANYMEDE", "Largest moon of Jupiter", "Silicate and water ice crust", 5, 7, 0, "1.43 ms⁻²"
8070 DATA "LUNA", "Moon of Earth", "Basalt plains and silicate regolith", 7, 4, 0, "1.62 ms⁻²"
8080 DATA "IO", "Most active moon of Jupiter", "Sulfur and silicate volcanic plains", 4, 2, 1, "1.80 ms⁻²"
