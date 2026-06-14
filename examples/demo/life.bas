1000 REM ### Game of Life ###
1010 LET width = 160
1015 PLOTMODE 8
1020 LET height = 96
1030 DIM cells$(height+2, width+2)
1040 DIM next_cells$(height+2, width+2)
1050 DIM empty_row$(1, width+2)
1060 REM ### Init R-pentomino at center ###
1070 LET centre_x = 81
1080 LET centre_y = 49
1090 LET cells$(centre_y, centre_x) = CHR$ 33
1100 LET cells$(centre_y, centre_x+1) = CHR$ 33
1110 LET cells$(centre_y+1, centre_x-1) = CHR$ 33
1120 LET cells$(centre_y+1, centre_x) = CHR$ 33
1130 LET cells$(centre_y+2, centre_x) = CHR$ 33
1140 GOSUB 1460
1150 REM ### Initial draw ###
1160 FOR y = 2 TO height+1
1170 FOR x = 2 TO width+1
1180 IF CODE cells$(y, x) = 33 THEN PLOT x-2, y-2
1190 NEXT x
1200 NEXT y
1210 LET generation = 0
1215 LET start_t = TIMER : LET gps = 0 : LET gen_start = 0
1220 REM ### Generation loop ###
1225 FAST
1226 LET now = TIMER
1227 IF now - start_t < 50 THEN GOTO 1230
1228 LET gps = INT((generation - gen_start) * 50 / (now - start_t)) : LET gen_start = generation : LET start_t = now
1230 PRINT AT 0, 0; "GENERATION: "; generation; " ("; gps; " GPS)   "
1240 FOR y = 2 TO height+1
1250 LET up_y = y - 1
1260 LET down_y = y + 1
1270 IF cells$(up_y) <> empty_row$(1) OR cells$(y) <> empty_row$(1) OR cells$(down_y) <> empty_row$(1) THEN GOTO 1292
1280 LET next_cells$(y) = empty_row$(1)
1290 GOTO 1380
1292 LET r0$ = cells$(up_y) : LET r1$ = cells$(y) : LET r2$ = cells$(down_y)
1295 LET col1 = CODE r0$(1) + CODE r1$(1) + CODE r2$(1)
1298 LET col2 = CODE r0$(2) + CODE r1$(2) + CODE r2$(2)
1300 FOR x = 2 TO width+1
1305 LET centre = CODE r1$(x)
1310 LET col3 = CODE r0$(x+1) + CODE r1$(x+1) + CODE r2$(x+1)
1315 LET neighbors = col1 + col2 + col3 - centre - 256
1320 LET alive = (neighbors = 3) OR (neighbors = 2 AND centre = 33)
1330 LET next_cells$(y, x) = CHR$ (32 + alive)
1340 IF centre = 32 + alive THEN GOTO 1370
1350 IF alive = 1 THEN PLOT x-2, y-2
1360 IF alive = 0 THEN UNPLOT x-2, y-2
1370 LET col1 = col2 : LET col2 = col3
1375 NEXT x
1380 NEXT y
1390 REM ### Update state ###
1400 FOR y = 2 TO height+1
1410 LET cells$(y) = next_cells$(y)
1420 NEXT y
1430 GOSUB 1460
1440 LET generation = generation + 1
1445 SLOW
1450 GOTO 1220
1460 REM ### Toroidal wrap around (ghost cells) ###
1470 LET cells$(1) = cells$(height+1)
1480 LET cells$(height+2) = cells$(2)
1490 FOR y = 1 TO height+2
1500 LET cells$(y, 1) = cells$(y, width+1)
1510 LET cells$(y, width+2) = cells$(y, 2)
1520 NEXT y
1530 RETURN
