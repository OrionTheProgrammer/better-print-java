# BPJ Runtime API Reference

This document describes all public functions in `io.github.bpj.BPJ`.

## Placeholder Syntax

BPJ placeholders use braces:
- `{name}`
- `{user.name}`

Root token:
- The first segment (`name`, `user`) is resolved from context.

Nested token:
- Remaining segments (`name` in `user.name`) are resolved through:
  - `Map` keys
  - record components
  - getters (`getX`, `isX`, and exact method name)
  - fields (including inherited fields)

Escaping:
- `{{` is rendered as literal `{`
- `}}` is rendered as literal `}`

## Context Model

BPJ can resolve placeholders from:
- Explicit map context per call
- Explicit root object per call
- Explicit key-value pairs per call
- Thread-bound context stack (`bind(...)`)

When multiple bound scopes exist, BPJ resolves from the most recent scope first.

## Functions

### `boolean hasBoundContext()`

Returns `true` if at least one context scope is currently bound in the current thread.

### `void clearBoundContext()`

Clears all bound scopes in the current thread.

Use this only for defensive cleanup in managed thread scenarios. Prefer `try-with-resources`.

### `Scope bind(Map<String, ?> context)`

Binds a map scope to the current thread.

Use in `try-with-resources`:

```java
try (BPJ.Scope ignored = BPJ.bind(Map.of("name", "Ana"))) {
    BPJ.println("Hello {name}");
}
```

### `Scope bind(Object context)`

Binds a root object scope.

If `context` is a `Map`, BPJ treats it as map context.

### `Scope bind(Object... keyValues)`

Binds key-value pairs as map context.

The array length must be even and keys must be `String`.

### `String format(String template)`

Formats using currently bound scopes.

If no scope is bound:
- returns template unchanged (except escaped braces normalization).

### `String format(String template, Map<String, ?> context)`

Formats using explicit map context.

### `String format(String template, Object context)`

Formats using explicit root object or map.

### `String format(String template, Object... keyValues)`

Formats using explicit key-value pairs.

### `String formatStrict(String template)`

Strict format with bound scopes.

Throws `IllegalArgumentException` when any placeholder is unresolved.

### `String formatStrict(String template, Map<String, ?> context)`

Strict format with explicit map context.

### `String formatStrict(String template, Object context)`

Strict format with explicit root context (or map).

### `String formatStrict(String template, Object... keyValues)`

Strict format with explicit key-value context.

### `void print(String template)`

Prints formatted output (no newline), resolving against bound scopes.

### `void print(String template, Map<String, ?> context)`

Prints formatted output (no newline), resolving against explicit map context.

### `void print(String template, Object context)`

Prints formatted output (no newline), resolving against explicit root context.

### `void print(String template, Object... keyValues)`

Prints formatted output (no newline), resolving against explicit key-value context.

### `void println(String template)`

Prints formatted output with newline, resolving against bound scopes.

### `void println(String template, Map<String, ?> context)`

Prints formatted output with newline, resolving against explicit map context.

### `void println(String template, Object context)`

Prints formatted output with newline, resolving against explicit root context.

### `void println(String template, Object... keyValues)`

Prints formatted output with newline, resolving against explicit key-value context.

## Scope Contract

`BPJ.Scope` is `AutoCloseable`:

- Closing is idempotent.
- Closing out of stack order throws `IllegalStateException`.

This enforces deterministic lifecycle for nested contexts:

```java
try (BPJ.Scope outer = BPJ.bind("name", "A")) {
    try (BPJ.Scope inner = BPJ.bind("name", "B")) {
        BPJ.println("{name}"); // B
    }
    BPJ.println("{name}"); // A
}
```

## Strict vs Non-Strict

Non-strict (`format` / `print` / `println`):
- unresolved placeholders remain unchanged (`{missing}`)

Strict (`formatStrict`):
- unresolved placeholders throw `IllegalArgumentException`

## Error Conditions

`IllegalArgumentException`:
- unresolved placeholder in strict mode
- odd varargs length in key-value API
- non-string keys in key-value API
- non-string keys in `Map` contexts

`IllegalStateException`:
- reflection access issues for object property resolution
- out-of-order scope close

## Threading

Bound scopes are thread-local:
- Each thread has independent context stack.
- Context does not cross thread boundaries automatically.
