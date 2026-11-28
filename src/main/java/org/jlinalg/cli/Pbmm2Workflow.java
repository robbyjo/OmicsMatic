/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** One new immutable session, complete roots at literal original paths and an unchanged original binary. */
final class Pbmm2Workflow {
    private Pbmm2Workflow() { }
    static Path run(Path supplied, Path requestFile, Path replay) throws Exception {
        if (!System.getProperty("os.name").equals("Linux") || !Set.of("amd64", "x86_64").contains(System.getProperty("os.arch"))) throw new NextflowProcess.Unavailable("Original pbmm2 execution requires x86_64 Linux Java and permitted unprivileged containers, including WSL. Portable --verify is available on Windows");
        Path root = supplied.toAbsolutePath().normalize(); Pbmm2Request.path(root.toString());
        if (root.getParent() == null || !root.getParent().toRealPath().equals(root.getParent())) throw new IOException("Use a new canonical owned pbmm2 session with an existing physical parent");
        Map<String, Object> previous = replay == null ? null : Pbmm2Bundle.verify(replay);
        if (replay != null) requestFile = replay.toAbsolutePath().normalize().resolve("request.json");
        Files.createDirectory(root); String phase = "capture request and producer";
        try {
            copy(requestFile, root.resolve("request.json"), 4194304);
            var request = Pbmm2Request.read(root.resolve("request.json"));
            Path jar = Path.of(Pbmm2Workflow.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            if (!Files.isRegularFile(jar) || !jar.getFileName().toString().endsWith(".jar")) throw new NextflowProcess.Unavailable("Use the packaged self-contained producing JAR; loose development classes do not qualify execution");
            Pin implementation = pin(jar, 1000000000L, () -> { }); copy(jar, root.resolve("adapter.jar"), 1000000000L);
            var context = new LinkedHashMap<String, Object>(); context.put("schema_version", 1); context.put("producer_root", root.toString()); context.put("producer", implementation); context.put("replay_of_manifest", previous == null ? null : previous.get("manifest")); writeJson(root.resolve("context.json"), context);
            Files.writeString(root.resolve("WARNINGS.md"), Pbmm2ToolsCli.warnings(request.version()), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            phase = "validate complete resource namespaces and exact prerequisites";
            for (String external : List.of(request.home(), request.containerHome(), request.image())) disjoint(root.toString(), external);
            for (var resource : request.resources()) {
                disjoint(root.toString(), resource.original());
                for (String external : List.of(request.home(), request.containerHome(), request.image())) disjoint(resource.original(), external);
                if (Set.of("/tmp", "/dev", "/proc", "/sys", "/etc", "/usr", "/bin", "/lib", "/lib64", "/run").stream().anyMatch(system -> Pbmm2Request.inside(system, resource.original()) || Pbmm2Request.inside(resource.original(), system))) throw new NextflowProcess.Unavailable("A declared input root shadows required OS/private temporary paths: " + resource.original() + "; use an allowed complete data location or retain the skip");
            }
            Path binary = Path.of(request.home()).resolve("bin/pbmm2"), image = Path.of(request.image());
            require(binary, "exact licensed original pbmm2 binary"); require(image, "exact OS-only carrier image");
            if (!pin(binary, 1000000000L, () -> { }).equals(Pbmm2Profiles.executable(request.version())) || !pin(image, 5000000000L, () -> { }).equals(Pbmm2Profiles.image())) throw new IOException("Declared original pbmm2 binary or image differs from the exact qualified edition");
            Path runtime = Files.createDirectory(root.resolve("runtime")); copy(binary, runtime.resolve("pbmm2"), 1000000000L); Files.setPosixFilePermissions(runtime.resolve("pbmm2"), PosixFilePermissions.fromString("r-xr-xr-x")); copy(image, runtime.resolve("carrier.sif"), 5000000000L);
            writeJson(root.resolve("image-set.json"), Map.of("schema_version", 1, "images", Map.of(Pbmm2Profiles.imageUri(), runtime.resolve("carrier.sif").toString())));
            var containers = new NfcoreContainerSet(Path.of(request.containerHome()), root.resolve("image-set.json"), Files.createDirectory(root.resolve("runtime-health")), request.process(), Pbmm2Profiles.RESOURCE + "IMAGES.json", Pbmm2Profiles.IMAGES);
            phase = "check exact container backend and image capability"; containers.health();
            phase = "capture every original input and separate mutable original-path copies";
            var captures = Pbmm2Capture.prepare(root, request, replay == null ? null : replay.toAbsolutePath().normalize());
            Files.createDirectory(root.resolve("output")); Files.createDirectory(root.resolve("private-tmp"));
            if (!request.cwd().equals("OUTPUT")) directory(staged(root, request, request.cwd()));
            require(staged(root, request, request.reference()), "complete reference/index input");
            if (request.reads() != null) require(staged(root, request, request.reads()), "complete reads/native resource input");
            NextflowProcess.Check guard = () -> {
                containers.verify();
                if (!pin(binary, 1000000000L, () -> { }).equals(Pbmm2Profiles.executable(request.version())) || !pin(runtime.resolve("pbmm2"), 1000000000L, () -> { }).equals(Pbmm2Profiles.executable(request.version())) || !pin(jar, 1000000000L, () -> { }).equals(implementation) || !pin(root.resolve("adapter.jar"), 1000000000L, () -> { }).equals(implementation)) throw new IOException("Original binary or producer changed during analysis");
                Pbmm2Capture.verify(root, request, captures, true);
            };
            var environment = new LinkedHashMap<>(containers.environment()); environment.put("APPTAINERENV_TMPDIR", "/tmp");
            phase = "check literal original multi-provider version";
            NfcoreProcess.executeSession(command(root.toString(), request, true), environment, root.resolve("version-process"), root, root, request.process(), guard, () -> { });
            if (!utf8(bytes(root.resolve("version-process/process.stdout"), 65536)).equals(text(Pbmm2Profiles.edition(request.version()).get("literal_multi_version_output")))) throw new IOException("Original multi-provider version output differs; no suffix stripping or edition substitution");
            phase = "execute complete selected original operation";
            writeJson(root.resolve("command.json"), Map.of("version", command(root.toString(), request, true), "science", command(root.toString(), request, false), "original_native_resource_locator_strings_rewritten", false));
            NfcoreProcess.executeSession(command(root.toString(), request, false), environment, root.resolve("science-process"), root, root, request.process(), guard, () -> { });
            phase = "recompute all output BAM CIGAR/tag contracts"; guard.verify();
            writeJson(root.resolve("audit.json"), Pbmm2Audit.run(root.resolve("output"), request));
            phase = "portable semantic verification and atomic completion"; guard.verify(); Pbmm2Bundle.finish(root); guard.verify();
            return root;
        } catch (Exception | LinkageError | OutOfMemoryError failure) {
            int code = exit(failure);
            try {
                if (Files.exists(root.resolve("COMPLETED.json"))) Files.move(root.resolve("COMPLETED.json"), root.resolve("COMPLETED-PREFINAL.json"));
                var stack = new StringWriter(); failure.printStackTrace(new PrintWriter(stack)); Files.writeString(root.resolve("failure.stacktrace"), stack.toString(), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
                writeJson(root.resolve("FAILURE.json"), Map.of("status", code == 3 ? "SKIP3" : "FAIL2", "phase", phase, "error", failure.toString(), "exit_code", code, "required_action", Pbmm2ToolsCli.ACTION));
                if (Files.exists(root.resolve("WARNINGS.md"))) Files.move(root.resolve("WARNINGS.md"), root.resolve("WARNINGS-PREFINAL.md"));
                Files.writeString(root.resolve("WARNINGS.md"), "# " + (code == 3 ? "SKIP3 - ANALYSIS SKIPPED" : "FAIL2 - NO RESULT ADMITTED") + "\n\n" + phase + ": " + failure + "\n\n" + Pbmm2ToolsCli.ACTION + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            } catch (IOException diagnostic) { failure.addSuppressed(diagnostic); }
            if (code == 3) throw new NextflowProcess.Unavailable("During " + phase + ": " + failure + "; inspect " + root.resolve("FAILURE.json"));
            throw new IOException("During " + phase + ": " + failure + "; inspect " + root.resolve("FAILURE.json"), failure);
        }
    }
    static void disjoint(String a, String b) throws IOException { if (Pbmm2Request.inside(a, b) || Pbmm2Request.inside(b, a)) throw new IOException("Owned session, full data roots and exact external runtime/image namespaces must be disjoint: " + a + " / " + b); }
    static Path staged(Path root, Pbmm2Request.Request request, String original) throws IOException {
        for (var resource : request.resources()) if (Pbmm2Request.inside(original, resource.original())) return root.resolve("staged").resolve(resource.id()).resolve(original.equals(resource.original()) ? "" : original.substring(resource.original().length() + 1));
        throw new IOException("Uncaptured original native resource");
    }
    static void require(Path path, String what) throws IOException { if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) throw new NextflowProcess.Unavailable("Missing " + what + ": " + path + "; complete the named permitted setup/input separately or retain the skipped analysis"); pin(path, 1000000000000L, () -> { }); }
    static void copy(Path from, Path to, long maximum) throws IOException {
        require(from, "required permitted file"); Pin before = pin(from, maximum, () -> { }); Files.copy(from, to);
        if (!before.equals(pin(from, maximum, () -> { })) || !before.equals(pin(to, maximum, () -> { }))) throw new IOException("Original file changed during capture: " + from);
    }
    static List<String> command(String root, Pbmm2Request.Request r, boolean version) throws IOException {
        var result = new ArrayList<>(List.of(r.containerHome() + "/bin/apptainer", "exec", "--userns", "--pid", "--containall", "--no-home", "--cleanenv", "--no-mount", "cwd,home,hostfs,bind-paths,tmp", "--pwd", r.cwd().equals("OUTPUT") ? root + "/output" : r.cwd(), "-B", root + ":" + root + ":rw", "-B", root + "/private-tmp:/tmp:rw"));
        for (var resource : r.resources()) result.addAll(List.of("-B", root + "/staged/" + resource.id() + ":" + resource.original() + ":rw"));
        result.addAll(List.of(root + "/runtime/carrier.sif", root + "/runtime/pbmm2"));
        if (version) { result.add("--version"); return List.copyOf(result); }
        result.add(r.operation().equals("INDEX") ? "index" : "align"); result.addAll(List.of("--preset", r.preset(), "-j", Integer.toString(r.threads()), "--log-level", r.logging()));
        result.addAll(r.parameterArguments());
        if (r.operation().equals("ALIGN")) result.addAll(List.of("--report-json", root + "/output/mapping-report.json"));
        result.add(r.reference()); if (r.reads() != null) result.add(r.reads()); result.add(root + "/output/" + outputName(r.outputKind()));
        return List.copyOf(result);
    }
    static String outputName(String kind) throws IOException { return switch (kind) {
        case "MMI" -> "result.mmi"; case "BAM" -> "result.bam"; case "ALIGNMENTSET" -> "result.alignmentset.xml";
        case "CONSENSUSALIGNMENTSET" -> "result.consensusalignmentset.xml"; case "TRANSCRIPTALIGNMENTSET" -> "result.transcriptalignmentset.xml"; case "DATASTORE" -> "result.json";
        default -> throw new IOException("Unknown original output kind"); }; }
    static void verifyCommand(Path root, Pbmm2Request.Request request, String producer) throws IOException {
        if (!NextflowJson.json(Map.of("version", command(producer, request, true), "science", command(producer, request, false), "original_native_resource_locator_strings_rewritten", false)).equals(NextflowJson.json(json(root.resolve("command.json"), 4194304)))) throw new IOException("Complete original argument/path binding differs");
        for (String process : List.of("version-process", "science-process")) {
            var ledger = object(json(root.resolve(process + "/process.json"), 67108864)); var argv = strings(ledger.get("command")); var expected = command(producer, request, process.equals("version-process"));
            var invocation = new ArrayList<>(List.of("/usr/bin/setsid", "/usr/bin/prlimit", "--as=" + request.process().addressSpaceBytes(), "--cpu=" + request.process().cpuSeconds(), "--fsize=" + request.process().fileBytes(), "--nofile=" + request.process().openFiles(), "--")); invocation.addAll(expected);
            if (!argv.equals(invocation) || !NextflowJson.json(request.process()).equals(NextflowJson.json(ledger.get("limits"))) || !producer.equals(ledger.get("working_directory")) || !producer.equals(ledger.get("workspace_root"))) throw new IOException("Original executed process/capacity binding differs");
        }
        if (!utf8(bytes(root.resolve("version-process/process.stdout"), 65536)).equals(text(Pbmm2Profiles.edition(request.version()).get("literal_multi_version_output")))) throw new IOException("Saved original version provider differs");
    }
    static int exit(Throwable failure) { return failure instanceof NextflowProcess.Unavailable || failure instanceof AccessDeniedException ? 3 : 2; }
}
