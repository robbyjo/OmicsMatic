/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.SmrWorkflowInputs.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.require;

/** Complete bounded little-endian BESD0/5 dense and0x40400000/3 sparse input reader. */
final class SmrWorkflowBesd {
    private SmrWorkflowBesd() { }
    static Qtl read(Path prefix,SmrWorkflowRequest.Request r,Budget budget)throws IOException {
        var variants=new ArrayList<Variant>();var ids=new HashSet<String>();for(String[] x:table(Path.of(prefix+".esi"),7,r.variants(),r)){id(x[1]);require(ids.add(x[1]),"Repeated BESD SNP ID");alleles(x[4],x[5]);number(x[2]);variants.add(new Variant(x[1],chromosome(x[0]),position(x[3]),x[4],x[5],frequency(x[6])));}
        var probes=new ArrayList<Probe>();ids.clear();for(String[] x:table(Path.of(prefix+".epi"),6,r.probesLimit(),r)){id(x[1]);require(ids.add(x[1]),"Repeated BESD probe ID");number(x[2]);probes.add(new Probe(x[1],chromosome(x[0]),position(x[3]),x[4],orientation(x[5])));}
        var entries=new LinkedHashMap<String,List<Entry>>();int ns=variants.size(),np=probes.size();long n=-9;
        try(var file=new RandomAccessFile(Path.of(prefix+".besd").toFile(),"r")){
            int type=i32(file);require(Set.of(0,5,3,0x40400000).contains(type),"Unsupported literal BESD format; no format inference");boolean modern=type==3||type==5,sparse=type==3||type==0x40400000;long base=modern?64:4;
            if(modern){n=i32(file);require(n==-9||n>0,"Invalid BESD sample-size metadata");require(i32(file)==ns&&i32(file)==np,"BESD dimensions disagree with complete ESI/EPI axes");for(int i=4;i<16;i++)require(i32(file)==-9,"Unexpected BESD reserved metadata; select a supported profile");}
            if(sparse){
                long values=i64(file);require(values>=0&&values%2==0&&values/2<=r.qtlEntries(),"Sparse BESD value count exceeds capacity or has unpaired beta/SE values");long columns=2L*np+1,rowBase=base+8+8*columns,valueBase=rowBase+4*values;require(file.length()==valueBase+4*values,"Sparse BESD size/offsets disagree; no truncation or trailing bytes admitted");long[] col=new long[Math.toIntExact(columns)];for(int i=0;i<col.length;i++){col[i]=i64(file);require(col[i]>=0&&col[i]<=values&&(i==0?col[i]==0:col[i]>=col[i-1]),"Invalid sparse BESD column pointer");}require(col[col.length-1]==values,"Sparse BESD unreferenced trailing values");
                for(int p=0;p<np;p++){
                    long b=col[2*p],s=col[2*p+1],end=col[2*p+2];require(s-b==end-s,"Sparse BESD beta/SE populations differ");var seen=new HashSet<Integer>();var row=new ArrayList<Entry>();
                    for(long j=0;j<s-b;j++){
                        budget.add(1);file.seek(rowBase+4*(b+j));int index=i32(file);require(index>=0&&index<ns&&seen.add(index),"Sparse BESD SNP index duplicate/outside ESI axis");file.seek(rowBase+4*(s+j));require(i32(file)==index,"Sparse BESD beta/SE row identities differ");file.seek(valueBase+4*(b+j));float beta=f32(file);file.seek(valueBase+4*(s+j));float se=f32(file);check(beta,se);require(Math.abs(se+9)>=1e-6,"Sparse BESD explicitly stores a missing sentinel that original sparse selection does not omit; supply a valid explicitly prepared dataset, no silent omission");row.add(new Entry(probes.get(p).id(),variants.get(index).id(),beta,se));
                    }
                    entries.put(probes.get(p).id(),List.copyOf(row));
                }
            }else{
                long cells=(long)ns*np;require(cells<=r.qtlEntries(),"Complete dense BESD population exceeds maximum_qtl_entries, including sentinels");require(file.length()==base+8*cells,"Dense BESD size/dimensions disagree; no truncation/trailing bytes admitted");for(int p=0;p<np;p++){var row=new ArrayList<Entry>();for(int j=0;j<ns;j++){budget.add(1);file.seek(base+8L*ns*p+4L*j);float beta=f32(file);file.seek(base+8L*ns*p+4L*ns+4L*j);float se=f32(file);check(beta,se);row.add(new Entry(probes.get(p).id(),variants.get(j).id(),beta,se));}entries.put(probes.get(p).id(),List.copyOf(row));}
            }
            return new Qtl(List.copyOf(variants),List.copyOf(probes),Collections.unmodifiableMap(entries),type,n);
        }catch(EOFException e){throw new IOException("Truncated BESD; no result admitted",e);}
    }
    private static void check(float b,float se)throws IOException {require(Float.isFinite(b)&&Float.isFinite(se),"Nonfinite BESD observation; no replacement");if(Math.abs(se+9)<1e-6)require(Math.abs(b+9)<1e-6,"Unpaired BESD missing sentinel");else require(se>0&&Math.abs(b+9)>1e-6,"Invalid BESD SE or ambiguous beta sentinel");}
    private static int i32(RandomAccessFile f)throws IOException{return Integer.reverseBytes(f.readInt());}
    private static long i64(RandomAccessFile f)throws IOException{return Long.reverseBytes(f.readLong());}
    private static float f32(RandomAccessFile f)throws IOException{return Float.intBitsToFloat(i32(f));}
}
