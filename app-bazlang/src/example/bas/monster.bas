1000 REM ### 3D Monster Maze ###
1010 REM ### ZX81 classic reconstructed in BazLang BASIC ###
1020 PLOTMODE 2 : CLS
1030 DIM c(7) : DIM w(10) : REM c() is corridor perspective widths, w() is wall perspective heights
1040 FOR i = 1 TO 7 : READ c(i) : NEXT i
1050 FOR i = 1 TO 10 : READ w(i) : NEXT i
1060 DATA 1, 2, 5, 7, 9, 10, 11
1070 DATA 1, 2, 3, 4, 5, 6, 7, 8, 9, 10
1080 LET score = 0 : LET high = 0
1090 REM ### Ringmaster Text Data ###
1100 DATA "  ROLL UP,ROLL UP,", "SEE THE AMAZING", "TYRANNOSAURUS REX", "KING OF THE DINOSAURS", "IN HIS LAIR."
1110 DATA "PERFECTLY PRESERVED", "IN SILICON SINCE", "PREHISTORIC TIMES,HE", "IS BROUGHT TO YOU FOR", "YOUR ENTERTAINMENT", "AND EXHILARATION."
1120 DATA "  IF YOU DARE TO", "ENTER HIS LAIR,YOU DO", "SO AT YOUR OWN RISK."
1130 DATA "THE MANAGEMENT ACCEPT", "NO RESPONSIBILITY FOR", "THE HEALTH AND SAFETY", "OF THE ADVENTURER WHO", "ENTERS HIS REALM.THE", "MANAGEMENT ADVISE", "THAT THIS IS NOT A", "GAME FOR THOSE OF A", "NERVOUS DISPOSITION."
1140 DATA " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " ", " "
1150 REM ### Title Screen - Ringmaster Pitch ###
1160 CLS
1170 PRINT AT 2, 5; "███████▀▀▀▀█████████"
1180 PRINT AT 3, 5; "███████    █████████"
1190 PRINT AT 4, 5; "████▄▄------▄▄🮑🮑████"
1200 PRINT AT 5, 5; "██████ +()+ ██🮒🮒  ██"
1210 PRINT AT 6, 5; "██████▄ == ▄███▀  ██"
1220 PRINT AT 7, 5; "█████▀ ▀▄▄▀ ▀██   ▀█"
1230 PRINT AT 8, 5; "██▀           I    █"
1240 PRINT AT 9, 5; "█       ▄          █"
1250 PRINT AT 10, 5; "█  I    ██   ▄     █"
1260 PRINT AT 11, 5; "█  I   ▄▀ █   ▀▄▄▄██"
1270 PRINT AT 12, 5; "█  I  ▄▀**▀▄   █████"
1280 PRINT AT 13, 5; "█  ▄▄▀******▀▄ █████"
1290 PRINT AT 14, 5; "█  I**********▀█████"
1300 PRINT AT 15, 5; "█▄ +********** █████"
1310 PRINT AT 16, 5; "██▄ ****I ****▀█████"
1320 PRINT AT 17, 5; "███ ****I ****██████"
1330 PRINT AT 18, 5; "███ ****I ****██████"
1340 PRINT AT 19, 5; "███ ****█▄****██████"
1350 PRINT AT 20, 5; "███ ****██****██████"
1360 PRINT AT 21, 5; "████** ▄██** ▄██████"
1370 PRINT AT 22, 5; "▀▀▀▀▀  ▀▀▀  ▀▀▀▀▀▀▀▀"
1380 DIM sc$(24, 40)
1390 RESTORE 1100
1400 FOR l = 1 TO 35
1410 READ line$
1420 FOR r = 1 TO 22 : LET sc$(r) = sc$(r + 2) : NEXT r
1430 LET sc$(23) = "" : LET sc$(24) = line$
1440 FAST
1450 FOR r = 1 TO 24
1460 PRINT AT r, 32; "                                        "
1470 PRINT AT r, 32; sc$(r)
1480 NEXT r
1490 SLOW
1500 PAUSE 10
1510 LET k$ = INKEY$ : IF k$ <> "" THEN LET l = 35
1520 NEXT l
1530 REM Wait for final key to go to controls
1540 LET k$ = INKEY$ : IF k$ = "" THEN GO TO 1540
1550 REM ### Controls Screen ###
1560 CLS
1570 PRINT AT 5, 0; "THE ONLY CONTROLS"
1580 PRINT AT 6, 0; "YOU REQUIRE ARE:-"
1590 PRINT AT 8, 2; "5....TURN LEFT"
1600 PRINT AT 10, 2; "6....MOVE FORWARD"
1610 PRINT AT 12, 2; "8....TURN RIGHT"
1620 PRINT AT 16, 0; "PRESS ANY KEY TO START..."
1630 LET k$ = INKEY$ : IF k$ = "" THEN GO TO 1630
2000 REM ### Loading Screen ###
2010 CLS
2020 REM Animate loading screen
2030 FOR a = 1 TO 4
2040 CLS
2050 IF a / 2 = INT (a / 2) THEN PRINT AT 2, 5; "███████▀▀▀▀█████████" : PRINT AT 3, 5; "███████    █████████" : PRINT AT 4, 5; "████▄▄------▄▄🮑🮑████" : PRINT AT 5, 5; "██████ +()+ ██🮒🮒  ██" : PRINT AT 6, 5; "██████▄ == ▄███▀  ██" : PRINT AT 7, 5; "█████▀ ▀▄▄▀ ▀██   ▀█" : PRINT AT 8, 5; "██▀           I    █" : PRINT AT 9, 5; "█       ▄          █" : PRINT AT 10, 5; "█  I    ██   ▄     █" : PRINT AT 11, 5; "█  I   ▄▀ █   ▀▄▄▄██" : PRINT AT 12, 5; "█  I  ▄▀**▀▄   █████" : PRINT AT 13, 5; "█  ▄▄▀******▀▄ █████" : PRINT AT 14, 5; "█  I**********▀█████" : PRINT AT 15, 5; "█▄ +********** █████" : PRINT AT 16, 5; "██▄ ****I ****▀█████" : PRINT AT 17, 5; "███ ****I ****██████" : PRINT AT 18, 5; "███ ****I ****██████" : PRINT AT 19, 5; "███ ****█▄****██████" : PRINT AT 20, 5; "███ ****██****██████" : PRINT AT 21, 5; "████** ▄██** ▄██████" : PRINT AT 22, 5; "▀▀▀▀▀  ▀▀▀  ▀▀▀▀▀▀▀▀"
2060 IF a / 2 <> INT (a / 2) THEN PRINT AT 2, 5; "████████████████████" : PRINT AT 3, 5; "████████████████████" : PRINT AT 4, 5; "████████████████████" : PRINT AT 5, 5; "████████🮑🮑██████████" : PRINT AT 6, 5; "██████🮐🮐🮐🮐🮐🮐████████" : PRINT AT 7, 5; "██████🮐🮐🮐🮐🮐🮐████████" : PRINT AT 8, 5; "██▀   ▀█🮒🮒█▀    ████" : PRINT AT 9, 5; "█       ()       ███" : PRINT AT 10, 5; "█    ▄▄▀▀▀▄▄ ▄   ███" : PRINT AT 11, 5; "█  I█       ▀█   ███" : PRINT AT 12, 5; "█  I█      ▄▀   ▄███" : PRINT AT 13, 5; "█  ▄▄▀▄▄ ▄▀  ▄▄█████" : PRINT AT 14, 5; "█  I****▀▄▄▄▀ ▀█████" : PRINT AT 15, 5; "█▄ +********** █████" : PRINT AT 16, 5; "██▄ ****I ****▄█████" : PRINT AT 17, 5; "███ ****I ****██████" : PRINT AT 18, 5; "███ ****I ****██████" : PRINT AT 19, 5; "███ ****█▄****██████" : PRINT AT 20, 5; "███ ****██****██████" : PRINT AT 21, 5; "████** ▄██** ▄██████" : PRINT AT 22, 5; "▀▀▀▀▀  ▀▀▀  ▀▀▀▀▀▀▀▀"
2070 PRINT AT 5, 32; "  THE MISTS OF TIME"
2080 PRINT AT 7, 32; "WILL PASS OVER YOU"
2090 PRINT AT 9, 32; "FOR ABOUT  5 SECONDS"
2100 PRINT AT 11, 32; "WHILE TRANSPORTING"
2110 PRINT AT 13, 32; "YOU TO THE LAIR OF"
2120 PRINT AT 15, 32; "TYRANNOSAURUS REX."
2130 PRINT AT 17, 32; "  BEST OF LUCK....."
2140 PAUSE 60
2150 NEXT a
3000 REM ### Maze Generation ###
3010 CLS
3020 PRINT AT 10, 5; "CARVING THE LAIR... STAND BACK"
3030 DIM m(18, 16)
3040 FOR y = 1 TO 16 : FOR x = 1 TO 18 : LET m(x, y) = 1 : NEXT x : NEXT y
3050 DIM sx(150) : DIM sy(150)
3060 LET sp = 1 : LET sx(1) = 16 : LET sy(1) = 15 : LET m(16, 15) = 0
3070 IF sp = 0 THEN GO TO 3240
3080 LET cx = sx(sp) : LET cy = sy(sp)
3090 LET found = 0 : LET tries = 0
3100 LET d = INT (RND * 4) : LET tries = tries + 1
3110 IF tries > 10 THEN LET sp = sp - 1 : GO TO 3070
3120 LET dx = 0 : LET dy = 0
3130 IF d = 0 THEN LET dy = -2
3140 IF d = 1 THEN LET dx = 2
3150 IF d = 2 THEN LET dy = 2
3160 IF d = 3 THEN LET dx = -2
3170 LET nx = cx + dx : LET ny = cy + dy
3180 IF nx < 2 OR nx > 17 OR ny < 2 OR ny > 15 THEN GO TO 3100
3190 IF m(nx, ny) = 0 THEN GO TO 3100
3200 LET m(cx + dx / 2, cy + dy / 2) = 0
3210 LET m(nx, ny) = 0
3220 LET sp = sp + 1 : LET sx(sp) = nx : LET sy(sp) = ny
3230 GO TO 3070
3240 REM Exit placement
3250 LET ex = 9 : LET ey = 2
3260 LET m(ex, 2) = 0 : LET m(ex, 1) = 0
4000 REM ### Game Reset ###
4010 LET px = 16 : LET py = 15 : LET pd = 1 : REM Start at south-east, facing West (like original)
4020 LET rx = 0 : LET ry = 0
4030 FOR ry = 2 TO 15 : FOR rx = 6 TO 11
4040 IF m(rx, ry) = 0 AND rx <> ex THEN LET rx = rx : LET ry = ry : GO TO 4070
4050 NEXT rx : NEXT ry
4060 LET rx = 6 : LET ry = 3 : REM Fallback
4070 REM Rex start found
4080 LET caught = 0 : LET escaped = 0 : LET pm = 0
4090 LET stat$ = "REX LIES IN WAIT"
5000 REM ### Main Game Loop ###
5010 GO SUB 7000 : REM 3D Render
5020 GO SUB 10000 : REM Rex Render (on top)
5030 GO SUB 21000 : REM Exit Pattern (on top)
5040 GO SUB 30000 : REM HUD / Status Messages
5050 IF caught = 1 THEN GO TO 6000
5060 IF escaped = 1 THEN GO TO 6180
5070 LET k$ = UINKEY$ : IF k$ <> "" THEN FOR fl = 1 TO 200 : LET d$ = UINKEY$ : NEXT fl
5080 LET pm = 0
5090 IF k$ = "5" OR k$ = CHR$ (27) + "[D" OR k$ = CHR$ (27) + "OD" THEN LET pd = pd + 1 : IF pd > 3 THEN LET pd = 0
5100 IF k$ = "5" OR k$ = CHR$ (27) + "[D" OR k$ = CHR$ (27) + "OD" THEN LET pm = 1
5110 IF k$ = "8" OR k$ = CHR$ (27) + "[C" OR k$ = CHR$ (27) + "OC" THEN LET pd = pd - 1 : IF pd < 0 THEN LET pd = 3
5120 IF k$ = "8" OR k$ = CHR$ (27) + "[C" OR k$ = CHR$ (27) + "OC" THEN LET pm = 1
5130 IF k$ = "6" OR k$ = CHR$ (27) + "[A" OR k$ = CHR$ (27) + "OA" THEN GO SUB 5160 : REM Step Forward
5140 GO SUB 31000 : REM Rex AI Tick
5150 PAUSE 5 : GO TO 5010
5160 REM ### Step Forward Subroutine ###
5170 LET dx = 0 : LET dy = 0
5180 IF pd = 0 THEN LET dy = -1
5190 IF pd = 1 THEN LET dx = -1
5200 IF pd = 2 THEN LET dy = 1
5210 IF pd = 3 THEN LET dx = 1
5220 LET nx = px + dx : LET ny = py + dy
5230 IF nx < 1 OR nx > 18 OR ny < 1 OR ny > 16 THEN RETURN
5240 IF m(nx, ny) = 1 THEN RETURN
5250 LET px = nx : LET py = ny : LET pm = 1
5260 IF px = ex AND py = 1 THEN LET escaped = 1
5270 RETURN
6000 REM ### Caught Screen ###
6010 FOR i = 1 TO 5 : CLS : PAUSE 5 : PRINT AT 5, 5; "REX HAS CAUGHT YOU!" : PAUSE 5 : NEXT i
6020 CLS
6030 PRINT AT 5, 5; "REX HAS CAUGHT YOU!"
6040 PRINT AT 7, 5; "SENTENCED TO ROAM THE MAZE FOREVER"
6050 PRINT AT 9, 5; "FINAL SCORE: "; score
6060 IF score > high THEN LET high = score
6070 PRINT AT 11, 5; "HIGH SCORE:  "; high
6080 PRINT AT 14, 5; "PRESS 'A' TO APPEAL SENTENCE (A/N)?"
6090 LET k$ = INKEY$
6100 IF k$ = "a" OR k$ = "A" THEN GO TO 6130
6110 IF k$ = "n" OR k$ = "N" THEN GO TO 6260
6120 GO TO 6090
6130 REM Appeal logic
6140 IF RND < 0.5 THEN GO TO 6160
6150 CLS : PRINT AT 10, 5; "REX REJECTS YOUR APPEAL!" : PAUSE 60 : GO TO 6000
6160 CLS : PRINT AT 10, 5; "REX ACCEPTS YOUR APPEAL!" : PAUSE 60
6170 LET score = 0 : GO TO 3000
6180 REM ### Escaped Screen ###
6190 LET score = score + 200
6200 CLS
6210 PRINT AT 8, 5; "ANOTHER VICTIM ESCAPES REX!"
6220 PRINT AT 10, 5; "BONUS +200 POINTS"
6230 PRINT AT 12, 5; "PREPARE FOR THE NEXT LAIR..."
6240 PAUSE 100
6250 GO TO 3000
6260 REM ### Quit Game ###
6270 CLS : PRINT AT 10, 5; "THANK YOU FOR PLAYING!" : STOP
7000 REM ### 3D Viewport Drawing ###
7010 FAST
7020 FOR y = 2 TO 21 : PRINT AT y, 4; "                                          " : NEXT y
7030 FOR z = 5 TO 0 STEP -1
7040 LET dx = 0 : LET dy = 0
7050 IF pd = 0 THEN LET dy = -1
7060 IF pd = 1 THEN LET dx = -1
7070 IF pd = 2 THEN LET dy = 1
7080 IF pd = 3 THEN LET dx = 1
7090 LET rdx = -dy : LET rdy = dx
7100 LET cx = px + z * dx : LET cy = py + z * dy
7110 LET lx = cx - rdx : LET ly = cy - rdy
7120 LET rx2 = cx + rdx : LET ry2 = cy + rdy
7130 LET k = 1 : IF cx >= 1 AND cx <= 18 AND cy >= 1 AND cy <= 16 THEN LET k = m(cx, cy)
7140 LET l = 1 : IF lx >= 1 AND lx <= 18 AND ly >= 1 AND ly <= 16 THEN LET l = m(lx, ly)
7150 LET r = 1 : IF rx2 >= 1 AND rx2 <= 18 AND ry2 >= 1 AND ry2 <= 16 THEN LET r = m(rx2, ry2)
7160 LET c1 = c(z + 1) : LET c2 = c(z + 2) - 1
7170 FOR i = c1 TO c2
7180 LET h = w(i)
7190 IF 2 + h < 21 - h THEN LET t$ = "▄ " : LET b$ = "▀ " : IF l = 0 THEN LET t$ = "  " : LET b$ = "  "
7200 IF 2 + h < 21 - h THEN PRINT AT 2 + h, 2 * i + 2; t$; AT 21 - h, 2 * i + 2; b$
7210 IF 2 + h < 21 - h AND l = 1 THEN FOR y = 3 + h TO 20 - h : PRINT AT y, 2 * i + 2; "██" : NEXT y
7220 IF 2 + h < 21 - h AND l = 0 THEN FOR y = 3 + h TO 20 - h : PRINT AT y, 2 * i + 2; "  " : NEXT y
7230 IF 2 + h < 21 - h THEN LET t$ = " ▄" : LET b$ = " ▀" : IF r = 0 THEN LET t$ = "  " : LET b$ = "  "
7240 IF 2 + h < 21 - h THEN PRINT AT 2 + h, 46 - 2 * i; t$; AT 21 - h, 46 - 2 * i; b$
7250 IF 2 + h < 21 - h AND r = 1 THEN FOR y = 3 + h TO 20 - h : PRINT AT y, 46 - 2 * i; "██" : NEXT y
7260 IF 2 + h < 21 - h AND r = 0 THEN FOR y = 3 + h TO 20 - h : PRINT AT y, 46 - 2 * i; "  " : NEXT y
7270 NEXT i
7280 IF k = 1 THEN LET h = w(c1) : FOR i = c1 TO 22 - c1 : FOR y = 2 + h TO 21 - h : PRINT AT y, 2 * i + 2; "▒▒" : NEXT y : NEXT i
7290 NEXT z
7300 RETURN
10000 REM ### Draw Rex Sprite Subroutine ###
10010 LET rd = 0
10020 LET dx = 0 : LET dy = 0
10030 IF pd = 0 THEN LET dy = -1
10040 IF pd = 1 THEN LET dx = -1
10050 IF pd = 2 THEN LET dy = 1
10060 IF pd = 3 THEN LET dx = 1
10070 FOR z = 1 TO 5
10080 LET cx = px + z * dx : LET cy = py + z * dy
10090 IF cx < 1 OR cx > 18 OR cy < 1 OR cy > 16 THEN GO TO 10130
10100 IF m(cx, cy) = 1 THEN GO TO 10130
10110 IF cx = rx AND cy = ry THEN LET rd = z
10120 NEXT z
10130 IF rd = 0 THEN RETURN
10140 FAST
10150 LET step_type = INT (RND * 2)
10160 IF rd = 1 AND step_type = 0 THEN GO TO 11000
10170 IF rd = 1 AND step_type = 1 THEN GO TO 12000
10180 IF rd = 2 AND step_type = 0 THEN GO TO 13000
10190 IF rd = 2 AND step_type = 1 THEN GO TO 14000
10200 IF rd = 3 AND step_type = 0 THEN GO TO 15000
10210 IF rd = 3 AND step_type = 1 THEN GO TO 16000
10220 IF rd = 4 AND step_type = 0 THEN GO TO 17000
10230 IF rd = 4 AND step_type = 1 THEN GO TO 18000
10240 IF rd = 5 AND step_type = 0 THEN GO TO 19000
10250 IF rd = 5 AND step_type = 1 THEN GO TO 20000
10260 RETURN
11000 REM Large Rex (Distance 1 left step, L42E9/$4D)
11010 PRINT AT 2, 17; " ▄▄▄▄▄▄▄▄▄▄ "
11020 PRINT AT 3, 15; " ▄████████████▄ "
11030 PRINT AT 4, 13; " ▄████████████████  "
11040 PRINT AT 5, 11; "  ▄███()████████()███ "
11050 PRINT AT 6, 11; "  ███████████████████ "
11060 PRINT AT 7, 11; " ▄████████▀██▀███████ "
11070 PRINT AT 8, 9; " ▄████🮑🮑████████████🮑🮑█▄  "
11080 PRINT AT 9, 7; "  █████ 🮎🮎\/\/🮑🮑🮑🮑\/\/🮐🮐███ "
11090 PRINT AT 10, 7; " ███████🮏🮏    🮎🮎🮎🮎  🮏🮏🮒🮒████  "
11100 PRINT AT 11, 7; " ███████🮐🮐          🮐🮐██████▄ "
11110 PRINT AT 12, 7; " ███████🮒🮒🮐🮐        🮐🮐███████ "
11120 PRINT AT 13, 7; "  🮑🮑██████🮐🮐🮏🮏    🮏🮏🮒🮒███████ "
11130 PRINT AT 14, 7; "  🮒🮒🮑🮑██████🮒🮒🮐🮐🮐🮐🮒🮒█████████▄  "
11140 PRINT AT 15, 7; "  ▀█🮒🮒🮑🮑████████████████🮐🮐████  "
11150 PRINT AT 16, 9; " ▀██🮒🮒████████████████🮐🮐████  "
11160 PRINT AT 17, 9; "  ▀██████▀█████ ▄█████🮐🮐████  "
11170 PRINT AT 18, 11; " ██████ ██████████████🮐🮐█▀  "
11180 PRINT AT 19, 7; " ▄▄▄███████  ██████▀ ██████   "
11190 PRINT AT 20, 7; " ████████▀    ▀▀▀    ██████▄▄ "
11200 PRINT AT 21, 27; "  ████████▄ "
11210 PRINT AT 22, 29; " ▀▀▀▀▀▀▀▀  "
11220 RETURN
12000 REM Large Rex (Distance 1 right step, L4214/$6F)
12010 PRINT AT 3, 19; "▄▄▄▄▄▄▄ "
12020 PRINT AT 4, 17; "▄██████████ "
12030 PRINT AT 5, 15; " █████████████"
12040 PRINT AT 6, 15; " ██▄ █████▄ █▀"
12050 PRINT AT 7, 13; " ▄█▀████████████▄ "
12060 PRINT AT 8, 11; "  ████ ▀██▄█▄██▀███▄"
12070 PRINT AT 9, 11; " █████▄  ▀▀▀▀▀ ▄████"
12080 PRINT AT 10, 11; "▄███████      ▄█████"
12090 PRINT AT 11, 9; " ▄███ █████▄▄▄▄██████▀"
12100 PRINT AT 12, 9; " ██▀ ███████████████▀ "
12110 PRINT AT 13, 9; " █▀ ▄████████████████ "
12120 PRINT AT 14, 11; "  ██████████████████▄ "
12130 PRINT AT 15, 11; " ▄████████████████████"
12140 PRINT AT 16, 11; " ████████████ ▀██████▀"
12150 PRINT AT 17, 11; "  ███████████████████ "
12160 PRINT AT 18, 13; " ████▀ ████▀▀▄████▄ "
12170 PRINT AT 19, 9; " ▄▄▄█████   ▀▀   ▀▀▀▀▀▀▀"
12180 PRINT AT 20, 9; " ▀▀▀▀▀▀▀  "
12190 RETURN
13000 REM Medium Rex (Distance 2 left step, L4194/$B0)
13010 PRINT AT 5, 19; " ▄█████▄"
13020 PRINT AT 6, 19; "█████████ "
13030 PRINT AT 7, 19; "██▄████▄█ "
13040 PRINT AT 8, 17; " █ ▀█████▀▄█  "
13050 PRINT AT 9, 17; "▄██  ▀▀▀ ▄███ "
13060 PRINT AT 10, 17; "████▄   ██████"
13070 PRINT AT 11, 17; "▀██████████ ███ "
13080 PRINT AT 12, 17; "  █████████▄ ▀██"
13090 PRINT AT 13, 17; "▄████████████▄▀▀"
13100 PRINT AT 14, 15; "▄███████████████"
13110 PRINT AT 15, 15; "████▀██████▀█████ "
13120 PRINT AT 16, 15; "▀███  ▀███▄█████▀ "
13130 PRINT AT 17, 13; "▄▄███▀   ▀███ ███▀  "
13140 PRINT AT 18, 27; "▀██▄▄ "
13150 RETURN
14000 REM Medium Rex (Distance 2 right step, L4138/$D2)
14010 PRINT AT 6, 21; " ▄▄▄▄▄"
14020 PRINT AT 7, 21; "█▀███▀█ "
14030 PRINT AT 8, 19; " ▄███████ "
14040 PRINT AT 9, 19; " █▄ ▀▀▀ █▄"
14050 PRINT AT 10, 19; "███▄  ▄███▄ "
14060 PRINT AT 11, 17; " ▄███████████ "
14070 PRINT AT 12, 17; " ██ ███████▀  "
14080 PRINT AT 13, 17; " ▀ █████████▄ "
14090 PRINT AT 14, 19; "████████████"
14100 PRINT AT 15, 19; "███████ ▄███"
14110 PRINT AT 16, 19; "▀███ ███▀███▄▄"
14120 PRINT AT 17, 17; "▄▄███ "
14130 RETURN
15000 REM Small Rex (Distance 3 left step, L40FD/$14)
15010 PRINT AT 8, 21; "▄████▄"
15020 PRINT AT 9, 19; " ██▄██▄█"
15030 PRINT AT 10, 19; " ▀▄▀▀▀▀▄"
15040 PRINT AT 11, 19; " ███▄▄███ "
15050 PRINT AT 12, 19; " ▀████████"
15060 PRINT AT 13, 19; " ▄███████ "
15070 PRINT AT 14, 19; "█████████ "
15080 PRINT AT 15, 17; " ▄▄██ ▀▀███ "
15090 PRINT AT 16, 25; "▀▀▀▀"
15100 RETURN
16000 REM Small Rex (Distance 3 right step, L40D9/$35)
16010 PRINT AT 9, 21; " ███▄ "
16020 PRINT AT 10, 21; " ▀██▀ "
16030 PRINT AT 11, 19; " ▄██▄▄█ "
16040 PRINT AT 12, 19; " █▀████▄"
16050 PRINT AT 13, 21; "██████"
16060 PRINT AT 14, 19; " ▄███▀▀▀▀ "
16070 RETURN
17000 REM Tiny Rex (Distance 4 left step, L40C0/$35)
17010 PRINT AT 9, 21; " ▄▄▄"
17020 PRINT AT 10, 21; " ▀█▀"
17030 PRINT AT 11, 21; "██▄██▄"
17040 PRINT AT 12, 21; "▀███▄▀"
17050 PRINT AT 13, 21; "▀▀▀▀█▄"
17060 RETURN
18000 REM Tiny Rex (Distance 4 right step, L40AE/$57)
18010 PRINT AT 10, 23; "▄▄"
18020 PRINT AT 11, 21; " ▄██"
18030 PRINT AT 12, 21; " ▄███ "
18040 PRINT AT 13, 21; " ▀▀ "
18050 RETURN
19000 REM Furthest Rex (Distance 5 left step, L40A6/$78)
19010 PRINT AT 11, 23; "██"
19020 PRINT AT 12, 23; "██"
19030 RETURN
20000 REM Furthest Rex (Distance 5 right step, L42E1/$78)
20010 PRINT AT 11, 23; "██"
20020 PRINT AT 12, 23; "██"
20030 RETURN
21000 REM ### Exit Pattern Subroutine ###
21010 LET ed = 0
21020 LET dx = 0 : LET dy = 0
21030 IF pd = 0 THEN LET dy = -1
21040 IF pd = 1 THEN LET dx = -1
21050 IF pd = 2 THEN LET dy = 1
21060 IF pd = 3 THEN LET dx = 1
21070 FOR z = 1 TO 5
21080 LET cx = px + z * dx : LET cy = py + z * dy
21090 IF cx < 1 OR cx > 18 OR cy < 1 OR cy > 16 THEN GO TO 21130
21100 IF m(cx, cy) = 1 THEN GO TO 21130 : REM Wall blocks exit
21110 IF cx = ex AND (cy = ey OR cy = 1) THEN LET ed = z : GO TO 21130
21120 NEXT z
21130 IF ed = 0 THEN RETURN
21140 LET nr = 6 - ed
21150 FAST
21160 FOR r = 1 TO nr
21170 LET ch$ = "██"
21180 IF r / 2 = INT (r / 2) THEN LET ch$ = "🮐🮐"
21190 FOR c = 11 - r TO 11 + r
21200 IF 11 - r >= 2 THEN PRINT AT 11 - r, 2 * c + 2; ch$
21210 IF 11 + r <= 21 THEN PRINT AT 11 + r, 2 * c + 2; ch$
21220 NEXT c
21230 FOR y = 12 - r TO 10 + r
21240 IF y >= 2 AND y <= 21 THEN PRINT AT y, 2 * (11 - r) + 2; ch$
21250 IF y >= 2 AND y <= 21 THEN PRINT AT y, 2 * (11 + r) + 2; ch$
21260 NEXT y
21270 NEXT r
21280 RETURN
30000 REM ### HUD Display ###
30010 FAST
30020 GO SUB 32000 : REM Calculate Status Message
30030 PRINT AT 2, 48; "3D MONSTER MAZE"
30040 PRINT AT 4, 48; "SCORE: "; score; "    HIGH: "; high
30050 PRINT AT 6, 48; "STATUS MESSAGE:"
30060 PRINT AT 8, 48; "                                "
30070 PRINT AT 8, 48; stat$
30080 PRINT AT 11, 48; "EXIT IN NORTH AT COL 9"
30090 PRINT AT 13, 48; "KEYS: 5=LEFT, 6=FORWARD, 8=RIGHT"
30100 LET pdir$ = "NORTH"
30110 IF pd = 1 THEN LET pdir$ = "WEST"
30120 IF pd = 2 THEN LET pdir$ = "SOUTH"
30130 IF pd = 3 THEN LET pdir$ = "EAST"
30140 PRINT AT 15, 48; "POS:"; px; ","; py; " ("; pdir$; ") REX:"; rx; ","; ry; "    "
30150 SLOW : RETURN
31000 REM ### Rex AI Tick Subroutine ###
31010 LET pmoved = 0 : LET roll = RND
31020 IF pm = 1 AND roll < 0.30 THEN LET pmoved = 1
31030 IF pm = 0 AND roll < 0.15 THEN LET pmoved = 1
31040 IF pmoved = 0 THEN GO TO 31200
31050 LET hx = px - rx : LET hy = py - ry
31060 IF hx = 0 AND hy = 0 THEN LET caught = 1 : RETURN
31070 LET mx_dir = 0 : IF ABS (hx) > ABS (hy) THEN LET mx_dir = 1
31080 IF mx_dir = 1 THEN GO TO 31150
31090 REM Try vertical first
31100 LET ny = ry : IF hy <> 0 THEN LET ny = ry + SGN (hy)
31110 IF ny >= 1 AND ny <= 16 THEN IF m(rx, ny) = 0 THEN LET ry = ny : GO TO 31200
31120 LET nx = rx : IF hx <> 0 THEN LET nx = rx + SGN (hx)
31130 IF nx >= 1 AND nx <= 18 THEN IF m(nx, ry) = 0 THEN LET rx = nx : GO TO 31200
31140 GO TO 31200
31150 REM Try horizontal first
31160 LET nx = rx : IF hx <> 0 THEN LET nx = rx + SGN (hx)
31170 IF nx >= 1 AND nx <= 18 THEN IF m(nx, ry) = 0 THEN LET rx = nx : GO TO 31200
31180 LET ny = ry : IF hy <> 0 THEN LET ny = ry + SGN (hy)
31190 IF ny >= 1 AND ny <= 16 THEN IF m(rx, ny) = 0 THEN LET ry = ny : GO TO 31200
31200 REM Check caught after move
31210 IF rx = px AND ry = py THEN LET caught = 1 : RETURN
31220 REM Score tracking
31230 IF pm = 1 AND (ABS (px - rx) + ABS (py - ry)) < 8 THEN LET score = score + 5
31240 RETURN
32000 REM ### Calculate Status Message Subroutine ###
32010 LET stat$ = "REX LIES IN WAIT"
32020 LET dist = ABS (px - rx) + ABS (py - ry)
32030 IF dist > 8 THEN LET stat$ = "HE IS HUNTING YOU" : RETURN
32040 IF dist = 7 OR dist = 8 THEN LET stat$ = "FOOTSTEPS APPROACHING" : RETURN
32050 REM Check Line Of Sight (LOS)
32060 LET los = 0
32070 IF px <> rx THEN GO TO 32110
32080 LET los = 1 : LET dstp = 1 : IF ry < py THEN LET dstp = -1
32090 FOR y = py + dstp TO ry - dstp STEP dstp : IF m(px, y) = 1 THEN LET los = 0
32100 NEXT y
32110 IF py <> ry THEN GO TO 32150
32120 LET los = 1 : LET dstp = 1 : IF rx < px THEN LET dstp = -1
32130 FOR x = px + dstp TO rx - dstp STEP dstp : IF m(x, py) = 1 THEN LET los = 0
32140 NEXT x
32150 IF los = 0 THEN RETURN
32160 REM We have LOS. Check if visible on screen (RD > 0)
32170 IF rd > 0 THEN LET stat$ = "" : RETURN
32180 REM Check if behind
32190 LET behind = 0
32200 IF pd = 0 AND ry > py THEN LET behind = 1
32210 IF pd = 1 AND rx > px THEN LET behind = 1
32220 IF pd = 2 AND ry < py THEN LET behind = 1
32230 IF pd = 3 AND rx < px THEN LET behind = 1
32240 REM Check if beside
32250 LET beside = 0
32260 IF px = rx AND (pd = 0 OR pd = 2) THEN LET beside = 1
32270 IF py = ry AND (pd = 1 OR pd = 3) THEN LET beside = 1
32280 IF dist < 3 AND behind = 1 THEN LET stat$ = "RUN HE IS BEHIND YOU" : RETURN
32290 IF dist < 3 AND beside = 1 THEN LET stat$ = "RUN HE IS BESIDE YOU" : RETURN
32300 IF dist >= 3 AND dist <= 6 THEN LET stat$ = "REX HAS SEEN YOU" : RETURN
32310 RETURN
