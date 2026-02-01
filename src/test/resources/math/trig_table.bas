# Trigonometry Table
# Displays sine, cosine, and tangent values for common angles
10 PRINT "DEG", "SIN", "COS", "TAN"
20 PRINT "---", "---", "---", "---"
30 FOR D = 0 TO 90 STEP 15
40   LET R = D * PI / 180
50   PRINT D, SIN R, COS R,
60   IF D = 90 THEN GOTO 80
70   PRINT TAN R
75   GOTO 90
80   PRINT "INF"
90 NEXT D
