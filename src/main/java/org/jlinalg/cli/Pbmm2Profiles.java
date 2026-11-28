/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import static org.jlinalg.cli.NextflowFiles.*;

/** Exact original binary editions and an explicitly selected OS-only image carrier. */
final class Pbmm2Profiles {
    private Pbmm2Profiles() { }
    static final String RESOURCE = "/org/jlinalg/omics/pbmm2/";
    static final Pin PROFILE = new Pin(20463, "a87e988b23535bcc594322dfb085fd4e8e35c0a6977466704ee29ddd5987db0e");
    static final Pin IMAGES = new Pin(443, "c27cd0a678ea387993f21d65bfe4da73b54f1f60c38f6bb532643249d7d76f4a");

    static byte[] resource(String name, Pin expected) throws IOException {
        try (var stream = Pbmm2Profiles.class.getResourceAsStream(RESOURCE + name)) {
            if (stream == null) throw new NextflowProcess.Unavailable("Required packaged pbmm2 profile is absent; use the complete current producing JAR or retain the skipped analysis");
            byte[] raw = stream.readNBytes(Math.toIntExact(expected.bytes() + 1));
            if (!new Pin(raw.length, sha(raw)).equals(expected)) throw new IOException("Changed original pbmm2 profile: " + name);
            return raw;
        }
    }
    static Map<?, ?> profile() throws IOException {
        var p = object(NextflowParameters.parse(utf8(resource("PROFILES.json", PROFILE)), 1048576, 100000));
        integer(p.get("schema_version"), 1, 1);
        if (!object(p.get("versions")).keySet().equals(Set.of("1.13.1", "1.17.0"))) throw new IOException("Exact pbmm2 edition census differs");
        return p;
    }
    static Map<?, ?> edition(String version) throws IOException {
        var versions = object(profile().get("versions"));
        if (!versions.containsKey(version)) throw new NextflowProcess.Unavailable("Exact pbmm2 edition is not qualified: " + version + "; another binary is never substituted");
        return object(versions.get(version));
    }
    static Pin executable(String version) throws IOException { return fingerprint(edition(version).get("executable"), 1000000000L); }
    static String imageUri() throws IOException { return text(profile().get("image_uri")); }
    static Pin image() throws IOException {
        var p = object(NextflowParameters.parse(utf8(resource("IMAGES.json", IMAGES)), 1048576, 100000));
        var images = object(p.get("images"));
        if (!images.keySet().equals(Set.of(imageUri()))) throw new IOException("Original pbmm2 OS-carrier image census differs");
        return fingerprint(object(images.get(imageUri())).get("pin"), 5000000000L);
    }
    static Map<String, Map<?, ?>> options(String version, String operation) throws IOException {
        var rows = list(object(profile().get("source_option_definitions")).get(version));
        var result = new LinkedHashMap<String, Map<?, ?>>();
        String file = operation.equals("INDEX") ? "/IndexSettings.cpp" : "/AlignSettings.cpp";
        for (Object item : rows) {
            var row = object(item);
            if (!text(row.get("source")).endsWith(file)) continue;
            for (String alias : strings(row.get("names"))) if (result.putIfAbsent(alias, row) != null) throw new IOException("Repeated original pbmm2 option alias");
        }
        if (result.isEmpty()) throw new IOException("Missing exact original pbmm2 option definitions");
        // This option is explicitly documented in the later binary release; public program code stopped updating.
        if (version.equals("1.17.0") && operation.equals("ALIGN")) result.put("include-fail-reads", Map.of("names", List.of("include-fail-reads"), "type", "bool", "default", false, "source", "documented binary1.14+ option"));
        return result;
    }
}
