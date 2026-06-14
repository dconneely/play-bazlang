1000 REM ### Game of Life ###
1010 LET width = 200
1020 LET height = 100
1030 PLOTMODE 8
1040 LET ox = INT((PLOTW - width) / 2)
1050 LET oy = INT((PLOTH - height) / 2)
1060 DIM cells(height+2, width+2)
1070 DIM buf(height+2, width+2)
1080 DIM row_sum(height+2)
1090 DIM buf_sum(height+2)
1100 REM ### Init R-pentomino at centre ###
1110 LET cx = INT(width / 2) + 1
1120 LET cy = INT(height / 2) + 1
1130 LET cells(cy, cx) = 1 : LET buf(cy, cx) = 1
1140 LET cells(cy, cx+1) = 1 : LET buf(cy, cx+1) = 1
1150 LET cells(cy+1, cx-1) = 1 : LET buf(cy+1, cx-1) = 1
1160 LET cells(cy+1, cx) = 1 : LET buf(cy+1, cx) = 1
1170 LET cells(cy+2, cx) = 1 : LET buf(cy+2, cx) = 1
1180 LET row_sum(cy) = 2
1190 LET row_sum(cy+1) = 2
1200 LET row_sum(cy+2) = 1
1210 GOSUB 1800
1220 REM ### Initial draw ###
1230 FOR y = 2 TO height+1
1240 IF row_sum(y) = 0 THEN GOTO 1280
1250 FOR x = 2 TO width+1
1260 IF cells(y, x) = 1 THEN PLOT x-2+ox, y-2+oy
1270 NEXT x
1280 NEXT y
1290 LET gen = 0
1310 LET timer = FRAMES : LET gps = 0 : LET gen0 = 0
1400 REM ### Generation loop ###
1410 FAST
1420 LET now = FRAMES
1430 IF now - timer < 50 THEN GOTO 1460
1440 LET gps = INT((gen - gen0) * 50 / (now - timer))
1450 LET gen0 = gen : LET timer = now
1460 PRINT AT 0, 0; "BazLang Game of Life - "; gen; " generations, "; gps; " GPS        "
1470 FOR y = 1 TO height+2 : LET buf_sum(y) = row_sum(y) : NEXT y
1500 FOR y = 2 TO height+1
1510 LET up = y - 1
1520 LET dn = y + 1
1530 IF row_sum(up) <> 0 THEN GOTO 1560
1540 IF row_sum(y) <> 0 THEN GOTO 1560
1550 IF row_sum(dn) = 0 THEN GOTO 1680
1560 LET c1 = cells(up, 1) + cells(y, 1) + cells(dn, 1)
1570 LET c2 = cells(up, 2) + cells(y, 2) + cells(dn, 2)
1580 LET py = y - 2 + oy
1590 FOR x = 2 TO width+1
1600 LET c = cells(y, x)
1610 LET c3 = cells(up, x+1) + cells(y, x+1) + cells(dn, x+1)
1620 LET n = c1 + c2 + c3
1630 IF c = 0 THEN IF n <> 3 THEN GOTO 1670
1640 IF c = 1 THEN IF n = 3 OR n = 4 THEN GOTO 1670
1650 LET a = 1 - c
1651 LET buf(y, x) = a
1652 LET buf_sum(y) = buf_sum(y) + a - c
1653 IF a = 1 THEN PLOT x-2+ox, py
1654 IF a = 0 THEN UNPLOT x-2+ox, py
1670 LET c1 = c2 : LET c2 = c3
1675 NEXT x
1680 NEXT y
1690 REM ### Copy buf to cells ###
1700 FOR y = 2 TO height+1
1710 IF row_sum(y) = 0 THEN IF buf_sum(y) = 0 THEN GOTO 1740
1720 FOR x = 2 TO width+1 : LET cells(y, x) = buf(y, x) : NEXT x
1730 LET row_sum(y) = buf_sum(y)
1740 NEXT y
1750 GOSUB 1800
1760 LET gen = gen + 1
1780 SLOW
1790 GOTO 1400
1800 REM ### Toroidal wrap (ghost cells) ###
1810 IF row_sum(height+1) = 0 THEN IF row_sum(1) = 0 THEN GOTO 1830
1820 FOR x = 1 TO width+2 : LET cells(1, x) = cells(height+1, x) : NEXT x
1830 IF row_sum(2) = 0 THEN IF row_sum(height+2) = 0 THEN GOTO 1850
1840 FOR x = 1 TO width+2 : LET cells(height+2, x) = cells(2, x) : NEXT x
1850 LET row_sum(1) = row_sum(height+1)
1860 LET row_sum(height+2) = row_sum(2)
1870 FOR y = 1 TO height+2
1880 IF row_sum(y) = 0 THEN GOTO 1910
1890 LET cells(y, 1) = cells(y, width+1)
1900 LET cells(y, width+2) = cells(y, 2)
1910 NEXT y
1920 RETURN
