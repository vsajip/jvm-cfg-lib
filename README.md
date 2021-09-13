The CFG configuration format is a text format for configuration files which is similar to, and a superset of, the JSON format. It dates from before its first announcement in [2008](https://wiki.python.org/moin/HierConfig) and has the following aims:

* Allow a hierarchical configuration scheme with support for key-value mappings and lists.
* Support cross-references between one part of the configuration and another.
* Provide a string interpolation facility to easily build up configuration values from other configuration values.
* Provide the ability to compose configurations (using include and merge facilities).
* Provide the ability to access real application objects safely, where supported by the platform.
* Be completely declarative.

It overcomes a number of drawbacks of JSON when used as a configuration format:

* JSON is more verbose than necessary.
* JSON doesn’t allow comments.
* JSON doesn’t provide first-class support for dates and multi-line strings.
* JSON doesn’t allow trailing commas in lists and mappings.
* JSON doesn’t provide easy cross-referencing, interpolation, or composition.

The CFG reference implementation for the Java Virtual Machine (JVM) is written in the Kotlin programming language. The implementation assumes a JVM version of 8 or later. The Kotlin version tested with is 1.3.61 or later. The jar is usually named config-X.Y.Z.jar where X.Y.Z is the version number.

Installation
============
You can install the jar from the Maven repository at https://repo1.maven.org/maven2/com/red-dove/config/ and configure Maven, Gradle etc. appropriately to use it as a dependency. The jar will be located at a path like

https://repo1.maven.org/maven2/com/red-dove/config/X.Y.Z/config-X.Y.Z.jar

Where X.Y.Z is the version of the library.

Exploration
============
To explore CFG functionality for the JVM, we use the Kotlin compiler in interactive mode to act as a Read-Eval-Print-Loop (REPL). Once installed, you can invoke a shell using
```
$ export CP=config-X.Y.Z.jar:commons-math3-3.6.1.jar:kotlin-reflect-1.3.61.jar:kotlin-stdlib-1.3.61.jar
$ kotlinc-jvm -cp $CP
```
The additional jar files are runtime dependencies of this JVM implmentation.

Getting Started with CFG in Kotlin / Java
=========================================
A configuration is represented by an instance of the `Config` struct. The constructor for this class can be passed a filename or a stream which contains the text for the configuration. The text is read in, parsed and converted to an object that you can then query. A simple example:

```
a: 'Hello, '
b: 'world!'
c: {
  d: 'e'
}
'f.g': 'h'
christmas_morning: `2019-12-25 08:39:49`
home: `$HOME`
foo: `$FOO|bar`
```

Loading a configuration
=======================
The configuration above can be loaded as shown below. In the REPL shell:

```
>>> import com.reddove.config.*
>>> val cfg = Config("path/to/test0.cfg")
```

Access elements with keys
=========================
Accessing elements of the configuration with a simple key is just like using a `HashMap<String, Any>`:

```
>>> cfg["a"]
res3: kotlin.Any = Hello,
>>> cfg["b"]
res4: kotlin.Any = world!
```
You can see the types and values of the returned objects are as expected.

Access elements with paths
==========================
As well as simple keys, elements  can also be accessed using `path` strings:
```
>>> cfg["c.d"]
res5: kotlin.Any = e
```
Here, the desired value is obtained in a single step, by (under the hood) walking the path `c.d` – first getting the mapping at key `c`, and then the value at `d` in the resulting mapping.

Note that you can have simple keys which look like paths:
```
>>> cfg["f.g"]
res6: kotlin.Any = h
```
If a key is given that exists in the configuration, it is used as such, and if it is not present in the configuration, an attempt is made to interpret it as a path. Thus, `f.g` is present and accessed via key, whereas `c.d` is not an existing key, so is interpreted as a path.

Access to date/time objects
===========================
You can also get native Java `java.time.LocalDate` and `java.time.OffsetDateTime` objects from a configuration, by using an ISO date/time pattern in a `backtick-string`:
```
>>> cfg["christmas_morning"]
res7: kotlin.Any = 2019-12-25T08:39:49
>>> cfg["christmas_morning"] as java.time.LocalDateTime
res8: java.time.LocalDateTime = 2019-12-25T08:39:49
```

Access to other JVM objects
===========================
Access to other JVM objects is also possible using the backtick-string syntax, provided that they are one of:

* Environment variables
* Public static fields of public classes
* Public static methods without parameters of public classes

```
>>> import java.time.LocalDate
>>> cfg["today"] == LocalDate.now()
res9: kotlin.Boolean = true
>>> import java.lang.System
>>> cfg["output"] === System.out
res10: kotlin.Boolean = true
```
Accessing the "today" element of the above configuration invokes the static method `java.time.LocalDate.now()` and returns its value.

Access to environment variables
===============================

To access an environment variable, use a `backtick-string` of the form `$VARNAME`:
```
>>> import java.lang.System
>>> cfg["home"] == System.getenv("HOME")
res11: kotlin.Boolean = true
```
You can specify a default value to be used if an environment variable isn’t present using the `$VARNAME|default-value` form. Whatever string follows the pipe character (including the empty string) is returned if `VARNAME` is not a variable in the environment.
```
>>> cfg["foo"]
res12: kotlin.Any = bar
```
For more information, see [the CFG documentation](https://docs.red-dove.com/cfg/index.html).
