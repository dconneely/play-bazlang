# Factorial
# Calculates N! using iteration
10 PRINT "Factorial Calculator"
20 PRINT "Enter a number: ";
30 INPUT N
40 LET F = 1
50 LET I = 1
60 IF I > N THEN GOTO 100
70 LET F = F * I
80 LET I = I + 1
90 GOTO 60
100 PRINT "Factorial of "; N; " is "; F
