/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Audits effective TFBSTools 1.50.0 PFMatrix slots, including its original integer coercion. */
public final class SignacPfmConstructionAudit {
    private SignacPfmConstructionAudit() { }
    public record Result(int motifs,int positions,long changedCounts,double maximumCountChange) { }

    /** Accepts source zero columns without repairing them; scanner admission remains a separate gate. */
    public static Result audit(Path effective,List<SignacPfmIO.Model> declared,BooleanSupplier cancelled)throws IOException {
        Objects.requireNonNull(declared);Objects.requireNonNull(cancelled);
        if(declared.isEmpty()||!Files.isRegularFile(effective)||Files.size(effective)>256_000_000L)throw new IOException("Effective PFM capture unavailable/excessive");
        int positions=0;long changed=0;double maximum=0;
        try(var in=new BufferedReader(new InputStreamReader(Files.newInputStream(effective),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))) {
            if(!SignacPfmIO.HEADER.equals(line(in,cancelled)))throw new IOException("Require the exact effective PFM header");
            for(var model:declared) {
                double[][] matrix=model.positions();double[] background=model.background();
                for(int position=0;position<matrix.length;position++) {
                    String raw=line(in,cancelled);if(raw==null)throw new IOException("Missing effective PFM positions");String[] fields=raw.split("\t",-1);
                    if(fields.length!=11||!model.id().equals(fields[0])||!model.name().equals(fields[1])||!Integer.toString(position+1).equals(fields[2]))throw new IOException("Effective PFM identity/name/position/order differs");
                    for(int base=0;base<4;base++) {
                        double value=matrix[position][base];long expected=(long)value;if(expected>Integer.MAX_VALUE)throw new IOException("Source constructor cannot retain this count as a finite R integer");
                        double actual=number(fields[base+3]);if(actual!=expected)throw new IOException("Effective PFM counts differ from original truncation toward zero");
                        if(value!=expected){changed++;maximum=Math.max(maximum,value-expected);}
                        if(number(fields[base+7])!=background[base])throw new IOException("Effective PFM background was changed");
                    }
                    positions++;
                }
            }
            if(line(in,cancelled)!=null)throw new IOException("Unexpected effective PFM models/positions");
        }
        return new Result(declared.size(),positions,changed,maximum);
    }
    private static double number(String value)throws IOException {
        try{if(!value.matches("[+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"))throw new NumberFormatException();double result=Double.parseDouble(value);if(!Double.isFinite(result)||result<0)throw new NumberFormatException();return result;}
        catch(NumberFormatException error){throw new IOException("Malformed effective PFM numeric value",error);}
    }
    private static String line(BufferedReader in,BooleanSupplier cancelled)throws IOException {
        SignacFragments.check(cancelled);StringBuilder raw=new StringBuilder();int c;
        while((c=in.read())>=0&&c!='\n'){if(raw.length()>=16384)throw new IOException("Effective PFM line capacity exceeded");raw.append((char)c);}
        if(c<0&&raw.isEmpty())return null;if(!raw.isEmpty()&&raw.charAt(raw.length()-1)=='\r')raw.setLength(raw.length()-1);return raw.toString();
    }
}
