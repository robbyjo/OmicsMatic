/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.jlinalg.cli.SnifflesWorkflowFiles.*;

/** Pre-execution original-runtime binding. Does not install software or admit SV analysis. */
public final class SnifflesRuntimeCli {
    private SnifflesRuntimeCli() { }
    public static void main(String[] args) { System.exit(run(args)); }
    static final SnifflesProcess.Limits HEALTH = new SnifflesProcess.Limits(60000, 8589934592L, 60, 16777216, 256, 134217728, 1024, 16, 256, 2147483648L, 50);
    public static int run(String[] args) {
        Path out = null; int code = 2; boolean preparation = false;
        var state = new LinkedHashMap<String, Object>(); state.put("status", "FAIL2_SNIFFLES_RUNTIME_BINDING");
        state.put("analysis_admitted", false); state.put("analysis_installed_software", false);
        try {
            require(args.length == 4 && args[0].equals("--root") && args[2].equals("--out"), "Use --root EXPORTED_ABSOLUTE_RUNTIME --out NEW_ABSOLUTE_BINDING_DIRECTORY");
            Path root = Path.of(args[1]); Path requested = Path.of(args[3]);
            require(requested.isAbsolute() && requested.normalize().equals(requested) && requested.getParent() != null && requested.getParent().equals(requested.getParent().toRealPath()), "Require a NEW absolute binding directory with canonical parent");
            require(!requested.startsWith(root) && !root.startsWith(requested), "Runtime and binding paths must be disjoint");
            out = Files.createDirectory(requested); warnings(out, state); preparation = true;
            SnifflesRuntimeProfile.verify(root, () -> {});
            Path profile = out.resolve("profile.original.json"), bootstrap = out.resolve("bootstrap.py");
            Files.write(profile, SnifflesRuntimeProfile.resource("profiles.json"), StandardOpenOption.CREATE_NEW);
            Files.write(bootstrap, SnifflesRuntimeProfile.resource("bootstrap.py"), StandardOpenOption.CREATE_NEW);
            var pins = Map.of(profile, pin(profile, 8388608, () -> {}), bootstrap, pin(bootstrap, 8388608, () -> {}));
            SnifflesProcess.Check captured = () -> { for (var entry : pins.entrySet()) require(entry.getValue().equals(pin(entry.getKey(), 8388608, () -> {})), "Packaged binding helper/profile changed"); };
            var host = SnifflesHost.capture(root, out.resolve("host"), captured);
            Path bound = out;
            SnifflesProcess.Check check = () -> { captured.verify(); cancel(bound); SnifflesHost.verifyFiles(host, root); };
            Path health = Files.createDirectory(out.resolve("health"));
            var healthProcess = SnifflesProcess.executeOwned(python(root, bootstrap, "health", health, List.of()), SnifflesRuntimeProfile.environment(), health.resolve("process"), HEALTH, check, () -> {}, List.of());
            require(healthProcess.exitCode() == 0 && text(read(health.resolve("HEALTH.json"), 1048576).get("status")).equals("PASS_BOUND_ORIGINAL_DEPENDENCY_IMPORTS_ONLY"), "Original dependency import health failed");
            var versions = new LinkedHashMap<String, Object>();
            for (String edition : List.of("1.0.7", "2.0.2", "2.0.7", "2.3.2", "2.4")) {
                Path work = Files.createDirectory(out.resolve("version-" + edition));
                List<String> command = edition.equals("1.0.7") ? List.of(root.resolve("legacy-sniffles").toString(), "--version") : python(root, bootstrap, "version", work, List.of("v" + edition));
                var result = SnifflesProcess.executeOwned(command, SnifflesRuntimeProfile.environment(), work.resolve("process"), HEALTH, check, () -> {}, List.of());
                String first = utf8(bytes(result.stdout(), 1048576)), second = utf8(bytes(result.stderr(), 1048576));
                require(result.exitCode() == 0 && first.contains(edition) && second.isEmpty(), "Original edition probe failed or reported an error: " + edition);
                versions.put(edition, Map.of("stdout", pin(result.stdout(), 1048576, () -> {}), "stderr", pin(result.stderr(), 1048576, () -> {})));
            }
            SnifflesRuntimeProfile.verify(root, () -> {}); check.verify();
            state.put("status", "PASS_BOUND_ORIGINAL_SNIFFLES_RUNTIME_ONLY"); state.put("runtime_root", root.toString());
            state.put("profile_pin", pins.get(profile)); state.put("bootstrap_pin", pins.get(bootstrap)); state.put("host_binding", host); state.put("versions", versions);
            state.put("source_namespace_patched", false); state.put("startup_hooks_executed", false); state.put("python_hash_seed_effective", "0");
            state.put("owned_live_after_cleanup", List.of()); code = 0;
            warnings(out, state);
            var evidence = new LinkedHashMap<String, Object>();
            try (var tree = Files.walk(out)) {
                for (Path file : tree.sorted().toList()) {
                    require(!Files.isSymbolicLink(file), "Binding evidence contains a link");
                    if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) continue;
                    require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS), "Binding evidence contains a nonregular node");
                    evidence.put(out.relativize(file).toString().replace('\\', '/'), pin(file, 67108864, () -> {}));
                }
            }
            state.put("binding_files", evidence);
        } catch (SnifflesProcess.UnavailableDependency unavailable) { state.put("status", "SKIP3_SNIFFLES_RUNTIME_UNAVAILABLE"); state.put("error", unavailable.toString()); code = 3; }
        catch (Exception failure) { state.put("error", failure.toString()); if (preparation) { state.put("status", "SKIP3_SNIFFLES_RUNTIME_UNAVAILABLE"); code = 3; } }
        finally {
            if (out != null) try { warnings(out, state); write(out.resolve("COMPLETED.json"), state); }
            catch (IOException failure) { System.err.println("FAIL2: binding receipt unavailable: " + failure); code = 2; }
            if (code != 0) System.err.println(state.get("status") + ": " + state.get("error") + "\nACTION: " + SnifflesRuntimeProfile.ACTION);
        }
        if (code == 0) System.out.println(state.get("status") + ": " + out);
        return code;
    }
    static List<String> python(Path root, Path bootstrap, String mode, Path work, List<String> arguments) {
        var result = new ArrayList<>(List.of(root.resolve("python").toString(), "-B", "-P", "-s", "-S", "-X", "pycache_prefix=" + work.resolve("never-created-bytecode-cache"), bootstrap.toString(), mode, root.toString(), work.toString()));
        result.addAll(arguments); return result;
    }
    static void cancel(Path out) throws IOException { require(!Thread.currentThread().isInterrupted() && !Files.exists(out.resolve("CANCEL_REQUESTED"), LinkOption.NOFOLLOW_LINKS), "Cancellation requested; no result admitted"); }
    private static void warnings(Path out, Map<String, Object> state) throws IOException {
        String text = "# " + state.get("status") + "\n\n" + SnifflesRuntimeProfile.ACTION + "\n\n" +
            "Binding performs fixed library imports and original version probes only. It does not call SVs or certify a paper. Python startup has an explicit standard-library/private-site path, -S/-s/-P/-B, a fresh nonexistent cache prefix and effective PYTHONHASHSEED=0; no .pth/sitecustomize/usercustomize execution is permitted. Original scientific namespaces remain unchanged. The ELF census covers inspected images, their declared dependencies and search candidates; it is not an OS security sandbox. No external SNF/pickle data are accepted.\n\n";
        if (state.containsKey("error")) text += "**ACTION REQUIRED: " + state.get("error") + "**\n\nDependent calling is skipped. Preserve this attempt and prepare NEW permitted paths after correcting the named error.\n";
        Files.writeString(out.resolve("WARNINGS.md"), text, StandardCharsets.UTF_8);
    }
}
