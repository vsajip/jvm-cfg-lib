//
//  Copyright (C) 2018-2021 Red Dove Consultants Limited
//
package com.reddove.config.test

import java.io.*
import java.nio.file.Paths
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

import kotlin.collections.HashMap
import kotlin.test.assertFailsWith

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.fail

import org.junit.jupiter.api.Test

import com.reddove.config.*
import org.apache.commons.math3.complex.Complex
import java.time.ZoneOffset

// general utility functions

val SEPARATOR_PATTERN = Regex("""^-- ([A-Z]\d+) -+""")

private fun loadData(fn: String): SortedMap<String, String> {
    val result = HashMap<String, String>()
    val reader = BufferedReader(getReader(fn))
    val lines = reader.readLines()
    var key: String? = null
    val value = ArrayList<String>()

    reader.close()

    for (line in lines) {
        val m = SEPARATOR_PATTERN.find(line)?.groupValues?.get(1)

        if (m == null) {
            value.add(line)
        }
        else {
            if ((key != null) && (value.size > 0)) {
                result[key] = value.joinToString(separator = "\n")
            }
            key = m
            value.clear()
        }
    }
    return result.toSortedMap()
}

fun dataFileDir(fn : String): File {
    return File(Paths.get(System.getProperty("user.dir"), "src", "test", "resources", fn).toString())
}

fun dataFilePath(fn: String): String {
    val path = Paths.get(System.getProperty("user.dir"), "src", "test", "resources", fn)

    return path.toString()
}

private fun makeTokenizer(s: String): Tokenizer {
    return Tokenizer(StringReader(s))
}

private fun isDict(o: Any): Boolean {
    return o is HashMap<*, *>
}

private fun <T> toList(i: Iterable<T>): List<T> {
    val result = ArrayList<T>()
    val iter = i.iterator()

    while (iter.hasNext()) {
        result.add(iter.next())
    }
    return result
}

private fun toMap(pairs : ArrayList<Pair<ASTNode, ASTNode>>) : HashMap<String, ASTNode> {
    val result = HashMap<String, ASTNode>()

    for ((k, v) in pairs) {
        val key = (k as Token).value as String

        result[key] = v
    }
    return result
}

private fun assertIn(ss: String, s: String) {
    val msg = "'$ss' not found in '$s'"
    assertTrue(s.indexOf(ss) >= 0, msg)
}

private fun assertIn(ss: String, e : Exception) {
    val msg = "'$ss' not found in $e"
    assertTrue(e.message!!.indexOf(ss) >= 0, msg)
}

private fun compareObjects(e: Any?, a: Any?, context: Any? = null) {
    if ((e == null) && (a == null)) {
        return
    }

    var equal = ((e != null) && (a != null))
    var t: Class<Any>? = null

    if (equal) { // both must be non-null
        t = e!!.javaClass
        equal = (t == a!!.javaClass)
    }
    if (equal) { // both must be of the same type
        if (t!!.isArray) {
            compareArrays(e as Array<Any?>, a as Array<Any?>)
        }
        else if (isDict(e!!) && isDict(a!!)) {
            compareDictionaries(e as HashMap<String, Any>, a as HashMap<String, Any>)
        }
        else if (e is ArrayList<*>) {
            val ea = e.toArray()
            val aa = (a as ArrayList<*>).toArray()
            compareArrays(ea as Array<Any?>, aa as Array<Any?>)
        }
        else if (e is UnaryNode) {
            val an = a as UnaryNode

            assertEquals(e.kind, an.kind)
            compareObjects(e.operand, an.operand)
        }
        else if (e is BinaryNode) {
            val an = a as BinaryNode

            assertEquals(e.kind, an.kind)
            compareObjects(e.left, an.left)
            compareObjects(e.right, an.right)
        }
        else if (e is SliceNode) {
            val an = a as SliceNode

            assertEquals(e.kind, a.kind)
            compareObjects(e.startIndex, an.startIndex)
            compareObjects(e.stopIndex, an.stopIndex)
            compareObjects(e.step, an.step)
        }
        else if (e is Token) {
            val at = a as Token

            assertEquals(e.kind, at.kind)
            assertEquals(e.text, at.text)
            assertEquals(e.value, at.value)
        }
        else if (e is Pair<*, *>) {
            val ap = a as Pair<*, *>

            compareObjects(e.first, ap.first)
            compareObjects(e.second, ap.second)
        }
        else if (e is Triple<*, *, *>) {
            val at = a as Triple<*, *, *>

            compareObjects(e.first, at.first)
            compareObjects(e.second, at.second)
            compareObjects(e.third, at.third)
        }
        else {
            equal = e == a
        }
    }
    if (!equal) {
        val ctx = if (context == null) "" else " ($context)"
        val msg = "Failed$ctx: expected $e, actual $a"

        fail(msg)
    }
}

private fun compareArrays(expected: Array<Any?>, actual: Array<Any?>) {
    assertEquals(expected.size, actual.size, "sizes differ: expected ${expected.size}, was ${actual.size}")
    for (i in actual.indices) {
        compareObjects(expected[i], actual[i], i)
    }
}

private fun compareDictionaries(expected: HashMap<String, Any>, actual: HashMap<String, Any>) {
    assertEquals(expected.size, actual.size)
    for ((k, v1) in actual) {

        assertTrue(expected.containsKey(k), "key not found: $k")

        val v2 = expected[k]

        compareObjects(v2, v1, k)
    }
}

private fun makeTokenTextsWithEOF(source: String): Array<String> {
    val result = ArrayList<String>()

    result.addAll(source.split(' '))
    result.add("")
    return result.toTypedArray()
}

private fun compareLocations(expected: Location, actual: Location) {
    assertEquals(expected.line, actual.line, "lines differ: expected ${expected.line}, actual ${actual.line}")
    assertEquals(expected.column, actual.column, "columns differ: expected ${expected.column}, actual ${actual.column}")
}

/*
private fun compareTokens(expected: Token, actual: Token) {
    assertEquals(expected.kind, actual.kind, "kinds differ: expected ${expected.kind}, actual ${actual.kind}")
    assertEquals(expected.text, actual.text, "texts differ: expected ${expected.text}, actual ${actual.text}")
    assertEquals(expected.value, actual.value, "values differ: expected ${expected.value}, actual ${actual.value}")
    assertNotNull(actual.start)
    assertNotNull(actual.end)
    compareLocations(expected.start, actual.start)
    compareLocations(expected.end, actual.end)
}
*/

data class Quadruple<out A, out B, out C, out D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
) : Serializable {

    override fun toString(): String = "($first, $second, $third, $fourth)"
}

class TokenizerTest {
    @Test
    fun tokens() {
        var tokenizer = makeTokenizer("")

        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)
        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)

        tokenizer = makeTokenizer("# a comment\n")
        var token = tokenizer.getToken()
        assertEquals(TokenKind.Newline, token.kind)
        assertEquals(token.text, "# a comment")
        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)

        tokenizer = makeTokenizer("foo")
        token = tokenizer.getToken()
        assertEquals(TokenKind.Word, token.kind)
        assertEquals("foo", token.text)
        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)

        tokenizer = makeTokenizer("'foo'")
        token = tokenizer.getToken()
        assertEquals(TokenKind.String, token.kind)
        assertEquals("'foo'", token.text)
        assertEquals("foo", token.value)
        compareLocations(Location(1, 1), token.start)
        compareLocations(Location(1, 5), token.end)
        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)

        tokenizer = makeTokenizer("2.71828")
        token = tokenizer.getToken()
        assertEquals(TokenKind.Float, token.kind)
        assertEquals("2.71828", token.text)
        assertEquals(2.71828, token.value)
        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)

        tokenizer = makeTokenizer(".5")
        token = tokenizer.getToken()
        assertEquals(TokenKind.Float, token.kind)
        assertEquals(".5", token.text)
        assertEquals(0.5, token.value)
        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)

        tokenizer = makeTokenizer("-.5")
        token = tokenizer.getToken()
        assertEquals(TokenKind.Float, token.kind)
        assertEquals("-.5", token.text)
        assertEquals(-0.5, token.value)
        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)

        tokenizer = makeTokenizer("0x123aBc")
        token = tokenizer.getToken()
        assertEquals(TokenKind.Integer, token.kind)
        assertEquals("0x123aBc", token.text)
        assertEquals(0x123abcL, token.value)
        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)

        tokenizer = makeTokenizer("0o123")
        token = tokenizer.getToken()
        assertEquals(TokenKind.Integer, token.kind)
        assertEquals("0o123", token.text)
        assertEquals(83L, token.value)
        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)

        tokenizer = makeTokenizer("0123")
        token = tokenizer.getToken()
        assertEquals(TokenKind.Integer, token.kind)
        assertEquals("0123", token.text)
        assertEquals(83L, token.value)
        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)

        tokenizer = makeTokenizer("0b0001_0110_0111")
        token = tokenizer.getToken()
        assertEquals(TokenKind.Integer, token.kind)
        assertEquals("0b0001_0110_0111", token.text)
        assertEquals(0x167L, token.value)
        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)

        tokenizer = makeTokenizer("1e8")
        token = tokenizer.getToken()
        assertEquals(TokenKind.Float, token.kind)
        assertEquals("1e8", token.text)
        assertEquals(1e8, token.value)
        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)

        tokenizer = makeTokenizer("1e-8")
        token = tokenizer.getToken()
        assertEquals(TokenKind.Float, token.kind)
        assertEquals("1e-8", token.text)
        assertEquals(1e-8, token.value)
        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)

        tokenizer = makeTokenizer("-4")
        token = tokenizer.getToken()
        assertEquals(TokenKind.Integer, token.kind)
        assertEquals("-4", token.text)
        assertEquals(-4L, token.value)
        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)

        tokenizer = makeTokenizer("-4e8")
        token = tokenizer.getToken()
        assertEquals(TokenKind.Float, token.kind)
        assertEquals("-4e8", token.text)
        assertEquals(-4e8, token.value)
        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)

        val empties = arrayOf(
            Triple("\"\"", 1, 2),
            Triple("''", 1, 2),
            Triple("''''''", 1, 6),
            Triple("\"\"\"\"\"\"", 1, 6)
        )
        for (empty in empties) {
            tokenizer = makeTokenizer(empty.first)
            token = tokenizer.getToken()
            assertEquals(TokenKind.String, token.kind)
            assertEquals(empty.first, token.text)
            assertEquals("", token.value)
            val end = token.end
            assertEquals(empty.second, end.line)
            assertEquals(empty.third, end.column)
            assertEquals(TokenKind.EOF, tokenizer.getToken().kind)
        }
        var s = "\"\"\"abc\ndef\n\"\"\""
        tokenizer = makeTokenizer(s)
        token = tokenizer.getToken()
        assertEquals(TokenKind.String, token.kind)
        assertEquals(s, token.text)
        assertEquals("abc\ndef\n", token.value)
        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)

        s = """'\n'"""
        tokenizer = makeTokenizer(s)
        token = tokenizer.getToken()
        assertEquals(TokenKind.String, token.kind)
        assertEquals(s, token.text)
        assertEquals("\n", token.value)
        assertEquals(TokenKind.EOF, tokenizer.getToken().kind)

        tokenizer = makeTokenizer("9+4j+a*b")
        var tokens = toList(tokenizer)
        var kinds = tokens.map { it.kind }.toTypedArray()
        var expectedKinds = arrayOf(
            TokenKind.Integer, TokenKind.Plus, TokenKind.Complex,
            TokenKind.Plus, TokenKind.Word, TokenKind.Star,
            TokenKind.Word, TokenKind.EOF)
        compareArrays(expectedKinds as Array<Any?>, kinds as Array<Any?>)
        var texts = tokens.map { it.text }.toTypedArray()
        var expectedTexts = arrayOf("9", "+", "4j", "+",
            "a", "*", "b", "")
        compareArrays(expectedTexts as Array<Any?>, texts as Array<Any?>)

        val punct = "< > { } [ ] ( ) + - * / ** // % . <= <> << >= >> == != , : @ ~ & | ^ $ && ||"

        tokenizer = makeTokenizer(punct)
        tokens = toList(tokenizer)
        kinds = tokens.map { it.kind }.toTypedArray()
        texts = tokens.map { it.text }.toTypedArray()
        expectedKinds = arrayOf(
            TokenKind.LessThan, TokenKind.GreaterThan, TokenKind.LeftCurly, TokenKind.RightCurly,
            TokenKind.LeftBracket, TokenKind.RightBracket, TokenKind.LeftParenthesis, TokenKind.RightParenthesis,
            TokenKind.Plus, TokenKind.Minus, TokenKind.Star, TokenKind.Slash, TokenKind.Power, TokenKind.SlashSlash,
            TokenKind.Modulo, TokenKind.Dot, TokenKind.LessThanOrEqual, TokenKind.AltUnequal, TokenKind.LeftShift,
            TokenKind.GreaterThanOrEqual, TokenKind.RightShift, TokenKind.Equal, TokenKind.Unequal, TokenKind.Comma,
            TokenKind.Colon, TokenKind.At, TokenKind.BitwiseComplement, TokenKind.BitwiseAnd, TokenKind.BitwiseOr,
            TokenKind.BitwiseXor, TokenKind.Dollar, TokenKind.And, TokenKind.Or, TokenKind.EOF)
        compareArrays(expectedKinds as Array<Any?>, kinds as Array<Any?>)
        expectedTexts = makeTokenTextsWithEOF(punct)
        compareArrays(expectedTexts as Array<Any?>, texts as Array<Any?>)

        //keywords
        val keywords = "true false null is in not and or"
        tokenizer = makeTokenizer(keywords)
        tokens = toList(tokenizer)
        kinds = tokens.map { it.kind }.toTypedArray()
        texts = tokens.map { it.text }.toTypedArray()
        expectedKinds = arrayOf(
            TokenKind.True, TokenKind.False, TokenKind.None, TokenKind.Is,
            TokenKind.In, TokenKind.Not, TokenKind.And, TokenKind.Or,
            TokenKind.EOF)
        compareArrays(expectedKinds as Array<Any?>, kinds as Array<Any?>)
        expectedTexts = makeTokenTextsWithEOF(keywords)
        compareArrays(expectedTexts as Array<Any?>, texts as Array<Any?>)

        // various newlines
        tokenizer = makeTokenizer("\n \r \r\n")
        tokens = toList(tokenizer)
        kinds = tokens.map { it.kind }.toTypedArray()
        expectedKinds = arrayOf(TokenKind.Newline, TokenKind.Newline,
            TokenKind.Newline, TokenKind.EOF)
        compareArrays(expectedKinds as Array<Any?>, kinds as Array<Any?>)

        // values
        assertEquals(false, makeTokenizer("false").getToken().value)
        assertEquals(true, makeTokenizer("true").getToken().value)
        assertEquals(NullValue, makeTokenizer("null").getToken().value)
    }

    @Test
    fun data() {
        val path = dataFilePath("testdata.txt")

        val cases = loadData(path)
        val expected = mapOf("C25" to arrayOf(Token(TokenKind.Word, "unicode", "unicode"),
            Token(TokenKind.Assign, "=", null),
            Token(TokenKind.String, "'Grüß Gott'", "Grüß Gott"),
            Token(TokenKind.Newline, "\n", null),
            Token(TokenKind.Word, "more_unicode", "more_unicode"),
            Token(TokenKind.Colon, ":", null),
            Token(TokenKind.String, "'Øresund'", "Øresund"),
            Token(TokenKind.EOF, "", null)))

        for ((k, s) in cases) {
            val tokenizer = makeTokenizer(s)
            val tokens = toList(tokenizer).toTypedArray()

            if (k in expected) {
                compareArrays(expected[k] as Array<Any?>, tokens as Array<Any?>)
            }
        }
    }

    @Test
    fun locations() {
        var path = dataFilePath("pos.forms.cfg.txt")
        val expected = ArrayList<Pair<Location, Location>>()

        File(path).readLines().forEach{
            val numbers = it.split(' ').map{ iit ->
                iit.toInt()
            }
            expected.add(Pair(Location(numbers[0], numbers[1]), Location(numbers[2], numbers[3])))
        }

        path = dataFilePath("forms.cfg")
        var reader = getReader(path)
        val tokenizer = Tokenizer(reader)
        val tokens = toList(tokenizer)

        assertEquals(expected.size, tokens.size)

        for ((i, t) in tokens.withIndex()) {
            val e = expected[i]

            compareLocations(e.first, t.start)
            compareLocations(e.second, t.end)
        }

        reader = getReader(path)

        val p = Parser(reader)

        val d = toMap(p.mappingBody().elements)

        assertNotNull(d)
        assertTrue(p.atEnd)
        assertTrue("refs" in d)
        assertTrue("fieldsets" in d)
        assertTrue("forms" in d)
        assertTrue("modals" in d)
        assertTrue("pages" in d)
    }

    @Test
    fun badTokens() {
        val badNumbers = arrayOf(
            Quadruple("9a", "Invalid character in number", 1, 2),
            Quadruple("079", "Invalid character in number", 1, 1),
            Quadruple("0xaBcz", "Invalid character in number", 1, 6),
            Quadruple("0o79", "Invalid character in number", 1, 4),
            Quadruple(".5z", "Invalid character in number", 1, 3),
            Quadruple("0.5.7", "Invalid character in number", 1, 4),
            Quadruple(" 0.4e-z", "Invalid character in number", 1, 7),
            Quadruple(" 0.4e-8.3", "Invalid character in number", 1, 8),
            Quadruple(" 089z", "Invalid character in number", 1, 5),
            Quadruple("0o89z", "Invalid character in number", 1, 3),
            Quadruple("0X89g", "Invalid character in number", 1, 5),
            Quadruple("10z", "Invalid character in number", 1, 3),
            Quadruple(" 0.4e-8Z", "Invalid character in number: Z", 1, 8),
            Quadruple("123_", "Invalid '_' at end of number: 123_", 1, 4),
            Quadruple("1__23", "Invalid '_' in number: 1__", 1, 3),
            Quadruple("1_2__3", "Invalid '_' in number: 1_2__", 1, 5),
            Quadruple(" 0.4e-8_", "Invalid '_' at end of number: 0.4e-8_", 1, 8),
            Quadruple(" 0.4_e-8", "Invalid '_' at end of number: 0.4_", 1, 5),
            Quadruple(" 0._4e-8", "Invalid '_' in number: 0._", 1, 4),
            Quadruple("\\ ", "Unexpected character: \\", 1, 2)
        )

        for (bn in badNumbers) {
            val tokenizer = makeTokenizer(bn.first)

            val e = assertThrows(RecognizerException::class.java) {
                tokenizer.getToken()
            }
            assertIn(bn.second, e)
            val loc = e.location!!
            assertEquals(bn.third, loc.line)
            assertEquals(bn.fourth, loc.column)
        }

        val badStrings = arrayOf(
            Quadruple("\'", "Unterminated quoted string:",1, 1),
            Quadruple("\"", "Unterminated quoted string:",1, 1),
            Quadruple("\'\'\'", "Unterminated quoted string:", 1, 1),
            Quadruple("  ;", "Unexpected character: ", 1, 3),
            Quadruple("\"abc", "Unterminated quoted string: ", 1, 1),
            Quadruple("\"abc\\\ndef", "Unterminated quoted string: ", 1, 1)
        )

        for (bs in badStrings) {
            val tokenizer = makeTokenizer(bs.first)

            val e = assertThrows(RecognizerException::class.java) {
                tokenizer.getToken()
            }
            assertIn(bs.second, e)

            val loc = e.location!!
            assertEquals(bs.third, loc.line)
            assertEquals(bs.fourth, loc.column)
        }
    }

    @Test
    fun escapes() {
        val cases = arrayOf(
            Pair("'\\a'", "\u0007"),
            Pair("'\\b'", "\b"),
            Pair("'\\f'", "\u000C"),
            Pair("'\\n'", "\n"),
            Pair("'\\r'", "\r"),
            Pair("'\\t'", "\t"),
            Pair("'\\v'", "\u000B"),
            Pair("'\\\\'", "\\"),
            Pair("'\\''", "'"),
            Pair("'\\\"'", "\""),
            Pair("'\\xAB'", "\u00AB"),
            Pair("'\\u2803'", "\u2803"),
            Pair("'\\u28A0abc\\u28A0'", "\u28a0abc\u28a0"),
            Pair("'\\u28A0abc'", "\u28a0abc"),
            Pair("'\\uE000'", "\ue000"),
            // Note: Under Kotlin, 32-bit Unicode is supported via surrogate pairs
            Pair("'\\U0010ffff'", "\udbff\udfff")
        )

        for (c in cases) {
            val tokenizer = makeTokenizer(c.first)
            val t = tokenizer.getToken()

            assertEquals(c.second, t.value)
        }

        val badCases = arrayOf(
            "'\\z'",
            "'\\x'",
            "'\\xa'",
            "'\\xaz'",
            "'\\u'",
            "'\\u0'",
            "'\\u01'",
            "'\\u012'",
            "'\\u012z'",
            "'\\u012zA'",
            "'\\ud800'",
            "'\\udfff'",
            "'\\U00110000'"
        )

        for (s in badCases) {
            val tokenizer = makeTokenizer(s)
            val e = assertThrows(RecognizerException::class.java) {
                tokenizer.getToken()
            }
            assertIn("Invalid escape sequence", e.message!!)
        }
    }

    @Test
    fun representations() {
        val t = Token(TokenKind.Word, "foo", "foo")
        val l = Location(3, 14)

        assertEquals("Token(Word:foo:foo)", t.toString())
        assertEquals("(3, 14)", l.toString())
    }
}

class ParserTest {

    @Test
    fun representations() {
        val lhs = Token(TokenKind.Word, "foo", "foo")
        val rhs = Token(TokenKind.Word, "bar", "bar")
        val opd = Token(TokenKind.Integer, "4", 4L)
        val b = BinaryNode(TokenKind.Plus, lhs, rhs)
        val u = UnaryNode(TokenKind.Minus, opd)

        assertEquals("UnaryNode(Minus, Token(Integer:4:4))", u.toString())
        assertEquals("BinaryNode(Plus, Token(Word:foo:foo), Token(Word:bar:bar))", b.toString())
    }

    @Test
    fun tokenValues() {
        val source = "a + 4"
        var p = makeParser(source)
        var node = p.expr() as BinaryNode

        assertNotNull(node)
        assertEquals(TokenKind.Plus, node.kind)
        assertEquals(TokenKind.Word, node.left.kind)
        assertEquals(TokenKind.Integer, node.right.kind)
        assertEquals("a", (node.left as Token).value!!)
        assertEquals(4L, (node.right as Token).value!!)
        p = makeParser(source)
        node = p.expr() as BinaryNode
        assertNotNull(node)
        assertEquals(TokenKind.Plus, node.kind)
        var t = node.left as Token
        assertNotNull(t)
        assertEquals(TokenKind.Word, t.kind)
        assertEquals("a", t.value)
        t = node.right as Token
        assertNotNull(t)
        assertEquals(TokenKind.Integer, t.kind)
        assertEquals(4L, t.value)
    }

    @Test
    fun fragments() {
        var o = parse("foo", "expr")

        assertEquals("foo", (o as Token).value!!)
        o = makeParser("0.5").expr()
        assertEquals(0.5, (o as Token).value!!)
        o = makeParser("'foo' \"bar\"").expr()
        assertEquals("foobar", (o as Token).value!!)
        o = makeParser("a.b").expr()
        assertEquals(TokenKind.Dot, o.kind)
        assertEquals("a", ((o as BinaryNode).left as Token).value!!)
        assertEquals("b", (o.right as Token).value!!)
        o = makeParser("a.b.c.d").expr()
        val dot = TokenKind.Dot
        assertEquals(dot, o.kind)
        assertEquals("d", ((o as BinaryNode).right as Token).value!!)
        val abc = o.left as BinaryNode
        assertNotNull(abc)
        assertEquals(dot, abc.kind)
        assertEquals("c", (abc.right as Token).value!!)
        val ab = abc.left as BinaryNode
        assertNotNull(ab)
        assertEquals(dot, ab.kind)
        assertEquals("a", (ab.left as Token).value!!)
        assertEquals("b", (ab.right as Token).value!!)
    }

    private fun expressions(ops: Array<String>, rule: String, multiple: Boolean = true) {
        for (op in ops) {
            val s = "foo${op}bar"
            val kind = makeTokenizer(op).getToken().kind
            val o = parse(s, rule) as BinaryNode

            assertNotNull(o)
            assertEquals(kind, o.kind)
            assertEquals("foo", (o.left as Token).value!!)
            assertEquals("bar", (o.right as Token).value!!)
        }
        if (multiple) {
            val r = SecureRandom()

            for (i in 0..9999) {
                val op1 = ops[r.nextInt(ops.size)]
                val op2 = ops[r.nextInt(ops.size)]
                val k1 = makeTokenizer(op1).getToken().kind
                val k2 = makeTokenizer(op2).getToken().kind
                val s = "foo${op1}bar${op2}baz"
                val o = parse(s, rule) as BinaryNode

                assertNotNull(o)
                assertEquals(k2, o.kind)
                assertEquals("baz", (o.right as Token).value!!)
                assertEquals(k1, o.left.kind)
                assertEquals("foo", ((o.left as BinaryNode).left as Token).value!!)
                assertEquals("bar", (o.left.right as Token).value!!)
            }
        }
    }

    @Test
    fun unaries() {
        val ops = arrayOf("+", "-", "~", "not ", "@")

        for (op in ops) {
            val s = "${op}foo"
            val kind = makeTokenizer(op).getToken().kind

            val o = parse(s, "expr") as UnaryNode

            assertNotNull(o)
            assertEquals(kind, o.kind)
            assertEquals("foo", (o.operand as Token).value!!)
        }
    }

    @Test
    fun binaries() {
        expressions(arrayOf("*", "/", "//", "%"), "mulExpr")
        expressions(arrayOf("+", "-"), "addExpr")
        expressions(arrayOf("<<", ">>"), "shiftExpr")
        expressions(arrayOf("&"), "bitandExpr")
        expressions(arrayOf("^"), "bitxorExpr")
        expressions(arrayOf("|"), "bitorExpr")
        expressions(arrayOf("**"), "power", false)
        var o = parse("foo**bar**baz", "power") as BinaryNode
        assertNotNull(o)
        val k = TokenKind.Power
        assertEquals(k, o.kind)
        assertEquals(TokenKind.Word, o.left.kind)
        assertEquals("foo", (o.left as Token).value!!)
        assertEquals(k, o.right.kind)
        assertEquals("bar", ((o.right as BinaryNode).left as Token).value!!)
        assertEquals("baz", ((o.right as BinaryNode).right as Token).value!!)

        o = parse("foo is not bar", "comparison") as BinaryNode
        assertNotNull(o)
        assertEquals(TokenKind.IsNot, o.kind)
        assertEquals("foo", (o.left as Token).value!!)
        assertEquals("bar", (o.right as Token).value!!)

        o = parse("foo not in bar", "comparison") as BinaryNode
        assertNotNull(o)
        assertEquals(TokenKind.NotIn, o.kind)
        assertEquals("foo", (o.left as Token).value!!)
        assertEquals("bar", (o.right as Token).value!!)

        expressions(arrayOf("<=", "<>", "<", ">=", ">", "==", "!=", " in ", " is "), "comparison", false)
        expressions(arrayOf(" and ", "&&"), "andExpr")
        expressions(arrayOf(" or ", "||"), "expr")
    }

    @Test
    fun atoms() {
        for (s in arrayOf("[1, 2, 3]", "[1, 2, 3,]")) {
            val o = parse(s, "atom") as ListNode

            assertNotNull(o)
            for ((i, t) in o.elements.withIndex()) {
                assertEquals((i + 1).toLong(), (t as Token).value!!)
            }
        }
    }

    @Test
    fun data() {
        val path = dataFilePath("testdata.txt")

        val cases = loadData(path)

        val expectedMessages = mapOf(
            "D01" to "Unexpected type for key: Integer",
            "D02" to "Unexpected type for key: LeftBracket",
            "D03" to "Unexpected type for key: LeftCurly"
        )

        for ((k, s) in cases) {
            val p = makeParser(s)

            if (k < "D01") {
                val o = p.mappingBody()

                assertNotNull(o)
                assertTrue(p.atEnd)
            }
            else {
                val e = assertThrows(RecognizerException::class.java) {
                    p.mappingBody()
                }
                if (k in expectedMessages) {
                    assertEquals(expectedMessages[k], e.message)
                }
            }
        }
    }

    @Test
    fun json() {
        val path = dataFilePath("forms.conf")
        val reader = getReader(path)
        val parser = Parser(reader)
        val d = toMap(parser.mapping().elements)

        assertNotNull(d)
        assertTrue("refs" in d)
        assertTrue("fieldsets" in d)
        assertTrue("forms" in d)
        assertTrue("modals" in d)
        assertTrue("pages" in d)
    }

    @Test
    fun unexpected() {
        var e = assertThrows(RecognizerException::class.java) {
            parse("{foo", "mapping")
        }
        assertEquals("Expected key-value separator, found: EOF", e.message)
        compareLocations(Location(1, 5), e.location!!)

        e = assertThrows(RecognizerException::class.java) {
            parse("   :", "value")
        }
        assertEquals("Unexpected when looking for value: Colon", e.message)
        compareLocations(Location(1, 4), e.location!!)

        e = assertThrows(RecognizerException::class.java) {
            parse("   :", "atom")
        }
        assertEquals("Unexpected: Colon", e.message)
        compareLocations(Location(1, 4), e.location!!)
    }

    @Test
    fun files() {
        dataFileDir("derived").listFiles().forEach {
            val parser = Parser(getReader(it.toString()))

            //println(it)
            parser.container()
        }
    }

    private fun W(s : String) : Token {
        return Token(TokenKind.Word, s, s)
    }

    @Test
    fun slices() {
        var node = makeParser("foo[start:stop:step]").expr()

        var expected = BinaryNode(TokenKind.Colon,
                                  W("foo"),
                                  SliceNode(W("start"), W("stop"), W("step")))
        compareObjects(node, expected)

        node = makeParser("foo[start:stop]").expr()
        expected = BinaryNode(TokenKind.Colon,
                              W("foo"),
                              SliceNode(W("start"), W("stop"), null))
        compareObjects(node, expected)

        node = makeParser("foo[start:stop:]").expr()
        compareObjects(node, expected)

        node = makeParser("foo[start:]").expr()
        expected = BinaryNode(TokenKind.Colon,
                W("foo"),
                SliceNode(W("start"), null, null))
        compareObjects(node, expected)
        node = makeParser("foo[start::]").expr()
        compareObjects(node, expected)

        node = makeParser("foo[:stop]").expr()
        expected = BinaryNode(TokenKind.Colon,
                W("foo"),
                SliceNode(null, W("stop"), null))
        compareObjects(node, expected)
        node = makeParser("foo[:stop:]").expr()
        compareObjects(node, expected)

        node = makeParser("foo[::step]").expr()
        expected = BinaryNode(TokenKind.Colon,
                W("foo"),
                SliceNode(null, null, W("step")))
        compareObjects(node, expected)

        node = makeParser("foo[::]").expr()
        expected = BinaryNode(TokenKind.Colon,
                W("foo"),
                SliceNode(null, null, null))
        compareObjects(node, expected)

        node = makeParser("foo[:]").expr()
        expected = BinaryNode(TokenKind.Colon,
                W("foo"),
                SliceNode(null, null, null))
        compareObjects(node, expected)

        node = makeParser("foo[start::step]").expr()
        expected = BinaryNode(TokenKind.Colon,
                W("foo"),
                SliceNode(W("start"), null, W("step")))
        compareObjects(node, expected)

        // non-slice case

        node = makeParser("foo[start]").expr()
        expected = BinaryNode(TokenKind.LeftBracket, W("foo"), W("start"))
        compareObjects(node, expected)

        // Failure cases

        var e: ParserException = assertFailsWith(ParserException::class) {
            makeParser("foo[start::step:]").expr()
        }
        assertIn("Expected RightBracket but got Colon", e)

        e = assertFailsWith(ParserException::class) {
            makeParser("foo[a, b:c:d]").expr()
        }
        assertIn("expected 1 expression, found 2", e)

        e = assertFailsWith(ParserException::class) {
            makeParser("foo[a:b, c:d]").expr()
        }
        assertIn("expected 1 expression, found 2", e)

        e = assertFailsWith(ParserException::class) {
            makeParser("foo[a:b:c,d, e]").expr()
        }
        assertIn("expected 1 expression, found 3", e)
    }
}

class ConfigTest {
    @Test
    fun files() {

        val notMappings = setOf(
            "data.cfg",
            "incl_list.cfg",
            "pages.cfg",
            "routes.cfg"
        )

        dataFileDir("derived").listFiles().forEach {
            val config = Config()

            try {
                config.loadFile(it.toString())
            }
            catch (e: ConfigException) {
                val bn = it.name
                val msg = e.message!!

                if (bn in notMappings) {
                    assertIn("Root configuration must be a mapping", msg)
                }
                else if (bn == "dupes.cfg"){
                    assertIn("Duplicate key ", msg)
                }
            }
        }
    }

    private fun pp(s : String) {
        parsePath(s)
    }

    @Test
    fun badPaths() {
        val cases = listOf(
            Pair("foo[1, 2]", "Invalid index at (1, 5): expected 1 expression, found 2"),
            Pair("foo[1] bar", "Invalid path: foo[1] bar"),
            Pair("foo.123", "Invalid path: foo.123"),
            Pair("foo.", "Expected Word but got EOF"),
            Pair("foo[]", "Invalid index at (1, 5): expected 1 expression, found 0"),
            Pair("foo[1a]", "Invalid character in number: a"),
            Pair("4", "Invalid path: 4")
        )

        for (c in cases) {
            val e = { pp(c.first) }
            val t = assertFailsWith<InvalidPathException>(block = e)
            assertEquals("Invalid path: ${c.first}", t.message)
            assertEquals(c.second, t.cause!!.message)
        }
    }

    @Test
    fun identifiers() {
        val cases = listOf(
            Pair("foo", true),
            Pair("\u0935\u092e\u0938", true),
            Pair("\u73b0\u4ee3\u6c49\u8bed\u5e38\u7528\u5b57\u8868", true),
            Pair("foo ", false),
            Pair("foo[", false),
            Pair("foo [", false),
            Pair("foo.", false),
            Pair("foo .", false),
            Pair("\u0935\u092e\u0938.", false),
            Pair("\u73b0\u4ee3\u6c49\u8bed\u5e38\u7528\u5b57\u8868.", false),
            Pair("9", false),
            Pair("9foo", false),
            Pair("hyphenated-key", false)
        )
        for ((i, c) in cases.withIndex()) {
            val p = isIdentifier(c.first)
            assertEquals(c.second, p, "Failed at $i for ${c.first}")
        }
    }

    @Test
    fun pathIteration() {
        var p = parsePath("foo[bar].baz.bozz[3].fizz ")
        var iter = pathIterator(p)
        var actual = iter.toList()
        var expected : List<Any?> = arrayListOf(
            Token(TokenKind.Word, "foo", "foo"),
            Pair(TokenKind.LeftBracket, "bar"),
            Pair(TokenKind.Dot, "baz"),
            Pair(TokenKind.Dot, "bozz"),
            Pair(TokenKind.LeftBracket, 3L),
            Pair(TokenKind.Dot, "fizz")
        )
        compareObjects(expected, actual)
        p = parsePath("foo[1:2]")
        iter = pathIterator(p)
        actual = iter.toList()
        expected = arrayListOf(
            Token(TokenKind.Word, "foo", "foo"),
            Pair(TokenKind.Colon, SliceNode(Token(TokenKind.Integer, "1", 1L),
                                            Token(TokenKind.Integer, "2", 2L), null))
        )
        compareObjects(expected, actual)
    }

    @Test
    fun mainConfig() {
        val rd = dataFileDir("derived").toString()
        val p = Paths.get(rd, "main.cfg").toString()
        val config = Config()

        config.includePath.add(dataFileDir("base").toString())
        config.loadFile(p)

        val logConf = config["logging"] as Config

        val keys = ArrayList(logConf.asDict().keys).sorted()

        assertEquals(listOf("formatters", "handlers", "loggers", "root"), keys)
        var e = assertFailsWith(InvalidPathException::class) {
            logConf["handlers.file/filename"]
        }
        assertIn("Invalid path: handlers.file/filename", e)
        e = assertFailsWith(InvalidPathException::class) {
            logConf["\"handlers.file/filename"]
        }
        assertIn("Invalid path: \"handlers.file/filename", e)
        assertEquals("bar", logConf.get("foo", "bar"))
        assertEquals("baz", logConf.get("foo.bar", "baz"))
        assertEquals("bozz", logConf.get("handlers.debug.levl", "bozz"))
        assertEquals("run/server.log", logConf["handlers.file.filename"])
        assertEquals("run/server-debug.log", logConf["handlers.debug.filename"])
        assertEquals(listOf("file", "error", "debug"), logConf["root.handlers"])
        assertEquals(listOf("file", "error"), logConf["root.handlers[:2]"])
        assertEquals(listOf("file", "debug"), logConf["root.handlers[::2]"])

        val test = config["test"] as Config
        assertEquals(1.0e-7, test["float"])
        assertEquals(0.3, test["float2"])
        assertEquals(3.0, test["float3"])
        assertEquals(2L, test["list[1]"])
        assertEquals("b", test["dict.a"])
        val ld = LocalDate.of(2019, 3, 28)
        assertEquals(ld, test["date"])
        var ldt = LocalDateTime.of(2019, 3, 28,
                                            23, 27, 4,
                                    314159000)
        val zo = ZoneOffset.ofHoursMinutes(5, 30)
        val expected = OffsetDateTime.of(ldt, zo)
        assertEquals(expected, test["date_time"])
        val negzo = ZoneOffset.ofHoursMinutes(-5, -30)
        val negoffset = OffsetDateTime.of(ldt, negzo)
        assertEquals(negoffset, test["neg_offset_time"])
        ldt = LocalDateTime.of(2019, 3, 28,
                        23, 27, 4,
                    271828000)
        assertEquals(ldt, test["alt_date_time"])
        ldt =  LocalDateTime.of(2019, 3, 28,
                23, 27, 4)
        assertEquals(ldt, test["no_ms_time"])
        assertEquals(3.3, test["computed"])
        assertEquals(2.7, test["computed2"])
        assertEquals(0.9, toDouble(test["computed3"]), 1e-7)
        assertEquals(10.0, test["computed4"])
        config["base"] as Config
        val elist = listOf(
            "derived_foo", "derived_bar", "derived_baz",
            "test_foo", "test_bar", "test_baz",
            "base_foo", "base_bar", "base_baz"
        )
        assertEquals(elist, config["combined_list"])
        var emap = hashMapOf(
            "foo_key" to "base_foo",
            "bar_key" to "base_bar",
            "baz_key" to "base_baz",
            "base_foo_key" to "base_foo",
            "base_bar_key" to "base_bar",
            "base_baz_key" to "base_baz",
            "derived_foo_key" to "derived_foo",
            "derived_bar_key" to "derived_bar",
            "derived_baz_key" to "derived_baz",
            "test_foo_key" to "test_foo",
            "test_bar_key" to "test_bar",
            "test_baz_key" to "test_baz"
        )
        assertEquals(emap, config["combined_map_1"])
        emap = hashMapOf(
            "derived_foo_key" to "derived_foo",
            "derived_bar_key" to "derived_bar",
            "derived_baz_key" to "derived_baz"
        )
        assertEquals(emap, config["combined_map_2"])

        assertEquals((config["number_1"] as Long) and
                     (config["number_2"] as Long),
                     config["number_3"])
        assertEquals((config["number_1"] as Long) xor
                (config["number_2"] as Long),
                config["number_4"])
        val cases = listOf(
            Pair("logging[4]", "string required, but found 4"),
            Pair("logging[:4]", "slices can only operate on lists"),
            Pair("no_such_key", "Not found in configuration: no_such_key")
        )
        for(c in cases) {
            val ce = assertFailsWith(ConfigException::class) {
                config[c.first]
            }
            assertIn(c.second, ce)
        }
    }

    @Test
    fun exampleConfig() {
        val rd = dataFileDir("derived").toString()
        val p = Paths.get(rd, "example.cfg").toString()
        val config = Config()

        config.includePath.add(dataFileDir("base").toString())
        config.loadFile(p)

        // strings
        assertEquals(config["snowman_escaped"], config["snowman_unescaped"])
        assertEquals("\u2603", config["snowman_escaped"])
        assertEquals("\ud83d\ude02",config[ "face_with_tears_of_joy"])
        assertEquals("\ud83d\ude02", config["unescaped_face_with_tears_of_joy"])
        val strings = config["strings"] as ArrayList<Any?>
        assertEquals("Oscar Fingal O'Flahertie Wills Wilde", strings[0])
        assertEquals("size: 5\"", strings[1])
        if (System.getProperty("os.name").startsWith("Windows")) {
            assertEquals("Triple quoted form\r\ncan span\r\n'multiple' lines", strings[2])
            assertEquals("with \"either\"\r\nkind of 'quote' embedded within", strings[3])
        }
        else {
            assertEquals("Triple quoted form\ncan span\n'multiple' lines", strings[2])
            assertEquals("with \"either\"\nkind of 'quote' embedded within", strings[3])
        }

        // special strings
        assertEquals(File.pathSeparator, config["special_value_1"])

        assertEquals(System.getenv("HOME"), config["special_value_2"])

        val sv3  = config["special_value_3"] as OffsetDateTime
        assertNotNull(sv3)
        assertEquals(2019, sv3.year)
        assertEquals(3, sv3.monthValue)
        assertEquals(28, sv3.dayOfMonth)
        assertEquals(23, sv3.hour)
        assertEquals(27, sv3.minute)
        assertEquals(4, sv3.second)
        assertEquals(314159000, sv3.nano)
        assertEquals((5 * 60 + 30) * 60 + 43, sv3.offset.totalSeconds)

        assertEquals("bar", config["special_value_4"])

        assertEquals(LocalDate.now(), config["special_value_5"])

        // integers
        assertEquals(123L, config["decimal_integer"])
        assertEquals(0x123L, config["hexadecimal_integer"])
        assertEquals(83L, config["octal_integer"])
        assertEquals(0b000100100011L, config["binary_integer"])

        // floats
        assertEquals(123.456, config["common_or_garden"])
        assertEquals(0.123, config["leading_zero_not_needed"])
        assertEquals(123.0, config["trailing_zero_not_needed"])
        assertEquals(1.0e6, config["scientific_large"])
        assertEquals(1.0e-7, config["scientific_small"])
        assertEquals(3.14159, config["expression_1"])

        // complex
        assertEquals(Complex(3.0, 2.0), config["expression_2"])
        assertEquals(Complex(1.0, 3.0), config["list_value[4]"])

        // boolean
        assertEquals(true, config["boolean_value"])
        assertEquals(false, config["opposite_boolean_value"])
        assertEquals(false, config["computed_boolean_2"])
        assertEquals(true, config["computed_boolean_1"])

        // list
        assertEquals(listOf("a", "b", "c"), config["incl_list"])

        // mapping
        var expected = hashMapOf(
            "bar" to "baz",
            "foo" to "bar"
        )
        assertEquals(expected, (config["incl_mapping"] as Config).asDict())
        expected = hashMapOf(
            "baz" to "bozz",
            "fizz" to "buzz"
        )
        assertEquals(expected, (config["incl_mapping_body"] as Config).asDict())
    }

    @Test
    fun duplicates() {
        val rd = dataFileDir("derived").toString()
        val p = Paths.get(rd, "dupes.cfg").toString()
        val config = Config()
        val e = assertFailsWith(ConfigException::class) {
            config.loadFile(p)
        }
        assertIn("Duplicate key ", e)
        config.noDuplicates = false
        config.loadFile(p)
        assertEquals("not again!", config["foo"])
    }

    @Test
    fun context() {
        val rd = dataFileDir("derived").toString()
        val p = Paths.get(rd, "context.cfg").toString()
        val config = Config()
        config.context = hashMapOf(
            "bozz" to "bozz-bozz"
        )
        config.loadFile(p)
        assertEquals("bozz-bozz", config["baz"])
        val e = assertFailsWith(ConfigException::class) {
            config["bad"]
        }
        assertIn("Unknown variable ", e)
    }

    private fun fromPath(p : String) : Config {
        val result = Config(p)

        assertEquals(p, result.path)
        return result
    }

    @Test
    fun expressions() {
        val rd = dataFileDir("derived").toString()
        val p = Paths.get(rd, "test.cfg").toString()
        val config = fromPath(p)

        val expected = hashMapOf("a" to "b", "c" to "d")
        assertEquals(expected, config["dicts_added"])
        val expected2 = hashMapOf(
            "a" to hashMapOf("b" to "c", "w" to "x"),
            "d" to hashMapOf("e" to "f", "y" to "z")
        )
        assertEquals(expected2, config["nested_dicts_added"])
        val expected3 = listOf("a", 1L, "b", 2L)
        assertEquals(expected3, config["lists_added"])
        assertEquals(listOf(1L, 2L), config["list[:2]"])
        assertEquals(hashMapOf("a" to "b"), config["dicts_subtracted"])
        assertEquals(hashMapOf<String, Any?>(), config["nested_dicts_subtracted"])
        val expected4 = hashMapOf<String, Any?>(
            "a_list" to listOf(1L, 2L, hashMapOf("a" to 3L)),
            "a_map" to hashMapOf("k1" to listOf("b", "c", hashMapOf("d" to "e")))
        )
        assertEquals(expected4, config["dict_with_nested_stuff"])
        assertEquals(listOf(1L, 2L), config["dict_with_nested_stuff.a_list[:2]"])
        assertEquals(-4L, config["unary"])
        assertEquals("mno", config["abcdefghijkl"])
        assertEquals(8L, config["power"])
        assertEquals(2.5, config["computed5"])
        assertEquals(2L, config["computed6"])
        assertEquals(Complex(3.0, 1.0), config["c3"])
        assertEquals(Complex(5.0, 5.0), config["c4"])
        assertEquals(2L, config["computed8"])
        assertEquals(160L, config["computed9"])
        assertEquals(62L, config["computed10"])

        assertEquals("b", config["dict.a"])
        // second call should return the same
        assertEquals("b", config["dict.a"])

        // test interpolation

        assertEquals("A-4 a test_foo true 10 1.0E-7 1 b [a, c, e, g]Z", config["interp"])
        assertEquals("{a: b}", config["interp2"])

        // test failure cases

        val cases = arrayOf(
            Pair("bad_include", "@ operand must be a string"),
            Pair("computed7", "Not found in configuration: float4"),
            Pair("bad_interp", "Unable to convert string ")
        )

        for (c in cases) {
            val e = assertFailsWith(ConfigException::class) {
                config[c.first]
            }
            assertIn(c.second, e)
        }
    }

    @Test
    fun forms() {
        val rd = dataFileDir("derived").toString()
        val p = Paths.get(rd, "forms.cfg").toString()
        val config = Config()

        config.includePath.add(dataFileDir("base").toString())
        config.loadFile(p)
        val d = config.get("modals.deletion.contents[0].id", NullValue)

        assertEquals(d, "frm-deletion")
        val cases = listOf(
            Pair("refs.delivery_address_field", hashMapOf(
                "kind" to "field",
                "type" to "textarea",
                "name" to "postal_address",
                "label" to "Postal address",
                "label_i18n" to "postal-address",
                "short_name" to "address",
                "placeholder" to "We need this for delivering to you",
                "ph_i18n" to "your-postal-address",
                "message" to " ",
                "required" to true,
                "attrs" to hashMapOf("minlength" to 10L),
                "grpclass" to "col-md-6"
            )),
            Pair("refs.delivery_instructions_field", hashMapOf(
                "kind" to "field",
                "type" to "textarea",
                "name" to "delivery_instructions",
                "label" to "Delivery Instructions",
                "short_name" to "notes",
                "placeholder" to "Any special delivery instructions?",
                "message" to " ",
                "label_i18n" to "delivery-instructions",
                "ph_i18n" to "any-special-delivery-instructions",
                "grpclass" to "col-md-6"
            )),
            Pair("refs.verify_field", hashMapOf(
                "kind" to  "field",
                "type" to  "input",
                "name" to  "verification_code",
                "label" to  "Verification code",
                "label_i18n" to  "verification-code",
                "short_name" to  "verification code",
                "placeholder" to  "Your verification code (NOT a backup code)",
                "ph_i18n" to  "verification-not-backup-code",
                "attrs" to  hashMapOf(
                        "minlength" to  6L,
                        "maxlength" to  6L,
                        "autofocus" to  true),
                "append" to  hashMapOf(
                        "label" to  "Verify",
                        "type" to  "submit",
                        "classes" to  "btn-primary"),
                "message" to  " ",
                "required" to  true
            )),
            Pair("refs.signup_password_field", hashMapOf(
                "kind" to "field",
                "type" to "password",
                "label" to "Password",
                "label_i18n" to "password",
                "message" to " ",
                "name" to "password",
                "ph_i18n" to "password-wanted-on-site",
                "placeholder" to "The password you want to use on this site",
                "required" to true,
                "toggle" to true
            )),
            Pair("refs.signup_password_conf_field", hashMapOf(
                "kind" to "field",
                "type" to "password",
                "name" to "password_conf",
                "label" to "Password confirmation",
                "label_i18n" to "password-confirmation",
                "placeholder" to "The same password, again, " +
                "to guard against mistyping",
                "ph_i18n" to "same-password-again",
                "message" to " ",
                "toggle" to true,
                "required" to true
            )),
            Pair("fieldsets.signup_ident[0].contents[0]", hashMapOf(
                "kind" to  "field",
                "type" to  "input",
                "name" to  "display_name",
                "label" to  "Your name",
                "label_i18n" to  "your-name",
                "placeholder" to  "Your full name",
                "ph_i18n" to  "your-full-name",
                "message" to  " ",
                "data_source" to  "user.display_name",
                "required" to  true,
                "attrs" to  hashMapOf("autofocus" to  true),
                "grpclass" to  "col-md-6"
            )),
            Pair("fieldsets.signup_ident[0].contents[1]", hashMapOf(
                "kind" to "field",
                "type" to "input",
                "name" to "familiar_name",
                "label" to "Familiar name",
                "label_i18n" to "familiar-name",
                "placeholder" to "If not just the first word in your full name",
                "ph_i18n" to "if-not-first-word",
                "data_source" to "user.familiar_name",
                "message" to " ",
                "grpclass" to "col-md-6"
            )),
            Pair("fieldsets.signup_ident[1].contents[0]", hashMapOf(
                "kind" to "field",
                "type" to "email",
                "name" to "email",
                "label" to "Email address (used to sign in)",
                "label_i18n" to "email-address",
                "short_name" to "email address",
                "placeholder" to "Your email address",
                "ph_i18n" to "your-email-address",
                "message" to " ",
                "required" to true,
                "data_source" to "user.email",
                "grpclass" to "col-md-6"
            )),
            Pair("fieldsets.signup_ident[1].contents[1]", hashMapOf(
                "kind" to "field",
                "type" to "input",
                "name" to "mobile_phone",
                "label" to "Phone number",
                "label_i18n" to "phone-number",
                "short_name" to "phone number",
                "placeholder" to "Your phone number",
                "ph_i18n" to "your-phone-number",
                "classes" to "numeric",
                "message" to " ",
                "prepend" to hashMapOf("icon" to "phone"),
                "attrs" to hashMapOf("maxlength" to 10L),
                "required" to true,
                "data_source" to "customer.mobile_phone",
                "grpclass" to "col-md-6"
            ))
        )
        for (t in cases) {
            val k = t.first
            val v = t.second
            val dw = config[k]

            compareObjects(v, dw)
        }
    }

    @Test
    fun pathAcrossIncludes() {
        val rd = dataFileDir("base").toString()
        val p = Paths.get(rd, "main.cfg").toString()
        val config = fromPath(p)

        val e1 = org.apache.logging.log4j.core.appender.FileAppender::class.java
        assertEquals(e1, config["logging.appenders.file.class"])
        assertEquals("run/server.log", config["logging.appenders.file.filename"])
        assertTrue(config["logging.appenders.file.append"] as Boolean)
        assertEquals(e1, config["logging.appenders.error.class"])
        assertEquals("run/server-errors.log", config["logging.appenders.error.filename"])
        assertFalse(config["logging.appenders.error.append"] as Boolean)
        assertEquals("https://freeotp.github.io/", config["redirects.freeotp.url"])
        assertFalse(config["redirects.freeotp.permanent"] as Boolean)
    }

    @Test
    fun sources() {
        val cases = listOf(
            "foo[::2]",
            "foo[:]",
            "foo[:2]",
            "foo[2:]",
            "foo[::1]",
            "foo[::-1]",
            "foo[3]"
        )

        for (c in cases) {
            val node = parsePath(c)
            val s = toSource(node)

            assertEquals(c, s)
        }
    }

    @Test
    fun badConversions() {
        val config = Config()
        val cases = listOf(
            "foo"
        )

        for (c in cases) {
            config.strictConversions = true
            val e = assertFailsWith(ConfigException::class) {
                config.convertString(c)
            }
            assertIn("Unable to convert string $c", e)
            config.strictConversions = false
            val s = config.convertString(c)
            assertTrue(c === s)
        }
    }

    @Test
    fun circularReferences() {
        val rd = dataFileDir("derived").toString()
        val p = Paths.get(rd, "test.cfg").toString()
        val config = fromPath(p)

        val cases = arrayOf(
            Pair("circ_list[1]", "Circular reference: circ_list[1] (46, 5)"),
            Pair("circ_map.a", "Circular reference: circ_map.a (53, 8), circ_map.b (51, 8), circ_map.c (52, 8)")
        )

        for (c in cases) {
            val e = assertFailsWith(CircularReferenceException::class) {
                config[c.first]
            }
            assertEquals(c.second, e.message)
        }
    }

    @Test
    fun caching() {
        val rd = dataFileDir("derived").toString()
        val p = Paths.get(rd, "test.cfg").toString()
        val config = fromPath(p)
        config.cached = true
        val v1 = config["time_now"]
        Thread.sleep(50)
        val v2 = config["time_now"]
        assertEquals(v1, v2)
        config.cached = false
        val v3 = config["time_now"]
        Thread.sleep(50)
        val v4 = config["time_now"]
        assertNotEquals(v3, v4)
        assertNotEquals(v3, v1)
    }

    @Test
    fun slicesAndIndices() {
        val rd = dataFileDir("derived").toString()
        val p = Paths.get(rd, "test.cfg").toString()
        val config = fromPath(p)
        val theList = arrayListOf("a", "b", "c", "d", "e", "f", "g")

        // slices

        compareObjects(theList, config["test_list[:]"])
        compareObjects(theList, config["test_list[::]"])
        compareObjects(theList, config["test_list[:20]"])
        compareObjects(arrayListOf("a", "b", "c", "d"), config["test_list[-20:4]"])
        compareObjects(theList, config["test_list[-20:20]"])
        compareObjects(arrayListOf("c", "d", "e", "f", "g"), config["test_list[2:]"])
        compareObjects(arrayListOf("e", "f", "g"), config["test_list[-3:]"])
        compareObjects(arrayListOf("f", "e", "d"), config["test_list[-2:2:-1]"])
        compareObjects(arrayListOf("g", "f", "e", "d", "c", "b", "a"), config["test_list[::-1]"])
        compareObjects(arrayListOf("c", "e"), config["test_list[2:-2:2]"])
        compareObjects(arrayListOf("a", "c", "e", "g"), config["test_list[::2]"])
        compareObjects(arrayListOf("a", "d", "g"), config["test_list[::3]"])
        compareObjects(arrayListOf("a", "g"), config["test_list[::2][::3]"])

        // indices

        for ((i, v) in theList.withIndex()) {
            assertEquals(v, config["test_list[$i]"])
        }

        // negative indices

        val n = theList.size
        for (i in n downTo 1) {
            assertEquals(theList[n - i], config["test_list[-$i]"])
        }

        // invalid indices

        for (i in arrayOf(n, n + 1, -(n + 1), -(n + 2))) {
            val ce = assertFailsWith(ConfigException::class) {
                config["test_list[$i]"]
            }
            assertIn("index out of range: ", ce)
        }
    }

    @Test
    fun includePaths() {
        val rd = dataFileDir("derived").toString()
        val p1 = Paths.get(rd, "test.cfg")
        val p2 = p1.toAbsolutePath()
        for (p in listOf(p1, p2)) {
            val s = p.toString().replace("\\", "/")
            val source = "test: @'$s'"
            val cfg = Config(StringReader(source))
            assertEquals(2L, cfg["test.computed6"])
        }
    }

    @Test
    fun nestedIncludePaths() {
        val d1 = dataFileDir("base").toString()
        val d2 = dataFileDir("derived").toString()
        val d3 = dataFileDir("another").toString()
        val p = Paths.get(d1, "top.cfg").toString()
        val cfg = fromPath(p)

        cfg.includePath = arrayListOf(d2, d3)
        assertEquals(42L, cfg["level1.level2.final"])
    }

    @Test
    fun recursiveConfiguration() {
        val rd = dataFileDir("derived").toString()
        val p = Paths.get(rd, "recurse.cfg")
        val cfg = fromPath(p.toString())

        var e: ConfigException = assertFailsWith(ConfigException::class) {
            cfg["recurse"]
        }
        assertIn("Configuration cannot include itself: recurse.cfg", e)
    }
}
