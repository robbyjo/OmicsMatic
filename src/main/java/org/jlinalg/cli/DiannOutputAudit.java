/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.jlinalg.cli.NextflowFiles.*;

/** Complete original table consistency. Original files are never rewritten or numerically repaired.
 * Runtime execution, provenance, full raw-input capture and overall admission belong to the caller.
 */
final class DiannOutputAudit {
    private DiannOutputAudit() { }
    record Run(String fileName,String name) { }
    record Capacity(DiannReportRows.Limits tables,DiannReference.Limits reference,long joinEntries) {
        Capacity {if(tables==null||reference==null||joinEntries<1||joinEntries>1000000000L)throw new IllegalArgumentException("Invalid explicit DIA-NN audit capacities");}
    }
    private record Key(String file,String id) { }
    private static final List<String> KINDS=List.of("pr","pg","gg","unique_genes");
    private static final Set<String> STRINGS=Set.of("File.Name","Run","Channel","Protein.Group","Protein.Ids","Protein.Names","Genes","Modified.Sequence","Stripped.Sequence","Precursor.Id","First.Protein.Description","Site.Occupancy.Probabilities","Protein.Sites");
    private static final Set<String> ARRAYS=Set.of("Fragment.Quant.Raw","Fragment.Quant.Corrected","Fragment.Correlations");
    private static final Set<String> OPTIONAL=Set.of("Translated.Quality","Genes.Quantity","Genes.Normalised","Genes.MaxLFQ","Genes.MaxLFQ.Unique");
    private static final Pattern DECIMAL=Pattern.compile("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?");
    private static final Pattern MODIFICATION=Pattern.compile("\\(UniMod:(4|35)\\)");

    static Map<String,Object> audit(Path outputs,Path fasta,String edition,List<Run> runs,String executionOutputs,
            double reportQ,Capacity capacity,Path newScratch,Runnable check)throws IOException {
        return audit(outputs,fasta,edition,runs,executionOutputs,reportQ,false,capacity,newScratch,check);
    }
    static Map<String,Object> audit(Path outputs,Path fasta,String edition,List<Run> runs,String executionOutputs,
            double reportQ,boolean matchBetweenRuns,Capacity capacity,Path newScratch,Runnable check)throws IOException {
        require(Double.isFinite(reportQ)&&reportQ>=.01&&reportQ<=.05,"Require an explicit report q-threshold in0.01..0.05; matrices use original1percent filters");
        require(runs!=null&&!runs.isEmpty()&&runs.size()<=10000,"Require complete explicit ordered raw-run identity");
        var byFile=new LinkedHashMap<String,Run>();var byName=new LinkedHashMap<String,Run>();
        for(Run run:runs)require(run!=null&&literal(run.fileName())&&literal(run.name())&&byFile.putIfAbsent(run.fileName(),run)==null&&byName.putIfAbsent(run.name(),run)==null,"Blank, control or duplicate run identity");
        require(literal(executionOutputs)&&!executionOutputs.endsWith("/"),"Require the original literal execution output directory, including in portable readback");
        var profile=profile(edition);boolean parquet=edition.equals("2.0.2");
        Path scratch=newScratch.toAbsolutePath().normalize();require(scratch.getParent()!=null&&scratch.getParent().toRealPath().equals(scratch.getParent()),"Require a NEW canonical audit scratch directory");
        Path out=outputs.toAbsolutePath().normalize();require(out.toRealPath().equals(out)&&Files.isDirectory(out,LinkOption.NOFOLLOW_LINKS),"Require canonical original output directory");
        require(!scratch.startsWith(out)&&!out.startsWith(scratch),"Audit scratch must be separate from original outputs");Files.createDirectory(scratch);
        var reference=DiannReference.read(fasta,capacity.reference(),check);
        var sourcePins=new TreeMap<String,Pin>();String mainName=parquet?"report.parquet":"report.tsv";
        var names=new ArrayList<String>(List.of(mainName,"report.stats.tsv"));for(String kind:KINDS)names.add("report."+kind+"_matrix.tsv");if(parquet)names.addAll(List.of("report.protein_description.tsv","report.manifest.txt"));
        if(parquet&&matchBetweenRuns)names.addAll(List.of("empirical-library.parquet","empirical-library.parquet.skyline.speclib"));
        for(String name:names)sourcePins.put(name,pin(out.resolve(name),capacity.tables().bytes(),check));
        if(parquet&&matchBetweenRuns)for(String name:List.of("empirical-library.parquet","empirical-library.parquet.skyline.speclib"))require(sourcePins.get(name).bytes()>0,"Original MBR library output is empty");
        var state=new State(capacity.joinEntries());var counts=new LinkedHashMap<String,Long>();var confident=new LinkedHashMap<String,Long>();for(String name:byFile.keySet()){counts.put(name,0L);confident.put(name,0L);}
        var blank=new TreeMap<String,Long>();long total=0,unassigned=0;
        try(var table=DiannReportRows.open(out.resolve(mainName),text(profile.get("format")),parquet?scratch.resolve("parquet-spill"):null,capacity.tables(),check)) {
            var expected=list(profile.get("columns"));require(expected.size()==table.columns().size(),"Original report schema column count differs from selected edition");
            for(int i=0;i<expected.size();i++){var col=object(expected.get(i));require(table.columns().get(i).name().equals(col.get("name"))&&table.columns().get(i).storageType().equals(col.get("storage_type")),"Original report column/type/order differs from selected edition");}
            for(List<Object> cells;(cells=table.next())!=null;){
                check.run();var row=new LinkedHashMap<String,Object>();for(int i=0;i<cells.size();i++)row.put(table.columns().get(i).name(),cells.get(i));
                Run run=byName.get(string(row,"Run"));require(run!=null,"Original report contains an unrequested raw run");
                if(parquet){require(integerValue(row,"Run.Index")==runs.indexOf(run)&&integerValue(row,"Decoy")==0&&string(row,"Channel").isEmpty(),"Original run index/decoy/channel differs from the label-free target contract");}
                else require(string(row,"File.Name").equals(run.fileName()),"Original full file identity differs");
                for(var entry:row.entrySet()){
                    String name=entry.getKey();Object value=entry.getValue();require(value!=null,"Original SQL null is not a qualified computed report value: "+name);
                    if(STRINGS.contains(name)){require(value instanceof String,"Changed original text value type");if(value.equals(""))blank.merge(name,1L,Long::sum);continue;}
                    if(ARRAYS.contains(name)){array(string(row,name),name,capacity.tables().arrayMembers());continue;}
                    if(!parquet&&value.equals("")&&OPTIONAL.contains(name)){blank.merge(name,1L,Long::sum);continue;}
                    double n=finite(value);
                    if(name.endsWith("Q.Value")||Set.of("Q.Value","PEP","PG.PEP").contains(name))require(n>=0&&n<=1,"Original probability outside0..1: "+name);
                    if(Set.of("PG.Quantity","PG.Normalised","PG.MaxLFQ","Genes.Quantity","Genes.Normalised","Genes.MaxLFQ","Genes.MaxLFQ.Unique","Precursor.Quantity","Precursor.Normalised","Precursor.Translated").contains(name))require(n>=0,"Negative original quantity: "+name);
                }
                double q=number(row,"Q.Value");require(q<=reportQ,"Original report exceeds its explicit requested q-threshold");
                int charge=integerValue(row,"Precursor.Charge");require(charge>=1&&charge<=6,"Unqualified precursor charge");
                int proteotypic=integerValue(row,"Proteotypic");require(proteotypic==0||proteotypic==1,"Unresolved original proteotypicity");
                String modified=string(row,"Modified.Sequence"),stripped=string(row,"Stripped.Sequence"),precursor=string(row,"Precursor.Id");
                require(!stripped.isEmpty()&&stripped.chars().allMatch(c->c>='A'&&c<='Z')&&MODIFICATION.matcher(modified).replaceAll("").equals(stripped)&&precursor.equals(modified+charge),"Original peptide/modification/charge identity differs; no string is repaired");
                var proteins=members(string(row,"Protein.Ids"));String groupText=string(row,"Protein.Group");
                Set<String> group=groupText.isEmpty()?Set.of():members(groupText);var known=new HashSet<String>(proteins);known.addAll(group);
                if(group.isEmpty()){
                    require(parquet&&string(row,"Protein.Names").isEmpty()&&string(row,"Genes").isEmpty()
                            &&number(row,"PG.Q.Value")==1&&number(row,"Global.PG.Q.Value")==1
                            &&number(row,"PG.MaxLFQ")==0&&number(row,"Genes.MaxLFQ")==0&&number(row,"Genes.MaxLFQ.Unique")==0,
                            "Unassigned original group has inconsistent annotations, confidence or nonzero group quantities");
                    unassigned++;
                }
                require(known.stream().allMatch(reference.proteins()::containsKey),"Original report protein identifiers do not resolve to literal FASTA accessions");
                require(proteins.stream().anyMatch(p->reference.proteins().get(p).sequence().contains(stripped)),"Original peptide is absent from its declared source protein sequences");
                // Names/genes annotate the inferred group; Protein.Ids lists all precursor matches.
                var entryNames=new HashSet<String>();var genes=new HashSet<String>();for(String id:group){var protein=reference.proteins().get(id);entryNames.add(protein.entryName());if(protein.gene()!=null)genes.add(protein.gene());}
                require(group.isEmpty()?string(row,"Protein.Names").isEmpty():!string(row,"Protein.Names").isEmpty()&&entryNames.containsAll(members(string(row,"Protein.Names"))),"Original protein names are absent/unresolved in the inferred source group");
                String gene=string(row,"Genes");require(gene.isEmpty()?genes.isEmpty():genes.containsAll(members(gene)),"Original genes are absent/unresolved in the reported source proteins");
                require(number(row,"RT.Start")<=number(row,"RT")&&number(row,"RT")<=number(row,"RT.Stop"),"Original retention time lies outside original peak bounds");
                state.precursor(run.fileName(),precursor);
                if(!group.isEmpty())state.add("pr",run.fileName(),precursor,number(row,"Precursor.Normalised"));
                state.add("pg",run.fileName(),string(row,"Protein.Group"),number(row,"PG.MaxLFQ"));
                state.add("gg",run.fileName(),gene,optional(row,"Genes.MaxLFQ"));
                state.add("unique_genes",run.fileName(),gene,optional(row,"Genes.MaxLFQ.Unique"));
                for(String kind:KINDS){String id=kind.equals("pr")?precursor:kind.equals("pg")?groupText:gene;if(id.isEmpty()||kind.equals("pr")&&group.isEmpty())continue;
                    for(Object rawColumn:list(object(profile.get("matrix_metadata_columns")).get(kind))){String column=text(rawColumn);if(row.containsKey(column)&&row.get(column)!=null)state.metadata(kind,id,column,row.get(column).toString());}
                }
                counts.merge(run.fileName(),1L,Long::sum);if(q<=.01)confident.merge(run.fileName(),1L,Long::sum);total++;
            }
        }
        require(counts.values().stream().allMatch(n->n>0),"A requested run has no admitted report rows; no requested sample is silently omitted");
        var matrices=new LinkedHashMap<String,Object>();long inferredGroupIdsRows=0;
        for(String kind:KINDS){
            var metadata=list(object(profile.get("matrix_metadata_columns")).get(kind)).stream().map(Object::toString).toList();long matrixRows=0,empty=0;var seen=new HashSet<String>();var axis=new ArrayList<String>();
            try(var table=DiannReportRows.open(out.resolve("report."+kind+"_matrix.tsv"),"literal-utf8-tsv",null,capacity.tables(),check)){
                var columns=table.columns().stream().map(DiannReportRows.Column::name).toList();require(columns.size()==metadata.size()+runs.size()&&columns.subList(0,metadata.size()).equals(metadata),"Original matrix metadata columns differ");
                axis.addAll(columns.subList(metadata.size(),columns.size()));require(new HashSet<>(axis).equals(byFile.keySet()),"Original matrix omits/adds a requested run");
                int keyColumn=metadata.indexOf(kind.equals("pr")?"Precursor.Id":kind.equals("pg")?"Protein.Group":"Genes");require(keyColumn>=0,"Missing original matrix identifier");
                for(List<Object> cells;(cells=table.next())!=null;){
                    String id=(String)cells.get(keyColumn);require(!id.isEmpty()&&seen.add(id),"Blank/duplicate original matrix identifier");
                    for(int j=0;j<metadata.size();j++){var possible=state.metadata.get(new MetaKey(kind,id,metadata.get(j)));if(possible!=null&&!possible.contains((String)cells.get(j))){
                        // A PG-matrix annotation can name its inferred group; main Protein.Ids names all peptide matches.
                        require(kind.equals("pg")&&metadata.get(j).equals("Protein.Ids")&&cells.get(j).equals(id),"Original matrix metadata differs from complete original main report");inferredGroupIdsRows++;
                    }}
                    boolean quantified=false;
                    for(int j=0;j<axis.size();j++){
                        String cell=(String)cells.get(metadata.size()+j);if(cell.isEmpty()){empty++;continue;}
                        quantified=true;
                        double value=finite(cell);require(value>=0,"Negative original matrix quantity");
                        var candidates=state.values.get(kind).get(new Key(axis.get(j),id));require(candidates!=null&&candidates.stream().anyMatch(v->printedMatches(value,v,parquet)),"Original matrix quantity/identifier has no consistent original report value");
                    }require(quantified,"Original matrix row has no recorded quantity in any requested run");matrixRows++;
                }
            }
            var description=new LinkedHashMap<String,Object>();description.put("rows",matrixRows);description.put("empty_original_cells",empty);description.put("original_column_axis",axis);
            if(kind.equals("pg"))description.put("original_inferred_group_id_annotation_rows",inferredGroupIdsRows);matrices.put(kind,description);
        }
        var statistics=new ArrayList<Map<String,Object>>();var statFiles=new HashSet<String>();
        try(var table=DiannReportRows.open(out.resolve("report.stats.tsv"),"literal-utf8-tsv",null,capacity.tables(),check)){
            List<String> columns=table.columns().stream().map(DiannReportRows.Column::name).toList();require(columns.equals(list(profile.get("statistics_columns"))),"Original statistics schema differs");
            for(List<Object> cells;(cells=table.next())!=null;){
                var row=new LinkedHashMap<String,Object>();for(int j=0;j<columns.size();j++)row.put(columns.get(j),cells.get(j));String file=string(row,"File.Name");require(byFile.containsKey(file)&&statFiles.add(file),"Original statistics omit/duplicate/add run identity");
                for(String name:columns)if(!name.equals("File.Name"))finite(row.get(name));
                require(number(row,"Precursors.Identified")==confident.get(file),"Original one-percent precursor count differs from complete main report");statistics.add(row);
            }
        }
        require(statFiles.equals(byFile.keySet()),"Missing original run statistics");
        long descriptions=parquet?descriptions(out,reference,profile,capacity,check):0;
        if(parquet){var labels=new LinkedHashMap<String,Object>();object(profile.get("manifest_labels_to_filenames")).forEach((k,v)->labels.put(k.toString(),v));if(matchBetweenRuns)labels.put("Library in .speclib format for Skyline","empirical-library.parquet.skyline.speclib");var found=new HashSet<String>();for(String line:utf8(bytes(out.resolve("report.manifest.txt"),1048576)).lines().toList()){var row=object(SimpleJson.parse(line));require(row.size()==1,"Unqualified original manifest row");var e=row.entrySet().iterator().next();String label=text(e.getKey());require(labels.containsKey(label)&&found.add(label)&&e.getValue().equals(executionOutputs+"/"+text(labels.get(label))),"Original manifest output link differs from captured execution");}require(found.equals(labels.keySet()),"Original output manifest is incomplete");}
        for(var entry:sourcePins.entrySet())require(entry.getValue().equals(pin(out.resolve(entry.getKey()),capacity.tables().bytes(),check)),"Original output changed during full consistency audit");
        var result=new LinkedHashMap<String,Object>();result.put("status","COMPLETE_DIA_NN_ORIGINAL_OUTPUT_CONSISTENCY_COMPONENT");result.put("edition",edition);result.put("main_report_rows",total);result.put("main_rows_by_original_file",counts);result.put("reference_records",reference.proteins().size());result.put("reference_residues",reference.residues());result.put("original_blank_field_counts",blank);result.put("original_unassigned_group_rows",unassigned);result.put("matrices",matrices);result.put("statistics",statistics);result.put("protein_description_records",descriptions);result.put("original_table_pins",sourcePins);result.put("join_entries",state.entries);result.put("matrix_comparison",parquet?"original FLOAT32 versus six-significant-digit text; half last printed decimal unit plus0.000001unit":"original textual numeric values; exact parsed comparison");result.put("original_engine_executed_by_this_component",false);result.put("original_model_refitted",false);return result;
    }
    private static Map<?,?> profile(String edition)throws IOException {
        try(var input=DiannOutputAudit.class.getResourceAsStream("/org/jlinalg/cli/diann-tools/report-profiles.json")){
            require(input!=null,"Missing packaged DIA-NN output profiles");byte[] bytes=input.readNBytes(1048577);require(bytes.length<=1048576,"Output profile exceeds capacity");var document=object(SimpleJson.parse(utf8(bytes)));keys(document,"schema_version","editions");integer(document.get("schema_version"),1,1);var editions=object(document.get("editions"));require(editions.containsKey(edition),"Unqualified exact DIA-NN report edition");return object(editions.get(edition));
        }
    }
    private static long descriptions(Path out,DiannReference.Reference reference,Map<?,?> profile,Capacity c,Runnable check)throws IOException {
        long count=0;var seen=new HashSet<String>();
        try(var table=DiannReportRows.open(out.resolve("report.protein_description.tsv"),"literal-utf8-tsv",null,c.tables(),check)){
            require(table.columns().stream().map(DiannReportRows.Column::name).toList().equals(list(profile.get("protein_description_columns"))),"Original protein description schema differs");
            for(List<Object> row;(row=table.next())!=null;){String id=(String)row.get(0);var p=reference.proteins().get(id);require(p!=null&&seen.add(id)&&row.get(1).equals(p.entryName())&&row.get(2).equals(p.gene()==null?"":p.gene())&&row.get(4).equals(p.sequence()),"Original description identifier/name/gene/sequence differs from literal reference");count++;}
        }return count;
    }
    private static final class State {
        final long maximum;long entries;final Set<Key> precursors=new HashSet<>();final Map<String,Map<Key,Set<Double>>> values=new LinkedHashMap<>();final Map<MetaKey,Set<String>> metadata=new HashMap<>();
        State(long maximum){this.maximum=maximum;for(String kind:KINDS)values.put(kind,new HashMap<>());}
        void unit()throws IOException{require(++entries<=maximum,"Complete original-output join capacity exceeded");}
        void precursor(String file,String id)throws IOException{require(precursors.add(new Key(file,id)),"Duplicate original run/precursor report row");unit();}
        void add(String kind,String file,String id,Double value)throws IOException{if(id.isEmpty()||value==null)return;var map=values.get(kind);var key=new Key(file,id);var set=map.get(key);if(set==null){unit();set=new HashSet<>();map.put(key,set);}if(set.add(value))unit();}
        void metadata(String kind,String id,String column,String value)throws IOException{var key=new MetaKey(kind,id,column);var set=metadata.get(key);if(set==null){unit();set=new HashSet<>();metadata.put(key,set);}if(set.add(value))unit();}
    }
    private record MetaKey(String kind,String id,String column) { }
    private static Set<String> members(String value)throws IOException{require(!value.isEmpty(),"Empty original protein/name identifier set");var result=new HashSet<String>();for(String token:value.split(";",-1))require(!token.isEmpty()&&result.add(token),"Empty/duplicate original identifier token");return result;}
    private static boolean printedMatches(double text,double binary,boolean parquet){if(!parquet||binary==0)return text==binary;double half=0.500001*Math.pow(10,Math.floor(Math.log10(Math.abs(binary)))-5);return Math.abs(text-binary)<=half;}
    private static String string(Map<String,Object> row,String name)throws IOException{Object value=row.get(name);require(value instanceof String,"Missing/nontext original field: "+name);return(String)value;}
    private static Double optional(Map<String,Object> row,String name)throws IOException{Object value=row.get(name);return value instanceof String s&&s.isEmpty()?null:finite(value);}
    private static double number(Map<String,Object> row,String name)throws IOException{return finite(row.get(name));}
    private static int integerValue(Map<String,Object> row,String name)throws IOException{double value=number(row,name);require(value==Math.rint(value)&&value>=Integer.MIN_VALUE&&value<=Integer.MAX_VALUE,"Unresolved original integer: "+name);return(int)value;}
    private static double finite(Object value)throws IOException{double number;if(value instanceof Number n)number=n.doubleValue();else if(value instanceof String s&&DECIMAL.matcher(s).matches()){try{number=Double.parseDouble(s);}catch(NumberFormatException e){throw new IOException("Invalid original decimal",e);}}else throw new IOException("Missing, nondecimal or unqualified original numeric value");require(Double.isFinite(number),"Original nonfinite number; no repair or complete result admitted");return number;}
    private static void array(String text,String name,int maximum)throws IOException{require(text.endsWith(";"),"Unqualified original fragment array syntax");String[] values=text.substring(0,text.length()-1).split(";",-1);require(values.length>0&&values.length<=maximum,"Original fragment array capacity exceeded");for(String value:values){double n=finite(value);require(name.equals("Fragment.Correlations")?n>=-1&&n<=1:n>=0,"Invalid original fragment quantity/correlation");}}
    private static boolean literal(String value){return value!=null&&!value.isBlank()&&value.length()<=32768&&value.chars().noneMatch(c->c<32||c==127);}
    private static void require(boolean condition,String message)throws IOException{if(!condition)throw new IOException(message);}
}
