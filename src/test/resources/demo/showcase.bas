# Feature Showcase
# Demonstrates interpreter features including graphics, strings, and maths
10 PRINT "========================"
20 PRINT "    Interpreter Demo"
30 PRINT "========================"
40 PRINT
50 REM Character Set Demonstrations
60 PRINT "1. Graphics Characters:"
70 PRINT "   Blocks: "; CHR$(32); CHR$(9624); CHR$(9629); CHR$(9600); CHR$(9622); CHR$(9612); CHR$(9630); CHR$(9627)
80 PRINT "   More:   "; CHR$(9608); CHR$(9631); CHR$(9625); CHR$(9604); CHR$(9628); CHR$(9616); CHR$(9626); CHR$(9623)
90 PRINT "   Shaded: "; CHR$(9618); CHR$(129934); CHR$(129935); CHR$(129936); CHR$(129937); CHR$(129938)
100 PRINT
110 PRINT "2. Compatible Character Codes:"
120 FOR I = 65 TO 74
130   PRINT CHR$(I);
140 NEXT I
150 PRINT " (A-J using CHR$)"
160 PRINT
170 PRINT "3. CODE Function:"
180 PRINT "   CODE('H') = "; CODE("H"); " (char code)"
190 PRINT
200 PRINT "4. String Functions:"
210 LET MSG$ = "Hello world!"
220 PRINT "   Message: "; MSG$
230 PRINT "   Length: "; LEN(MSG$)
240 PRINT "   STR$(42) = "; STR$(42)
250 PRINT
260 PRINT "5. Maths Functions:"
270 PRINT "   PI = "; PI
280 PRINT "   SQR(16) = "; SQR(16)
290 PRINT "   SIN(PI/2) = "; SIN(PI/2)
300 PRINT "   ACS(0.5) = "; ACS(0.5)
310 PRINT
320 PRINT "6. Unicode Support:"
330 PRINT "   CHR$(9786) = "; CHR$(9786); " (smiley)"
340 PRINT "   CHR$(9733) = "; CHR$(9733); " (star)"
350 PRINT
360 PRINT "All features working! "; CHR$(10003)