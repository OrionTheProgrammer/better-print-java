# BPJ (Better Print for Java)

BPJ is a Java 17+ toolkit for ergonomic string interpolation and print helpers.

It has two modules:
- `bpj` (runtime library): interpolation engine, context binding, strict mode.
- `bpj-maven-plugin` (build-time transformer): rewrites one-argument BPJ calls and injects context automatically from placeholders.
- `bpj-starter-parent` (turnkey parent POM): auto-adds runtime + plugin.
- `bpj-starter-smoke` (integration guard): verifies starter auto-runs plugin with no extra consumer config.

This enables the style:

```java
BPJ.println("Welcome {user.name}");
```

with Maven build-time transformation, even though Java does not expose caller local variables directly at runtime.

## Requirements

- Java 17 or higher
- Maven 3.9+

## Modules

- Runtime: [`bpj`](./bpj)
- Maven plugin: [`bpj-maven-plugin`](./bpj-maven-plugin)
- Turnkey parent: [`bpj-starter-parent`](./bpj-starter-parent)

## Installation (Recommended: Everything Included)

Use BPJ starter parent in your microservice `pom.xml`:

```xml
<parent>
  <groupId>io.github.bpj</groupId>
  <artifactId>bpj-starter-parent</artifactId>
  <version>0.2.0-SNAPSHOT</version>
</parent>
```

That parent automatically:
- adds `io.github.bpj:bpj` as dependency
- enables `io.github.bpj:bpj-maven-plugin` (`prepare` goal)
- compiles with Java release `17`

After this, one-argument calls work directly:

```java
public String retornarTexto(String name, int edad) {
    return BPJ.format("Hola {name}, tienes {edad}");
}
```

## Installation (Runtime Only)

Use this if you want explicit context per call or thread-bound contexts.

```xml
<dependency>
  <groupId>io.github.bpj</groupId>
  <artifactId>bpj</artifactId>
  <version>0.2.0-SNAPSHOT</version>
</dependency>
```

## Installation (Runtime + Auto Context Injection)

Add runtime dependency:

```xml
<dependency>
  <groupId>io.github.bpj</groupId>
  <artifactId>bpj</artifactId>
  <version>0.2.0-SNAPSHOT</version>
</dependency>
```

Add BPJ Maven plugin:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>io.github.bpj</groupId>
      <artifactId>bpj-maven-plugin</artifactId>
      <version>0.2.0-SNAPSHOT</version>
      <executions>
        <execution>
          <goals>
            <goal>prepare</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

With this plugin enabled, calls like:

```java
BPJ.println("Welcome {user.name}");
```

are rewritten during build to:

```java
BPJ.println("Welcome {user.name}", java.util.Map.of("user", user));
```

## Quick Start

### 1) Direct explicit context (`Map`)

```java
String text = BPJ.format(
    "Product value: {product.value}",
    Map.of("product", product)
);
```

### 2) Direct explicit context (varargs)

```java
String text = BPJ.format(
    "Customer {name}, final price {finalPrice}",
    "name", "Anna",
    "finalPrice", 1200
);
```

### 3) Bound context scope (no context per call)

```java
try (BPJ.Scope ignored = BPJ.bind("name", "Pablo", "total", 990)) {
    BPJ.println("Hello {name}");
    BPJ.println("Total: {total}");
}
```

### 4) Strict mode

```java
BPJ.formatStrict("Total: {final}", Map.of("subtotal", 100));
// throws IllegalArgumentException: Unresolved placeholder: {final}
```

### 5) Escaped braces

```java
BPJ.format("Literal {{name}} and value {name}", Map.of("name", "Maria"));
// Literal {name} and value Maria
```

## API Reference

Full API and behavior are documented in:
- [`docs/API_REFERENCE.md`](./docs/API_REFERENCE.md)
- [`docs/MAVEN_PLUGIN.md`](./docs/MAVEN_PLUGIN.md)

## Technical Notes

- Java runtime alone cannot read local variables from the caller method.
- BPJ solves this with two strategies:
  - Runtime context binding (`BPJ.bind(...)`)
  - Build-time source transformation (`bpj-maven-plugin`)
- In Maven, a plain dependency cannot auto-run a build plugin. Use `bpj-starter-parent` for turnkey setup.
- The Maven plugin currently transforms one-argument BPJ calls (`format`, `formatStrict`, `print`, `println`) when the argument is a string literal/text block.

## Build

```bash
mvn test
```

## License

MIT. See [`LICENSE`](./LICENSE).
