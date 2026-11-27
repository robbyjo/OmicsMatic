/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NfcoreValidationPluginTest {
    private static Map<String, NextflowCapture.Item> payload() throws IOException {
        return payload(NfcoreValidationPlugin.files());
    }
    private static Map<String, NextflowCapture.Item> payload(Map<String, NextflowFiles.Pin> files) {
        var result = new TreeMap<String, NextflowCapture.Item>();
        for (String path : NfcoreValidationPlugin.directories(files.keySet())) result.put(path, new NextflowCapture.Item("directory", null, null, null, 0755));
        for (var row : files.entrySet()) result.put(row.getKey(), new NextflowCapture.Item("file", new NextflowFiles.Identity(row.getValue(), "fixture", "2026-11-27", 0644, 1), null, null, 0644));
        return result;
    }
    private static NextflowCapture.Snapshot snapshot(Map<String, NextflowCapture.Item> items) {
        return new NextflowCapture.Snapshot(List.of(new NextflowCapture.Root("r1", "/private/plugins", "plugins", "directory", items)), Map.of("plugins", "r1/plugins"));
    }
    @Test void admitsCompleteClosedOriginalPayloadAndReportedEngine() throws Exception {
        var items = payload(); assertEquals(47, NfcoreValidationPlugin.files().size());
        NfcoreValidationPlugin.validate(Map.of("engine_profile", NfcoreValidationPlugin.ENGINE), snapshot(items));
    }
    @Test void refusesMissingPluginInsteadOfDownloading() throws Exception {
        var capture = new NextflowCapture.Snapshot(List.of(), Map.of());
        assertThrows(NextflowRuntime.Unavailable.class, () -> NfcoreValidationPlugin.validate(Map.of("engine_profile", NfcoreValidationPlugin.ENGINE), capture));
    }
    @Test void refusesAnInferredOrOtherEngineEdition() throws Exception {
        var items = payload(); assertThrows(NextflowRuntime.Unavailable.class, () -> NfcoreValidationPlugin.validate(Map.of("engine_profile", "linux-x86_64-nextflow-22.10.6"), snapshot(items)));
    }
    @Test void rejectsChangedAndMissingJarMembers() throws Exception {
        var items = payload(); String jar = items.keySet().stream().filter(x -> x.endsWith(".jar")).findFirst().orElseThrow();
        var old = items.get(jar); items.put(jar, new NextflowCapture.Item("file", new NextflowFiles.Identity(new NextflowFiles.Pin(1, "0".repeat(64)), "fixture", "time", 0644, 1), null, null, 0644));
        assertThrows(IOException.class, () -> NfcoreValidationPlugin.validateItems(items));
        items.put(jar, old); items.remove(jar); assertThrows(IOException.class, () -> NfcoreValidationPlugin.validateItems(items));
    }
    @Test void rejectsAdditionalPluginsEmptyDirectoriesAndLinks() throws Exception {
        var items = payload(); items.put("extra-plugin", new NextflowCapture.Item("directory", null, null, null, 0755));
        assertThrows(IOException.class, () -> NfcoreValidationPlugin.validateItems(items)); items.remove("extra-plugin");
        items.put("link", new NextflowCapture.Item("symlink", null, "/external/plugin", "/external/plugin", -1)); assertThrows(IOException.class, () -> NfcoreValidationPlugin.validateItems(items));
    }
    @Test void rejectsPluginModifiedInWorkingCopy() throws Exception {
        var items = payload(); var working = new TreeMap<String, NextflowCapture.Item>(); items.forEach((k,v) -> working.put("resources/r1/plugins" + (k.isEmpty() ? "" : "/" + k), v));
        var snapshot = new NextflowCapture.Snapshot(List.of(new NextflowCapture.Root("r0", "/session/workspace", "workspace", "directory", working)), Map.of("working", "r0/workspace"));
        NfcoreValidationPlugin.validateWorkspace(snapshot, snapshot(items));
        assertNotNull(working.remove("resources/r1/plugins/nf-validation-1.1.3/classes/META-INF/MANIFEST.MF"));
        var changed = new NextflowCapture.Snapshot(List.of(new NextflowCapture.Root("r0", "/session/workspace", "workspace", "directory", working)), Map.of("working", "r0/workspace"));
        assertThrows(IOException.class, () -> NfcoreValidationPlugin.validateWorkspace(changed, snapshot(items)));
    }
    @Test void engineRequiresExactlyItsDeclaredEmptyMavenLockDirectory() throws Exception {
        var items = payload(NfcoreValidationEngine.files()); assertEquals(53, NfcoreValidationEngine.files().size());
        var before = new NextflowCapture.Snapshot(List.of(new NextflowCapture.Root("r2", "/private/engine", "engine", "directory", items)), Map.of("engine", "r2/engine"));
        assertThrows(IOException.class, () -> NfcoreValidationEngine.validate(before));
        items.put("repository/.locks", new NextflowCapture.Item("directory", null, null, null, 0755));
        var ready = new NextflowCapture.Snapshot(List.of(new NextflowCapture.Root("r2", "/private/engine", "engine", "directory", items)), Map.of("engine", "r2/engine"));
        NfcoreValidationEngine.validate(ready);
        items.put("repository/.locks/unexpected", new NextflowCapture.Item("directory", null, null, null, 0755));
        var changed = new NextflowCapture.Snapshot(List.of(new NextflowCapture.Root("r2", "/private/engine", "engine", "directory", items)), Map.of("engine", "r2/engine"));
        assertThrows(IOException.class, () -> NfcoreValidationEngine.validate(changed));
    }
    @Test void engineRejectsAnExtraOrMissingDependency() throws Exception {
        var items = payload(NfcoreValidationEngine.files()); items.put("repository/.locks", new NextflowCapture.Item("directory", null, null, null, 0755));
        String jar = items.keySet().stream().filter(x -> x.endsWith(".jar")).findFirst().orElseThrow(); items.remove(jar);
        var changed = new NextflowCapture.Snapshot(List.of(new NextflowCapture.Root("r2", "/private/engine", "engine", "directory", items)), Map.of("engine", "r2/engine"));
        assertThrows(IOException.class, () -> NfcoreValidationEngine.validate(changed));
    }
}
