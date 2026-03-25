# BPJ (Better Print for Java)

BPJ is a Java 17+ library that makes string interpolation and prints easier.
It is designed to reduce boilerplate when building messages like:

```java
BPJ.println("Bienvenido {usuario.name}", this);
```

## What It Is For

- Build readable messages with placeholders (`{name}`, `{product.value}`, `{final}`).
- Return interpolated `String` values with `BPJ.format(...)`.
- Print interpolated text with `BPJ.print(...)` and `BPJ.println(...)`.
- Use Maven or Gradle build-time transformation so one-argument BPJ calls can work in regular Java code.

## Installation

### Maven (runtime + plugin manual setup)

```xml
<dependency>
  <groupId>io.github.oriontheprogrammer</groupId>
  <artifactId>bpj</artifactId>
  <version>0.3.0</version>
</dependency>
```

```xml
<build>
  <plugins>
    <plugin>
      <groupId>io.github.oriontheprogrammer</groupId>
      <artifactId>bpj-maven-plugin</artifactId>
      <version>0.3.0</version>
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

### Gradle (runtime + plugin)

Recommended (plugin DSL):

```groovy
// settings.gradle
pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}
```

```groovy
// build.gradle
plugins {
  id "java"
  id "io.github.oriontheprogrammer.bpj" version "0.3.0"
}

repositories {
  mavenCentral()
}

dependencies {
  implementation "io.github.oriontheprogrammer:bpj:0.3.0"
}
```

Legacy fallback (`buildscript` + `apply plugin`) is still supported.

## Usage

### Print

```java
BPJ.println("Hola {name}");
BPJ.print("Valor del producto: {product.value}");
```

### Return a String

```java
public String retornarTexto(String name, int edad) {
    return BPJ.format("Hola {name}, tienes {edad}", this);
}
```
