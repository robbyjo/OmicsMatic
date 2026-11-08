/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Complete-receipt admission and explicit corruption rejection; no scientific execution. */
public final class CellPhoneDbPythonStartupMatrix {
    private CellPhoneDbPythonStartupMatrix() { }
    public static void main(String[] args)throws Exception {
        require(args.length==5,"Require startup receipt, seed, exact Python executable, NEW case directory, NEW result JSON");Path path=Path.of(args[0]),home=Path.of(args[3]),out=Path.of(args[4]);long seed=Long.parseLong(args[1]);String python=args[2];Files.createDirectory(home);require(!Files.exists(out),"Existing matrix protected");var valid=CellPhoneDbPythonStartup.verify(path,seed,python);var records=new ArrayList<Object>();
        for(String mutation:List.of("missing-field","unknown-field","seed","environment","python","version","algorithm","width","hash-bits","seed-bits","late","ignore-environment","isolated","unsafe-path","user-site","randomization","optimized","bytecode","missing-probe","probe-order","empty-hash","bad-hash")){
            var value=CellPhoneDbWorkflowRuntime.read(path,65536);var flags=object(value.get("flags"));var hashes=new ArrayList<Object>(list(value.get("hashes")));value.put("hashes",hashes);
            switch(mutation){
                case "missing-field" -> value.remove("environment_seed");case "unknown-field" -> value.put("unbound",true);case "seed" -> value.put("requested_seed",seed==0?1:0);case "environment" -> value.put("environment_seed","random");case "python" -> value.put("python_executable",python+".other");case "version" -> value.put("python_version","3.11.16");case "algorithm" -> value.put("hash_algorithm","siphash24");case "width" -> value.put("hash_width",32);case "hash-bits" -> value.put("hash_bits",32);case "seed-bits" -> value.put("seed_bits",64);case "late" -> value.put("before_scientific_imports",false);
                case "ignore-environment" -> flags.put("ignore_environment",1);case "isolated" -> flags.put("isolated",1);case "unsafe-path" -> flags.put("safe_path",false);case "user-site" -> flags.put("no_user_site",0);case "randomization" -> flags.put("hash_randomization",seed==0?1:0);case "optimized" -> flags.put("optimize",1);case "bytecode" -> flags.put("dont_write_bytecode",0);case "missing-probe" -> hashes.remove(0);case "probe-order" -> Collections.swap(hashes,0,1);case "empty-hash" -> {var probe=object(hashes.get(0));probe.put("hash",1L);hashes.set(0,probe);}case "bad-hash" -> {var probe=object(hashes.get(1));probe.put("hash",seed==0?0L:"untyped");hashes.set(1,probe);}
                default -> throw new IOException("Unknown matrix mutation");
            }
            value.put("flags",flags);
            Path changed=home.resolve(mutation+".json");NextflowFiles.writeJson(changed,value);boolean rejected=false;String reason="";try{CellPhoneDbPythonStartup.verify(changed,seed,python);}catch(IOException failure){rejected=true;reason=failure.toString();}require(rejected,"Startup corruption admitted: "+mutation);records.add(Map.of("mutation",mutation,"rejected",true,"reason",reason));
        }
        boolean missing=false;try{CellPhoneDbPythonStartup.verify(home.resolve("absent.json"),seed,python);}catch(IOException failure){missing=true;}require(missing,"Missing startup receipt admitted");
        NextflowFiles.writeJson(out,Map.of("status","PASS_EFFECTIVE_STARTUP_AUDIT_AND_REJECTIONS_ONLY","valid",valid,"rejections",records,"missing_receipt_rejected",missing));System.out.println("PASS effective startup: "+records.size()+" corrupted receipts and missing receipt rejected");
    }
}
