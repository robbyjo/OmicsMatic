/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

/** Syntax inventory only: no attribution, compilation, execution or semantic equivalence claim. */
public final class SourceMembers {
    private static String file;
    private static String b64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
    private static void emit(String kind, String key, String canonical) {
        System.out.println(file + "\t" + kind + "\t" + b64(key) + "\t" + b64(canonical));
    }
    private static String methodKey(String owner, MethodTree method) {
        return owner + "/" + method.getName() + "(" + method.getParameters().stream()
            .map(p -> p.getType().toString()).collect(Collectors.joining(",")) + ")";
    }
    private static void type(String parent, ClassTree type) {
        String owner = parent + "/" + type.getSimpleName();
        emit("TYPE", owner, type.getKind() + " " + type.getModifiers() + " " + type.getSimpleName()
            + " " + type.getTypeParameters() + " extends " + type.getExtendsClause()
            + " implements " + type.getImplementsClause() + " permits " + type.getPermitsClause());
        List<String> order = new ArrayList<>();
        int blocks = 0;
        for (Tree member : type.getMembers()) {
            if (member instanceof MethodTree method) {
                String key = methodKey(owner, method);
                order.add("METHOD:" + key);
                emit("METHOD", key, method.toString());
            } else if (member instanceof VariableTree field) {
                String key = owner + "/" + field.getName();
                order.add("FIELD:" + key);
                emit("FIELD", key, field.toString());
            } else if (member instanceof ClassTree nested) {
                order.add("TYPE:" + owner + "/" + nested.getSimpleName());
                type(owner, nested);
            } else if (member instanceof BlockTree block) {
                String key = owner + "/initializer-" + (++blocks);
                order.add("BLOCK:" + key);
                emit("BLOCK", key, block.toString());
            } else {
                order.add(member.getKind() + ":" + member);
                emit("OTHER", owner + "/other-" + order.size(), member.toString());
            }
        }
        emit("MEMBER_ORDER", owner, String.join("\n", order));
    }
    public static void main(String[] args) throws Exception {
        if (Runtime.version().feature() != 21) throw new IllegalStateException("Explicit JDK21 parser required");
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("JDK compiler is unavailable");
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            var paths = List.of(args).stream().map(Path::of).toList();
            var task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                List.of("-proc:none", "--release", "21"), null, manager.getJavaFileObjectsFromPaths(paths));
            for (CompilationUnitTree unit : task.parse()) {
                file = unit.getSourceFile().toUri().toASCIIString();
                emit("UNIT", "unit", String.valueOf(unit.getPackageName()) + "\n" + unit.getImports()
                    + "\n" + unit.getPackageAnnotations() + "\n" + unit.getModule());
                // Complete canonical unit preserves syntax not represented by a
                // member key. It is not used as a semantic-equivalence oracle.
                emit("COMPLETE_UNIT", "complete-unit", unit.toString());
                for (Tree declaration : unit.getTypeDecls()) {
                    if (declaration instanceof ClassTree type) type(String.valueOf(unit.getPackageName()), type);
                    else emit("TOP_LEVEL_OTHER", declaration.getKind().toString(), declaration.toString());
                }
            }
        }
        for (var diagnostic : diagnostics.getDiagnostics()) {
            System.err.println(diagnostic);
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) throw new IllegalArgumentException("Source syntax error");
        }
    }
}
