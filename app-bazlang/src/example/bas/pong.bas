1000 REM ### Classic Pong ###
1010 PLOTMODE 8
1020 INK -1 : PAPER -1 : CLS
1030 LET w = PLOTW : LET h = PLOTH
1040 LET s1 = 0 : LET s2 = 0
1050 LET ox = INT ((w - 160) / 2)
1060 LET oy = INT ((h - 100) / 2)
1070 LET tox = INT ((TEXTW - 80) / 2)
1080 LET toy = INT ((TEXTH - 25) / 2)
2000 REM ### Round Reset & Init ###
2010 LET bx = 80 : LET by = 50
2020 LET vx = 0.8 : IF RND > 0.5 THEN LET vx = -0.8
2030 LET vy = 0.4 : IF RND > 0.5 THEN LET vy = -0.4
2040 LET p1 = 45 : LET p2 = 45 : REM Paddle Y positions
2050 LET pv = 0 : REM Player 1 paddle velocity
3000 REM ### Main Game Loop ###
3010 LET k$ = UINKEY$
3020 REM --- Player 1 controls (Q = Up, A = Down) ---
3030 IF k$ = "q" OR k$ = "Q" THEN LET pv = pv + 1.2
3040 IF k$ = "a" OR k$ = "A" THEN LET pv = pv - 1.2
3045 REM --- Apply Friction & Smooth Motion ---
3046 LET pv = pv * 0.82
3047 LET p1 = p1 + pv
3048 IF p1 < 2 THEN LET p1 = 2 : LET pv = 0
3049 IF p1 > 88 THEN LET p1 = 88 : LET pv = 0
3050 REM --- Computer AI controls Paddle 2 ---
3060 LET target = p2 + 5
3070 IF by > target THEN LET p2 = p2 + 1.2 : IF p2 > 88 THEN LET p2 = 88
3080 IF by < target THEN LET p2 = p2 - 1.2 : IF p2 < 2 THEN LET p2 = 2
3090 REM --- Update Ball Position ---
3100 LET bx = bx + vx
3110 LET by = by + vy
3120 REM --- Bounce off Top/Bottom walls ---
3130 IF by <= 2 THEN LET by = 2 : LET vy = -vy
3140 IF by >= 96 THEN LET by = 96 : LET vy = -vy
3150 REM --- Paddle 1 Collision Check ---
3160 IF bx <= 8 THEN IF bx >= 4 THEN IF by >= p1 - 1 AND by <= p1 + 11 THEN LET bx = 8 : LET vx = -vx : LET vy = vy + (RND * 1.2 - 0.6) : APLAY "T240V11O6N1e"
3170 REM --- Paddle 2 Collision Check ---
3180 IF bx >= 151 THEN IF bx <= 155 THEN IF by >= p2 - 1 AND by <= p2 + 11 THEN LET bx = 151 : LET vx = -vx : LET vy = vy + (RND * 1.2 - 0.6) : APLAY "T240V11O6N1e"
3190 REM --- Out of Bounds Checks (Scoring) ---
3200 IF bx < 2 THEN LET s2 = s2 + 1 : APLAY "T240N3cO3N4g" : GO TO 6000
3210 IF bx > 158 THEN LET s1 = s1 + 1 : APLAY "T240N2c2e3g" : GO TO 6000
3300 REM --- Render Board ---
3310 FAST : CLS
3320 REM --- Draw Center Court Net ---
3330 INK 7
3340 FOR ny = 4 TO 96 STEP 6
3350 PLOT 80 + ox, ny + oy : DRAW 0, 2
3360 NEXT ny
3370 REM --- Draw Paddles (Thicker: 2 pixels wide) ---
3380 INK 7
3390 PLOT 5 + ox, p1 + oy : DRAW 0, 10
3400 PLOT 6 + ox, p1 + oy : DRAW 0, 10
3410 PLOT 153 + ox, p2 + oy : DRAW 0, 10
3420 PLOT 154 + ox, p2 + oy : DRAW 0, 10
3430 REM --- Draw Ball (Fatter: 3x3 pixels for visibility) ---
3440 PLOT bx + ox, by + oy : PLOT bx + 1 + ox, by + oy : PLOT bx + 2 + ox, by + oy
3450 PLOT bx + ox, by + 1 + oy : PLOT bx + 1 + ox, by + 1 + oy : PLOT bx + 2 + ox, by + 1 + oy
3460 PLOT bx + ox, by + 2 + oy : PLOT bx + 1 + ox, by + 2 + oy : PLOT bx + 2 + ox, by + 2 + oy
3470 REM --- Draw Scores ---
3480 LET x = 60 : LET y = 88 : LET nval = s1 : GO SUB 12000
3490 LET x = 86 : LET y = 88 : LET nval = s2 : GO SUB 12000
3500 SLOW
3510 PAUSE 0.5
3515 GO TO 3000
6000 REM ### Point Scored / Win Check (the per-side point sounds play at 3200/3210) ###
6010 IF s1 >= 9 OR s2 >= 9 THEN GO TO 7000
6020 PAUSE 25 : REM Short delay
6030 GO TO 2000
7000 REM ### Game Over Screen ###
7010 CLS
7020 PRINT AT 10 + toy, 10 + tox; "GAME OVER"
7030 PRINT AT 12 + toy, 10 + tox; "PLAYER: "; s1; "  COMPUTER: "; s2
7040 IF s1 > s2 THEN PRINT AT 14 + toy, 10 + tox; "PLAYER WINS!"
7050 IF s2 > s1 THEN PRINT AT 14 + toy, 10 + tox; "COMPUTER WINS!"
7060 PRINT AT 16 + toy, 10 + tox; "PLAY AGAIN? (Y/N)"
7070 LET k$ = INKEY$
7080 IF k$ = "y" OR k$ = "Y" THEN LET s1 = 0 : LET s2 = 0 : GO TO 2000
7090 IF k$ = "n" OR k$ = "N" THEN STOP
7100 GO TO 7070
11000 REM ### Plot Digit d at x, y ###
11010 IF d = 0 THEN PLOT x + ox, y + oy : DRAW 2, 0 : DRAW 0, 4 : DRAW -2, 0 : DRAW 0, -4 : RETURN
11020 IF d = 1 THEN PLOT x + 1 + ox, y + oy : DRAW 0, 4 : RETURN
11030 IF d = 2 THEN PLOT x + ox, y + 4 + oy : DRAW 2, 0 : DRAW 0, -2 : DRAW -2, 0 : DRAW 0, -2 : DRAW 2, 0 : RETURN
11040 IF d = 3 THEN PLOT x + ox, y + 4 + oy : DRAW 2, 0 : DRAW 0, -4 : DRAW -2, 0 : PLOT x + ox, y + 2 + oy : DRAW 2, 0 : RETURN
11050 IF d = 4 THEN PLOT x + ox, y + 4 + oy : DRAW 0, -2 : DRAW 2, 0 : PLOT x + 2 + ox, y + 4 + oy : DRAW 0, -4 : RETURN
11060 IF d = 5 THEN PLOT x + 2 + ox, y + 4 + oy : DRAW -2, 0 : DRAW 0, -2 : DRAW 2, 0 : DRAW 0, -2 : DRAW -2, 0 : RETURN
11070 IF d = 6 THEN PLOT x + 2 + ox, y + 4 + oy : DRAW -2, 0 : DRAW 0, -4 : DRAW 2, 0 : DRAW 0, 2 : DRAW -2, 0 : RETURN
11080 IF d = 7 THEN PLOT x + ox, y + 4 + oy : DRAW 2, 0 : DRAW 0, -4 : RETURN
11090 IF d = 8 THEN PLOT x + ox, y + oy : DRAW 2, 0 : DRAW 0, 4 : DRAW -2, 0 : DRAW 0, -4 : PLOT x + ox, y + 2 + oy : DRAW 2, 0 : RETURN
11100 IF d = 9 THEN PLOT x + 2 + ox, y + oy : DRAW 0, 4 : DRAW -2, 0 : DRAW 0, -2 : DRAW 2, 0 : RETURN
11110 RETURN
12000 REM ### Plot Number nval at x, y ###
12010 LET ntemp = nval
12020 LET nd1 = INT (ntemp / 1000) : LET ntemp = ntemp - nd1 * 1000
12040 LET nd2 = INT (ntemp / 100) : LET ntemp = ntemp - nd2 * 100
12050 LET nd3 = INT (ntemp / 10) : LET ntemp = ntemp - nd3 * 10
12060 LET nd4 = ntemp
12070 LET orig_x = x
12080 LET d = nd1 : GO SUB 11000
12090 LET x = orig_x + 4 : LET d = nd2 : GO SUB 11000
12100 LET x = orig_x + 8 : LET d = nd3 : GO SUB 11000
12110 LET x = orig_x + 12 : LET d = nd4 : GO SUB 11000
12120 LET x = orig_x : RETURN
