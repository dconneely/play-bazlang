980 REM ### Hunt the Wumpus ###
990 REM ### Classic cave exploration game - find and shoot the Wumpus! ###
1000 RAND 0
1010 GOSUB 1990
1020 GOSUB 1900

1029 REM ### Main game loop ###
1030 GOSUB 1180
1040 PRINT
1050 PRINT "Shoot or Move (S/M)? ";
1060 INPUT a$
1070 IF a$ = "S" OR a$ = "s" THEN GOSUB 1290
1080 IF a$ = "M" OR a$ = "m" THEN GOSUB 1630
1090 IF dead = 0 AND won = 0 THEN GOTO 1030

1099 REM ### Game over ###
1100 PRINT
1110 IF won = 1 THEN PRINT "HEE HEE HEE - THE WUMPUS'LL GET YOU NEXT TIME!!"
1120 IF dead = 1 THEN PRINT "HA HA HA - YOU LOSE!"
1130 PRINT
1140 PRINT "Play again? (Y/N) ";
1150 INPUT a$
1160 IF a$ = "Y" OR a$ = "y" THEN GOTO 1010
1170 STOP

1179 REM ### Show status ###
1180 PRINT
1190 FOR i = 1 TO 3
1200 LET room = tunnel(player_loc, i)
1210 IF room = wumpus_loc THEN PRINT "I SMELL A WUMPUS!"
1220 IF room = pit_1 OR room = pit_2 THEN PRINT "I FEEL A DRAFT"
1230 IF room = bats_1 OR room = bats_2 THEN PRINT "BATS NEARBY!"
1240 NEXT i
1250 PRINT
1260 PRINT "YOU ARE IN ROOM "; player_loc
1270 PRINT "TUNNELS LEAD TO "; tunnel(player_loc, 1); " "; tunnel(player_loc, 2); " "; tunnel(player_loc, 3)
1280 RETURN

1289 REM ### Shoot arrow ###
1290 PRINT "NO. OF ROOMS (1-5)? ";
1300 INPUT num_rooms
1310 IF num_rooms < 1 OR num_rooms > 5 THEN GOTO 1290
1320 DIM arrow_path(5)
1330 FOR i = 1 TO num_rooms
1340 PRINT "ROOM #? ";
1350 INPUT arrow_path(i)
1360 IF i = 1 THEN GOTO 1390
1370 IF arrow_path(i) = arrow_path(i - 1) THEN PRINT "ARROWS AREN'T THAT CROOKED - TRY ANOTHER ROOM"
1380 IF arrow_path(i) = arrow_path(i - 1) THEN GOTO 1340
1390 NEXT i

1399 REM ### Fly the arrow ###
1400 LET arrow_loc = player_loc
1410 FOR i = 1 TO num_rooms
1420 LET found = 0
1430 FOR j = 1 TO 3
1440 IF tunnel(arrow_loc, j) = arrow_path(i) THEN LET found = 1
1450 IF tunnel(arrow_loc, j) = arrow_path(i) THEN LET arrow_loc = arrow_path(i)
1460 NEXT j
1470 IF found = 0 THEN LET arrow_loc = tunnel(arrow_loc, INT(RND * 3) + 1)
1480 IF arrow_loc = wumpus_loc THEN GOTO 1570
1490 IF arrow_loc = player_loc THEN GOTO 1600
1500 NEXT i
1510 PRINT "MISSED"
1520 LET arrows = arrows - 1
1530 GOSUB 1870
1540 IF arrows = 0 THEN PRINT "YOU RAN OUT OF ARROWS..."
1550 IF arrows = 0 THEN LET dead = 1
1560 RETURN

1569 REM ### Arrow hit Wumpus ###
1570 PRINT "AHA! YOU GOT THE WUMPUS!"
1580 LET won = 1
1590 RETURN

1599 REM ### Arrow hit player ###
1600 PRINT "OUCH! ARROW GOT YOU!"
1610 LET dead = 1
1620 RETURN

1629 REM ### Move player ###
1630 PRINT "WHERE TO? ";
1640 INPUT new_loc
1650 LET valid_move = 0
1660 FOR i = 1 TO 3
1670 IF tunnel(player_loc, i) = new_loc THEN LET valid_move = 1
1680 NEXT i
1690 IF valid_move = 0 THEN PRINT "NOT POSSIBLE -"
1700 IF valid_move = 0 THEN GOTO 1630
1710 LET player_loc = new_loc

1719 REM ### Check hazards ###
1720 IF player_loc = wumpus_loc THEN GOTO 1760
1730 IF player_loc = pit_1 OR player_loc = pit_2 THEN GOTO 1810
1740 IF player_loc = bats_1 OR player_loc = bats_2 THEN GOTO 1840
1750 RETURN

1759 REM ### Bumped Wumpus ###
1760 PRINT "... OOPS! BUMPED A WUMPUS!"
1770 GOSUB 1870
1780 IF player_loc = wumpus_loc THEN PRINT "TSK TSK TSK - WUMPUS GOT YOU!"
1790 IF player_loc = wumpus_loc THEN LET dead = 1
1800 RETURN

1809 REM ### Fell in pit ###
1810 PRINT "YYYIIIIEEEE . . . FELL IN PIT"
1820 LET dead = 1
1830 RETURN

1839 REM ### Grabbed by bats ###
1840 PRINT "ZAP--SUPER BAT SNATCH! ELSEWHEREVILLE FOR YOU!"
1850 LET player_loc = INT(RND * 20) + 1
1860 GOTO 1720

1869 REM ### Move Wumpus ###
1870 LET k = INT(RND * 4)
1880 IF k < 3 THEN LET wumpus_loc = tunnel(wumpus_loc, k + 1)
1890 RETURN

1899 REM ### Set up cave (dodecahedron) ###
1900 DIM tunnel(20, 3) : RESTORE 1950
1910 FOR i = 1 TO 20
1920 READ tunnel(i, 1) : READ tunnel(i, 2) : READ tunnel(i, 3)
1930 NEXT i
1940 RETURN
1950 DATA 2, 5, 8, 1, 3, 10, 2, 4, 12, 3, 5, 14, 1, 4, 6
1960 DATA 5, 7, 15, 6, 8, 17, 1, 7, 9, 8, 10, 18, 2, 9, 11
1970 DATA 10, 12, 19, 3, 11, 13, 12, 14, 20, 4, 13, 15, 6, 14, 16
1980 DATA 15, 17, 20, 7, 16, 18, 9, 17, 19, 11, 18, 20, 13, 16, 19

1989 REM ### Place hazards and player ###
1990 LET dead = 0
2000 LET won = 0
2010 LET arrows = 5
2020 LET player_loc = INT(RND * 20) + 1
2030 LET wumpus_loc = INT(RND * 20) + 1
2040 IF wumpus_loc = player_loc THEN GOTO 2030
2050 LET pit_1 = INT(RND * 20) + 1
2060 IF pit_1 = player_loc OR pit_1 = wumpus_loc THEN GOTO 2050
2070 LET pit_2 = INT(RND * 20) + 1
2080 IF pit_2 = player_loc OR pit_2 = wumpus_loc OR pit_2 = pit_1 THEN GOTO 2070
2090 LET bats_1 = INT(RND * 20) + 1
2100 IF bats_1 = player_loc OR bats_1 = wumpus_loc OR bats_1 = pit_1 OR bats_1 = pit_2 THEN GOTO 2090
2110 LET bats_2 = INT(RND * 20) + 1
2120 IF bats_2 = player_loc OR bats_2 = wumpus_loc OR bats_2 = pit_1 OR bats_2 = pit_2 OR bats_2 = bats_1 THEN GOTO 2110
2130 PRINT
2140 PRINT "HUNT THE WUMPUS"
2150 PRINT
2160 PRINT "YOU HAVE 5 CROOKED ARROWS."
2170 PRINT "GOOD LUCK!"
2180 RETURN
