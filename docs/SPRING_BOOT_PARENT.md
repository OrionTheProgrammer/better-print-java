# BPJ Spring Boot Parent

`bpj-spring-boot-parent` is a parent POM for projects that want:
- Spring Boot dependency management
- BPJ runtime dependency
- Automatic execution of `bpj-maven-plugin:prepare`

This solves the single-parent limitation when you want BPJ auto-start behavior in Spring Boot services.

## Usage

```xml
<parent>
  <groupId>io.github.oriontheprogrammer</groupId>
  <artifactId>bpj-spring-boot-parent</artifactId>
  <version>0.3.0</version>
</parent>
```

## What It Configures

- Imports `org.springframework.boot:spring-boot-dependencies`
- Adds `io.github.oriontheprogrammer:bpj`
- Runs `io.github.oriontheprogrammer:bpj-maven-plugin:prepare` in `generate-sources`
- Sets Java compiler `release` to `17`

## Notes

- If you already use `spring-boot-starter-parent`, keep it and configure `bpj-maven-plugin` manually.
- For non-Spring projects, use `bpj-starter-parent` or direct plugin configuration.
