/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Literal UTF-8 matrices and exact source positional metadata/effect ordering. */
final class GenomicsemData {
    private GenomicsemData() { }
    record Unit(String id,String chromosome,String position,String maf,String allele1,String allele2,String panel,String hsq){ }
    record Inputs(List<String> traits,Map<String,Unit> units,Map<String,Object> summary) { }
    static BufferedReader reader(Path path)throws IOException {
        return new BufferedReader(new InputStreamReader(Files.newInputStream(path),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
    }
    static String line(BufferedReader in,long maximum)throws IOException {
        var b=new StringBuilder();for(int c;(c=in.read())!=-1;){if(c=='\n')return b.toString();if(c=='\r'){in.mark(1);int next=in.read();if(next!='\n'&&next!=-1)in.reset();return b.toString();}require(b.length()<maximum,"Input/result line exceeds maximum_line_characters");b.append((char)c);}return b.isEmpty()?null:b.toString();
    }
    static double numeric(String s)throws IOException {
        require(s.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Require an explicit finite numeric token,without missing-value substitution: "+s);
        try{double d=Double.parseDouble(s);require(Double.isFinite(d),"Nonfinite/out-of-range numeric value");return d;}catch(NumberFormatException e){throw new IOException("Invalid numeric value",e);}
    }
    static List<String> pairs(List<String> traits){var out=new ArrayList<String>();for(int i=0;i<traits.size();i++)for(int j=i;j<traits.size();j++)out.add(traits.get(i)+":"+traits.get(j));return out;}
    private record Matrix(List<String> names,double[] data){ }
    private static Matrix matrix(Path file,List<String> expected,Map<String,Object> request)throws IOException {
        long limit=GenomicsemContracts.cap(request,"maximum_line_characters");
        try(var in=reader(file)){
            String header=line(in,limit);require(header!=null,"Empty prepared matrix");var h=Arrays.asList(header.split("\t",-1));require(h.size()>1&&h.get(0).equals("id"),"Prepared matrix starts with literal id then ordered column labels");var names=List.copyOf(h.subList(1,h.size()));
            require(new HashSet<>(names).size()==names.size(),"Duplicated matrix labels");if(expected!=null)require(names.equals(expected),"Matrix row/column/vech order differs from the declared covariance trait order");
            int n=names.size();require((long)n*n<=GenomicsemContracts.cap(request,"maximum_values"),"Matrix value capacity exceeded");if(expected==null)require(n>=2&&n<=GenomicsemContracts.cap(request,"maximum_traits"),"Require2..maximum_traits prepared traits");
            double[] values=new double[Math.multiplyExact(n,n)];int row=0;String s;
            while((s=line(in,limit))!=null){require(row<n,"Too many prepared matrix rows");String[] v=s.split("\t",-1);require(v.length==n+1&&v[0].equals(names.get(row)),"Matrix row label/order/width mismatch");for(int j=0;j<n;j++)values[row*n+j]=numeric(v[j+1]);row++;}
            require(row==n,"Incomplete square prepared matrix");for(int i=0;i<n;i++)for(int j=0;j<i;j++)require(values[i*n+j]==values[j*n+i],"Require exactly symmetric prepared matrices;no averaging/repair occurs");return new Matrix(names,values);
        }
    }
    static Inputs inputs(Path root,Map<String,Object> request)throws IOException {
        String op=text(request.get("operation"),"operation");Matrix s=matrix(GenomicsemContracts.input(root,"covariance"),null,request);var traits=s.names();for(String t:traits)require(t.matches("[A-Za-z][A-Za-z0-9_]{0,63}")&&!Set.of("SNP","Gene").contains(t),"Traits require unique simple lavaan identifiers,excluding SNP/Gene");
        if(op.startsWith("COMMONFACTOR"))require(traits.size()>=3,"Original common-factor workflows require at least3 indicators");
        Matrix v=matrix(GenomicsemContracts.input(root,"sampling_covariance"),pairs(traits),request);Map<String,Unit> units=new LinkedHashMap<>();long numericValues=(long)s.data().length+v.data().length;require(numericValues<=GenomicsemContracts.cap(request,"maximum_values"),"Combined prepared numeric-value capacity exceeded");
        if("correlation".equals(object(request.get("options")).get("covariance_scale")))for(int i=0;i<traits.size();i++)require(Math.abs(s.data()[i*traits.size()+i]-1)<=1e-10,"Declared correlation matrix requires unit diagonal;no rescaling is performed");
        if(GenomicsemContracts.association(op)){
            Matrix intercepts=matrix(GenomicsemContracts.input(root,"intercepts"),traits,request);numericValues+=intercepts.data().length;boolean tw=GenomicsemContracts.twas(op);List<String> metadata=tw?List.of("Gene","Panel","HSQ"):List.of("SNP","CHR","BP","MAF","A1","A2");long cap=GenomicsemContracts.cap(request,"maximum_line_characters");
            try(var in=reader(GenomicsemContracts.input(root,"associations"))){String header=line(in,cap);require(header!=null,"Empty association matrix");List<String> h=Arrays.asList(header.split("\t",-1));require(h.size()==metadata.size()+2*traits.size()&&h.subList(0,metadata.size()).equals(metadata),"Original GenomicSEM positional metadata must be "+metadata+" then exactly aligned beta.<trait>/se.<trait> columns");
                require(new HashSet<>(h).size()==h.size(),"Duplicated effect/metadata headers");require(h.stream().filter(x->x.startsWith("beta.")).toList().equals(traits.stream().map(t->"beta."+t).toList())&&h.stream().filter(x->x.startsWith("se.")).toList().equals(traits.stream().map(t->"se."+t).toList()),"Relative beta and SE orders must each equal S trait order;no column reordering occurs");
                for(int j=metadata.size();j<h.size();j++)require(h.get(j).startsWith("beta.")||h.get(j).startsWith("se."),"Unknown prepared association column");
                String row;long count=0;while((row=line(in,cap))!=null){require(++count<=GenomicsemContracts.cap(request,"maximum_rows"),"Association row capacity exceeded");String[] f=row.split("\t",-1);require(f.length==h.size(),"Association row width mismatch");for(String x:f)require(!x.isEmpty()&&x.codePoints().noneMatch(c->c<32||c==127),"Blank/control characters in prepared association row");
                    require(f[0].length()<=4096,"Association identifier length exceeded");Unit u;
                    if(tw){require(numeric(f[2])>0,"Prepared FUSION gene variance HSQ must be positive");u=new Unit(f[0],null,null,null,null,null,f[1],f[2]);}
                    else{require(f[2].matches("[1-9][0-9]{0,9}")&&Long.parseLong(f[2])<=Integer.MAX_VALUE,"BP must be a canonical positive position within upstream integer coordinates");double maf=numeric(f[3]);require(maf>0&&maf<=.5,"MAF must lie in (0,0.5]");require(!f[4].equals(f[5]),"Identical A1/A2 labels are not aligned effect alleles");u=new Unit(f[0],f[1],f[2],f[3],f[4],f[5],null,null);}
                    String key=unitKey(u);require(!units.containsKey(key),"Duplicated SNP or Gene/Panel identifier");units.put(key,u);
                    numericValues+=2L*traits.size()+(tw?1:2);require(numericValues<=GenomicsemContracts.cap(request,"maximum_values"),"Combined prepared numeric-value capacity exceeded");for(int j=metadata.size();j<f.length;j++){double d=numeric(f[j]);if(h.get(j).startsWith("se."))require(d>0,"Every prepared effect SE must be positive");}
                }require(count>0,"No prepared association rows");
            }
        }
        if(GenomicsemContracts.user(op)){byte[] raw=ScenicWorkflowFiles.bytes(GenomicsemContracts.input(root,"model"),1048576);String model=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(raw)).toString();require(!model.isBlank()&&model.codePoints().noneMatch(c->c==0||c==127),"Require a literal nonempty UTF-8 lavaan model");if(GenomicsemContracts.association(op))require(model.contains(GenomicsemContracts.twas(op)?"Gene":"SNP"),"Model must name its original Gene/SNP predictor");}
        var summary=map("traits",traits,"trait_count",traits.size(),"sampling_covariance_dimension",v.names().size(),"sampling_order","lower-column-including-diagonal","source_upper_row_equivalence_for_symmetric_S",true,"association_units",units.size(),"operation",op,"input_scale",object(request.get("options")).get("covariance_scale"),"alleles_or_columns_reordered",false,"missing_values_imputed",false,"full_sampling_dependencies_supplied_by_caller",true);
        return new Inputs(traits,Collections.unmodifiableMap(units),summary);
    }
    static String unitKey(Unit u){return u.panel()==null?u.id():u.id()+"\u0000"+u.panel();}
}
