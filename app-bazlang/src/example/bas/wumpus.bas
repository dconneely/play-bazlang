1000 REM ### Hunt the Wumpus ###
1010 REM ### Classic cave exploration game - find and shoot the Wumpus! ###
1020 RANDOMIZE
1030 GO SUB 2160
1040 GO SUB 2060
1050 REM ### Main game loop ###
1060 GO SUB 1230
1070 PRINT
1080 PRINT "Shoot or Move (S/M)? ";
1090 INPUT a$
1100 IF a$ = "S" OR a$ = "s" THEN GO SUB 1350
1110 IF a$ = "M" OR a$ = "m" THEN GO SUB 1730
1120 IF dead = 0 AND won = 0 THEN GO TO 1060
1130 REM ### Game over ###
1140 PRINT
1150 IF won = 1 THEN PRINT "HEE HEE HEE - THE WUMPUS'LL GET YOU NEXT TIME!!"
1160 IF dead = 1 THEN PRINT "HA HA HA - YOU LOSE!"
1170 PRINT
1180 PRINT "Play again? (Y/N) ";
1190 INPUT a$
1200 IF a$ = "Y" OR a$ = "y" THEN GO TO 1030
1210 STOP
1220 REM ### Show status ###
1230 PRINT
1240 FOR i = 1 TO 3
1250 LET room = tunnel(player_loc, i)
1260 IF room = wumpus_loc THEN PRINT "I SMELL A WUMPUS!"
1270 IF room = pit_1 OR room = pit_2 THEN PRINT "I FEEL A DRAFT"
1280 IF room = bats_1 OR room = bats_2 THEN PRINT "BATS NEARBY!"
1290 NEXT i
1300 PRINT
1310 PRINT "YOU ARE IN ROOM "; player_loc
1320 PRINT "TUNNELS LEAD TO "; tunnel(player_loc, 1); " "; tunnel(player_loc, 2); " "; tunnel(player_loc, 3)
1330 RETURN
1340 REM ### Shoot arrow ###
1350 PRINT "NO. OF ROOMS (1-5)? ";
1360 INPUT num_rooms
1370 IF num_rooms < 1 OR num_rooms > 5 THEN GO TO 1350
1380 DIM arrow_path(5)
1390 FOR i = 1 TO num_rooms
1400 PRINT "ROOM #? ";
1410 INPUT arrow_path(i)
1420 IF i = 1 THEN GO TO 1450
1430 IF arrow_path(i) = arrow_path(i - 1) THEN PRINT "ARROWS AREN'T THAT CROOKED - TRY ANOTHER ROOM"
1440 IF arrow_path(i) = arrow_path(i - 1) THEN GO TO 1400
1450 NEXT i
1460 REM ### Fly the arrow ###
1470 LET arrow_loc = player_loc
1480 FOR i = 1 TO num_rooms
1490 LET found = 0
1500 FOR j = 1 TO 3
1510 IF tunnel(arrow_loc, j) = arrow_path(i) THEN LET found = 1
1520 IF tunnel(arrow_loc, j) = arrow_path(i) THEN LET arrow_loc = arrow_path(i)
1530 NEXT j
1540 IF found = 0 THEN LET arrow_loc = tunnel(arrow_loc, INT (RND * 3) + 1)
1550 IF arrow_loc = wumpus_loc THEN GO TO 1650
1560 IF arrow_loc = player_loc THEN GO TO 1690
1570 NEXT i
1580 PRINT "MISSED"
1590 LET arrows = arrows - 1
1600 GO SUB 2020
1610 IF arrows = 0 THEN PRINT "YOU RAN OUT OF ARROWS..."
1620 IF arrows = 0 THEN LET dead = 1
1630 RETURN
1640 REM ### Arrow hit Wumpus ###
1650 PRINT "AHA! YOU GOT THE WUMPUS!"
1660 LET won = 1
1670 RETURN
1680 REM ### Arrow hit player ###
1690 PRINT "OUCH! ARROW GOT YOU!"
1700 LET dead = 1
1710 RETURN
1720 REM ### Move player ###
1730 PRINT "WHERE TO? ";
1740 INPUT new_loc
1750 LET valid_move = 0
1760 FOR i = 1 TO 3
1770 IF tunnel(player_loc, i) = new_loc THEN LET valid_move = 1
1780 NEXT i
1790 IF valid_move = 0 THEN PRINT "NOT POSSIBLE -"
1800 IF valid_move = 0 THEN GO TO 1730
1810 LET player_loc = new_loc
1820 REM ### Check hazards ###
1830 IF player_loc = wumpus_loc THEN GO TO 1880
1840 IF player_loc = pit_1 OR player_loc = pit_2 THEN GO TO 1940
1850 IF player_loc = bats_1 OR player_loc = bats_2 THEN GO TO 1980
1860 RETURN
1870 REM ### Bumped Wumpus ###
1880 PRINT "... OOPS! BUMPED A WUMPUS!"
1890 GO SUB 2020
1900 IF player_loc = wumpus_loc THEN PRINT "TSK TSK TSK - WUMPUS GOT YOU!"
1910 IF player_loc = wumpus_loc THEN LET dead = 1
1920 RETURN
1930 REM ### Fell in pit ###
1940 PRINT "YYYIIIIEEEE . . . FELL IN PIT"
1950 LET dead = 1
1960 RETURN
1970 REM ### Grabbed by bats ###
1980 PRINT "ZAP--SUPER BAT SNATCH! ELSEWHEREVILLE FOR YOU!"
1990 LET player_loc = INT (RND * 20) + 1
2000 GO TO 1830
2010 REM ### Move Wumpus ###
2020 LET k = INT (RND * 4)
2030 IF k < 3 THEN LET wumpus_loc = tunnel(wumpus_loc, k + 1)
2040 RETURN
2050 REM ### Set up cave (dodecahedron) ###
2060 DIM tunnel(20, 3) : RESTORE 2110
2070 FOR i = 1 TO 20
2080 READ tunnel(i, 1) : READ tunnel(i, 2) : READ tunnel(i, 3)
2090 NEXT i
2100 RETURN
2110 DATA 2, 5, 8, 1, 3, 10, 2, 4, 12, 3, 5, 14, 1, 4, 6
2120 DATA 5, 7, 15, 6, 8, 17, 1, 7, 9, 8, 10, 18, 2, 9, 11
2130 DATA 10, 12, 19, 3, 11, 13, 12, 14, 20, 4, 13, 15, 6, 14, 16
2140 DATA 15, 17, 20, 7, 16, 18, 9, 17, 19, 11, 18, 20, 13, 16, 19
2150 REM ### Place hazards and player ###
2160 LET dead = 0
2170 LET won = 0
2180 LET arrows = 5
2190 LET player_loc = INT (RND * 20) + 1
2200 LET wumpus_loc = INT (RND * 20) + 1
2210 IF wumpus_loc = player_loc THEN GO TO 2200
2220 LET pit_1 = INT (RND * 20) + 1
2230 IF pit_1 = player_loc OR pit_1 = wumpus_loc THEN GO TO 2220
2240 LET pit_2 = INT (RND * 20) + 1
2250 IF pit_2 = player_loc OR pit_2 = wumpus_loc OR pit_2 = pit_1 THEN GO TO 2240
2260 LET bats_1 = INT (RND * 20) + 1
2270 IF bats_1 = player_loc OR bats_1 = wumpus_loc OR bats_1 = pit_1 OR bats_1 = pit_2 THEN GO TO 2260
2280 LET bats_2 = INT (RND * 20) + 1
2290 IF bats_2 = player_loc OR bats_2 = wumpus_loc OR bats_2 = pit_1 OR bats_2 = pit_2 OR bats_2 = bats_1 THEN GO TO 2280
2300 PRINT
2310 PRINT "HUNT THE WUMPUS"
2320 PRINT
2330 PRINT "YOU HAVE 5 CROOKED ARROWS."
2340 PRINT "GOOD LUCK!"
2350 RETURN
