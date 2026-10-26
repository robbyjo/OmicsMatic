/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** Actual GO/Reactome source bytes enter native enrichment cache identities. */
public final class StudyRareFollowupResources {
    private StudyRareFollowupResources() { }
    public static Map<String,String> inputHashes(Path project,Map<String,Object> study,Map<String,Object> operation)throws IOException {
        var out=new LinkedHashMap<String,String>();String type=String.valueOf(operation.get("type"));
        if(!Set.of("gene_ontology","pathway_enrichment").contains(type)||operation.containsKey("supplied_result_id")||StudyPublicEvidence.map(operation.get("configuration")).containsKey("command"))return out;
        Path root=StudyResourceManager.resourceRoot(project,study).resolve("enrichment/"+(type.equals("gene_ontology")?"go-human":"reactome-human")).toAbsolutePath().normalize();Path manifest=root.resolve("manifest.properties");
        out.put("enrichment:manifest",Files.isRegularFile(manifest)?FollowupSupport.hash(manifest):"missing");
        if(Files.isRegularFile(manifest)) {
            var metadata=new Properties();try(var input=Files.newBufferedReader(manifest)){metadata.load(input);}
            for(String key:metadata.stringPropertyNames())if(key.startsWith("sha256.")) {
                Path data=root.resolve(key.substring(7)).normalize();if(!data.startsWith(root))throw new IOException("Unsafe enrichment resource manifest path");
                out.put("enrichment:"+key,Files.isRegularFile(data)?FollowupSupport.hash(data):"missing");
            }
        }return out;
    }
}
