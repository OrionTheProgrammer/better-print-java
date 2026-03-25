# BPJ (Better Print for Java)

BPJ is a Java 17+ library that makes string interpolation and prints easier.
It is designed to reduce boilerplate when building messages like:

```java
BPJ.println("Bienvenido {usuario.name}");
```

## What It Is For

- Build readable messages with placeholders (`{name}`, `{product.value}`, `{final}`).
- Return interpolated `String` values with `BPJ.format(...)`.
- Print interpolated text with `BPJ.print(...)` and `BPJ.println(...)`.
- Use Maven build-time transformation so one-argument BPJ calls can work in regular Java code.

## Installation

### Recommended (runtime + plugin auto-configured)

```xml
<parent>
  <groupId>io.github.oriontheprogrammer</groupId>
  <artifactId>bpj-starter-parent</artifactId>
  <version>0.2.0</version>
</parent>
```

### Runtime + plugin (manual setup)

```xml
<dependency>
  <groupId>io.github.oriontheprogrammer</groupId>
  <artifactId>bpj</artifactId>
  <version>0.2.0</version>
</dependency>
```

```xml
<build>
  <plugins>
    <plugin>
      <groupId>io.github.oriontheprogrammer</groupId>
      <artifactId>bpj-maven-plugin</artifactId>
      <version>0.2.0</version>
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

## Usage

### Print

```java
BPJ.println("Hola {name}");
BPJ.print("Valor del producto: {product.value}");
```

### Return a String

```java
public String retornarTexto(String name, int edad) {
    return BPJ.format("Hola {name}, tienes {edad}");
}
```
