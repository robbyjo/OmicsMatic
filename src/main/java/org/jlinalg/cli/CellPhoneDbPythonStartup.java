/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.singlecell.SpyrrowSourceSemantics;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Strict data-only effective-startup gate; seed0 hashes have an independent native audit. */
final class CellPhoneDbPythonStartup {
    private CellPhoneDbPythonStartup() { }
    static final List<String> PROBES=List.of("","Spyrrow ligand","caf\u00e9","ligand\u0100","cell \uD83E\uDDEC","#123");
    static Map<String,Object> verify(Path path,long seed,String python)throws IOException {
        require(seed>=0&&seed<=4294967295L,"Invalid requested Python hash seed");
        var receipt=CellPhoneDbWorkflowRuntime.read(path,65536);
        exact(receipt,"schema_version","runner_revision","requested_seed","environment_seed","flags","python_executable","python_version","hash_algorithm","hash_width","hash_bits","seed_bits","hashes","before_scientific_imports");
        integer(receipt.get("schema_version"),1,1);integer(receipt.get("runner_revision"),2,2);
        require(integer(receipt.get("requested_seed"),0,4294967295L)==seed&&text(receipt.get("environment_seed")).equals(Long.toString(seed)),"Effective startup seed differs from the complete request");
        require(text(receipt.get("python_executable")).equals(python)&&text(receipt.get("python_version")).equals("3.11.17"),"Python startup executable/version differs from the bound original runtime");
        require(text(receipt.get("hash_algorithm")).equals("siphash13")&&Boolean.TRUE.equals(receipt.get("before_scientific_imports")),"Startup hash implementation/order is not admitted");
        integer(receipt.get("hash_width"),64,64);integer(receipt.get("hash_bits"),64,64);integer(receipt.get("seed_bits"),128,128);
        var flags=object(receipt.get("flags"));exact(flags,"ignore_environment","isolated","safe_path","no_user_site","hash_randomization","optimize","dont_write_bytecode");
        integer(flags.get("ignore_environment"),0,0);integer(flags.get("isolated"),0,0);integer(flags.get("no_user_site"),1,1);integer(flags.get("hash_randomization"),seed==0?0:1,seed==0?0:1);integer(flags.get("optimize"),0,0);integer(flags.get("dont_write_bytecode"),1,1);require(Boolean.TRUE.equals(flags.get("safe_path")),"Safe startup path is required");
        var hashes=list(receipt.get("hashes"));require(hashes.size()==PROBES.size(),"Incomplete effective-interpreter probe family");
        for(int i=0;i<PROBES.size();i++){
            var probe=object(hashes.get(i));exact(probe,"text","hash");String value=PROBES.get(i);require(text(probe.get("text")).equals(value),"Startup hash probes changed identity/order");long observed=integer(probe.get("hash"),Long.MIN_VALUE,Long.MAX_VALUE);
            if(seed==0)require(observed==SpyrrowSourceSemantics.stringHash(value),"Effective seed0 Python hash differs from independent native SipHash13/Unicode computation");
            if(value.isEmpty())require(observed==0,"Empty Python string hash must be zero");
        }
        var result=new LinkedHashMap<String,Object>();result.put("status","PASS_EFFECTIVE_PYTHON_STARTUP_ONLY");result.put("requested_seed",seed);result.put("probe_count",PROBES.size());result.put("independent_native_seed0_hashes",seed==0);result.put("nonzero_hash_secret_natively_reimplemented",false);result.put("receipt",receipt);return result;
    }
    private static void exact(Map<String,Object> map,String... names)throws IOException {require(map.keySet().equals(Set.of(names)),"Incomplete/unknown Python startup receipt fields");}
}
