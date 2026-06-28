grammar BazLang;

options { caseInsensitive=true; }

// ===== Parser Rules =====

// Top-level program structure
program
    : (line | NEWLINE)* lastLine? EOF
    ;

line
    : NUM_LITERAL statements NEWLINE
    ;

lastLine
    : NUM_LITERAL statements
    ;

replLine
    : NUM_LITERAL statements? EOF                          # NumberedLine
    | replCommand EOF                                      # ReplCommandLine
    | statements EOF                                       # ImmediateLine
    ;

// One or more statements separated by colons
statements
    : statement (':' statement)*
    ;

// Entry rule for parsing isolated statements (e.g. from a ProgramLine or REPL input)
statementsInput
    : statements EOF
    ;

// Entry rule for parsing a standalone numeric expression (e.g. VAL, INPUT).
// Anchors to EOF so trailing garbage is a syntax error rather than silently ignored.
numExprInput
    : numExpr EOF
    ;

// Entry rule for parsing a standalone string expression (e.g. VAL$).
strExprInput
    : strExpr EOF
    ;

replCommand
    : DELETE lineRange?                                    # DeleteCmd
    | EDIT numExpr                                         # EditCmd
    | RENUM renumArgs?                                     # RenumCmd
    | REFORMAT lineRange?                                  # ReformatCmd
    ;

// Statements
statement
    : BRIGHT numExpr                                       # BrightStmt
    | CLEAR                                                # ClearStmt
    | CLS                                                  # ClsStmt
    | (CONT | CONTINUE)                                    # ContStmt
    | DATA expression (',' expression)*                    # DataStmt
    | DEF FN name=(NUM_IDENTIFIER | STR_IDENTIFIER) '(' ( params+=(NUM_IDENTIFIER | STR_IDENTIFIER) (',' params+=(NUM_IDENTIFIER | STR_IDENTIFIER))* )? ')' '=' expression # DefFnStmt
    | DIM dimDecl                                          # DimStmt
    | DRAW styleList numExpr ',' numExpr                   # DrawStmt
    | FAST                                                 # FastStmt
    | FLASH numExpr                                        # FlashStmt
    | FOR NUM_IDENTIFIER '=' numExpr TO numExpr (STEP numExpr)? # ForStmt
    | (GO SUB | GOSUB) numExpr                             # GosubStmt
    | (GO TO | GOTO) numExpr                               # GotoStmt
    | IF numExpr THEN statements                           # IfStmt
    | INK numExpr                                          # InkStmt
    | INPUT assignmentTarget                               # InputStmt
    | INVERSE numExpr                                      # InverseStmt
    | LET assignmentTarget '=' expression                  # LetStmt
    | LIST lineRange?                                      # ListStmt
    | LLIST lineRange?                                     # LListStmt
    | LOAD strExpr                                         # LoadStmt
    | LPRINT printList?                                    # LPrintStmt
    | NEW                                                  # NewStmt
    | NEXT NUM_IDENTIFIER                                  # NextStmt
    | OVER numExpr                                         # OverStmt
    | PAPER numExpr                                        # PaperStmt
    | PAUSE numExpr                                        # PauseStmt
    | PLOT styleList numExpr ',' numExpr                   # PlotStmt
    | PLOTMODE numExpr                                     # PlotmodeStmt
    | PRINT printList?                                     # PrintStmt
    | (RAND | RANDOMISE | RANDOMIZE) numExpr?              # RandStmt
    | READ assignmentTarget (',' assignmentTarget)*        # ReadStmt
    | REM                                                  # RemStmt
    | RESTORE numExpr?                                     # RestoreStmt
    | RETURN                                               # ReturnStmt
    | RUN numExpr?                                         # RunStmt
    | SAVE strExpr                                         # SaveStmt
    | SCROLL                                               # ScrollStmt
    | SLOW                                                 # SlowStmt
    | STOP                                                 # StopStmt
    ;

dimDecl
    : NUM_IDENTIFIER '(' numExpr (',' numExpr)* ')'        // numeric array
    | STR_IDENTIFIER '(' numExpr (',' numExpr)* ')'        // string/char array
    ;

// LIST/LLIST/DELETE line range using TO (consistent with slice syntax)
// LIST, LIST 10, LIST 10 TO, LIST TO 100, LIST 10 TO 100, LIST TO
lineRange
    : numExpr (TO numExpr?)?                               // start or start TO end or start TO
    | TO numExpr?                                          // TO end or just TO (all)
    ;

// RENUM arguments: [new_start] [STEP new_step] [, [old_start] TO [old_end]]
// At least one component required to avoid ANTLR warning about matching empty string
renumArgs
    : numExpr (STEP numExpr)? (',' numExpr? TO numExpr?)?  // new_start with optional STEP and range
    | STEP numExpr (',' numExpr? TO numExpr?)?             // STEP without new_start
    | ',' numExpr? TO numExpr?                             // just the range part
    ;

assignmentTarget
locals [ Object varRef ]
    : NUM_IDENTIFIER
    | NUM_IDENTIFIER '(' numExpr (',' numExpr)* ')'
    | STR_IDENTIFIER
    | STR_IDENTIFIER '(' strSubscript ')'
    ;

printList
    : printSep* printItem (printSep+ printItem)* printSep*
    | printSep+
    ;

styleList
    : (styleItem printSep?)*
    ;

styleItem
    : BRIGHT numExpr                                       # StyleBrightItem
    | FLASH numExpr                                        # StyleFlashItem
    | INK numExpr                                          # StyleInkItem
    | INVERSE numExpr                                      # StyleInverseItem
    | OVER numExpr                                         # StyleOverItem
    | PAPER numExpr                                        # StylePaperItem
    ;

printItem
    : AT numExpr ',' numExpr                               # PrintAtItem
    | TAB numExpr                                          # PrintTabItem
    | styleItem                                            # PrintStyleItem
    | expression                                           # PrintExprItem
    ;

printSep
    : ';'
    | ','
    | '\''
    ;

// Expressions - numeric and string combined
expression
    : numExpr
    | strExpr
    ;

// Numeric expressions
// Sinclair ZX BASIC precedence (higher number = binds tighter):
//   12: subscripting/slicing, 11: functions, 10: **, 9: unary minus,
//   8: */,  6: +-, 5: comparisons, 4: NOT, 3: AND, 2: OR
// ANTLR: earlier alternatives = higher precedence (bind tighter)
// Note: ** (10) binds tighter than unary minus (9), so -2**2 = -(2**2) = -4
numExpr
locals [ double cachedNum, Object varRef ]
    : NUM_LITERAL                                          # NumLiteralExpr
    | BIN_LITERAL                                          # BinLiteralExpr
    | NUM_IDENTIFIER                                       # NumVarExpr
    | NUM_IDENTIFIER '(' numExpr (',' numExpr)* ')'        # NumArrayExpr
    | '(' numExpr ')'                                      # NumParenExpr
    | numFunc                                              # NumFuncCallExpr
    | FN NUM_IDENTIFIER '(' ( args+=expression (',' args+=expression)* )? ')' # FnNumCallExpr
    | <assoc=right> numExpr ('**' | '^') numExpr           # NumPowerExpr
    | '-' numExpr                                          # NumUnaryMinusExpr
    | numExpr ('*' | '/') numExpr                          # NumMulDivExpr
    | numExpr ('+' | '-') numExpr                          # NumAddSubExpr
    | numExpr ('<' | '<=' | '>' | '>=' | '=' | '<>') numExpr # NumCompExpr
    | strExpr ('<' | '<=' | '>' | '>=' | '=' | '<>') strExpr # StrCompExpr
    | NOT numExpr                                          # NumNotExpr
    | numExpr AND numExpr                                  # NumAndExpr
    | numExpr OR numExpr                                   # NumOrExpr
    ;

// String expressions
// Subscripts can include indices and an optional slice at the end
// A$(1), A$(1,2), A$(1 TO 5), A$(TO 5), A$(1, 2 TO 5), etc.
strExpr
locals [ Object cachedStr, Object varRef ]
    : STR_LITERAL                                          # StrLiteralExpr
    | STR_IDENTIFIER                                       # StrVarExpr
    | STR_IDENTIFIER '(' strSubscript ')'                  # StrSubscriptExpr
    | '(' strExpr ')'                                      # StrParenExpr
    | strExpr '+' strExpr                                  # StrConcatExpr
    | strFunc                                              # StrFuncCallExpr
    | FN STR_IDENTIFIER '(' ( args+=expression (',' args+=expression)* )? ')' # FnStrCallExpr
    | strExpr AND numExpr                                  # StrAndExpr
    ;

// String subscript: indices optionally followed by a slice
// The slice (with TO) can only appear at the end
strSubscript
    : indices+=numExpr (',' indices+=numExpr)*
    | indices+=numExpr (',' indices+=numExpr)* ',' slice=strSlice
    | slice=strSlice
    ;

strSlice
    : start=numExpr? TO end=numExpr?
    ;

// Numeric functions - parentheses optional, function binds to numAtom (not full expression)
// SIN PI/2 means SIN(PI)/2, SIN (PI/2) means SIN(PI/2)
numFunc
    : ABS numAtom
    | ACS numAtom
    | ASN numAtom
    | ATN numAtom
    | CODE strAtom
    | COLOUR '(' numExpr ',' numExpr ',' numExpr ')'
    | COS numAtom
    | EXP numAtom
    | FRAMES
    | INT numAtom
    | LEN strAtom
    | LN numAtom
    | PI
    | PLOTH
    | PLOTMODE
    | PLOTW
    | PLOTX
    | PLOTY
    | POINT '(' numExpr ',' numExpr ')'
    | RND
    | SGN numAtom
    | SIN numAtom
    | SQR numAtom
    | TAN numAtom
    | TEXTH
    | TEXTW
    | TEXTX
    | TEXTY
    | UCNEXT '(' strExpr ',' numExpr ')'
    | UCODE strAtom
    | VAL strAtom
    ;

// Atomic numeric expression (for function arguments without parens)
numAtom
locals [ double cachedNum, Object varRef ]
    : NUM_LITERAL
    | BIN_LITERAL
    | NUM_IDENTIFIER
    | NUM_IDENTIFIER '(' numExpr (',' numExpr)* ')'
    | '(' numExpr ')'
    | numFunc
    ;

// String functions - parentheses optional, function binds to atom
strFunc
    : CHR_STR numAtom
    | INKEY_STR
    | STR_STR numAtom
    | UCHR_STR numAtom
    | UINKEY_STR
    | VAL_STR strAtom
    ;

// Atomic string expression (for function arguments without parens)
strAtom
locals [ Object cachedStr, Object varRef ]
    : STR_LITERAL
    | STR_IDENTIFIER
    | STR_IDENTIFIER '(' strSubscript ')'
    | '(' strExpr ')'
    | strFunc
    ;

// ===== Lexer Rules =====

// Keywords - Statements
BRIGHT   : 'BRIGHT';
CLEAR    : 'CLEAR';
CLS      : 'CLS';
CONT     : 'CONT';
CONTINUE : 'CONTINUE';
DATA     : 'DATA';
DEF      : 'DEF';
DELETE   : 'DELETE';
DIM      : 'DIM';
DRAW     : 'DRAW';
EDIT     : 'EDIT';
FAST     : 'FAST';
FLASH    : 'FLASH';
FN       : 'FN';
FOR      : 'FOR';
GO       : 'GO';
GOSUB    : 'GOSUB';
GOTO     : 'GOTO';
IF       : 'IF';
INK      : 'INK';
INPUT    : 'INPUT';
INVERSE  : 'INVERSE';
LET      : 'LET';
LIST     : 'LIST';
LLIST    : 'LLIST';
LOAD     : 'LOAD';
LPRINT   : 'LPRINT';
NEW      : 'NEW';
NEXT     : 'NEXT';
OVER     : 'OVER';
PAPER    : 'PAPER';
PAUSE    : 'PAUSE';
PLOT     : 'PLOT';
PLOTMODE : 'PLOTMODE';
POINT    : 'POINT';
PRINT    : 'PRINT';
RAND     : 'RAND';
RANDOMISE: 'RANDOMISE';
RANDOMIZE: 'RANDOMIZE';
READ     : 'READ';
REFORMAT : 'REFORMAT';
RENUM    : 'RENUM';
RESTORE  : 'RESTORE';
RETURN   : 'RETURN';
RUN      : 'RUN';
SAVE     : 'SAVE';
SCROLL   : 'SCROLL';
SLOW     : 'SLOW';
STOP     : 'STOP';
SUB      : 'SUB';

// Keywords - Operators
AND      : 'AND';
NOT      : 'NOT';
OR       : 'OR';
STEP     : 'STEP';
THEN     : 'THEN';
TO       : 'TO';
AT       : 'AT';
TAB      : 'TAB';

// Functions
ABS      : 'ABS';
ACS      : 'ACS';
ASN      : 'ASN';
ATN      : 'ATN';
CHR_STR  : 'CHR$';
CODE     : 'CODE';
COLOUR   : 'COLOUR';
COS      : 'COS';
EXP      : 'EXP';
FRAMES   : 'FRAMES';
INKEY_STR: 'INKEY$';
INT      : 'INT';
LEN      : 'LEN';
LN       : 'LN';
PI       : 'PI';
PLOTH    : 'PLOTH';
PLOTW    : 'PLOTW';
PLOTX    : 'PLOTX';
PLOTY    : 'PLOTY';
RND      : 'RND';
SGN      : 'SGN';
SIN      : 'SIN';
SQR      : 'SQR';
STR_STR  : 'STR$';
TAN      : 'TAN';
TEXTH    : 'TEXTH';
TEXTW    : 'TEXTW';
TEXTX    : 'TEXTX';
TEXTY    : 'TEXTY';
UCHR_STR : 'UCHR$';
UCNEXT   : 'UCNEXT';
UCODE    : 'UCODE';
UINKEY_STR: 'UINKEY$';
VAL      : 'VAL';
VAL_STR  : 'VAL$';

// Identifiers - matched case-insensitively, normalized to uppercase
STR_IDENTIFIER
    : [A-Z][A-Z0-9_]*'$'
    ;

NUM_IDENTIFIER
    : [A-Z][A-Z0-9_]*
    ;

NUM_LITERAL
    : [0-9]+ ('.' [0-9]+)? ([E][+-]? [0-9]+)?
    | '.' [0-9]+ ([E][+-]? [0-9]+)?
    ;

STR_LITERAL
    : '"' ( '""' | ~["\r\n] )* '"'
    ;

// Whitespace and comments
NEWLINE  : '\r'? '\n';
WS       : [ \t\r]+ -> skip;
LINE_COMMENT: '#' ~[\r\n]* -> skip;

// REM consumes rest of line as part of the token
REM      : 'REM' ~[\r\n]* ;

// Operators (order matters for multi-char operators)
POWER    : '**';
CARET    : '^';
LE       : '<=';
GE       : '>=';
NE       : '<>';

// BIN: binary literal notation (Spectrum). Digits 0/1 only, spaces allowed between digits.
// BIN is a keyword prefix - the lexer captures the whole token including the BIN prefix.
BIN_LITERAL
    : 'BIN' [ \t]* [01] ([ \t]* [01])*
    ;
