1000 REM ### Lunar Lander ###
1010 REM ### Retro graphical landing simulation under PLOTMODE 8 ###
1020 PLOTMODE 8
1030 INK -1 : PAPER -1
1040 LET w = PLOTW : LET h = PLOTH
1050 LET n = 8
1060 DIM tx(8) : DIM ty(8)
1070 LET score = 0 : LET level = 1
2000 REM ### Game start / Reset lander ###
2010 IF level = 9 THEN GO TO 6500
2020 LET limit_level = level : IF limit_level > 8 THEN LET limit_level = 8
2030 LET p$ = "Phobos" : LET desc$ = "Phobos, largest moon of Mars - dark carbonaceous regolith" : LET s_ink = 2 : LET p_ink = 7 : LET s_bright = 1 : LET rg$ = "0.006 m/s2"
2040 IF limit_level = 2 THEN LET p$ = "Enceladus" : LET desc$ = "Enceladus, moon of Saturn - white geyser-active water ice crust" : LET s_ink = 7 : LET p_ink = 6 : LET s_bright = 1 : LET rg$ = "0.11 m/s2"
2050 IF limit_level = 3 THEN LET p$ = "Charon" : LET desc$ = "Charon, moon of Pluto - water ice and reddish tholin plains" : LET s_ink = 3 : LET p_ink = 7 : LET s_bright = 0 : LET rg$ = "0.29 m/s2"
2060 IF limit_level = 4 THEN LET p$ = "Triton" : LET desc$ = "Triton, largest moon of Neptune - frozen nitrogen ice crust" : LET s_ink = 3 : LET p_ink = 5 : LET s_bright = 1 : LET rg$ = "0.78 m/s2"
2070 IF limit_level = 5 THEN LET p$ = "Titan" : LET desc$ = "Titan, largest moon of Saturn - water ice crust and methane lakes" : LET s_ink = 6 : LET p_ink = 1 : LET s_bright = 1 : LET rg$ = "1.35 m/s2"
2080 IF limit_level = 6 THEN LET p$ = "Ganymede" : LET desc$ = "Ganymede, largest moon of Jupiter - silicate and water ice crust" : LET s_ink = 5 : LET p_ink = 7 : LET s_bright = 0 : LET rg$ = "1.43 m/s2"
2090 IF limit_level = 7 THEN LET p$ = "Luna" : LET desc$ = "Luna, moon of Earth - basalt plains and silicate regolith" : LET s_ink = 7 : LET p_ink = 4 : LET s_bright = 0 : LET rg$ = "1.62 m/s2"
2100 IF limit_level = 8 THEN LET p$ = "Io" : LET desc$ = "Io, most active moon of Jupiter - sulfur and silicate volcanic plains" : LET s_ink = 4 : LET p_ink = 2 : LET s_bright = 1 : LET rg$ = "1.80 m/s2"
2110 LET px = w * 0.1
2120 LET py = h * 0.8
2130 LET vx = 0.03 * limit_level
2140 LET vy = 0
2150 LET fuel = 650 - 30 * limit_level
2160 LET theta = 1.5708
2170 LET g = 0.002 + 0.002 * limit_level
2180 LET thrust = 0.045
2190 LET max_vy = 2.2 - 0.15 * limit_level
2200 LET max_vx = 1.2 - 0.08 * limit_level
2210 LET max_ang = 45 - 3 * limit_level
2220 RESTORE 7000 + limit_level * 10
2230 FOR i = 1 TO n
2240 READ px_pct, py_pct
2250 LET tx(i) = px_pct * w / 100
2260 LET ty(i) = py_pct * h / 100
2270 NEXT i
3000 REM ### Main game loop ###
3010 LET k$ = UINKEY$
3020 LET thrusting = 0
3030 IF k$ = " " AND fuel > 0 THEN LET thrusting = 1
3040 IF thrusting = 0 THEN GO TO 3080
3050 LET vx = vx + thrust * COS (theta)
3060 LET vy = vy + thrust * SIN (theta)
3070 LET fuel = fuel - 2
3080 IF k$ = "o" OR k$ = "O" THEN LET theta = theta + 0.1
3090 IF k$ = "p" OR k$ = "P" THEN LET theta = theta - 0.1
3100 IF theta > 6.28318 THEN LET theta = theta - 6.28318
3110 IF theta < 0 THEN LET theta = theta + 6.28318
3120 REM ### Apply gravity and update position ###
3130 LET vy = vy - g
3140 LET px = px + vx : LET py = py + vy
3150 REM ### Boundary check ###
3160 IF px < 0 OR px > w OR py > h THEN GO TO 6000 : REM ### Lost in Space ###
3170 REM ### Landscape collision detection ###
3180 LET seg = 1
3190 FOR i = 1 TO n - 1
3200 IF px >= tx(i) AND px <= tx(i + 1) THEN LET seg = i
3210 NEXT i
3220 LET x_diff = tx(seg + 1) - tx(seg)
3230 LET y_diff = ty(seg + 1) - ty(seg)
3240 LET terrain_y = ty(seg) + (px - tx(seg)) * y_diff / x_diff
3250 IF py - 4 <= terrain_y THEN GO TO 5000 : REM ### Touched down/Crashed ###
3260 REM ### Drawing phase ###
3270 FAST
3280 CLS
3290 REM ### Draw terrain ###
3300 BRIGHT s_bright
3305 FOR i = 1 TO n - 1
3310 INK s_ink : IF i = 4 THEN INK p_ink : REM ### Moon-specific colours ###
3320 PLOT tx(i), ty(i) : DRAW tx(i + 1) - tx(i), ty(i + 1) - ty(i)
3330 NEXT i
3335 BRIGHT 1 : REM ### Default bright for lander, flame, UI ###
3340 REM ### Draw landing pad text indicator ###
3350 PRINT AT TEXTH - 3, INT ((tx(4) + tx(5)) / 4) - 4; INK -1; "== PAD =="
3360 REM ### Draw Lander ###
3370 INK 5 : REM ### Cyan lander body ###
3380 LET c_th = COS (theta) : LET s_th = SIN (theta)
3390 LET tip_x = px + 6 * c_th : LET tip_y = py + 6 * s_th
3400 LET left_x = px - 4 * c_th - 3 * s_th : LET left_y = py - 4 * s_th + 3 * c_th
3410 LET right_x = px - 4 * c_th + 3 * s_th : LET right_y = py - 4 * s_th - 3 * c_th
3420 PLOT left_x, left_y : DRAW tip_x - left_x, tip_y - left_y
3430 PLOT right_x, right_y : DRAW tip_x - right_x, tip_y - right_y
3440 INK 5 : IF thrusting = 1 THEN INK 6
3445 PLOT left_x, left_y : DRAW right_x - left_x, right_y - left_y : REM ### Dynamic base ###
3450 REM ### Draw thrust flame if active ###
3460 IF thrusting = 0 THEN GO TO 3510
3470 LET base_x = px - 4 * c_th : LET base_y = py - 4 * s_th
3480 INK 2 : PLOT base_x, base_y : DRAW -6 * c_th, -6 * s_th
3490 INK 6 : PLOT base_x, base_y : DRAW -3 * c_th, -3 * s_th
3510 REM ### Print dashboard ###
3520 LET t_deg = INT ((theta - 1.5708) * 57.3)
3530 IF t_deg < -180 THEN LET t_deg = t_deg + 360
3540 IF t_deg > 180 THEN LET t_deg = t_deg - 360
3550 PRINT AT 0, 0; INK -1; PAPER -1; desc$
3560 PRINT AT 1, 0; INK -1; PAPER -1; "Score:"; score; "  Fuel:"; fuel; "  Ang:"; t_deg; "  G:"; rg$; "  VX:"; INT (vx * 100) / 100; "  VY:"; INT (vy * 100) / 100
3570 SLOW
3580 PAUSE 2
3590 GO TO 3000
5000 REM ### Landing / Crash logic ###
5010 SLOW
5020 CLS
5030 PRINT AT 5, 5; INK -1; "TOUCHDOWN DETECTED!"
5040 LET landing_ok = 1
5050 REM ### Check speed, angle, segment, and fuel ###
5060 IF seg <> 4 THEN LET landing_ok = 0 : PRINT AT 7, 5; INK -1; "- Landed off-pad!"
5070 IF ABS (vy) > max_vy THEN LET landing_ok = 0 : PRINT AT 8, 5; INK -1; "- Too fast vertically! (Limit: "; INT (max_vy * 100) / 100; ")"
5080 IF ABS (vx) > max_vx THEN LET landing_ok = 0 : PRINT AT 9, 5; INK -1; "- Slide crash! (Limit: "; INT (max_vx * 100) / 100; ")"
5090 IF ABS (t_deg) > max_ang THEN LET landing_ok = 0 : PRINT AT 10, 5; INK -1; "- Tilted! (Ang: "; t_deg; " deg, Limit: "; max_ang; ")"
5100 IF fuel <= 0 THEN LET landing_ok = 0 : PRINT AT 11, 5; INK -1; "- Out of fuel!"
5110 IF landing_ok = 0 THEN GO TO 5160
5120 PRINT AT 13, 5; INK 4; "SUCCESSFUL LANDING!"
5130 PRINT AT 14, 5; INK -1; "Bonus Score +100"
5140 LET score = score + 100 : LET level = level + 1
5150 GO TO 5190
5160 PRINT AT 13, 5; INK 2; "CRASHED! MODULE EXPLODED!"
5170 PRINT AT 14, 5; INK -1; "Score Reset!"
5180 LET score = (level - 1) * 100
5190 PRINT AT 17, 5; INK -1; "Press any key to retry..."
5200 IF UINKEY$ = "" THEN GO TO 5200
5210 GO TO 2000
6000 REM ### Out of bounds crash ###
6010 SLOW
6020 CLS
6030 PRINT AT 5, 5; INK 2; "MODULE LOST IN DEEP SPACE!"
6040 PRINT AT 7, 5; INK -1; "Score Reset!"
6050 LET score = (level - 1) * 100
6060 GO TO 5190
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
7010 DATA 0, 5, 20, 25, 35, 15, 50, 10, 70, 10, 80, 20, 90, 15, 100, 5
7020 DATA 0, 5, 10, 15, 20, 10, 40, 10, 55, 25, 70, 15, 85, 20, 100, 5
7030 DATA 0, 15, 15, 5, 30, 20, 40, 10, 60, 10, 75, 25, 88, 10, 100, 15
7040 DATA 0, 5, 15, 20, 30, 15, 35, 10, 55, 10, 70, 25, 85, 15, 100, 5
7050 DATA 0, 5, 15, 25, 30, 15, 40, 10, 60, 10, 75, 30, 88, 15, 100, 5
7060 DATA 0, 5, 20, 15, 35, 25, 45, 10, 65, 10, 78, 20, 88, 15, 100, 5
7070 DATA 0, 10, 15, 25, 35, 15, 55, 10, 75, 10, 85, 20, 92, 15, 100, 5
7080 DATA 0, 5, 10, 15, 18, 25, 25, 20, 45, 20, 65, 10, 85, 15, 100, 5
