1000 REM ### Conway's Game of Life cellular automaton ###
1010 LET width = 200
1020 LET height = 100
1030 PLOTMODE 8 : CLS
1040 LET ox = INT ((PLOTW - width) / 2)
1050 LET oy = INT ((PLOTH - height) / 2)
1060 DIM cells(height + 2, width + 2)
1070 DIM buf(height + 2, width + 2)
1080 DIM row_sum(height + 2)
1090 DIM buf_sum(height + 2)
1100 REM ### Init R-pentomino at centre ###
1110 LET cx = INT (width / 2) + 1
1120 LET cy = INT (height / 2) + 1
1130 LET cells(cy, cx) = 1 : LET buf(cy, cx) = 1
1140 LET cells(cy, cx + 1) = 1 : LET buf(cy, cx + 1) = 1
1150 LET cells(cy + 1, cx - 1) = 1 : LET buf(cy + 1, cx - 1) = 1
1160 LET cells(cy + 1, cx) = 1 : LET buf(cy + 1, cx) = 1
1170 LET cells(cy + 2, cx) = 1 : LET buf(cy + 2, cx) = 1
1180 LET row_sum(cy) = 2
1190 LET row_sum(cy + 1) = 2
1200 LET row_sum(cy + 2) = 1
1210 GO SUB 5000
2000 REM ### Initial draw ###
2010 FOR y = 2 TO height + 1
2020 IF row_sum(y) = 0 THEN GO TO 2060
2030 FOR x = 2 TO width + 1
2040 IF cells(y, x) = 1 THEN PLOT x - 2 + ox, y - 2 + oy
2050 NEXT x
2060 NEXT y
2070 LET gen = 0
2080 LET timer = FRAMES : LET gps = 0 : LET gen0 = 0
3000 REM ### Generation loop ###
3010 FAST
3020 LET now = FRAMES
3030 IF now - timer < 50 THEN GO TO 3060
3040 LET gps = INT ((gen - gen0) * 50 / (now - timer))
3050 LET gen0 = gen : LET timer = now
3060 PRINT AT 0, 0; "BazLang Game of Life - "; gen; " generations, "; gps; " GPS        "
3070 FOR y = 1 TO height + 2 : LET buf_sum(y) = row_sum(y) : NEXT y
3080 FOR y = 2 TO height + 1
3090 LET up = y - 1
3100 LET dn = y + 1
3110 IF row_sum(up) <> 0 THEN GO TO 3140
3120 IF row_sum(y) <> 0 THEN GO TO 3140
3130 IF row_sum(dn) = 0 THEN GO TO 3300
3140 LET c1 = cells(up, 1) + cells(y, 1) + cells(dn, 1)
3150 LET c2 = cells(up, 2) + cells(y, 2) + cells(dn, 2)
3160 LET py = y - 2 + oy
3170 FOR x = 2 TO width + 1
3180 LET c = cells(y, x)
3190 LET c3 = cells(up, x + 1) + cells(y, x + 1) + cells(dn, x + 1)
3200 LET n = c1 + c2 + c3
3210 IF c = 0 THEN IF n <> 3 THEN GO TO 3280
3220 IF c = 1 THEN IF n = 3 OR n = 4 THEN GO TO 3280
3230 LET a = 1 - c
3240 LET buf(y, x) = a
3250 LET buf_sum(y) = buf_sum(y) + a - c
3260 IF a = 1 THEN PLOT x - 2 + ox, py
3270 IF a = 0 THEN UNPLOT x - 2 + ox, py
3280 LET c1 = c2 : LET c2 = c3
3290 NEXT x
3300 NEXT y
4000 REM ### Copy buf to cells ###
4010 FOR y = 2 TO height + 1
4020 IF row_sum(y) = 0 THEN IF buf_sum(y) = 0 THEN GO TO 4050
4030 FOR x = 2 TO width + 1 : LET cells(y, x) = buf(y, x) : NEXT x
4040 LET row_sum(y) = buf_sum(y)
4050 NEXT y
4060 GO SUB 5000
4070 LET gen = gen + 1
4080 SLOW
4090 GO TO 3000
5000 REM ### Toroidal wrap (ghost cells) ###
5010 IF row_sum(height + 1) = 0 THEN IF row_sum(1) = 0 THEN GO TO 5030
5020 FOR x = 1 TO width + 2 : LET cells(1, x) = cells(height + 1, x) : NEXT x
5030 IF row_sum(2) = 0 THEN IF row_sum(height + 2) = 0 THEN GO TO 5050
5040 FOR x = 1 TO width + 2 : LET cells(height + 2, x) = cells(2, x) : NEXT x
5050 LET row_sum(1) = row_sum(height + 1)
5060 LET row_sum(height + 2) = row_sum(2)
5070 FOR y = 1 TO height + 2
5080 IF row_sum(y) = 0 THEN GO TO 5110
5090 LET cells(y, 1) = cells(y, width + 1)
5100 LET cells(y, width + 2) = cells(y, 2)
5110 NEXT y
5120 RETURN
