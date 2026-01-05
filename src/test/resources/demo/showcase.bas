# Complete Feature Showcase
10 PRINT "========================"
20 PRINT "    Interpreter Demo"
30 PRINT "========================"
40 PRINT
# Character Set Demonstrations
50 PRINT "1. Graphics Characters:"
60 PRINT "   Blocks: "; CHR$(32); CHR$(9624); CHR$(9629); CHR$(9600); CHR$(9622); CHR$(9612); CHR$(9630); CHR$(9627)
70 PRINT "   More:   "; CHR$(9608); CHR$(9631); CHR$(9625); CHR$(9604); CHR$(9628); CHR$(9616); CHR$(9626); CHR$(9623)
80 PRINT "   Shaded: "; CHR$(9618); CHR$(129934); CHR$(129935); CHR$(129936); CHR$(129937); CHR$(129938)
90 PRINT
100 PRINT "2. Compatible Character Codes:"
110 FOR I = 65 TO 74
120   PRINT CHR$(I);
130 NEXT I
140 PRINT " (A-J using CHR$)"
150 PRINT
160 PRINT "3. CODE Function:"
170 PRINT "   CODE('H') = "; CODE("H"); " (char code)"
180 PRINT
190 PRINT "4. String Functions:"
200 LET MSG$ = "Hello world!"
210 PRINT "   Message: "; MSG$
220 PRINT "   Length: "; LEN(MSG$)
230 PRINT "   STR$(42) = "; STR$(42)
240 PRINT
250 PRINT "5. Math Functions:"
260 PRINT "   PI = "; PI
270 PRINT "   SQR(16) = "; SQR(16)
280 PRINT "   SIN(PI/2) = "; SIN(PI/2)
290 PRINT "   ACS(0.5) = "; ACS(0.5)
300 PRINT
310 PRINT "6. Unicode Support:"
320 PRINT "   CHR$(9786) = "; CHR$(9786); " (smiley)"
330 PRINT "   CHR$(9733) = "; CHR$(9733); " (star)"
340 PRINT
350 PRINT "All features working! "; CHR$(10003)