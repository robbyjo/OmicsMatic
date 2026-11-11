/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import org.jlinalg.genetics.ShapeitCommonVariants;
import org.jlinalg.genetics.ShapeitGeneticMap;

/** Controlled original c34d ordinary-GT fitting, with complete native preparation and output audit. */
final class ShapeitCommonPhasing {
    private ShapeitCommonPhasing() { }
    enum CountPolicy { REQUIRE_SAMPLE_COUNTS, USE_DECLARED_COUNTS }
    enum MissingPolicy { REQUIRE_COMPLETE, SOURCE_WHOLE_GENOTYPE_IMPUTATION }
    record Limits(ShapeitBcf.Limits raw,ShapeitRawIndex.Limits spool,ShapeitCommonVariants.Limits selection,
                  ShapeitGeneticMap.Limits map,ShapeitCommonCalls.Limits calls,ShapeitJoinedRecords.Limits join,
                  ShapeitProcess.Limits process,long ledgerBytes,long tableBytes,long wholeWorkspaceBytes,int wholeFiles,long wholeWallMillis) {
        Limits {
            Objects.requireNonNull(raw);Objects.requireNonNull(spool);Objects.requireNonNull(selection);Objects.requireNonNull(map);Objects.requireNonNull(calls);Objects.requireNonNull(join);Objects.requireNonNull(process);
            if(ledgerBytes<24||tableBytes<1||wholeWorkspaceBytes<1048576||wholeFiles<32||wholeWallMillis<1)throw new IllegalArgumentException("Require adequate explicit complete SHAPEIT workflow capacities");
        }
        static Limits defaults(){return new Limits(ShapeitBcf.Limits.defaults(),ShapeitRawIndex.Limits.defaults(),ShapeitCommonVariants.Limits.defaults(),ShapeitGeneticMap.Limits.defaults(),ShapeitCommonCalls.Limits.defaults(),ShapeitJoinedRecords.Limits.defaults(),new ShapeitProcess.Limits(86400000,68719476736L,86400,100000000000L,1024,137438953472L,10000,64,4096,34359738368L,100),1073741824,1073741824,274877906944L,50000,172800000);}
    }
    record Request(Path runtime,Path target,Path reference,Path scaffold,Path map,String contig,String region,
                   boolean snpsOnly,Double minimumMaf,int seed,int threads,CountPolicy counts,MissingPolicy missing,Limits limits) {
        Request {
            Objects.requireNonNull(runtime);Objects.requireNonNull(target);Objects.requireNonNull(counts);Objects.requireNonNull(missing);Objects.requireNonNull(limits);
            for(String text:List.of(contig,region))if(text.isBlank()||text.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Require exact single-line SHAPEIT contig and region");
            if(seed<0||threads<1||threads>limits.process().threads()||minimumMaf!=null&&(!Double.isFinite(minimumMaf)||minimumMaf<0||minimumMaf>0.5))throw new IllegalArgumentException("Invalid typed SHAPEIT seed, thread or MAF settings");
        }
    }
    record Source(Path original,ShapeitRawIndex.Identity originalIdentity,Path staged,ShapeitRawIndex.Identity stagedIdentity) {
        void verify(Runnable check)throws IOException{
            if(!originalIdentity.equals(ShapeitRawIndex.identity(original,originalIdentity.bytes(),check))||!stagedIdentity.equals(ShapeitRawIndex.identity(staged,stagedIdentity.bytes(),check)))throw new IOException("SHAPEIT original or staged input changed; no fitting result is admitted");
        }
    }
    record Admission(long selectedRows,long selectedTargetCells,long selectedReferenceCells,long appliedScaffoldConstraints,
                     long declaredCountMismatchRows,long partiallyMissingTargetCalls,Path countReview,ShapeitRawIndex.Identity countReviewIdentity) { }
    record Result(Path work,Path output,ShapeitRawIndex.Identity outputIdentity,Path targetCounts,ShapeitRawIndex.Identity targetCountsIdentity,
                  Admission admission,List<String> warnings,Path manifest) {Result {warnings=List.copyOf(warnings);} }

    static Result run(Request request,Path destination,Runnable cancellation)throws IOException{
        Path work=destination.toAbsolutePath().normalize();if(work.getParent()==null||!work.getParent().toRealPath().equals(work.getParent()))throw new IOException("Require canonical parent and fresh SHAPEIT analysis directory");
        Path home=request.runtime().toAbsolutePath().normalize();if(work.startsWith(home)||home.startsWith(work))throw new IOException("SHAPEIT runtime and analysis output must be separate");
        Files.createDirectory(work);var sources=new ArrayList<Source>();long started=System.nanoTime();long[] lastWorkspace={0};
        Runnable check=()->{cancellation.run();if((System.nanoTime()-started)/1000000>request.limits().wholeWallMillis())throw new IllegalStateException("Complete SHAPEIT workflow wall-time capacity exceeded");
            if(System.nanoTime()-lastWorkspace[0]>100000000){try{workspace(work,request.limits(),1048576);lastWorkspace[0]=System.nanoTime();}catch(IOException error){throw new UncheckedIOException(error);}}};
        try{
            check.run();var runtime=ShapeitRuntime.open(request.runtime());Path staged=Files.createDirectory(work.resolve("staged"));var inputs=new ArrayList<ShapeitCommonData.Input>();
            for(var role:ShapeitJoinedRecords.Role.values()){
                Path original=switch(role){case TARGET->request.target();case REFERENCE->request.reference();case SCAFFOLD->request.scaffold();};if(original==null)continue;
                Path input=copy(original,staged.resolve(role.name().toLowerCase(Locale.ROOT)+".bcf"),request.limits().raw().inputBytes(),sources,check);
                try(var magic=Files.newInputStream(input)){if(magic.read()!=31||magic.read()!=139)throw new ShapeitRuntime.Unavailable("Indexed BGZF BCF is required by this fitting transport; bare BCF and VCF need separately verified staging and are not silently converted");}
                // This bounded format probe does not replace the complete BGZF/CRC/BCF audit below.
                try(var compressed=Files.newInputStream(input);var gzip=new java.util.zip.GZIPInputStream(compressed)){
                    byte[] prefix=gzip.readNBytes(5);
                    if(Arrays.equals(prefix,"##fil".getBytes(StandardCharsets.US_ASCII))||prefix.length==5&&prefix[0]=='B'&&prefix[1]=='C'&&prefix[2]=='F'&&(prefix[3]!=2||prefix[4]!=2))
                        throw new ShapeitRuntime.Unavailable("VCF staging and non-BCF2.2 transport require a separately validated workflow; the dependent analysis is skipped without conversion");
                    if(!Arrays.equals(prefix,new byte[]{'B','C','F',2,2}))throw new IOException("Compressed input is not a complete BCF2.2 header");
                }catch(EOFException|java.util.zip.ZipException malformed){
                    throw new IOException("Compressed input header is truncated or invalid; supply a complete BGZF BCF2.2 file and matching CSI, then rerun to a fresh directory",malformed);
                }
                Path index=copy(Path.of(original.toString()+".csi"),Path.of(input+".csi"),request.limits().join().resourceBytes(),sources,check);
                var store=ShapeitRawIndex.build(input,work.resolve("spool-"+role.name().toLowerCase(Locale.ROOT)),request.limits().raw(),request.limits().spool(),check);
                header(store.header());
                var resources=List.of(ShapeitJoinedRecords.Resource.bind("B",input,input.toString(),request.limits().join().resourceBytes(),check),ShapeitJoinedRecords.Resource.bind("I",index,index.toString(),request.limits().join().resourceBytes(),check));
                inputs.add(new ShapeitCommonData.Input(role,store,resources));
            }
            int population=inputs.stream().filter(i->i.role()!=ShapeitJoinedRecords.Role.SCAFFOLD).mapToInt(i->i.store().header().samples().size()).sum();if(population<50)throw new IOException("Original common fitting requires at least 50 target plus reference samples; scaffold samples do not count");
            Path map=request.map()==null?null:copy(request.map(),staged.resolve("genetic.map.tsv"),Math.multiplyExact(4L,request.limits().map().totalCharacters()),sources,check);
            ShapeitProcess.Check integrity=()->{for(var source:sources)source.verify(check);for(var input:inputs)input.store().verify();};
            Path protocol=work.resolve("original.join.tsv");var joinArgs=new ArrayList<String>(List.of(request.region(),protocol.toString()));for(var input:inputs)joinArgs.add(input.store().source().toString());
            var joined=ShapeitProcess.run(runtime,ShapeitRuntime.Program.JOIN,joinArgs,work.resolve("join-process"),request.limits().process(),integrity,check);
            var capture=ShapeitRawIndex.identity(protocol,request.limits().join().protocolBytes(),check);
            var spec=new ShapeitCommonData.Spec(request.contig(),request.region(),request.snpsOnly(),request.minimumMaf(),map,request.limits().selection(),request.limits().map(),request.limits().calls(),request.limits().join(),request.limits().ledgerBytes());
            var prepared=ShapeitCommonData.prepare(spec,inputs,protocol,capture.sha256(),work.resolve("prepared"),check);
            var admission=admit(prepared,request.counts(),request.missing(),work.resolve("input-count-review.tsv"),request.limits().tableBytes(),check);
            Path output=work.resolve("phased.bcf");var arguments=new ArrayList<String>(List.of("--input",inputs.get(0).store().source().toString(),"--region",request.region(),"--output",output.toString(),"--seed",Integer.toString(request.seed())));
            for(var input:inputs)if(input.role()!=ShapeitJoinedRecords.Role.TARGET){arguments.add(input.role()==ShapeitJoinedRecords.Role.REFERENCE?"--reference":"--scaffold");arguments.add(input.store().source().toString());}
            if(map!=null){arguments.add("--map");arguments.add(map.toString());}if(request.snpsOnly())arguments.add("--filter-snp");if(request.minimumMaf()!=null){arguments.add("--filter-maf");arguments.add(Double.toString(request.minimumMaf()));}
            // Preserve defaulted thread=1. Making it explicit changes an upstream warning and is unnecessary.
            if(request.threads()!=1){arguments.add("--thread");arguments.add(Integer.toString(request.threads()));}
            ShapeitProcess.Check fitIntegrity=()->{integrity.verify();prepared.verify(check);if(!admission.countReviewIdentity().equals(ShapeitRawIndex.identity(admission.countReview(),request.limits().tableBytes(),check)))throw new IOException("SHAPEIT input admission evidence changed");};
            var fitted=ShapeitProcess.run(runtime,ShapeitRuntime.Program.COMMON,arguments,work.resolve("fit-process"),request.limits().process(),fitIntegrity,check);
            var audit=ShapeitCommonOutputs.compare(prepared,output,request.limits().raw(),work.resolve("output-audit"),request.limits().tableBytes(),check);
            var warnings=new ArrayList<>(audit.warnings());
            if(admission.declaredCountMismatchRows()!=0)warnings.add("DECLARED COUNT FILTERING: "+admission.declaredCountMismatchRows()+" selected panel records have AC/AN different from physical called genotypes. The source scanner used the original declared counts under USE_DECLARED_COUNTS. Review input-count-review.tsv before interpreting filtering; original metadata were not retagged.");
            if(request.map()==null)warnings.add("UNIFORM GENETIC MAP: original 1 cM/Mb default was used. Genome-build and study suitability require investigator review.");
            if(request.threads()>1)warnings.add("MULTITHREADING: the upstream runtime warns that a fixed seed does not guarantee reproducibility across threaded runs.");
            warnings.add("SCOPE: original c34d ordinary diploid-GT common stage only, before optional pedigree/haploid processing. Binary genotypes, phase-set/pedigree/haploid options, rare phasing, ligation and historical singleton-posterior workflows are not admitted. Genotype preservation is not phase-accuracy or convergence validation.");
            fitIntegrity.verify();runtime.verify();check.run();var outputIdentity=ShapeitRawIndex.identity(output,request.limits().raw().inputBytes(),check);
            Path manifest=work.resolve("analysis.json");var record=new LinkedHashMap<String,Object>();record.put("status","completed-audited-common-stage");record.put("profile",runtime.profile());record.put("runtime",runtime);record.put("request",request);record.put("source_lineage",sources);record.put("join_process",joined);record.put("fit_process",fitted);record.put("capture",capture);record.put("admission",admission);record.put("output",output);record.put("output_identity",outputIdentity);record.put("target_counts",audit.targetCounts());record.put("target_counts_identity",audit.targetCountsIdentity());record.put("source_AN_includes_reference_rows",audit.sourceANIncludesReferenceRows());record.put("warnings",warnings);record.put("scientific_result_admitted",true);record.put("phase_accuracy_certified",false);
            String diagnostic=ShapeitJson.json(record)+"\n",warning="# SHAPEIT analysis warnings\n\n"+String.join("\n\n",warnings)+"\n\nRequired action: preserve original output and use the verified target-count table for target-specific allele frequencies. Review all warnings before interpretation. Correct a failed analysis and rerun to a fresh output directory.\n";
            workspace(work,request.limits(),diagnostic.getBytes(StandardCharsets.UTF_8).length+warning.getBytes(StandardCharsets.UTF_8).length+4096L);
            Files.writeString(work.resolve("WARNINGS.md"),warning,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);Files.writeString(manifest,diagnostic,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            Files.writeString(work.resolve("status.txt"),"COMPLETE AUDITED COMMON-STAGE ANALYSIS\nInspect WARNINGS.md and analysis.json before interpretation. Phase accuracy is not certified.\n",StandardOpenOption.CREATE_NEW);
            return new Result(work,output,outputIdentity,audit.targetCounts(),audit.targetCountsIdentity(),admission,warnings,manifest);
        }catch(IOException|RuntimeException error){
            boolean skip=error instanceof ShapeitRuntime.Unavailable;String text=(skip?"SKIP3 runtime unavailable":"FAIL2 no scientific result is admitted")+"\n"+error+"\nRequired action: preserve original inputs, failed state and logs. Supply the permitted exact runtime or correct the named input/capacity/integrity cause, then rerun to a fresh output directory. Never admit a partial output.\n";
            try{Files.writeString(work.resolve("status.txt"),text,StandardOpenOption.CREATE_NEW);Files.writeString(work.resolve("WARNINGS.md"),"# "+text,StandardOpenOption.CREATE_NEW);}catch(IOException diagnostic){error.addSuppressed(diagnostic);}
            if(skip)throw (ShapeitRuntime.Unavailable)error;throw new IOException("SHAPEIT common analysis failed; no result is admitted: "+(error.getMessage()==null?error.toString():error.getMessage()),error);
        }
    }
    static Admission admit(ShapeitCommonData.Prepared prepared,CountPolicy countPolicy,MissingPolicy missingPolicy,Path review,long tableBytes,Runnable check)throws IOException{
        if(!prepared.selection().risks().isEmpty())throw new IOException("Common input has source scanner metadata/format risks requiring correction before fitting: "+prepared.selection().risks());
        var forbidden=EnumSet.copyOf(EnumSet.allOf(ShapeitCommonCalls.Risk.class));forbidden.remove(ShapeitCommonCalls.Risk.OBSERVED_ALLELES_IGNORED_IN_TARGET_MISSING_CALL);forbidden.remove(ShapeitCommonCalls.Risk.UNMATCHED_SCAFFOLD_SAMPLE);
        if(prepared.risks().stream().anyMatch(forbidden::contains))throw new IOException("Common input has unadmitted missingness/ploidy encoding: "+prepared.risks());
        if(!prepared.callsHeader().scaffold().isEmpty()&&prepared.totals().scaffoldApplied()==0)throw new IOException("Configured scaffold applies zero constraints; correct sample/site/phase compatibility before scaffold-assisted fitting");
        var readers=new EnumMap<ShapeitJoinedRecords.Role,ShapeitRawIndex.Reader>(ShapeitJoinedRecords.Role.class);long[] counts={0,0,0};
        try(var ledger=new DataInputStream(new BufferedInputStream(Files.newInputStream(prepared.ledger())));var stream=Files.newOutputStream(review,StandardOpenOption.CREATE_NEW)){
            var output=new Table(stream,tableBytes);output.line("selected_ordinal\tpanel\tcontig\tposition\tid\tdeclared_AC\tdeclared_AN\tcalled_AC\tcalled_AN\tcounts_match");
            for(var input:prepared.inputs())readers.put(input.role(),input.store().open());
            for(var site:prepared.selection().selectedSites()){
                check.run();long[] ordinals={ledger.readLong(),ledger.readLong(),ledger.readLong()};
                for(var role:ShapeitJoinedRecords.Role.values()){
                    var row=ShapeitCommonData.row(readers,role,ordinals[role.ordinal()]);if(row==null||row.alternates().size()!=1)continue;
                    for(String field:List.of(row.contig(),row.id(),row.reference(),row.alternates().get(0)))if(field.codePoints().anyMatch(Character::isISOControl))throw new IOException("Selected site has control characters incompatible with exact count-table identities");
                    int samples=switch(role){case TARGET->prepared.callsHeader().target().size();case REFERENCE->prepared.callsHeader().reference().size();case SCAFFOLD->prepared.callsHeader().scaffold().size();};var calls=ShapeitCommonData.calls(row,samples);long ac=0,an=0;
                    for(var call:calls){
                        int a=call.values().get(0),b=call.values().get(1);for(int wire:List.of(a,b)){if(wire!=0&&wire!=2&&wire!=3&&wire!=4&&wire!=5)throw new IOException("Selected GT has unadmitted phased-missing, vector-end or non-biallelic encoding");if(wire!=0){an++;ac+=(wire>>1)-1;}}
                        if(role==ShapeitJoinedRecords.Role.TARGET){if((a==0)^(b==0))counts[2]++;if(missingPolicy==MissingPolicy.REQUIRE_COMPLETE&&(a==0||b==0))throw new IOException("Target missing GT violates REQUIRE_COMPLETE; choose an appropriate explicit missing-call policy or correct the input");}
                        else if(a==0||b==0||(b&1)==0)throw new IOException("Selected reference/scaffold GT must be complete phased diploid calls; correct the input before fitting");
                    }
                    long declaredAC=integer(row,"AC"),declaredAN=integer(row,"AN");boolean same=declaredAC==ac&&declaredAN==an;
                    if(!same){counts[1]++;if(countPolicy==CountPolicy.REQUIRE_SAMPLE_COUNTS)throw new IOException("Declared AC/AN differs from called selected "+role+" genotypes; review metadata or use an appropriate explicit declared-count policy");}
                    output.line((counts[0]+1)+"\t"+role+"\t"+row.contig()+"\t"+row.position()+"\t"+row.id()+"\t"+declaredAC+"\t"+declaredAN+"\t"+ac+"\t"+an+"\t"+same);
                }counts[0]++;
            }
            if(ledger.read()!=-1)throw new IOException("Complete scientific admission ledger did not reach EOF");
        }finally{for(var reader:readers.values())reader.close();}
        prepared.verify(check);return new Admission(counts[0],Math.multiplyExact(counts[0],prepared.callsHeader().target().size()),Math.multiplyExact(counts[0],prepared.callsHeader().reference().size()),prepared.totals().scaffoldApplied(),counts[1],counts[2],review,ShapeitRawIndex.identity(review,tableBytes,check));
    }
    private static long integer(ShapeitBcf.Row row,String name)throws IOException{var field=row.info().get(name);if(field==null||field.type()<1||field.type()>3)throw new IOException("Missing ordinary selected integer INFO/"+name);int[] values=field.cellIntegers(0);if(values.length!=1||values[0]<0)throw new IOException("Invalid selected INFO/"+name);return values[0];}
    private static void header(ShapeitBcf.HeaderView header)throws IOException{
        if(header.infoTags().contains("SEEK"))throw new ShapeitRuntime.Unavailable("XCF binary genotype transport requires a separately validated workflow; dependent fitting is skipped");
        var seen=new HashSet<String>();if(header.samples().isEmpty())throw new IOException("Common input must contain a nonempty sample population");
        for(String name:header.samples())if(name.isEmpty()||name.codePoints().anyMatch(Character::isISOControl)||!seen.add(name))throw new IOException("Require exact unique nonempty single-line sample names before fitting");
    }
    private static Path copy(Path supplied,Path staged,long maximum,List<Source> sources,Runnable check)throws IOException{
        Path original=supplied.toAbsolutePath().normalize();var before=ShapeitRawIndex.identity(original,maximum,check);long total=0;
        try(var input=Files.newInputStream(original);var output=Files.newOutputStream(staged,StandardOpenOption.CREATE_NEW)){byte[] block=new byte[1048576];for(int n;(n=input.read(block))!=-1;){check.run();total=Math.addExact(total,n);if(total>maximum)throw new IOException("Complete staged input exceeds capacity");output.write(block,0,n);}}
        var after=ShapeitRawIndex.identity(original,maximum,check);var copy=ShapeitRawIndex.identity(staged,maximum,check);if(!before.equals(after)||!before.equals(copy))throw new IOException("Input changed while staging a complete immutable analysis snapshot");sources.add(new Source(original,before,staged,copy));return staged;
    }
    private static final class Table {private final OutputStream output;private final long maximum;private long bytes;Table(OutputStream output,long maximum){this.output=output;this.maximum=maximum;}void line(String line)throws IOException{byte[] raw=(line+"\n").getBytes(StandardCharsets.UTF_8);if((bytes=Math.addExact(bytes,raw.length))>maximum)throw new IOException("Complete input count-review table exceeds capacity");output.write(raw);}}
    private static void workspace(Path work,Limits limits,long reserve)throws IOException{
        long[] bytes={reserve};int[] files={0};Files.walkFileTree(work,new SimpleFileVisitor<>(){
            @Override public FileVisitResult preVisitDirectory(Path path,BasicFileAttributes attrs)throws IOException{if(++files[0]>limits.wholeFiles()||Files.isSymbolicLink(path))throw new IOException("Whole SHAPEIT workspace link/file-count capacity exceeded");return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs)throws IOException{if(!attrs.isRegularFile()||++files[0]>limits.wholeFiles()||(bytes[0]=Math.addExact(bytes[0],attrs.size()))>limits.wholeWorkspaceBytes())throw new IOException("Whole SHAPEIT workspace capacity/nonregular output failure");return FileVisitResult.CONTINUE;}
            @Override public FileVisitResult visitFileFailed(Path path,IOException error)throws IOException{if(error instanceof NoSuchFileException)return FileVisitResult.CONTINUE;throw error;}
        });if(bytes[0]>limits.wholeWorkspaceBytes())throw new IOException("Whole SHAPEIT workspace diagnostic reserve exceeds capacity");
    }
}
