/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.jlinalg.genetics.MinimacReferenceBlocks;
import org.jlinalg.genetics.VcfCsiIndex;

/** Complete immutable reference/target admission and on-disk typed-call ledgers. */
final class MinimacInputs {
    private MinimacInputs() { }
    record Key(String contig, int position, String reference, String alternate) {
        String text() { return contig+"\t"+position+"\t"+reference+"\t"+alternate; }
    }
    record Marker(Key key, String id, int alternateCopies, int copies,
            OptionalDouble error, OptionalDouble recombination, OptionalDouble cm) { }
    record Reference(Path engineFile, MinimacVcf.Identity identities, List<String> columns,
            Map<Key,Marker> markers, String matrixHash, String identityHash, String modelHash,
            String logicalCopyHash, String format, long physicalRows, long blocks, long sharedBoundaries,
            Map<String,Object> audit) { }
    record TargetMarker(Key key, String id, long callOffset) { }
    record Target(Path engineFile, Path calls, MinimacVcf.Identity identities, List<String> columns,
            List<Integer> ploidies, Map<Key,TargetMarker> markers, long excluded,
            MinimacVcf.Summary audit) { }
    record Prepared(MinimacRecipe.Spec spec, MinimacRecipe.Snapshot snapshots, Reference reference,
            Target target, Map<Path,String> stagedSha256, List<String> warnings, Budget budget) {
        void verify() throws IOException {
            snapshots.verify();
            for (var e:stagedSha256.entrySet()) {
                MinimacVcf.regular(e.getKey(),spec.limits().workspaceBytes());
                if (!FollowupSupport.hash(e.getKey()).equals(e.getValue()))
                    throw new IOException("Minimac admitted stage changed: "+e.getKey());
            }
            budget.check();
        }
    }
    static final class Budget {
        final Path root; final MinimacRecipe.Spec spec;
        long retained, bytes, referenceTransient;
        Budget(Path root, MinimacRecipe.Spec spec) throws IOException {
            this.root=root;this.spec=spec;
            keep(Math.multiplyExact(8L,spec.limits().vcf().lineCharacters()),"bounded text buffers");
            check();
        }
        void keep(long value,String what) throws IOException {
            retained=Math.addExact(retained,value);
            if(retained>spec.limits().retainedBytes())throw new IOException("Estimated native retained-memory bound exceeded while admitting "+what+"; choose reviewed larger limits/heap or smaller explicit inputs");
        }
        void referenceTransient(long maximum) throws IOException {
            if(maximum>referenceTransient){keep(maximum-referenceTransient,"compressed reference row/mapping buffers");referenceTransient=maximum;}
        }
        void check() throws IOException {
            long total=0;int count=0;
            try(var paths=Files.walk(root)){
                var iterator=paths.iterator();while(iterator.hasNext()) {Path path=iterator.next();
                    if(++count>4096||Files.isSymbolicLink(path))throw new IOException("Minimac workspace file/link bound exceeded");
                    if(Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS))total=Math.addExact(total,Files.size(path));
                    else if(!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))throw new IOException("Nonregular Minimac workspace member");
                }
            }
            bytes=total;if(bytes>spec.limits().workspaceBytes())throw new IOException("Minimac whole-workspace byte bound exceeded; no partial results are admitted");
        }
        void count(int length) throws IOException {
            bytes=Math.addExact(bytes,length);
            if(bytes>spec.limits().workspaceBytes())throw new IOException("Minimac whole-workspace byte bound exceeded while writing a provisional ledger");
        }
        BufferedWriter writer(Path path) throws IOException {
            OutputStream out=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW);
            return new BufferedWriter(new OutputStreamWriter(new java.io.FilterOutputStream(out){
                @Override public void write(int value)throws IOException{count(1);out.write(value);}
                @Override public void write(byte[] data,int start,int length)throws IOException{count(length);out.write(data,start,length);}
            },StandardCharsets.UTF_8));
        }
        MinimacVcf.Limits vcfLimits() throws IOException {
            var l=spec.limits().vcf();
            long available=spec.limits().retainedBytes()-retained;
            if(available<1024)throw new IOException("No native row-buffer budget remains");
            return new MinimacVcf.Limits((int)Math.min(l.samples(),available/1024),l.variants(),l.genotypeCells(),
                    l.inputBytes(),l.decodedCharacters(),l.lineCharacters(),l.stagedBytes(),l.identityRows());
        }
    }

    static Prepared prepare(MinimacRecipe.Spec spec,Path work,MinimacRuntime.Context runtime,
            int timeout,Map<String,Object> session) throws Exception {
        var snapshots=MinimacRecipe.snapshot(spec,work);var budget=new Budget(work,spec);
        var warnings=new ArrayList<String>();
        var identities=identity(snapshots.files().get("reference-identities"),spec.reference().columns(),budget);
        Path source=snapshots.files().get("reference-source");
        Path engine;
        Reference reference;
        if(spec.reference().kind().equals("phased-vcf")){
            engine=work.resolve("reference.vcf.gz");
            reference=vcfReference(source,engine,identities,work,"reference",budget);
        }else{
            String kind=spec.reference().kind();
            if(kind.equals("msav")) {
                engine=work.resolve("reference.msav");copy(source,engine);budget.check();
                if(runtime==null)throw new IOException("MSAV inspection requires its explicitly admitted original exporter");
                snapshots.verify();
                int code=MinimacRuntime.execute(runtime,true,List.of("export","--update-info","never","-O","vcf",engine.toString()),work,"inspect-source",spec.engine().threads(),timeout,spec.limits().workspaceBytes(),session);
                snapshots.verify();if(code!=0)throw new IOException("Original complete MSAV export failed; inspect inspect-source.stderr");
                source=work.resolve("inspect-source.stdout");
                reference=blocks(source,engine,identities,work,"reference",budget);
                if(!reference.format.equals("MVCF_3_0"))throw new IOException("MSAV exporter did not produce the declared current compressed format");
            }else{
                engine=work.resolve(kind.equals("m3vcf")?"reference.m3vcf.gz":"reference.mvcf");
                reference=blocks(source,engine,identities,work,"reference",budget);
                if(kind.equals("m3vcf")&&reference.format.equals("MVCF_3_0")||kind.equals("mvcf")&&!reference.format.equals("MVCF_3_0"))
                    throw new IOException("Declared reference kind differs from native file header");
                if(kind.equals("m3vcf"))stageM3(source,engine,spec.reference().staging(),reference,budget);
                else{copy(source,engine);budget.check();}
            }
        }
        if(reference.markers.isEmpty())throw new IOException("Empty admitted reference");
        boolean full= !spec.hasTarget();
        long expected=reference.markers.keySet().stream().filter(k->inside(k,spec.region())).count();
        if(expected==0||full&&expected!=reference.markers.size())
            throw new IOException("Declared region has no expected markers or does not cover the complete reference operation; no implicit region cropping");
        if(!spec.modern()&&spec.hasTarget()&&reference.format.equals("M3VCF_2_0")&&spec.profile().equals(MinimacRecipe.MIN3))
            throw new MinimacRecipe.UnavailableCapability("Minimac3 does not consume the admitted version2 reference directly. SKIP this incompatible imputation. Required action: supply a separately validated compatible v1 reference or explicitly use a supported conversion/engine; no native format rewrite is inferred.");
        Target target=null;
        if(spec.hasTarget()){
            var targetIdentity=identity(snapshots.files().get("target-identities"),spec.target().columns(),budget);
            var overlap=new HashSet<String>();
            for(var columns:identities.contigs().values())for(var copies:columns.values())for(var c:copies)overlap.add(c.person());
            var shared=new java.util.TreeSet<String>();
            for(var columns:targetIdentity.contigs().values())for(var copies:columns.values())for(var c:copies)if(overlap.contains(c.person()))shared.add(c.person());
            if(!shared.isEmpty()){
                if(spec.sampleOverlap().equals("error"))throw new IOException("Declared logical reference/target people overlap: "+shared);
                warnings.add("REFERENCE/TARGET IDENTITY OVERLAP: "+shared.size()+" declared people occur in both roles. Required action: review the retained identity ledgers and reference eligibility; names alone do not establish biological independence.");
            }
            target=target(snapshots.files().get("target-source"),targetIdentity,reference,work,budget,warnings);
            VcfCsiIndex.create(target.engineFile,work.resolve("target.vcf.gz.csi"));budget.check();
        }
        var staged=new LinkedHashMap<Path,String>();
        try(var paths=Files.walk(work)){
            for(Path path:paths.filter(Files::isRegularFile).toList())staged.put(path,FollowupSupport.hash(path));
        }
        var result=new Prepared(spec,snapshots,reference,target,Map.copyOf(staged),List.copyOf(warnings),budget);result.verify();return result;
    }

    static MinimacVcf.Identity identity(Path path,String semantics,Budget budget)throws IOException{
        try(var lines=new MinimacVcf.Lines(path,budget.spec.limits().vcf())){
            String text;boolean header=true;long n=0;
            while((text=lines.next())!=null){
                if(header){header=false;continue;}
                if(++n>budget.spec.limits().vcf().identityRows())throw new IOException("Identity row bound exceeded");
                budget.keep(512L+12L*text.length(),"explicit identity rows");
            }
        }
        var value=MinimacVcf.identity(path,budget.vcfLimits());MinimacRecipe.grouping(value,semantics);return value;
    }
    static Reference vcfReference(Path source,Path engine,MinimacVcf.Identity identities,Path work,
            String prefix,Budget budget)throws IOException{
        var markers=new LinkedHashMap<Key,Marker>();
        try(var rows=budget.writer(work.resolve(prefix+"-selected.tsv"))){
            rows.write("contig\tposition\tref\talt\tselected_id\talternate_copies\ttotal_copies\tsource_line\tblock\terror\trecombination\tcm\n");
            var audit=MinimacVcf.read(source,MinimacVcf.Role.REFERENCE,identities,MinimacVcf.MissingPolicy.EXACT_PHASED,
                    budget.spec.phase(),budget.vcfLimits(),engine,row->MinimacVcf.Selection.retain(),(row,selection)->{
                        Key key=key(row);sameContig(key,budget.spec.region());
                        int ac=0,n=0;for(var call:row.calls())for(int allele:call.alleles()){ac+=allele;n++;}
                        budget.keep(markerBytes(key,row.id()),"reference variants");Marker marker=new Marker(key,row.id(),ac,n,OptionalDouble.empty(),OptionalDouble.empty(),OptionalDouble.empty());
                        markers.put(key,marker);selectedRow(rows,marker,row.sourceLine(),0);
                        bounds(markers.size(),auditCells(markers.size(),n),budget.spec);
                    });
            if(audit.columns().size()>budget.spec.limits().vcf().samples())throw new IOException("Reference column bound exceeded");
            identities.header(audit.columns());identities.completed(Set.of(budget.spec.region().contig()));
            String logical=logicalHash(identities,audit.columns(),budget.spec.region().contig());
            var report=Map.<String,Object>of("format","phased-vcf","vcf",MinimacJson.value(audit),"logical_copy_sha256",logical);
            writeJson(work.resolve(prefix+"-audit.json"),report,budget);
            return new Reference(engine,identities,audit.columns(),Collections.unmodifiableMap(markers),audit.input().orderedMatrixSha256(),
                    audit.input().identityPhaseSha256(),null,logical,"phased-vcf",audit.input().variants(),0,0,report);
        }finally{budget.check();}
    }
    static Reference blocks(Path source,Path engine,MinimacVcf.Identity identities,Path work,String prefix,
            Budget budget)throws IOException{
        int columns=precheckBlocks(source,budget);
        var markers=new LinkedHashMap<Key,Marker>();long[] emittedBlock={0};
        try(var selected=budget.writer(work.resolve(prefix+"-selected.tsv"));
                var physical=budget.writer(work.resolve(prefix+"-physical.jsonl"));
                var mappings=budget.writer(work.resolve(prefix+"-blocks.jsonl"));
                var boundaries=budget.writer(work.resolve(prefix+"-boundaries.jsonl"));
                var extents=budget.writer(work.resolve(prefix+"-extents.jsonl"))){
            selected.write("contig\tposition\tref\talt\tselected_id\talternate_copies\ttotal_copies\tsource_line\tblock\terror\trecombination\tcm\n");
            var audit=MinimacReferenceBlocks.read(source,budget.spec.boundary(),new MinimacReferenceBlocks.Visitor(){
                public void selected(MinimacReferenceBlocks.Row row)throws IOException{
                    Key key=new Key(row.block().contig(),row.position(),row.reference(),row.alternate());sameContig(key,budget.spec.region());
                    budget.keep(markerBytes(key,row.id()),"selected reference variants");
                    Marker marker=new Marker(key,row.id(),row.alternateCopies(),row.totalCopies(),row.error(),row.recombination(),row.centimorgans());
                    if(markers.putIfAbsent(key,marker)!=null)throw new IOException("Repeated selected compressed-reference key");
                    bounds(markers.size(),Math.multiplyExact((long)markers.size(),columns),budget.spec);
                    selectedRow(selected,marker,row.sourceLine(),row.block().number());
                }
                public void physical(MinimacReferenceBlocks.Row row)throws IOException{
                    if(emittedBlock[0]!=row.block().number()){emittedBlock[0]=row.block().number();mappings.write(MinimacJson.json(row.block()));mappings.write('\n');}
                    physical.write(MinimacJson.json(rowReport(row)));physical.write('\n');
                }
                public void boundary(MinimacReferenceBlocks.Boundary event)throws IOException{
                    boundaries.write(MinimacJson.json(Map.of("earlier",rowReport(event.earlier()),"later",rowReport(event.later()),
                            "id_changed",event.idChanged(),"parameters_changed",event.parametersChanged())));boundaries.write('\n');
                }
                public void block(MinimacReferenceBlocks.Extent extent)throws IOException{extents.write(MinimacJson.json(extent));extents.write('\n');}
            });
            identities.header(audit.columns());identities.completed(new HashSet<>(audit.contigs().stream().map(MinimacReferenceBlocks.Contig::id).toList()));
            for(var c:audit.contigs())for(int i=0;i<audit.columns().size();i++)
                if(c.columnPloidies().get(i)!=identities.copies(c.id(),audit.columns().get(i)).size())throw new IOException("Reference mappings disagree with the complete identity ledger");
            if(audit.selectedHaplotypeCells()>2L*budget.spec.limits().vcf().genotypeCells())throw new IOException("Reference haplotype-cell bound exceeded");
            String logical=logicalHash(identities,audit.columns(),budget.spec.region().contig());
            var report=Map.<String,Object>of("compressed",MinimacJson.value(audit),"logical_copy_sha256",logical);
            writeJson(work.resolve(prefix+"-audit.json"),report,budget);
            return new Reference(engine,identities,audit.columns(),Collections.unmodifiableMap(markers),audit.orderedHaplotypeMatrixSha256(),
                    audit.identityAndPhaseSha256(),audit.float32ModelParametersSha256(),logical,audit.format().name(),audit.physicalRows(),audit.blocks(),audit.sharedBoundaries(),report);
        }finally{budget.check();}
    }
    private static int precheckBlocks(Path source,Budget budget)throws IOException{
        int columns=0;
        try(var lines=new MinimacVcf.Lines(source,budget.spec.limits().vcf())){
            String text;
            while((text=lines.next())!=null){
                if(text.startsWith("##"))continue;
                String[] fields=text.split("\t",-1);
                if(text.startsWith("#CHROM\t")){
                    columns=fields.length-9;
                    if(columns<1||columns>budget.spec.limits().vcf().samples())throw new IOException("Compressed-reference column bound exceeded");
                    budget.referenceTransient(2048L*columns);continue;
                }
                if(columns>0&&fields.length==columns+9){
                    long reps=-1;
                    for(String item:fields[7].split(";"))if(item.startsWith("REPS=")){
                        try{reps=Long.parseLong(item.substring(5));}catch(NumberFormatException failure){throw new IOException("Invalid REPS capacity",failure);}
                    }
                    if(reps<1||reps>2000000)throw new IOException("Compressed reference REPS exceeds native capacity");
                    budget.referenceTransient(Math.addExact(2048L*columns,Math.multiplyExact(32L,reps)));
                }
            }
        }
        return columns;
    }
    private static Target target(Path source,MinimacVcf.Identity identities,Reference reference,Path work,
            Budget budget,List<String> warnings)throws IOException{
        var markers=new LinkedHashMap<Key,TargetMarker>();Path engine=work.resolve("target.vcf.gz"),calls=work.resolve("target.calls.bin");
        long[] offset={0},sharedObserved={0};long[] rowCount={0};
        try(var stored=Files.newOutputStream(calls,StandardOpenOption.CREATE_NEW);
                var rows=budget.writer(work.resolve("target-variants.tsv"));
                var representations=budget.writer(work.resolve("target-representations.tsv"))){
            rows.write("contig\tposition\tref\talt\ttarget_id\treference_id\tretained\tdecision\n");
            representations.write("contig\tposition\tref\talt\tcolumn_index\toriginal_GT\tstaged_GT\tmissing_representation_changed\thomozygote_phase_representation_changed\n");
            var audit=MinimacVcf.read(source,MinimacVcf.Role.TARGET,identities,budget.spec.targetMissing(),budget.spec.phase(),
                    budget.vcfLimits(),engine,row->{
                        Key key=key(row);sameContig(key,budget.spec.region());
                        if(reference.markers.containsKey(key))return MinimacVcf.Selection.retain();
                        if(budget.spec.targetOnly().equals("error"))throw new IOException("Target key is absent from the exact reference REF/ALT inventory: "+key.text()+"; no allele/strand/build transformation is inferred");
                        return new MinimacVcf.Selection(false,"EXPLICIT_TARGET_ONLY_EXCLUSION");
                    },(row,selection)->{
                        Key key=key(row);Marker match=reference.markers.get(key);
                        rows.write(key.text()+"\t"+row.id()+"\t"+(match==null?"":match.id)+"\t"+selection.keep()+"\t"+selection.reason()+"\n");
                        if(selection.keep()){
                            budget.keep(markerBytes(key,row.id()),"retained target marker offsets");
                            markers.put(key,new TargetMarker(key,row.id(),offset[0]));
                            for(var call:row.calls())for(int allele:call.alleles()){stored.write(allele<0?2:allele);offset[0]++;if(allele>=0)sharedObserved[0]++;}
                        }
                        for(int i=0;i<row.calls().size();i++){
                            var call=row.calls().get(i);
                            if(!call.original().equals(call.staged()))
                                representations.write(key.text()+"\t"+i+"\t"+call.original()+"\t"+call.staged()+"\t"+call.missingRepresentationChanged()+"\t"+call.homozygousPhaseRepresentationChanged()+"\n");
                        }
                        if(++rowCount[0]%1024==0)budget.check();
                    });
            if(markers.isEmpty()||sharedObserved[0]==0)throw new IOException("No observed shared target copies remain after explicit reference matching");
            if(audit.contigs().size()!=1||!audit.contigs().get(0).id().equals(budget.spec.region().contig()))throw new IOException("Exactly the declared target contig is required");
            if(audit.excludedVariants()>0)warnings.add("EXPLICIT TARGET-ONLY EXCLUSIONS: "+audit.excludedVariants()+" complete allele keys were excluded. Required action: review target-variants.tsv, especially any co-located allele differences; no flip/complement/normalization or panel substitution occurred.");
            if(audit.input().stagedGtRepresentationChanges()>0)warnings.add("EXPLICIT GT REPRESENTATION CHANGES: "+audit.input().stagedGtRepresentationChanges()+" calls have staged text differing from their source under the declared genotype/missing/homozygote policies. Required action: review target-representations.tsv and immutable original snapshots; no heterozygous phase was inferred.");
            writeJson(work.resolve("target-audit.json"),audit,budget);
            return new Target(engine,calls,identities,audit.columns(),audit.contigs().get(0).columnPloidies(),Collections.unmodifiableMap(markers),audit.excludedVariants(),audit);
        }finally{budget.check();}
    }
    static void indexTarget(Prepared prepared,Path work)throws IOException{
        if(prepared.target==null)throw new IOException("Target index requested without a target");
        VcfCsiIndex.create(prepared.target.engineFile,work.resolve("target.vcf.gz.csi"));prepared.budget.check();
    }
    private static void stageM3(Path source,Path target,String policy,Reference reference,Budget budget)throws IOException{
        if(policy.equals("preserve-compressed-source")){
            if(!gzip(source))throw new IOException("preserve-compressed-source requires original gzip M3; select an explicit lossless-gzip staging policy for plain text");
            copy(source,target);
        }else if(policy.equals("lossless-gzip")){
            try(var raw=Files.newInputStream(source);var decoded=gzip(source)?new GZIPInputStream(raw):raw;
                    var output=new GZIPOutputStream(Files.newOutputStream(target,StandardOpenOption.CREATE_NEW))){decoded.transferTo(output);}
        }else if(policy.equals("lf-gzip")){
            try(var lines=new MinimacVcf.Lines(source,budget.spec.limits().vcf());
                    var output=new GZIPOutputStream(Files.newOutputStream(target,StandardOpenOption.CREATE_NEW))){
                for(String line;(line=lines.next())!=null;){output.write(line.getBytes(StandardCharsets.UTF_8));output.write('\n');}
            }
        }else throw new IOException("Unsupported explicit M3 staging policy");
        budget.check();
        var staged=MinimacReferenceBlocks.audit(target,budget.spec.boundary());
        if(staged.crlfLines()>0)throw new IOException("Original M3 consumer requires LF text; preserve the original and explicitly select lf-gzip staging, without changing decoded fields");
        if(!staged.orderedHaplotypeMatrixSha256().equals(reference.matrixHash)
                ||!staged.identityAndPhaseSha256().equals(reference.identityHash)
                ||!staged.float32ModelParametersSha256().equals(reference.modelHash)
                ||staged.physicalRows()!=reference.physicalRows||staged.blocks()!=reference.blocks)
            throw new IOException("M3 staging changed decoded reference identity/phase/model/physical inventory");
    }
    static byte[] calls(FileChannel channel,Target target,TargetMarker marker)throws IOException{
        int copies=target.ploidies.stream().mapToInt(Integer::intValue).sum();byte[] values=new byte[copies];ByteBuffer buffer=ByteBuffer.wrap(values);long position=marker.callOffset;
        while(buffer.hasRemaining()){int n=channel.read(buffer,position);if(n<=0)throw new IOException("Truncated admitted target call store");position+=n;}
        for(byte value:values)if(value<0||value>2)throw new IOException("Invalid admitted target call-store state");
        return values;
    }
    static boolean inside(Key key,MinimacRecipe.Region region){return key.contig.equals(region.contig())&&key.position>=region.start()&&key.position<=region.end();}
    static Key key(MinimacVcf.Row row){return new Key(row.contig(),row.position(),row.reference(),row.alternate());}
    static void sameContig(Key key,MinimacRecipe.Region region)throws IOException{if(!key.contig.equals(region.contig()))throw new IOException("Input contig differs from exact declared region; no chr/build alias is inferred: "+key.contig);}
    private static long markerBytes(Key key,String id){return 1024L+8L*(key.contig.length()+key.reference.length()+key.alternate.length()+id.length());}
    private static long auditCells(int markers,int copies){return Math.multiplyExact((long)markers,(copies+1L)/2);}
    private static void bounds(long variants,long cells,MinimacRecipe.Spec spec)throws IOException{if(variants>spec.limits().vcf().variants()||cells>spec.limits().vcf().genotypeCells())throw new IOException("Complete reference marker/cell bound exceeded");}
    private static Map<String,Object> rowReport(MinimacReferenceBlocks.Row row){
        var result=new LinkedHashMap<String,Object>();result.put("block",row.block().number());result.put("within_block",row.withinBlock());result.put("source_line",row.sourceLine());result.put("contig",row.block().contig());result.put("position",row.position());result.put("id",row.id());result.put("ref",row.reference());result.put("alt",row.alternate());result.put("representative_alleles",row.representativeAlleles());result.put("info",row.info());result.put("flags",row.flags());result.put("alternate_copies",row.alternateCopies());result.put("actual_copies",row.totalCopies());return result;
    }
    private static void selectedRow(BufferedWriter output,Marker marker,long line,long block)throws IOException{
        output.write(marker.key.text()+"\t"+marker.id+"\t"+marker.alternateCopies+"\t"+marker.copies+"\t"+line+"\t"+block+"\t"+number(marker.error)+"\t"+number(marker.recombination)+"\t"+number(marker.cm)+"\n");
    }
    private static String number(OptionalDouble value){return value.isPresent()?Double.toString(value.getAsDouble()):".";}
    private static String logicalHash(MinimacVcf.Identity identities,List<String> columns,String contig)throws IOException{
        try {
            var digest=java.security.MessageDigest.getInstance("SHA-256");digest.update(("Minimac-ordered-person-copies-v1\n"+contig+"\n").getBytes(StandardCharsets.UTF_8));
            for(String column:columns)for(var copy:identities.copies(contig,column))digest.update((copy.person()+"\t"+copy.personCopy()+"\n").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        }catch(java.security.NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static void writeJson(Path path,Object value,Budget budget)throws IOException{try(var writer=budget.writer(path)){writer.write(MinimacJson.json(value));writer.write('\n');}}
    static void copy(Path source,Path target)throws IOException{Files.copy(source,target);if(!FollowupSupport.hash(source).equals(FollowupSupport.hash(target)))throw new IOException("Input changed during stage copy");}
    static boolean gzip(Path source)throws IOException{try(var in=Files.newInputStream(source)){return in.read()==31&&in.read()==139;}}
}

