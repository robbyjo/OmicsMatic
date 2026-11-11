/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Literal complete mutation/covariate populations; source omissions must receive an explicit ledger disposition. */
final class DndscvWorkflowInputs {
    private DndscvWorkflowInputs() { }
    record Key(String sample,String chromosome,int position,String reference,String alternate){ }
    record Event(int row,Key key){ }
    record NumericTable(List<String> genes,List<String> columns,List<List<Double>> values){ }
    record Population(List<Event> events,Map<Integer,Integer> duplicateOf,List<String> requestedGenes,
                      NumericTable covariates,List<String> known,NumericTable duplex,Map<String,List<String>> geneSets){ }
    static final class Budget {
        final long maximum;long used;
        Budget(long maximum){this.maximum=maximum;}
        void add(long value)throws IOException{require(value>=0&&value<=maximum-used,"Complete native audit exceeds maximum_audit_operations; retain inputs and retry permitted capacity without dropping events/genes");used+=value;}
    }
    static Population validate(Path root,DndscvWorkflowRequest.Request r,Budget budget)throws IOException{
        var table=table(root.resolve("mutations.tsv"),r.events(),r);header(table,"sample_id","chromosome","position","reference","alternate");require(table.size()>1,"Require independent mutation events");var events=new ArrayList<Event>();var first=new HashMap<Key,Integer>();var duplicates=new LinkedHashMap<Integer,Integer>();
        for(int i=1;i<table.size();i++){budget.add(1);String[] row=table.get(i);int pos;try{pos=new BigDecimal(row[2]).intValueExact();}catch(ArithmeticException|NumberFormatException e){throw new IOException("Position must be an exact1-based32-bit integer; no rounding/coordinate repair",e);}require(pos>=1,"Position must be1-based");require(row[3].matches("[ACGT]+|-")&&row[4].matches("[ACGT]+|-"),"Supply literal uppercase canonical alleles or a single indel gap; no allele repair/decomposition");require(row[3].length()<=100000&&row[4].length()<=100000,"Allele length capacity exceeded");require((long)pos+row[3].length()-1<=Integer.MAX_VALUE,"Reference event span exceeds original32-bit coordinate capacity");Key key=new Key(row[0],row[1],pos,row[3],row[4]);Integer old=first.putIfAbsent(key,i);if(old!=null){require(r.duplicates().equals("source-collapse-with-ledger"),"Repeated exact event at row"+i+"; explicitly choose source-collapse-with-ledger or correct its biological provenance without silent omission");duplicates.put(i,old);}events.add(new Event(i,key));}
        var genes=new ArrayList<String>();if(r.genes().get("kind").equals("supplied")){genes.addAll(axis(table(root.resolve("genes.tsv"),r.maximumGenes(),r),"gene_id"));require(!genes.isEmpty(),"Require requested genes");if(genes.contains("CDKN2A"))require(r.reference().get("kind").equals("builtin")&&r.genes().get("cdkn2a_policy").equals("source-expand-isoforms"),"Literal CDKN2A expands to two built-in isoforms only under the explicit source alias policy");}
        NumericTable cov=null;if(r.covariates().get("kind").equals("supplied"))cov=numericTable(root.resolve("covariates.tsv"),r,false,budget);var known=new ArrayList<String>();if(r.known().get("kind").equals("supplied")){known.addAll(axis(table(root.resolve("known-cancer-genes.tsv"),r.maximumGenes(),r),"gene_id"));require(!known.isEmpty()&&!known.get(0).equals("cgc81"),"Use the explicit none/builtin prior mode; a first supplied cgc81 would invoke the original database branch");}
        NumericTable duplex=null;if(r.duplex().get("kind").equals("per-gene")){duplex=numericTable(root.resolve("duplex-input"),r,true,budget);require(duplex.columns().equals(List.of("coverage")),"Per-gene coverage requires exact gene_id coverage headers");}
        var sets=new LinkedHashMap<String,List<String>>();if(r.geneSets()!=null){var rows=table(root.resolve("gene-sets.tsv"),Math.toIntExact(Math.min(r.matrixEntries(),Integer.MAX_VALUE)),r);header(rows,"gene_set","gene_id");var seen=new HashSet<List<String>>();for(int i=1;i<rows.size();i++){budget.add(1);var row=rows.get(i);require(seen.add(List.of(row[0],row[1])),"Repeated member in a supplied gene set");sets.computeIfAbsent(row[0],k->new ArrayList<>()).add(row[1]);}require(!sets.isEmpty(),"Require supplied gene-set membership");for(var e:sets.entrySet())require(e.getValue().size()>=2,"Original genesetdnds requires at least two members: "+e.getKey());}
        return new Population(List.copyOf(events),Map.copyOf(duplicates),List.copyOf(genes),cov,List.copyOf(known),duplex,Collections.unmodifiableMap(sets));
    }
    static List<String[]> table(Path path,int maximum,DndscvWorkflowRequest.Request r)throws IOException{
        String raw=NextflowFiles.utf8(NextflowFiles.bytes(path,(int)Math.min(r.inputBytes(),Integer.MAX_VALUE)));require(!raw.startsWith("\ufeff")&&raw.indexOf('\0')<0,"Literal UTF-8 table cannot contain BOM/NUL");raw=raw.replace("\r\n","\n");require(raw.indexOf('\r')<0,"Bare CR in a literal table");String[] lines=raw.split("\n",-1);int count=lines.length-(lines.length>0&&lines[lines.length-1].isEmpty()?1:0);require(count>=1&&(long)count<=maximum+1L,"Complete table exceeds declared row capacity");var rows=new ArrayList<String[]>();int width=-1;for(int i=0;i<count;i++){String[] row=lines[i].split("\t",-1);if(i==0)width=row.length;require(row.length==width,"Table width differs at row"+i);for(String field:row){require(!field.isEmpty()&&field.length()<=32768,"Empty/oversized literal field at row"+i);}rows.add(row);}return rows;
    }
    private static List<String> axis(List<String[]> rows,String column)throws IOException{header(rows,column);var result=new ArrayList<String>();var seen=new HashSet<String>();for(int i=1;i<rows.size();i++){require(seen.add(rows.get(i)[0]),"Duplicate literal gene identifier");result.add(rows.get(i)[0]);}return result;}
    private static NumericTable numericTable(Path path,DndscvWorkflowRequest.Request r,boolean positive,Budget budget)throws IOException{
        var rows=table(path,r.maximumGenes(),r);require(rows.get(0).length>=2&&rows.get(0)[0].equals("gene_id"),"Require complete gene_id and numeric columns");var columns=Arrays.asList(rows.get(0)).subList(1,rows.get(0).length);require(new HashSet<>(columns).size()==columns.size(),"Repeated numeric column name");for(String column:columns)require(column.matches("[A-Za-z][A-Za-z0-9_]{0,63}"),"Require literal numeric feature names");require((long)(rows.size()-1)*columns.size()<=r.matrixEntries(),"Complete numeric family exceeds maximum_matrix_entries");var genes=new ArrayList<String>();var values=new ArrayList<List<Double>>();var seen=new HashSet<String>();for(int i=1;i<rows.size();i++){String[] row=rows.get(i);require(seen.add(row[0]),"Repeated numeric-table gene");genes.add(row[0]);var numbers=new ArrayList<Double>();for(int j=1;j<row.length;j++){budget.add(1);double value;try{value=Double.parseDouble(row[j]);}catch(NumberFormatException e){throw new IOException("Require finite numeric input; no missing-value omission",e);}require(Double.isFinite(value)&&(!positive||value>0),"Nonfinite/nonpositive required numeric input; no replacement");numbers.add(value);}values.add(List.copyOf(numbers));}require(!genes.isEmpty(),"Require complete numeric-table genes");return new NumericTable(List.copyOf(genes),List.copyOf(columns),List.copyOf(values));
    }
    private static void header(List<String[]> rows,String... columns)throws IOException{require(Arrays.equals(rows.get(0),columns),"Require exact header: "+String.join(" ",columns));}
}
