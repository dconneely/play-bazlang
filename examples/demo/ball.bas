1000 REM ### 3D Rotating Football Demo ###
1010 PLOTMODE 8
1020 INK -1 : PAPER -1
1030 LET w = PLOTW : LET h = PLOTH
1040 LET center_x = w / 2 : LET center_y = h / 2
1050 LET aspect = 1.25
1060 LET s = h * 0.45
1070 DIM vertices(60, 3)
1080 DIM projected(60, 2)
1090 DIM proj_z(60)
1100 DIM rot_x(60)
1110 DIM rot_y(60)
1120 DIM edges(90, 2)
1130 DIM edge_type(90)
1140 DIM ipx(12)
1150 DIM ipy(12)
1160 DIM ipz(12)
1170 DIM hpx(20)
1180 DIM hpy(20)
1190 DIM hpz(20)
1200 DIM hc_z(20)
1210 DIM projected_hc(20, 2)
1220 DIM rot_x_hc(20)
1230 DIM rot_y_hc(20)
1240 DIM hex_vertices(20, 6)
1250 DIM poly_x(6)
1260 DIM poly_y(6)
1270 LET num_vertices = 0
1280 GO SUB 6000 : REM ### Initialize vertices, edges and types ###
1290 LET a = 0.5 : LET b = 0.5 : LET c = 0.2
1300 LET use_color = 1 : REM ### 0 = B&W wireframe, 1 = Color football ###
1310 LET cycles = 0 : LET start_t = FRAMES : LET cps = 0

2000 REM ### Main loop ###
2010 LET sin_a = SIN (a) : LET cos_a = COS (a)
2020 LET sin_b = SIN (b) : LET cos_b = COS (b)
2030 LET sin_c = SIN (c) : LET cos_c = COS (c)
2040 FOR i = 1 TO 60
2050 LET x = vertices(i, 1) : LET y = vertices(i, 2) : LET z = vertices(i, 3)
2060 LET y1 = y * cos_a - z * sin_a
2070 LET z1 = y * sin_a + z * cos_a
2080 LET x1 = x * cos_b + z1 * sin_b
2090 LET z2 = -x * sin_b + z1 * cos_b
2100 LET x2 = x1 * cos_c - y1 * sin_c
2110 LET y2 = x1 * sin_c + y1 * cos_c
2120 LET z_proj = 10 - z2
2130 LET projected(i, 1) = center_x + (x2 / z_proj) * s * aspect
2140 LET projected(i, 2) = center_y + (y2 / z_proj) * s
2150 LET proj_z(i) = z2
2160 LET rot_x(i) = x2 : LET rot_y(i) = y2
2170 NEXT i
2180 FOR i = 1 TO 20
2190 LET x = hpx(i) : LET y = hpy(i) : LET z = hpz(i)
2200 LET y1 = y * cos_a - z * sin_a
2210 LET z1 = y * sin_a + z * cos_a
2220 LET x1 = x * cos_b + z1 * sin_b
2230 LET z2 = -x * sin_b + z1 * cos_b
2240 LET x2 = x1 * cos_c - y1 * sin_c
2250 LET y2 = x1 * sin_c + y1 * cos_c
2260 LET z_proj = 10 - z2
2270 LET projected_hc(i, 1) = center_x + (x2 / z_proj) * s * aspect
2280 LET projected_hc(i, 2) = center_y + (y2 / z_proj) * s
2290 LET hc_z(i) = z2
2300 LET rot_x_hc(i) = x2 : LET rot_y_hc(i) = y2
2310 NEXT i

3000 FAST
3010 CLS
3020 FOR j = 1 TO 20
3030 IF use_color = 0 THEN GO TO 3100
3040 IF hc_z(j) < 2.45623 THEN GO TO 3100
3050 LET cx = projected_hc(j, 1) : LET cy = projected_hc(j, 2)
3060 FOR k = 1 TO 6
3070 LET v = hex_vertices(j, k)
3071 LET zv = proj_z(v)
3072 IF zv < 2.45623 THEN GO TO 3075
3073 LET px = projected(v, 1) : LET py = projected(v, 2)
3074 GO TO 3080
3075 LET t = (hc_z(j) - 2.45623) / (hc_z(j) - zv)
3076 LET rx = rot_x_hc(j) + t * (rot_x(v) - rot_x_hc(j))
3077 LET ry = rot_y_hc(j) + t * (rot_y(v) - rot_y_hc(j))
3078 LET px = center_x + (rx / 7.54377) * s * aspect
3079 LET py = center_y + (ry / 7.54377) * s
3080 LET poly_x(k) = px : LET poly_y(k) = py
3081 NEXT k
3082 FOR k = 1 TO 6
3083 LET k2 = k + 1 : IF k2 = 7 THEN LET k2 = 1
3084 LET x1_d = poly_x(k) : LET y1_d = poly_y(k)
3085 LET x2_d = poly_x(k2) : LET y2_d = poly_y(k2)
3086 GO SUB 3500
3087 NEXT k
3100 NEXT j
3110 FOR i = 1 TO 90
3111 LET e1 = edges(i, 1) : LET e2 = edges(i, 2)
3112 LET z1 = proj_z(e1) : LET z2 = proj_z(e2)
3113 IF z1 < 2.45623 AND z2 < 2.45623 THEN GO TO 3140
3114 IF z1 >= 2.45623 AND z2 >= 2.45623 THEN GO TO 3132
3115 IF z1 >= 2.45623 THEN GO TO 3124
3116 REM ### Case: z2 >= 2.45623 and z1 < 2.45623 ###
3117 LET t = (z2 - 2.45623) / (z2 - z1)
3118 LET rx = rot_x(e2) + t * (rot_x(e1) - rot_x(e2))
3119 LET ry = rot_y(e2) + t * (rot_y(e1) - rot_y(e2))
3120 LET x1_d = projected(e2, 1) : LET y1_d = projected(e2, 2)
3121 LET x2_d = center_x + (rx / 7.54377) * s * aspect
3122 LET y2_d = center_y + (ry / 7.54377) * s
3123 GO TO 3135
3124 REM ### Case: z1 >= 2.45623 and z2 < 2.45623 ###
3125 LET t = (z1 - 2.45623) / (z1 - z2)
3126 LET rx = rot_x(e1) + t * (rot_x(e2) - rot_x(e1))
3127 LET ry = rot_y(e1) + t * (rot_y(e2) - rot_y(e1))
3128 LET x1_d = projected(e1, 1) : LET y1_d = projected(e1, 2)
3129 LET x2_d = center_x + (rx / 7.54377) * s * aspect
3130 LET y2_d = center_y + (ry / 7.54377) * s
3131 GO TO 3135
3132 REM ### Case: both >= 2.45623 ###
3133 LET x1_d = projected(e1, 1) : LET y1_d = projected(e1, 2)
3134 LET x2_d = projected(e2, 1) : LET y2_d = projected(e2, 2)
3135 INK -1
3136 PLOT x1_d, y1_d : DRAW x2_d - x1_d, y2_d - y1_d
3140 NEXT i
3150 LET cycles = cycles + 1
3160 LET now = FRAMES
3170 IF now - start_t < 50 THEN GO TO 3200
3180 LET cps = INT (cycles * 50 / (now - start_t)) : LET cycles = 0 : LET start_t = now
3200 PRINT AT 0, 0; INK -1; PAPER -1; "Bazlang 3D Football - 60 vertices, 90 edges, "; cps; " CPS    "
3210 SLOW
3220 LET a = a + 0.02
3230 LET b = b + 0.015
3240 LET c = c + 0.01
3250 GO TO 2000

3500 REM ### Subroutine: Fill triangle from projected_hc(j) to edge (x1_d, y1_d) - (x2_d, y2_d) ###
3510 LET cx = projected_hc(j, 1) : LET cy = projected_hc(j, 2)
3520 INK -1 : REM ### Fill with Default Foreground ###
3530 FOR f = 0 TO 8
3540 LET tf = f * 0.125
3550 LET tx = x1_d + tf * (x2_d - x1_d)
3560 LET ty = y1_d + tf * (y2_d - y1_d)
3570 PLOT cx, cy : DRAW tx - cx, ty - cy
3580 NEXT f
3590 RETURN

6000 REM ### Initialize geometry ###
6010 LET phi = 1.618034
6020 FOR s1 = -1 TO 1 STEP 2
6030 FOR s2 = -1 TO 1 STEP 2
6040 FOR s3 = -1 TO 1 STEP 2
6050 REM ### Pattern 1 ###
6060 LET tx = 0 : LET ty = s2 : LET tz = 3 * phi * s3 : GO SUB 7100
6070 LET tx = s1 : LET ty = 3 * phi * s2 : LET tz = 0 : GO SUB 7100
6080 LET tx = 3 * phi * s1 : LET ty = 0 : LET tz = s3 : GO SUB 7100
6090 REM ### Pattern 2 ###
6100 LET val_b = 2 + phi : LET val_c = 2 * phi
6110 LET tx = s1 * 1 : LET ty = s2 * val_b : LET tz = s3 * val_c : GO SUB 7100
6120 LET tx = s1 * val_b : LET ty = s2 * val_c : LET tz = s3 * 1 : GO SUB 7100
6130 LET tx = s1 * val_c : LET ty = s2 * 1 : LET tz = s3 * val_b : GO SUB 7100
6140 REM ### Pattern 3 ###
6150 LET val_a = phi : LET val_c = 2 * phi + 1
6160 LET tx = s1 * val_a : LET ty = s2 * 2 : LET tz = s3 * val_c : GO SUB 7100
6170 LET tx = s1 * 2 : LET ty = s2 * val_c : LET tz = s3 * val_a : GO SUB 7100
6180 LET tx = s1 * val_c : LET ty = s2 * val_a : LET tz = s3 * 2 : GO SUB 7100
6190 NEXT s3
6200 NEXT s2
6210 NEXT s1
6220 REM ### Generate 12 Icosahedron vertices (pentagon centers) ###
6230 LET ico_count = 0
6240 FOR s1 = -1 TO 1 STEP 2
6250 FOR s2 = -1 TO 1 STEP 2
6260 LET ico_count = ico_count + 1
6270 LET ipx(ico_count) = 0 : LET ipy(ico_count) = s1 * 3 : LET ipz(ico_count) = s2 * 3 * phi
6280 LET ico_count = ico_count + 1
6290 LET ipx(ico_count) = s1 * 3 : LET ipy(ico_count) = s2 * 3 * phi : LET ipz(ico_count) = 0
6300 LET ico_count = ico_count + 1
6310 LET ipx(ico_count) = s1 * 3 * phi : LET ipy(ico_count) = 0 : LET ipz(ico_count) = s2 * 3
6320 NEXT s2
6330 NEXT s1
6340 REM ### Find 90 edges and classify pentagon edges ###
6350 LET num_edges = 0
6360 FOR i = 1 TO 59
6370 FOR j = i + 1 TO 60
6380 LET dx = vertices(i, 1) - vertices(j, 1)
6390 LET dy = vertices(i, 2) - vertices(j, 2)
6400 LET dz = vertices(i, 3) - vertices(j, 3)
6410 LET dist2 = dx * dx + dy * dy + dz * dz
6420 IF ABS (dist2 - 4) > 0.05 THEN GO TO 6570
6430 LET num_edges = num_edges + 1
6440 LET edges(num_edges, 1) = i
6450 LET edges(num_edges, 2) = j
6460 LET mx = (vertices(i, 1) + vertices(j, 1)) / 2
6470 LET my = (vertices(i, 2) + vertices(j, 2)) / 2
6480 LET mz = (vertices(i, 3) + vertices(j, 3)) / 2
6490 LET min_d2 = 999
6500 FOR k = 1 TO 12
6510 LET dx = mx - ipx(k) : LET dy = my - ipy(k) : LET dz = mz - ipz(k)
6520 LET d2 = dx * dx + dy * dy + dz * dz
6530 IF d2 < min_d2 THEN LET min_d2 = d2
6540 NEXT k
6550 LET edge_type(num_edges) = 0
6560 IF min_d2 < 1.0 THEN LET edge_type(num_edges) = 1
6570 NEXT j
6580 NEXT i
6590 GO SUB 6600 : REM ### Generate hexagon centers and map edges ###
6595 RETURN

6600 REM ### Generate 20 hexagon centers ###
6610 LET hc_count = 0
6620 LET phi2 = phi + 1 : LET phi3 = 2 * phi + 1
6630 FOR s1 = -1 TO 1 STEP 2
6640 FOR s2 = -1 TO 1 STEP 2
6650 FOR s3 = -1 TO 1 STEP 2
6660 LET tx = s1 * phi2 : LET ty = s2 * phi2 : LET tz = s3 * phi2 : GO SUB 7200
6670 NEXT s3
6680 NEXT s2
6690 NEXT s1
6700 FOR s1 = -1 TO 1 STEP 2
6710 FOR s2 = -1 TO 1 STEP 2
6720 LET tx = 0 : LET ty = s1 * phi3 : LET tz = s2 * phi : GO SUB 7200
6730 LET tx = s1 * phi3 : LET ty = s2 * phi : LET tz = 0 : GO SUB 7200
6740 LET tx = s1 * phi : LET ty = 0 : LET tz = s2 * phi3 : GO SUB 7200
6750 NEXT s2
6760 NEXT s1

6770 REM ### Find and order hexagon vertices ###
6780 FOR j = 1 TO 20
6790 LET hc = 0
6800 FOR i = 1 TO 60
6810 LET dx = vertices(i, 1) - hpx(j)
6820 LET dy = vertices(i, 2) - hpy(j)
6830 LET dz = vertices(i, 3) - hpz(j)
6840 LET d = dx * dx + dy * dy + dz * dz
6850 IF ABS (d - 4) > 0.1 THEN GO TO 6870
6860 LET hc = hc + 1 : LET hex_vertices(j, hc) = i
6870 NEXT i
6880 REM ### Order the 6 vertices circularly ###
6890 DIM temp_v(6)
6900 FOR k = 1 TO 6 : LET temp_v(k) = hex_vertices(j, k) : NEXT k
6910 LET hex_vertices(j, 1) = temp_v(1)
6920 FOR k = 1 TO 5
6930 LET v_curr = hex_vertices(j, k)
6940 FOR m = 2 TO 6
6950 LET v_cand = temp_v(m)
6960 IF v_cand = 0 THEN GO TO 7040
6970 REM ### Check if v_curr and v_cand are adjacent ###
6980 LET dx = vertices(v_curr, 1) - vertices(v_cand, 1)
6990 LET dy = vertices(v_curr, 2) - vertices(v_cand, 2)
7000 LET dz = vertices(v_curr, 3) - vertices(v_cand, 3)
7010 LET d = dx * dx + dy * dy + dz * dz
7020 IF ABS (d - 4) > 0.1 THEN GO TO 7040
7030 LET hex_vertices(j, k + 1) = v_cand : LET temp_v(m) = 0 : GO TO 7050
7040 NEXT m
7050 NEXT k
7060 NEXT j
7070 RETURN

7100 REM ### Subroutine: Add vertex if unique ###
7110 IF num_vertices = 60 THEN RETURN
7120 FOR k = 1 TO num_vertices
7130 IF ABS (vertices(k, 1) - tx) < 0.01 AND ABS (vertices(k, 2) - ty) < 0.01 AND ABS (vertices(k, 3) - tz) < 0.01 THEN RETURN
7140 NEXT k
7150 LET num_vertices = num_vertices + 1
7160 LET vertices(num_vertices, 1) = tx
7170 LET vertices(num_vertices, 2) = ty
7180 LET vertices(num_vertices, 3) = tz
7190 RETURN

7200 REM ### Subroutine: Add hexagon center if unique ###
7210 IF hc_count = 20 THEN RETURN
7220 FOR k = 1 TO hc_count
7230 IF ABS (hpx(k) - tx) < 0.01 AND ABS (hpy(k) - ty) < 0.01 AND ABS (hpz(k) - tz) < 0.01 THEN RETURN
7240 NEXT k
7250 LET hc_count = hc_count + 1
7260 LET hpx(hc_count) = tx
7270 LET hpy(hc_count) = ty
7280 LET hpz(hc_count) = tz
7290 RETURN
