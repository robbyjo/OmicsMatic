/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Seals a deliberately completed fixture, never a consumer's read or a tampered test input. */
final class StudyTestSources {
    private StudyTestSources() { }
    static void seal(Path directory,String type,String id,String hypothesis)throws IOException {
        var hashes=new LinkedHashMap<String,Object>();
        try(var files=Files.walk(directory)) {
            for(Path file:files.filter(Files::isRegularFile).filter(p->!p.getFileName().toString().equals("manifest.yaml")).sorted().toList())
                hashes.put(directory.relativize(file).toString().replace('\\','/'),FollowupSupport.hash(file));
        }
        StudyPublicEvidence.write(directory.resolve("manifest.yaml"),Map.of("operation_type",type,"operation_id",id,"hypothesis",hypothesis,"state","complete","output_sha256",hashes));
    }
}
