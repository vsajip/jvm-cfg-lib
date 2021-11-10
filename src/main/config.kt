//
//  Copyright (C) 2018-2021 Red Dove Consultants Limited
//
package com.reddove.config

import java.io.*

import java.lang.NoSuchFieldException
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.nio.charset.Charset
import java.nio.file.Paths
import java.util.*

import kotlin.collections.ArrayList

import org.apache.commons.math3.complex.Complex
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.regex.Pattern
import kotlin.collections.HashMap
import kotlin.math.pow

internal class NamedReader(path: String, encoding: String) : InputStreamReader(FileInputStream(path), Charset.forName(encoding)) {
    val name = path
}

internal fun getReader(path: String, encoding: String = "UTF-8") : Reader {
    // If only we could just do FileReader(path) ... unfortunately, there's no way
    // to specify an encoding, and it's guess can be often wrong, e.g. under
    // Windows. Hence the need to use InputStreamReader, etc.
    return NamedReader(path, encoding)
}

internal fun toDouble(n: Any) : Double {
    val result: Double

    result = when (n) {
        is Double -> {
            n
        }
        is Long -> {
            n.toDouble()
        }
        else -> {
            val msg = "cannot convert $n to an integer"

            throw IllegalArgumentException(msg)
        }
    }
    return result
}

internal fun toSource(node: Any) : String {
    if (node is Token) {
        return node.value.toString()
    }
    if (node !is ASTNode) {
        return node.toString()
    }
    val pi = pathIterator(node).iterator()
    val parts = StringBuilder((pi.next() as Token).value as String)

    for (x in pi) {
        val t = x as Pair<*, *>
        val op = t.first
        val operand = t.second

        when (op) {
            TokenKind.Dot -> {
                parts.append(".")
                parts.append(operand as String)
            }
            TokenKind.Colon -> {
                val sn = operand as SliceNode

                parts.append("[")
                if (sn.startIndex != null) {
                    parts.append(toSource(sn.startIndex))
                }
                parts.append(":")
                if (sn.stopIndex != null) {
                    parts.append(toSource(sn.stopIndex))
                }
                if (sn.step != null) {
                    parts.append(":")
                    parts.append(toSource(sn.step))
                }
                parts.append("]")
            }
            TokenKind.LeftBracket -> {
                parts.append("[")
                parts.append(toSource(operand!!))
                parts.append("]")
            }
            else -> {
                val msg = "unable to compute source for $node"

                throw ConfigException(msg)
            }
        }
    }
    return parts.toString()
}

internal fun stringFor(o: Any) : String {
    val result : String

    if (o is ArrayList<*>) {
        val parts = arrayListOf<String>()

        for (item in o) {
            parts.add(stringFor(item))
        }
        val contents = parts.joinToString { it }
        result = "[$contents]"
    }
    else if (o is HashMap<*, *>) {
        val parts = arrayListOf<String>()

        for ((k, v) in o) {
            parts.add("$k: ${stringFor(v)}")
        }
        val contents = parts.joinToString { it }
        result = "{$contents}"
    }
    else {
        result = o.toString()
    }
    return result
}

class Location(line: Int = 1, column: Int = 1) {

    var line: Int = line
        internal set

    var column: Int = column
        internal set

    constructor(other : Location) : this(other.line, other.column)

    internal fun nextLine() {
        line++
        column = 1
    }

    internal fun update(other: Location) {
        line = other.line
        column = other.column
    }

    override fun toString(): String {
        return "($line, $column)"
    }
}

enum class TokenKind {
    EOF,
    Word,
    Integer,
    Float,
    String,
    Newline,
    LeftCurly,
    RightCurly,
    LeftBracket,
    RightBracket,
    LeftParenthesis,
    RightParenthesis,
    LessThan,
    GreaterThan,
    LessThanOrEqual,
    GreaterThanOrEqual,
    Assign,
    Equal,
    Unequal,
    AltUnequal,
    LeftShift,
    RightShift,
    Dot,
    Comma,
    Colon,
    At,
    Plus,
    Minus,
    Star,
    Power,
    Slash,
    SlashSlash,
    Modulo,
    BackTick,
    Dollar,
    True,
    False,
    None,
    Is,
    In,
    Not,
    And,
    Or,
    BitwiseAnd,
    BitwiseOr,
    BitwiseXor,
    BitwiseComplement,
    Complex,
    IsNot,
    NotIn,
}

abstract class RecognizerException(message: String?, cause: Throwable?) : Exception(message, cause) {
    var location: Location? = null
        internal set
}

class TokenizerException(message: String?, cause: Throwable?) : RecognizerException(message, cause) {

    constructor(message: String?) : this(message, null)
}

class ParserException(message: String?, cause: Throwable?) : RecognizerException(message, cause) {

    constructor(message: String?) : this(message, null)
}

open class ConfigException(message: String?, cause: Throwable?) : RecognizerException(message, cause) {

    constructor(message: String?) : this(message, null)
}

class InvalidPathException(message: String?, cause: Throwable?) : ConfigException(message, cause) {

    constructor(message: String?) : this(message, null)
}

class BadIndexException(message: String?, cause: Throwable?) : ConfigException(message, cause) {

    constructor(message: String?) : this(message, null)
}

class CircularReferenceException(message: String?, cause: Throwable?) : ConfigException(message, cause) {

    constructor(message: String?) : this(message, null)
}

abstract class ASTNode(val kind: TokenKind) {
    var start: Location = Location()
    var end: Location = Location()
}

class Token(kind: TokenKind, val text: String, value: Any? = null) : ASTNode(kind) {
    var value: Any? = value
        internal set

    override fun toString(): String {
        return "Token($kind:$text:$value)"
    }
}

internal val punctuation = mapOf(
    ':' to TokenKind.Colon,
    '-' to TokenKind.Minus,
    '+' to TokenKind.Plus,
    '*' to TokenKind.Star,
    '/' to TokenKind.Slash,
    '%' to TokenKind.Modulo,
    ',' to TokenKind.Comma,
    '{' to TokenKind.LeftCurly,
    '}' to TokenKind.RightCurly,
    '[' to TokenKind.LeftBracket,
    ']' to TokenKind.RightBracket,
    '(' to TokenKind.LeftParenthesis,
    ')' to TokenKind.RightParenthesis,
    '@' to TokenKind.At,
    '$' to TokenKind.Dollar,
    '<' to TokenKind.LessThan,
    '>' to TokenKind.GreaterThan,
    '!' to TokenKind.Not,
    '~' to TokenKind.BitwiseComplement,
    '&' to TokenKind.BitwiseAnd,
    '|' to TokenKind.BitwiseOr,
    '^' to TokenKind.BitwiseXor,
    '.' to TokenKind.Dot,
    '=' to TokenKind.Assign
)

internal val keywords = mapOf(
    "true" to TokenKind.True,
    "false" to TokenKind.False,
    "null" to TokenKind.None,
    "is" to TokenKind.Is,
    "in" to TokenKind.In,
    "not" to TokenKind.Not,
    "and" to TokenKind.And,
    "or" to TokenKind.Or
)

internal val escapes = mapOf(
    'a' to '\u0007',
    'b' to '\b',
    'f' to '\u000C',
    'n' to '\n',
    'r' to '\r',
    't' to '\t',
    'v' to '\u000B',
    '\\' to '\\',
    '\'' to '\'',
    '"' to '"'
)

val NullValue = Any()

internal val keywordValues = mapOf(
    TokenKind.True to true,
    TokenKind.False to false,
    TokenKind.None to NullValue
)

internal val scalarTokens = setOf(
    TokenKind.String,
    TokenKind.Integer,
    TokenKind.Float,
    TokenKind.Complex,
    TokenKind.False,
    TokenKind.True,
    TokenKind.None
)

private class TokenIterator(val container: Tokenizer) : Iterator<Token> {
    private var more = true

    override fun next(): Token {
        val result = container.getToken()

        more = result.kind != TokenKind.EOF
        return result
    }

    override fun hasNext(): Boolean {
        return more
    }
}

private fun isHexDigit(c: Char): Boolean {
    var result = c.isDigit()

    if (!result) {
        if ((c >= 'a') && (c <= 'f')) {
            result = true
        }
        else if ((c >= 'A') && (c <= 'F')) {
            result = true
        }
    }
    return result
}

internal fun StringBuilder.indexOf(c: Char, pos: Int = 0): Int {
    var result = -1
    var i = pos

    while (i < this.length) {
        if (this[i] == c) {
            result = i
            break
        }
        ++i
    }
    return result
}

internal class Tokenizer(r: Reader) : Iterable<Token> {

    private val pushedBack = Stack<Pair<Char, Location>>()
    private val location = Location()
    private val charLocation = Location()
    private val reader: BufferedReader = r as? BufferedReader ?: BufferedReader(r)

    private fun pushBack(c: Char) {
        if (c != '\u0000') {
            pushedBack.push(Pair(c, Location(charLocation)))
        }
    }

    private fun getChar(): Char {
        val result: Char

        if (pushedBack.size > 0) {
            val t = pushedBack.pop()
            val loc = t.second

            result = t.first
            charLocation.update(loc)
            location.update(loc) // will be bumped later
        }
        else {
            charLocation.update(location)

            val i = reader.read()

            result = if (i < 0) '\u0000' else i.toChar()
        }
        if (result != '\u0000') {
            if (result == '\n')
                location.nextLine()
            else
                location.column++
        }
        return result
    }

    private fun appendChar(text: StringBuilder, c: Char, endLocation: Location) {
        text.append(c)
        endLocation.update(charLocation)
    }

    private fun getNumber(text: StringBuilder, startLocation: Location, endLocation: Location): Pair<TokenKind, Any> {
        var result = TokenKind.Integer
        var inExponent = false
        var radix = 0
        var dotSeen = text.indexOf('.') >= 0
        var lastWasDigit = text[text.length - 1].isDigit()
        var c: Char

        while (true) {
            c = getChar()

            if (c == '\u0000') {
                break
            }
            if (c == '.') {
                dotSeen = true
            }
            if (c == '_') {
                if (lastWasDigit) {
                    appendChar(text, c, endLocation)
                    lastWasDigit = false
                    continue
                }
                val msg = "Invalid '_' in number: $text$c"
                val e = TokenizerException(msg)

                e.location = charLocation
                throw e
            }
            lastWasDigit = false  // unless set in one of the clauses below
            if (((radix == 0) && (c >= '0') && (c <= '9')) ||
                ((radix == 2) && (c >= '0') && (c <= '1')) ||
                ((radix == 8) && (c >= '0') && (c <= '7')) ||
                ((radix == 16) && isHexDigit(c))) {
                appendChar(text, c, endLocation)
                lastWasDigit = true
            }
            else if (((c == 'o') || (c == 'O') || (c == 'x') ||
                      (c == 'X') || (c == 'b') || (c == 'B')) &&
                      (text.length == 1) && (text[0] == '0')) {
                radix = if ((c == 'x') || (c == 'X')) {
                    16
                } else if ((c == 'o') || (c == 'O')) {
                    8
                } else {
                    2
                }
                appendChar(text, c, endLocation)
                // lastWasDigit = false
            }
            else if ((radix == 0) && (c == '.') && !inExponent && (text.indexOf(c) < 0)) {
                appendChar(text, c, endLocation)
            }
            else if ((radix == 0) && (c == '-') && (text.indexOf('-', 1) < 0) && inExponent) {
                appendChar(text, c, endLocation)
            }
            else if ((radix == 0) && ((c == 'e') || (c == 'E')) && (text.indexOf('e') < 0) &&
                     (text.indexOf('E') < 0) && (text[text.length - 1] != '_')) {
                appendChar(text, c, endLocation)
                inExponent = true
            }
            else {
                break
            }
        }

        // Reached the end of the actual number part. Before checking
        // for complex, ensure that the last char wasn't an underscore.
        if (text[text.length - 1] == '_') {
            val msg = "Invalid '_' at end of number: $text"
            val e = TokenizerException(msg)

            e.location = endLocation
            throw e
        }
        if ((radix == 0) && ((c == 'j') || (c == 'J'))) {
            appendChar(text, c, endLocation)
            result = TokenKind.Complex
        }
        else {
            // not allowed to have a letter or digit which wasn't accepted
            if ((c != '.') && !c.isLetterOrDigit()) {
                pushBack(c)
            }
            else {
                val msg = "Invalid character in number: $c"
                val e = TokenizerException(msg)

                e.location = charLocation
                throw e
            }
        }

        val s = text.toString().replace("_", "")
        val value: Any

        if (radix != 0) {
            value = s.substring(2).toLong(radix)
        }
        else if (result == TokenKind.Complex) {
            val imaginary = s.substring(0, s.length - 1).toDouble()
            value = Complex(0.0, imaginary)
        }
        else if (inExponent || dotSeen) {
            result = TokenKind.Float
            value = s.toDouble()
        }
        else {
            radix = if (s[0] == '0') 8 else 10
            try {
                value = s.toLong(radix)
            }
            catch (fe: NumberFormatException) {
                val e = TokenizerException("Invalid character in number: $s", fe)

                e.location = startLocation
                throw e
            }
        }
        return Pair(result, value)
    }

    private fun parseEscapes(inp : String) : String {
        val result : String
        var s = inp
        var i = s.indexOf('\\')

        if (i < 0) {
            result = s
        }
        else {
            val sb = StringBuilder()
            var failed = false

            while (i >= 0) {
                val n = s.length

                if (i > 0) {
                    sb.append(s.substring(0, i))
                }
                val c = s[i + 1]
                if (c in escapes) {
                    sb.append(escapes[c])
                    i += 2
                }
                else if ((c == 'x') || (c == 'X') || (c == 'u') || (c == 'U')) {
                    val slen : Int = if ((c == 'x') || (c == 'X')) {
                        4
                    } else if (c == 'u') {
                        6
                    } else {
                        10
                    }

                    if ((i + slen) > n) {
                        failed = true
                        break
                    }
                    val p = s.substring(i + 2, i + slen)
                    try {
                        val j = p.toInt(16)
                        if ((j in 0xd800..0xdfff) || (j >= 0x110000)) {
                            failed = true
                            break
                        }
                        if (j < 0x10000) {
                            sb.append(j.toChar())
                        }
                        else {
                            // push as a surrogate pair
                            val high = ((j - 0x10000) ushr 10) or 0xd800
                            val low = (j and 0x3FF) or 0xdc00

                            sb.append(high.toChar())
                            sb.append(low.toChar())
                        }
                        i += slen
                    }
                    catch (fe: NumberFormatException) {
                        failed = true
                        break
                    }
                }
                else {
                    failed = true
                    break
                }
                s = s.substring(i)
                i = s.indexOf('\\')
            }
            if (failed) {
                throw TokenizerException("Invalid escape sequence at index $i")
            }
            sb.append(s)
            result = sb.toString()
        }
        return result
    }

    fun getToken(): Token {
        var kind = TokenKind.EOF
        val text = StringBuilder()
        var s: String? = null
        var value: Any? = null
        val startLocation = Location()
        val endLocation = Location()

        while (true) {
            var c = getChar()

            startLocation.update(charLocation)
            endLocation.update(charLocation)

            if (c == '\u0000') {
                break
            }
            if (c == '#') {
                text.append(c).append(reader.readLine())
                kind = TokenKind.Newline
                location.nextLine()
                endLocation.update(location)
                endLocation.column -= 1
                break
            }
            else if (c == '\n') {
                text.append(c)
                endLocation.update(location)
                endLocation.column -= 1
                kind = TokenKind.Newline
                break
            }
            else if (c == '\r') {
                c = getChar()
                if (c != '\n') {
                    pushBack(c)
                }
                text.append(c)
                endLocation.update(location)
                endLocation.column -= 1
                kind = TokenKind.Newline
                break
            }
            else if (c == '\\') {
                c = getChar()
                if (c == '\r') {
                    c = getChar()
                }
                if (c != '\n') {
                    val e = TokenizerException("Unexpected character: \\")

                    e.location = charLocation
                    throw e
                }
                endLocation.update(charLocation)
                continue
            }
            else if (c.isWhitespace()) {
                continue
            }
            else if (c.isLetter() || (c == '_')) {
                kind = TokenKind.Word
                appendChar(text, c, endLocation)
                c = getChar()
                while ((c != '\u0000') && (c.isLetterOrDigit() || (c == '_'))) {
                    appendChar(text, c, endLocation)
                    c = getChar()
                }
                pushBack(c)
                s = text.toString()
                value = s
                if (keywords.containsKey(s)) {
                    kind = keywords[s] ?: error("unexpected null value in internal lookup")
                    if (keywordValues.containsKey(kind)) {
                        value = keywordValues[kind]
                    }
                }
                break
            }
            else if (c == '`') {
                kind = TokenKind.BackTick
                appendChar(text, c, endLocation)
                while (true) {
                    c = getChar()
                    if (c == '\u0000') {
                        break
                    }
                    appendChar(text, c, endLocation)
                    if (c == '`') {
                        break
                    }
                }
                if (c == '\u0000') {
                    val msg = "Unterminated `-string: $text"
                    val e = TokenizerException(msg)

                    e.location = startLocation
                    throw e
                }
                s = text.toString()
                try {
                    value = parseEscapes(s.substring(1, text.length - 1))
                }
                catch (e: RecognizerException) {
                    e.location = startLocation
                    throw e
                }
                break
            }
            else if ((c == '\'') || (c == '\"')) {
                val quote = c
                var multiLine = false
                var escaped = false
                var n: Int

                kind = TokenKind.String

                text.append(c)
                val c1 = getChar()
                val c1Loc = Location(charLocation)

                if (c1 != quote) {
                    pushBack(c1)
                }
                else {
                    val c2 = getChar()

                    if (c2 != quote) {
                        pushBack(c2)
                        charLocation.update(c1Loc)
                        pushBack(c1)
                    }
                    else {
                        multiLine = true
                        text.append(quote).append(quote)
                    }
                }

                val quoter = text.toString()

                while (true) {
                    c = getChar()
                    if (c == '\u0000') {
                        break
                    }
                    appendChar(text, c, endLocation)
                    if ((c == quote) && !escaped) {
                        n = text.length

                        if (!multiLine || (n >= 6) && (text.substring(n - 3, n) == quoter) && text[n - 4] != '\\') {
                            break
                        }
                    }
                    escaped = if (c == '\\') {
                        !escaped
                    }
                    else {
                        false
                    }
                }
                if (c == '\u0000') {
                    val msg = "Unterminated quoted string: $text"
                    val e = TokenizerException(msg)

                    e.location = startLocation
                    throw e
                }
                n = quoter.length
                s = text.toString()
                try {
                    value = parseEscapes(s.substring(n, text.length - n))
                }
                catch (e: RecognizerException) {
                    e.location = startLocation
                    throw e
                }
                break
            }
            else if (c.isDigit()) {
                appendChar(text, c, endLocation)
                val t = getNumber(text, startLocation, endLocation)
                kind = t.first
                value = t.second
                break
            }
            else if (punctuation.containsKey(c)) {
                kind = punctuation[c] ?: error("unexpected null value in internal lookup")
                appendChar(text, c, endLocation)
                if (c == '.') {
                    c = getChar()
                    if (!c.isDigit()) {
                        pushBack(c)
                    }
                    else {
                        appendChar(text, c, endLocation)
                        val t = getNumber(text, startLocation, endLocation)
                        kind = t.first
                        value = t.second
                    }
                }
                else if (c == '=') {
                    c = getChar()
                    if (c != '=') {
                        pushBack(c)
                    }
                    else {
                        kind = TokenKind.Equal
                        appendChar(text, c, endLocation)
                    }
                }
                else if (c == '-') {
                    c = getChar()
                    if (!c.isDigit() && (c != '.')) {
                        pushBack(c)
                    }
                    else {
                        appendChar(text, c, endLocation)
                        val t = getNumber(text, startLocation, endLocation)
                        kind = t.first
                        value = t.second
                    }
                }
                else if (c == '<') {
                    c = getChar()
                    when (c) {
                        '=' -> {
                            kind = TokenKind.LessThanOrEqual
                            appendChar(text, c, endLocation)
                        }
                        '>' -> {
                            kind = TokenKind.AltUnequal
                            appendChar(text, c, endLocation)
                        }
                        '<' -> {
                            kind = TokenKind.LeftShift
                            appendChar(text, c, endLocation)
                        }
                        else -> pushBack(c)
                    }
                }
                else if (c == '>') {
                    c = getChar()
                    when (c) {
                        '=' -> {
                            kind = TokenKind.GreaterThanOrEqual
                            appendChar(text, c, endLocation)
                        }
                        '>' -> {
                            kind = TokenKind.RightShift
                            appendChar(text, c, endLocation)
                        }
                        else -> pushBack(c)
                    }
                }
                else if (c == '!') {
                    c = getChar()
                    if (c == '=') {
                        kind = TokenKind.Unequal
                        appendChar(text, c, endLocation)
                    }
                    else {
                        pushBack(c)
                    }
                }
                else if (c == '/') {
                    c = getChar()
                    if (c != '/') {
                        pushBack(c)
                    }
                    else {
                        kind = TokenKind.SlashSlash
                        appendChar(text, c, endLocation)
                    }
                }
                else if (c == '*') {
                    c = getChar()
                    if (c != '*') {
                        pushBack(c)
                    }
                    else {
                        kind = TokenKind.Power
                        appendChar(text, c, endLocation)
                    }
                }
                else if ((c == '&') || (c == '|')) {
                    val c2 = getChar()

                    if (c2 != c) {
                        pushBack(c2)
                    }
                    else {
                        kind = if (c2 == '&') {
                            TokenKind.And
                        } else {
                            TokenKind.Or
                        }
                        appendChar(text, c2, endLocation)
                    }
                }
                break
            }
            else {
                val e = TokenizerException("Unexpected character: $c")

                e.location = charLocation
                throw e
            }
        }
        if (s == null) {
            s = text.toString()
        }
        val result = Token(kind, s)
        result.value = value
        result.start.update(startLocation)
        result.end.update(endLocation)
        return result
    }

    override fun iterator(): Iterator<Token> {
        return TokenIterator(this)
    }

}

internal class UnaryNode(kind: TokenKind, val operand: ASTNode) : ASTNode(kind) {
    override fun toString(): String {
        return "UnaryNode($kind, $operand)"
    }
}

internal class BinaryNode(kind: TokenKind, val left: ASTNode, val right: ASTNode) : ASTNode(kind) {
    override fun toString(): String {
        return "BinaryNode($kind, $left, $right)"
    }
}

internal class SliceNode(val startIndex: ASTNode?, val stopIndex: ASTNode?, val step: ASTNode?) : ASTNode(TokenKind.Colon) {
    override fun toString(): String {
        return "SliceNode($startIndex:$stopIndex:$step)"
    }
}

internal class ListNode(val elements: ArrayList<ASTNode>) : ASTNode(TokenKind.LeftBracket)

internal class MappingNode(val elements: ArrayList<Pair<ASTNode, ASTNode>>) : ASTNode(TokenKind.LeftCurly)

// class is not internal because used by expr_repl.kts
class Parser(r: Reader) {
    private val tokenizer = Tokenizer(r)
    internal var next = tokenizer.getToken()  // used in parsePath for checking start of path

    val atEnd : Boolean
        get() = (next.kind == TokenKind.EOF)

    private fun advance(): TokenKind {
        next = tokenizer.getToken()
        return next.kind
    }

    private fun expect(kind: TokenKind): Token {
        if (next.kind != kind) {
            val e = ParserException("Expected $kind but got ${next.kind}")

            e.location = next.start
            throw e
        }
        val result = next
        advance()
        return result
    }

    private fun consumeNewlines(): TokenKind {
        var result = next.kind

        while (result == TokenKind.Newline) {
            result = advance()
        }
        return result
    }

    private val expressionStarters = setOf(
        TokenKind.LeftCurly, TokenKind.LeftBracket, TokenKind.LeftParenthesis,
        TokenKind.At, TokenKind.Dollar, TokenKind.BackTick, TokenKind.Plus,
        TokenKind.Minus, TokenKind.BitwiseComplement, TokenKind.Integer,
        TokenKind.Float, TokenKind.Complex, TokenKind.True, TokenKind.False,
        TokenKind.None, TokenKind.Not, TokenKind.String, TokenKind.Word
    )

    private val valueStarters = setOf(
        TokenKind.Word,
        TokenKind.Integer,
        TokenKind.Float,
        TokenKind.Complex,
        TokenKind.String,
        TokenKind.BackTick,
        TokenKind.None,
        TokenKind.True,
        TokenKind.False
    )

    private fun strings(): Token {
        var result = next

        if (advance() == TokenKind.String) {
            val allText = StringBuilder()
            val allValue = StringBuilder()
            var kind: TokenKind
            var end: Location
            var t = result.text
            var v = result.value.toString()
            val start = result.start

            do {
                allText.append(t)
                allValue.append(v)
                t = next.text
                v = next.value.toString()
                end = next.end
                kind = advance()
            } while (kind == TokenKind.String)
            allText.append(t) // the last one
            allValue.append(v)
            result = Token(TokenKind.String, allText.toString(), allValue.toString())
            result.start.update(start)
            result.end.update(end)
        }
        return result
    }

    // not private - used in tests
    internal fun value(): ASTNode {
        val kind = next.kind
        val t: Token

        if (kind !in valueStarters) {
            val e = ParserException("Unexpected when looking for value: $kind")

            e.location = next.start
            throw e
        }

        if (kind == TokenKind.String) {
            t = strings()
        }
        else {
            t = next
            advance()
        }
        return t
    }

    // Not private, as used in tests
    internal fun atom(): ASTNode {
        val kind = next.kind
        val result: ASTNode

        when (kind) {
            TokenKind.LeftCurly -> {
                result = mapping()
            }
            TokenKind.LeftBracket -> {
                result = list()
            }
            TokenKind.Dollar -> {
                advance()
                expect(TokenKind.LeftCurly)
                val spos = next.start
                result = UnaryNode(TokenKind.Dollar, primary())
                result.start = spos
                expect(TokenKind.RightCurly)
            }
            TokenKind.Word, TokenKind.Integer, TokenKind.Float,
            TokenKind.Complex, TokenKind.String,
            TokenKind.BackTick, TokenKind.True, TokenKind.False, TokenKind.None -> {
                result = value()
            }
            TokenKind.LeftParenthesis -> {
                advance()
                result = expr()
                expect(TokenKind.RightParenthesis)
            }
            else -> {
                val e = ParserException("Unexpected: $kind")

                e.location = next.start
                throw e
            }
        }
        return result
    }

    private fun trailer() : Pair<TokenKind, ASTNode> {
        var op = next.kind
        var result: ASTNode? = null

        fun invalidIndex(n: Int, pos: Location) {
            val msg = "Invalid index at $pos: expected 1 expression, found $n"
            val e = ParserException(msg)
            e.location = pos
            throw e
        }

        if (op != TokenKind.LeftBracket) {
            expect(TokenKind.Dot)
            result = expect(TokenKind.Word)
        }
        else {
            var kind = advance()
            var isSlice = false
            var startIndex: ASTNode? = null
            var stopIndex: ASTNode? = null
            var step: ASTNode? = null

            fun getSliceElement() : ASTNode {
                val lb = listBody()
                val size = lb.elements.size

                if (size != 1) {
                    invalidIndex(size, lb.start)
                }
                return lb.elements[0]
            }

            fun tryGetStep() {
                kind = advance()
                if (kind != TokenKind.RightBracket) {
                    step = getSliceElement()
                }
            }

            if (kind == TokenKind.Colon) {
                // it's a slice like [:xyz:abc]
                isSlice = true
            }
            else {
                val elem = getSliceElement()

                kind = next.kind
                if (kind != TokenKind.Colon) {
                    result = elem
                }
                else {
                    startIndex = elem
                    isSlice = true
                }
            }
            if (isSlice) {
                op = TokenKind.Colon
                // at this point startIndex is either null (if foo[:xyz]) or a
                // value representing the start. We are pointing at the COLON
                // after the start value
                kind = advance()
                if (kind == TokenKind.Colon) {  // no stop, but there might be a step
                    tryGetStep()
                }
                else if (kind != TokenKind.RightBracket) {
                    stopIndex = getSliceElement()
                    kind = next.kind
                    if (kind == TokenKind.Colon) {
                        tryGetStep()
                    }
                }
                result = SliceNode(startIndex, stopIndex, step)
            }
            expect(TokenKind.RightBracket)
        }
        return Pair(op, result!!)
    }

    internal fun primary(): ASTNode {
        var result = atom()

        while ((next.kind == TokenKind.Dot) || (next.kind == TokenKind.LeftBracket)) {
            val p = trailer()
            result = BinaryNode(p.first, result, p.second)
        }
        return result
    }

    private fun mappingKey(): Token {
        val result: Token

        if (next.kind == TokenKind.String) {
            result = strings()
        }
        else {
            result = next
            advance()
        }
        return result
    }

    internal fun mappingBody(): MappingNode {
        val result = ArrayList<Pair<ASTNode, ASTNode>>()
        var kind = consumeNewlines()
        val spos = next.start

        if ((kind != TokenKind.RightCurly) && (kind != TokenKind.EOF)) {
            if ((kind != TokenKind.Word) && (kind != TokenKind.String)) {
                val e = ParserException("Unexpected type for key: $kind")

                e.location = next.start
                throw e
            }
            while ((kind == TokenKind.Word) || (kind == TokenKind.String)) {
                val key = mappingKey()
                kind = next.kind
                if ((kind != TokenKind.Colon) && (kind != TokenKind.Assign)) {
                    val e = ParserException("Expected key-value separator, found: $kind")

                    e.location = next.start
                    throw e
                }
                advance()
                consumeNewlines()
                result.add(Pair(key, expr()))
                kind = next.kind
                if ((kind == TokenKind.Newline) || (kind == TokenKind.Comma)) {
                    advance()
                    kind = consumeNewlines()
                }
                else if ((kind != TokenKind.RightCurly) && (kind != TokenKind.EOF)) {
                    val e = ParserException("Unexpected following value: $kind")

                    e.location = next.start
                    throw e
                }
            }
        }
        val mn = MappingNode(result)
        mn.start = spos
        return mn
    }

    internal fun mapping(): MappingNode {
        expect(TokenKind.LeftCurly)
        val result = mappingBody()
        expect(TokenKind.RightCurly)
        return result
    }

    private fun listBody(): ListNode {
        val result = ArrayList<ASTNode>()
        var kind = consumeNewlines()
        val spos = next.start

        while (kind in expressionStarters) {
            result.add(expr())
            kind = next.kind
            if ((kind != TokenKind.Newline) && (kind != TokenKind.Comma)) {
                break
            }
            advance()
            kind = consumeNewlines()
        }
        val ln = ListNode(result)
        ln.start = spos
        return ln
    }

    private fun list(): ListNode {
        expect(TokenKind.LeftBracket)
        val result = listBody()
        expect(TokenKind.RightBracket)
        return result
    }

    fun container(): ASTNode {
        val kind = consumeNewlines()
        val result: ASTNode

        if (kind == TokenKind.LeftCurly) {
            result = mapping()
        }
        else if (kind == TokenKind.LeftBracket) {
            result = list()
        }
        else if (kind == TokenKind.Word || kind == TokenKind.String || kind == TokenKind.EOF) {
            result = mappingBody()
        }
        else {
            val e = ParserException("Unexpected type for container: $kind")

            e.location = next.start
            throw e
        }
        consumeNewlines()
        return result
    }

    // not private - used in tests
    internal fun power(): ASTNode {
        var result = primary()

        while (next.kind == TokenKind.Power) {
            advance()
            result = BinaryNode(TokenKind.Power, result, unaryExpr())
        }
        return result
    }

    private fun unaryExpr(): ASTNode {
        val result: ASTNode
        val kind = next.kind
        val spos = next.start
        result = if ((kind != TokenKind.Plus) && (kind != TokenKind.Minus) &&
                (kind != TokenKind.BitwiseComplement) && (kind != TokenKind.At)) {
            power()
        }
        else {
            advance()
            UnaryNode(kind, unaryExpr())
        }
        result.start = spos
        return result
    }

    // not private - used in tests
    internal fun mulExpr(): ASTNode {
        var result = unaryExpr()
        var kind = next.kind

        while ((kind == TokenKind.Star) || (kind == TokenKind.Slash) ||
               (kind == TokenKind.SlashSlash) || (kind == TokenKind.Modulo)) {
            advance()
            result = BinaryNode(kind, result, unaryExpr())
            kind = next.kind
        }
        return result
    }

    // not private - used in tests
    internal fun addExpr(): ASTNode {
        var result = mulExpr()
        var kind = next.kind

        while ((kind == TokenKind.Plus) || (kind == TokenKind.Minus)) {
            advance()
            result = BinaryNode(kind, result, mulExpr())
            kind = next.kind
        }
        return result
    }

    // not private - used in tests
    internal fun shiftExpr(): ASTNode {
        var result = addExpr()
        var kind = next.kind

        while ((kind == TokenKind.LeftShift) || (kind == TokenKind.RightShift)) {
            advance()
            result = BinaryNode(kind, result, addExpr())
            kind = next.kind
        }
        return result
    }

    // not private - used in tests
    internal fun bitandExpr(): ASTNode {
        var result = shiftExpr()

        while (next.kind == TokenKind.BitwiseAnd) {
            advance()
            result = BinaryNode(TokenKind.BitwiseAnd, result, shiftExpr())
        }
        return result
    }

    // not private - used in tests
    internal fun bitxorExpr(): ASTNode {
        var result = bitandExpr()

        while (next.kind == TokenKind.BitwiseXor) {
            advance()
            result = BinaryNode(TokenKind.BitwiseXor, result, bitandExpr())
        }
        return result
    }

    // not private - used in tests
    internal fun bitorExpr(): ASTNode {
        var result = bitxorExpr()

        while (next.kind == TokenKind.BitwiseOr) {
            advance()
            result = BinaryNode(TokenKind.BitwiseOr, result, bitxorExpr())
        }
        return result
    }

    private fun compOp(): TokenKind {
        var result = next.kind
        var shouldAdvance = false

        advance()
        if ((result == TokenKind.Is) && (next.kind == TokenKind.Not)) {
            result = TokenKind.IsNot
            shouldAdvance = true
        }
        else if ((result == TokenKind.Not) && (next.kind == TokenKind.In)) {
            result = TokenKind.NotIn
            shouldAdvance = true
        }
        if (shouldAdvance) {
            advance()
        }
        return result
    }

    private val comparisonOperators = setOf(
        TokenKind.LessThan,
        TokenKind.LessThanOrEqual,
        TokenKind.GreaterThan,
        TokenKind.GreaterThanOrEqual,
        TokenKind.Equal,
        TokenKind.Unequal,
        TokenKind.AltUnequal,
        TokenKind.Is,
        TokenKind.In,
        TokenKind.Not
    )

    // not private - used in tests
    internal fun comparison(): ASTNode {
        var result = bitorExpr()

        if (next.kind in comparisonOperators) {
            val op = compOp()

            result = BinaryNode(op, result, bitorExpr())
        }
        return result
    }

    private fun notExpr(): ASTNode {

        return if (next.kind != TokenKind.Not) {
            comparison()
        }
        else {
            advance()
            UnaryNode(TokenKind.Not, notExpr())
        }
    }

    // not private - used in tests
    internal fun andExpr(): ASTNode {
        var result = notExpr()

        while (next.kind == TokenKind.And) {
            advance()
            result = BinaryNode(TokenKind.And, result, notExpr())
        }
        return result
    }

    fun expr(): ASTNode {
        var result = andExpr()

        while (next.kind == TokenKind.Or) {
            advance()
            result = BinaryNode(TokenKind.Or, result, andExpr())
        }
        return result
    }
}

// utility functions

internal fun makeParser(source: String): Parser {
    return Parser(StringReader(source))
}

internal fun parse(source: String, rule: String = "mappingBody"): ASTNode {
    val p = makeParser(source)
    val f = p::class.members.find { it.name == rule } ?: throw IllegalArgumentException("unknown rule: $rule")

    try {
        return f.call(p) as ASTNode
    }
    catch (e: InvocationTargetException) {
        throw if (e.cause != null) e.cause!! else e
    }
}

internal fun unwrap(o: Any) : Any {
    if (o is DictWrapper)
        return o.asDict()
    if (o is ListWrapper)
        return o.asList()
//    if (o is Config)
//        return o.asDict()
    return o
}

internal class DictWrapper(private val config: Config): LinkedHashMap<String, Any>(16, 0.75F, false) {  // preserves insertion order
    override fun get(key: String) : Any {
        return if (containsKey(key)) {
            config.evaluated(super.get(key)!!)
        }
        else {
            throw ConfigException("Not found in configuration: $key")
        }
    }

    internal fun baseGet(key: String) : Any {
        return super.get(key)!!
    }

    internal fun asDict() : HashMap<String, Any> {
        val result = hashMapOf<String, Any>()

        for ((k, v) in this) {
            var rv = config.evaluated(v)

            if (rv is DictWrapper) {
                rv = rv.asDict()
            }
            else if (rv is Config) {
                rv = rv.asDict()
            }
            else if (rv is ListWrapper) {
                rv = rv.asList()
            }
            result[k] = rv
        }
        return result
    }
}

internal class ListWrapper(internal val config: Config): ArrayList<Any>() {
    override fun get(index: Int): Any {
        val result = config.evaluated(super.get(index))

        this[index] = result
        return result
    }

    internal fun baseGet(index: Int): Any {
        return super.get(index)
    }

    internal fun asList() : ArrayList<Any> {
        val result = arrayListOf<Any>()

        for (i in indices) {
            val rv : Any
            val elem = this[i]

            rv = when (elem) {
                is ListWrapper -> {
                    elem.asList()
                }
                is DictWrapper -> {
                    elem.asDict()
                }
                is Config -> {
                    elem.asDict()
                }
                else -> {
                    config.evaluated(elem)
                }
            }
            result.add(rv)
        }
        return result
    }
}

typealias StringConverter = (String, Config) -> Any

internal val ISO_DATETIME_PATTERN = Regex("""^(\d{4})-(\d{2})-(\d{2})(([ T])(((\d{2}):(\d{2}):(\d{2}))(\.\d{1,6})?(([+-])(\d{2}):(\d{2})(:(\d{2})(\.\d{1,6})?)?)?))?${'$'}""")
internal val ENV_VALUE_PATTERN = Regex("""^\$(\w+)(\|(.*))?$""")
internal val COLON_OBJECT_PATTERN = Regex("""^([A-Za-z_]\w*(\.[A-Za-z_]\w*)*)(:([A-Za-z_]\w*))?$""")
internal val INTERPOLATION_PATTERN = Regex("""\$\{([^}]+)\}""")

val defaultStringConverter = fun(s: String, cfg: Config) : Any {
    var result: Any = s
    var m = ISO_DATETIME_PATTERN.find(s)?.groupValues?.toTypedArray()

    if (m != null) {
        val year = m[1].toInt()
        val month = m[2].toInt()
        val day = m[3].toInt()
        val hasTime = m[5] != ""

        if (!hasTime) {
            result = LocalDate.of(year, month, day)
        }
        else {
            val hour = m[8].toInt()
            val minute = m[9].toInt()
            val second = m[10].toInt()
            val hasOffset = m[13] != ""
            var nanosecond = 0

            if (m[11] != "") {
                nanosecond = (m[11].toDouble() * 1.0e9).toInt()
            }

            result = LocalDateTime.of(year, month, day, hour, minute, second,
                                      nanosecond)

            if (hasOffset) {
                val sign = if (m[13] == "-") -1 else 1
                val ohour = m[14].toInt()
                val ominute = m[15].toInt()
                val osecond = if (m[17] == "") { 0 } else { m[17].toInt() }
                val offset = ZoneOffset.ofHoursMinutesSeconds(ohour * sign,
                                                              ominute * sign,
                                                              osecond * sign)

                result = OffsetDateTime.of(result, offset)
            }
        }
    }
    else {
        m = ENV_VALUE_PATTERN.find(s)?.groupValues?.toTypedArray()

        if (m != null) {
            val varName = m[1]
            val hasPipe = m[2] != ""
            val dv = if (!hasPipe) {
                NullValue
            }
            else {
                m[3]
            }
            result = System.getenv(varName) ?: dv
        }
        else {
            m = COLON_OBJECT_PATTERN.find(s)?.groupValues?.toTypedArray()
            if (m != null) {
                val publicOrStatic = Modifier.PUBLIC or Modifier.STATIC
                val fname = m[4]

                try {
                    result = Class.forName(m[1])
                    if (fname != "") {
                        try {
                            val f = result.getField(fname)

                            if ((f.modifiers and publicOrStatic) != publicOrStatic) {
                                val msg = "Field is not public or not static: $fname"
                                throw ConfigException(msg)
                            }
                            result = f.get(null)
                        }
                        catch (nfe: NoSuchFieldException) {
                            val meth = result.getMethod(fname)

                            if ((meth.modifiers and publicOrStatic) != publicOrStatic) {
                                val msg = "Method is not public or not static: $fname"
                                throw ConfigException(msg)
                            }
                            if (meth.parameterCount != 0) {
                                val msg = "Method should not have parameters: $fname"
                                throw ConfigException(msg)
                            }
                            result = meth.invoke(null)
                        }
                    }
                }
                catch (cnfe: ClassNotFoundException) {
                    // nothing to do
                }
            }
            else if (INTERPOLATION_PATTERN.containsMatchIn(s)) {
                val matches = INTERPOLATION_PATTERN.findAll(s)
                var cp = 0
                val sb = StringBuilder()
                var failed = false

                for (match in matches) {
                    val range = match.range
                    val path = match.groupValues[1];

                    if (range.first > cp) {
                        sb.append(s.substring(cp until range.first))
                    }
                    try {
                        sb.append(stringFor(cfg.get(path)))
                    }
                    catch (e: Exception) {
                        failed = true
                        break
                    }
                    cp = range.endInclusive + 1
                }
                if (!failed) {
                    if (cp < s.length) {
                        sb.append(s.substring(cp))
                    }
                    result = sb.toString()
                }
            }
        }
    }
    return result
}

private fun sameFile(s1: String, s2: String): Boolean {
    val p1 = Paths.get(s1)
    val p2 = Paths.get(s2)
    return p1 == p2
}

internal class Evaluator(private val config: Config) {
    val refsSeen = hashSetOf<ASTNode>()

    private fun evalAt(node: UnaryNode) : Any {
        val fn = evaluate(node.operand)

        if (fn !is String) {
            val ce = ConfigException("@ operand must be a string, but is $fn")

            ce.location = node.operand.start
            throw ce
        }

        var found = false
        var p = Paths.get(fn)
        val result: Any

        if (p.isAbsolute && p.toFile().exists()) {
            found = true
        }
        else {
            p = Paths.get(config.rootDir, fn)

            if (p.toFile().exists()) {
                found = true
            }
            else {
                for (it in config.includePath) {
                    p = Paths.get(it, fn)

                    if (p.toFile().exists()) {
                        found = true
                        break
                    }
                }
            }
        }
        if (!found) {
            val e = ConfigException("Unable to locate $fn")

            e.location = node.operand.start
            throw e
        }

        if ((config.path != null) && sameFile(config.path!!, p.toString())) {
            val e = ConfigException("Configuration cannot include itself: $fn")

            e.location = node.operand.start
            throw e
        }

        val r = getReader(p.toString())
        val parser = Parser(r)
        val cnode = parser.container()

        if (cnode is MappingNode) {
            val cfg = Config()

            cfg.noDuplicates = config.noDuplicates
            cfg.strictConversions = config.strictConversions
            cfg.context = config.context
            cfg.setPath(p.toString())
            cfg.includePath = config.includePath
            if (config.cache != null) {
                cfg.cache = HashMap()
            }
            cfg.parent = config
            cfg.data = cfg.wrapMapping(cnode)
            result = cfg
        }
        else {
            result = cnode
        }
        return result
    }

    private fun evalReference(node: UnaryNode) : Any {
        return getFromPath(node.operand)
    }

    private fun mergeDicts(target : MutableMap<String, Any>, source : Map<String, Any>) {
        for ((k, v) in source) {
            if ((k in target) && (target[k] is MutableMap<*, *>) and (v is Map<*, *>)) {
                mergeDicts(target[k] as MutableMap<String, Any>, v as Map<String, Any>)
            }
            else {
                target[k] = v
            }
        }
    }

    private fun toComplex(n: Any) : Complex {
        val result: Complex

        when (n) {
            is Complex -> {
                result = n
            }
            is Long -> {
                result = Complex(n.toDouble(), 0.0)
            }
            is Double -> {
                result = Complex(n, 0.0)
            }
            else -> {
                val msg = "cannot convert $n to a complex number"

                throw IllegalArgumentException(msg)
            }
        }
        return result
    }

    private fun mergeDictWrappers(lhs: DictWrapper, rhs: DictWrapper) : DictWrapper {
        val result : DictWrapper
        val r = lhs.asDict()
        val source = rhs.asDict()

        mergeDicts(r, source)
        result = DictWrapper(config)
        for ((k, v) in r) {
            result[k] = v
        }
        return result
    }

    private fun evalAdd(node : BinaryNode) : Any {
        val lhs = evaluate(node.left)
        val rhs = evaluate(node.right)
        val result : Any

        when {
            (lhs is DictWrapper) and (rhs is DictWrapper) -> {
                result = mergeDictWrappers(lhs as DictWrapper, rhs as DictWrapper)
            }
            (lhs is String) and (rhs is String) -> {
                result = (lhs as String) + (rhs as String)
            }
            (lhs is Complex) or (rhs is Complex) -> {
                result = toComplex(lhs).add(toComplex(rhs))
            }
            (lhs is Number) and (rhs is Number) -> {
                result = if ((lhs is Double) or (rhs is Double)) {
                    toDouble(lhs) + toDouble(rhs)
                } else {
                    (lhs as Long) + (rhs as Long)
                }
            }
            (lhs is ListWrapper) and (rhs is ListWrapper) -> {
                val combined = ListWrapper((lhs as ListWrapper).config) // cast shouldn't really be needed :-(

                combined.addAll(lhs.asList())
                combined.addAll((rhs as ListWrapper).asList())
                result = combined
            }
            else -> {
                val msg = "unable to add $lhs and $rhs"

                throw IllegalArgumentException(msg)
            }
        }
        return result
    }

    private fun evalSubtract(node : BinaryNode) : Any {
        val lhs = evaluate(node.left)
        val rhs = evaluate(node.right)
        val result : Any

        when {
            (lhs is DictWrapper) and (rhs is DictWrapper) -> {
                val r = (lhs as DictWrapper).asDict()
                val source = (rhs as DictWrapper).asDict()

                result = DictWrapper(config)
                for ((k, v) in r) {
                    if (k !in source) {
                        result[k] = v
                    }
                }
            }
            (lhs is Complex) or (rhs is Complex) -> {
                result = toComplex(lhs).subtract(toComplex(rhs))
            }
            (lhs is Number) and (rhs is Number) -> {
                result = if ((lhs is Double) or (rhs is Double)) {
                    toDouble(lhs) - toDouble(rhs)
                } else {
                    (lhs as Long) - (rhs as Long)
                }
            }
            else -> {
                val msg = "unable to subtract $rhs from $lhs"

                throw IllegalArgumentException(msg)
            }
        }
        return result
    }

    private fun evalMultiply(node: BinaryNode) : Any {
        val lhs = evaluate(node.left)
        val rhs = evaluate(node.right)

        if ((lhs is Complex) or (rhs is Complex)) {
            return toComplex(lhs).multiply(toComplex(rhs))
        }
        if ((lhs is Double) or (rhs is Double)) {
            return toDouble(lhs) * toDouble(rhs)
        }
        if ((lhs is Long) and (rhs is Long)) {
            return (lhs as Long) * (rhs as Long)
        }
        throw IllegalArgumentException("unable to multiply $lhs by $rhs")
    }

    private fun evalDivide(node: BinaryNode) : Any {
        val lhs = evaluate(node.left)
        val rhs = evaluate(node.right)

        if ((lhs is Complex) or (rhs is Complex)) {
            return toComplex(lhs).divide(toComplex(rhs))
        }
        if ((lhs is Double) or (rhs is Double)) {
            return toDouble(lhs) / toDouble(rhs)
        }
        if ((lhs is Long) and (rhs is Long)) {
            return toDouble(lhs) / (rhs as Long)
        }
        throw IllegalArgumentException("unable to divide $lhs by $rhs")
    }

    private fun evalIntegerDivide(node: BinaryNode) : Any {
        val lhs = evaluate(node.left)
        val rhs = evaluate(node.right)

        if ((lhs is Long) and (rhs is Long)) {
            return (lhs as Long) / (rhs as Long)
        }
        throw IllegalArgumentException("unable to integer-divide $lhs by $rhs")
    }

    private fun evalModulo(node: BinaryNode) : Any {
        val lhs = evaluate(node.left)
        val rhs = evaluate(node.right)

        if ((lhs is Long) and (rhs is Long)) {
            return (lhs as Long) % (rhs as Long)
        }
        throw IllegalArgumentException("unable to compute $lhs modulo $rhs")
    }

    private fun evalLeftShift(node: BinaryNode) : Any {
        val lhs = evaluate(node.left)
        val rhs = evaluate(node.right)

        if ((lhs is Long) and (rhs is Long)) {
            return (lhs as Long) shl ((rhs as Long).toInt())
        }
        throw IllegalArgumentException("unable to left-shift $lhs by $rhs")
    }

    private fun evalRightShift(node: BinaryNode) : Any {
        val lhs = evaluate(node.left)
        val rhs = evaluate(node.right)

        if ((lhs is Long) and (rhs is Long)) {
            return (lhs as Long) shr ((rhs as Long).toInt())
        }
        throw IllegalArgumentException("unable to right-shift $lhs by $rhs")
    }

    private fun evalLogicalAnd(node: BinaryNode) : Any {
        val lhs = evaluate(node.left) as Boolean

        if (!lhs) {
            return false
        }

        return evaluate(node.right) as Boolean
    }

    private fun evalLogicalOr(node: BinaryNode) : Any {
        val lhs = evaluate(node.left) as Boolean

        if (lhs) {
            return true
        }

        return evaluate(node.right) as Boolean
    }

    private fun evalBitwiseOr(node : BinaryNode) : Any {
        val lhs = evaluate(node.left)
        val rhs = evaluate(node.right)
        val result : Any

        result = when {
            (lhs is DictWrapper) and (rhs is DictWrapper) -> {
                mergeDictWrappers(lhs as DictWrapper, rhs as DictWrapper)
            }
            (lhs is Long) and (rhs is Long) -> {
                (lhs as Long) or (rhs as Long)
            }
            else -> {
                val msg = "unable to bitwise-or $lhs and $rhs"

                throw IllegalArgumentException(msg)
            }
        }
        return result
    }

    private fun evalBitwiseAnd(node : BinaryNode) : Any {
        val lhs = evaluate(node.left)
        val rhs = evaluate(node.right)
        val result : Any

        if ((lhs is Long) and (rhs is Long)) {
            result = (lhs as Long) and (rhs as Long)
        }
        else {
            val msg = "unable to bitwise-and $lhs and $rhs"

            throw IllegalArgumentException(msg)
        }
        return result
    }

    private fun evalBitwiseXor(node : BinaryNode) : Any {
        val lhs = evaluate(node.left)
        val rhs = evaluate(node.right)
        val result : Any

        if ((lhs is Long) and (rhs is Long)) {
            result = (lhs as Long) xor (rhs as Long)
        }
        else {
            val msg = "unable to bitwise-xor $lhs and $rhs"

            throw IllegalArgumentException(msg)
        }
        return result
    }

    private fun negateNode(node: UnaryNode) : Any {

        return when (val operand = evaluate(node.operand)) {
            is Long -> {
                -operand
            }
            is Double -> {
                -operand
            }
            is Complex -> {
                Complex(-operand.real, -operand.imaginary)
            }
            else -> throw IllegalArgumentException("unable to negate $operand")
        }
    }

    private fun evalPower(node: BinaryNode) : Any {
        val lhs = evaluate(node.left)
        val rhs = evaluate(node.right)

        if ((lhs is Double) or (rhs is Double)) {
            return toDouble(lhs).pow(toDouble(rhs))
        }
        else if ((lhs is Long) and (rhs is Long)) {
            return toDouble(lhs).pow(toDouble(rhs)).toLong()
        }
        throw IllegalArgumentException("unable to raise $lhs to power $rhs")
    }

    fun evaluate(node: ASTNode) : Any {
        val result: Any

        if (node is Token) {
            val value = node.value

            if (node.kind in scalarTokens) {
                result = value!!
            } else if (node.kind == TokenKind.Word) {
                if (value in config.context) {
                    result = config.context[value]!!
                } else {
                    val e = ConfigException("Unknown variable '$value'")
                    e.location = node.start

                    throw e
                }
            } else if (node.kind == TokenKind.BackTick) {
                result = config.convertString(node.value as String)
            }
            else {
                val e = ConfigException("Unable to evaluate $node")

                e.location = node.start
                throw e
            }
        }
        else if (node is MappingNode) {
            result = config.wrapMapping(node)
        }
        else if (node is ListNode) {
            result = config.wrapList(node)
        }
        else when (node.kind) {
            TokenKind.At -> {
                result = evalAt(node as UnaryNode)
            }
            TokenKind.Dollar -> {
                result = evalReference(node as UnaryNode)
            }
            TokenKind.LeftCurly -> {
                result = config.wrapMapping(node as MappingNode)
            }
            TokenKind.Plus -> {
                result = evalAdd(node as BinaryNode)
            }
            TokenKind.Minus -> {
                result = if (node is BinaryNode) {
                    evalSubtract(node)
                }
                else {
                    negateNode(node as UnaryNode)
                }
            }
            TokenKind.Star -> {
                result = evalMultiply(node as BinaryNode)
            }
            TokenKind.Slash -> {
                result = evalDivide(node as BinaryNode)
            }
            TokenKind.SlashSlash -> {
                result = evalIntegerDivide(node as BinaryNode)
            }
            TokenKind.Modulo -> {
                result = evalModulo(node as BinaryNode)
            }
            TokenKind.LeftShift -> {
                result = evalLeftShift(node as BinaryNode)
            }
            TokenKind.RightShift -> {
                result = evalRightShift(node as BinaryNode)
            }
            TokenKind.Power -> {
                result = evalPower(node as BinaryNode)
            }
            TokenKind.And -> {
                result = evalLogicalAnd(node as BinaryNode)
            }
            TokenKind.Or -> {
                result = evalLogicalOr(node as BinaryNode)
            }
            TokenKind.BitwiseOr -> {
                result = evalBitwiseOr(node as BinaryNode)
            }
            TokenKind.BitwiseAnd -> {
                result = evalBitwiseAnd(node as BinaryNode)
            }
            TokenKind.BitwiseXor -> {
                result = evalBitwiseXor(node as BinaryNode)
            }
            else -> {
                val e = ConfigException("Unable to evaluate $node")

                e.location = node.start
                throw e
            }
        }

        return result
    }

    private fun getSlice(container: ListWrapper, slice: SliceNode) : ListWrapper {
        val size = container.size

        val step : Int = if (slice.step === null) {
            1
        }
        else {
            (evaluate(slice.step) as Long).toInt()
        }
        if (step == 0) {
            throw IllegalArgumentException("slice step cannot be zero")
        }

        var startIndex : Int = if (slice.startIndex === null) {
            0
        }
        else {
            var n =(evaluate(slice.startIndex) as Long).toInt()

            if (n < 0) {
                if (n >= -size) {
                    n += size
                }
                else {
                    n = 0
                }
            }
            else if (n >= size) {
                n = size - 1
            }
            n
        }
        var stopIndex : Int = if (slice.stopIndex === null) {
            size - 1
        }
        else {
            var n = (evaluate(slice.stopIndex) as Long).toInt()

            if (n < 0) {
                if (n >= -size) {
                    n += size
                }
                else {
                    n = 0
                }
            }
            if (n > size) {
                n = size
            }
            if (step < 0) {
                n + 1
            }
            else {
                n - 1
            }
        }

        if ((step < 0) && (startIndex < stopIndex)) {
            val tmp = stopIndex

            stopIndex = startIndex
            startIndex = tmp
        }

        val result = ListWrapper(config)

        var i = startIndex

        var notDone = if (step > 0) {
            i <= stopIndex
        }
        else {
            i >= stopIndex
        }
        while (notDone) {
            result.add(container[i])
            i += step
            notDone = if (step > 0) {
                i <= stopIndex
            }
            else {
                i >= stopIndex
            }
        }
        return result
    }

    private fun isRef(node: Any) : Boolean {
        if (node !is ASTNode) {
            return false
        }
        return node.kind == TokenKind.Dollar
    }

    internal fun getFromPath(path: ASTNode) : Any {
        val pi = pathIterator(path).iterator()
        val first = pi.next()
        var result = config.baseGet((first as Token).value as String)

        // We start the evaluation with the current instance, but a path may
        // cross sub-configuration boundaries, and references must always be
        // evaluated in the context of the immediately enclosing configuration,
        // not the top-level configuration (references are relative to the
        // root of the enclosing configuration - otherwise configurations would
        // not be standalone. So whenever we cross a sub-configuration boundary,
        // the current_evaluator has to be pegged to that sub-configuration.

        var currentEvaluator = if (result !is Config) {
            this
        }
        else {
            result.evaluator
        }

        for (x in pi) {
            val t = x as Pair<TokenKind, *>
            val op = t.first
            var operand = t.second
            val sliced = (operand is SliceNode)

            if (operand is Long) {
                operand = operand.toInt()
            }
            else if (!sliced && (op != TokenKind.Dot) && (operand is ASTNode)) {
                operand = currentEvaluator.evaluate(operand)
            }
            if (sliced && (result !is ListWrapper)) {
                throw BadIndexException("slices can only operate on lists")
            }
            if (((result is DictWrapper) || (result is Config)) && (operand !is String)) {
                throw BadIndexException("string required, but found $operand")
            }
            if (result is DictWrapper) {
                val key = operand as String

                if (key in result) {
                    result = result.baseGet(key)
                }
                else {
                    throw ConfigException("Not found in configuration: $key")
                }
            }
            else if (result is Config) {
                currentEvaluator = Evaluator(result)
                result = result.baseGet(operand as String)
            }
            else result = if (result is ListWrapper) {
                val n = result.size

                when {
                    operand is Int -> {
                        if (operand < 0) {
                            if (operand >= -n) {
                                operand += n
                            }
                        }
                        try {
                            result.baseGet(operand)
                        } catch (bi: IndexOutOfBoundsException) {
                            throw BadIndexException("index out of range: is $operand, must be between 0 and ${n - 1}")
                        }
                    }
                    sliced -> {
                        getSlice(result, operand as SliceNode)
                    }
                    else -> {
                        throw BadIndexException("integer required, but found $operand")
                    }
                }
            }
            else {
                // result is not a Config, DictWrapper or ListWrapper. Just throw a generic "not in configuration" error
                val p = toSource(path)
                val ce = ConfigException("Not found in configuration: $p")

                throw ce
            }
            if (isRef(result)) {
                if (result in currentEvaluator.refsSeen) {
                    val parts = arrayListOf<String>()

                    for (item in currentEvaluator.refsSeen) {
                        val s = toSource((item as UnaryNode).operand)
                        val ls = item.start.toString()

                        parts.add("$s $ls")
                    }
                    parts.sort()
                    val ps = parts.joinToString { it }
                    val msg = "Circular reference: $ps"
                    throw CircularReferenceException(msg)
                }
                currentEvaluator.refsSeen.add(result as ASTNode)
            }
            if (result is MappingNode) {
                result = config.wrapMapping(result)
            }
            else if (result is ListNode) {
                result = config.wrapList(result)
            }
            if (result is ASTNode) {
                val e = currentEvaluator.evaluate(result)

                if (e !== result) {
                    // TODO put back in container to prevent repeated evaluations
                    result = e
                }
            }
        }
        refsSeen.clear()
        return result
    }
}

internal val MISSING = Any()

// We can't use Kotlin's regex here, as it doesn't seem to support Unicode character classes
internal val IDENTIFIER_PATTERN = Pattern.compile("""^(?!\d)(\w+)$""", Pattern.UNICODE_CHARACTER_CLASS)

internal fun isIdentifier(s: String) : Boolean {
    return IDENTIFIER_PATTERN.matcher(s).matches()
}

internal fun parsePath(s : String): ASTNode {
    val result: ASTNode

    try {
        val parser = Parser(StringReader(s))

        if (parser.next.kind != TokenKind.Word) {
            throw InvalidPathException("Invalid path: $s")
        }

        result = parser.primary()
        if (!parser.atEnd) {
            throw InvalidPathException("Invalid path: $s")
        }
    }
    catch (e: RecognizerException) {
        throw InvalidPathException("Invalid path: $s", e)
    }
    return result
}

internal fun pathIterator(start: ASTNode) : Sequence<Any> = sequence {
    fun visit(node: ASTNode) : Sequence<Any> = sequence {
        when (node) {
            is Token -> {
                yield(node)
            }
            is UnaryNode -> {
                visit(node.operand).forEach {
                    yield(it)
                }
            }
            is BinaryNode -> {
                visit(node.left).forEach {
                    yield(it)
                }
                when (node.kind) {
                    TokenKind.Dot -> {
                        yield(Pair(node.kind, (node.right as Token).value))
                    }
                    TokenKind.Colon -> {
                        // it's a slice
                        yield(Pair(node.kind, node.right as SliceNode))
                    }
                    else -> {
                        // it's a list access
                        yield(Pair(node.kind, (node.right as Token).value))
                    }
                }
            }
        }
    }

    visit(start).forEach {
        yield(it)
    }
}

class Config() {    // parens => no-arg primary constructor
    var noDuplicates: Boolean = true
    var strictConversions: Boolean = true
    var context: Map<String, Any> = hashMapOf()
    var includePath: ArrayList<String> = arrayListOf()
    var path: String? = null
    var rootDir: String? = null
    var stringConverter: StringConverter = defaultStringConverter
    internal var evaluator = Evaluator(this)

    internal var cache: HashMap<String, Any>? = null
    internal var data: DictWrapper? = null
    internal var parent: Config? = null

    constructor (path: String) : this() {
        loadFile(path)
    }

    constructor(reader: Reader) : this() {
        load(reader)
    }

    var cached : Boolean
        get() = (cache != null)
        set(value) {
            if (value) {
                if (cache == null) {
                    cache = hashMapOf()
                }
            }
            else {
                cache = null
            }
        }

    internal fun wrapList(ln: ListNode) : ListWrapper {
        val result = ListWrapper(this)

        result.addAll(ln.elements)
        return result
    }

    internal fun wrapMapping(mn: MappingNode) : DictWrapper {
        val result = DictWrapper(this)
        var seen : HashMap<String, Location>?  = null

        if (noDuplicates) {
            seen = HashMap()
        }
        mn.elements.forEach {
            val t = it.first as Token
            val k = t.value as String
            val v = it.second

            if (seen != null) {  // meaning noDuplicates allowed ...
                if (k in seen) {
                    val e = ConfigException("Duplicate key $k seen at ${t.start} (previously at ${seen[k]})")

                    throw e
                }
                seen[k] = t.start
            }
            result[k] = v
        }
        return result
    }

    internal fun setPath(p: String) {
        path = p
        val f = File(p)
        rootDir = f.absoluteFile.parent
    }

    fun load(r: Reader) {
        val parser = Parser(r)
        val node = parser.container()

        if (node !is MappingNode) {
            val e = ConfigException("Root configuration must be a mapping")

            e.location = node.start
            throw e
        }
        if (r is NamedReader) {
            setPath(r.name)
        }
        data = wrapMapping(node)
        // the cache needs clearing when loading new stuff in
        cache?.clear()
    }

    fun loadFile(path : String) {
        load(getReader(path))
    }

    internal fun getFromPath(path: String) : Any {
        evaluator.refsSeen.clear()
        return evaluator.getFromPath(parsePath(path))
    }

    fun convertString(s : String) : Any {
        val result = stringConverter(s, this)

        if (strictConversions && (result === s)) {
            throw ConfigException("Unable to convert string $s")
        }
        return result
    }

    internal fun evaluated(value: Any, ev: Evaluator? = null) : Any {
        var result = value

        if (value is ASTNode) {
            val e = ev ?: evaluator
            result = e.evaluate(value)
        }
        return result
    }

    internal fun baseGet(key: String, default: Any = MISSING) : Any {
        var result : Any

        if ((cache != null) && cache!!.contains(key)) {
            result = cache!![key]!!
        }
        else if (data == null) {
            val e = ConfigException("No data in configuration")

            throw e
        }
        else {
            if (key in data!!) {
                result = evaluated(data!![key])
            }
            else if (isIdentifier(key)) {
                if (default === MISSING) {
                    val e = ConfigException("Not found in configuration: $key")

                    throw e
                }
                result = default
            }
            else {
                // not an identifier. Treat as a path
                try {
                    result = getFromPath(key)
                } catch (ipe: InvalidPathException) {
                    throw ipe
                } catch (bie: BadIndexException) {
                    throw bie
                } catch (cre: CircularReferenceException) {
                    throw cre
                } catch (ce : ConfigException) {
                    if (default === MISSING) {
                        //val e = ConfigException("Not found in configuration: $key")

                        //throw e
                        throw ce
                    }
                    result = default
                }
            }
            // If a user specified a cache, use it
            if (cache != null) {
                cache!![key] = result
            }
        }
        return result
    }

    fun get(key: String, default: Any) : Any {
        return unwrap(baseGet(key, default))
    }

    operator fun get(key: String) : Any {
        return unwrap(baseGet(key, MISSING))
    }


    fun asDict() : HashMap<String, Any> {
        return data!!.asDict()
    }
}
