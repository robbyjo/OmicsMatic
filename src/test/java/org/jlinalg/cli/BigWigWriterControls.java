/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.jlinalg.genetics.BedGraphSignals.Segment;
import org.jlinalg.genetics.BigWigWriter;

/** Original-reader control export; not a public analysis command or biological validation. */
public final class BigWigWriterControls {
    private BigWigWriterControls() { }
    /** @param args NEW output directory and exact tested artifact path */
    public static void main(String[] args)throws Exception {
        Path root=Path.of(args[0]).toAbsolutePath();if(Files.exists(root)||!BigWigWriter.class.getProtectionDomain().getCodeSource().getLocation().toURI().equals(Path.of(args[1]).toUri()))throw new AssertionError("Require NEW control directory and actual tested JAR owner");Files.createDirectory(root);var cases=new ArrayList<Map<String,Object>>();
        var small=List.of(new Segment("chr1",0,3,.1),new Segment("chr1",4,7,-2),new Segment("chr1",8,9,-0.0),new Segment("chr2",10,20,1.0/3));var reference=Map.of("chr1",100L,"chr2",100L,"unused",50L);
        for(boolean compressed:List.of(true,false))for(int chunk:List.of(1,2,1024))add(root,cases,"mixed-"+compressed+"-"+chunk,reference,small,new BigWigWriter.Options(compressed,chunk,2,50000000,false,false));
        var floats=List.of(0.0,-0.0,Double.MIN_VALUE,-Double.MIN_VALUE,1e-50,-1e-50,(double)Float.MIN_VALUE,-(double)Float.MIN_VALUE,(double)Float.MAX_VALUE,-(double)Float.MAX_VALUE,1e20,-1e20,16777217.0,16777216.0,1.0000000596046448,1.0000000596046446);var floatRows=new ArrayList<Segment>();for(int i=0;i<floats.size();i++)floatRows.add(new Segment("chr",3L*i,3L*i+1,floats.get(i)));add(root,cases,"float32-boundaries",Map.of("chr",1000L),floatRows,BigWigWriter.Options.defaults());
        add(root,cases,"nonlex-chromosome-blocks",reference,List.of(new Segment("chr2",0,3,2),new Segment("chr1",0,1,1)),BigWigWriter.Options.defaults());
        add(root,cases,"all-reference-chromosomes",reference,small,new BigWigWriter.Options(true,2,2,50000000,false,true));
        add(root,cases,"empty-declared-track",reference,List.of(),new BigWigWriter.Options(true,2,2,50000000,false,true));
        add(root,cases,"unicode-keys",Map.of("chr\uE000",100L,"chr\uD800\uDC00",100L),List.of(new Segment("chr\uE000",0,1,1),new Segment("chr\uD800\uDC00",0,2,2)),BigWigWriter.Options.defaults());
        add(root,cases,"unsigned32-coordinates",Map.of("huge",4294967295L),List.of(new Segment("huge",2147483648L,2147483651L,2),new Segment("huge",4294967294L,4294967295L,3)),BigWigWriter.Options.defaults());
        var dictionaries=new LinkedHashMap<String,Long>();var branches=new ArrayList<Segment>();for(int c=0;c<270;c++){String name=String.format(java.util.Locale.ROOT,"chr%04d",c);dictionaries.put(name,1000L);for(int n=0;n<7;n++)branches.add(new Segment(name,3L*n,3L*n+2,c-n));}add(root,cases,"multiple-internal-tree-levels",dictionaries,branches,new BigWigWriter.Options(true,2,2,50000000,true,false));
        var many=new ArrayList<Segment>();var random=new SplittableRandom(7359);for(int i=0;i<70000;i++)many.add(new Segment("chr",3L*i,3L*i+1,(random.nextInt(2001)-1000)/8.0));add(root,cases,"many-raw-sections",Map.of("chr",1000000L),many,new BigWigWriter.Options(true,31,3,50000000,true,false));
        for(int seed=0;seed<8;seed++){var rows=new ArrayList<Segment>();var rng=new SplittableRandom(seed);for(int i=0;i<1001;i++)rows.add(new Segment("chr",11L*i,11L*i+1+rng.nextInt(10),Math.scalb(rng.nextDouble(-1,1),rng.nextInt(-100,100))));add(root,cases,"seeded-"+seed,Map.of("chr",20000L),rows,new BigWigWriter.Options(seed%2==0,1+seed*7,2+seed,50000000,false,false));}
        Files.writeString(root.resolve("CONTROLS.json"),RsemJson.json(Map.of("status","NATIVE_OUTPUTS_FOR_INDEPENDENT_ORIGINAL_READERS","cases",cases,"public_integration_credited",false,"biological_accuracy_certified",false))+"\n",StandardCharsets.UTF_8);System.out.println("Exported "+cases.size()+" complete native bigWig reference protocols");
    }
    private static void add(Path root,List<Map<String,Object>> cases,String name,Map<String,Long> reference,List<Segment> rows,BigWigWriter.Options options)throws Exception {
        Path folder=root.resolve(name);Files.createDirectory(folder);var sizes=new StringBuilder();for(var e:reference.entrySet())sizes.append(e.getKey()).append('\t').append(e.getValue()).append('\n');Files.writeString(folder.resolve("reference.sizes"),sizes,StandardCharsets.UTF_8);var input=new StringBuilder();for(var row:rows)input.append(row.chromosome()).append('\t').append(row.start()).append('\t').append(row.end()).append('\t').append(Double.toString(row.value())).append('\n');Files.writeString(folder.resolve("signal.bedGraph"),input,StandardCharsets.UTF_8);var report=BigWigWriter.write(folder.resolve("native.bw"),reference,rows,options,()->{});if(!report.equals(BigWigWriter.verify(folder.resolve("native.bw"),reference,rows,options,()->{})))throw new AssertionError("Native complete replay differs");var document=report(report);Files.writeString(folder.resolve("native-report.json"),RsemJson.json(document)+"\n",StandardCharsets.UTF_8);cases.add(Map.of("id",name,"records",rows.size(),"options",Map.of("compressed",options.compressed(),"itemsPerSection",options.itemsPerSection(),"treeFanout",options.treeFanout(),"maximumBytes",options.maximumBytes(),"requireExactFloat32",options.requireExactFloat32(),"includeUnusedChromosomes",options.includeUnusedChromosomes()),"report",document,"original_converter_expected_dictionary",options.includeUnusedChromosomes()?"used-only-original;native-all-explicit":"same-used-only","scope","Synthetic unchanged complete reference and float64 input; independent readers must verify every stored float32 interval and native double summary."));
    }
    private static Map<String,Object> report(BigWigWriter.Report report){return Map.ofEntries(Map.entry("dictionary",report.dictionary()),Map.entry("records",report.records()),Map.entry("sections",report.sections()),Map.entry("coveredBases",report.coveredBases()),Map.entry("minimum",report.minimum()),Map.entry("maximum",report.maximum()),Map.entry("sum",report.sum()),Map.entry("sumSquares",report.sumSquares()),Map.entry("roundedValues",report.roundedValues()),Map.entry("underflowedValues",report.underflowedValues()),Map.entry("maximumRawBlock",report.maximumRawBlock()),Map.entry("bytes",report.bytes()));}
}
