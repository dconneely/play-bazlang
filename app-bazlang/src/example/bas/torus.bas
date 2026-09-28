1000 REM ### 3D rotating torus (doughnut) ###
1010 REM Solid shaded panels painted far to near, with perspective projection and back-face culling
1020 REM ### Pixel mode, switchable while running: Q = quadrant (4), H = half-cell (2), B = braille (8) ###
1030 LET pm = 4
1040 REM ### Mesh lines (braille only): 1 cuts each panel's outline back out of its fill ###
1050 LET grid = 1
1060 GO SUB 6000 : REM ### Set pixel mode ###
1070 REM ### Camera: eye at the origin looking along +z, torus centred cam_dist in front ###
1080 LET cam_dist = 4
1090 REM ### Light: unit vector from the surface towards the light, above left and in front ###
1100 LET lx = -0.5 : LET ly = 0.5 : LET lz = -0.7071
1110 REM ### Pacing: target frame rate, and spin rates in radians per second ###
1120 LET target_cps = 120
1130 LET spin_a = 0.8 : LET spin_b = 0.5 : LET spin_c = 0.3
1140 LET n1 = 24
1150 LET n2 = 12
1160 LET num_vertices = n1 * n2
1170 LET num_faces = num_vertices
1180 LET num_buckets = 48
1190 DIM vertices(num_vertices, 3)
1200 DIM projected(num_vertices, 2)
1210 DIM faces(num_faces, 11)
1220 DIM shade(num_faces)
1230 DIM head(num_buckets)
1240 DIM chain(num_faces)
1250 DIM ramp(12)
1260 GO SUB 4000 : REM ### Init data ###
1270 REM ### Face centres lie within r1 + r2 of the torus centre, so their depths fit these buckets ###
1280 LET z_near = cam_dist - r1 - r2 : LET z_scale = num_buckets / (2 * (r1 + r2) + 0.001)
1290 LET cycles = 0 : LET cps = 0
1300 LET start_t = FRAMES : LET t0 = start_t : LET next_t = start_t
2000 REM ### Main loop ###
2010 LET t = (FRAMES - t0) / 50
2020 LET a = t * spin_a : LET b = t * spin_b : LET c = t * spin_c
2030 LET sin_a = SIN (a) : LET cos_a = COS (a)
2040 LET sin_b = SIN (b) : LET cos_b = COS (b)
2050 LET sin_c = SIN (c) : LET cos_c = COS (c)
2060 REM ### Rotation matrix: rotate about X by a, then Y by b, then Z by c ###
2070 LET m11 = cos_b * cos_c : LET m12 = sin_a * sin_b * cos_c - cos_a * sin_c : LET m13 = cos_a * sin_b * cos_c + sin_a * sin_c
2080 LET m21 = cos_b * sin_c : LET m22 = sin_a * sin_b * sin_c + cos_a * cos_c : LET m23 = cos_a * sin_b * sin_c - sin_a * cos_c
2090 LET m31 = -sin_b : LET m32 = sin_a * cos_b : LET m33 = cos_a * cos_b
2100 FOR i = 1 TO num_vertices
2110 LET x = vertices(i, 1) : LET y = vertices(i, 2) : LET z = vertices(i, 3)
2120 REM ### Perspective project: screen offset = focal * x / depth ###
2130 LET depth = m31 * x + m32 * y + m33 * z + cam_dist
2140 LET projected(i, 1) = centre_x + focal_x * (m11 * x + m12 * y + m13 * z) / depth
2150 LET projected(i, 2) = centre_y + focal * (m21 * x + m22 * y + m23 * z) / depth
2160 NEXT i
2170 REM ### Sort: drop each front-facing panel into a depth bucket, a linked list per bucket ###
2180 FOR k = 1 TO num_buckets : LET head(k) = 0 : NEXT k
2190 LET drawn = 0
2200 FOR i = 1 TO num_faces
2210 LET nx = faces(i, 2) : LET ny = faces(i, 3) : LET nz = faces(i, 4)
2220 LET rz = m31 * nx + m32 * ny + m33 * nz
2230 REM ### Back-face cull: rotation keeps n.p, so n'.(p' + cam_dist * z) = n.p + cam_dist * n'z ###
2240 IF faces(i, 1) + cam_dist * rz >= 0 THEN GO TO 2320
2250 LET rx = m11 * nx + m12 * ny + m13 * nz
2260 LET ry = m21 * nx + m22 * ny + m23 * nz
2270 REM ### Shade 1 (unlit) to 12 (facing the light) from the rotated normal . light ###
2280 LET lum = rx * lx + ry * ly + rz * lz : IF lum < 0 THEN LET lum = 0
2290 LET shade(i) = 1 + INT (lum * 11.99)
2300 LET k = 1 + INT ((m31 * faces(i, 5) + m32 * faces(i, 6) + m33 * faces(i, 7) + cam_dist - z_near) * z_scale)
2310 LET chain(i) = head(k) : LET head(k) = i : LET drawn = drawn + 1
2320 NEXT i
3000 FAST
3010 INK -1 : PAPER -1 : INVERSE 0 : CLS
3020 REM ### Paint far to near, so nearer panels cover farther ones ###
3030 FOR k = num_buckets TO 1 STEP -1
3040 LET f = head(k)
3050 IF f = 0 THEN GO TO 3080
3060 GO SUB 5000
3070 LET f = chain(f) : GO TO 3050
3080 NEXT k
3090 LET cycles = cycles + 1
3100 LET now = FRAMES
3110 IF now - start_t < 50 THEN GO TO 3130
3120 LET cps = INT (cycles * 50 / (now - start_t) + 0.5) : LET cycles = 0 : LET start_t = now
3130 PRINT AT 0, 0; INK 8; PAPER 8; "BazLang 3D Torus - "; drawn; "/"; num_faces; " panels, "; cps; " CPS - Q/H/B changes mode    "
3140 SLOW
3150 REM ### Poll the keyboard inside this frame's time slot: the wait below is measured after it ###
3160 LET k$ = INKEY$ : LET new_pm = pm
3170 IF k$ = "q" OR k$ = "Q" THEN LET new_pm = 4
3180 IF k$ = "h" OR k$ = "H" THEN LET new_pm = 2
3190 IF k$ = "b" OR k$ = "B" THEN LET new_pm = 8
3200 IF new_pm <> pm THEN LET pm = new_pm : GO SUB 6000
3210 REM ### Wait for the next frame's slot; if more than a frame behind, resynchronise rather than rush ###
3220 LET next_t = next_t + 50 / target_cps
3230 LET wait = next_t - FRAMES
3240 IF wait > 0 THEN PAUSE wait
3250 IF wait < -50 / target_cps THEN LET next_t = FRAMES
3260 GO TO 2000
4000 REM ### Init torus vertices, panels and colour ramp ###
4010 PRINT INK 8; PAPER 8; "Generating mesh..."
4020 LET r1 = 1.5
4030 LET r2 = 0.6
4040 LET step1 = 2 * PI / n1
4050 LET step2 = 2 * PI / n2
4060 FOR i = 0 TO n1 - 1
4070 FOR j = 0 TO n2 - 1
4080 LET idx = i * n2 + j + 1
4090 LET ca1 = COS (i * step1) : LET sa1 = SIN (i * step1)
4100 LET ca2 = COS (j * step2) : LET sa2 = SIN (j * step2)
4110 LET vertices(idx, 1) = (r1 + r2 * ca2) * ca1
4120 LET vertices(idx, 2) = (r1 + r2 * ca2) * sa1
4130 LET vertices(idx, 3) = r2 * sa2
4140 REM ### Panel idx is the quad from vertex idx to the next ring and tube step, sampled at its middle ###
4150 LET ca1 = COS ((i + 0.5) * step1) : LET sa1 = SIN ((i + 0.5) * step1)
4160 LET ca2 = COS ((j + 0.5) * step2) : LET sa2 = SIN ((j + 0.5) * step2)
4170 LET faces(idx, 2) = ca2 * ca1 : LET faces(idx, 3) = ca2 * sa1 : LET faces(idx, 4) = sa2
4180 LET faces(idx, 5) = (r1 + r2 * ca2) * ca1 : LET faces(idx, 6) = (r1 + r2 * ca2) * sa1 : LET faces(idx, 7) = r2 * sa2
4190 REM ### n.p for the panel centre p (columns 5 to 7) and its normal n (columns 2 to 4) ###
4200 LET faces(idx, 1) = (r1 + r2 * ca2) * ca2 + r2 * sa2 * sa2
4210 REM ### Corners a (idx itself), then b, c and d going round: next tube step, then next ring ###
4220 LET ip = i + 1 : IF ip >= n1 THEN LET ip = ip - n1
4230 LET jp = j + 1 : IF jp >= n2 THEN LET jp = jp - n2
4240 LET faces(idx, 8) = i * n2 + jp + 1 : LET faces(idx, 9) = ip * n2 + jp + 1 : LET faces(idx, 10) = ip * n2 + j + 1
4250 REM ### Checkerboard parity: neighbouring panels always differ, so they use different colour slots ###
4260 LET faces(idx, 11) = (i + j) - 2 * INT ((i + j) / 2)
4270 NEXT j
4280 NEXT i
4290 REM ### Colour ramp, dim to bright like donut.c's .,-~:;=!*#$@ - a glazed-doughnut gold ###
4300 FOR k = 1 TO 12
4310 LET v = 0.15 + 0.85 * (k - 1) / 11
4320 LET ramp(k) = COLOUR (INT (255 * v), INT (190 * v), INT (110 * v))
4330 NEXT k
4340 PAPER 8 : INK 8 : CLS
4350 RETURN
5000 REM ### Fill panel f: sweep lines from side a-b across to side d-c, at most one dot apart ###
5010 LET ax = projected(f, 1) : LET ay = projected(f, 2)
5020 LET v = faces(f, 8) : LET bx = projected(v, 1) : LET by = projected(v, 2)
5030 LET v = faces(f, 9) : LET cx = projected(v, 1) : LET cy = projected(v, 2)
5040 LET v = faces(f, 10) : LET dx = projected(v, 1) : LET dy = projected(v, 2)
5050 LET n = ABS (bx - ax) : IF ABS (by - ay) > n THEN LET n = ABS (by - ay)
5060 IF ABS (cx - dx) > n THEN LET n = ABS (cx - dx)
5070 IF ABS (cy - dy) > n THEN LET n = ABS (cy - dy)
5080 LET n = INT (n) + 1
5090 LET sx1 = (bx - ax) / n : LET sy1 = (by - ay) / n : LET sx2 = (cx - dx) / n : LET sy2 = (cy - dy) / n
5100 LET x1 = ax : LET y1 = ay : LET x2 = dx : LET y2 = dy
5110 REM ### Even panels set dots in INK. In two-colour modes, odd panels clear dots and colour the PAPER ###
5120 REM ### instead (INK 8 keeps the cell's ink), so a cell shared by neighbours keeps both colours ###
5130 LET col = ramp(shade(f))
5140 IF two_col * faces(f, 11) = 1 THEN GO TO 5170
5150 INK col : PAPER 8 : INVERSE 0
5160 GO TO 5180
5170 INK 8 : PAPER col : INVERSE 1
5180 FOR s = 0 TO n
5190 PLOT x1, y1 : DRAW x2 - x1, y2 - y1
5200 LET x1 = x1 + sx1 : LET y1 = y1 + sy1 : LET x2 = x2 + sx2 : LET y2 = y2 + sy2
5210 NEXT s
5220 INVERSE 0
5230 IF grid = 0 OR two_col = 1 THEN RETURN
5240 REM ### Cut the outline back out of the fill, leaving dark mesh lines ###
5250 PLOT INVERSE 1; ax, ay : DRAW INVERSE 1; bx - ax, by - ay : DRAW INVERSE 1; cx - bx, cy - by : DRAW INVERSE 1; dx - cx, dy - cy : DRAW INVERSE 1; ax - dx, ay - dy
5260 RETURN
6000 REM ### Set pixel mode pm, and the plot size, centre and projection scale that go with it ###
6010 PLOTMODE pm
6020 LET w = PLOTW : LET h = PLOTH
6030 LET centre_x = w / 2 : LET centre_y = h / 2
6040 LET focal = h * 0.47
6050 REM ### Pixel height over width for a cell twice as tall as wide: quadrant pixels are 1 by 2 ###
6060 LET aspect = 1 : IF pm = 4 THEN LET aspect = 2
6070 LET focal_x = focal * aspect
6080 REM ### Quadrant and half-cell blocks show an INK and a PAPER colour per cell; braille shows one ###
6090 LET two_col = pm <> 8
6100 RETURN
