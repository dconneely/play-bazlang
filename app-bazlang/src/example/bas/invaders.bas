1000 REM ### Space Invaders ###
1010 PLOTMODE 8
1020 INK -1 : PAPER -1 : CLS
1030 LET w = PLOTW : LET h = PLOTH
1040 LET score = 0 : LET high = 0 : LET wave = 1
1050 LET ox = INT ((w - 160) / 2)
1060 LET oy = INT ((h - 100) / 2)
1070 LET tox = INT ((TEXTW - 80) / 2)
1080 LET toy = INT ((TEXTH - 25) / 2)
2000 REM ### Wave Start / Init ###
2010 LET gx = 30 : LET gy = 44 - (wave - 1) * 4
2020 IF gy < 28 THEN LET gy = 28
2030 LET gdir = 1
2040 LET px = 80 : LET lives = 3
2050 LET bx = 0 : LET by = 0 : REM Player Bullet
2060 DIM abx(3) : DIM aby(3) : REM Alien Bullets
2070 FOR i = 1 TO 3 : LET abx(i) = 0 : LET aby(i) = 0 : NEXT i
2080 DIM a(5, 6) : REM 5 rows, 6 columns of aliens
2090 FOR r = 1 TO 5 : FOR c = 1 TO 6 : LET a(r, c) = 1 : NEXT c : NEXT r
2100 LET aliens_left = 30 : LET alien_tick = 0 : LET tick_max = 12 - wave
2110 IF tick_max < 2 THEN LET tick_max = 2
2120 LET ux = -20 : LET udir = 0 : REM UFO status
2130 DIM s(160) : DIM b(160) : REM Shield top/bottom heights
2140 GO SUB 5000 : REM Reset Shield heights
3000 REM ### Main Game Loop ###
3010 LET k$ = UINKEY$
3020 REM --- Player Control ---
3030 IF k$ = "5" OR k$ = CHR$ (27) + "[D" OR k$ = CHR$ (27) + "OD" THEN LET px = px - 4 : IF px < 6 THEN LET px = 6
3040 IF k$ = "8" OR k$ = CHR$ (27) + "[C" OR k$ = CHR$ (27) + "OC" THEN LET px = px + 4 : IF px > 153 THEN LET px = 153
3050 IF k$ = " " OR k$ = "6" OR k$ = CHR$ (27) + "[A" OR k$ = CHR$ (27) + "OA" THEN IF by = 0 THEN LET bx = px : LET by = 14
3060 REM --- Update Player Bullet ---
3070 IF by = 0 THEN GO TO 3110
3080 LET by = by + 3
3090 IF by >= 96 THEN LET by = 0 : GO TO 3110
3100 GO SUB 4000 : REM Handle Bullet Collision (can set by = 0)
3110 REM --- Update Alien Bullets ---
3120 FOR i = 1 TO 3
3130 IF aby(i) = 0 THEN GO TO 3170
3140 LET aby(i) = aby(i) - 2
3150 IF aby(i) < 4 THEN LET aby(i) = 0 : GO TO 3170
3160 GO SUB 4200 : REM Handle Alien Bullet Hit (can set aby(i) = 0)
3170 NEXT i
3180 REM --- alien firing ---
3190 IF RND > 0.05 THEN GO TO 3250
3200 FOR i = 1 TO 3
3210 IF aby(i) <> 0 THEN GO TO 3240
3220 REM Fire from a random column
3230 LET col = INT (RND * 6) + 1 : GO SUB 4400 : LET i = 3
3240 NEXT i
3250 REM --- Update UFO ---
3260 IF ux < -15 THEN IF RND < 0.01 THEN LET ux = 0 : LET udir = 1
3270 IF ux < 0 THEN GO TO 3310
3280 LET ux = ux + 2
3290 IF ux > 150 THEN LET ux = -20 : GO TO 3310
3300 IF by > 0 THEN IF bx >= ux AND bx <= ux + 12 AND by >= 78 AND by <= 83 THEN GO SUB 9080
3310 REM --- Update Alien Grid Position ---
3320 LET alien_tick = alien_tick + 1
3330 IF alien_tick < tick_max THEN GO TO 3430
3340 LET alien_tick = 0
3350 LET gx = gx + gdir * 3
3360 REM Check grid boundaries
3370 LET edge = 0
3380 FOR r = 1 TO 5 : FOR c = 1 TO 6
3390 IF a(r, c) = 0 THEN GO TO 3410
3400 LET ax = gx + (c - 1) * 18 : IF ax < 4 OR ax > 146 THEN LET edge = 1
3410 NEXT c : NEXT r
3420 IF edge = 1 THEN LET gdir = -gdir : LET gy = gy - 2
3430 REM Check landing condition based on lowest active alien
3440 LET lowest_y = 999
3450 FOR r = 1 TO 5 : FOR c = 1 TO 6
3460 IF a(r, c) = 1 THEN LET ay = gy + (5 - r) * 8 : IF ay < lowest_y THEN LET lowest_y = ay
3470 NEXT c : NEXT r
3480 IF lowest_y < 14 THEN GO TO 6100
3490 REM --- Render All Elements (FAST / CLS / SLOW) ---
3500 FAST : CLS
3510 GO SUB 5140 : REM Draw Shelters dynamically using s(x) and b(x)
3520 GO SUB 7000 : REM Draw Player
3530 IF by > 0 THEN GO SUB 14000 : REM Draw Player Bullet
3540 FOR i = 1 TO 3
3550 IF aby(i) > 0 THEN LET type = i : GO SUB 14050 : REM Draw Alien Bullet
3560 NEXT i
3570 IF ux >= 0 THEN GO SUB 9000 : REM Draw UFO
3580 GO SUB 8230 : REM Draw Aliens
3590 REM Draw Scores & Labels
3600 INK 7 : LET x = 10 : LET y = 88 : LET nval = score : GO SUB 12000
3610 INK 7 : LET x = 70 : LET y = 88 : LET nval = high : GO SUB 12000
3620 INK 7 : LET x = 10 : LET y = 94 : GO SUB 12120 : REM SCORE label
3630 INK 7 : LET x = 70 : LET y = 94 : GO SUB 12200 : REM HI-SCORE label
3640 GO SUB 13000 : REM Draw Docked Ships (Lives)
3650 SLOW : REM Commit frame buffer
3660 PAUSE 1
3670 GO TO 3010
4000 REM ### Bullet Collision Handler ###
4010 REM check shields dynamically using s(x) and b(x)
4020 IF by >= 20 AND by <= 27 THEN IF bx >= 24 AND bx <= 135 THEN IF by - 20 >= b(bx) AND by - 20 < s(bx) THEN LET b(bx) = by - 19 : LET by = 0 : RETURN
4030 REM check aliens
4040 FOR r = 1 TO 5 : FOR c = 1 TO 6
4050 IF a(r, c) = 0 THEN GO TO 4080
4060 LET ax = gx + (c - 1) * 18 : LET ay = gy + (5 - r) * 8
4070 IF bx >= ax AND bx <= ax + 10 AND by >= ay AND by <= ay + 6 THEN GO TO 4100
4080 NEXT c : NEXT r
4090 RETURN
4100 REM Alien Hit!
4110 LET a(r, c) = 0 : LET aliens_left = aliens_left - 1
4120 REM squid 30 pts, crab 20 pts, octopus 10 pts
4130 LET points = 10 : IF r = 1 THEN LET points = 30
4140 IF r = 2 OR r = 3 THEN LET points = 20
4150 LET score = score + points
4160 LET by = 0
4170 LET tick_max = INT (aliens_left / 3) + 2 - wave : IF tick_max < 1 THEN LET tick_max = 1
4180 IF aliens_left = 0 THEN LET wave = wave + 1 : GO TO 2000
4190 RETURN
4200 REM ### Alien Bullet Collision Handler ###
4210 REM check shields dynamically using s(x) and b(x)
4220 LET ay = aby(i) : LET ax = abx(i)
4230 IF ay >= 20 AND ay <= 27 THEN IF ax >= 24 AND ax <= 135 THEN IF ay - 20 >= b(ax) AND ay - 20 < s(ax) THEN LET s(ax) = ay - 20 : LET aby(i) = 0 : RETURN
4240 REM check player
4250 IF ay >= 6 AND ay <= 14 AND ax >= px - 6 AND ax <= px + 6 THEN GO TO 4270
4260 RETURN
4270 REM Player Hit!
4280 LET lives = lives - 1
4290 LET aby(i) = 0
4300 IF lives = 0 THEN GO TO 6000
4310 FOR f = 1 TO 6
4320 FAST : CLS
4330 GO SUB 5140 : REM Keep shelters drawn
4340 REM Alternate explosion shapes
4350 IF f - INT (f / 2) * 2 = 0 THEN PLOT px - 4 + ox, 9 + oy : DRAW 8, 0 : PLOT px - 1 + ox, 11 + oy : PLOT px + ox, 12 + oy
4360 IF f - INT (f / 2) * 2 <> 0 THEN PLOT px - 5 + ox, 8 + oy : DRAW 10, 0 : PLOT px - 2 + ox, 10 + oy : DRAW 4, 0
4370 SLOW : PAUSE 4
4380 NEXT f
4390 LET px = 80 : RETURN
4400 REM ### Alien Fire Subroutine ###
4410 FOR r = 5 TO 1 STEP -1
4420 IF a(r, col) = 1 THEN LET abx(i) = gx + (col - 1) * 18 + 5 : LET aby(i) = gy + (5 - r) * 8 - 2 : RETURN
4430 NEXT r
4440 RETURN
5000 REM ### Reset Shield heights array ###
5010 FOR sx = 1 TO 160 : LET s(sx) = 0 : LET b(sx) = 0 : NEXT sx
5020 FOR sx = 24 TO 120 STEP 48
5030 FOR ix = 0 TO 15
5040 LET s(sx + ix) = 8 : LET b(sx + ix) = 0
5050 IF ix = 0 OR ix = 15 THEN LET s(sx + ix) = 4
5060 IF ix = 1 OR ix = 14 THEN LET s(sx + ix) = 6
5070 IF ix = 2 OR ix = 13 THEN LET s(sx + ix) = 7
5080 IF ix = 3 OR ix = 12 THEN LET b(sx + ix) = 1
5090 IF ix = 4 OR ix = 11 THEN LET b(sx + ix) = 3
5100 IF ix >= 5 AND ix <= 10 THEN LET b(sx + ix) = 4
5110 NEXT ix
5120 NEXT sx
5130 RETURN
5140 REM ### Draw Shelters from heights array ###
5150 INK 4 : REM Green shelters
5160 FOR sx = 24 TO 135
5170 IF b(sx) >= s(sx) THEN GO TO 5210
5180 FOR sh = b(sx) TO s(sx) - 1
5190 PLOT sx + ox, 20 + sh + oy
5200 NEXT sh
5210 NEXT sx
5220 RETURN
6000 REM ### Game Over Screen ###
6010 CLS
6020 PRINT AT 10 + toy, 10 + tox; "GAME OVER"
6030 PRINT AT 12 + toy, 10 + tox; "FINAL SCORE: "; score
6040 IF score > high THEN LET high = score
6050 PRINT AT 14 + toy, 10 + tox; "PLAY AGAIN? (Y/N)"
6060 LET k$ = INKEY$
6070 IF k$ = "y" OR k$ = "Y" THEN GO TO 2000
6080 IF k$ = "n" OR k$ = "N" THEN STOP
6090 GO TO 6060
6100 REM ### Invaders Landed Game Over ###
6110 CLS
6120 PRINT AT 10 + toy, 10 + tox; "THE INVADERS LANDED!"
6130 GO TO 6030
7000 REM ### Draw Player Ship ###
7010 INK 4 : REM Green player ship
7020 PLOT px - 3 + ox, 8 + oy : DRAW 6, 0
7030 PLOT px - 3 + ox, 9 + oy : DRAW 6, 0
7040 PLOT px - 2 + ox, 10 + oy : DRAW 4, 0
7050 PLOT px - 1 + ox, 11 + oy : DRAW 2, 0
7060 PLOT px + ox, 12 + oy : PLOT px + ox, 13 + oy
7070 RETURN
8000 REM ### Draw Single Alien ###
8010 INK 7 : REM All white aliens (Taito style)
8020 IF r = 1 THEN GO TO 8110 : REM Squid
8030 IF r = 2 OR r = 3 THEN GO TO 8170 : REM Crab
8040 REM Octopus (r=4, 5)
8050 PLOT x + 3, y : PLOT x + 6, y
8060 PLOT x + 1, y + 1 : PLOT x + 3, y + 1 : PLOT x + 6, y + 1 : PLOT x + 8, y + 1
8070 PLOT x + 1, y + 2 : DRAW 7, 0
8080 PLOT x, y + 3 : DRAW 2, 0 : PLOT x + 4, y + 3 : DRAW 1, 0 : PLOT x + 7, y + 3 : DRAW 2, 0
8090 PLOT x, y + 4 : DRAW 9, 0
8100 PLOT x + 2, y + 5 : DRAW 5, 0 : RETURN
8110 REM Squid
8120 PLOT x + 4, y : PLOT x + 5, y
8130 PLOT x + 2, y + 1 : DRAW 5, 0
8140 PLOT x + 1, y + 2 : DRAW 7, 0
8150 PLOT x + 1, y + 3 : PLOT x + 3, y + 3 : PLOT x + 6, y + 3 : PLOT x + 8, y + 3
8160 PLOT x + 3, y + 4 : DRAW 3, 0 : PLOT x + 4, y + 5 : PLOT x + 5, y + 5 : RETURN
8170 REM Crab
8180 PLOT x + 2, y : PLOT x + 7, y
8190 PLOT x + 1, y + 1 : PLOT x + 3, y + 1 : PLOT x + 6, y + 1 : PLOT x + 8, y + 1
8200 PLOT x + 1, y + 2 : DRAW 7, 0
8210 PLOT x, y + 3 : DRAW 9, 0
8220 PLOT x + 2, y + 4 : DRAW 5, 0 : PLOT x + 1, y + 5 : PLOT x + 8, y + 5 : RETURN
8230 REM ### Draw All Aliens ###
8240 FOR ar = 1 TO 5 : FOR ac = 1 TO 6
8250 IF a(ar, ac) = 0 THEN GO TO 8280
8260 LET x = gx + (ac - 1) * 18 + ox : LET y = gy + (5 - ar) * 8 + oy
8270 LET r = ar : GO SUB 8000
8280 NEXT ac : NEXT ar
8290 RETURN
9000 REM ### Draw UFO ###
9010 INK 2 : REM Red UFO
9020 PLOT ux + 4 + ox, 78 + oy : DRAW 3, 0
9030 PLOT ux + 2 + ox, 78 + 1 + oy : DRAW 7, 0
9040 PLOT ux + ox, 78 + 2 + oy : DRAW 11, 0
9050 PLOT ux + 1 + ox, 78 + 3 + oy : DRAW 9, 0
9060 PLOT ux + 3 + ox, 78 + 4 + oy : DRAW 5, 0
9070 RETURN
9080 REM ### UFO Hit Subroutine ###
9090 LET uval = RND
9100 LET pts = 50
9110 IF uval > 0.25 THEN LET pts = 100
9120 IF uval > 0.50 THEN LET pts = 150
9130 IF uval > 0.75 THEN LET pts = 300
9140 LET score = score + pts
9150 LET by = 0 : LET ux = -20
9160 RETURN
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
11120 REM ### Plot Character c at x, y ###
11130 REM Inputs: c (1=S, 2=C, 3=O, 4=R, 5=E, 6=H, 7=I, 8=-)
11140 IF c = 1 THEN PLOT x + 2 + ox, y + 4 + oy : DRAW -2, 0 : DRAW 0, -2 : DRAW 2, 0 : DRAW 0, -2 : DRAW -2, 0 : RETURN
11150 IF c = 2 THEN PLOT x + 2 + ox, y + 4 + oy : DRAW -2, 0 : DRAW 0, -4 : DRAW 2, 0 : RETURN
11160 IF c = 3 THEN PLOT x + ox, y + oy : DRAW 2, 0 : DRAW 0, 4 : DRAW -2, 0 : DRAW 0, -4 : RETURN
11170 IF c = 4 THEN PLOT x + ox, y + oy : DRAW 0, 4 : DRAW 2, 0 : DRAW 0, -2 : DRAW -2, 0 : PLOT x + 2 + ox, y + oy : DRAW -1, 2 : RETURN
11180 IF c = 5 THEN PLOT x + 2 + ox, y + 4 + oy : DRAW -2, 0 : DRAW 0, -4 : DRAW 2, 0 : PLOT x + ox, y + 2 + oy : DRAW 1, 0 : RETURN
11190 IF c = 6 THEN PLOT x + ox, y + oy : DRAW 0, 4 : PLOT x + 2 + ox, y + oy : DRAW 0, 4 : PLOT x + ox, y + 2 + oy : DRAW 2, 0 : RETURN
11200 IF c = 7 THEN PLOT x + 1 + ox, y + oy : DRAW 0, 4 : PLOT x + ox, y + oy : DRAW 2, 0 : PLOT x + ox, y + 4 + oy : DRAW 2, 0 : RETURN
11210 IF c = 8 THEN PLOT x + ox, y + 2 + oy : DRAW 2, 0 : RETURN
11220 RETURN
12000 REM ### Plot Number nval at x, y ###
12010 LET ntemp = nval
12020 LET nd1 = INT (ntemp / 1000) : LET ntemp = ntemp - nd1 * 1000
12030 LET nd2 = INT (ntemp / 100) : LET ntemp = ntemp - nd2 * 100
12040 LET nd3 = INT (ntemp / 10) : LET ntemp = ntemp - nd3 * 10
12050 LET nd4 = ntemp
12060 LET orig_x = x
12070 LET d = nd1 : GO SUB 11000
12080 LET x = orig_x + 4 : LET d = nd2 : GO SUB 11000
12090 LET x = orig_x + 8 : LET d = nd3 : GO SUB 11000
12100 LET x = orig_x + 12 : LET d = nd4 : GO SUB 11000
12110 LET x = orig_x : RETURN
12120 REM ### Plot word SCORE at x, y ###
12130 LET orig_x = x
12140 LET c = 1 : GO SUB 11120 : REM S
12150 LET x = orig_x + 4 : LET c = 2 : GO SUB 11120 : REM C
12160 LET x = orig_x + 8 : LET c = 3 : GO SUB 11120 : REM O
12170 LET x = orig_x + 12 : LET c = 4 : GO SUB 11120 : REM R
12180 LET x = orig_x + 16 : LET c = 5 : GO SUB 11120 : REM E
12190 LET x = orig_x : RETURN
12200 REM ### Plot word HI-SCORE at x, y ###
12210 LET orig_x = x
12220 LET c = 6 : GO SUB 11120 : REM H
12230 LET x = orig_x + 4 : LET c = 7 : GO SUB 11120 : REM I
12240 LET x = orig_x + 8 : LET c = 8 : GO SUB 11120 : REM -
12250 LET x = orig_x + 12 : LET c = 1 : GO SUB 11120 : REM S
12260 LET x = orig_x + 16 : LET c = 2 : GO SUB 11120 : REM C
12270 LET x = orig_x + 20 : LET c = 3 : GO SUB 11120 : REM O
12280 LET x = orig_x + 24 : LET c = 4 : GO SUB 11120 : REM R
12290 LET x = orig_x + 28 : LET c = 5 : GO SUB 11120 : REM E
12300 LET x = orig_x : RETURN
13000 REM ### Draw Docked Ships (Lives) ###
13010 INK 4 : REM Green
13020 IF lives < 2 THEN RETURN
13030 FOR i = 1 TO lives - 1
13040 LET lx = i * 12
13050 PLOT lx - 3 + ox, 2 + oy : DRAW 6, 0
13060 PLOT lx - 3 + ox, 3 + oy : DRAW 6, 0
13070 PLOT lx - 2 + ox, 4 + oy : DRAW 4, 0
13080 PLOT lx - 1 + ox, 5 + oy : DRAW 2, 0
13090 PLOT lx + ox, 6 + oy : PLOT lx + ox, 7 + oy
13100 NEXT i
13110 RETURN
14000 REM ### Draw Player Bullet ###
14010 REM Player Bullet is a 3-pixel vertical line (Straight Rolling style)
14020 INK 6 : REM Yellow player bullet
14030 PLOT bx + ox, by - 1 + oy : DRAW 0, 2
14040 RETURN
14050 REM ### Draw Alien Bullet ###
14060 REM Inputs: type (1=Squiggly, 2=Plunger, 3=Rolling)
14070 LET ax = abx(type) : LET ay = aby(type)
14080 INK 6 : REM Yellow alien bomb
14090 IF type = 1 THEN GO TO 14140 : REM Squiggly
14100 IF type = 2 THEN GO TO 14180 : REM Plunger
14110 REM Type 3: Rolling (Vertical Line)
14120 PLOT ax + ox, ay - 1 + oy : DRAW 0, 2
14130 RETURN
14140 REM Squiggly (A zig-zag shape alternating based on ay coordinate)
14150 LET alt = ay - INT (ay / 2) * 2
14160 IF alt = 0 THEN PLOT ax + ox, ay + 1 + oy : PLOT ax + 1 + ox, ay + oy : PLOT ax + ox, ay - 1 + oy : RETURN
14170 PLOT ax + 1 + ox, ay + 1 + oy : PLOT ax + ox, ay + oy : PLOT ax + 1 + ox, ay - 1 + oy : RETURN
14180 REM Plunger (Cross Shape)
14190 PLOT ax + ox, ay - 1 + oy : DRAW 0, 2
14200 PLOT ax - 1 + ox, ay + oy : PLOT ax + 1 + ox, ay + oy
14210 RETURN
