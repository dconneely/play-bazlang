# Decimal to Binary
# Converts a decimal number (0-255) to binary
10 PRINT "DECIMAL TO BINARY"
20 PRINT "ENTER NUMBER (0-255): ";
30 INPUT N
40 LET B$ = ""
50 FOR I = 1 TO 8
60   LET R = N - 2 * INT(N / 2)
70   LET B$ = STR$(R) + B$
80   LET N = INT(N / 2)
90 NEXT I
100 PRINT "BINARY: "; B$
