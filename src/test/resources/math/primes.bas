# Prime Finder
# Finds all prime numbers up to a given limit
10 PRINT "Prime Number Finder"
20 PRINT "Enter upper limit: ";
30 INPUT LIMIT
40 PRINT "Prime numbers up to "; LIMIT; ":"
50 FOR N = 2 TO LIMIT
60   GOSUB 200
70   IF ISPRIME = 1 THEN PRINT N; ", ";
80 NEXT N
90 PRINT
100 STOP
200 REM Check if N is prime
210 LET ISPRIME = 1
220 IF N < 2 THEN LET ISPRIME = 0
230 IF N < 2 THEN RETURN
240 LET I = 2
250 IF I * I > N THEN RETURN
260 IF N - I * INT(N / I) = 0 THEN LET ISPRIME = 0
270 IF N - I * INT(N / I) = 0 THEN RETURN
280 LET I = I + 1
290 GOTO 250
