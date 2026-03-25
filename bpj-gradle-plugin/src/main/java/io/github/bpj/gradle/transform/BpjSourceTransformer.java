package io.github.bpj.gradle.transform;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/**
 * Source transformer that rewrites one-argument BPJ calls and injects the context map argument.
 */
public final class BpjSourceTransformer {
    private static final Set<String> TARGET_METHODS = Set.of(
            "format",
            "formatStrict",
            "print",
            "println",
            "formatHighlighted",
            "printHighlighted",
            "printlnHighlighted"
    );
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{\\s*([a-zA-Z_$][\\w$]*(?:\\.[a-zA-Z_$][\\w$]*)*)\\s*\\}");
    private static final Pattern PLACEHOLDER_EXPRESSION_PATTERN =
            Pattern.compile("^[a-zA-Z_$][\\w$]*(?:\\.[a-zA-Z_$][\\w$]*)*$");
    private static final Pattern BRACED_TOKEN_PATTERN = Pattern.compile("\\{([^{}]*)\\}");
    private static final String ESCAPED_OPEN_TOKEN = "\u0001BPJ_OPEN\u0001";
    private static final String ESCAPED_CLOSE_TOKEN = "\u0001BPJ_CLOSE\u0001";

    /**
     * Transforms one Java source file content.
     *
     * @param sourcePath source path used in parser diagnostics
     * @param source source code content
     * @return transformation result
     */
    public TransformationResult transform(Path sourcePath, String source) {
        return transform(sourcePath, source, false);
    }

    /**
     * Transforms one Java source file content.
     *
     * @param sourcePath source path used in parser diagnostics
     * @param source source code content
     * @param failOnUnresolved whether unresolved placeholder roots should fail transformation
     * @return transformation result
     */
    public TransformationResult transform(Path sourcePath, String source, boolean failOnUnresolved) {
        Objects.requireNonNull(sourcePath, "sourcePath cannot be null");
        Objects.requireNonNull(source, "source cannot be null");

        ParseContext parse = parse(sourcePath, source);
        List<Insertion> insertions = collectInsertions(
                sourcePath,
                parse.compilationUnit,
                parse.sourcePositions,
                failOnUnresolved
        );
        if (insertions.isEmpty()) {
            return new TransformationResult(source, 0);
        }

        String transformed = applyInsertions(source, insertions);
        return new TransformationResult(transformed, insertions.size());
    }

    private ParseContext parse(Path sourcePath, String source) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException(
                    "No system Java compiler found. Ensure the Gradle build runs with a JDK, not a JRE."
            );
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaFileObject sourceObject = new StringJavaFileObject(sourcePath, source);

        try (StandardJavaFileManager fileManager =
                     compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            JavacTask task = (JavacTask) compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    List.of("-proc:none"),
                    null,
                    List.of(sourceObject)
            );

            CompilationUnitTree compilationUnit = task.parse().iterator().next();
            failIfParseErrors(sourcePath, diagnostics);
            Trees trees = Trees.instance(task);
            SourcePositions sourcePositions = trees.getSourcePositions();
            return new ParseContext(compilationUnit, sourcePositions);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse source file: " + sourcePath, e);
        }
    }

    private void failIfParseErrors(Path sourcePath, DiagnosticCollector<JavaFileObject> diagnostics) {
        List<String> errors = diagnostics.getDiagnostics().stream()
                .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                .map(diagnostic -> diagnostic.getMessage(Locale.ROOT))
                .toList();

        if (!errors.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot parse source file " + sourcePath + ". First error: " + errors.get(0)
            );
        }
    }

    private List<Insertion> collectInsertions(
            Path sourcePath,
            CompilationUnitTree compilationUnit,
            SourcePositions sourcePositions,
            boolean failOnUnresolved
    ) {
        List<Insertion> insertions = new ArrayList<>();

        new TreePathScanner<Void, Void>() {
            @Override
            public Void visitMethodInvocation(MethodInvocationTree invocation, Void unused) {
                maybeCollect(
                        sourcePath,
                        getCurrentPath(),
                        invocation,
                        compilationUnit,
                        sourcePositions,
                        insertions,
                        failOnUnresolved
                );
                return super.visitMethodInvocation(invocation, unused);
            }
        }.scan(compilationUnit, null);

        return insertions;
    }

    private void maybeCollect(
            Path sourcePath,
            TreePath invocationPath,
            MethodInvocationTree invocation,
            CompilationUnitTree compilationUnit,
            SourcePositions sourcePositions,
            List<Insertion> insertions,
            boolean failOnUnresolved
    ) {
        if (invocation.getArguments().size() != 1) {
            return;
        }

        String methodName = methodName(invocation.getMethodSelect());
        if (!TARGET_METHODS.contains(methodName)) {
            return;
        }

        if (!isBpjCall(invocation.getMethodSelect(), methodName, compilationUnit)) {
            return;
        }

        Tree argument = invocation.getArguments().get(0);
        if (!(argument instanceof LiteralTree literal) || !(literal.getValue() instanceof String template)) {
            return;
        }

        List<String> invalid = findInvalidPlaceholders(template);
        if (!invalid.isEmpty()) {
            throw invalidPlaceholderException(sourcePath, compilationUnit, sourcePositions, invocation, invalid);
        }

        LinkedHashSet<String> roots = extractRootPlaceholders(template);
        if (roots.isEmpty()) {
            return;
        }

        if (failOnUnresolved) {
            List<String> unresolved = findUnresolvedRoots(roots, invocationPath);
            if (!unresolved.isEmpty()) {
                throw unresolvedPlaceholderException(
                        sourcePath,
                        compilationUnit,
                        sourcePositions,
                        invocation,
                        unresolved
                );
            }
        }

        long end = sourcePositions.getEndPosition(compilationUnit, argument);
        if (end < 0) {
            return;
        }

        insertions.add(new Insertion((int) end, ", " + buildContextExpression(roots)));
    }

    private String methodName(Tree methodSelect) {
        if (methodSelect instanceof MemberSelectTree memberSelect) {
            return memberSelect.getIdentifier().toString();
        }
        if (methodSelect instanceof IdentifierTree identifier) {
            return identifier.getName().toString();
        }
        return "";
    }

    private boolean isBpjCall(Tree methodSelect, String methodName, CompilationUnitTree compilationUnit) {
        if (methodSelect instanceof MemberSelectTree memberSelect) {
            String scope = memberSelect.getExpression().toString();
            return "BPJ".equals(scope) || "io.github.bpj.BPJ".equals(scope);
        }

        if (!(methodSelect instanceof IdentifierTree)) {
            return false;
        }

        for (ImportTree importTree : compilationUnit.getImports()) {
            if (!importTree.isStatic()) {
                continue;
            }

            String imported = importTree.getQualifiedIdentifier().toString();
            if ("io.github.bpj.BPJ.*".equals(imported) || ("io.github.bpj.BPJ." + methodName).equals(imported)) {
                return true;
            }
        }
        return false;
    }

    private LinkedHashSet<String> extractRootPlaceholders(String template) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(escapeBraces(template));
        LinkedHashSet<String> roots = new LinkedHashSet<>();
        while (matcher.find()) {
            String expression = matcher.group(1);
            String[] parts = expression.split("\\.");
            roots.add(parts[0]);
        }
        return roots;
    }

    private List<String> findInvalidPlaceholders(String template) {
        Matcher matcher = BRACED_TOKEN_PATTERN.matcher(escapeBraces(template));
        LinkedHashSet<String> invalid = new LinkedHashSet<>();

        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            if (!PLACEHOLDER_EXPRESSION_PATTERN.matcher(expression).matches()) {
                invalid.add("{" + matcher.group(1) + "}");
            }
        }

        return new ArrayList<>(invalid);
    }

    private IllegalArgumentException invalidPlaceholderException(
            Path sourcePath,
            CompilationUnitTree compilationUnit,
            SourcePositions sourcePositions,
            MethodInvocationTree invocation,
            List<String> invalid
    ) {
        long start = sourcePositions.getStartPosition(compilationUnit, invocation);
        long line = start >= 0 ? compilationUnit.getLineMap().getLineNumber(start) : -1;
        String location = line > 0 ? sourcePath + ":" + line : sourcePath.toString();

        String message = "Invalid BPJ placeholder(s) " + invalid
                + " at " + location
                + ". Allowed syntax: {name} or {object.field}. "
                + "Use double braces to escape literals: {{ and }}.";
        return new IllegalArgumentException(message);
    }

    private List<String> findUnresolvedRoots(LinkedHashSet<String> roots, TreePath invocationPath) {
        Set<String> availableRoots = collectAvailableRoots(invocationPath);
        return roots.stream()
                .filter(root -> !availableRoots.contains(root))
                .toList();
    }

    private Set<String> collectAvailableRoots(TreePath invocationPath) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add("this");
        names.add("super");

        for (TreePath current = invocationPath; current != null; current = current.getParentPath()) {
            Tree leaf = current.getLeaf();
            if (leaf instanceof MethodTree method) {
                for (VariableTree parameter : method.getParameters()) {
                    names.add(parameter.getName().toString());
                }
                if (method.getBody() != null) {
                    collectVariableNames(method.getBody(), names);
                }
            } else if (leaf instanceof LambdaExpressionTree lambda) {
                for (VariableTree parameter : lambda.getParameters()) {
                    names.add(parameter.getName().toString());
                }
                collectVariableNames(lambda.getBody(), names);
            } else if (leaf instanceof com.sun.source.tree.ClassTree classTree) {
                for (Tree member : classTree.getMembers()) {
                    if (member instanceof VariableTree field) {
                        names.add(field.getName().toString());
                    }
                }
            }
        }

        return names;
    }

    private void collectVariableNames(Tree tree, Set<String> names) {
        new TreeScanner<Void, Set<String>>() {
            @Override
            public Void visitClass(com.sun.source.tree.ClassTree node, Set<String> collector) {
                return null;
            }

            @Override
            public Void visitVariable(VariableTree variable, Set<String> collector) {
                collector.add(variable.getName().toString());
                return super.visitVariable(variable, collector);
            }
        }.scan(tree, names);
    }

    private IllegalArgumentException unresolvedPlaceholderException(
            Path sourcePath,
            CompilationUnitTree compilationUnit,
            SourcePositions sourcePositions,
            MethodInvocationTree invocation,
            List<String> unresolved
    ) {
        long start = sourcePositions.getStartPosition(compilationUnit, invocation);
        long line = start >= 0 ? compilationUnit.getLineMap().getLineNumber(start) : -1;
        String location = line > 0 ? sourcePath + ":" + line : sourcePath.toString();

        String message = "Unresolved BPJ placeholder root(s) " + unresolved
                + " at " + location
                + ". Define these variables in scope or disable this validation with failOnUnresolved=false.";
        return new IllegalArgumentException(message);
    }

    private String escapeBraces(String template) {
        return template
                .replace("{{", ESCAPED_OPEN_TOKEN)
                .replace("}}", ESCAPED_CLOSE_TOKEN);
    }

    private String buildContextExpression(LinkedHashSet<String> roots) {
        if (roots.size() <= 10) {
            StringBuilder sb = new StringBuilder("java.util.Map.of(");
            int index = 0;
            for (String root : roots) {
                if (index > 0) {
                    sb.append(", ");
                }
                sb.append('"').append(root).append('"').append(", ").append(root);
                index++;
            }
            sb.append(')');
            return sb.toString();
        }

        StringBuilder sb = new StringBuilder("java.util.Map.ofEntries(");
        int index = 0;
        for (String root : roots) {
            if (index > 0) {
                sb.append(", ");
            }
            sb.append("java.util.Map.entry(")
                    .append('"').append(root).append('"')
                    .append(", ")
                    .append(root)
                    .append(')');
            index++;
        }
        sb.append(')');
        return sb.toString();
    }

    private String applyInsertions(String source, List<Insertion> insertions) {
        List<Insertion> ordered = new ArrayList<>(insertions);
        ordered.sort(Comparator.comparingInt(Insertion::position).reversed());

        StringBuilder sb = new StringBuilder(source);
        for (Insertion insertion : ordered) {
            sb.insert(insertion.position(), insertion.text());
        }
        return sb.toString();
    }

    private record ParseContext(
            CompilationUnitTree compilationUnit,
            SourcePositions sourcePositions
    ) {
    }

    private record Insertion(int position, String text) {
    }

    /**
     * Result of a source transformation operation.
     *
     * @param source transformed source
     * @param replacements number of inserted context arguments
     */
    public record TransformationResult(String source, int replacements) {
    }

    private static final class StringJavaFileObject extends SimpleJavaFileObject {
        private final String source;

        private StringJavaFileObject(Path sourcePath, String source) {
            super(sourcePath.toUri(), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
