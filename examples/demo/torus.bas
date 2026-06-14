1000 REM ### 3D rotating torus (donut) ###
1010 PLOTMODE 8
1020 LET w = PLOTW
1030 LET h = PLOTH
1040 LET center_x = w / 2
1050 LET center_y = h / 2
1060 LET s = h * 0.47
1070 LET n1 = 12
1080 LET n2 = 8
1090 LET num_vertices = n1 * n2
1100 LET num_edges = num_vertices * 2
1110 DIM vertices(num_vertices, 3)
1120 DIM projected(num_vertices, 2)
1130 DIM old_proj(num_vertices, 2)
1140 DIM edges(num_edges, 2)
1150 GOSUB 1810 : REM ### Init data ###
1160 LET a = 0 : LET b = 0 : LET c = 0
1170 LET u = 0 : REM ### First frame flag ###

1175 LET cycles = 0 : LET start_t = FRAMES : LET cps = 0
1180 REM ### Main loop ###
1190 LET sin_a = SIN(a) : LET cos_a = COS(a)
1200 LET sin_b = SIN(b) : LET cos_b = COS(b)
1210 LET sin_c = SIN(c) : LET cos_c = COS(c)
1220 FOR i = 1 TO num_vertices
1230 LET x = vertices(i, 1) : LET y = vertices(i, 2) : LET z = vertices(i, 3)
1240 REM ### Rotate X ###
1250 LET y1 = y * cos_a - z * sin_a
1260 LET z1 = y * sin_a + z * cos_a
1270 REM ### Rotate Y ###
1280 LET x1 = x * cos_b + z1 * sin_b
1290 LET z2 = -x * sin_b + z1 * cos_b
1300 REM ### Rotate Z ###
1310 LET x2 = x1 * cos_c - y1 * sin_c
1320 LET y2 = x1 * sin_c + y1 * cos_c
1330 REM ### Project ###
1340 LET z_proj = z2 + 4
1350 LET projected(i, 1) = center_x + (x2 / z_proj) * s
1360 LET projected(i, 2) = center_y + (y2 / z_proj) * s
1370 NEXT i

1380 FAST
1390 REM ### Erase old lines ###
1400 IF u = 0 THEN GOTO 1470
1420 FOR i = 1 TO num_edges
1425 LET e1 = edges(i, 1) : LET e2 = edges(i, 2)
1430 LET x1 = old_proj(e1, 1) : LET y1 = old_proj(e1, 2)
1440 LET x2 = old_proj(e2, 1) : LET y2 = old_proj(e2, 2)
1450 PLOT x1, y1 : UNDRAW x2 - x1, y2 - y1
1460 NEXT i

1470 REM ### Draw new lines ###
1490 FOR i = 1 TO num_edges
1495 LET e1 = edges(i, 1) : LET e2 = edges(i, 2)
1500 LET x1 = projected(e1, 1) : LET y1 = projected(e1, 2)
1510 LET x2 = projected(e2, 1) : LET y2 = projected(e2, 2)
1520 PLOT x1, y1 : DRAW x2 - x1, y2 - y1
1530 NEXT i

1540 REM ### Save old points ###
1550 FOR i = 1 TO num_vertices
1560 LET old_proj(i, 1) = projected(i, 1) : LET old_proj(i, 2) = projected(i, 2)
1570 NEXT i
1580 LET u = 1
1590 SLOW

1600 LET a = a + 0.05
1610 LET b = b + 0.03
1620 LET c = c + 0.02
1622 LET cycles = cycles + 1
1624 LET now = FRAMES
1626 IF now - start_t < 50 THEN GOTO 1630
1628 LET cps = INT(cycles * 50 / (now - start_t)) : LET cycles = 0 : LET start_t = now
1630 PRINT AT 0, 0; "Bazlang 3D Torus - " ; num_vertices ; " vertices, " ; num_edges ; " edges, "; cps; " CPS    "
1640 GOTO 1180


1810 REM ### Init torus vertices and edges ###
1820 PRINT "Generating mesh..."
1830 LET r1 = 1.5
1840 LET r2 = 0.6
1850 LET idx = 1
1860 FOR i = 0 TO n1 - 1
1870 LET a1 = i * (2 * PI / n1)
1880 LET ca1 = COS(a1) : LET sa1 = SIN(a1)
1890 FOR j = 0 TO n2 - 1
1900 LET a2 = j * (2 * PI / n2)
1910 LET ca2 = COS(a2) : LET sa2 = SIN(a2)
1920 LET vertices(idx, 1) = (r1 + r2 * ca2) * ca1
1930 LET vertices(idx, 2) = (r1 + r2 * ca2) * sa1
1940 LET vertices(idx, 3) = r2 * sa2
1950 LET idx = idx + 1
1960 NEXT j
1970 NEXT i

1980 LET idx = 1
1990 FOR i = 0 TO n1 - 1
2000 FOR j = 0 TO n2 - 1
2010 LET v1 = i * n2 + j + 1
2020 LET m1 = j + 1
2030 IF m1 >= n2 THEN LET m1 = m1 - n2
2040 LET v2 = i * n2 + m1 + 1
2050 LET m2 = i + 1
2060 IF m2 >= n1 THEN LET m2 = m2 - n1
2070 LET v3 = m2 * n2 + j + 1
2080 LET edges(idx, 1) = v1 : LET edges(idx, 2) = v2
2090 LET edges(idx + 1, 1) = v1 : LET edges(idx + 1, 2) = v3
2100 LET idx = idx + 2
2110 NEXT j
2120 NEXT i
2130 CLS
2140 RETURN
