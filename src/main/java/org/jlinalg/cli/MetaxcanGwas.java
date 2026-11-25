/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import jdistlib.Normal;

/** Independent original GWAS ingestion: priority, source filtering and float32 Z rounding. */
final class MetaxcanGwas {
    private MetaxcanGwas(){ }
    // pandas1.5.3 default NA strings, plus original MetaXcan object-column sanitation.
    static final Set<String> MISSING=Set.of("","#N/A","#N/A N/A","#NA","-1.#IND","-1.#QNAN","-NaN","-nan","1.#IND","1.#QNAN","<NA>","N/A","NA","NULL","NaN","n/a","nan","null");
    record Row(String id,String effectAllele,String otherAllele,double zscore,Double beta) { }
    record Disposition(long ordinal,String id,String status,Double unroundedZ,Double sourceZ,Double sourceBeta) { }
    @FunctionalInterface interface Sink {void accept(Disposition row)throws IOException;}
    record Result(Map<String,Row> rows,long sourceRows,long missingIds,long nonRsIds,long nonfiniteZ,
                  boolean oddsRatioOverridesBeta,Double divergentFloor,String zscoreScheme) { }
    static Result read(Path path,boolean gzip,MetaxcanRequest.Columns c,boolean keepNonRsid,
                       boolean floorDivergent,MetaxcanRequest.Capacity capacity,
                       MetaxcanTable.Budget budget,Sink sink)throws IOException {
        boolean fromP=c.zscore()==null&&c.pvalue()!=null;
        double floor=Double.NaN;boolean divergent=false;long visited=0;
        // The original derives a single floor from ALL retained GWAS rows before
        // model intersection. A streaming first pass avoids a second full table in memory.
        if(fromP&&floorDivergent)try(var table=open(path,gzip,c,capacity,budget)) {
            var indices=indices(table,c);for(List<String> row;(row=table.next())!=null;) {
                work(++visited,c,capacity);String id=row.get(indices[0]);if(missing(id)||!keepNonRsid&&!id.contains("rs"))continue;
                double p=number(row.get(indices[5]));domainP(p);double z=absoluteZ(p);
                if(Double.isInfinite(z))divergent=true;
                if(Double.isFinite(z)&&p!=0&&(Double.isNaN(floor)||p<floor))floor=p;
            }
            if(divergent&&Double.isNaN(floor))throw new IOException("Original divergent-P correction has no nonzero finite P from which to derive a floor (e.g. all P=0). Supply valid Z or BETA/SE, or explicitly discard divergent rows; no floor is invented");
            if(divergent)floor=Math.min(floor,1e-50);
        }
        Double usedFloor=divergent&&floorDivergent?floor:null;
        var admitted=new LinkedHashMap<String,Row>();var identities=new HashSet<String>();long missing=0,nonRs=0,nonfinite=0,sourceRows=0;boolean textualIdColumn=false;
        try(var table=open(path,gzip,c,capacity,budget)) {
            var ix=indices(table,c);for(List<String> row;(row=table.next())!=null;) {
                work(++visited,c,capacity);sourceRows++;String id=row.get(ix[0]);
                if(missing(id)){missing++;sink.accept(new Disposition(sourceRows,id,"SOURCE_MISSING_ID",null,null,null));continue;}
                identifier(id);
                if(!id.matches("(?i)[-+]?(?:(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?|inf(?:inity)?)"))textualIdColumn=true;
                if(!keepNonRsid&&!id.contains("rs")){nonRs++;sink.accept(new Disposition(sourceRows,id,"SOURCE_NON_RSID_FILTER",null,null,null));continue;}
                if(identities.contains(id))throw new IOException("Duplicate retained GWAS identifier is ambiguous; no first-row selection or deduplication: "+id);
                if(identities.size()>=capacity.numericBytes()/512)throw new IOException("Complete GWAS numeric/identity memory estimate exceeds declared numeric_bytes");identities.add(id);
                double rawZ=value(row,ix[3]),beta=value(row,ix[4]),p=value(row,ix[5]),se=value(row,ix[6]),odds=value(row,ix[7]);
                if(ix[7]>=0){if(!Double.isNaN(odds)&&(!Double.isFinite(odds)||odds<=0))throw new IOException("Invalid odds ratio for "+id+"; require positive finite OR or an explicit missing value");beta=Math.log(odds);}
                if(c.zscore()==null) {
                    if(fromP) {
                        domainP(p);double absolute=absoluteZ(p);if(Double.isInfinite(absolute)&&usedFloor!=null)absolute=absoluteZ(usedFloor);
                        double sign;
                        if(c.beta()!=null||c.oddsRatio()!=null)sign=Math.signum(beta);
                        else {String declared=row.get(ix[8]);if(!Set.of("+","-").contains(declared))throw new IOException("Require unambiguous literal + or - beta_sign for "+id+"; METAL direction strings are not reinterpreted");sign=declared.equals("+")?1:-1;}
                        rawZ=absolute*sign;
                    }else rawZ=beta/se;
                }
                // Upstream explicitly converts every ingested Z array to numpy.float32.
                double z=(double)(float)rawZ;Double sourceBeta=c.beta()!=null||c.oddsRatio()!=null?beta:null;
                if(!Double.isFinite(z)){nonfinite++;sink.accept(new Disposition(sourceRows,id,"SOURCE_NONFINITE_Z_FILTER",nullable(rawZ),null,nullable(sourceBeta)));continue;}
                if(sourceBeta!=null&&!Double.isFinite(sourceBeta))throw new IOException("Finite Z with missing/nonfinite source BETA for "+id+" would give an unreportable effect; supply compatible statistics or omit the beta mapping explicitly");
                String ea=row.get(ix[1]).toUpperCase(Locale.ROOT),oa=row.get(ix[2]).toUpperCase(Locale.ROOT);
                if(!ea.matches("[ACGT]+")||!oa.matches("[ACGT]+")||ea.equals(oa))throw new IOException("Unresolved GWAS alleles for "+id+"; provide a separately prepared resolved file");
                admitted.put(id,new Row(id,ea,oa,z,sourceBeta));sink.accept(new Disposition(sourceRows,id,"SOURCE_FINITE_Z",rawZ,z,sourceBeta));
            }
        }
        if(!textualIdColumn)throw new IOException("All-numeric GWAS identifier columns require separate pandas dtype qualification. Supply separately prepared textual model-compatible identifiers; no numeric-to-text coercion is admitted");
        if(admitted.isEmpty())throw new IOException("No finite GWAS rows remain under the explicit source filtering policy");
        return new Result(Collections.unmodifiableMap(admitted),sourceRows,missing,nonRs,nonfinite,c.oddsRatio()!=null&&c.beta()!=null,usedFloor,c.zscore()!=null?"declared-Z":fromP?"P-and-effect-sign":"BETA-or-logOR-divided-by-SE");
    }
    private static void work(long rows,MetaxcanRequest.Columns c,MetaxcanRequest.Capacity capacity)throws IOException {if(rows>capacity.multiplyAdds()/16)throw new IOException("Complete GWAS scan work exceeds multiply_adds");}
    private static MetaxcanTable open(Path p,boolean gzip,MetaxcanRequest.Columns c,MetaxcanRequest.Capacity cap,MetaxcanTable.Budget b)throws IOException{return new MetaxcanTable(p,gzip,c.separator(),cap.variants(),b,true);}
    private static int[] indices(MetaxcanTable t,MetaxcanRequest.Columns c)throws IOException {
        String[] names={c.snp(),c.effectAllele(),c.otherAllele(),c.zscore(),c.beta(),c.pvalue(),c.se(),c.oddsRatio(),c.betaSign()};int[] result=new int[names.length];
        for(int i=0;i<names.length;i++)result[i]=names[i]==null?-1:t.column(names[i]);return result;
    }
    static boolean missing(String s){return MISSING.contains(s);}
    static double number(String raw)throws IOException {
        if(missing(raw)||raw.equals(".")||raw.equals("\\N"))return Double.NaN;String s=raw;
        if(s.matches("^[-+]?[0-9]*,[0-9]+([eE][-+]?[0-9]+)?$"))s=s.replace(',','.');
        s=s.strip();if(s.equalsIgnoreCase("inf")||s.equalsIgnoreCase("+inf")||s.equalsIgnoreCase("infinity")||s.equalsIgnoreCase("+infinity"))return Double.POSITIVE_INFINITY;if(s.equalsIgnoreCase("-inf")||s.equalsIgnoreCase("-infinity"))return Double.NEGATIVE_INFINITY;
        // Python/numpy decimal parsing does not admit Java hexadecimal literals or suffixes.
        if(!s.matches("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?"))throw new IOException("Invalid original decimal GWAS number: "+raw);
        try{return Double.parseDouble(s);}catch(NumberFormatException e){throw new IOException("Invalid GWAS number",e);}
    }
    private static double value(List<String> row,int index)throws IOException{return index<0?Double.NaN:number(row.get(index));}
    private static void domainP(double p)throws IOException{if(!Double.isNaN(p)&&(!Double.isFinite(p)||p<0||p>1))throw new IOException("GWAS P must be missing or in[0,1]; invalid P is never repaired");}
    private static double absoluteZ(double p){return -Normal.quantile(p/2,0,1,true,false);}
    private static Double nullable(Double x){return x!=null&&Double.isFinite(x)?x:null;}
    private static void identifier(String s)throws IOException{if(s.length()>1048576||s.chars().anyMatch(c->c<=32||c==127))throw new IOException("Unbounded/whitespace-containing GWAS identifier");}
}
