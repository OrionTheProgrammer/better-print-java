# BPJ Maven Plugin (`bpj:prepare`)

`bpj-maven-plugin` enables ergonomic one-argument BPJ calls by rewriting source code during build.

## Easiest Setup

Use `bpj-starter-parent` and you get this plugin preconfigured automatically.

```xml
<parent>
  <groupId>io.github.bpj</groupId>
  <artifactId>bpj-starter-parent</artifactId>
  <version>0.2.0-SNAPSHOT</version>
</parent>
```

## Goal

`prepare`

- Default phase: `generate-sources`
- Thread-safe: yes

## What It Rewrites

Supported method names:
- `format`
- `formatStrict`
- `print`
- `println`

Supported call styles:
- `BPJ.println("Hello {name}")`
- `io.github.bpj.BPJ.println("Hello {name}")`
- `println("Hello {name}")` when statically imported from `io.github.bpj.BPJ`

Conditions:
- Exactly one argument
- Argument is string literal or text block
- At least one BPJ placeholder exists

## Example

Input:

```java
BPJ.println("Welcome {user.name}");
```

Generated:

```java
BPJ.println("Welcome {user.name}", java.util.Map.of("user", user));
```

## Placeholder Root Extraction

For each placeholder:
- `{user.name}` -> root variable is `user`
- `{price}` -> root variable is `price`

Repeated roots are de-duplicated in insertion order.

Generated map strategy:
- Up to 10 roots: `java.util.Map.of(...)`
- More than 10 roots: `java.util.Map.ofEntries(...)`

## Plugin Parameters

### `inputDirectory`

Default: `${project.build.sourceDirectory}`

Source root to read from.

### `outputDirectory`

Default: `${project.build.directory}/generated-sources/bpj`

Generated source root.

### `replaceCompileSourceRoot`

Default: `true`

When enabled:
- removes original `src/main/java` compile root
- compiles only generated source root

This prevents duplicate class compilation.

### `failOnError`

Default: `true`

If `true`, transformation errors fail the build.
If `false`, plugin logs a warning and build continues.

### `verbose`

Default: `false`

Logs transformed files and replacement counts.

## Configuration

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
      <configuration>
        <verbose>true</verbose>
      </configuration>
    </plugin>
  </plugins>
</build>
```

## Limitations

- Does not rewrite calls with non-literal template arguments.
- Does not evaluate arbitrary Java expressions inside placeholders.
- Does not transform methods other than BPJ public formatting/printing APIs.
- Operates at source level, not bytecode level.

## Recommended Usage Pattern

1. Add `bpj` dependency.
2. Add `bpj-maven-plugin` execution (`prepare`).
3. Write simple BPJ calls with placeholders.
4. Let build-time transformation inject context maps automatically.
