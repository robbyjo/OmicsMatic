/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.genetics;

import java.io.*;
import java.math.*;
import java.util.*;

/** CADD conversion-table ceiling lookup. A conversion table is model and assembly specific. */
public final class CaddCalibration {
    private final NavigableMap<Double,String> values;
    private CaddCalibration(NavigableMap<Double,String> values){this.values=Collections.unmodifiableNavigableMap(values);}
    /** The original two-column file contains PHRED text followed by a raw-score boundary. */
    public static CaddCalibration read(Reader supplied,int maximumRows,int maximumLineCharacters)throws IOException {
        if(maximumRows<1||maximumLineCharacters<1)throw new IllegalArgumentException("Positive complete-table capacities required");
        var values=new TreeMap<Double,String>();long rows=0;var reader=new BufferedReader(supplied);String line;
        while((line=line(reader,maximumLineCharacters))!=null){if(++rows>maximumRows)throw new IOException("Complete CADD conversion-table capacity exceeded");String[] fields=line.trim().split("\\s+");if(fields.length!=2)continue;
            double boundary=parse(fields[1]);if(!Double.isFinite(boundary))throw new IOException("Nonfinite CADD table boundary");double phred=parse(fields[0]);if(!Double.isFinite(phred))throw new IOException("Nonfinite CADD PHRED table value");values.put(boundary==0?0.0:boundary,fields[0]);
        }
        if(values.isEmpty())throw new IOException("Empty CADD conversion table");return new CaddCalibration(values);
    }
    /** Equal boundaries match; intermediate scores take the next greater boundary; tails clamp. */
    public String phred(double raw){if(Double.isNaN(raw))return "NA";var entry=values.ceilingEntry(raw==0?0.0:raw);return entry==null?values.lastEntry().getValue():entry.getValue();}
    public int boundaries(){return values.size();}
    public static double parse(String raw)throws IOException {int start=0,end=raw.length();String whitespace=" \t\r\n\f\u000b";while(start<end&&whitespace.indexOf(raw.charAt(start))>=0)start++;while(end>start&&whitespace.indexOf(raw.charAt(end-1))>=0)end--;String text=raw.substring(start,end).toLowerCase(Locale.ROOT);if(!text.matches("[+-]?(?:(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:e[+-]?[0-9]+)?|nan|inf(?:inity)?)"))throw new IOException("Invalid literal CADD raw score: "+raw);try{return switch(text){case "nan","+nan","-nan"->Double.NaN;case "inf","+inf","infinity","+infinity"->Double.POSITIVE_INFINITY;case "-inf","-infinity"->Double.NEGATIVE_INFINITY;default->Double.parseDouble(text);};}catch(NumberFormatException e){throw new IOException("Invalid literal CADD raw score: "+raw,e);}}
    /** Six decimals from the binary double, retaining negative zero and nonfinite source tokens. */
    public static String sourceRaw(double raw){if(Double.isNaN(raw))return "nan";if(Double.isInfinite(raw))return raw<0?"-inf":"inf";boolean negative=Double.doubleToRawLongBits(raw)<0;String text=new BigDecimal(raw).setScale(6,RoundingMode.HALF_EVEN).toPlainString();return negative&&text.charAt(0)!='-'?"-"+text:text;}
    private static String line(Reader reader,int cap)throws IOException {var s=new StringBuilder();int c;while((c=reader.read())!=-1){if(c=='\n')break;if(s.length()>=cap)throw new IOException("Complete CADD table line capacity exceeded");s.append((char)c);}if(c==-1&&s.isEmpty())return null;if(!s.isEmpty()&&s.charAt(s.length()-1)=='\r')s.setLength(s.length()-1);return s.toString();}
}
