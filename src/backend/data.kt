package backend

interface Data

object None: Data {
    override fun toString() = "None"
}


// Primitives & Strings
interface HashableData: Data {
    override fun hashCode(): Int
}

class StringData(val value:String): HashableData {
    override fun toString() = "$value"
    override fun hashCode() = value.hashCode()
    override fun equals(other:Any?): Boolean {
        if(other !is StringData) return false
        return value == other.value
    }
}

class IntData(val value:Int): HashableData {
    override fun toString() = "$value"
    override fun hashCode() = value.hashCode()
    override fun equals(other:Any?): Boolean {
        if(other !is IntData) return false
        return value == other.value
    }

}

class FloatData(val value:Float): HashableData {
    override fun toString() = "$value"
    override fun hashCode() = value.hashCode()
    override fun equals(other:Any?): Boolean {
        if(other !is FloatData) return false
        return value == other.value
    }

}

class BooleanData(val value:Boolean): HashableData {
    override fun toString() = "$value"
    override fun hashCode() = value.hashCode()
    override fun equals(other:Any?): Boolean {
        if(other !is BooleanData) return false
        return value == other.value
    }
}


// Collections
interface IterableData: Data {
    val iter: Iterable<Data>
}

class ListData(override val iter:MutableList<Data>): IterableData {
    override fun toString(): String {
        var output = "[ "
        iter.forEach { output += if(it is StringData) "\"$it\", " else "$it, " }
        return if(iter.isEmpty()) output + "]" else output.dropLast(2) + " ]"
    }
}

class DictData(val mappings:HashMap<Data, Data>): IterableData {
    override val iter = mappings.keys

    override fun toString(): String {
        var output = "{ "
        mappings.forEach { (k, v) ->
            output += if(k is StringData) "\"$k\": " else "$k: "
            output += if(v is StringData) "\"$v\", " else "$v, "
        }
        return if(mappings.isEmpty()) output + "}" else output.dropLast(2) + " }"
    }
}


// Integer ranges
class IntDataRange(val start:IntData, val end:IntData): Iterator<IntData>, Iterable<IntData> {
    val descending:Boolean
    val step:Int
    var current = start

    init {
        if(start.value <= end.value) {
            step = 1
            descending = false
        } else {
            step = -1
            descending = true
        }
    }

    override fun hasNext() = when(descending) {
        false -> current.value <= end.value
        true  -> current.value >= end.value
    }

    override fun next(): IntData {
        val result = current
        current = IntData(current.value + step)
        return result
    }

    override fun iterator() = this
}

class RangeData(start:IntData, end:IntData): IterableData {
    override val iter = IntDataRange(start, end)
    override fun toString() = "${iter.start}..${iter.end}"
}


// Functions
class FuncData(val name:String, val params:List<String>, val body:Expr): Data {
    override fun toString() = params.joinToString(", ").let { "$name($it) { ... }" }
}
