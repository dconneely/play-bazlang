1000 REM ### Hammurabi ###
1010 REM ### Resource management game based on the 1968 classic by Doug Dyment ###
1020 REM ### Rule ancient Sumeria for 10 years ###
1030 RANDOMIZE
1040 PRINT "HAMMURABI"
1050 PRINT "========="
1060 PRINT
1070 PRINT "Rule ancient Sumeria for 10 years"
1080 PRINT "Manage land, grain, and population"
1090 PRINT
1100 REM ### Initialise game state ###
1110 LET year = 1
1120 LET population = 100
1130 LET acres = 1000
1140 LET grain = 3000
1150 REM ### Deaths this year ###
1160 LET deaths = 0
1170 REM ### Total deaths ###
1180 LET total_deaths = 0
1190 REM ### Immigrants this year ###
1200 LET immigrants = 5
1210 REM ### Population at start of game ###
1220 LET pop_start = population
1230 REM ### Main game loop ###
1240 IF year > 10 THEN GO TO 2030
1250 PRINT
1260 PRINT "Year "; year; " of your rule"
1270 PRINT "=========================="
1280 PRINT
1290 REM ### Calculate land price ###
1300 LET land_price = INT (RND * 10) + 17
1310 REM ### Report status ###
1320 PRINT "Population: "; population
1330 PRINT "Acres owned: "; acres
1340 PRINT "Bushels in store: "; grain
1350 PRINT "People starved last year: "; deaths
1360 PRINT "People immigrated last year: "; immigrants
1370 PRINT
1380 PRINT "Land value: "; land_price; " bushels/acre"
1390 PRINT
1400 REM ### Buy or sell land ###
1410 PRINT "How many acres to BUY (- to SELL)? "; 
1420 INPUT acres_to_buy
1430 IF acres_to_buy > 0 AND acres_to_buy * land_price > grain THEN GO TO 1830
1440 IF acres_to_buy < 0 AND ABS (acres_to_buy) > acres THEN GO TO 1850
1450 LET acres = acres + acres_to_buy
1460 LET grain = grain - acres_to_buy * land_price
1470 REM ### Feed people ###
1480 PRINT "How many bushels to FEED people? "; 
1490 INPUT grain_to_feed
1500 IF grain_to_feed > grain THEN GO TO 1870
1510 LET grain = grain - grain_to_feed
1520 REM ### Plant crops ###
1530 PRINT "How many acres to PLANT? "; 
1540 INPUT acres_to_plant
1550 IF acres_to_plant > acres THEN GO TO 1890
1560 IF acres_to_plant > grain * 2 THEN GO TO 1910
1570 IF acres_to_plant > population * 10 THEN GO TO 1930
1580 LET grain = grain - INT (acres_to_plant / 2)
1590 REM ### Calculate harvest ###
1600 LET yield = INT (RND * 5) + 1
1610 LET harvest = acres_to_plant * yield
1620 LET grain = grain + harvest
1630 PRINT "Harvest: "; yield; " bushels/acre"
1640 REM ### Calculate rat damage ###
1650 LET rat_chance = INT (RND * 5) + 1
1660 LET rat_loss = 0
1670 IF rat_chance <> 1 THEN GO TO 1700
1680 LET rat_loss = INT (grain * (RND * 0.3))
1690 LET grain = grain - rat_loss
1700 IF rat_loss > 0 THEN PRINT "Rats ate "; rat_loss; " bushels!"
1710 REM ### Calculate deaths from starvation ###
1720 LET deaths = population - INT (grain_to_feed / 20)
1730 IF deaths < 0 THEN LET deaths = 0
1740 IF deaths > population * 0.45 THEN GO TO 1950
1750 LET total_deaths = total_deaths + deaths
1760 LET population = population - deaths
1770 REM ### Calculate immigration ###
1780 LET immigrants = INT (deaths / 2 + (5 - yield) * grain / 600 + 1)
1790 IF immigrants < 0 THEN LET immigrants = 0
1800 LET population = population + immigrants
1810 LET year = year + 1
1820 GO TO 1240
1830 PRINT "You don't have enough grain!"
1840 GO TO 1410
1850 PRINT "You don't have that much land!"
1860 GO TO 1410
1870 PRINT "You don't have that much grain!"
1880 GO TO 1480
1890 PRINT "You don't have that much land!"
1900 GO TO 1530
1910 PRINT "You need 0.5 bushels per acre to plant!"
1920 GO TO 1530
1930 PRINT "Each person can only plant 10 acres!"
1940 GO TO 1530
1950 REM ### Impeached for poor performance ###
1960 REM ### Count the deaths that triggered it ###
1970 LET total_deaths = total_deaths + deaths
1980 PRINT
1990 PRINT "*** IMPEACHED ***"
2000 PRINT "Over 45% of your people starved!"
2010 PRINT "You have been thrown out of office!"
2020 GO TO 2080
2030 REM ### End of game ###
2040 PRINT
2050 PRINT "=========================="
2060 PRINT "End of 10-Year Rule"
2070 PRINT "=========================="
2080 REM ### Final statistics calculation ###
2090 IF population < 1 THEN LET population = 1
2100 LET acres_per_person = acres / population
2110 LET death_pct = (total_deaths * 100) / pop_start
2120 PRINT
2130 PRINT "Final population: "; population
2140 PRINT "Acres per person: "; acres_per_person
2150 PRINT "Total death rate: "; death_pct; "%"
2160 PRINT
2170 IF death_pct > 33 THEN GO TO 2230
2180 IF death_pct > 10 THEN GO TO 2260
2190 IF acres_per_person < 9 THEN GO TO 2290
2200 PRINT "*** EXCELLENT ***"
2210 PRINT "You are a great ruler!"
2220 GO TO 2310
2230 PRINT "*** POOR ***"
2240 PRINT "Your people suffered greatly."
2250 GO TO 2310
2260 PRINT "*** FAIR ***"
2270 PRINT "An adequate performance."
2280 GO TO 2310
2290 PRINT "*** GOOD ***"
2300 PRINT "A respectable reign."
2310 PRINT
2320 PRINT "Play again (Y/N)? "; 
2330 INPUT replay$
2340 IF replay$ = "Y" OR replay$ = "y" THEN GO TO 1110
2350 PRINT "Thanks for playing!"
