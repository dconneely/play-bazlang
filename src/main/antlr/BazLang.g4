grammar BazLang;

options { caseInsensitive=true; }

// ===== Parser Rules =====

// Top-level program structure
program
    : (line | NEWLINE)* lastLine? EOF
    ;

line
    : NUM_LITERAL statement NEWLINE
    ;

lastLine
    : NUM_LITERAL statement
    ;

replLine
    : NUM_LITERAL statement? EOF   # NumberedLine
    | EDIT numExpr EOF             # EditLine
    | statement EOF                # ImmediateLine
    ;

// Statements
statement
    : CLEAR                                                             # ClearStmt
    | CLS                                                               # ClsStmt
    | CONT                                                              # ContStmt
    | COPY                                                              # CopyStmt
    | DELETE lineRange?                                                 # DeleteStmt
    | DIM dimDecl                                                        # DimStmt
    | FAST                                                              # FastStmt
    | FOR NUM_IDENTIFIER '=' numExpr TO numExpr (STEP numExpr)?         # ForStmt
    | GOSUB numExpr                                                     # GosubStmt
    | GOTO numExpr                                                      # GotoStmt
    | IF numExpr THEN statement                                         # IfStmt
    | INPUT assignmentTarget                                             # InputStmt
    | LET assignmentTarget '=' expression                               # LetStmt
    | LIST lineRange?                                                   # ListStmt
    | LLIST lineRange?                                                  # LListStmt
    | LOAD strExpr                                                      # LoadStmt
    | LPRINT printList?                                                 # LPrintStmt
    | NEW                                                               # NewStmt
    | NEXT NUM_IDENTIFIER                                               # NextStmt
    | PAUSE numExpr                                                     # PauseStmt
    | PLOT numExpr ',' numExpr                                          # PlotStmt
    | POKE numExpr ',' numExpr                                          # PokeStmt
    | PRINT printList?                                                  # PrintStmt
    | RAND numExpr?                                                     # RandStmt
    | REM                                                               # RemStmt
    | RENUM renumArgs?                                                  # RenumStmt
    | RETURN                                                            # ReturnStmt
    | RUN numExpr?                                                      # RunStmt
    | SAVE strExpr                                                      # SaveStmt
    | SCROLL                                                            # ScrollStmt
    | SLOW                                                              # SlowStmt
    | STOP                                                              # StopStmt
    | UNPLOT numExpr ',' numExpr                                        # UnplotStmt
    ;

dimDecl
    : NUM_IDENTIFIER '(' numExpr (',' numExpr)* ')'    // numeric array
    | STR_IDENTIFIER '(' numExpr (',' numExpr)* ')'    // string/char array
    ;

// LIST/LLIST/DELETE line range using TO (consistent with slice syntax)
// LIST, LIST 10, LIST 10 TO, LIST TO 100, LIST 10 TO 100, LIST TO
lineRange
    : numExpr (TO numExpr?)?    // start or start TO end or start TO
    | TO numExpr?               // TO end or just TO (all)
    ;

// RENUM arguments: [new_start] [STEP new_step] [, [old_start] TO [old_end]]
renumArgs
    : numExpr? (STEP numExpr)? (',' numExpr? TO numExpr?)?
    ;

assignmentTarget
    : NUM_IDENTIFIER
    | NUM_IDENTIFIER '(' numExpr (',' numExpr)* ')'
    | STR_IDENTIFIER
    | STR_IDENTIFIER '(' strSubscript ')'
    ;

printList
    : printItem (printSep printItem)* printSep?
    ;

printItem
    : AT numExpr ',' numExpr    # PrintAtItem
    | TAB numExpr               # PrintTabItem
    | expression                # PrintExprItem
    ;

printSep
    : ';'
    | ','
    ;

// Expressions - numeric and string combined
expression
    : numExpr
    | strExpr
    ;

// Numeric expressions
// ZX81 BASIC precedence (higher number = binds tighter):
//   12: subscripting/slicing, 11: functions, 10: **, 9: unary minus,
//   8: */,  6: +-, 5: comparisons, 4: NOT, 3: AND, 2: OR
// ANTLR: earlier alternatives = higher precedence (bind tighter)
// Note: ** (10) binds tighter than unary minus (9), so -2**2 = -(2**2) = -4
numExpr
    : NUM_LITERAL                                           # NumLiteralExpr
    | NUM_IDENTIFIER                                        # NumVarExpr
    | NUM_IDENTIFIER '(' numExpr (',' numExpr)* ')'         # NumArrayExpr
    | '(' numExpr ')'                                       # NumParenExpr
    | numFunc                                               # NumFuncCallExpr
    | <assoc=right> numExpr '**' numExpr                    # NumPowerExpr
    | '-' numExpr                                           # NumUnaryMinusExpr
    | numExpr ('*' | '/') numExpr                           # NumMulDivExpr
    | numExpr ('+' | '-') numExpr                           # NumAddSubExpr
    | numExpr ('<' | '<=' | '>' | '>=' | '=' | '<>') numExpr    # NumCompExpr
    | strExpr ('<' | '<=' | '>' | '>=' | '=' | '<>') strExpr    # StrCompExpr
    | NOT numExpr                                           # NumNotExpr
    | numExpr AND numExpr                                   # NumAndExpr
    | numExpr OR numExpr                                    # NumOrExpr
    ;

// String expressions
// Subscripts can include indices and an optional slice at the end
// A$(1), A$(1,2), A$(1 TO 5), A$(TO 5), A$(1, 2 TO 5), etc.
strExpr
    : STR_LITERAL                                               # StrLiteralExpr
    | STR_IDENTIFIER                                            # StrVarExpr
    | STR_IDENTIFIER '(' strSubscript ')'                       # StrSubscriptExpr
    | '(' strExpr ')'                                           # StrParenExpr
    | strExpr '+' strExpr                                       # StrConcatExpr
    | strFunc                                                   # StrFuncCallExpr
    | strExpr AND numExpr                                       # StrAndExpr
    ;

// String subscript: indices optionally followed by a slice
// The slice (with TO) can only appear at the end
strSubscript
    : numExpr (',' numExpr)*                                    // indices only
    | numExpr (',' numExpr)* ',' numExpr? TO numExpr?           // indices + slice
    | numExpr? TO numExpr?                                      // slice only
    ;

// Numeric functions - parentheses optional, function binds to numAtom (not full expression)
// SIN PI/2 means SIN(PI)/2, SIN (PI/2) means SIN(PI/2)
numFunc
    : ABS numAtom
    | ACS numAtom
    | ASN numAtom
    | ATN numAtom
    | CODE strAtom
    | COS numAtom
    | EXP numAtom
    | INT numAtom
    | LEN strAtom
    | LN numAtom
    | PEEK numAtom
    | PI
    | RND
    | SGN numAtom
    | SIN numAtom
    | SQR numAtom
    | TAN numAtom
    | USR numAtom
    | VAL strAtom
    ;

// Atomic numeric expression (for function arguments without parens)
numAtom
    : NUM_LITERAL
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
    ;

// Atomic string expression (for function arguments without parens)
strAtom
    : STR_LITERAL
    | STR_IDENTIFIER
    | STR_IDENTIFIER '(' strSubscript ')'
    | '(' strExpr ')'
    | strFunc
    ;

// ===== Lexer Rules =====

// Keywords - Statements
CLEAR   : 'CLEAR';
CLS     : 'CLS';
CONT    : 'CONT';
COPY    : 'COPY';
DELETE  : 'DELETE';
DIM     : 'DIM';
EDIT    : 'EDIT';
FAST    : 'FAST';
FOR     : 'FOR';
GOSUB   : 'GOSUB';
GOTO    : 'GOTO';
IF      : 'IF';
INPUT   : 'INPUT';
LET     : 'LET';
LIST    : 'LIST';
LLIST   : 'LLIST';
LOAD    : 'LOAD';
LPRINT  : 'LPRINT';
NEW     : 'NEW';
NEXT    : 'NEXT';
PAUSE   : 'PAUSE';
PLOT    : 'PLOT';
POKE    : 'POKE';
PRINT   : 'PRINT';
RAND    : 'RAND';
RENUM   : 'RENUM';
RETURN  : 'RETURN';
RUN     : 'RUN';
SAVE    : 'SAVE';
SCROLL  : 'SCROLL';
SLOW    : 'SLOW';
STOP    : 'STOP';
UNPLOT  : 'UNPLOT';

// Keywords - Operators
AND     : 'AND';
NOT     : 'NOT';
OR      : 'OR';
STEP    : 'STEP';
THEN    : 'THEN';
TO      : 'TO';
AT      : 'AT';
TAB     : 'TAB';

// Functions
ABS     : 'ABS';
ACS     : 'ACS';
ASN     : 'ASN';
ATN     : 'ATN';
CHR_STR : 'CHR$';
CODE    : 'CODE';
COS     : 'COS';
EXP     : 'EXP';
INKEY_STR : 'INKEY$';
INT     : 'INT';
LEN     : 'LEN';
LN      : 'LN';
PEEK    : 'PEEK';
PI      : 'PI';
RND     : 'RND';
SGN     : 'SGN';
SIN     : 'SIN';
SQR     : 'SQR';
STR_STR : 'STR$';
TAN     : 'TAN';
USR     : 'USR';
VAL     : 'VAL';

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
NEWLINE : '\r'? '\n';
WS      : [ \t\r]+ -> skip;
LINE_COMMENT : '#' ~[\r\n]* -> skip;

// REM consumes rest of line as part of the token
REM     : 'REM' ~[\r\n]* ;

// Operators (order matters for multi-char operators)
POWER   : '**';
LE      : '<=';
GE      : '>=';
NE      : '<>';
