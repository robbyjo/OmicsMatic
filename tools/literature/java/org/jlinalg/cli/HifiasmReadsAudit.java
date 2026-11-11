/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jlinalg.assembly.HifiasmReads;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Complete field equality against six original kseq readers and stricter explicit adapter contracts. */
public final class HifiasmReadsAudit {
    private HifiasmReadsAudit(){ }
    private static long capacity(Map<String,Object> map,String name,long fallback)throws IOException{return map.containsKey(name)?integer(map.get(name),1,10000000):fallback;}
    private static HifiasmReads.Limits limits(Map<String,Object> map)throws IOException{return new HifiasmReads.Limits(capacity(map,"encoded_bytes",1000000),capacity(map,"decoded_bytes",1000000),(int)capacity(map,"line_bytes",10000),(int)capacity(map,"read_bases",10000),(int)capacity(map,"records",1000),capacity(map,"bases",100000),capacity(map,"header_bytes",100000));}
    private static Path file(Path root,Object raw)throws IOException{var record=object(raw);Path file=root.resolve(text(record.get("path")));require(NextflowFiles.fingerprint(record.get("pin"),1000000).equals(NextflowFiles.pin(file,1000000,()->{})),"Pinned read fixture changed");return file;}
    private static void reject(Checked operation)throws Exception{boolean rejected=false;try{operation.run();}catch(IOException|IllegalArgumentException expected){rejected=true;}require(rejected,"Invalid input was accepted");}
    @FunctionalInterface private interface Checked{void run()throws Exception;}
    public static void main(String[] args)throws Exception{
        require(args.length==3,"Require cases, repository root and NEW output");Path root=Path.of(args[1]).toAbsolutePath().normalize(),out=Path.of(args[2]);Files.createDirectory(out);var cases=GseapyExecutionRuntime.read(Path.of(args[0]),16777216);int valid=0,invalid=0,pairs=0;
        for(Object raw:list(cases.get("valid"))){var spec=object(raw);Path file=file(root,spec);var expected=list(spec.get("expected"));var actual=new ArrayList<Map<String,Object>>();HifiasmReads.Inspection stats;
            try(var reader=new HifiasmReads.Reader(Files.newInputStream(file),limits(object(spec.get("limits"))),bool(spec.get("require_fastq")))){reject(reader::inspection);for(HifiasmReads.Read read;(read=reader.next())!=null;){var record=new java.util.LinkedHashMap<String,Object>();record.put("id",read.id());int split=read.id().length();record.put("comment",split==read.header().length()?"":read.header().substring(split+1));record.put("sequence",read.sequence());record.put("quality",read.quality());actual.add(record);}stats=reader.inspection();}
            require(NextflowJson.json(actual).equals(NextflowJson.json(expected)),"Native literal fields differ from every original kseq reader: "+spec.get("id"));require(stats.reads().size()==expected.size()&&stats.bases()==actual.stream().mapToLong(r->((String)r.get("sequence")).length()).sum(),"Complete native census differs");NextflowFiles.writeJson(out.resolve(text(spec.get("id"))+".json"),HifiasmJson.value(stats));valid++;
        }
        for(Object raw:list(cases.get("invalid"))){var spec=object(raw);Path file=file(root,spec);reject(()->HifiasmReads.inspect(Files.newInputStream(file),limits(object(spec.get("limits"))),bool(spec.get("require_fastq"))));invalid++;}
        for(Object raw:list(cases.get("pairs"))){var spec=object(raw);Path first=file(root,spec.get("first")),second=file(root,spec.get("second"));if(bool(spec.get("valid"))){var result=HifiasmReads.inspectPair(Files.newInputStream(first),Files.newInputStream(second),limits(Map.of()),false);require(result.pairs().size()==result.first().reads().size()&&result.pairs().size()==result.second().reads().size(),"Incomplete paired census");}else reject(()->HifiasmReads.inspectPair(Files.newInputStream(first),Files.newInputStream(second),limits(Map.of()),false));pairs++;}
        reject(()->new HifiasmReads.Read("r","other","AC",null));reject(()->new HifiasmReads.Read("r","r","AX",null));reject(()->new HifiasmReads.Read("r","r","AC","I"));reject(()->new HifiasmReads.Limits(0,1,1,1,1,1,1));
        try(var reader=new HifiasmReads.Reader(new ByteArrayInputStream(">r\nacgtuNRY\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)),limits(Map.of()),false)){require(reader.next().sequence().equals("acgtuNRY")&&reader.next()==null,"Literal read changed");var stats=reader.inspection();require(stats.lowercaseBases()==5&&stats.uracilBases()==1&&stats.ambiguousBases()==3,"Literal U/case/ambiguity census differs");}
        NextflowFiles.writeJson(out.resolve("COMPLETED.json"),Map.of("status","passed-original-kseq-fields-and-explicit-native-read-admission-contracts-only","valid_cases",valid,"invalid_cases",invalid,"pair_cases",pairs,"public_api_rejections",4,"analysis_admitted",false,"implementation_credit",false));System.out.println("Passed "+valid+" complete source read cases, "+invalid+" rejections and "+pairs+" paired cases");
    }
}
