1000 REM ### Pontoon ###
1010 REM ### Classic UK card game - try to get a Pontoon or 5-Card Trick ###
1020 RANDOMIZE
1030 LET money = 1000 : LET dealer_money = 10000
1040 LET ox = 0 : LET oy = 0
1050 IF TEXTW > 80 THEN LET ox = INT ((TEXTW - 80) / 2)
1060 IF TEXTH > 25 THEN LET oy = INT ((TEXTH - 25) / 2)
1070 DIM deck(52)
1080 DIM player_hand(10)
1090 DIM dealer_hand(10)
1100 GO SUB 7500
2000 REM ### Start of round ###
2010 CLS
2020 PRINT AT oy, ox; "Player: £"; money; "   Dealer: £"; dealer_money; "        "
2030 IF money <= 0 THEN PRINT AT oy + 10, ox + 10; "Game over! You went broke." : STOP
2040 IF dealer_money <= 0 THEN PRINT AT oy + 10, ox + 10; "Game over! You broke the bank!" : STOP
2050 PRINT AT oy + 1, ox; "Choose bet: (1) £10  (2) £50  (3) £100  (4) All in!  (5) Custom"
2500 LET k$ = INKEY$
2510 IF k$ = "1" THEN LET bet = 10 : GO TO 3000
2520 IF k$ = "2" THEN LET bet = 50 : GO TO 3000
2530 IF k$ = "3" THEN LET bet = 100 : GO TO 3000
2540 IF k$ = "4" THEN LET bet = money : GO TO 3000
2550 IF k$ = "5" THEN GO TO 2570
2560 GO TO 2500
2570 PRINT AT oy + 2, ox; "Enter bet amount: £"; 
2580 INPUT bet
2590 IF bet <= 0 THEN PRINT AT oy + 3, ox; "Must be > £0!          " : GO TO 2570
2600 IF bet > money THEN PRINT AT oy + 3, ox; "Insufficient funds!   " : GO TO 2570
3000 IF bet > money THEN GO TO 2500
3010 CLS
3020 PRINT AT oy, ox; "Player: £"; money; "   Dealer: £"; dealer_money; "   Bet: £"; bet; "    "
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
5080 IF player_score > 21 THEN PRINT AT oy + 15, ox; "Bust! You lose.           " : LET money = money - bet : LET dealer_money = dealer_money + bet : GO TO 7000
5090 IF player_cards = 5 THEN PRINT AT oy + 15, ox; "Five-Card Trick!          " : PAUSE 20 : GO TO 5500
5100 GO TO 4500
5500 REM ### Dealer turn ###
5510 LET draw_x = ox + 6
5520 LET draw_y = oy + 4
5530 LET c = dealer_hand(2)
5540 GO SUB 9000
6000 REM ### Evaluate dealer hand ###
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
6110 LET dealer_type = 2
6120 IF dealer_score > 21 THEN LET dealer_type = 0 : GO TO 6500
6130 IF dealer_score = 21 AND dealer_cards = 2 THEN LET dealer_type = 5 : GO TO 6500
6140 IF dealer_cards = 5 THEN LET dealer_type = 4 : GO TO 6500
6150 IF dealer_score = 21 THEN LET dealer_type = 3
6160 IF dealer_score >= 17 THEN GO TO 6500
6170 GO SUB 8000
6180 LET dealer_cards = dealer_cards + 1
6190 LET dealer_hand(dealer_cards) = c
6200 LET draw_x = ox + (dealer_cards - 1) * 6
6210 LET draw_y = oy + 4
6220 GO SUB 9000
6230 PAUSE 20
6240 GO TO 6000
6500 REM ### End round ###
6510 GO SUB 8500
6520 LET player_wins = 0
6530 IF player_type > dealer_type THEN LET player_wins = 1
6540 IF player_type = 2 AND dealer_type = 2 AND player_score > dealer_score THEN LET player_wins = 1
6550 IF dealer_type = 0 THEN PRINT AT oy + 15, ox; "Player: "; player_score; "  Dealer: Bust ("; dealer_score; ")          "
6560 IF dealer_type > 0 THEN PRINT AT oy + 15, ox; "Player: "; player_score; "  Dealer: "; dealer_score; "          "
6570 LET f = 1
6580 IF player_wins = 1 THEN GO TO 6650
6590 REM ### Dealer wins ###
6600 IF dealer_type = 5 OR dealer_type = 4 THEN LET f = 2
6610 PRINT AT oy + 16, ox; "Dealer wins £"; f * bet; "."
6620 LET money = money - f * bet
6630 LET dealer_money = dealer_money + f * bet
6640 GO TO 7000
6650 REM ### Player wins ###
6660 IF player_type = 5 OR player_type = 4 THEN LET f = 2
6670 PRINT AT oy + 16, ox; "You win £"; f * bet; "!"
6680 LET money = money + f * bet
6690 LET dealer_money = dealer_money - f * bet
7000 PRINT AT oy + 17, ox; "Press any key"
7010 IF INKEY$ = "" THEN GO TO 7010
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
8600 LET player_score = s
8610 LET player_type = 2
8620 IF player_score > 21 THEN LET player_type = 0 : RETURN
8630 IF player_score = 21 AND player_cards = 2 THEN LET player_type = 5 : RETURN
8640 IF player_cards = 5 THEN LET player_type = 4 : RETURN
8650 IF player_score = 21 THEN LET player_type = 3
8660 RETURN
9000 REM ### Render card c at draw_x,draw_y ###
9010 IF c = 0 THEN LET val_str$ = "?" : LET suit_str$ = "？" : LET card_ink = 1 : GO TO 9510
9020 LET v = (c - 1) - INT ((c - 1) / 13) * 13 + 1
9030 LET suit = INT ((c - 1) / 13)
9040 LET val_str$ = STR$ (v)
9050 IF v = 1 THEN LET val_str$ = "A"
9060 IF v = 11 THEN LET val_str$ = "J"
9070 IF v = 12 THEN LET val_str$ = "Q"
9080 IF v = 13 THEN LET val_str$ = "K"
9090 LET suit_str$ = ""
9100 IF suit = 0 THEN LET suit_str$ = "♠️" : REM UCHR$ (9824)
9110 IF suit = 1 THEN LET suit_str$ = "♥️" : REM UCHR$ (9825)
9120 IF suit = 2 THEN LET suit_str$ = "♣️" : REM UCHR$ (9827)
9130 IF suit = 3 THEN LET suit_str$ = "♦️" : REM UCHR$ (9826)
9140 LET card_ink = 0 : IF suit = 1 OR suit = 3 THEN LET card_ink = 2
9500 REM ### Render the card box ###
9510 PRINT AT draw_y, draw_x; INK card_ink; PAPER 7; UCHR$ (9484); UCHR$ (9472); UCHR$ (9472); UCHR$ (9472); UCHR$ (9488)
9520 IF LEN (val_str$) = 2 THEN PRINT AT draw_y + 1, draw_x; INK card_ink; PAPER 7; UCHR$ (9474); val_str$; " "; UCHR$ (9474)
9530 IF LEN (val_str$) = 1 THEN PRINT AT draw_y + 1, draw_x; INK card_ink; PAPER 7; UCHR$ (9474); val_str$; "  "; UCHR$ (9474)
9540 PRINT AT draw_y + 2, draw_x; INK card_ink; PAPER 7; UCHR$ (9474); " "; suit_str$; ""; UCHR$ (9474)
9550 PRINT AT draw_y + 3, draw_x; INK card_ink; PAPER 7; UCHR$ (9492); UCHR$ (9472); UCHR$ (9472); UCHR$ (9472); UCHR$ (9496)
9560 RETURN
