/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jlinalg.pipeline.HumanPloidyProfile;

/** Local declared complements, deliberately separate from phenotype sex/gender and remote evidence. */
final class HumanPloidySamples {
    record Selection(List<String> ids,List<String[]> audit,Map<String,Object> summary) { }
    final Path path;final String hash;private final Map<String,String> groups;
    HumanPloidySamples(Path path)throws IOException {
        this.path=path.toRealPath();hash=FollowupSupport.hash(this.path);var table=DelimitedData.read(this.path);int id=table.column("id"),group=table.column("chromosome_complement");groups=new LinkedHashMap<>();
        for(String[] row:table.rows()){String key=row[id].strip(),value=row[group].strip().toUpperCase(java.util.Locale.ROOT);if(key.isBlank()||key.chars().anyMatch(Character::isISOControl)||groups.putIfAbsent(key,value)!=null)throw new IOException("Ploidy sample IDs must be unique, nonblank and free of control characters");if(value.length()>40||value.chars().anyMatch(Character::isISOControl))throw new IOException("Unbounded declared complement label");}
    }
    Selection select(List<String> eligible,HumanPloidyProfile profile)throws IOException {
        var ids=new ArrayList<String>();var audit=new ArrayList<String[]>();int xx=0,xy=0,unknown=0,outside=0;
        for(String id:eligible){String value=groups.getOrDefault(id,"UNKNOWN");String disposition;
            if(!value.equals("XX")&&!value.equals("XY")){unknown++;if(profile.unknownSamples().equals("error"))throw new IOException("Missing/unknown declared complement among eligible participants; supply the local ploidy sample table or explicitly exclude unknown samples");disposition="unknown_complement_excluded";}
            else if(!profile.includesComplement(value)){outside++;disposition="outside_registered_complement";}
            else {ids.add(id);if(value.equals("XX"))xx++;else xy++;disposition="selected";}
            audit.add(new String[]{id,value,disposition});
        }
        return new Selection(List.copyOf(ids),audit,Map.of("candidate_samples",eligible.size(),"selected_samples",ids.size(),"selected_XX",xx,"selected_XY",xy,"unknown_excluded",unknown,"outside_complement",outside,"complement_origin","caller_declared_not_inferred_or_certified"));
    }
    void verify()throws IOException{if(!hash.equals(FollowupSupport.hash(path)))throw new IOException("Ploidy sample declaration changed during analysis");}
}
