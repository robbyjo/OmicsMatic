/* Copyright (C) 2026 JLinAlg contributors
 * SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.util.*;
import static org.jlinalg.cli.PlinkRelatednessSupport.*;

/** Fixed original-profile operations, caller choices and explicit capacities. */
final class PlinkRelatednessContracts {
    private PlinkRelatednessContracts() { }
    static final String PROFILE="original-official-windows-x86_64-20260927-20261024";
    enum Operation { KING_TABLE,KING_MATRIX,KING_CUTOFF,GENOME }
    record Limits(long inputBytes,long metadataBytes,long workingBytes,long diskBytes,long outputBytes,
                  int files,int samples,int variants,long pairVariantOperations,int pairBatchSize,int lineBytes,int wallSeconds) { }
    record Request(Operation operation,String bfile,String runtimeHome,String out,int threads,int memoryMiB,long seed,
                   Map<String,Object> settings,Limits limits) {
        String engine(){return operation==Operation.GENOME?"plink1":"plink2";}
    }
    static Request validate(Map<String,Object> value)throws IOException {
        keys(value,"schema_version","mode","operation","bfile","runtime_home","out","threads","memory_mib","seed","settings","limits");
        integer(value,"schema_version",1,1);require(text(value,"mode").equals("RUN"),"Require mode RUN");
        Operation operation;try{operation=Operation.valueOf(text(value,"operation"));}catch(IllegalArgumentException e){throw new IOException("Unknown relatedness operation",e);}
        var s=object(value.get("settings"),"settings");
        switch(operation){
            case KING_TABLE->{keys(s,"counts","rel_check","kinship_filter");bool(s,"counts");bool(s,"rel_check");nullableNumber(s,"kinship_filter");}
            case KING_MATRIX->{keys(s,"precision");require(Set.of("FLOAT64","FLOAT32").contains(text(s,"precision")),"precision must be FLOAT64 or FLOAT32; source shape is explicitly triangle");}
            case KING_CUTOFF->{keys(s,"threshold","audit_retained_pairs");number(s,"threshold");bool(s,"audit_retained_pairs");}
            case GENOME->{keys(s,"rel_check","unbounded","nudge","ppc_gap_kb","min_pi_hat","max_pi_hat","frequency_mode");bool(s,"rel_check");bool(s,"unbounded");bool(s,"nudge");number(s,"ppc_gap_kb");nullableNumber(s,"min_pi_hat");nullableNumber(s,"max_pi_hat");require(text(s,"frequency_mode").equals("COHORT_FOUNDERS"),"This source profile uses explicit COHORT_FOUNDERS frequency mode; external frequency files are not implemented");}
        }
        var c=object(value.get("limits"),"limits");keys(c,"input_bytes","metadata_bytes","working_bytes","disk_bytes","output_bytes","files","samples","variants","pair_variant_operations","pair_batch_size","line_bytes","wall_seconds");
        long safe=9_007_199_254_740_991L;
        var l=new Limits(integer(c,"input_bytes",1,safe),integer(c,"metadata_bytes",1,safe),integer(c,"working_bytes",1,safe),integer(c,"disk_bytes",1,safe),integer(c,"output_bytes",1,safe),
            (int)integer(c,"files",16,1_000_000),(int)integer(c,"samples",2,Integer.MAX_VALUE-8),(int)integer(c,"variants",1,Integer.MAX_VALUE),integer(c,"pair_variant_operations",1,safe),
            (int)integer(c,"pair_batch_size",1,10_000_000),(int)integer(c,"line_bytes",64,16_777_216),(int)integer(c,"wall_seconds",1,Integer.MAX_VALUE));
        require(l.metadataBytes()<=l.inputBytes()&&l.outputBytes()<=l.diskBytes(),"metadata_bytes must not exceed input_bytes; output_bytes must not exceed disk_bytes");
        return new Request(operation,text(value,"bfile"),text(value,"runtime_home"),text(value,"out"),(int)integer(value,"threads",1,256),
            (int)integer(value,"memory_mib",operation==Operation.GENOME?64:640,Integer.MAX_VALUE),integer(value,"seed",0,4_294_967_295L),Collections.unmodifiableMap(new LinkedHashMap<>(s)),l);
    }
    static List<String> arguments(Request r){
        var a=new ArrayList<>(List.of("--bfile","../inputs/cohort","--threads",Integer.toString(r.threads()),"--memory",Integer.toString(r.memoryMiB()),"--seed",Long.toString(r.seed())));
        var s=r.settings();
        switch(r.operation()){
            case KING_TABLE->{a.add("--make-king-table");if((Boolean)s.get("counts"))a.add("counts");if((Boolean)s.get("rel_check"))a.add("rel-check");a.add("cols=fid,id,nsnp,hethet,ibs0,ibs1,kinship");if(s.get("kinship_filter")!=null)a.addAll(List.of("--king-table-filter",s.get("kinship_filter").toString()));}
            case KING_MATRIX->a.addAll(List.of("--make-king","triangle",s.get("precision").equals("FLOAT64")?"bin":"bin4"));
            case KING_CUTOFF->a.addAll(List.of("--king-cutoff",s.get("threshold").toString()));
            case GENOME->{a.addAll(List.of("--genome","full"));for(String flag:List.of("rel_check","unbounded","nudge"))if((Boolean)s.get(flag))a.add(flag.replace('_','-'));a.addAll(List.of("--ppc-gap",s.get("ppc_gap_kb").toString()));if(s.get("min_pi_hat")!=null)a.addAll(List.of("--min",s.get("min_pi_hat").toString()));if(s.get("max_pi_hat")!=null)a.addAll(List.of("--max",s.get("max_pi_hat").toString()));}
        }
        a.addAll(List.of("--out","result"));return List.copyOf(a);
    }
}
