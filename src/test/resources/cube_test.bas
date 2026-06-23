1000 REM ### 3D rotating Rubik's cube ###
1010 PLOTMODE 8
1020 LET w = PLOTW : LET h = PLOTH
1025 BRIGHT 1
1030 LET centre_x = w / 2 : LET centre_y = h / 2
1040 LET s = h * 0.7
1050 LET num_vertices = 8
1060 LET num_edges = 12
1070 DIM vertices(num_vertices, 3)
1080 DIM projected(num_vertices, 2)
1100 DIM edges(num_edges, 2)
1110 GO SUB 4000
1120 DIM colours(6)
1130 LET colours(1) = 1 : LET colours(2) = 2
1132 LET colours(3) = 7 : LET colours(4) = 4
1134 LET colours(5) = 6 : LET colours(6) = 3
1140 LET a = 0.5 : LET b = 0.5 : LET c = 0.2
2000 LET sin_a = SIN (a) : LET cos_a = COS (a)
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
2130 LET projected(i, 1) = centre_x + (x2 / z_proj) * s
2140 LET projected(i, 2) = centre_y + (y2 / z_proj) * s
2150 NEXT i
3000 FAST : CLS
3020 FOR i = 1 TO num_edges
3030 LET e1 = edges(i, 1) : LET e2 = edges(i, 2)
3040 LET x1 = projected(e1, 1) : LET y1 = projected(e1, 2)
3050 LET x2 = projected(e2, 1) : LET y2 = projected(e2, 2)
3060 INK colours((i - 6 * INT (i / 6)) + 1)
3070 PLOT x1, y1 : DRAW x2 - x1, y2 - y1
3080 NEXT i
3200 STOP
4000 RESTORE 5000
4010 FOR i = 1 TO num_vertices
4020 READ vertices(i, 1), vertices(i, 2), vertices(i, 3)
4030 NEXT i
4040 FOR i = 1 TO num_edges
4050 READ edges(i, 1), edges(i, 2)
4060 NEXT i
4070 CLS : RETURN
5000 DATA -1,-1,-1,  1,-1,-1,  1, 1,-1, -1, 1,-1
5010 DATA -1,-1, 1,  1,-1, 1,  1, 1, 1, -1, 1, 1
5020 DATA 1,2, 2,3, 3,4, 4,1
5030 DATA 5,6, 6,7, 7,8, 8,5
5040 DATA 1,5, 2,6, 3,7, 4,8
