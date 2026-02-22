# Dice Game - Higher or Lower
# Guess if the next dice roll will be higher or lower
5 RAND 0
10 PRINT "DICE GAME: HIGHER OR LOWER"
20 LET D = INT(RND * 6) + 1
30 PRINT "CURRENT DIE: "; D
40 PRINT "WILL NEXT BE (H)IGHER OR (L)OWER? ";
50 INPUT G$
60 LET N = INT(RND * 6) + 1
70 PRINT "NEXT DIE: "; N
80 IF N = D THEN PRINT "SAME! NO WINNER."
90 IF N > D AND (G$ = "H" OR G$ = "h") THEN PRINT "YOU WIN!"
100 IF N < D AND (G$ = "L" OR G$ = "l") THEN PRINT "YOU WIN!"
110 IF N > D AND (G$ = "L" OR G$ = "l") THEN PRINT "YOU LOSE!"
120 IF N < D AND (G$ = "H" OR G$ = "h") THEN PRINT "YOU LOSE!"
130 PRINT "PLAY AGAIN (Y/N)? ";
140 INPUT A$
150 IF A$ = "Y" OR A$ = "y" THEN GOTO 20
