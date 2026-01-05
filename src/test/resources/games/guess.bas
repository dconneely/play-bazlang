# Number Guess Game
10 PRINT "NUMBER GUESSING GAME"
20 PRINT "==================="
30 PRINT
40 PRINT "I'm thinking of a number"
50 PRINT "between 1 and 100"
60 PRINT
70 LET N = INT(RND * 100) + 1
80 LET G = 0
90 PRINT "Guess the number: ";
100 INPUT A
110 LET G = G + 1
120 IF A = N THEN GOTO 200
130 IF A < N THEN PRINT "Too low!"
140 IF A > N THEN PRINT "Too high!"
150 GOTO 90
200 PRINT "Correct! You guessed it in "; G; " tries!"
210 PRINT
220 IF G <= 5 THEN PRINT "Excellent!"
230 IF G > 5 AND G <= 10 THEN PRINT "Good job!"
240 IF G > 10 THEN PRINT "You can do better!"
250 PRINT
260 PRINT "Play again (Y/N)? ";
270 INPUT B$
280 IF B$ = "Y" OR B$ = "y" THEN GOTO 30
290 PRINT "Thanks for playing!"
