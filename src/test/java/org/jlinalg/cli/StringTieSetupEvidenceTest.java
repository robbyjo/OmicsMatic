/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CancellationException;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class StringTieSetupEvidenceTest {
    @TempDir Path temporary;
    private static final StringTieSetupEvidence.Limits LIMITS = StringTieSetupEvidence.Limits.defaults();
    @ParameterizedTest @ValueSource(strings={"install-1.3.0", "install-1.3.6", "install-2.1.2", "install-2.1.7", "install-2.2.1", "install-3.0.0", "install-3.0.3", "recheck-1.3.0", "recheck-1.3.6", "recheck-2.1.2", "recheck-2.1.7", "recheck-2.2.1", "recheck-3.0.0", "recheck-3.0.3", "download-report", "standalone-report"})
    void originalCompleteReceiptsArePortableWithoutOpeningProducerPaths(String name)throws Exception {
        Path root = fixture(name); var receipt = StringTieSetupEvidence.read(root, LIMITS, () -> { });
        assertNotEquals(root.toString(), receipt.producerReport()); assertEquals("linux-x86_64-stringtie-" + receipt.version(), receipt.profile());
        assertTrue(receipt.producerHome().startsWith("/var/tmp/")); receipt.verify(LIMITS, () -> { });
        Files.writeString(root.resolve("unexpected"), "extra"); assertThrows(IOException.class, () -> receipt.verify(LIMITS, () -> { }));
    }
    @ParameterizedTest @ValueSource(strings={"INCOMPLETE.json", "FAILURE.json", "unexpected.txt", "extra/nested.txt"})
    void extraOrPartialNamespaceCannotBecomeSuccess(String name)throws Exception {
        Path root = fixture("install-3.0.3"), target = root.resolve(name); Files.createDirectories(target.getParent()); Files.writeString(target, "retained");
        assertThrows(IOException.class, () -> StringTieSetupEvidence.read(root, LIMITS, () -> { }));
    }
    @ParameterizedTest @ValueSource(strings={"request.json", "staging.json", "WARNINGS.md", "manifest.json", "COMPLETED.json"})
    void everyMissingOriginalMemberFails(String name)throws Exception {
        Path root = fixture("install-3.0.3"); Files.delete(root.resolve(name)); assertThrows(IOException.class, () -> StringTieSetupEvidence.read(root, LIMITS, () -> { }));
    }
    @Test void selfConsistentPermissionAndProfileRewritesStillFail()throws Exception {
        Path root = fixture("install-3.0.3"); var request = mutable(root.resolve("request.json")); request.put("allow_install", false); replace(root.resolve("request.json"), request); reseal(root);
        assertThrows(IOException.class, () -> StringTieSetupEvidence.read(root, LIMITS, () -> { }));
        Path other = fixture("recheck-3.0.3"); var manifest = mutable(other.resolve("manifest.json")); manifest.put("version", "1.3.0"); replace(other.resolve("manifest.json"), manifest); reseal(other);
        assertThrows(IOException.class, () -> StringTieSetupEvidence.read(other, LIMITS, () -> { }));
    }
    @Test void unsafeOpaqueProducerPathAndFabricatedAdmissionFail()throws Exception {
        Path root = fixture("install-1.3.0"); var request = mutable(root.resolve("request.json")); request.put("cache", "/tmp/../untrusted"); replace(root.resolve("request.json"), request); reseal(root);
        assertThrows(IOException.class, () -> StringTieSetupEvidence.read(root, LIMITS, () -> { }));
        Path other = fixture("install-2.1.7"); var complete = mutable(other.resolve("COMPLETED.json")); complete.put("scientific_runtime_admitted", true); replace(other.resolve("COMPLETED.json"), complete);
        assertThrows(IOException.class, () -> StringTieSetupEvidence.read(other, LIMITS, () -> { }));
    }
    @Test void changedReceiptCancellationAndCapacityNeverReturnAReceipt()throws Exception {
        Path root = fixture("install-3.0.3"); var receipt = StringTieSetupEvidence.read(root, LIMITS, () -> { }); Files.writeString(root.resolve("WARNINGS.md"), "changed");
        assertThrows(IOException.class, () -> receipt.verify(LIMITS, () -> { }));
        assertThrows(CancellationException.class, () -> StringTieSetupEvidence.read(root, LIMITS, () -> { throw new CancellationException("controlled"); }));
        assertThrows(IOException.class, () -> StringTieSetupEvidence.read(root, new StringTieSetupEvidence.Limits(1024, 1024, 4), () -> { }));
        assertThrows(IOException.class, () -> StringTieSetupEvidence.profile("linux-x86_64-stringtie-latest"));
    }
    private Path fixture(String name)throws Exception {
        Path root = temporary.resolve(name).toAbsolutePath(); Files.createDirectory(root);
        try (var source = getClass().getResourceAsStream("stringtie-runtime-receipts.zip")) {
            assertNotNull(source); try (var zip = new ZipInputStream(source)) { for (var e = zip.getNextEntry(); e != null; e = zip.getNextEntry()) if (e.getName().startsWith(name + "/")) Files.write(root.resolve(e.getName().substring(name.length() + 1)), zip.readAllBytes()); }
        }
        return root;
    }
    private static Map<String, Object> mutable(Path file)throws Exception { var result = new TreeMap<String, Object>(); NextflowFiles.object(NextflowFiles.json(file, 1048576)).forEach((k,v) -> result.put(k.toString(), v)); return result; }
    private static void replace(Path file, Object value)throws Exception { Files.delete(file); NextflowFiles.writeJson(file, value); }
    private static void reseal(Path root)throws Exception {
        var manifest = mutable(root.resolve("manifest.json")); var files = new TreeMap<String, Object>();
        for (String name : List.of("request.json", "staging.json", "WARNINGS.md")) if (Files.exists(root.resolve(name))) files.put(name, MemeRuntimeFiles.pin(NextflowFiles.pin(root.resolve(name), 1048576, () -> { })));
        manifest.put("files", files); replace(root.resolve("manifest.json"), manifest);
        var completion = mutable(root.resolve("COMPLETED.json")); completion.put("manifest", MemeRuntimeFiles.pin(NextflowFiles.pin(root.resolve("manifest.json"), 1048576, () -> { }))); replace(root.resolve("COMPLETED.json"), completion);
    }
}
