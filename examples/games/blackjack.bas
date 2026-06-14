1000 REM ### Blackjack ###
1010 REM ### Classic card game - try to get 21 without going bust ###
1020 RANDOMIZE
1030 LET money = 1000 : LET dealer_money = 10000
1040 LET ox = 0 : LET oy = 0
1050 IF PRINTW > 80 THEN LET ox = INT ((PRINTW - 80) / 2)
1060 IF PRINTH > 25 THEN LET oy = INT ((PRINTH - 25) / 2)
1070 DIM deck(52)
1080 DIM player_hand(10)
1090 DIM dealer_hand(10)
1100 GO SUB 7500
2000 REM ### Start of round ###
2010 CLS
2020 PRINT AT oy, ox; "Player: $"; money; "   Dealer: $"; dealer_money; "        "
2030 IF money <= 0 THEN PRINT AT oy + 10, ox + 10; "Game over! You went broke." : STOP
2040 IF dealer_money <= 0 THEN PRINT AT oy + 10, ox + 10; "Game over! You broke the bank!" : STOP
2050 PRINT AT oy + 1, ox; "Choose bet: (1) $10  (2) $50  (3) $100  (4) All in!  (5) Custom"
2500 LET k$ = INKEY$
2510 IF k$ = "1" THEN LET bet = 10 : GO TO 3000
2520 IF k$ = "2" THEN LET bet = 50 : GO TO 3000
2530 IF k$ = "3" THEN LET bet = 100 : GO TO 3000
2540 IF k$ = "4" THEN LET bet = money : GO TO 3000
2550 IF k$ = "5" THEN GO TO 2570
2560 GO TO 2500
2570 PRINT AT oy + 2, ox; "Enter bet amount: $"; 
2580 INPUT bet
2590 IF bet <= 0 THEN PRINT AT oy + 3, ox; "Must be > $0!          " : GO TO 2570
2600 IF bet > money THEN PRINT AT oy + 3, ox; "Insufficient funds!   " : GO TO 2570
3000 IF bet > money THEN GO TO 2500
3010 CLS
3020 PRINT AT oy, ox; "Player: $"; money; "   Dealer: $"; dealer_money; "   Bet: $"; bet; "    "
3030 REM ### Start game ###
3040 REM ### Deal ###
3050 LET player_cards = 0
3060 LET dealer_cards = 0
3070 GO SUB 8000
3080 LET player_hand(1) = c
3090 LET player_cards = 1
3100 GO SUB 8000
3110 LET dealer_hand(1) = c
3120 LET dealer_cards = 1
3130 GO SUB 8000
3140 LET player_hand(2) = c
3150 LET player_cards = 2
3160 GO SUB 8000
3170 LET dealer_hand(2) = c
3180 LET dealer_cards = 2
3500 REM ### Draw initial ###
3510 PRINT AT oy + 3, ox; "Dealer:"
3520 LET draw_x = ox
3530 LET draw_y = oy + 4
3540 LET c = dealer_hand(1)
3550 GO SUB 9000
3560 LET draw_x = ox + 6
3570 LET c = 0
3580 GO SUB 9000
4000 PRINT AT oy + 9, ox; "Player:"
4010 LET draw_x = ox
4020 LET draw_y = oy + 10
4030 FOR i = 1 TO player_cards
4040 LET c = player_hand(i)
4050 GO SUB 9000
4060 LET draw_x = draw_x + 6
4070 NEXT i
4500 REM ## Player turn ###
4510 PRINT AT oy + 15, ox; "Stick (S) or Twist (T)?"
4520 LET k$ = INKEY$
4530 IF k$ = "t" OR k$ = "T" THEN GO TO 5000
4540 IF k$ = "s" OR k$ = "S" THEN GO TO 5500
4550 GO TO 4520
5000 REM ### Player twist / hit ###
5010 GO SUB 8000
5020 LET player_cards = player_cards + 1
5030 LET player_hand(player_cards) = c
5040 LET draw_x = ox + (player_cards - 1) * 6
5050 LET draw_y = oy + 10
5060 GO SUB 9000
5070 GO SUB 8500
5080 IF s > 21 THEN PRINT AT oy + 15, ox; "Bust! you lose.           " : LET money = money - bet : LET dealer_money = dealer_money + bet : GO TO 7000
5090 GO TO 4500
5500 REM ### Dealer turn ###
5510 LET draw_x = ox + 6
5520 LET draw_y = oy + 4
5530 LET c = dealer_hand(2)
5540 GO SUB 9000
6000 REM ### Calc dealer score dealer_score ###
6010 LET s = 0
6020 LET ace_count = 0
6030 FOR i = 1 TO dealer_cards
6040 LET v = (dealer_hand(i) - 1) - INT ((dealer_hand(i) - 1) / 13) * 13 + 1
6050 IF v > 10 THEN LET v = 10
6060 IF v = 1 THEN LET ace_count = ace_count + 1
6070 LET s = s + v
6080 NEXT i
6090 IF ace_count > 0 AND s <= 11 THEN LET s = s + 10
6100 LET dealer_score = s
6110 IF dealer_score >= 17 THEN GO TO 6500
6120 GO SUB 8000
6130 LET dealer_cards = dealer_cards + 1
6140 LET dealer_hand(dealer_cards) = c
6150 LET draw_x = ox + (dealer_cards - 1) * 6
6160 LET draw_y = oy + 4
6170 GO SUB 9000
6180 PAUSE 20
6190 GO TO 6000
6500 REM ### End round ###
6510 GO SUB 8500
6520 LET player_score = s
6530 PRINT AT oy + 15, ox; "Player: "; player_score; "  dealer: "; dealer_score; "      "
6540 IF dealer_score > 21 THEN PRINT AT oy + 16, ox; "Dealer bust! you win!" : LET money = money + bet : LET dealer_money = dealer_money - bet : GO TO 7000
6550 IF player_score > dealer_score THEN PRINT AT oy + 16, ox; "You win!" : LET money = money + bet : LET dealer_money = dealer_money - bet : GO TO 7000
6560 IF player_score < dealer_score THEN PRINT AT oy + 16, ox; "Dealer wins." : LET money = money - bet : LET dealer_money = dealer_money + bet : GO TO 7000
6570 PRINT AT oy + 16, ox; "Push." : GO TO 7000
7000 PRINT AT oy + 17, ox; "Press any key"
7010 IF UINKEY$ = "" THEN GO TO 7010
7020 GO TO 2000
7500 REM ### Init deck ###
7510 FOR i = 1 TO 52
7520 LET deck(i) = i
7530 NEXT i
7540 FOR i = 1 TO 52
7550 LET r = INT (RND * 52) + 1
7560 LET t = deck(i)
7570 LET deck(i) = deck(r)
7580 LET deck(r) = t
7590 NEXT i
7600 LET deck_pos = 1
7610 RETURN
8000 REM ### Draw a card ###
8010 IF deck_pos > 52 THEN GO SUB 7500
8020 LET c = deck(deck_pos)
8030 LET deck_pos = deck_pos + 1
8040 RETURN
8500 REM ### Calc player score ###
8510 LET s = 0
8520 LET ace_count = 0
8530 FOR i = 1 TO player_cards
8540 LET v = (player_hand(i) - 1) - INT ((player_hand(i) - 1) / 13) * 13 + 1
8550 IF v > 10 THEN LET v = 10
8560 IF v = 1 THEN LET ace_count = ace_count + 1
8570 LET s = s + v
8580 NEXT i
8590 IF ace_count > 0 AND s <= 11 THEN LET s = s + 10
8600 RETURN
9000 REM ### Render card c at draw_x,draw_y ###
9010 IF c = 0 THEN LET val_str$ = "?" : LET suit_str$ = "?" : GO TO 9510
9020 LET v = (c - 1) - INT ((c - 1) / 13) * 13 + 1
9030 LET suit = INT ((c - 1) / 13)
9040 LET val_str$ = STR$ (v)
9050 IF v = 1 THEN LET val_str$ = "A"
9060 IF v = 11 THEN LET val_str$ = "J"
9070 IF v = 12 THEN LET val_str$ = "Q"
9080 IF v = 13 THEN LET val_str$ = "K"
9090 LET suit_str$ = "?"
9100 IF suit = 0 THEN LET suit_str$ = UCHR$ (9824)
9110 IF suit = 1 THEN LET suit_str$ = UCHR$ (9825)
9120 IF suit = 2 THEN LET suit_str$ = UCHR$ (9827)
9130 IF suit = 3 THEN LET suit_str$ = UCHR$ (9826)
9500 REM ### Render the card box ###
9510 PRINT AT draw_y, draw_x; UCHR$ (9484); UCHR$ (9472); UCHR$ (9472); UCHR$ (9472); UCHR$ (9488)
9520 IF LEN (val_str$) = 2 THEN PRINT AT draw_y + 1, draw_x; UCHR$ (9474); val_str$; " "; UCHR$ (9474)
9530 IF LEN (val_str$) = 1 THEN PRINT AT draw_y + 1, draw_x; UCHR$ (9474); val_str$; "  "; UCHR$ (9474)
9540 PRINT AT draw_y + 2, draw_x; UCHR$ (9474); " "; suit_str$; AT draw_y + 2, draw_x + 4; UCHR$ (9474)
9550 PRINT AT draw_y + 3, draw_x; UCHR$ (9492); UCHR$ (9472); UCHR$ (9472); UCHR$ (9472); UCHR$ (9496)
9560 RETURN
