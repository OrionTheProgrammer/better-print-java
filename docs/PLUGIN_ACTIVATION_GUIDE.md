# BPJ Plugin Activation Guide

This guide explains how to activate BPJ source transformation so one-argument calls like:

```java
BPJ.print("Hello {user.name}");
```

work correctly without manually passing context.

## Why Activation Is Required

BPJ has two usage modes:

- Runtime explicit context:
  - `BPJ.print("Hello {user.name}", Map.of("user", user))`
  - Works without build plugin.
- Build-time auto context injection:
  - `BPJ.print("Hello {user.name}")`
  - Requires BPJ Maven or Gradle plugin activation.

If plugin activation is missing, one-argument calls with placeholders can be printed unchanged.

## Prerequisites

- Java 17+
- BPJ version `0.3.0`
- Build with Maven or Gradle (recommended from terminal or delegated build in IDE)

## Maven Activation

### Option A: Auto-configured parent (recommended)

```xml
<parent>
  <groupId>io.github.oriontheprogrammer</groupId>
  <artifactId>bpj-starter-parent</artifactId>
  <version>0.3.0</version>
</parent>
```

### Option B: Spring Boot-compatible auto parent

```xml
<parent>
  <groupId>io.github.oriontheprogrammer</groupId>
  <artifactId>bpj-spring-boot-parent</artifactId>
  <version>0.3.0</version>
</parent>
```

### Option C: Manual plugin setup (for custom parent, including `spring-boot-starter-parent`)

```xml
<dependencies>
  <dependency>
    <groupId>io.github.oriontheprogrammer</groupId>
    <artifactId>bpj</artifactId>
    <version>0.3.0</version>
  </dependency>
</dependencies>

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

## Gradle Activation

### Option A: Plugin DSL (recommended)

`settings.gradle`:

```groovy
pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
  }
}
```

`build.gradle`:

```groovy
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

### Option B: Legacy buildscript classpath

```groovy
buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath "io.github.oriontheprogrammer:bpj-gradle-plugin:0.3.0"
  }
}

apply plugin: "java"
apply plugin: "io.github.oriontheprogrammer.bpj"

repositories {
  mavenCentral()
}

dependencies {
  implementation "io.github.oriontheprogrammer:bpj:0.3.0"
}
```

## Verify Activation

### Maven

Run:

```bash
mvn clean compile
```

Check generated transformed sources:

- `target/generated-sources/bpj`

### Gradle

Run:

```bash
./gradlew clean compileJava
```

Check generated transformed sources:

- `build/generated/sources/bpj/main`

### Functional check

Compile/run code with:

```java
BPJ.println("Hello {user.name}");
BPJ.println("Name method: {user.getName()}");
```

If activation is correct, output resolves values instead of printing placeholders literally.

## IDE Setup (Important)

If you run `main()` directly from IDE without delegated build, transformation may be skipped.

- IntelliJ IDEA:
  - Enable delegated build/run using Maven or Gradle.
- Eclipse/STS:
  - Prefer launching through Maven/Gradle tasks.
- VS Code:
  - Run project via Maven/Gradle tasks or terminal commands.

## Troubleshooting

### Problem: Prints `{placeholder}` literally

Common causes:
- Plugin not configured.
- Build not executed through Maven/Gradle.
- Placeholder root not available in context.

Fix:
- Activate plugin using this guide.
- Build/run from Maven/Gradle.
- Or pass context explicitly.

### Problem: `{persona.getName()}` still unresolved

Check:
- `persona` root exists in context (or source transformation injected it).
- Method has no arguments and is accessible.

### Problem: `{name.substring(1)}` fails

Expected behavior:
- Placeholder method calls support only no-arg form (`getName()`), not method arguments.

## Recommended Pattern

For microservices and team projects:

1. Use one of the BPJ parent POMs (or Gradle plugin DSL).
2. Keep build delegated to Maven/Gradle in IDE.
3. Use one-argument BPJ calls for readability.
4. Use explicit context overloads in scripts/small tools where plugin setup is not needed.
