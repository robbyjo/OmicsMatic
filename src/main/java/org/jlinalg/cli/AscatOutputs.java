/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.CRC32;
import org.jlinalg.genetics.AscatObjective;
import static org.jlinalg.cli.NextflowFiles.*;
import static org.jlinalg.cli.AllcoolsPosteriorRequest.require;
import static org.jlinalg.cli.AllcoolsPosteriorCompute.map;

/** Complete source-table populations, original model states and native full-grid checks. */
final class AscatOutputs {
    private AscatOutputs() { }
    static Map<String,Object> audit(Path engine,AscatRequest.Spec spec,Runnable check)throws IOException {
        var result=object(json(engine.resolve("RESULTS.json"),134217728));require("COMPLETE_ORIGINAL_ASCAT_WORKFLOW_OUTPUTS".equals(result.get("status"))&&Boolean.TRUE.equals(result.get("all_samples_admitted")),"Original ASCAT has incomplete/unreportable samples; retain all outputs without admitting a complete analysis");
        var profile=AscatRuntime.profile(spec.profile());require(profile.get("R").equals(result.get("R"))&&profile.get("ASCAT").equals(result.get("ASCAT"))&&spec.mode().equals(result.get("mode")),"Source profile or preparation mode differs");
        var groups=object(result.get("groups"));var expectedGroups=new LinkedHashMap<String,List<String>>();
        if(spec.mode().equals("matrices"))expectedGroups.put("cohort",spec.samples().stream().map(AscatRequest.Sample::id).toList());
        else for(int index=0;index<spec.samples().size();index++)expectedGroups.put(String.format(Locale.ROOT,"sample-%06d",index+1),List.of(spec.samples().get(index).id()));
        require(groups.keySet().equals(expectedGroups.keySet()),"Complete original cohort/group population differs");
        var tables=object(result.get("tables"));var counts=new TreeMap<String,Object>();
        for(var entry:tables.entrySet()) {
            String name=member(text(entry.getKey()));var meta=object(entry.getValue());long rows=table(engine.resolve(name),meta,check);counts.put(name,rows);
        }
        long samples=0,loci=0,segments=0,cells=0,sourceFallbackCaps=0;double largestAbsolute=0,largestRelative=0;
        for(var expected:expectedGroups.entrySet()) {
            check.run();String id=expected.getKey();var group=object(groups.get(id));List<String> ids=strings(group.get("samples"));require(ids.equals(expected.getValue())&&id.equals(group.get("id")),"Original group sample identities/order differ");
            require(Boolean.TRUE.equals(group.get("all_samples_admitted"))&&list(group.get("source_failed")).isEmpty(),"Failed source samples cannot be dropped to obtain admission");long groupLoci=integer(group.get("loci"),1,Integer.MAX_VALUE);String prefix="models/"+id+"/";
            for(String name:List.of("loaded-object.rds","corrected-object.rds","segmented-object.rds","fitted-object.rds"))require(Files.size(engine.resolve(prefix+name))>0,"Complete original R object missing: "+name);
            for(String name:List.of("corrected-tumor-logR.tsv","segmented-tumor-logR.tsv","fit-nA.tsv","fit-nB.tsv"))require(counts.get(prefix+name)instanceof Long n&&n==groupLoci,"Incomplete original locus population: "+name);
            for(String name:List.of("fit-segments.tsv","fit-segments_raw.tsv"))require(counts.get(prefix+name)instanceof Long n&&n>0,"Complete original segment population missing: "+name);
            require(counts.get(prefix+"sample-summary.tsv")instanceof Long n&&n==ids.size(),"Complete source summary population differs");summary(engine.resolve(prefix+"sample-summary.tsv"),ids,spec,check);
            if("3.2.0".equals(profile.get("ASCAT")))require(counts.get(prefix+"source-metrics.tsv")instanceof Long n&&n==ids.size()&&Files.size(engine.resolve(prefix+"source-metrics.rds"))>0,"Complete original current-edition metrics are missing");
            for(String sample:ids)for(String suffix:List.of(".ASCATprofile.png",".rawprofile.png",".sunrise.png"))require(Files.isRegularFile(engine.resolve(prefix+sample+suffix)),"Source model plot missing: "+sample+suffix);
            var nativeAudit=nativeAudit(engine.resolve(prefix+"native-components.bin"),ids,object(group.get("native_reference")),((Number)spec.settings().get("gamma")).doubleValue(),spec.nativeCells()-cells,check);
            samples+=ids.size();loci+=groupLoci;segments+=(Long)nativeAudit.get("segments");cells+=(Long)nativeAudit.get("distance_cells");sourceFallbackCaps+=(Long)nativeAudit.get("original_fitting_fallback_capped_cells");largestAbsolute=Math.max(largestAbsolute,(Double)nativeAudit.get("largest_absolute_error"));largestRelative=Math.max(largestRelative,(Double)nativeAudit.get("largest_scaled_relative_error"));
        }
        require(samples==spec.samples().size(),"A declared sample was omitted");long png=0;
        try(var paths=Files.walk(engine)){for(Path path:paths.filter(p->Files.isRegularFile(p)&&p.getFileName().toString().endsWith(".png")).toList()){check.run();png(path,spec.outputBytes(),check);png++;}}
        require(png>=3*samples,"Incomplete model plot population");Object warnings=json(engine.resolve("SOURCE-WARNINGS.json"),16777216);list(warnings);
        return map("status","PASS_COMPLETE_ORIGINAL_ASCAT_TABLES_STATES_PNG_CRC_AND_NATIVE_COMPONENTS","profile",spec.profile(),"mode",spec.mode(),"samples",samples,"groups",groups.size(),"group_locus_rows",loci,"tables",counts,"PNG_files_with_complete_chunk_CRC_validation",png,"native_segments",segments,"native_distance_cells",cells,"source_fitting_fallback_capped_cells_recorded",sourceFallbackCaps,"largest_absolute_error",largestAbsolute,"largest_scaled_relative_error",largestRelative,"source_warnings",warnings,"all_samples_admitted",true,"model_binary_validation","complete fresh replay required; no user RDS is deserialized","whole_package_admitted",false);
    }
    private static long table(Path path,Map<?,?> meta,Runnable check)throws IOException {
        require(meta.get("source_null")instanceof Boolean,"Source NULL distinction missing");var columns=list(meta.get("columns"));long expected=integer(meta.get("rows"),0,Integer.MAX_VALUE);
        if(Boolean.TRUE.equals(meta.get("source_null"))){require(columns.isEmpty()&&expected==0&&Files.size(path)==0,"Invalid source NULL table");return 0;}
        long rows=0;try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            String header=reader.readLine();require(header!=null,"Source table is empty");String[] names=header.split("\t",-1);require(names.length==columns.size(),"Source table width differs");
            for(int i=0;i<names.length;i++){var column=object(columns.get(i));require(integer(column.get("ordinal"),1,names.length)==i+1&&names[i].equals(column.get("name")),"Ordinal source table header differs");require(Set.of("source-number-token","source-logical","source-text").contains(column.get("type")),"Unknown original table type");}
            for(String line=reader.readLine();line!=null;line=reader.readLine()){check.run();String[] fields=line.split("\t",-1);require(fields.length==names.length,"Truncated original source table");for(int i=0;i<fields.length;i++){Object type=object(columns.get(i)).get("type");if(type.equals("source-number-token"))sourceNumber(fields[i]);else if(type.equals("source-logical"))require(Set.of("TRUE","FALSE","NA").contains(fields[i]),"Invalid original logical token");}rows++;}
        }require(rows==expected,"Incomplete original table row population");return rows;
    }
    private static void summary(Path path,List<String> ids,AscatRequest.Spec spec,Runnable check)throws IOException {
        try(var reader=Files.newBufferedReader(path,StandardCharsets.UTF_8)) {
            require("sample\tsex\tsource_failed\tsource_nonaberrant\tpurity\tploidy\tpsi\tgoodness_of_fit\tadmitted".equals(reader.readLine()),"Source summary schema differs");
            for(String id:ids){check.run();String line=reader.readLine();require(line!=null,"Missing source sample state");String[] fields=line.split("\t",-1);require(fields.length==9&&fields[0].equals(id)&&fields[2].equals("FALSE")&&fields[8].equals("TRUE"),"Source sample was reordered, failed or unreportable");
                String sex=spec.samples().stream().filter(sample->sample.id().equals(id)).findFirst().orElseThrow().sex();require(sex.equals(fields[1]),"Original sex-chromosome handling changed");for(int i=4;i<=7;i++)require(Double.isFinite(sourceNumber(fields[i])),"Nonfinite fitted model estimate");
                require(sourceNumber(fields[4])>0&&sourceNumber(fields[4])<=1.05&&sourceNumber(fields[5])>=0&&sourceNumber(fields[6])>0&&sourceNumber(fields[7])>=((Number)spec.settings().get("minimum_goodness_of_fit")).doubleValue(),"Source model estimates fail the explicit admission threshold");
            }require(reader.readLine()==null,"Extra source sample state");
        }
    }
    static Map<String,Object> nativeAudit(Path path,List<String> ids,Map<?,?> expected,double gamma,long maximumCells,Runnable check)throws IOException {
        require(expected.keySet().equals(new HashSet<>(ids)),"Complete original native-reference identities differ");long loci=0,retained=0,segments=0,cells=0,caps=0;double largestAbsolute=0,largestRelative=0;
        try(var input=new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            require(Arrays.equals(input.readNBytes(11),"OMICSASCAT1".getBytes(StandardCharsets.US_ASCII)),"Native component packet header differs");require(input.readInt()==ids.size(),"Native component sample population differs");
            for(int sample=0;sample<ids.size();sample++) {
                check.run();require(input.readInt()==sample+1,"Reordered/missing native component sample");var meta=object(expected.get(ids.get(sample)));int n=input.readInt();require(n>0&&n==integer(meta.get("input_loci"),1,Integer.MAX_VALUE),"Incomplete aligned original loci");
                double[] r=vector(input,n,check),b=vector(input,n,check);var actual=AscatObjective.segments(r,b,check);int nSegments=input.readInt();require(nSegments==actual.size()&&nSegments==integer(meta.get("segments"),1,n),"Original/native segment count differs");long sampleRetained=0;
                for(var segment:actual){check.run();require(Double.doubleToRawLongBits(input.readDouble())==Double.doubleToRawLongBits(segment.logR())&&Double.doubleToRawLongBits(input.readDouble())==Double.doubleToRawLongBits(segment.baf())&&input.readDouble()==segment.length(),"Complete original/native segment bits or lengths differ");sampleRetained+=segment.length();}
                require(sampleRetained==integer(meta.get("retained_loci"),1,n),"Retained original locus population differs");int p=input.readInt();require(p>0&&p<=1000000,"Invalid explicit original psi axis");double[] psi=vector(input,p,check);int q=input.readInt();require(q>0&&q<=1000000,"Invalid explicit original rho axis");double[] rho=vector(input,q,check);long sampleCells=(long)p*q;
                require(sampleCells<=1000000&&sampleCells==integer(meta.get("distance_cells"),1,1000000)&&cells+sampleCells<=maximumCells,"Complete native grid exceeds declared cell capacity");
                for(double ploidy:psi)for(double purity:rho){check.run();double original=input.readDouble(),computed=AscatObjective.distance(actual,gamma,ploidy,purity,check),difference=Math.abs(original-computed);require(Double.isFinite(original)&&Double.isFinite(computed)&&difference<=2e-11+2e-12*Math.abs(original),"Complete original/native objective cell differs");largestAbsolute=Math.max(largestAbsolute,difference);largestRelative=Math.max(largestRelative,difference/Math.max(1,Math.abs(original)));}
                loci+=n;retained+=sampleRetained;segments+=nSegments;cells+=sampleCells;caps+=integer(meta.get("original_fitting_fallback_capped_cells"),0,sampleCells);
            }require(input.read()==-1,"Extra native component packet data");
        }catch(EOFException e){throw new IOException("Truncated complete original native component packet",e);}
        return map("samples",ids.size(),"input_loci",loci,"retained_loci",retained,"segments",segments,"distance_cells",cells,"original_fitting_fallback_capped_cells",caps,"largest_absolute_error",largestAbsolute,"largest_scaled_relative_error",largestRelative);
    }
    private static double[] vector(DataInputStream input,int n,Runnable check)throws IOException {
        require(n>0&&n<=16777216,"Original native-reference vector exceeds bounded row capacity");double[] result=new double[n];for(int i=0;i<n;i++){if((i&4095)==0)check.run();result[i]=input.readDouble();}return result;
    }
    private static double sourceNumber(String token)throws IOException {
        if(token.equals("NA")||token.equals("NaN"))return Double.NaN;if(token.equals("Inf"))return Double.POSITIVE_INFINITY;if(token.equals("-Inf"))return Double.NEGATIVE_INFINITY;
        try{double result=Double.parseDouble(token);require(Double.isFinite(result),"Unknown nonfinite source token");return result;}catch(NumberFormatException e){throw new IOException("Invalid original numerical token",e);}
    }
    private static void png(Path path,long maximum,Runnable check)throws IOException {
        require(Files.size(path)<=maximum,"Original PNG exceeds byte capacity");try(var input=new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            require(Arrays.equals(input.readNBytes(8),new byte[]{(byte)137,80,78,71,13,10,26,10}),"Invalid original PNG signature");boolean header=false,image=false,end=false;byte[] buffer=new byte[65536];
            while(!end){check.run();long length=Integer.toUnsignedLong(input.readInt());require(length<=maximum,"Invalid PNG chunk length");byte[] type=input.readNBytes(4);require(type.length==4,"Truncated PNG chunk");String kind=new String(type,StandardCharsets.US_ASCII);var crc=new CRC32();crc.update(type);long remaining=length;
                if(!header){require(kind.equals("IHDR")&&length==13,"Missing original PNG header");byte[] data=input.readNBytes(13);require(data.length==13,"Truncated PNG header");crc.update(data);var dimensions=new DataInputStream(new ByteArrayInputStream(data));require(dimensions.readInt()>0&&dimensions.readInt()>0,"Invalid source PNG dimensions");remaining=0;header=true;}
                while(remaining>0){check.run();int count=input.read(buffer,0,(int)Math.min(buffer.length,remaining));require(count>0,"Truncated PNG data");crc.update(buffer,0,count);remaining-=count;}
                require(Integer.toUnsignedLong(input.readInt())==crc.getValue(),"Original PNG chunk CRC differs");if(kind.equals("IDAT"))image=true;if(kind.equals("IEND")){require(length==0&&image,"Incomplete original PNG image");end=true;}
            }require(input.read()==-1,"Extra original PNG data");
        }catch(EOFException e){throw new IOException("Truncated original PNG",e);}
    }
    static Map<String,Object> canonical(Path engine,AscatRequest.Spec spec,Runnable check)throws IOException {
        var output=new TreeMap<String,Object>();for(var row:tree(engine,spec.files(),spec.outputBytes(),spec.outputBytes(),check).entrySet()){
            String name=row.getKey();if(name.startsWith("inputs/")||name.startsWith("process/")||name.startsWith("cache/")||name.equals("session.txt")||name.equals("R-MAPS.txt"))continue;require(row.getValue().kind().equals("file"),"Nonregular canonical original output");output.put(name,map("bytes",row.getValue().pin().bytes(),"sha256",row.getValue().pin().sha256()));
        }return output;
    }
}
