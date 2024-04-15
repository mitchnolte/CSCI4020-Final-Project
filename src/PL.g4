grammar PL;

@header {
import backend.*;
import java.util.HashMap;
}

@members {
}


program returns [Expr e]
    : block EOF  { $e = $block.e; }
    ;

block returns [Expr e]
    : { List<Expr> exprs = new ArrayList<Expr>(); }
      (statement { exprs.add($statement.e); } )+
      { $e = new Block(exprs); }
    ;

statement returns [Expr e]
    : expr ';'     { $e = $expr.e;        }
    | conditional  { $e = $conditional.e; }
    | for_loop     { $e = $for_loop.e;    }
    | declare_fn   { $e = $declare_fn.e;  }
    ;

expr returns [Expr e]
    : STRING                   { $e = new StringLiteral($STRING.text);               }
    | INTEGER                  { $e = new IntLiteral($INTEGER.text);                 }
    | FLOAT                    { $e = new FloatLiteral($FLOAT.text);                 }
    | BOOLEAN                  { $e = new BooleanLiteral($BOOLEAN.text);             }
    | IDENTIFIER               { $e = new Deref($IDENTIFIER.text);                   }
    | list_literal             { $e = $list_literal.e;                               }
    | dict_literal             { $e = $dict_literal.e;                               }
    | collection_deref         { $e = $collection_deref.e;                           }
    | low=expr '..' high=expr  { $e = new RangeExpr($low.e, $high.e);                }
    | '-' INTEGER              { $e = new IntLiteral("-" + $INTEGER.text);           }
    | '-' FLOAT                { $e = new FloatLiteral("-" + $FLOAT.text);           }
    | '!' expr                 { $e = new Negate($expr.e);                           }
    | '(' expr ')'             { $e = $expr.e;                                       }
    | x=expr '*'  y=expr       { $e = new Arithmetic(Operator.Mul, $x.e, $y.e);      }
    | x=expr '/'  y=expr       { $e = new Arithmetic(Operator.Div, $x.e, $y.e);      }
    | x=expr '+'  y=expr       { $e = new Arithmetic(Operator.Add, $x.e, $y.e);      }
    | x=expr '-'  y=expr       { $e = new Arithmetic(Operator.Sub, $x.e, $y.e);      }
    | x=expr '==' y=expr       { $e = new Equality(Comparator.EQ, $x.e, $y.e);       }
    | x=expr '!=' y=expr       { $e = new Equality(Comparator.NE, $x.e, $y.e);       }
    | x=expr '<'  y=expr       { $e = new Compare(Comparator.LT, $x.e, $y.e);        }
    | x=expr '>'  y=expr       { $e = new Compare(Comparator.GT, $x.e, $y.e);        }
    | x=expr '<=' y=expr       { $e = new Compare(Comparator.LE, $x.e, $y.e);        }
    | x=expr '>=' y=expr       { $e = new Compare(Comparator.GE, $x.e, $y.e);        }
    | x=expr '&&' y=expr       { $e = new LogicalOperator(Operator.And, $x.e, $y.e); }
    | x=expr '||' y=expr       { $e = new LogicalOperator(Operator.Or, $x.e, $y.e);  }
    | x=expr '++' y=expr       { $e = new Concatenate($x.e, $y.e);                   }
    | invoke_fn                { $e = $invoke_fn.e;                                  }
    | print                    { $e = $print.e;                                      }
    | IDENTIFIER '=' expr      { $e = new Assign($IDENTIFIER.text, $expr.e);         }
    | collection_assign        { $e = $collection_assign.e;                          }
    ;


// Functions
declare_fn returns [Expr e]
    : { List<String> params = new ArrayList<String>(); }
      'function' name=IDENTIFIER '('
          (id=IDENTIFIER        { params.add($id.text); }
            (',' id=IDENTIFIER  { params.add($id.text); } )*
          )?
      ')' '{' block '}'
      { $e = new DeclareFn($name.text, params, $block.e); }
    ;

invoke_fn returns [Expr e]
    : { List<Expr> args = new ArrayList<Expr>(); }
      name=IDENTIFIER '('
          (expr        { args.add($expr.e); }
            (',' expr  { args.add($expr.e); } )*
          )?
      ')'
      { $e = new InvokeFn($name.text, args); }
    ;

print returns [Expr e]
    : { boolean newLine = false; }
      ( 'print' | 'println'  { newLine = true; } ) '('
          ( expr  { $e = new Print($expr.e, newLine);        }
          |       { $e = new Print(new NoneExpr(), newLine); }
          )
      ')'
    ;
      


// Loops & conditionals
for_loop returns [Expr e]
    : 'for' '(' IDENTIFIER 'in' expr ')' '{' block '}'
      { $e = new ForLoop($IDENTIFIER.text, $expr.e, $block.e); }
    ;

conditional returns [Expr e]
    : 'if' '(' expr ')' '{' block '}'
      ( cond_else  { $e = new Conditional($expr.e, $block.e, $cond_else.e);   }
      |            { $e = new Conditional($expr.e, $block.e, new NoneExpr()); }
      )
    ;

cond_else returns [Expr e]
    : 'else'
      ( conditional    { $e = $conditional.e; }
      | '{' block '}'  { $e = $block.e;       }
      )
    ;


// Collections
list_literal returns [Expr e]
    : { List<Expr> elements = new ArrayList(); }
      '[' (
            expr       { elements.add($expr.e); }
            (',' expr  { elements.add($expr.e); } )*
          )?
      ']'
      { $e = new ListExpr(elements); }
    ;

dict_literal returns [Expr e]
    : { HashMap<Expr, Expr> mappings = new HashMap(); }
      '{' (
            key=expr ':' val=expr       { mappings.put($key.e, $val.e); }
            (',' key=expr ':' val=expr  { mappings.put($key.e, $val.e); } )*
          )?
      '}'
      { $e = new DictExpr(mappings); }
    ;

collection_deref returns [Expr e]
    : IDENTIFIER '[' expr ']'
      { $e = new CollectionDeref(new Deref($IDENTIFIER.text), $expr.e); }
    ;

collection_assign returns [Expr e]
    : IDENTIFIER '[' index=expr ']' '=' value=expr
      { $e = new CollectionAssign(new Deref($IDENTIFIER.text), $index.e, $value.e); }
    ;


// Lexer
STRING     : '"' (~'"' | '\\' '"')* '"';
INTEGER    : '0' | ([1-9] [0-9]*);
FLOAT      : INTEGER '.' [0-9]+;
BOOLEAN    : 'true' | 'false';
IDENTIFIER : [a-zA-Z_] [a-zA-Z0-9_]*;

COMMENT    : '//' ~[\nEOF]* ('\n' | EOF) -> skip;
WHITESPACE : [ \t\r\n] -> skip;
