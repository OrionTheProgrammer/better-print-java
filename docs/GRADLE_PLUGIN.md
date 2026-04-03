# BPJ Gradle Plugin

`bpj-gradle-plugin` enables one-argument BPJ calls in Gradle projects by rewriting source code before Java compilation.

For end-to-end activation steps (including IDE behavior), see:
- `docs/PLUGIN_ACTIVATION_GUIDE.md`

## Plugin ID

`io.github.oriontheprogrammer.bpj`

## Installation

### Recommended: plugin DSL

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
  id "io.github.oriontheprogrammer.bpj" version "0.3.1"
}

repositories {
  mavenCentral()
}

dependencies {
  implementation "io.github.oriontheprogrammer:bpj:0.3.1"
}
```

### Legacy fallback: buildscript classpath

```groovy
buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath "io.github.oriontheprogrammer:bpj-gradle-plugin:0.3.1"
  }
}

apply plugin: "java"
apply plugin: "io.github.oriontheprogrammer.bpj"

repositories {
  mavenCentral()
}

dependencies {
  implementation "io.github.oriontheprogrammer:bpj:0.3.1"
}
```

## What It Rewrites

Input:

```java
BPJ.println("Welcome {user.name}");
```

Generated for compilation:

```java
BPJ.println("Welcome {user.name}", java.util.Map.of("user", user));
```

The plugin processes Java source sets and compiles transformed sources from:

`build/generated/sources/bpj/<sourceSetName>`

Supported BPJ methods:
- `format`, `formatStrict`, `print`, `println`
- `formatHighlighted`, `printHighlighted`, `printlnHighlighted`

Static compatibility:
- `BPJ.print("El error es: {e}", this)` inside static context is rewritten to generated map context.

## Configuration

```groovy
bpj {
  failOnError = true
  failOnUnresolved = false
  verbose = false
  writeReport = false
}
```

### `failOnError`

Default: `true`

If `true`, transformation failures break the build.

### `failOnUnresolved`

Default: `false`

If `true`, placeholders with unresolved root variables fail fast.

### `verbose`

Default: `false`

If `true`, logs transformed files and replacement counts.

### `writeReport`

Default: `false`

If `true`, writes reports at:

`build/reports/bpj/bpj-transform-<sourceSetName>.txt`

## Tasks

For each source set:

- `bpjPrepare` for `main`
- `bpjPrepare<SourceSetName>` for others (for example `bpjPrepareTest`)
