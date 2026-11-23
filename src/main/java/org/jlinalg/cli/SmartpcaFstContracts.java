/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Source fstonly choices; PCA normalization/allele filters are deliberately not FST parameters. */
final class SmartpcaFstContracts {
    private SmartpcaFstContracts() { }
    static final String PROFILE="eig-7.2.1-smartpca-16000-native-fst-v1";
    static final String ACTION=SmartpcaWorkflowContracts.ACTION;
    static final String SCOPE="Prepared human-autosomal EIGENSTRAT fstonly, diploid/inbreeding estimators, source block assignment and weighted block jackknife. Source v7.2.1/program16000. Native Java FP64 CPU; original C is not executed. No LD pruning, genotype harmonization, X/haploid analysis, historical-edition equivalence or whole-paper reproduction.";
    static Map<String,Object> validate(Map<String,Object> raw,String base,String style)throws IOException {
        keys(raw,Set.of("schema_version","mode","engine","profile","operation","out","inputs","options","limits","policies"));
        integer(raw.get("schema_version"),1,1);choice(raw,"mode",Set.of("RUN"));choice(raw,"engine",Set.of("native"));choice(raw,"profile",Set.of(PROFILE));choice(raw,"operation",Set.of("FST"));
        var input=object(raw.get("inputs"));keys(input,Set.of("genotypes","markers","samples","populations","genome_build"));require(input.containsKey("populations"),"Declare literal populations array or explicit null to select all populations");
        var nativeInput=new LinkedHashMap<>(input);nativeInput.put("reference_populations",nativeInput.remove("populations"));
        var parse=map("schema_version",1,"mode","RUN","engine","native","profile",SmartpcaWorkflowContracts.PROFILE,"operation","PCA","out",raw.get("out"),"inputs",nativeInput,
            "options",map("numoutevec",1,"numoutlieriter",0,"numoutlierevec",0),"limits",raw.getOrDefault("limits",map()),"policies",raw.getOrDefault("policies",map()));
        var checked=SmartpcaWorkflowContracts.validate(parse,base,style);var canonicalInput=new LinkedHashMap<>(object(checked.get("inputs")));Object populations=canonicalInput.remove("reference_populations");
        if(populations!=null)require(list(populations).size()>=2&&list(populations).size()<=100,"Source FST requires2..100 distinct declared populations");canonicalInput.put("populations",populations);
        var options=raw.containsKey("options")?object(raw.get("options")):map();keys(options,Set.of("inbreed","blgsize"));
        double length=decimal(options.getOrDefault("blgsize",.05)).doubleValue();require(Double.isFinite(length)&&length>0,"Positive finite source blgsize required");
        return map("schema_version",1,"mode","RUN","engine","native","profile",PROFILE,"operation","FST","out",checked.get("out"),"inputs",canonicalInput,
            "options",map("inbreed",SmartpcaWorkflowContracts.bool(options,"inbreed",false),"blgsize",length),"limits",checked.get("limits"),"policies",checked.get("policies"));
    }
    static SmartpcaWorkflowData.Prepared data(Path root,Map<String,Object> request)throws IOException {
        // Shared strict text parsing only. No PCA, PCA allele filter or PCA projection is run.
        var input=new LinkedHashMap<>(object(request.get("inputs")));input.put("reference_populations",input.remove("populations"));
        var parse=map("inputs",input,"options",map("shrinkmode",false),"limits",request.get("limits"));return SmartpcaWorkflowData.read(root,parse);
    }
    static java.util.List<String> populations(SmartpcaWorkflowData.Prepared data,Map<String,Object> request)throws IOException {
        Object declared=object(request.get("inputs")).get("populations");var names=new ArrayList<String>();
        if(declared==null){for(var sample:data.samples())if(!names.contains(sample.population()))names.add(sample.population());}
        else for(Object value:list(declared))names.add(text(value,"population"));require(names.size()>=2&&names.size()<=100,"Source FST requires2..100 populations");return java.util.List.copyOf(names);
    }
    static String action(){return SmartpcaWorkflowContracts.ACTION;}
}
