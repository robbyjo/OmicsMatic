/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.meta;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.lang.reflect.Array;
import java.util.*;

/** Developer qualification probe. Uses the packaged public API, never copied statistics. */
public final class Probe {
    private static double[] numbers(String text){return Arrays.stream(text.split(",")).mapToDouble(Double::parseDouble).toArray();}
    public static void main(String[] args)throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("Require cases.tsv and NEW output directory");
        var source=Path.of(SampleSizeWeightedMetaAnalysis.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        if(!source.getFileName().toString().endsWith(".jar"))throw new IllegalStateException("Qualification must load the public API from the packaged JAR");
        System.out.println("Public API loaded from packaged JAR: "+source);
        Path out=Path.of(args[1]);Files.createDirectory(out);int finite=0,refused=0;var ids=new HashSet<String>();
        var lines=Files.readAllLines(Path.of(args[0]),StandardCharsets.UTF_8);
        if(!lines.get(0).equals("id\tmode\tvalues\tdirections\tweights\texpected"))throw new IllegalArgumentException("Unknown fixture schema");
        for(String line:lines.subList(1,lines.size())){
            var c=line.split("\t",-1);if(c.length!=6||!c[0].matches("[A-Za-z0-9_-]+")||!ids.add(c[0]))throw new IllegalArgumentException("Invalid/duplicate fixture case");
            double[] values=numbers(c[2]),weights=numbers(c[4]);int[] signs=c[3].isEmpty()?new int[0]:Arrays.stream(c[3].split(",")).mapToInt(Integer::parseInt).toArray();
            SampleSizeWeightedMetaAnalysis.Result result;
            try{
                result=switch(c[1]){
                    case "z"->SampleSizeWeightedMetaAnalysis.fitSignedZ(values,weights);
                    case "p"->SampleSizeWeightedMetaAnalysis.fitPValues(values,signs,weights);
                    case "logp"->SampleSizeWeightedMetaAnalysis.fitLogPValues(values,signs,weights);
                    default->throw new IllegalArgumentException("Unknown fixture mode");
                };
            }catch(ArithmeticException e){
                if(!c[5].equals("refuse-unrepresentable"))throw e;
                Files.writeString(out.resolve(c[0]+".refusal.txt"),"NO_COMPLETE_MODEL\n"+e.getClass().getName()+"\n"+e.getMessage()+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);refused++;continue;
            }
            if(!c[5].equals("finite"))throw new AssertionError("Unexpected admission: "+c[0]);
            var fields=new TreeMap<String,String>();
            for(var component:result.getClass().getRecordComponents()){
                Object value=component.getAccessor().invoke(result);
                if(value!=null&&value.getClass().isArray())for(int i=0;i<Array.getLength(value);i++)fields.put(component.getName()+"."+i,String.valueOf(Array.get(value,i)));
                else fields.put(component.getName(),value==null?"NA":String.valueOf(value));
            }
            var text=new StringBuilder("field\tvalue\n");fields.forEach((k,v)->text.append(k).append('\t').append(v).append('\n'));
            Files.writeString(out.resolve(c[0]+".tsv"),text,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);finite++;
        }
        Files.writeString(out.resolve("COMPLETED.properties"),"finite_models="+finite+"\nexpected_unrepresentable="+refused+"\ncomplete_fixture_population="+ids.size()+"\n",StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
        System.out.println("Packaged API: "+finite+" complete finite models; "+refused+" expected complete-model refusals");
    }
}
