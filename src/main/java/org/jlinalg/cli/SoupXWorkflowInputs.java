/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.singlecell.SoupXAmbient;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Complete literal axes, exact integer counts and explicit biological masks; no implicit joins or sums. */
final class SoupXWorkflowInputs {
    private SoupXWorkflowInputs() { }
    record Population(SoupXAmbient.Counts raw,SoupXAmbient.Counts filtered,List<String> clusters,
                      Map<String,List<String>> geneSets,SoupXAmbient.Mask mask,List<Double> fixedRho,
                      SoupXAmbient.Profile profile,long storedEntries){ }
    static Population validate(Path inputs,SoupXWorkflowRequest.Request r,SoupXAmbient.Budget budget)throws IOException{
        List<String> genes=axis(table(inputs.resolve("genes.tsv"),r.maximumGenes(),r),"gene_id",r.maximumGenes(),2);
        List<String> droplets=axis(table(inputs.resolve("droplets.tsv"),r.maximumDroplets(),r),"droplet_id",r.maximumDroplets(),1);
        var cellsTable=table(inputs.resolve("cells.tsv"),r.maximumCells(),r);boolean supplied=r.clusters().equals("supplied");require(Arrays.equals(cellsTable.get(0),supplied?new String[]{"cell_id","cluster"}:new String[]{"cell_id"}),"Require exact declared cell metadata header");require(cellsTable.size()>=3,"Require at least two complete cells");var cells=new ArrayList<String>();var clusters=new ArrayList<String>();var seen=new HashSet<String>();
        for(int i=1;i<cellsTable.size();i++){String[] row=cellsTable.get(i);require(seen.add(row[0]),"Duplicate literal cell ID");cells.add(row[0]);if(supplied)clusters.add(row[1]);}
        var raw=matrix(inputs.resolve("raw-counts"),genes,droplets,r,r.inputEntries(),budget);var filtered=matrix(inputs.resolve("filtered-counts"),genes,cells,r,r.inputEntries()-raw.entries().size(),budget);
        for(int c=0;c<cells.size();c++)require(filtered.columnTotal(c)>0,"Zero-total filtered cell refused; retain it and review the input rather than silently dropping it");
        if(r.relation().equals("raw-subset"))SoupXAmbient.verifyRawSubset(raw,filtered,budget);
        double lower=finite(r.soupRange().get(0),0,9007199254740991.0),upper=finite(r.soupRange().get(1),0,9007199254740991.0);var profile=SoupXAmbient.profile(raw,lower,upper,budget);require(profile.total()>0,"No positive-total droplets in the explicit strict soup_range; original NaN profile cannot admit analysis. Preserve the input/range; do not recode NaN or silently choose another range");
        var sets=new LinkedHashMap<String,List<String>>();
        if(r.geneSets()!=null){var rows=table(inputs.resolve("gene-sets.tsv"),r.intermediateEntries(),r);require(Arrays.equals(rows.get(0),new String[]{"gene_set","gene_id"}),"Require gene_set gene_id header");Set<String> known=new HashSet<>(genes);var members=new HashSet<List<String>>();for(int i=1;i<rows.size();i++){String[] row=rows.get(i);require(known.contains(row[1])&&members.add(List.of(row[0],row[1])),"Absent/repeated gene-set member; no repair, intersection or deduplication");sets.computeIfAbsent(row[0],k->new ArrayList<>()).add(row[1]);}require(!sets.isEmpty(),"Require declared non-expression gene-set members");}
        long pairs=(long)sets.size()*cells.size();require(pairs<=r.intermediateEntries(),"Complete cell/gene-set family exceeds maximum_intermediate_entries");SoupXAmbient.Mask mask=null;
        if(r.mask()!=null){var rows=table(inputs.resolve("nonexpression-mask.tsv"),r.intermediateEntries(),r);require(Arrays.equals(rows.get(0),new String[]{"cell_id","gene_set","non_expressing"}),"Require cell_id gene_set non_expressing header");require(rows.size()-1==pairs,"Manual mask must provide every cell/gene-set pair");var names=new ArrayList<>(sets.keySet());var accepted=new ArrayList<List<Boolean>>();int at=1;
            for(String cell:cells){var row=new ArrayList<Boolean>();for(String set:names){String[] f=rows.get(at++);require(f[0].equals(cell)&&f[1].equals(set),"Mask rows must follow complete cell-major/gene-set order; no implicit reordering");require(Set.of("true","false").contains(f[2]),"Require literal true or false mask values");row.add(f[2].equals("true"));}accepted.add(row);}mask=new SoupXAmbient.Mask(cells,names,accepted,List.of());
        }
        var rho=new ArrayList<Double>();if(r.perCellRho()!=null){var rows=table(inputs.resolve("rho-by-cell.tsv"),r.maximumCells(),r);require(Arrays.equals(rows.get(0),new String[]{"cell_id","rho"})&&rows.size()==cells.size()+1,"Require complete cell_id rho table");for(int c=0;c<cells.size();c++){String[] row=rows.get(c+1);require(row[0].equals(cells.get(c)),"Rho rows must match literal cell order");double value=numeric(row[1]);require(value>=0&&value<=1,"Require finite per-cell rho in[0,1]");rho.add(value);}}
        for(var a:r.analyses()){
            var e=object(a.get("estimation"));if(text(e.get("kind")).startsWith("automatic-")){int groups=new HashSet<>(clusters).size();require(groups>=2,"Automatic estimation requires at least two supplied observed clusters");require((long)genes.size()*groups<=r.intermediateEntries(),"Original automatic gene/cluster family exceeds maximum_intermediate_entries");}
            if(a.get("adjustment")!=null&&text(object(a.get("adjustment")).get("clustering_scope")).equals("supplied-clusters")){require(!clusters.get(0).equals("FALSE"),"First literal cluster FALSE bypasses original cluster correction. Review the source branch and explicitly select cell-only if intended; do not rename labels to force success");require((long)genes.size()*new HashSet<>(clusters).size()<=r.intermediateEntries(),"Original clustered correction family exceeds maximum_intermediate_entries");}
        }
        var frozen=new LinkedHashMap<String,List<String>>();sets.forEach((k,v)->frozen.put(k,List.copyOf(v)));return new Population(raw,filtered,List.copyOf(clusters),Collections.unmodifiableMap(frozen),mask,List.copyOf(rho),profile,raw.entries().size()+filtered.entries().size());
    }
    private static SoupXAmbient.Counts matrix(Path path,List<String> genes,List<String> cells,SoupXWorkflowRequest.Request r,long maximum,SoupXAmbient.Budget budget)throws IOException{
        require(maximum>=0,"Combined raw/filtered entry capacity exceeded");var entries=new ArrayList<SoupXAmbient.Entry>();String content=content(path,r);
        if(r.format().equals("dense-tsv")){
            require((long)genes.size()*cells.size()<=maximum,"Dense raw/filtered entries exceed shared capacity");var rows=literal(content,genes.size());require(rows.size()==genes.size()+1,"Dense gene population differs from metadata");String[] header=rows.get(0);require(header.length==cells.size()+1&&header[0].equals("gene_id")&&Arrays.asList(header).subList(1,header.length).equals(cells),"Dense column IDs must match metadata exactly in order");
            for(int g=0;g<genes.size();g++){String[] row=rows.get(g+1);require(row[0].equals(genes.get(g)),"Dense gene IDs must match metadata exactly in order");for(int c=0;c<cells.size();c++)entries.add(new SoupXAmbient.Entry(g,c,count(row[c+1])));}
        }else{
            var lines=content.lines().iterator();require(lines.hasNext(),"Missing Matrix Market banner");require(Set.of("%%MatrixMarket matrix coordinate integer general","%%MatrixMarket matrix coordinate real general").contains(lines.next()),"Require general integer/real coordinate Matrix Market; no symmetric expansion or aggregation");String[] shape=next(lines).trim().split("\\s+");require(shape.length==3,"Invalid Matrix Market dimensions");require(index(shape[0],1,r.maximumGenes())==genes.size()&&index(shape[1],1,Math.max(r.maximumDroplets(),r.maximumCells()))==cells.size(),"Matrix Market axes differ from complete metadata");long n=index(shape[2],0,maximum);
            while(lines.hasNext()){String line=lines.next();if(line.startsWith("%"))continue;require(!line.isBlank()&&entries.size()<n,"Excess/blank Matrix Market record");String[] f=line.trim().split("\\s+");require(f.length==3,"Require row column value coordinate");entries.add(new SoupXAmbient.Entry((int)index(f[0],1,genes.size())-1,(int)index(f[1],1,cells.size())-1,count(f[2])));}require(entries.size()==n,"Matrix Market entry count differs");
        }
        return new SoupXAmbient.Counts(genes,cells,entries,budget);
    }
    private static List<String> axis(List<String[]> rows,String header,int maximum,int minimum)throws IOException{require(Arrays.equals(rows.get(0),new String[]{header}),"Require "+header+" header");require(rows.size()-1>=minimum&&rows.size()-1<=maximum,"Axis population outside declared capacity");var values=new ArrayList<String>();var seen=new HashSet<String>();for(int i=1;i<rows.size();i++){String id=rows.get(i)[0];require(seen.add(id),"Duplicate literal "+header);values.add(id);}return List.copyOf(values);}
    static List<String[]> table(Path path,int maximum,SoupXWorkflowRequest.Request r)throws IOException{return literal(content(path,r),maximum);}
    private static List<String[]> literal(String text,int maximum)throws IOException{var rows=new ArrayList<String[]>();int width=-1;for(String line:text.lines().toList()){require(!line.isEmpty(),"Blank literal TSV record");String[] row=line.split("\t",-1);if(width<0)width=row.length;require(row.length==width,"Ragged literal TSV");for(String value:row)label(value);rows.add(row);require(rows.size()-1<=maximum,"TSV row capacity exceeded");}require(!rows.isEmpty(),"Empty literal TSV");return rows;}
    private static String content(Path path,SoupXWorkflowRequest.Request r)throws IOException{String text=NextflowFiles.utf8(NextflowFiles.bytes(path,(int)r.inputBytes()));require(!text.startsWith("\ufeff")&&text.indexOf('\0')<0,"Refuse BOM/NUL in literal input");text=text.replace("\r\n","\n");require(text.indexOf('\r')<0,"Refuse bare-CR input");return text;}
    private static String next(Iterator<String> lines)throws IOException{while(lines.hasNext()){String line=lines.next();if(!line.startsWith("%"))return line;}throw new IOException("Missing Matrix Market dimensions");}
    private static long index(String value,long low,long high)throws IOException{require(value.matches("[0-9]+"),"Require nonnegative literal integer index");try{long n=Long.parseLong(value);require(n>=low&&n<=high,"Index/count outside declared capacity");return n;}catch(NumberFormatException e){throw new IOException("Integer index overflow",e);}}
    private static long count(String value)throws IOException{require(value.length()<=128&&value.matches("[+]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?"),"Require complete nonnegative exact integer counts; no NA/NaN/Inf omission or fractional rounding");try{long n=new BigDecimal(value).longValueExact();require(n>=0&&n<=9007199254740991L,"Count outside exact binary64 integer domain");return n;}catch(NumberFormatException|ArithmeticException e){throw new IOException("Fractional/nonrepresentable count refused",e);}}
    static double numeric(String value)throws IOException{require(value.length()<=128&&value.matches("[+]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?"),"Require finite nonnegative numeric token");double d;try{d=Double.parseDouble(value);}catch(NumberFormatException e){throw new IOException("Invalid numeric token",e);}require(Double.isFinite(d),"Nonfinite numeric token refused");return d;}
}
