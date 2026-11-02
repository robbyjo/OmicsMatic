/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.jlinalg.sequence.MafftAddMap;
import org.jlinalg.sequence.MafftSequences;

/** Portable complete source/restored-cell and original-residue-ledger replay; no aligner is launched. */
final class MafftOutputAudit {
    private MafftOutputAudit() { }
    static final String LEDGER_HEADER="original_taxon_json\topaque_taxon\toriginal_ungapped_position\toriginal_character_column\toriginal_character_codepoint\tsource_character_codepoint\taligned_column_or_minus1\tstate";
    static void verify(Path root)throws IOException {
        for(String name:List.of("alignment.fasta","output-audit.json","taxon-map.json","residue-map.tsv","session.json","runtime-admission.json","host-admission.json","recipe-request.json","recipe-replay.json","inputs/input.original.fasta","staged/input.fasta","process/process.stdout","process/process.stderr","process/process.json","host-admission/metadata/snapshot.json"))if(!Files.isRegularFile(root.resolve(name),LinkOption.NOFOLLOW_LINKS))throw new IOException("Incomplete MAFFT analysis family: "+name);
        Map<?,?> replay=object(SimpleJson.parse(Files.readString(root.resolve("recipe-replay.json"),StandardCharsets.UTF_8)));
        if(!"inputs/input.original.fasta".equals(replay.get("input"))||replay.containsKey("additions")&&!"inputs/additions.original.fasta".equals(replay.get("additions")))throw new IOException("MAFFT portable replay cannot reference files outside the completed bundle");
        var spec=MafftRecipe.read(root.resolve("recipe-replay.json"));
        IqtreeFiles.identity(spec.input(),spec.limits().inputBytes(),()->{});if(spec.additions()!=null)IqtreeFiles.identity(spec.additions(),spec.limits().inputBytes(),()->{});
        var primary=read(spec.input(),spec,spec.operation()==MafftRecipe.Operation.ADD?MafftSequences.Layout.ALIGNMENT:MafftSequences.Layout.SEQUENCES);
        var added=spec.additions()==null?null:read(spec.additions(),spec,MafftSequences.Layout.SEQUENCES);
        var original=added==null?primary:MafftSequences.combine(primary,added,spec.limits().fasta());
        var raw=read(root.resolve("process/process.stdout"),spec,MafftSequences.Layout.ALIGNMENT);
        var restored=read(root.resolve("alignment.fasta"),spec,MafftSequences.Layout.ALIGNMENT);
        var names=original.originalToOpaque();var reverse=new LinkedHashMap<String,String>();names.forEach((id,opaque)->reverse.put(opaque,id));
        MafftSequences.checkIdentities(original,raw,reverse,spec.parameters().outputOrder());
        var same=new LinkedHashMap<String,String>();for(String id:original.taxa())same.put(id,id);
        MafftSequences.checkIdentities(original,restored,same,spec.parameters().outputOrder());
        if(restored.columns()!=raw.columns())throw new IOException("MAFFT restored width differs from its complete source alignment");
        for(int index=0;index<raw.rows().size();index++){
            var source=raw.rows().get(index);var target=restored.rows().get(index);String id=reverse.get(source.taxon());
            if(!target.taxon().equals(id)||!target.originalHeader().equals(original.byTaxon().get(id).originalHeader()))throw new IOException("MAFFT restored header/order differs from the complete source mapping");
            for(int column=0;column<raw.columns();column++)if(upper(source.sequence().charAt(column))!=upper(target.sequence().charAt(column)))throw new IOException("MAFFT restoration changed a source cell/gap at column "+(column+1));
        }
        verifyTaxonMap(root.resolve("taxon-map.json"),primary,original,names,spec.limits().fasta().headerCharacters());
        long deleted=0;int maximum=(int)Math.min(Integer.MAX_VALUE,(long)spec.limits().fasta().headerCharacters()*6+1024);
        try(var ledger=new BufferedReader(Files.newBufferedReader(root.resolve("residue-map.tsv"),StandardCharsets.UTF_8))){
            if(!LEDGER_HEADER.equals(line(ledger,maximum)))throw new IOException("Unknown MAFFT residue-ledger schema");
            if(added!=null) {
                MafftSequences.auditBackbone(primary,raw,reverse,spec.parameters().keepLength()?MafftSequences.Backbone.KEEP_LENGTH:MafftSequences.Backbone.EXPAND);
                MafftSequences.auditBackbone(primary,restored,same,spec.parameters().keepLength()?MafftSequences.Backbone.KEEP_LENGTH:MafftSequences.Backbone.EXPAND);
            }
            if(spec.parameters().keepLength()) {
                verifyRows(primary,raw,restored,names,ledger,maximum);
                var map=MafftAddMap.read(root.resolve("staged/added.fasta.map"),added,raw,reverse,spec.limits().map(),row->{
                    String opaque=names.get(row.originalTaxon());char actual=row.alignedColumn()<0?row.sourcePrintedResidue():raw.byTaxon().get(opaque).sequence().charAt(row.alignedColumn()-1);
                    try{verifyLine(ledger,maximum,row.originalTaxon(),opaque,row.originalResiduePosition(),row.inputCharacterColumn(),row.originalResidue(),actual,row.alignedColumn());}catch(IOException failure){throw new java.io.UncheckedIOException(failure);}
                    if(row.alignedColumn()>0&&restored.byTaxon().get(row.originalTaxon()).sequence().charAt(row.alignedColumn()-1)!=row.originalResidue())throw new IllegalArgumentException("MAFFT mapped original residue case was not restored");
                },()->{});deleted=map.deletedResidues();
            }else{
                MafftSequences.audit(original,raw,reverse,spec.parameters().outputOrder());MafftSequences.audit(original,restored,same,spec.parameters().outputOrder());
                verifyRows(original,raw,restored,names,ledger,maximum);
            }
            if(line(ledger,maximum)!=null)throw new IOException("Extra MAFFT residue-ledger row; no prefix ledger is admitted");
        }catch(java.io.UncheckedIOException failure){throw failure.getCause();}
        Map<?,?> audit=object(SimpleJson.parse(Files.readString(root.resolve("output-audit.json"),StandardCharsets.UTF_8))),result=object(audit.get("result"));
        if(!"complete-original-alignment-and-residue-audit".equals(audit.get("status"))||!Boolean.TRUE.equals(audit.get("all_rows_and_output_cells_checked"))||!Boolean.TRUE.equals(audit.get("original_headers_and_retained_residue_case_restored"))||!Boolean.FALSE.equals(audit.get("biological_alignment_accuracy_certified")))throw new IOException("MAFFT output-audit scope/status differs");
        for(var e:Map.of("taxa",(long)raw.rows().size(),"columns",(long)raw.columns(),"originalResidues",original.statistics().residues(),"deliberatelyDeletedResidues",deleted,"retainedResidues",restored.statistics().residues(),"residueLedgerRows",original.statistics().residues()).entrySet())if(!e.getValue().equals(result.get(e.getKey())))throw new IOException("MAFFT complete audit population differs: "+e.getKey());
        if(restored.statistics().residues()+deleted!=original.statistics().residues())throw new IOException("MAFFT full original/retained/deleted population differs");
        Map<?,?> process=object(SimpleJson.parse(Files.readString(root.resolve("process/process.json"),StandardCharsets.UTF_8)));
        if(!"completed-process-only".equals(process.get("status"))||!Long.valueOf(0).equals(process.get("exit_code"))||!List.of().equals(process.get("owned_live_after_cleanup"))||!List.of(0L).equals(process.get("accepted_process_exit_codes")))throw new IOException("MAFFT process/owned cleanup is not a strict completed zero-exit run");
        Map<?,?> runtime=object(SimpleJson.parse(Files.readString(root.resolve("runtime-admission.json"),StandardCharsets.UTF_8))),host=object(SimpleJson.parse(Files.readString(root.resolve("host-admission.json"),StandardCharsets.UTF_8)));
        if(!spec.profile().equals(runtime.get("profile"))||!spec.profile().equals(host.get("profile"))||!IqtreeFiles.identity(root.resolve("host-admission/metadata/snapshot.json"),67108864,()->{}).sha256().equals(host.get("snapshot_sha256")))throw new IOException("MAFFT declared runtime/host binding identity differs");
    }
    private static MafftSequences.Data read(Path path,MafftRecipe.Spec spec,MafftSequences.Layout layout)throws IOException {
        IqtreeFiles.identity(path,Math.max(spec.limits().inputBytes(),spec.limits().process().fileBytes()),()->{});
        return MafftSequences.readFasta(path,spec.alphabet(),spec.symbols(),spec.headers(),layout,MafftSequences.Gaps.ALLOW,spec.limits().fasta());
    }
    private static void verifyTaxonMap(Path path,MafftSequences.Data primary,MafftSequences.Data all,Map<String,String> names,int headerCapacity)throws IOException {
        int maximum=(int)Math.min(Integer.MAX_VALUE,(long)headerCapacity*12+2048);
        try(var reader=new BufferedReader(Files.newBufferedReader(path,StandardCharsets.UTF_8))){
            if(!"[".equals(line(reader,maximum)))throw new IOException("MAFFT canonical taxon-map opening is missing");
            for(int index=0;index<all.rows().size();index++){
                String text=line(reader,maximum);if(text==null)throw new IOException("Truncated MAFFT full taxon map");
                if(index<all.rows().size()-1){if(!text.endsWith(","))throw new IOException("Incomplete MAFFT taxon-map row delimiter");text=text.substring(0,text.length()-1);}
                var expected=all.rows().get(index);Map<?,?> row=object(SimpleJson.parse(text));
                if(!row.keySet().equals(Set.of("input_row_index_zero_based","original_taxon","original_header","opaque_taxon","role","characters","residues"))
                        ||!Long.valueOf(index).equals(row.get("input_row_index_zero_based"))||!expected.taxon().equals(row.get("original_taxon"))||!expected.originalHeader().equals(row.get("original_header"))||!names.get(expected.taxon()).equals(row.get("opaque_taxon"))
                        ||!(primary.taxa().contains(expected.taxon())?"primary":"added").equals(row.get("role"))||!Long.valueOf(expected.sequence().length()).equals(row.get("characters"))||!Long.valueOf(expected.sequence().chars().filter(c->c!='-').count()).equals(row.get("residues")))throw new IOException("MAFFT full taxon-map row differs from its original input");
            }
            if(!"]".equals(line(reader,maximum))||line(reader,maximum)!=null)throw new IOException("Extra or incomplete MAFFT taxon-map rows");
        }
    }
    private static void verifyRows(MafftSequences.Data original,MafftSequences.Data raw,MafftSequences.Data restored,Map<String,String> names,BufferedReader ledger,int maximum)throws IOException {
        for(var row:original.rows()){
            String opaque=names.get(row.taxon());var output=raw.byTaxon().get(opaque);int position=0,ungapped=0;
            for(int i=0;i<row.sequence().length();i++){
                char expected=row.sequence().charAt(i);if(expected=='-')continue;while(position<output.sequence().length()&&output.sequence().charAt(position)=='-')position++;
                if(position>=output.sequence().length()||restored.byTaxon().get(row.taxon()).sequence().charAt(position)!=expected)throw new IOException("MAFFT original residue/case restoration differs");
                verifyLine(ledger,maximum,row.taxon(),opaque,++ungapped,i+1,expected,output.sequence().charAt(position),position+1);position++;
            }
        }
    }
    private static void verifyLine(BufferedReader input,int maximum,String original,String opaque,int position,int inputColumn,char before,char source,int target)throws IOException {
        String text=line(input,maximum);if(text==null)throw new IOException("Truncated MAFFT original-residue ledger");String[] fields=text.split("\t",-1);
        if(fields.length!=8||!original.equals(SimpleJson.parse(fields[0]))||!opaque.equals(fields[1])||!Integer.toString(position).equals(fields[2])||!Integer.toString(inputColumn).equals(fields[3])||!Integer.toString(before).equals(fields[4])||!Integer.toString(source).equals(fields[5])||!Integer.toString(target).equals(fields[6])||!(target<0?"SOURCE_DELETED_INSERTION":"RETAINED").equals(fields[7]))throw new IOException("MAFFT residue-ledger identity, coordinate, character or deletion state differs");
    }
    private static String line(BufferedReader in,int maximum)throws IOException {
        var text=new StringBuilder();for(int c;(c=in.read())>=0;){if(c=='\n')return text.toString();if(c=='\r')throw new IOException("MAFFT canonical ledger uses explicit LF records");if(text.length()>=maximum)throw new IOException("Complete MAFFT ledger line exceeds header-derived capacity");text.append((char)c);}return text.isEmpty()?null:text.toString();
    }
    private static char upper(char c){return c>='a'&&c<='z'?(char)(c-'a'+'A'):c;}
    private static Map<?,?> object(Object value)throws IOException{if(!(value instanceof Map<?,?> map))throw new IOException("Require complete MAFFT audit object");return map;}
}
