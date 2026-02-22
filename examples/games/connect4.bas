# Connect 4 - A two-player connection game
# Human (⚫) vs Computer (⚪)
# Use number keys 1-7 to drop a piece in that column

10 CLS
20 GOSUB 9000
30 LET TURN = 1
40 LET MOVES = 0

# Main game loop
100 GOSUB 1000
110 IF TURN = 1 THEN GOSUB 2000
120 IF TURN = 2 THEN GOSUB 3000
130 IF COL = 0 THEN GOTO 100
140 GOSUB 4000
150 GOSUB 5000
160 IF WIN = 1 THEN GOTO 200
170 LET MOVES = MOVES + 1
180 IF MOVES = 42 THEN GOTO 300
190 LET TURN = 3 - TURN
195 GOTO 100

# Win announcement
200 GOSUB 1000
210 PRINT AT 12, 0; "                                        "
220 IF TURN = 1 THEN PRINT AT 12, 0; "*** YOU WIN! ***"
230 IF TURN = 2 THEN PRINT AT 12, 0; "*** COMPUTER WINS! ***"
240 GOTO 400

# Draw announcement
300 GOSUB 1000
310 PRINT AT 12, 0; "*** IT'S A DRAW! ***"

# Play again?
400 PRINT AT 14, 0; "Play again? (Y/N) ";
410 INPUT A$
420 IF A$ = "Y" OR A$ = "y" THEN GOTO 10
430 CLS
440 STOP

# Draw the board (GOSUB 1000)
1000 PRINT AT 1, 0; "   1    2    3    4    5    6    7"
1010 PRINT AT 2, 0; "┌────┬────┬────┬────┬────┬────┬────┐"
1020 FOR R = 1 TO 6
1030 LET Y = R + 2
1040 PRINT AT Y, 0; "│";
1050 FOR C = 1 TO 7
1060 LET V = B(R, C)
1070 IF V = 0 THEN PRINT " ·· │";
1080 IF V = 1 THEN PRINT " ⚫ │";
1090 IF V = 2 THEN PRINT " ⚪ │";
1100 NEXT C
1110 NEXT R
1120 PRINT AT 9, 0; "└────┴────┴────┴────┴────┴────┴────┘"
1140 RETURN

# Human turn (GOSUB 2000)
2000 PRINT AT 11, 0; "                                        "
2010 PRINT AT 11, 0; "Your turn (⚫). Column (1-7): ";
2020 INPUT A$
2030 LET COL = VAL A$
2040 IF COL < 1 OR COL > 7 THEN GOTO 2000
2050 IF B(1, COL) <> 0 THEN GOTO 2000
2060 RETURN

# Computer turn (GOSUB 3000)
3000 PRINT AT 11, 0; "                                        "
3010 PRINT AT 11, 0; "Computer thinking..."

# Check for winning move
3020 FOR CC = 1 TO 7
3030 IF B(1, CC) <> 0 THEN GOTO 3090
3040 LET C = CC
3050 GOSUB 3500
3060 LET B(TR, CC) = 2
3070 GOSUB 5000
3080 LET B(TR, CC) = 0
3085 IF WIN = 1 THEN LET COL = CC
3087 IF WIN = 1 THEN RETURN
3090 NEXT CC

# Block human winning move
3100 FOR CC = 1 TO 7
3110 IF B(1, CC) <> 0 THEN GOTO 3170
3120 LET C = CC
3130 GOSUB 3500
3140 LET B(TR, CC) = 1
3150 GOSUB 5000
3160 LET B(TR, CC) = 0
3165 IF WIN = 1 THEN LET COL = CC
3167 IF WIN = 1 THEN RETURN
3170 NEXT CC

# Prefer center column
3200 IF B(1, 4) = 0 THEN LET COL = 4
3210 IF B(1, 4) = 0 THEN RETURN

# Try columns near center
3220 FOR I = 1 TO 3
3230 LET C = 4 - I
3240 IF C >= 1 THEN IF B(1, C) = 0 THEN LET COL = C
3245 IF C >= 1 THEN IF B(1, C) = 0 THEN RETURN
3250 LET C = 4 + I
3260 IF C <= 7 THEN IF B(1, C) = 0 THEN LET COL = C
3265 IF C <= 7 THEN IF B(1, C) = 0 THEN RETURN
3270 NEXT I

3280 LET COL = 0
3290 RETURN

# Find target row for column C (GOSUB 3500)
# Sets TR to the lowest empty row in column C
3500 LET TR = 0
3510 FOR R = 6 TO 1 STEP -1
3520 IF B(R, C) = 0 THEN LET TR = R
3530 IF TR > 0 THEN RETURN
3540 NEXT R
3550 RETURN

# Animate piece drop (GOSUB 4000)
4000 LET C = COL
4010 GOSUB 3500
4020 LET P$ = "⚫"
4030 IF TURN = 2 THEN LET P$ = "⚪"
4040 FOR R = 1 TO TR
4050 LET Y = R + 2
4060 PRINT AT Y, COL * 5 - 4; " "; P$; " "
4070 PAUSE 2
4080 IF R < TR THEN PRINT AT Y, COL * 5 - 4; " ·· "
4090 NEXT R
4100 LET B(TR, COL) = TURN
4110 RETURN

# Check for win (GOSUB 5000)
5000 LET WIN = 0

# Check horizontal
5010 FOR R = 1 TO 6
5020 FOR C = 1 TO 4
5030 IF B(R, C) = 0 THEN GOTO 5060
5040 IF B(R, C) = B(R, C + 1) AND B(R, C) = B(R, C + 2) AND B(R, C) = B(R, C + 3) THEN LET WIN = 1
5050 IF WIN = 1 THEN RETURN
5060 NEXT C
5070 NEXT R

# Check vertical
5100 FOR R = 1 TO 3
5110 FOR C = 1 TO 7
5120 IF B(R, C) = 0 THEN GOTO 5150
5130 IF B(R, C) = B(R + 1, C) AND B(R, C) = B(R + 2, C) AND B(R, C) = B(R + 3, C) THEN LET WIN = 1
5140 IF WIN = 1 THEN RETURN
5150 NEXT C
5160 NEXT R

# Check diagonal (down-right)
5200 FOR R = 1 TO 3
5210 FOR C = 1 TO 4
5220 IF B(R, C) = 0 THEN GOTO 5250
5230 IF B(R, C) = B(R + 1, C + 1) AND B(R, C) = B(R + 2, C + 2) AND B(R, C) = B(R + 3, C + 3) THEN LET WIN = 1
5240 IF WIN = 1 THEN RETURN
5250 NEXT C
5260 NEXT R

# Check diagonal (down-left)
5300 FOR R = 1 TO 3
5310 FOR C = 4 TO 7
5320 IF B(R, C) = 0 THEN GOTO 5350
5330 IF B(R, C) = B(R + 1, C - 1) AND B(R, C) = B(R + 2, C - 2) AND B(R, C) = B(R + 3, C - 3) THEN LET WIN = 1
5340 IF WIN = 1 THEN RETURN
5350 NEXT C
5360 NEXT R

5400 RETURN

# Initialize board (GOSUB 9000)
9000 DIM B(6, 7)
9010 FOR R = 1 TO 6
9020 FOR C = 1 TO 7
9030 LET B(R, C) = 0
9040 NEXT C
9050 NEXT R
9060 RETURN
