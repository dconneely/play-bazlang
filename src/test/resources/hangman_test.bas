10 PLOTMODE 4
20 LET clean_word$ = "ELEPHANT"
30 LET guess_word$ = "--------"
40 LET misses = 0
50 LET letters$ = ""
100 PRINT "Word: "; guess_word$
110 PRINT "Misses: "; misses
120 IF guess_word$ = clean_word$ THEN PRINT "You win!" : STOP
130 IF misses = 6 THEN PRINT "You died!" : STOP
140 LET a$ = UINKEY$
150 IF LEN (a$) <> 1 THEN GO TO 140
160 LET user_guess$ = a$
170 LET ascii_code = CODE user_guess$
185 IF ascii_code >= 97 AND ascii_code <= 122 THEN LET user_guess$ = CHR$ (ascii_code - 32)
190 LET found = 0
200 FOR i = 1 TO 8
210 IF clean_word$(i) = user_guess$ THEN LET guess_word$(i) = user_guess$ : LET found = 1
220 NEXT i
230 IF found <> 0 THEN GO TO 100
240 LET misses = misses + 1
250 GO TO 100
