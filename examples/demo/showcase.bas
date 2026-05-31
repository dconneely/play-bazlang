# Feature Showcase
# Demonstrates interpreter features including graphics, strings, and maths
10 PRINT "========================"
20 PRINT "    Interpreter Demo"
30 PRINT "========================"
40 PRINT
50 REM Character Set Demonstrations
60 PRINT "1. Graphics Characters:"
70 PRINT "   Blocks: "; CHR$(32); CODEPOINT$(9624); CODEPOINT$(9629); CODEPOINT$(9600); CODEPOINT$(9622); CODEPOINT$(9612); CODEPOINT$(9630); CODEPOINT$(9627)
80 PRINT "   More:   "; CODEPOINT$(9608); CODEPOINT$(9631); CODEPOINT$(9625); CODEPOINT$(9604); CODEPOINT$(9628); CODEPOINT$(9616); CODEPOINT$(9626); CODEPOINT$(9623)
90 PRINT "   Shaded: "; CODEPOINT$(9618); CODEPOINT$(129934); CODEPOINT$(129935); CODEPOINT$(129936); CODEPOINT$(129937); CODEPOINT$(129938)
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
330 PRINT "   CODEPOINT$(9786) = "; CODEPOINT$(9786); " (smiley)"
340 PRINT "   CODEPOINT$(9733) = "; CODEPOINT$(9733); " (star)"
350 PRINT
360 PRINT "All features working! "; CODEPOINT$(10003)