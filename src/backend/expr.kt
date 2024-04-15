package backend

abstract class Expr {
    abstract fun eval(runtime:Runtime): Data
}

class NoneExpr(): Expr() {
    override fun eval(runtime:Runtime) = None
}


// Literals
class StringLiteral(lexeme:String): Expr() {
    val lexeme = lexeme.substring(1, lexeme.length-1).replace("\\\"", "\"")
    override fun eval(runtime:Runtime): Data = StringData(lexeme)
}

class IntLiteral(val lexeme:String): Expr() {
    override fun eval(runtime:Runtime) : Data = IntData(lexeme.toInt())
}

class FloatLiteral(val lexeme:String): Expr() {
    override fun eval(runtime:Runtime) : Data = FloatData(lexeme.toFloat())
}

class BooleanLiteral(val lexeme:String): Expr() {
    override fun eval(runtime:Runtime): Data = BooleanData(lexeme.equals("true"))
}


// Variables
class Assign(val symbol:String, val expr:Expr): Expr() {
    override fun eval(runtime:Runtime): Data = expr.eval(runtime).apply {
        runtime.symbolTable.put(symbol, this)
    }
}

class Deref(val symbol:String): Expr() {
    override fun eval(runtime:Runtime): Data {
        val data = runtime.symbolTable[symbol]
        if(data == null)
            throw Exception("$symbol is not assigned.")
        return data
    }
}


// Aggregate datatypes
class ListExpr(val elements:MutableList<Expr>): Expr() {
    override fun eval(runtime:Runtime): Data {
        val result = MutableList<Data>(elements.size) { None }
        elements.forEachIndexed { i, element -> result[i] = element.eval(runtime) }
        return ListData(result)
    }
}

class DictExpr(val mappings:HashMap<Expr, Expr>): Expr() {
    override fun eval(runtime:Runtime): Data {
        val result = HashMap<Data, Data>(mappings.size)
        mappings.forEach { k, v ->
            val key = k.eval(runtime)
            if(key !is HashableData)
                throw Exception("Dictionary keys must be primitives or String.")
            result.put(key, v.eval(runtime))
        }
        return DictData(result)
    }
}

class RangeExpr(val low:Expr, val high:Expr): Expr() {
    override fun eval(runtime:Runtime): Data {
        val x = low.eval(runtime)
        val y = high.eval(runtime)
        if(x !is IntData || y !is IntData)
            throw Exception("Can only iterate over integer ranges.")
        return RangeData(x, y)
    }
}

class CollectionAssign(val coll:Expr, val subscript:Expr, val expr:Expr): Expr() {
    override fun eval(runtime:Runtime): Data {
        val collData = coll.eval(runtime)
        val exprData: Data
        if(collData is ListData) {
            val index = subscript.eval(runtime)
            if(index !is IntData)
                throw Exception("List index must be an integer.")
            exprData = expr.eval(runtime)
            collData.iter[index.value] = exprData
        }
        else if(collData is DictData) {
            val key = subscript.eval(runtime)
            if(key !is HashableData)
                throw Exception("Dictionary keys must be primitives or String.")
            exprData = expr.eval(runtime)
            collData.mappings.put(key, exprData)
        }
        else throw Exception("Only collections can be subscripted.")
        return exprData
    }
}

class CollectionDeref(val coll:Expr, val subscript:Expr): Expr() {
    override fun eval(runtime:Runtime): Data {
        val collData = coll.eval(runtime)
        if(collData is ListData) {
            val index = subscript.eval(runtime)
            if(index !is IntData ) throw Exception("List index must be an integer.")
            if(index.value < 0) throw Exception("List index must be nonnegative.")
            return collData.iter[index.value]
        }
        else if(collData is DictData) {
            val key = subscript.eval(runtime)
            if(key !is HashableData)
                throw Exception("Dictionary keys must be primitives or String.")
            return collData.mappings.getOrDefault(key, None)
        }
        else throw Exception("Only collections can be subscripted.")
    }
}


// Operations
enum class Operator   {Add, Sub, Mul, Div, And, Or}
enum class Comparator {LT, LE, GT, GE, EQ, NE}

class Arithmetic(val op:Operator, val left:Expr, val right:Expr): Expr() {
    override fun eval(runtime:Runtime): Data {
        val x = left.eval(runtime)
        val y = right.eval(runtime)

        // String repetiton
        if(op == Operator.Mul) {
            if(x is StringData && y is IntData)
                return StringData(x.value.repeat(y.value))
            else if(x is IntData && y is StringData)
                return StringData(y.value.repeat(x.value))
        }

        // Integer arithmetic
        if(x is IntData && y is IntData) return IntData(
            when(op) {
                Operator.Add -> x.value + y.value
                Operator.Sub -> x.value - y.value
                Operator.Mul -> x.value * y.value
                Operator.Div -> {
                    if(y.value != 0)
                        x.value / y.value
                    else
                        throw Exception("Cannot divide by zero.")
                }
                else -> throw Exception("Invalid comparator.")
            }
        )

        // Floating point arithmetic
        val xVal = when(x) {
            is IntData -> x.value.toFloat()
            is FloatData -> x.value
            else -> throw Exception("Undefined arithmetic operation.")
        }
        val yVal = when(y) {
            is IntData -> y.value.toFloat()
            is FloatData -> y.value
            else -> throw Exception("Undefined arithmetic operation.")
        }

        return FloatData(
            when(op) {
                Operator.Add -> xVal + yVal
                Operator.Sub -> xVal - yVal
                Operator.Mul -> xVal * yVal
                Operator.Div -> {
                    if(yVal != 0.0f)
                        xVal / yVal
                    else
                        throw Exception("Cannot divide by zero.")
                }
                else -> throw Exception("Invalid comparator.")
            }
        )
    }
}


class Equality(val comparator:Comparator, val left:Expr, val right:Expr): Expr() {
    override fun eval(runtime:Runtime): Data {
        val x = left.eval(runtime)
        val y = right.eval(runtime)
        return when {
            x is StringData  && y is StringData  -> getResult<String>(x.value, y.value)
            x is BooleanData && y is BooleanData -> getResult<Boolean>(x.value, y.value)
            x is IntData     && y is IntData     -> getResult<Int>(x.value, y.value)
            x is FloatData   && y is FloatData   -> getResult<Float>(x.value, y.value)
            else -> throw Exception(
                "Equality check can only be performed on objects of the same type."
            )
        }
    }

    private fun <T> getResult(left: T, right: T): BooleanData {
        return BooleanData(
            when(comparator) {
                Comparator.EQ -> left == right
                Comparator.NE -> left != right
                else -> throw Exception("Invalid comparator.")
            }
        )
    }
}

class Compare(val comparator:Comparator, val left:Expr, val right:Expr): Expr() {
    override fun eval(runtime:Runtime): Data {
        val x = left.eval(runtime)
        val y = right.eval(runtime)
        if(x is IntData && y is IntData) return BooleanData(
            when(comparator) {
                Comparator.LT -> x.value <  y.value
                Comparator.GT -> x.value >  y.value
                Comparator.LE -> x.value <= y.value
                Comparator.GE -> x.value >= y.value
                else -> throw Exception("Invalid comparator.")
            }
        )
        else if(x is FloatData && y is FloatData) return BooleanData(
            when(comparator) {
                Comparator.LT -> x.value <  y.value
                Comparator.GT -> x.value >  y.value
                Comparator.LE -> x.value <= y.value
                Comparator.GE -> x.value >= y.value
                else -> throw Exception("Invalid comparator.")
            }
        )
        else throw Exception("Can only compare numbers of the same type.")
    }
}

class Concatenate(val str1:Expr, val str2:Expr): Expr() {
    override fun eval(runtime:Runtime): Data {
        val a = str1.eval(runtime)
        val b = str2.eval(runtime)
        return StringData("$a$b")
    }
}

class Negate(val bool:Expr): Expr() {
    override fun eval(runtime:Runtime): Data {
        val boolData = bool.eval(runtime)
        if(boolData !is BooleanData)
            throw Exception("Can only negate boolean data.")
        return BooleanData(!boolData.value)
    }
}

class LogicalOperator(val op:Operator, val left:Expr, val right:Expr): Expr() {
    override fun eval(runtime:Runtime): Data {
        val cond1 = left.eval(runtime)
        val cond2 = right.eval(runtime)
        if(cond1 is BooleanData && cond2 is BooleanData) return BooleanData(
            when(op) {
                Operator.And -> cond1.value && cond2.value
                Operator.Or  -> cond1.value || cond2.value
                else -> throw Exception("Invalid operator.")
            }
        )
        else throw Exception("Logical operators can only be applied to boolean data.")
    }
}


// Composite expressions and flow control constructs
class Block(val exprList: List<Expr>): Expr() {
    override fun eval(runtime:Runtime): Data {
        var result:Data = None
        exprList.forEach {
            result = it.eval(runtime)
        }
        return result
    }
}

class Conditional(val cond:Expr, val trueExpr:Expr, val falseExpr:Expr): Expr() {
    override fun eval(runtime:Runtime): Data {
        val condData = cond.eval(runtime)
        if(condData !is BooleanData)
            throw Exception("Condition must evaluate to a boolean.")
        if(condData.value)
            return trueExpr.eval(runtime)
        else
            return falseExpr.eval(runtime)
    }
}

class ForLoop(val symbol:String, val iterable:Expr, val body:Expr): Expr() {
    override fun eval(runtime:Runtime): Data {
        val iterData = iterable.eval(runtime)
        if(iterData !is IterableData)
            throw Exception("Can only iterate over lists, dictionaries, and integer ranges.")

        var result:Data = None
        for(x in iterData.iter) {
            runtime.symbolTable[symbol] = x
            result = body.eval(runtime)
        }
        return result
    }
}


// Functions
class DeclareFn(val name:String, val params:List<String>, val body:Expr): Expr() {
    override fun eval(runtime:Runtime): Data = FuncData(name, params, body).also {
        runtime.symbolTable[name] = it
    }
}

class InvokeFn(val name:String, val args:List<Expr>): Expr() {
    override fun eval(runtime:Runtime): Data {
        val func = runtime.symbolTable[name]
        if(func == null || func !is FuncData)
            throw Exception("$name is not a function.")
        if(func.params.size != args.size) throw Exception(
            "$name expects ${func.params.size} arguments but received ${args.size}."
        )

        val r = runtime.subscope(
            func.params.zip(args.map {it.eval(runtime)}).toMap()
        )
        return func.body.eval(r)
    }
}

class Print(val expr:Expr, val newLine:Boolean): Expr() {
    override fun eval(runtime:Runtime): Data {
        val string = if(expr is NoneExpr) "" else "${expr.eval(runtime)}"
        print(if(newLine) "$string\n" else string)
        return None
    }
}
