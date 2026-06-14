1000 REM ### Blackjack ###
1010 REM ### Classic card game - try to get 21 without going bust ###
1020 RAND 0
1040 LET money = 1000 : LET dealer_money = 10000
1050 DIM deck(52)
1060 DIM player_hand(10)
1070 DIM dealer_hand(10)
1080 GOSUB 3500
1100 REM ### Start of round ###
1105 CLS
1110 PRINT AT 0,0; "Player: $"; money; "   Dealer: $"; dealer_money; "        "
1120 IF money <= 0 THEN PRINT AT 10,10; "Game over! You went broke." : STOP
1130 IF dealer_money <= 0 THEN PRINT AT 10,10; "Game over! You broke the bank!" : STOP
1140 PRINT AT 1,0; "Choose bet: (1) $10  (2) $50  (3) $100  (4) All in!  (5) Custom"
1200 LET k$ = INKEY$
1210 IF k$ = "1" THEN LET bet = 10 : GOTO 1300
1220 IF k$ = "2" THEN LET bet = 50 : GOTO 1300
1230 IF k$ = "3" THEN LET bet = 100 : GOTO 1300
1240 IF k$ = "4" THEN LET bet = money : GOTO 1300
1245 IF k$ = "5" THEN GOTO 1260
1250 GOTO 1200
1260 PRINT AT 2,0; "Enter bet amount: $";
1270 INPUT bet
1280 IF bet <= 0 THEN PRINT AT 3,0; "Must be > $0!          " : GOTO 1260
1290 IF bet > money THEN PRINT AT 3,0; "Insufficient funds!   " : GOTO 1260
1300 IF bet > money THEN GOTO 1200
1310 CLS
1320 PRINT AT 0,0; "Player: $"; money; "   Dealer: $"; dealer_money; "   Bet: $"; bet; "    "
1330 REM ### Start game ###
1340 REM ### Deal ###
1350 LET player_cards = 0
1360 LET dealer_cards = 0
1370 GOSUB 4000
1380 LET player_hand(1) = c
1390 LET player_cards = 1
1400 GOSUB 4000
1410 LET dealer_hand(1) = c
1420 LET dealer_cards = 1
1430 GOSUB 4000
1440 LET player_hand(2) = c
1450 LET player_cards = 2
1460 GOSUB 4000
1470 LET dealer_hand(2) = c
1480 LET dealer_cards = 2
1500 REM ### Draw initial ###
1510 PRINT AT 3,0; "Dealer:"
1520 LET draw_x = 0
1530 LET draw_y = 4
1540 LET c = dealer_hand(1)
1550 GOSUB 5000
1560 LET draw_x = 6
1570 LET c = 0
1580 GOSUB 5000
1600 PRINT AT 9,0; "Player:"
1610 LET draw_x = 0
1620 LET draw_y = 10
1630 FOR i = 1 TO player_cards
1640 LET c = player_hand(i)
1650 GOSUB 5000
1660 LET draw_x = draw_x + 6
1670 NEXT i
1700 REM ## Player turn ###
1710 PRINT AT 15,0; "Stick (S) or Twist (T)?"
1720 LET k$ = INKEY$
1730 IF k$ = "t" OR k$ = "T" THEN GOTO 1800
1740 IF k$ = "s" OR k$ = "S" THEN GOTO 1900
1750 GOTO 1720
1800 REM ### Player twist / hit ###
1810 GOSUB 4000
1820 LET player_cards = player_cards + 1
1830 LET player_hand(player_cards) = c
1840 LET draw_x = (player_cards-1)*6
1850 LET draw_y = 10
1860 GOSUB 5000
1870 GOSUB 4500
1880 IF s > 21 THEN PRINT AT 15,0; "Bust! you lose.           " : LET money = money - bet : LET dealer_money = dealer_money + bet : GOTO 3000
1890 GOTO 1710
1900 REM ### Dealer turn ###
1910 LET draw_x = 6
1920 LET draw_y = 4
1930 LET c = dealer_hand(2)
1940 GOSUB 5000
2000 REM ### Calc dealer score dealer_score ###
2010 LET s = 0
2020 LET ace_count = 0
2030 FOR i = 1 TO dealer_cards
2040 LET v = (dealer_hand(i)-1) - INT((dealer_hand(i)-1)/13)*13 + 1
2050 IF v > 10 THEN LET v = 10
2060 IF v = 1 THEN LET ace_count = ace_count + 1
2070 LET s = s + v
2080 NEXT i
2090 IF ace_count > 0 AND s <= 11 THEN LET s = s + 10
2100 LET dealer_score = s
2110 IF dealer_score >= 17 THEN GOTO 2500
2120 GOSUB 4000
2130 LET dealer_cards = dealer_cards + 1
2140 LET dealer_hand(dealer_cards) = c
2150 LET draw_x = (dealer_cards-1)*6
2160 LET draw_y = 4
2170 GOSUB 5000
2180 PAUSE 20
2190 GOTO 2000
2500 REM ### End round ###
2510 GOSUB 4500
2520 LET player_score = s
2530 PRINT AT 15,0; "Player: "; player_score; "  dealer: "; dealer_score; "      "
2540 IF dealer_score > 21 THEN PRINT "Dealer bust! you win!" : LET money = money + bet : LET dealer_money = dealer_money - bet : GOTO 3000
2550 IF player_score > dealer_score THEN PRINT "You win!" : LET money = money + bet : LET dealer_money = dealer_money - bet : GOTO 3000
2560 IF player_score < dealer_score THEN PRINT "Dealer wins." : LET money = money - bet : LET dealer_money = dealer_money + bet : GOTO 3000
2570 PRINT "Push." : GOTO 3000
3000 PRINT "Press any key"
3010 IF INKEY$ = "" THEN GOTO 3010
3020 GOTO 1100
3500 REM ### Init deck ###
3510 FOR i = 1 TO 52
3520 LET deck(i) = i
3530 NEXT i
3540 FOR i = 1 TO 52
3550 LET r = INT(RND * 52) + 1
3560 LET t = deck(i)
3570 LET deck(i) = deck(r)
3580 LET deck(r) = t
3590 NEXT i
3600 LET deck_pos = 1
3610 RETURN
4000 REM ### Draw card ###
4010 IF deck_pos > 52 THEN GOSUB 3500
4020 LET c = deck(deck_pos)
4030 LET deck_pos = deck_pos + 1
4040 RETURN
4500 REM ### Calc player score ###
4510 LET s = 0
4520 LET ace_count = 0
4530 FOR i = 1 TO player_cards
4540 LET v = (player_hand(i)-1) - INT((player_hand(i)-1)/13)*13 + 1
4550 IF v > 10 THEN LET v = 10
4560 IF v = 1 THEN LET ace_count = ace_count + 1
4570 LET s = s + v
4580 NEXT i
4590 IF ace_count > 0 AND s <= 11 THEN LET s = s + 10
4600 RETURN
5000 REM ### Draw card c at draw_x,draw_y ###
5005 IF c = 0 THEN LET val_str$ = "?" : LET suit_str$ = "?" : GOTO 5140
5010 LET v = (c-1) - INT((c-1)/13)*13 + 1
5020 LET suit = INT((c-1)/13)
5030 LET val_str$ = STR$(v)
5040 IF v = 1 THEN LET val_str$ = "A"
5050 IF v = 11 THEN LET val_str$ = "J"
5060 IF v = 12 THEN LET val_str$ = "Q"
5070 IF v = 13 THEN LET val_str$ = "K"
5080 LET suit_str$ = "?"
5090 IF suit = 0 THEN LET suit_str$ = UCHR$(9824)
5100 IF suit = 1 THEN LET suit_str$ = UCHR$(9825)
5110 IF suit = 2 THEN LET suit_str$ = UCHR$(9827)
5120 IF suit = 3 THEN LET suit_str$ = UCHR$(9826)
5130 REM draw the card box
5140 PRINT AT draw_y, draw_x; UCHR$(9484); UCHR$(9472); UCHR$(9472); UCHR$(9472); UCHR$(9488)
5150 IF LEN(val_str$) = 2 THEN PRINT AT draw_y+1, draw_x; UCHR$(9474); val_str$; " "; UCHR$(9474)
5160 IF LEN(val_str$) = 1 THEN PRINT AT draw_y+1, draw_x; UCHR$(9474); val_str$; "  "; UCHR$(9474)
5170 PRINT AT draw_y+2, draw_x; UCHR$(9474); " "; suit_str$; AT draw_y+2, draw_x+4; UCHR$(9474)
5180 PRINT AT draw_y+3, draw_x; UCHR$(9492); UCHR$(9472); UCHR$(9472); UCHR$(9472); UCHR$(9496)
5190 RETURN
