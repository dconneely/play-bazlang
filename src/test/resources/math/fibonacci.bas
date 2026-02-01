# Fibonacci Sequence
# Generates the Fibonacci sequence up to N terms
10 PRINT "FIBONACCI SEQUENCE"
20 PRINT "ENTER NUMBER OF TERMS: ";
30 INPUT N
40 IF N < 1 THEN GOTO 20
50 LET A = 0
60 LET B = 1
70 FOR I = 1 TO N
80   PRINT A; ", ";
90   LET T = A + B
100  LET A = B
110  LET B = T
120 NEXT I
130 PRINT
140 PRINT "DONE."
