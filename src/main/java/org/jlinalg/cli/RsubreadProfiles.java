/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.RsubreadSupport.*;

/** Immutable original helper modules and explicitly selected known Windows runtime families. */
final class RsubreadProfiles {
    private RsubreadProfiles() { }
    private static final String PREFIX="/org/jlinalg/omics/";
    static byte[] resource(String name) throws IOException {
        try(var input=RsubreadProfiles.class.getResourceAsStream(PREFIX+name)) {
            if(input==null)throw new IOException("Required packaged Rsubread resource unavailable: "+name);return input.readAllBytes();
        }
    }
    static Map<String,Object> profile(String edition) throws IOException {
        if(!Set.of("2260","2232-R441").contains(edition))throw new IOException("Declare exactly 2260 or 2232-R441; no edition substitution");
        var index=object(SimpleJson.parse(new String(resource("rsubread-workflow-reference-v2/profiles.json"),StandardCharsets.UTF_8)));
        var row=object(object(index.get("profiles")).get(edition));var members=object(row.get("files"));
        if(!members.keySet().equals(Set.of("profile.json","R/featureCounts.R","R/cellCounts.R","DESCRIPTION")))throw new IOException("Unexpected original reference population");
        for(var e:members.entrySet()) {
            byte[] raw=resource("rsubread-workflow-reference-v2/"+edition+"/"+e.getKey());
            if(!Map.of("bytes",(long)raw.length,"sha256",hash(raw)).equals(object(e.getValue())))throw new IOException("Packaged original source/profile differs");
        }
        var profile=object(SimpleJson.parse(new String(resource("rsubread-workflow-reference-v2/"+edition+"/profile.json"),StandardCharsets.UTF_8)));
        if(!edition.equals(profile.get("edition"))||!row.get("profile").equals(profile.get("profile")))throw new IOException("Edition/profile differs");return profile;
    }
    static void capture(Path root,String edition) throws IOException {
        profile(edition);Path reference=root.resolve("source/reference");Files.createDirectories(reference.resolve("R"));
        for(String name:List.of("profile.json","R/featureCounts.R","R/cellCounts.R","DESCRIPTION"))
            Files.write(reference.resolve(name),resource("rsubread-workflow-reference-v2/"+edition+"/"+name),StandardOpenOption.CREATE_NEW);
        Files.write(root.resolve("source/rsubread_runtime_guard.R"),resource("rsubread_runtime_guard.R"),StandardOpenOption.CREATE_NEW);
    }
    static void checkCapture(Path root,String edition) throws IOException {
        profile(edition);
        for(String name:List.of("profile.json","R/featureCounts.R","R/cellCounts.R","DESCRIPTION"))
            if(!Arrays.equals(Files.readAllBytes(root.resolve("source/reference/"+name)),resource("rsubread-workflow-reference-v2/"+edition+"/"+name)))throw new IOException("Captured original helper/profile changed");
        if(!Arrays.equals(Files.readAllBytes(root.resolve("source/rsubread_runtime_guard.R")),resource("rsubread_runtime_guard.R")))throw new IOException("Captured runtime guard changed");
    }
    static void paths(Path target,Map<String,Object> binding) throws IOException {
        StringBuilder out=new StringBuilder();
        for(String key:List.of("r_library","r_home","package_version","R_version"))
            out.append(key).append('\t').append(HexFormat.of().formatHex(text(binding.get(key)).getBytes(StandardCharsets.UTF_8))).append('\n');
        Files.writeString(target,out.toString(),StandardCharsets.US_ASCII,StandardOpenOption.CREATE_NEW);
    }
}
