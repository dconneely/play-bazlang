1000 REM ### 3D rotating torus (doughnut) ###
1010 PLOTMODE 8
1020 LET w = PLOTW
1030 BRIGHT 1
1040 LET h = PLOTH
1055 LET centre_x = w / 2
1060 LET centre_y = h / 2
1070 LET s = h * 0.47
1080 LET n1 = 12
1090 LET n2 = 8
1100 LET num_vertices = n1 * n2
1110 LET num_edges = num_vertices * 2
1120 DIM vertices(num_vertices, 3)
1130 DIM projected(num_vertices, 2)
1140 DIM edges(num_edges, 2)
1150 GO SUB 4000 : REM ### Init data ###
1160 LET a = 0 : LET b = 0 : LET c = 0
1170 LET cycles = 0 : LET start_t = FRAMES : LET cps = 0
2000 REM ### Main loop ###
2010 LET sin_a = SIN (a) : LET cos_a = COS (a)
2020 LET sin_b = SIN (b) : LET cos_b = COS (b)
2030 LET sin_c = SIN (c) : LET cos_c = COS (c)
2040 FOR i = 1 TO num_vertices
2050 LET x = vertices(i, 1) : LET y = vertices(i, 2) : LET z = vertices(i, 3)
2060 REM ### Rotate X ###
2070 LET y1 = y * cos_a - z * sin_a
2080 LET z1 = y * sin_a + z * cos_a
2090 REM ### Rotate Y ###
2100 LET x1 = x * cos_b + z1 * sin_b
2110 LET z2 = -x * sin_b + z1 * cos_b
2120 REM ### Rotate Z ###
2130 LET x2 = x1 * cos_c - y1 * sin_c
2140 LET y2 = x1 * sin_c + y1 * cos_c
2150 REM ### Project ###
2160 LET z_proj = z2 + 4
2170 LET projected(i, 1) = centre_x + (x2 / z_proj) * s
2180 LET projected(i, 2) = centre_y + (y2 / z_proj) * s
2190 NEXT i
3000 FAST
3010 CLS
3020 LET col = INT (a)
3030 LET col = 1 + col - 7 * INT (col / 7)
3040 INK col
3050 FOR i = 1 TO num_edges
3060 LET e1 = edges(i, 1) : LET e2 = edges(i, 2)
3070 LET x1 = projected(e1, 1) : LET y1 = projected(e1, 2)
3080 LET x2 = projected(e2, 1) : LET y2 = projected(e2, 2)
3090 PLOT x1, y1 : DRAW x2 - x1, y2 - y1
3100 NEXT i
3110 LET a = a + 0.05
3120 LET b = b + 0.03
3130 LET c = c + 0.02
3140 LET cycles = cycles + 1
3150 LET now = FRAMES
3160 IF now - start_t < 50 THEN GO TO 3180
3170 LET cps = INT (cycles * 50 / (now - start_t)) : LET cycles = 0 : LET start_t = now
3180 PRINT AT 0, 0; INK 8; PAPER 8; "BazLang 3D Torus - "; num_vertices; " vertices, "; num_edges; " edges, "; cps; " CPS    "
3190 SLOW
3200 GO TO 2000
4000 REM ### Init torus vertices and edges ###
4010 PRINT INK 8; PAPER 8; "Generating mesh..."
4020 LET r1 = 1.5
4030 LET r2 = 0.6
4040 LET idx = 1
4050 FOR i = 0 TO n1 - 1
4060 LET a1 = i * (2 * PI / n1)
4070 LET ca1 = COS (a1) : LET sa1 = SIN (a1)
4080 FOR j = 0 TO n2 - 1
4090 LET a2 = j * (2 * PI / n2)
4100 LET ca2 = COS (a2) : LET sa2 = SIN (a2)
4110 LET vertices(idx, 1) = (r1 + r2 * ca2) * ca1
4120 LET vertices(idx, 2) = (r1 + r2 * ca2) * sa1
4130 LET vertices(idx, 3) = r2 * sa2
4140 LET idx = idx + 1
4150 NEXT j
4160 NEXT i
4170 LET idx = 1
4180 FOR i = 0 TO n1 - 1
4190 FOR j = 0 TO n2 - 1
4200 LET v1 = i * n2 + j + 1
4210 LET m1 = j + 1
4220 IF m1 >= n2 THEN LET m1 = m1 - n2
4230 LET v2 = i * n2 + m1 + 1
4240 LET m2 = i + 1
4250 IF m2 >= n1 THEN LET m2 = m2 - n1
4260 LET v3 = m2 * n2 + j + 1
4270 LET edges(idx, 1) = v1 : LET edges(idx, 2) = v2
4280 LET edges(idx + 1, 1) = v1 : LET edges(idx + 1, 2) = v3
4290 LET idx = idx + 2
4300 NEXT j
4310 NEXT i
4320 PAPER 8 : INK 8 : CLS
4330 RETURN
