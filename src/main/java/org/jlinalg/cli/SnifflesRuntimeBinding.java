/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.SnifflesWorkflowFiles.*;

/** Fresh complete binding census before a batch, with immutable provenance rechecks. */
final class SnifflesRuntimeBinding {
    private SnifflesRuntimeBinding() { }
    record Bound(Path manifest, Pin manifestPin, Path root, Map<?, ?> document, Map<?, ?> host) {
        void verify() throws IOException {
            require(manifestPin.equals(pin(manifest, 67108864, () -> {})), "Original runtime manifest changed");
            evidence(manifest.getParent(), object(document.get("binding_files")));
            SnifflesHost.verifyFiles(host, root);
        }
    }
    static Bound prepare(Path manifest, Pin declared, Path probes, SnifflesProcess.Check integrity) throws IOException {
        try {
            SnifflesRuntimeProfile.platform(); regular(manifest);
            require(declared.equals(pin(manifest, 67108864, () -> {})), "Runtime manifest differs from its declared pin");
            var document = read(manifest, 67108864);
            require(text(document.get("status")).equals("PASS_BOUND_ORIGINAL_SNIFFLES_RUNTIME_ONLY")
                    && Boolean.FALSE.equals(document.get("source_namespace_patched")) && Boolean.FALSE.equals(document.get("analysis_installed_software"))
                    && Boolean.FALSE.equals(document.get("startup_hooks_executed")) && text(document.get("python_hash_seed_effective")).equals("0"), "Require a successful original runtime binding with guarded effective startup");
            require(fingerprint(SnifflesRuntimeProfile.resource("profiles.json")).equals(fingerprint(document.get("profile_pin"), 8388608))
                    && fingerprint(SnifflesRuntimeProfile.resource("bootstrap.py")).equals(fingerprint(document.get("bootstrap_pin"), 8388608)), "Exact packaged source/startup profile differs from this binding");
            require(object(document.get("versions")).keySet().equals(Set.of("1.0.7", "2.0.2", "2.0.7", "2.3.2", "2.4")), "Binding lacks a complete original edition probe population");
            evidence(manifest.getParent(), object(document.get("binding_files")));
            Path root = Path.of(text(document.get("runtime_root"))); SnifflesRuntimeProfile.verify(root, () -> {});
            var actual = SnifflesHost.capture(root, probes, integrity);
            require(actual.equals(object(document.get("host_binding"))), "Original runtime host/library/search bindings changed; prepare a NEW binding");
            var result = new Bound(manifest, declared, root, document, actual); result.verify(); integrity.verify(); return result;
        } catch (SnifflesProcess.UnavailableDependency unavailable) { throw unavailable; }
        catch (IOException failure) { throw new SnifflesProcess.UnavailableDependency("Prepared original runtime unavailable or changed: " + failure + ". " + SnifflesRuntimeProfile.ACTION); }
    }
    private static void evidence(Path root, Map<?, ?> expected) throws IOException {
        require(expected.size() <= 10000, "Binding evidence population capacity exceeded"); var found = new HashSet<String>();
        try (var tree = Files.walk(root)) {
            for (Path path : tree.toList()) {
                require(!Files.isSymbolicLink(path), "Original binding evidence contains a link");
                if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) continue;
                String name = root.relativize(path).toString().replace('\\', '/'); if (name.equals("COMPLETED.json")) continue;
                require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && expected.containsKey(name) && found.add(name), "Unexpected binding evidence member: " + path);
                require(fingerprint(expected.get(name), 67108864).equals(pin(path, 67108864, () -> {})), "Original binding evidence changed: " + name);
            }
        }
        require(found.equals(expected.keySet()), "Original binding evidence population changed");
    }
}
