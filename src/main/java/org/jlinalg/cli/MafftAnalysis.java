/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.MafftSequences;
import org.jlinalg.sequence.MafftAddMap;

/** Complete original/prepared populations, original execution, audits and literal identity restoration. */
final class MafftAnalysis {
    private MafftAnalysis() { }
    record Prepared(MafftSequences.Data primary,MafftSequences.Data added,MafftSequences.Data population,
                    Map<String,String> originalToOpaque,Map<Path,IqtreeFiles.Identity> originals,
                    MafftBundle.Inventory inputs,Map<Path,IqtreeFiles.Identity> staged,Path preparedInput,Path preparedAdditions) {
        Prepared {originalToOpaque=Map.copyOf(originalToOpaque);originals=Map.copyOf(originals);staged=Map.copyOf(staged);}
        void verify(Path inputRoot,MafftRecipe.Capacities limits,Runnable cancellation)throws IOException {
            for(var e:originals.entrySet())if(!e.getValue().equals(IqtreeFiles.identity(e.getKey(),limits.inputBytes(),cancellation)))throw new IOException("Original MAFFT input changed: "+e.getKey());
            if(!inputs.equals(MafftBundle.inventory(inputRoot,limits.bundleBytes(),limits.bundleFiles(),cancellation)))throw new IOException("MAFFT original input snapshot changed");
            for(var e:staged.entrySet())if(!e.getValue().equals(IqtreeFiles.identity(e.getKey(),limits.inputBytes(),cancellation)))throw new IOException("Prepared MAFFT input changed: "+e.getKey());
        }
    }
    record Result(int taxa,int columns,long originalResidues,long deliberatelyDeletedResidues,long retainedResidues,
                  long sourceCaseChanges,long residueLedgerRows,Path restoredAlignment,MafftSequences.BackboneAudit backbone,
                  RsemProcess.Result process,String sourceReportedStrategy,List<String> warnings) {
        Result {warnings=List.copyOf(warnings);}
    }
    static Prepared prepare(MafftRecipe.Spec spec,Path inputs,Path staged,Runnable cancellation)throws IOException {
        MafftBundle.create(inputs);MafftBundle.create(staged);var originalFiles=new LinkedHashMap<Path,IqtreeFiles.Identity>();
        copy(spec.input(),inputs.resolve("input.original.fasta"),spec.limits().inputBytes(),originalFiles,cancellation);
        if(spec.additions()!=null)copy(spec.additions(),inputs.resolve("additions.original.fasta"),spec.limits().inputBytes(),originalFiles,cancellation);
        var primary=read(inputs.resolve("input.original.fasta"),spec,spec.operation()==MafftRecipe.Operation.ADD?MafftSequences.Layout.ALIGNMENT:MafftSequences.Layout.SEQUENCES,spec.inputGaps()==MafftRecipe.InputGaps.FORBID,cancellation);
        var added=spec.additions()==null?null:read(inputs.resolve("additions.original.fasta"),spec,MafftSequences.Layout.SEQUENCES,spec.additionGaps()==MafftRecipe.InputGaps.FORBID,cancellation);
        var population=added==null?primary:MafftSequences.combine(primary,added,spec.limits().fasta());
        for(var row:population.rows())if(row.sequence().chars().allMatch(c->c=='-'))throw new IOException("MAFFT input taxon has no ungapped residue: "+row.taxon()+"; no row is silently omitted");
        var names=population.originalToOpaque();Path a=staged.resolve("input.fasta"),b=added==null?null:staged.resolve("added.fasta");
        writePrepared(primary,a,names,spec.operation()==MafftRecipe.Operation.DE_NOVO);
        if(added!=null)writePrepared(added,b,names,true);
        var pins=new LinkedHashMap<Path,IqtreeFiles.Identity>();pins.put(a,IqtreeFiles.identity(a,spec.limits().inputBytes(),cancellation));if(b!=null)pins.put(b,IqtreeFiles.identity(b,spec.limits().inputBytes(),cancellation));
        try(var out=boundedWriter(inputs.getParent().resolve("taxon-map.json"),remaining(inputs.getParent(),spec.limits(),cancellation))){
            out.write("[\n");int index=0;
            for(var row:population.rows()){
                cancellation.run();if(index>0)out.write(",\n");out.write(MafftJson.json(Map.of("input_row_index_zero_based",index++,"original_taxon",row.taxon(),"original_header",row.originalHeader(),"opaque_taxon",names.get(row.taxon()),"role",primary.taxa().contains(row.taxon())?"primary":"added","characters",row.sequence().length(),"residues",row.sequence().chars().filter(c->c!='-').count())));
            }
            out.write("\n]\n");
        }
        return new Prepared(primary,added,population,names,originalFiles,MafftBundle.inventory(inputs,spec.limits().bundleBytes(),spec.limits().bundleFiles(),cancellation),pins,a,b);
    }
    static Result run(MafftRecipe.Spec spec,MafftRuntime.Context runtime,Path work,Runnable cancellation)throws IOException {
        Path inputs=work.resolve("inputs"),staged=work.resolve("staged");Prepared p=prepare(spec,inputs,staged,cancellation);
        var host=MafftHost.capture(runtime,work.resolve("host-admission"),spec.limits().host(),cancellation);
        MafftBundle.write(work.resolve("host-admission.json"),Map.of("scope","declared-host-bindings-only; scientific result requires complete alignment audit","snapshot_sha256",host.capture().snapshotSha256(),"profile",runtime.profile(),"limits",spec.limits().host()));
        RsemProcess.Check integrity=()->{cancellation.run();spec.verify();p.verify(inputs,spec.limits(),cancellation);host.verify(cancellation);};
        integrity.verify();Path process=work.resolve("process");var command=new ArrayList<>(runtime.command());command.addAll(spec.arguments(p.preparedInput(),p.preparedAdditions()));
        RsemProcess.Result executed;
        try{executed=RsemProcess.executeOwned(command,MafftHost.environment(process,spec.parameters().threads()),process,spec.limits().process(),integrity,cancellation,List.of(staged));}
        catch(RsemProcess.UnavailableDependency unavailable){throw new MafftRuntime.Unavailable("MAFFT original execution unavailable: "+unavailable.getMessage(),unavailable);}
        var rawOutputs=MafftBundle.inventory(process,spec.limits().process().workspaceBytes(),spec.limits().process().fileCount(),cancellation);
        var aligned=read(executed.stdout(),spec,MafftSequences.Layout.ALIGNMENT,false,cancellation);
        var reverse=new LinkedHashMap<String,String>();p.originalToOpaque().forEach((original,opaque)->reverse.put(opaque,original));
        MafftSequences.checkIdentities(p.population(),aligned,reverse,spec.parameters().outputOrder());
        MafftSequences.BackboneAudit backbone=null;MafftSequences.AlignmentAudit audit=null;MafftAddMap.Audit map=null;
        var restored=new LinkedHashMap<String,char[]>();for(var row:aligned.rows())restored.put(row.taxon(),row.sequence().toCharArray());
        Path ledger=work.resolve("residue-map.tsv");long[] ledgerRows={0},caseChanges={0};
        try(var out=boundedWriter(ledger,remaining(work,spec.limits(),cancellation))) {
            out.write(MafftOutputAudit.LEDGER_HEADER+"\n");
            if(p.added()!=null)backbone=MafftSequences.auditBackbone(p.primary(),aligned,reverse,spec.parameters().keepLength()?MafftSequences.Backbone.KEEP_LENGTH:MafftSequences.Backbone.EXPAND);
            if(spec.parameters().keepLength()) {
                restoreRows(p.primary(),aligned,p.originalToOpaque(),restored,out,ledgerRows,caseChanges,cancellation);
                Path sourceMap=Path.of(p.preparedAdditions()+".map");
                map=MafftAddMap.read(sourceMap,p.added(),aligned,reverse,spec.limits().map(),row->{
                    cancellation.run();String opaque=p.originalToOpaque().get(row.originalTaxon());char source=row.alignedColumn()<0?row.sourcePrintedResidue():restored.get(opaque)[row.alignedColumn()-1];
                    try{line(out,row.originalTaxon(),opaque,row.originalResiduePosition(),row.inputCharacterColumn(),row.originalResidue(),source,row.alignedColumn());}catch(IOException e){throw new java.io.UncheckedIOException(e);}
                    if(row.alignedColumn()>0){if(source!=row.originalResidue())caseChanges[0]++;restored.get(opaque)[row.alignedColumn()-1]=row.originalResidue();}ledgerRows[0]++;
                },cancellation);
            }else{
                audit=MafftSequences.audit(p.population(),aligned,reverse,spec.parameters().outputOrder());
                restoreRows(p.population(),aligned,p.originalToOpaque(),restored,out,ledgerRows,caseChanges,cancellation);
            }
        }
        if(ledgerRows[0]!=p.population().statistics().residues())throw new IOException("Incomplete MAFFT original-residue ledger");
        var expectedStaged=new HashSet<>(List.of("input.fasta"));if(p.added()!=null)expectedStaged.add("added.fasta");if(spec.parameters().keepLength())expectedStaged.add("added.fasta.map");
        var stageInventory=MafftBundle.inventory(staged,spec.limits().process().workspaceBytes(),spec.limits().process().fileCount(),cancellation);
        if(!stageInventory.files().keySet().equals(expectedStaged)||!stageInventory.directories().equals(Set.of("")))throw new IOException("Unexpected MAFFT staged-input/output members");
        Path canonical=work.resolve("alignment.fasta");
        try(var out=boundedWriter(canonical,remaining(work,spec.limits(),cancellation))) {
            for(var row:aligned.rows()){
                cancellation.run();String original=reverse.get(row.taxon());var input=p.population().byTaxon().get(original);out.write('>');out.write(input.originalHeader());out.write('\n');
                char[] sequence=restored.get(row.taxon());for(int pos=0;pos<sequence.length;pos+=80){out.write(sequence,pos,Math.min(80,sequence.length-pos));out.write('\n');}
            }
        }
        var canonicalData=read(canonical,spec,MafftSequences.Layout.ALIGNMENT,false,cancellation);var identityNames=new LinkedHashMap<String,String>();for(String id:p.population().taxa())identityNames.put(id,id);
        MafftSequences.checkIdentities(p.population(),canonicalData,identityNames,spec.parameters().outputOrder());
        if(!spec.parameters().keepLength())MafftSequences.audit(p.population(),canonicalData,identityNames,spec.parameters().outputOrder());
        else MafftSequences.auditBackbone(p.primary(),canonicalData,identityNames,MafftSequences.Backbone.KEEP_LENGTH);
        for(var row:aligned.rows())if(!canonicalData.byTaxon().get(reverse.get(row.taxon())).sequence().equals(new String(restored.get(row.taxon()))))throw new IOException("MAFFT complete restored row differs after write/readback");
        if(!rawOutputs.equals(MafftBundle.inventory(process,spec.limits().process().workspaceBytes(),spec.limits().process().fileCount(),cancellation)))throw new IOException("Original MAFFT outputs changed during audit");
        integrity.verify();
        var warnings=new ArrayList<String>();if(runtime.knownWarning()!=null)warnings.add(runtime.knownWarning());
        if(map!=null&&map.deletedResidues()>0)warnings.add("DELIBERATE SOURCE DELETIONS: keep-length removed "+map.deletedResidues()+" inserted residues. Every original residue, including each deletion, remains in residue-map.tsv and the original input; inspect the map before downstream use.");
        if(backbone!=null&&!backbone.gapOnlyColumnIdentityInferred())warnings.add("BACKBONE GAP-ONLY COLUMN IDENTITY UNRESOLVED: original and output counts are retained; unique identities of removed/inserted all-gap columns are not inferred.");
        if(spec.symbols()==MafftSequences.Symbols.ANY_PRINTABLE)warnings.add("Literal unusual symbols were passed with --anysymbol and audited. Their scoring/biological interpretation remains the original program's behavior; this is not an ambiguity-imputation certificate.");
        if(spec.parameters().threads()>1&&spec.maximumIterations()!=null&&spec.maximumIterations()>0&&!Integer.valueOf(0).equals(spec.parameters().iterationThreads()))warnings.add("MULTITHREADED ITERATIVE REFINEMENT can return different alignments on repeat runs. Declare iteration_threads:0 when scientifically appropriate to disable only refinement multithreading; it does not set maximum_iterations to0.");
        String strategy=scanDiagnostics(executed.stderr(),spec.limits().fasta().lineCharacters(),spec.limits().diagnosticWarnings(),warnings,cancellation);long deleted=map==null?0:map.deletedResidues();
        var result=new Result(aligned.rows().size(),aligned.columns(),p.population().statistics().residues(),deleted,canonicalData.statistics().residues(),caseChanges[0],ledgerRows[0],canonical,backbone,executed,strategy,warnings);
        if(result.retainedResidues()+result.deliberatelyDeletedResidues()!=result.originalResidues())throw new IOException("MAFFT full original/retained/deleted residue counts disagree");
        var summary=new LinkedHashMap<String,Object>();summary.put("status","complete-original-alignment-and-residue-audit");summary.put("result",result);summary.put("requested_strategy",spec.strategy());summary.put("requested_maximum_iterations",spec.maximumIterations());summary.put("source_reported_strategy",strategy);summary.put("ordinary_residue_audit",audit);summary.put("source_add_map_audit",map);summary.put("all_rows_and_output_cells_checked",true);summary.put("original_headers_and_retained_residue_case_restored",true);summary.put("biological_alignment_accuracy_certified",false);
        MafftBundle.write(work.resolve("output-audit.json"),summary);return result;
    }
    private static void copy(Path from,Path to,long maximum,Map<Path,IqtreeFiles.Identity> originals,Runnable check)throws IOException {
        var before=IqtreeFiles.identity(from,maximum,check);long count=0;
        try(var in=Files.newInputStream(from);var out=Files.newOutputStream(to,StandardOpenOption.CREATE_NEW)){byte[] block=new byte[65536];for(int n;(n=in.read(block))>=0;){check.run();count=Math.addExact(count,n);if(count>maximum)throw new IOException("Complete MAFFT input copy capacity exceeded; partial retained");out.write(block,0,n);}}
        var copied=IqtreeFiles.identity(to,maximum,check);if(count!=before.bytes()||!copied.sha256().equals(before.sha256())||!before.equals(IqtreeFiles.identity(from,maximum,check)))throw new IOException("Original MAFFT file changed during copy");originals.put(from,before);
    }
    private static MafftSequences.Data read(Path file,MafftRecipe.Spec spec,MafftSequences.Layout layout,boolean forbid,Runnable check)throws IOException {
        check.run();try(var in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){return MafftSequences.readFasta(in,spec.alphabet(),spec.symbols(),spec.headers(),layout,forbid?MafftSequences.Gaps.FORBID:MafftSequences.Gaps.ALLOW,spec.limits().fasta(),check);}
    }
    private static void writePrepared(MafftSequences.Data data,Path file,Map<String,String> whole,boolean strip)throws IOException {
        var selected=new LinkedHashMap<String,String>();for(String id:data.taxa())selected.put(id,whole.get(id));
        try(var out=Files.newBufferedWriter(file,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)){data.writeFasta(out,selected,strip,80);}
    }
    private static void restoreRows(MafftSequences.Data original,MafftSequences.Data aligned,Map<String,String> names,Map<String,char[]> restored,
                                    BufferedWriter ledger,long[] count,long[] changed,Runnable check)throws IOException {
        for(var row:original.rows()){
            String opaque=names.get(row.taxon());char[] output=restored.get(opaque);int pos=0,ungapped=0;
            for(int i=0;i<row.sequence().length();i++){
                check.run();char expected=row.sequence().charAt(i);if(expected=='-')continue;while(pos<output.length&&output[pos]=='-')pos++;
                if(pos>=output.length||upper(expected)!=upper(output[pos]))throw new IOException("MAFFT restoration changed a literal source residue");
                line(ledger,row.taxon(),opaque,++ungapped,i+1,expected,output[pos],pos+1);if(expected!=output[pos])changed[0]++;output[pos++]=expected;count[0]++;
            }
            while(pos<output.length&&output[pos]=='-')pos++;if(pos!=output.length)throw new IOException("MAFFT restoration has unaccounted output residues");
        }
    }
    private static void line(BufferedWriter out,String original,String opaque,int position,int inputColumn,char expected,char actual,int target)throws IOException {
        out.write(RegenieRuntime.json(original)+"\t"+opaque+"\t"+position+"\t"+inputColumn+"\t"+(int)expected+"\t"+(int)actual+"\t"+target+"\t"+(target<0?"SOURCE_DELETED_INSERTION":"RETAINED")+"\n");
    }
    private static char upper(char c){return c>='a'&&c<='z'?(char)(c-'a'+'A'):c;}
    private static String scanDiagnostics(Path file,int maximumLine,int maximumWarnings,List<String> warnings,Runnable check)throws IOException {
        String strategy="not explicitly identified; inspect full original stderr";boolean next=false;var line=new StringBuilder();long characters=0;
        try(var in=Files.newBufferedReader(file,StandardCharsets.UTF_8)){
            for(int c;(c=in.read())>=0;){
                if((++characters&4095)==0)check.run();
                if(c=='\r'||c=='\n'){
                    if(!line.isEmpty()){
                        String text=line.toString().strip();if(next){strategy=text;next=false;}if(text.equals("Strategy:"))next=true;
                        if(text.toLowerCase(Locale.ROOT).contains("warning")){if(warnings.size()>=maximumWarnings)throw new IOException("Complete MAFFT diagnostic warning-count capacity exceeded");warnings.add("ORIGINAL DIAGNOSTIC: "+text);}
                    }
                    line.setLength(0);
                }else{if(line.length()>=maximumLine)throw new IOException("MAFFT diagnostic line exceeds the declared FASTA/diagnostic line-character capacity");line.append((char)c);}
            }
            if(!line.isEmpty()){String text=line.toString().strip();if(next)strategy=text;if(text.toLowerCase(Locale.ROOT).contains("warning")){if(warnings.size()>=maximumWarnings)throw new IOException("Complete MAFFT diagnostic warning-count capacity exceeded");warnings.add("ORIGINAL DIAGNOSTIC: "+text);}}
        }
        check.run();return strategy;
    }
    private static long remaining(Path work,MafftRecipe.Capacities limits,Runnable check)throws IOException {
        long used=0;for(var file:MafftBundle.inventory(work,limits.bundleBytes(),limits.bundleFiles(),check).files().values())used=Math.addExact(used,file.bytes());
        long bytes=limits.bundleBytes()-used-65536;if(bytes<1)throw new IOException("MAFFT bundle has no remaining complete-output capacity");return Math.min(bytes,limits.process().fileBytes());
    }
    private static BufferedWriter boundedWriter(Path path,long maximum)throws IOException {
        var raw=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW);
        var bounded=new java.io.FilterOutputStream(raw){long count;
            private void reserve(int size)throws IOException{if(size<0||Math.addExact(count,size)>maximum)throw new IOException("Complete MAFFT output byte capacity exceeded; partial file retained");count+=size;}
            @Override public void write(int value)throws IOException{reserve(1);out.write(value);}
            @Override public void write(byte[] block,int offset,int size)throws IOException{reserve(size);out.write(block,offset,size);}
        };
        return new BufferedWriter(new java.io.OutputStreamWriter(bounded,StandardCharsets.UTF_8));
    }
}
