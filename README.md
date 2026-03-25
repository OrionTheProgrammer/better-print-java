# BPJ - Better Print for Java

Libreria ligera para interpolar texto en Java 17+ con una sintaxis simple:

```java
BPJ.println("Hola! mi nombre es {name}", "name", "Pablo");
```

Tambien soporta acceso a propiedades anidadas:

```java
String text = BPJ.format("Valor del producto: {product.value}", Map.of("product", product));
```

## Requisitos

- Java 17 o superior
- Maven 3.9+

## Instalacion (Maven)

Cuando publiques la libreria, agrega la dependencia:

```xml
<dependency>
  <groupId>io.github.bpj</groupId>
  <artifactId>bpj</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Uso rapido

### 1) Variables con varargs

```java
String msg = BPJ.format("Hola {name}, tu total es {total}", "name", "Ana", "total", 1200);
// Hola Ana, tu total es 1200
```

### 2) Variables con Map

```java
Map<String, Object> ctx = Map.of("name", "Carlos", "final", 3500);
String msg = BPJ.format("Cliente: {name}, precio final: {final}", ctx);
```

### 3) Propiedades anidadas

```java
record Product(int value) {}

Map<String, Object> ctx = Map.of("product", new Product(700));
String msg = BPJ.format("Valor del producto: {product.value}", ctx);
```

### 4) Contexto raiz con objeto

```java
record Checkout(String customer, int finalPrice) {}

String msg = BPJ.format("Cliente: {customer} - Final: {finalPrice}", new Checkout("Luz", 5500));
```

### 5) Modo estricto

Por defecto, si una variable no existe se mantiene el placeholder:

```java
BPJ.format("Total: {final}", Map.of("subtotal", 100));
// Total: {final}
```

Si quieres error al faltar una variable:

```java
BPJ.formatStrict("Total: {final}", Map.of("subtotal", 100));
// IllegalArgumentException
```

## API principal

- `BPJ.format(String template)`
- `BPJ.format(String template, Map<String, ?> context)`
- `BPJ.format(String template, Object context)`
- `BPJ.format(String template, Object... keyValues)`
- `BPJ.formatStrict(...)`
- `BPJ.print(String template)`
- `BPJ.print(...)`
- `BPJ.println(String template)`
- `BPJ.println(...)`

## Desarrollo local

```bash
mvn test
```
