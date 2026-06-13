970 REM ### Hammurabi ###
980 REM ### Resource management game based on the 1968 classic by Doug Dyment ###
990 REM ### Rule ancient Sumeria for 10 years ###
1000 RAND 0
1010 PRINT "HAMMURABI"
1020 PRINT "========="
1030 PRINT
1040 PRINT "Rule ancient Sumeria for 10 years"
1050 PRINT "Manage land, grain, and population"
1060 PRINT
1070 REM ### Initialise game state ###
1080 LET year = 1
1090 LET population = 100
1100 LET acres = 1000
1110 LET grain = 3000
1120 REM ### Deaths this year ###
1130 LET deaths = 0
1140 REM ### Total deaths ###
1150 LET total_deaths = 0
1160 REM ### Immigrants this year ###
1170 LET immigrants = 5
1180 REM ### Population at start of game ###
1190 LET pop_start = population
1200 REM ### Main game loop ###
1210 IF year > 10 THEN GOTO 2000
1220 PRINT
1230 PRINT "Year "; year; " of your rule"
1240 PRINT "=========================="
1250 PRINT
1260 REM ### Calculate land price ###
1270 LET land_price = INT(RND * 10) + 17
1280 REM ### Report status ###
1290 PRINT "Population: "; population
1300 PRINT "Acres owned: "; acres
1310 PRINT "Bushels in store: "; grain
1320 PRINT "People starved last year: "; deaths
1330 PRINT "People immigrated last year: "; immigrants
1340 PRINT
1350 PRINT "Land value: "; land_price; " bushels/acre"
1360 PRINT
1370 REM ### Buy or sell land ###
1380 PRINT "How many acres to BUY (- to SELL)? ";
1390 INPUT acres_to_buy
1400 IF acres_to_buy > 0 AND acres_to_buy * land_price > grain THEN GOTO 1800
1410 IF acres_to_buy < 0 AND ABS(acres_to_buy) > acres THEN GOTO 1820
1420 LET acres = acres + acres_to_buy
1430 LET grain = grain - acres_to_buy * land_price
1440 REM ### Feed people ###
1450 PRINT "How many bushels to FEED people? ";
1460 INPUT grain_to_feed
1470 IF grain_to_feed > grain THEN GOTO 1840
1480 LET grain = grain - grain_to_feed
1490 REM ### Plant crops ###
1500 PRINT "How many acres to PLANT? ";
1510 INPUT acres_to_plant
1520 IF acres_to_plant > acres THEN GOTO 1860
1530 IF acres_to_plant > grain * 2 THEN GOTO 1880
1540 IF acres_to_plant > population * 10 THEN GOTO 1900
1550 LET grain = grain - INT(acres_to_plant / 2)
1560 REM ### Calculate harvest ###
1570 LET yield = INT(RND * 5) + 1
1580 LET harvest = acres_to_plant * yield
1590 LET grain = grain + harvest
1600 PRINT "Harvest: "; yield; " bushels/acre"
1610 REM ### Calculate rat damage ###
1620 LET rat_chance = INT(RND * 5) + 1
1630 LET rat_loss = 0
1640 IF rat_chance <> 1 THEN GOTO 1670
1650 LET rat_loss = INT(grain * (RND * 0.3))
1660 LET grain = grain - rat_loss
1670 IF rat_loss > 0 THEN PRINT "Rats ate "; rat_loss; " bushels!"
1680 REM ### Calculate deaths from starvation ###
1690 LET deaths = population - INT(grain_to_feed / 20)
1700 IF deaths < 0 THEN LET deaths = 0
1710 IF deaths > population * 0.45 THEN GOTO 1920
1720 LET total_deaths = total_deaths + deaths
1730 LET population = population - deaths
1740 REM ### Calculate immigration ###
1750 LET immigrants = INT(deaths / 2 + (5 - yield) * grain / 600 + 1)
1760 IF immigrants < 0 THEN LET immigrants = 0
1770 LET population = population + immigrants
1780 LET year = year + 1
1790 GOTO 1210
1800 PRINT "You don't have enough grain!"
1810 GOTO 1380
1820 PRINT "You don't have that much land!"
1830 GOTO 1380
1840 PRINT "You don't have that much grain!"
1850 GOTO 1450
1860 PRINT "You don't have that much land!"
1870 GOTO 1500
1880 PRINT "You need 0.5 bushels per acre to plant!"
1890 GOTO 1500
1900 PRINT "Each person can only plant 10 acres!"
1910 GOTO 1500
1920 REM ### Impeached for poor performance ###
1930 REM ### Count the deaths that triggered it ###
1940 LET total_deaths = total_deaths + deaths
1950 PRINT
1960 PRINT "*** IMPEACHED ***"
1970 PRINT "Over 45% of your people starved!"
1980 PRINT "You have been thrown out of office!"
1990 GOTO 2050
2000 REM ### End of game ###
2010 PRINT
2020 PRINT "=========================="
2030 PRINT "End of 10-Year Rule"
2040 PRINT "=========================="
2050 REM ### Final statistics calculation ###
2060 IF population < 1 THEN LET population = 1
2070 LET acres_per_person = acres / population
2080 LET death_pct = (total_deaths * 100) / pop_start
2090 PRINT
2100 PRINT "Final population: "; population
2110 PRINT "Acres per person: "; acres_per_person
2120 PRINT "Total death rate: "; death_pct; "%"
2130 PRINT
2140 IF death_pct > 33 THEN GOTO 2200
2150 IF death_pct > 10 THEN GOTO 2230
2160 IF acres_per_person < 9 THEN GOTO 2260
2170 PRINT "*** EXCELLENT ***"
2180 PRINT "You are a great ruler!"
2190 GOTO 2280
2200 PRINT "*** POOR ***"
2210 PRINT "Your people suffered greatly."
2220 GOTO 2280
2230 PRINT "*** FAIR ***"
2240 PRINT "An adequate performance."
2250 GOTO 2280
2260 PRINT "*** GOOD ***"
2270 PRINT "A respectable reign."
2280 PRINT
2290 PRINT "Play again (year/N)? ";
2300 INPUT replay$
2310 IF replay$ = "year" OR replay$ = "y" THEN GOTO 1080
2320 PRINT "Thanks for playing!"
