1000 REM ### 3D rotating Rubik's cube ###
1010 PLOTMODE 8
1020 LET w = PLOTW : LET h = PLOTH
1030 LET center_x = w / 2 : LET center_y = h / 2
1040 LET s = h * 0.7
1050 LET num_vertices = 64
1060 LET num_edges = 36
1070 DIM vertices(num_vertices, 3)
1080 DIM projected(num_vertices, 2)
1090 DIM old_proj(num_vertices, 2)
1100 DIM edges(num_edges, 2)
1110 GO SUB 4000 : REM ### Init data ###
1120 LET a = 0 : LET b = 0 : LET c = 0
1130 LET u = 0
1140 LET cycles = 0 : LET start_t = FRAMES : LET cps = 0
2000 REM ### Main loop ###
2010 LET sin_a = SIN (a) : LET cos_a = COS (a)
2020 LET sin_b = SIN (b) : LET cos_b = COS (b)
2030 LET sin_c = SIN (c) : LET cos_c = COS (c)
2040 FOR i = 1 TO num_vertices
2050 LET x = vertices(i, 1) : LET y = vertices(i, 2) : LET z = vertices(i, 3)
2060 LET y1 = y * cos_a - z * sin_a
2070 LET z1 = y * sin_a + z * cos_a
2080 LET x1 = x * cos_b + z1 * sin_b
2090 LET z2 = -x * sin_b + z1 * cos_b
2100 LET x2 = x1 * cos_c - y1 * sin_c
2110 LET y2 = x1 * sin_c + y1 * cos_c
2120 LET z_proj = z2 + 4
2130 LET projected(i, 1) = center_x + (x2 / z_proj) * s
2140 LET projected(i, 2) = center_y + (y2 / z_proj) * s
2150 NEXT i
3000 FAST
3010 REM ### Erase old lines ###
3020 IF u = 0 THEN GO TO 3110
3030 FOR i = 1 TO num_edges
3040 LET e1 = edges(i, 1) : LET e2 = edges(i, 2)
3050 LET x1 = old_proj(e1, 1) : LET y1 = old_proj(e1, 2)
3060 LET x2 = old_proj(e2, 1) : LET y2 = old_proj(e2, 2)
3070 PLOT x1, y1
3080 UNDRAW x2 - x1, y2 - y1
3090 NEXT i
3100 REM ### Draw new lines ###
3110 FOR i = 1 TO num_edges
3120 LET e1 = edges(i, 1) : LET e2 = edges(i, 2)
3130 LET x1 = projected(e1, 1) : LET y1 = projected(e1, 2)
3140 LET x2 = projected(e2, 1) : LET y2 = projected(e2, 2)
3150 PLOT x1, y1
3160 DRAW x2 - x1, y2 - y1
3170 NEXT i
3180 FOR i = 1 TO num_vertices
3190 LET old_proj(i, 1) = projected(i, 1) : LET old_proj(i, 2) = projected(i, 2)
3200 NEXT i
3210 LET u = 1
3220 SLOW
3230 LET a = a + 0.05
3240 LET b = b + 0.03
3250 LET c = c + 0.02
3260 LET cycles = cycles + 1
3270 LET now = FRAMES
3280 IF now - start_t < 50 THEN GO TO 3300
3290 LET cps = INT (cycles * 50 / (now - start_t)) : LET cycles = 0 : LET start_t = now
3300 PRINT AT 0, 0; "Bazlang 3D Cube - "; num_vertices; " vertices, "; num_edges; " edges, "; cps; " CPS    "
3310 GO TO 2000
4000 RESTORE 5000
4010 FOR i = 1 TO num_vertices
4020 READ vertices(i, 1), vertices(i, 2), vertices(i, 3)
4030 NEXT i
4040 FOR i = 1 TO num_edges
4050 READ edges(i, 1), edges(i, 2)
4060 NEXT i
4070 CLS
4080 RETURN
5000 REM ### Vertex data ###
5010 DATA -1, -1, -1, -1, -1, -0.333333, -1, -1, 0.333333, -1, -1, 1
5020 DATA -1, -0.333333, -1, -1, -0.333333, -0.333333, -1, -0.333333, 0.333333, -1, -0.333333, 1
5030 DATA -1, 0.333333, -1, -1, 0.333333, -0.333333, -1, 0.333333, 0.333333, -1, 0.333333, 1
5040 DATA -1, 1, -1, -1, 1, -0.333333, -1, 1, 0.333333, -1, 1, 1
5050 DATA -0.333333, -1, -1, -0.333333, -1, -0.333333, -0.333333, -1, 0.333333, -0.333333, -1, 1
5060 DATA -0.333333, -0.333333, -1, -0.333333, -0.333333, -0.333333, -0.333333, -0.333333, 0.333333, -0.333333, -0.333333, 1
5070 DATA -0.333333, 0.333333, -1, -0.333333, 0.333333, -0.333333, -0.333333, 0.333333, 0.333333, -0.333333, 0.333333, 1
5080 DATA -0.333333, 1, -1, -0.333333, 1, -0.333333, -0.333333, 1, 0.333333, -0.333333, 1, 1
5090 DATA 0.333333, -1, -1, 0.333333, -1, -0.333333, 0.333333, -1, 0.333333, 0.333333, -1, 1
5100 DATA 0.333333, -0.333333, -1, 0.333333, -0.333333, -0.333333, 0.333333, -0.333333, 0.333333, 0.333333, -0.333333, 1
5110 DATA 0.333333, 0.333333, -1, 0.333333, 0.333333, -0.333333, 0.333333, 0.333333, 0.333333, 0.333333, 0.333333, 1
5120 DATA 0.333333, 1, -1, 0.333333, 1, -0.333333, 0.333333, 1, 0.333333, 0.333333, 1, 1
5130 DATA 1, -1, -1, 1, -1, -0.333333, 1, -1, 0.333333, 1, -1, 1
5140 DATA 1, -0.333333, -1, 1, -0.333333, -0.333333, 1, -0.333333, 0.333333, 1, -0.333333, 1
5150 DATA 1, 0.333333, -1, 1, 0.333333, -0.333333, 1, 0.333333, 0.333333, 1, 0.333333, 1
5160 DATA 1, 1, -1, 1, 1, -0.333333, 1, 1, 0.333333, 1, 1, 1
5170 REM ### Edge data ###
5180 DATA 1, 49, 2, 50, 3, 51, 4, 52, 5, 53, 8, 56, 9, 57, 12, 60
5190 DATA 13, 61, 14, 62, 15, 63, 16, 64, 1, 13, 2, 14, 3, 15, 4, 16
5200 DATA 17, 29, 20, 32, 33, 45, 36, 48, 49, 61, 50, 62, 51, 63, 52, 64
5210 DATA 1, 4, 5, 8, 9, 12, 13, 16, 17, 20, 29, 32, 33, 36, 45, 48
5220 DATA 49, 52, 53, 56, 57, 60, 61, 64
