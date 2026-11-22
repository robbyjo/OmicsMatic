/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jlinalg.enrichment.FumaDegEnrichment;
import org.jlinalg.genetics.MagmaTables;
import static org.jlinalg.cli.FollowupSupport.*;

/** Exact supplied resources and auditable source-order step-3 matrix joins. */
final class FumaCelltypeInputs {
    private FumaCelltypeInputs() { }
    record Limits(int bytes,int genes,int datasets,int cells,long values) {
        Limits { if(bytes<1||genes<1||datasets<1||cells<1||values<1)throw new IllegalArgumentException("Invalid cell-type input capacities"); }
    }
    record GeneBundle(Path raw,List<String> genes,Map<?,?> metadata) {
        GeneBundle { genes=List.copyOf(genes); }
    }
    record Row(String gene,List<String> values) { Row { values=List.copyOf(values); } }
    record Dataset(String id,String resourceId,Path source,List<String> columns,List<Row> rows) {
        Dataset { columns=List.copyOf(columns);rows=List.copyOf(rows); }
        List<String> cells(){return columns.stream().filter(c->!c.equals("Average")).toList();}
    }
    record Joined(List<String> columns,List<Row> rows,List<String[]> disposition) {
        Joined { columns=List.copyOf(columns);rows=List.copyOf(rows);disposition=List.copyOf(disposition); }
    }
    static byte[] raw(Path input,Map<Path,String> sources,int maximum)throws IOException {
        input=input.toAbsolutePath().normalize();byte[] bytes=AdmixtureInputs.raw(input,maximum);String digest=AdmixtureInputs.digest(bytes),old=sources.putIfAbsent(input,digest);if(old!=null&&!old.equals(digest))throw new IOException("Cell-type input changed: "+input);return bytes;
    }
    private static List<String> lines(Path p,Map<Path,String> sources,int bytes)throws IOException {
        String text=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw(p,sources,bytes))).toString();if(text.indexOf('\0')>=0)throw new IOException("NUL in supplied cell-type resource");return text.lines().toList();
    }
    static GeneBundle bundle(Path directory,String build,Map<Path,String> sources,Limits limits)throws Exception {
        directory=directory.toAbsolutePath().normalize();var metadata=AdmixtureInputs.decode(raw(directory.resolve("magma-bundle.json"),sources,Math.min(limits.bytes,8388608)));
        if(!"gene".equals(metadata.get("kind"))||!build.equals(metadata.get("build"))||!(metadata.get("files") instanceof Map<?,?> files)||!files.containsKey("results.tsv")||!files.containsKey("genes.raw"))throw new IOException("Require a sealed MAGMA gene bundle with the declared build and raw/results members");
        if(files.size()>32)throw new IOException("Unexpected MAGMA bundle member count");
        for(var e:files.entrySet()){String name=e.getKey().toString(),expected=e.getValue().toString();Path p=RegenieRuntime.contained(directory,name);byte[] body=raw(p,sources,limits.bytes);if(!expected.matches("[0-9a-f]{64}")||!AdmixtureInputs.digest(body).equals(expected))throw new IOException("Sealed MAGMA bundle member changed: "+name);}
        var results=MagmaTables.table(directory.resolve("results.tsv"));if(!results.columns().contains("GENE")||results.rows().size()>limits.genes)throw new IOException("Missing or oversized MAGMA gene population");
        var genes=new ArrayList<String>();var seen=new HashSet<String>();for(var row:results.rows()){String gene=row.get("GENE");identifier(gene);if(!seen.add(gene))throw new IOException("Repeated gene in sealed MAGMA result population");genes.add(gene);}
        MagmaTables.verifyRaw(directory.resolve("genes.raw"),results);return new GeneBundle(directory.resolve("genes.raw"),genes,metadata);
    }
    static List<Dataset> datasets(Path manifest,Map<Path,String> sources,Limits limits,boolean permitMissingValues,FumaDegEnrichment.Check check)throws Exception {
        manifest=manifest.toAbsolutePath().normalize();var input=lines(manifest,sources,limits.bytes);if(input.isEmpty()||!input.get(0).equals("dataset\tresource_id\tpath")||input.size()<2||input.size()-1>limits.datasets)throw new IOException("Dataset manifest requires dataset/resource_id/path header and a bounded nonempty population");
        var result=new ArrayList<Dataset>();var ids=new HashSet<String>();long values=0;int cellCount=0;
        for(int i=1;i<input.size();i++) {
            check.run();String[] record=input.get(i).split("\t",-1);if(record.length!=3)throw new IOException("Malformed dataset manifest row "+(i+1));identifier(record[0]);if(record[0].contains("_fumaCelltype")||!ids.add(record[0]))throw new IOException("Duplicate dataset or source metric suffix in dataset ID");identity(record[1]);if(record[2].isBlank())throw new IOException("Empty dataset resource path");Path path=manifest.getParent().resolve(record[2]).normalize();var data=lines(path,sources,limits.bytes);if(data.isEmpty()||data.size()-1>limits.genes)throw new IOException("Empty or oversized complete expression property table");
            String[] header=data.get(0).split("\t",-1);if(header.length<3||!header[0].equals("GENE"))throw new IOException("Cell-type properties require GENE, at least one cell and Average");var columns=Arrays.asList(Arrays.copyOfRange(header,1,header.length));var unique=new HashSet<String>();for(String column:columns){identifier(column);if(!unique.add(column))throw new IOException("Repeated cell-type property column");}if(!unique.contains("Average"))throw new IOException("Cell-type properties require the exact precomputed Average column");cellCount=Math.addExact(cellCount,columns.size()-1);if(cellCount>limits.cells)throw new IOException("Complete supplied cell population exceeds --max-cells");values=Math.addExact(values,(long)(data.size()-1)*columns.size());if(values>limits.values)throw new IOException("Complete supplied property values exceed --max-values");
            var rows=new ArrayList<Row>();var genes=new HashSet<String>();for(int j=1;j<data.size();j++){check.run();String[] fields=data.get(j).split("\t",-1);if(fields.length!=header.length)throw new IOException("Property width differs at row "+(j+1));identifier(fields[0]);if(!genes.add(fields[0]))throw new IOException("Repeated property gene ID in "+record[0]);for(int k=1;k<fields.length;k++)numeric(fields[k],permitMissingValues);rows.add(new Row(fields[0],Arrays.asList(Arrays.copyOfRange(fields,1,fields.length))));}
            if(rows.isEmpty())throw new IOException("Empty property population: "+record[0]);result.add(new Dataset(record[0],record[1],path.toAbsolutePath().normalize(),columns,rows));
        }return List.copyOf(result);
    }
    static long coverage(List<Dataset> datasets,List<String> universe,boolean sourceCoverage,Path out,List<String> warnings,long maximumRows,FumaDegEnrichment.Check check)throws Exception {
        Set<String> genes=new HashSet<>(universe);var ledger=new ArrayList<String[]>();long missing=0,extra=0,na=0;
        for(var dataset:datasets){var observed=new HashSet<String>();for(var row:dataset.rows){check.run();if(ledger.size()>=maximumRows)throw new IOException("Complete resource coverage ledger exceeds --max-ledger-rows");observed.add(row.gene);boolean used=genes.contains(row.gene);if(!used)extra++;long absent=row.values.stream().filter(v->v.equals("NA")).count();na+=absent;ledger.add(new String[]{dataset.id,row.gene,used?"matched-gene-analysis":"outside-gene-analysis-source-ignore",Long.toString(absent)});}for(String gene:universe)if(!observed.contains(gene)){check.run();if(ledger.size()>=maximumRows)throw new IOException("Complete resource coverage ledger exceeds --max-ledger-rows");missing++;ledger.add(new String[]{dataset.id,gene,"missing-resource-gene-source-default","NA"});}}
        table(out,List.of("dataset","gene","disposition","missing_property_values"),ledger);
        if(!sourceCoverage&&(missing>0||extra>0))throw new IOException("Resource/gene-analysis populations differ: "+missing+" missing and "+extra+" extra genes; correct resources or explicitly choose --gene-coverage source-default");
        if(missing>0||extra>0||na>0)warnings.add("PROPERTY COVERAGE REQUIRES REVIEW: "+missing+" missing gene/resource memberships, "+extra+" extra memberships and "+na+" NA property cells. Source MAGMA missingness/defaults are retained, without Java imputation. Required action: inspect resource-coverage.tsv, native logs and effective NGENES; select matching resources or justify the explicit source-default policies.");
        return ledger.size();
    }
    static Joined join(Dataset left,List<String> leftCells,Dataset right,List<String> rightCells,FumaDegEnrichment.Check check)throws IOException {
        if(left.id.equals(right.id)||leftCells.isEmpty()||rightCells.isEmpty())throw new IOException("Step3 join requires two selected dataset populations");var columns=new ArrayList<String>();for(String cell:leftCells)columns.add(left.id+":"+cell);columns.add("Average1");for(String cell:rightCells)columns.add(right.id+":"+cell);columns.add("Average2");
        var l=new ArrayList<>(leftCells);l.add("Average");var r=new ArrayList<>(rightCells);r.add("Average");int[] li=indices(left,l),ri=indices(right,r);var rightRows=new LinkedHashMap<String,Row>();for(var row:right.rows)rightRows.put(row.gene,row);var rows=new ArrayList<Row>();var ledger=new ArrayList<String[]>();var visited=new HashSet<String>();
        for(var row:left.rows){check.run();Row partner=rightRows.get(row.gene);visited.add(row.gene);if(partner==null){ledger.add(new String[]{left.id,right.id,row.gene,"excluded-missing-right-gene"});continue;}if(partner.values.get(ri[ri.length-1]).equals("NA")){ledger.add(new String[]{left.id,right.id,row.gene,"excluded-missing-right-Average"});continue;}var values=new ArrayList<String>();for(int index:li)values.add(row.values.get(index));for(int index:ri)values.add(partner.values.get(index));rows.add(new Row(row.gene,values));ledger.add(new String[]{left.id,right.id,row.gene,"retained-source-left-order"});}
        for(var row:right.rows)if(!visited.contains(row.gene))ledger.add(new String[]{left.id,right.id,row.gene,"excluded-right-only-gene"});if(rows.isEmpty())throw new IOException("No source step3 rows remain after right-Average intersection");return new Joined(columns,rows,ledger);
    }
    private static int[] indices(Dataset dataset,List<String> requested)throws IOException {var seen=new HashSet<String>();int[] result=new int[requested.size()];for(int i=0;i<result.length;i++){String name=requested.get(i);result[i]=dataset.columns.indexOf(name);if(result[i]<0||!seen.add(name))throw new IOException("Unknown/repeated selected source property: "+name);}return result;}
    static void write(Path target,List<String> columns,List<Row> rows)throws IOException {
        try(var out=Files.newBufferedWriter(target)){var header=new ArrayList<String>(List.of("GENE"));header.addAll(columns);row(out,header.toArray(String[]::new));for(var entry:rows){var values=new ArrayList<String>(List.of(entry.gene));values.addAll(entry.values);row(out,values.toArray(String[]::new));}}
    }
    static void writeJoined(Path target,Joined joined)throws IOException {
        var rows=new ArrayList<Row>();for(var row:joined.rows){var values=new ArrayList<String>();for(String value:row.values)values.add(value.equals("NA")?value:new BigDecimal(Double.parseDouble(value)).round(new MathContext(15,RoundingMode.HALF_EVEN)).stripTrailingZeros().toString());rows.add(new Row(row.gene,values));}write(target,joined.columns,rows);
    }
    static void verify(Map<Path,String> sources)throws IOException {for(var e:sources.entrySet())if(!e.getValue().equals(hash(e.getKey())))throw new IOException("Protected cell-type input changed: "+e.getKey());}
    static void identity(String value)throws IOException {if(value==null||value.isBlank()||value.length()>4096||value.codePoints().anyMatch(Character::isISOControl))throw new IOException("Require a bounded explicit resource identity without controls");}
    static void identifier(String value)throws IOException {if(value==null||value.length()>256||!value.matches("[A-Za-z0-9_.-]+"))throw new IOException("Require exact ASCII gene/cell/dataset identifiers using letters, digits, dot, underscore or hyphen");}
    private static void numeric(String value,boolean missing)throws IOException {if(value.equals("NA")){if(missing)return;throw new IOException("NA property requires explicit --missing-values source-default");}if(!value.matches("[+-]?(?:[0-9]+(?:[.][0-9]*)?|[.][0-9]+)(?:[eE][+-]?[0-9]+)?")||!Double.isFinite(Double.parseDouble(value)))throw new IOException("Require finite decimal property tokens or explicit NA");}
}
