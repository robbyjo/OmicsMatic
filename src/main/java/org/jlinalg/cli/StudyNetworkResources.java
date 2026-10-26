/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.jlinalg.study.StudyNetworkPolicy;

/** Resource-aware cache identity for native network operations; never writes a cache. */
public final class StudyNetworkResources {
    private StudyNetworkResources() { }
    public static Map<String,String> inputHashes(Path project,Map<String,Object> study,Map<String,Object> operation) throws IOException {
        var hashes=new LinkedHashMap<String,String>();
        if(operation.get("type").equals("network_preservation_null")&&!operation.containsKey("supplied_result_id"))for(Object raw:StudyPublicEvidence.list(study.get("hypotheses"))){var h=StudyPublicEvidence.map(raw);if(h.get("id").equals(operation.get("hypothesis"))){var p=org.jlinalg.study.StudyNetworkNullPolicy.resolve(StudyPublicEvidence.map(StudyPublicEvidence.map(h.get("analysis")).get("network_preservation_null")));if(p.containsKey("strata_file")){Path path=project.resolve(String.valueOf(p.get("strata_file"))).normalize();hashes.put("network_null:strata",Files.isRegularFile(path)?FollowupSupport.hash(path):"missing");}}}
        if(!Set.of("network_analysis","pathway_network","module_gene_ontology").contains(operation.get("type"))||operation.containsKey("supplied_result_id"))return hashes;
        Map<String,Object> settings=Map.of();for(Object raw:StudyPublicEvidence.list(study.get("hypotheses"))){var h=StudyPublicEvidence.map(raw);if(h.get("id").equals(operation.get("hypothesis")))settings=StudyNetworkPolicy.resolve(StudyPublicEvidence.map(StudyPublicEvidence.map(h.get("analysis")).get("network")));}
        if(settings.containsKey("gene_sets")){Path file=project.resolve(String.valueOf(settings.get("gene_sets")));hashes.put("network:gene_sets",Files.isRegularFile(file)?FollowupSupport.hash(file):"missing");}
        if(!operation.get("type").equals("module_gene_ontology"))return hashes;
        Path root=settings.containsKey("go_database")?project.resolve(String.valueOf(settings.get("go_database"))):StudyResourceManager.resourceRoot(project,study).resolve("enrichment/go-human");root=root.toAbsolutePath().normalize();
        Path manifest=root.resolve("manifest.properties");hashes.put("module_go:manifest",Files.isRegularFile(manifest)?FollowupSupport.hash(manifest):"missing");
        if(Files.isRegularFile(manifest)){var metadata=new Properties();try(var reader=Files.newBufferedReader(manifest)){metadata.load(reader);}for(String key:metadata.stringPropertyNames())if(key.startsWith("sha256.")){Path file=root.resolve(key.substring(7)).normalize();if(!file.startsWith(root))throw new IOException("Unsafe network GO resource manifest path");hashes.put("module_go:"+key,Files.isRegularFile(file)?FollowupSupport.hash(file):"missing");}}
        return hashes;
    }
}
