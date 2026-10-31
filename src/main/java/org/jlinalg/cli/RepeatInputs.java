/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedWriter;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jlinalg.genetics.RepeatAnnotations;

/** Protected original snapshots, explicit FASTA staging and complete structural annotation intake. */
final class RepeatInputs {
    private RepeatInputs() { }
    private static final long DIAGNOSTIC_RESERVE = 65536;
    record Snapshot(String role, Path original, Path copy, long bytes, long maximumBytes, String sha256) { }
    record Mapping(int ordinal, String originalId, String originalHeader, String stagedId, String stagedHeader,
            long length, String originalSequenceSha256, String stagedSequenceSha256) { }
    record Fasta(Path path, RepeatFasta.Census original, RepeatFasta.Census staged, List<Mapping> mappings) {
        Fasta { mappings = List.copyOf(mappings); }
    }
    record Prepared(RepeatRecipe.Spec spec, Path work, Fasta query, Fasta library, Fasta masked,
            Path annotationPath, RepeatAnnotations.Table annotations, RepeatMaskAudit.Validation annotationValidation,
            RepeatMaskAudit.Report suppliedMaskAudit, List<Snapshot> snapshots, Map<Path,String> ownedSha256,
            List<String> warnings) {
        Prepared { snapshots = List.copyOf(snapshots); ownedSha256 = Map.copyOf(ownedSha256); warnings = List.copyOf(warnings); }
        void verify(Runnable check) throws IOException {
            spec.verify();
            for (Snapshot source : snapshots) {
                if (!hash(source.original(),source.maximumBytes(),check).equals(source.sha256())
                        || !hash(source.copy(),source.maximumBytes(),check).equals(source.sha256()))
                    throw new IOException("RepeatMasker original/snapshot changed: " + source.role());
            }
            Map<Path,String> actual = inventory(work,spec.limits(),check);
            if (!actual.equals(ownedSha256)) throw new IOException("RepeatMasker protected preparation changed or contains unexpected members");
        }
    }
    /** The prepared bytes/identity mapping stay unchanged when an outer workflow reserves its own workspace. */
    static Prepared rebudget(Prepared source,RepeatRecipe.Spec spec)throws IOException{
        if(!source.spec().recipeSha256().equals(spec.recipeSha256())||!source.spec().parameters().equals(spec.parameters())
                ||source.spec().mode()!=spec.mode()||source.spec().staging()!=spec.staging()||source.spec().mask()!=spec.mask())throw new IOException("Only the outer original-workflow budget may change after preparation");
        return new Prepared(spec,source.work(),source.query(),source.library(),source.masked(),source.annotationPath(),source.annotations(),source.annotationValidation(),source.suppliedMaskAudit(),source.snapshots(),source.ownedSha256(),source.warnings());
    }
    static Prepared prepare(RepeatRecipe.Spec spec, Path suppliedWork, Runnable check) throws IOException {
        Objects.requireNonNull(spec); Objects.requireNonNull(check); spec.verify(); check.run();
        if (spec.limits().retainedBytes() > Runtime.getRuntime().maxMemory() / 4 * 3)
            throw new IOException("Declared RepeatMasker retained capacity exceeds75%of this Java heap;use a reviewed larger -Xmx or smaller explicit row/sequence capacities without truncating the data");
        Path work = suppliedWork.toAbsolutePath().normalize();
        if (work.getParent()==null || !work.getParent().toRealPath().equals(work.getParent())) throw new IOException("Preparation requires a canonical existing parent and a NEW work directory");
        for (Path source : sourcePaths(spec)) if (source.startsWith(work)) throw new IOException("A protected RepeatMasker source may not lie inside its new preparation directory");
        Files.createDirectory(work); Budget budget = new Budget(spec.limits());
        try {
            budget.member(); Path copies=directory(work.resolve("input-snapshots"),budget),stage=directory(work.resolve("stage"),budget);
            var snapshots=new ArrayList<Snapshot>();
            Snapshot recipe=snapshot("recipe",spec.recipe(),copies.resolve("recipe.json"),2097152,budget,check);snapshots.add(recipe);
            if(!recipe.sha256().equals(spec.recipeSha256()))throw new IOException("Recipe changed before its protected snapshot");
            Snapshot querySource=snapshot("query",spec.query().path(),copies.resolve("query.source"),spec.limits().inputBytes(),budget,check);snapshots.add(querySource);
            Fasta query=fasta(querySource.copy(),spec.query().encoding(),stage.resolve("query.fa"),spec,true,budget,check);
            Fasta library=null,masked=null;Path annotationPath=null;RepeatAnnotations.Table annotations=null;
            RepeatMaskAudit.Validation validation=null;RepeatMaskAudit.Report maskAudit=null;
            if(spec.library()!=null){
                Snapshot source=snapshot("library",spec.library().path(),copies.resolve("library.source"),spec.limits().inputBytes(),budget,check);snapshots.add(source);
                library=fasta(source.copy(),spec.library().encoding(),stage.resolve("library.fa"),spec,false,budget,check);
                var names=new HashSet<String>();
                for(var sequence:library.staged().sequences()){
                    String id=sequence.id();int marker=id.indexOf('#');String name=marker<0?id:id.substring(0,marker);
                    if(name.isBlank() || marker>=0&&(marker==id.length()-1||id.indexOf('#',marker+1)>=0)||!names.add(name))
                        throw new IOException("Custom repeat library needs unique base names and an unambiguous optional#class/family token");
                }
            }
            if(spec.annotations()!=null){
                var requested=spec.annotations();Snapshot source=snapshot("annotations",requested.source().path(),copies.resolve("annotations.source"),spec.limits().inputBytes(),budget,check);snapshots.add(source);
                annotationPath=stage.resolve("annotations.txt");
                try(var decoded=decoded(source.copy(),requested.source().encoding(),spec.limits().inputBytes(),check);var writer=writer(annotationPath,budget);var tee=new FilterReader(decoded){
                    @Override public int read()throws IOException{int value=in.read();if(value>=0)writer.write(value);return value;}
                    @Override public int read(char[] data,int offset,int length)throws IOException{int n=in.read(data,offset,length);if(n>0)writer.write(data,offset,n);return n;}
                }){annotations=RepeatAnnotations.read(tee,source.original().toString(),requested.format(),spec.limits().annotations());}
                try(var decoded=decoded(annotationPath,RepeatFasta.Encoding.PLAIN_UTF8,spec.limits().workspaceBytes(),check)){
                    var reread=RepeatAnnotations.read(decoded,source.original().toString(),requested.format(),spec.limits().annotations());
                    if(!annotations.equals(reread))throw new IOException("Complete annotation staging changed source columns or row population");
                }
                validation=RepeatMaskAudit.validate(annotations.hits(),spec.selection(),query.staged(),spec.limits().indexBytes(),check);
            }
            if(spec.masked()!=null){
                Snapshot source=snapshot("masked",spec.masked().path(),copies.resolve("masked.source"),spec.limits().inputBytes(),budget,check);snapshots.add(source);
                masked=fasta(source.copy(),spec.masked().encoding(),stage.resolve("supplied-masked.fa"),spec,true,budget,check);
                maskAudit=RepeatMaskAudit.compare(query.path(),RepeatFasta.Encoding.PLAIN_UTF8,masked.path(),RepeatFasta.Encoding.PLAIN_UTF8,
                        Objects.requireNonNull(annotations).hits(),spec.selection(),spec.mask(),stageLimits(spec.limits()),spec.limits().indexBytes(),check);
            }
            var warnings=new ArrayList<String>();
            warnings.add("Preparation is structural/computational evidence only;no upstream search completion or biological absence is certified.");
            if(spec.search())warnings.add("Historical paper settings/library/dependency identity require explicit review;this request never substitutes an unavailable edition.");
            if(spec.staging()!=RepeatRecipe.Staging.EXACT_SOURCE)warnings.add("Explicit staging uses ASCII ordinal query IDs and uppercase FASTA bases;original bytes,headers,case and identity mappings are retained.");
            else warnings.add("FASTA stages are plain UTF-8 with60-base LF wrapping;exact headers,identifiers and base case are preserved. Original bytes remain in snapshots.");
            writeLedger(work.resolve("preparation.json"),spec,snapshots,query,library,annotations,validation,maskAudit,warnings,budget);
            Prepared result=new Prepared(spec,work,query,library,masked,annotationPath,annotations,validation,maskAudit,snapshots,inventory(work,spec.limits(),check),warnings);
            result.verify(check);return result;
        }catch(IOException|RuntimeException failure){
            String message=failure.toString();if(message.length()>8192)message=message.substring(0,8192);
            try{Files.writeString(work.resolve("PREPARATION_FAILED.txt"),"RepeatMasker FAIL2: preparation incomplete;no analysis result admitted.\n"+message+"\nRequired action: retain this directory and original inputs,correct the cause or reviewed capacity/heap settings,and retry with a NEW work directory. Do not resume or label partial snapshots/stages as complete.\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
            catch(IOException diagnostics){failure.addSuppressed(diagnostics);}throw failure;
        }
    }
    private static List<Path> sourcePaths(RepeatRecipe.Spec spec){
        var paths=new ArrayList<Path>();paths.add(spec.recipe());paths.add(spec.query().path());if(spec.library()!=null)paths.add(spec.library().path());
        if(spec.annotations()!=null)paths.add(spec.annotations().source().path());if(spec.masked()!=null)paths.add(spec.masked().path());return paths;
    }
    private static Fasta fasta(Path source,RepeatFasta.Encoding encoding,Path stage,RepeatRecipe.Spec spec,boolean query,Budget budget,Runnable check)throws IOException{
        var before=RepeatFasta.read(source,encoding,spec.limits().fasta(),check);var mappings=new ArrayList<Mapping>();boolean aliases=spec.staging()!=RepeatRecipe.Staging.EXACT_SOURCE;
        try(var cursor=RepeatFasta.open(source,encoding,spec.limits().fasta(),check);var output=writer(stage,budget)){
            while(cursor.nextSequence()){
                String id=aliases&&query?String.format(java.util.Locale.ROOT,"Q%010d",cursor.ordinal()):cursor.id();String header=aliases&&query?id:cursor.header();
                if(spec.search()&&query&&!aliases)searchHeader(cursor.id(),cursor.header());
                output.write('>');output.write(header);output.write('\n');int column=0,value;
                while((value=cursor.base())>=0){
                    if((cursor.position()&65535)==0)check.run();
                    if(spec.search()&&(value=='U'||value=='u'))throw new IOException("Original RepeatMasker FASTA admission does not accept U;provide reviewed DNA input without an inferred U-to-T replacement");
                    if(spec.search()&&!aliases&&value>='a'&&value<='z')throw new IOException("EXACT_SOURCE search staging refuses original-parser uppercase normalization;choose the explicit ASCII_ORDINAL_QUERY_IDS_AND_UPPERCASE_FASTA policy or provide reviewed uppercase input");
                    if(aliases&&value>='a'&&value<='z')value-=32;
                    output.write(value);if(++column==60){output.write('\n');column=0;}
                }
                if(column!=0)output.write('\n');
            }
            if(!before.equals(cursor.census()))throw new IOException("Protected FASTA changed during complete staging");
        }
        var after=RepeatFasta.read(stage,RepeatFasta.Encoding.PLAIN_UTF8,stageLimits(spec.limits()),check);
        if(before.sequences().size()!=after.sequences().size())throw new IOException("FASTA stage lost records");
        for(int i=0;i<before.sequences().size();i++){
            var original=before.sequences().get(i);var staged=after.sequences().get(i);
            if(original.length()!=staged.length()||!aliases&&(!original.id().equals(staged.id())||!original.header().equals(staged.header())||!original.sequenceSha256().equals(staged.sequenceSha256())))throw new IOException("FASTA staging changed the declared identity/case/length policy");
            mappings.add(new Mapping(original.ordinal(),original.id(),original.header(),staged.id(),staged.header(),original.length(),original.sequenceSha256(),staged.sequenceSha256()));
        }
        return new Fasta(stage,before,after,mappings);
    }
    static RepeatFasta.Limits stageLimits(RepeatRecipe.Limits limits){
        var f=limits.fasta();long chars=Math.addExact(f.decodedCharacters(),Math.addExact(f.residues()/60,2L*f.sequences()+8192));
        return new RepeatFasta.Limits(limits.workspaceBytes(),chars,f.residues(),f.perSequence(),f.sequences(),f.lineCharacters(),f.headerCharacters(),f.retainedBytes());
    }
    private static void searchHeader(String id,String header)throws IOException{
        if(!id.matches("[A-Za-z0-9_][A-Za-z0-9_.:-]{0,49}")||id.endsWith(".seq")||header.chars().anyMatch(c->c<32||c>126)||header.indexOf('>')>=0
                ||!header.equals(header.strip())||header.length()>id.length()&&(!header.startsWith(id+" ")||header.charAt(id.length()+1)==' ')
                ||header.matches(".*[ACGTNXacgtnx]{25,}$"))throw new IOException("Original FastaDB would reinterpret or cannot certify this query header;choose explicit ASCII ordinal staging and retain its mapping");
    }
    private static Snapshot snapshot(String role,Path original,Path copy,long maximum,Budget budget,Runnable check)throws IOException{
        String before=hash(original,maximum,check);
        try(var input=Files.newInputStream(original);var output=output(copy,budget)){
            byte[] data=new byte[65536];long total=0;int n;
            while((n=input.read(data))>=0){check.run();total=Math.addExact(total,n);if(total>maximum)throw new IOException("Source grew beyond its complete byte capacity: "+role);output.write(data,0,n);}
        }
        if(!hash(original,maximum,check).equals(before)||!hash(copy,maximum,check).equals(before))throw new IOException("Original changed during protected snapshot: "+role);
        return new Snapshot(role,original,copy,Files.size(copy),maximum,before);
    }
    private static Reader decoded(Path path,RepeatFasta.Encoding encoding,long maximum,Runnable check)throws IOException{
        InputStream raw=new FilterInputStream(Files.newInputStream(path)){
            private long bytes;
            private void used(int n)throws IOException{check.run();if(n>0){bytes=Math.addExact(bytes,n);if(bytes>maximum)throw new IOException("Annotation source-byte capacity exceeded");}}
            @Override public int read()throws IOException{int value=in.read();used(value<0?0:1);return value;}
            @Override public int read(byte[] data,int offset,int length)throws IOException{int n=in.read(data,offset,length);used(n);return n;}
        };
        return new InputStreamReader(encoding==RepeatFasta.Encoding.GZIP_UTF8?new RepeatGzip(raw,check):raw,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
    }
    private static void writeLedger(Path path,RepeatRecipe.Spec spec,List<Snapshot> snapshots,Fasta query,Fasta library,RepeatAnnotations.Table annotations,
            RepeatMaskAudit.Validation validation,RepeatMaskAudit.Report maskAudit,List<String> warnings,Budget budget)throws IOException{
        try(var out=writer(path,budget)){
            out.write("{\n\"schema_version\":1,\"scope\":\"structural-input-preparation-only\",\"upstream_analysis_completed\":false,\n");
            out.write("\"request\":");out.write(RegenieRuntime.json(Map.of("operation",spec.operation().name(),"mode",spec.mode().name(),"profile",spec.profile(),"assembly",spec.assembly(),"recipe_sha256",spec.recipeSha256(),"staging_policy",spec.staging().name())));
            out.write(",\n\"snapshots\":[");boolean first=true;
            for(var source:snapshots){if(!first)out.write(',');first=false;out.write(RegenieRuntime.json(Map.of("role",source.role(),"original",source.original().toString(),"copy",source.copy().toString(),"bytes",source.bytes(),"sha256",source.sha256())));}
            out.write("],\n\"query_mappings\":[");first=true;
            for(var mapping:query.mappings()){
                if(!first)out.write(',');first=false;
                out.write(RegenieRuntime.json(Map.of("ordinal",mapping.ordinal(),"original_id",mapping.originalId(),"original_header",mapping.originalHeader(),"staged_id",mapping.stagedId(),"staged_header",mapping.stagedHeader(),"length",mapping.length(),"original_sequence_sha256",mapping.originalSequenceSha256(),"staged_sequence_sha256",mapping.stagedSequenceSha256())));
            }
            out.write("],\n\"counts\":");var counts=new LinkedHashMap<String,Object>();counts.put("query_records",query.staged().sequences().size());counts.put("query_bases",query.staged().residues());
            if(library!=null){counts.put("library_records",library.staged().sequences().size());counts.put("library_bases",library.staged().residues());}
            if(annotations!=null){counts.put("annotation_rows",annotations.hits().size());counts.put("selected_rows",validation.selectedRows());counts.put("union_intervals",validation.unionIntervals());counts.put("imported_upstream_completion_certified",false);}
            if(maskAudit!=null){counts.put("supplied_mask_compared_bases",maskAudit.comparedBases());counts.put("supplied_mask_covered_bases",maskAudit.coveredBases());}
            out.write(RegenieRuntime.json(counts));out.write(",\n\"warnings\":");out.write(RegenieRuntime.json(warnings));out.write("\n}\n");
        }
    }
    private static Path directory(Path path,Budget budget)throws IOException{budget.member();return Files.createDirectory(path);}
    private static BufferedWriter writer(Path path,Budget budget)throws IOException{return new BufferedWriter(new OutputStreamWriter(output(path,budget),StandardCharsets.UTF_8));}
    private static OutputStream output(Path path,Budget budget)throws IOException{
        budget.member();return new FilterOutputStream(Files.newOutputStream(path,StandardOpenOption.CREATE_NEW)){
            @Override public void write(int value)throws IOException{budget.bytes(1);out.write(value);}
            @Override public void write(byte[] data,int offset,int length)throws IOException{budget.bytes(length);out.write(data,offset,length);}
        };
    }
    private static final class Budget{
        private final long maximum;private final int files;private long bytes;private int members;
        Budget(RepeatRecipe.Limits limits){maximum=Math.max(0,limits.workspaceBytes()-DIAGNOSTIC_RESERVE);files=limits.fileCount()-1;}
        void member()throws IOException{if(++members>files)throw new IOException("Preparation file/member capacity exceeded;diagnostic slot retained");}
        void bytes(int n)throws IOException{bytes=Math.addExact(bytes,n);if(bytes>maximum)throw new IOException("Preparation workspace capacity exceeded;64KiBdiagnostic reserve retained");}
    }
    static String hash(Path path,long maximum,Runnable check)throws IOException{
        Path canonical=path.toAbsolutePath().normalize();if(!Files.isRegularFile(canonical,LinkOption.NOFOLLOW_LINKS)||!canonical.toRealPath().equals(canonical)||Files.size(canonical)>maximum)throw new IOException("Require a canonical regular RepeatMasker file within its byte capacity: "+path);
        var before=Files.readAttributes(canonical,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);MessageDigest digest;
        try{digest=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
        long bytes=0;try(var input=Files.newInputStream(canonical)){byte[] data=new byte[65536];int n;while((n=input.read(data))>=0){check.run();bytes=Math.addExact(bytes,n);if(bytes>maximum)throw new IOException("Protected file grew beyond its byte capacity");digest.update(data,0,n);}}
        var after=Files.readAttributes(canonical,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);if(before.size()!=bytes||after.size()!=bytes||!before.lastModifiedTime().equals(after.lastModifiedTime())||!Objects.equals(before.fileKey(),after.fileKey()))throw new IOException("Protected file changed while hashing: "+path);
        return HexFormat.of().formatHex(digest.digest());
    }
    private static Map<Path,String> inventory(Path work,RepeatRecipe.Limits limits,Runnable check)throws IOException{
        if(!Files.isDirectory(work,LinkOption.NOFOLLOW_LINKS)||!work.toRealPath().equals(work))throw new IOException("Preparation root changed or is linked");
        var result=new LinkedHashMap<Path,String>();long[] bytes={0};int[] count={0};
        Files.walkFileTree(work,new SimpleFileVisitor<>(){
            private void member()throws IOException{check.run();if(++count[0]>limits.fileCount())throw new IOException("Preparation member capacity exceeded");}
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attributes)throws IOException{member();return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attributes)throws IOException{member();if(!attributes.isRegularFile())throw new IOException("Linked/nonregular preparation member");bytes[0]=Math.addExact(bytes[0],attributes.size());if(bytes[0]>limits.workspaceBytes())throw new IOException("Complete preparation workspace exceeds capacity");result.put(path,hash(path,limits.workspaceBytes(),check));return FileVisitResult.CONTINUE;}
        });return Map.copyOf(result);
    }
}
