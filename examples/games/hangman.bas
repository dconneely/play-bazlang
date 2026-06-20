1000 REM ### Hangman ###
1010 REM ### Guess the hidden word before the man is hanged ###
1020 RANDOMIZE
1030 PLOTMODE 4
1040 LET tw = TEXTW : LET th = TEXTH
1050 LET ox = 0 : LET oy = 0 : LET eh = th
1060 IF tw > 80 THEN LET ox = INT ((tw - 80) / 2)
1070 IF th > 25 THEN LET oy = INT ((th - 25) / 2) : LET eh = 25
1080 LET rand_idx = INT (RND * 50) + 1
1090 RESTORE 6000
1100 FOR i = 1 TO rand_idx : READ target_word$ : NEXT i
2000 REM ### Find word length and set up working copy ###
2010 LET word_len = LEN (target_word$)
2020 LET clean_word$ = target_word$(1 TO word_len)
2030 LET guess_word$ = ""
2040 FOR i = 1 TO word_len
2050 LET guess_word$ = guess_word$ + "-"
2060 NEXT i
2070 LET misses = 0
2080 LET letters$ = ""
2090 REM ### Main loop ###
2100 PAPER -1 : INK 4 : CLS
2110 PRINT AT oy, ox; INK 4; "Word: "; INK 5; guess_word$
2120 PRINT AT oy + 1, ox; INK 4; "Misses: "; INK 2; misses; INK 4; "/6"
2130 PRINT AT oy + 2, ox; INK 4; "Guessed: "; INK 6; letters$
2140 GO SUB 3000
2150 IF misses > 0 THEN GO SUB 4000
2160 IF guess_word$ = clean_word$ THEN GO TO 2390
2170 IF misses = 6 THEN GO TO 2410
2180 PRINT AT oy + eh - 2, ox; INK 4; "Guess a letter: "; INK 5; 
2190 LET a$ = UINKEY$
2200 IF LEN (a$) <> 1 THEN GO TO 2190
2210 PRINT a$; 
2220 LET user_guess$ = a$(1)
2230 LET ascii_code = CODE user_guess$
2240 IF ascii_code >= 97 AND ascii_code <= 122 THEN LET user_guess$ = CHR$ (ascii_code - 32)
2250 FOR i = 1 TO LEN (letters$)
2260 IF letters$(i) = user_guess$ THEN GO TO 2180
2270 NEXT i
2280 FOR i = 1 TO word_len
2290 IF guess_word$(i) = user_guess$ THEN GO TO 2180
2300 NEXT i
2310 LET found = 0
2320 FOR i = 1 TO word_len
2330 IF clean_word$(i) = user_guess$ THEN LET guess_word$(i) = user_guess$ : LET found = 1
2340 NEXT i
2350 IF found <> 0 THEN GO TO 2100
2360 LET misses = misses + 1
2370 LET letters$ = letters$ + user_guess$ + " "
2380 GO TO 2100
2390 REM ### Win ###
2400 PRINT AT oy + eh - 4, ox; INK 6; "You guessed it: "; INK 5; clean_word$ : PRINT AT oy + eh - 3, ox; INK 4; "You win!" : GO TO 5000
2410 REM ### Lose ###
2420 PRINT AT oy + eh - 4, ox; INK 2; "You died! It was: "; INK 5; clean_word$ : GO TO 5000
3000 REM ### Subroutine: Render gallows ###
3010 LET cx = INT (PLOTW / 2) : LET cy = INT (PLOTH / 2) : INK 6
3020 PLOT cx - 30, cy - 20 : DRAW 60, 0
3030 PLOT cx - 10, cy - 20 : DRAW 0, 36
3040 DRAW 30, 0 : DRAW 0, -6
3050 PLOT cx - 10, cy + 10 : DRAW 12, 6
3060 RETURN
4000 REM ### Subroutine: Render hangman ###
4010 LET cx = INT (PLOTW / 2) : LET cy = INT (PLOTH / 2) : INK 7
4020 IF misses >= 1 THEN PLOT cx + 17, cy + 7 : DRAW 0, 2 : DRAW 1, 1 : DRAW 4, 0 : DRAW 1, -1 : DRAW 0, -2 : DRAW -1, -1 : DRAW -4, 0 : DRAW -1, 1
4030 IF misses >= 2 THEN PLOT cx + 20, cy + 6 : DRAW 0, -10
4040 IF misses >= 3 THEN PLOT cx + 20, cy + 4 : DRAW -8, -4
4050 IF misses >= 4 THEN PLOT cx + 20, cy + 4 : DRAW 8, -4
4060 IF misses >= 5 THEN PLOT cx + 20, cy - 4 : DRAW -8, -6
4070 IF misses >= 6 THEN PLOT cx + 20, cy - 4 : DRAW 8, -6
4080 RETURN
5000 REM ### Play again ###
5010 PRINT AT oy + eh - 1, ox; INK 4; "Play again (Y/N)? "; INK 5; 
5020 LET r$ = UINKEY$
5030 IF LEN (r$) <> 1 THEN GO TO 5020
5040 PRINT r$
5050 IF r$ = "Y" OR r$ = "y" THEN GO TO 1080
5060 PRINT AT oy + eh - 1, ox + 18; INK 6; "Thanks for playing!"
5070 INK -1 : PAPER -1
6000 REM ### Word data ###
6010 DATA "ELEPHANT", "MOUNTAIN", "SUNFLOWER", "HOSPITAL", "UMBRELLA", "PENGUIN", "ASTRONAUT", "TELESCOPE", "DIAMOND", "BUTTERFLY"
6020 DATA "FESTIVAL", "KANGAROO", "TREASURE", "ORCHESTRA", "CHAMPION", "ADVENTURE", "VOLCANO", "OCTOPUS", "SYMPHONY", "PYRAMID"
6030 DATA "BICYCLE", "AQUARIUM", "CHOCOLATE", "DETECTIVE", "GALAXY", "HARMONY", "ISLAND", "JUNGLE", "LIBRARY", "MYSTERY"
6040 DATA "NOTEBOOK", "OASIS", "PHANTOM", "QUARTZ", "RAINBOW", "SANDWICH", "TORNADO", "UNIVERSE", "VAMPIRE", "WATERFALL"
6050 DATA "XYLOPHONE", "YACHT", "ZEPPELIN", "BLIZZARD", "CARNIVAL", "DINOSAUR", "ECLIPSE", "FIREWORK", "GLACIER", "HORIZON"
