/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.jlinalg.plot.WesAnderson037;

/** Compare every complete original vector, attributes and error; no sampled color comparisons. */
public final class NativeControl {
    private NativeControl(){ }
    public static void main(String[] args)throws Exception {
        long colors=0;int cases=0,failures=0,mismatches=0;
        var entries=WesAnderson037.palettes().entrySet().iterator();int palettes=0;
        for(String line:Files.readAllLines(Path.of(args[1]),StandardCharsets.UTF_8)){
            String[] r=line.split("\t",-1);if(r.length!=2||!entries.hasNext())throw new IllegalStateException("Original palette population differs");var entry=entries.next();if(!entry.getKey().equals(r[0])||!String.join(",",entry.getValue()).equals(r[1]))throw new IllegalStateException("Complete original palette data/order/case differs");palettes++;
        }
        if(entries.hasNext()||palettes!=24)throw new IllegalStateException("Original24-palette population differs");
        for(String line:Files.readAllLines(Path.of(args[0]),StandardCharsets.UTF_8)){
            String[] r=line.split("\t",-1);if(r.length!=10)throw new IllegalStateException("Complete reference schema differs");cases++;
            Double n=r[2].equals("MISSING")?null:Double.longBitsToDouble(Long.parseUnsignedLong(r[2],16));String type=r[3].equals("MISSING")?null:r[3];WesAnderson037.Palette actual=null;boolean failed=false;
            try{actual=WesAnderson037.palette(r[1],n,type);}catch(IllegalArgumentException e){failed=true;}
            boolean expectedError=r[4].equals("ERROR");
            if(failed!=expectedError){System.err.println(r[0]+" failure differs name="+r[1]+" n="+n+" type="+type+" expected="+r[4]);mismatches++;continue;}
            if(failed){failures++;continue;}
            String[] expected=r[6].isEmpty()?new String[0]:r[6].split(",",-1);String[] returned=actual.colors().toArray(String[]::new);colors+=expected.length;
            if(expected.length!=Integer.parseInt(r[5])||!r[7].equals(actual.name())||!r[8].equals(actual.sourceClass())||!Arrays.equals(expected,returned)){
                int first=0;while(first<Math.min(expected.length,returned.length)&&expected[first].equals(returned[first]))first++;
                if(mismatches<25)System.err.println(r[0]+" complete palette differs name="+r[1]+" n="+n+" type="+type+" expected_length="+expected.length+" actual_length="+returned.length+" first="+first+" expected="+(first<expected.length?expected[first]:"END")+" actual="+(first<returned.length?returned[first]:"END"));mismatches++;
            }
        }
        if(mismatches>0)throw new IllegalStateException("Complete original palette mismatches="+mismatches+" / cases="+cases);
        boolean capacity=false;try{WesAnderson037.palette("Royal1",100.0,"continuous",3);}catch(IllegalArgumentException e){capacity=e.getMessage().contains("no prefix");}if(!capacity)throw new IllegalStateException("Complete output capacity was not enforced");
        if(cases!=6108)throw new IllegalStateException("Incomplete original case population");System.out.println("PASS_COMPLETE_ORIGINAL_WESANDERSON037_NATIVE cases="+cases+" complete_colors="+colors+" original_failures="+failures+" palettes="+palettes);
    }
}
