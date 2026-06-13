980 REM ### Hangman ###
990 REM ### Guess the hidden word before the man is hanged ###
1000 RAND 0
1010 LET rand_idx = INT(RND * 50) + 1
1020 RESTORE 2660
1030 FOR i = 1 TO rand_idx : READ target_word$ : NEXT i
2000 REM ### Find word length and set up working copy ###
2010 LET word_len = LEN(target_word$)
2040 LET clean_word$ = target_word$(1 TO word_len)
2050 LET guess_word$ = ""
2060 FOR i = 1 TO word_len
2070 LET guess_word$ = guess_word$ + "-"
2080 NEXT i
2090 LET misses = 0
2100 LET letters$ = ""
2110 REM ### Main loop ###
2120 CLS
2130 PRINT AT 0, 0; "Word: "; guess_word$
2140 PRINT AT 1, 0; "Misses: "; misses; "/6"
2150 PRINT AT 2, 0; "Guessed: "; letters$
2160 GOSUB 2440
2170 IF misses > 0 THEN GOSUB 2530
2180 IF guess_word$ = clean_word$ THEN GOTO 2400
2190 IF misses = 6 THEN GOTO 2420
2200 PRINT AT 22, 0; "Guess a letter: ";
2210 INPUT a$
2220 IF LEN(a$) < 1 THEN GOTO 2200
2230 LET user_guess$ = a$(1)
2240 LET ascii_code = CODE user_guess$
2250 IF ascii_code >= 97 AND ascii_code <= 122 THEN LET user_guess$ = CHR$(ascii_code - 32)
2260 FOR i = 1 TO LEN(letters$)
2270 IF letters$(i) = user_guess$ THEN GOTO 2200
2280 NEXT i
2290 FOR i = 1 TO word_len
2300 IF guess_word$(i) = user_guess$ THEN GOTO 2200
2310 NEXT i
2320 LET found = 0
2330 FOR i = 1 TO word_len
2340 IF clean_word$(i) = user_guess$ THEN LET guess_word$(i) = user_guess$ : LET found = 1
2350 NEXT i
2360 IF found <> 0 THEN GOTO 2120
2370 LET misses = misses + 1
2380 LET letters$ = letters$ + user_guess$ + " "
2390 GOTO 2120
2400 REM ### Win ###
2410 PRINT AT 20, 0; "You guessed it: "; clean_word$ : PRINT "You win!" : GOTO 2610
2420 REM ### Lose ###
2430 PRINT AT 20, 0; "You died! word was "; clean_word$ : GOTO 2610
2440 REM ### Subroutine: draw gallows ###
2450 PRINT AT 5, 25; "__________"
2460 PRINT AT 6, 35; "|"
2470 FOR y = 6 TO 18
2480 PRINT AT y, 25; "|"
2490 NEXT y
2500 PRINT AT 6, 26; "/"
2510 PRINT AT 18, 22; "________________"
2520 RETURN
2530 REM ### Subroutine: draw hangman ###
2540 IF misses >= 1 THEN PRINT AT 7, 35; "O"
2550 IF misses >= 2 THEN PRINT AT 8, 35; "|" : PRINT AT 9, 35; "|" : PRINT AT 10, 35; "|"
2560 IF misses >= 3 THEN PRINT AT 8, 34; "/"
2570 IF misses >= 4 THEN PRINT AT 8, 36; "\"
2580 IF misses >= 5 THEN PRINT AT 11, 34; "/"
2590 IF misses >= 6 THEN PRINT AT 11, 36; "\"
2600 RETURN
2610 REM ### Play again ###
2620 PRINT "Play again (y/n)? ";
2630 INPUT r$
2640 IF r$ = "Y" OR r$ = "y" THEN GOTO 1010
2650 PRINT "Thanks for playing!"
2660 REM ### Words data ###
2670 DATA "ELEPHANT", "MOUNTAIN", "SUNFLOWER", "HOSPITAL", "UMBRELLA", "PENGUIN", "ASTRONAUT", "TELESCOPE", "DIAMOND", "BUTTERFLY"
2680 DATA "FESTIVAL", "KANGAROO", "TREASURE", "ORCHESTRA", "CHAMPION", "ADVENTURE", "VOLCANO", "OCTOPUS", "SYMPHONY", "PYRAMID"
2690 DATA "BICYCLE", "AQUARIUM", "CHOCOLATE", "DETECTIVE", "GALAXY", "HARMONY", "ISLAND", "JUNGLE", "LIBRARY", "MYSTERY"
2700 DATA "NOTEBOOK", "OASIS", "PHANTOM", "QUARTZ", "RAINBOW", "SANDWICH", "TORNADO", "UNIVERSE", "VAMPIRE", "WATERFALL"
2710 DATA "XYLOPHONE", "YACHT", "ZEPPELIN", "BLIZZARD", "CARNIVAL", "DINOSAUR", "ECLIPSE", "FIREWORK", "GLACIER", "HORIZON"
