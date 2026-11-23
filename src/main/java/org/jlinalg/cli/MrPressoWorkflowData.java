/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import org.jlinalg.mrpresso.MrPressoOriginal;
import org.jlinalg.mrpresso.MrPressoRandom;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.MrPressoWorkflowFiles.*;

/** Literal unquoted TSV associations and complete-case/work accounting. */
final class MrPressoWorkflowData {
    private MrPressoWorkflowData() { }
    record Prepared(MrPressoOriginal.Data data,Map<String,Object> metadata) { }

    static Prepared read(Path path,Map<String,Object> request)throws IOException {
        byte[] raw=bytes(path,MrPressoWorkflowContracts.limit(request,"maximum_input_bytes"));
        String content=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(raw)).toString();
        require(!content.startsWith("\ufeff")&&content.indexOf('\0')<0,"No BOM/NUL repair is performed; supply exact supported UTF-8 TSV");
        var input=object(request.get("inputs"));var names=MrPressoWorkflowContracts.columns(input.get("beta_exposure"));var errors=MrPressoWorkflowContracts.columns(input.get("se_exposure"));
        var labels=new ArrayList<String>();var ys=new ArrayList<Double>();var sys=new ArrayList<Double>();var xs=new ArrayList<double[]>();var sxs=new ArrayList<double[]>();
        int complete=0;long rows=0;
        try(var reader=new BufferedReader(new StringReader(content))){
            String first=reader.readLine();require(first!=null,"Association table is empty");String[] header=first.split("\t",-1);var columns=new LinkedHashMap<String,Integer>();
            for(int j=0;j<header.length;j++){MrPressoWorkflowContracts.column(header[j]);require(columns.putIfAbsent(header[j],j)==null,"Duplicate literal table column; no repair");}
            int id=index(columns,text(input.get("id_column"),"id_column")),outcome=index(columns,text(input.get("beta_outcome"),"beta_outcome")),outcomeError=index(columns,text(input.get("se_outcome"),"se_outcome"));
            int k=names.size();int[] exposure=new int[k],exposureError=new int[k];for(int j=0;j<k;j++){exposure[j]=index(columns,names.get(j));exposureError[j]=index(columns,errors.get(j));}
            for(String line;(line=reader.readLine())!=null;){
                rows++;require(rows<=MrPressoWorkflowContracts.limit(request,"maximum_rows")&&rows*k<=MrPressoWorkflowContracts.limit(request,"maximum_cells"),"Complete table exceeds declared row/cell capacity; no truncation");
                String[] values=line.split("\t",-1);require(values.length==header.length,"Require exact unquoted tab-separated width at data row "+rows);
                String label=values[id];MrPressoWorkflowContracts.column(label);labels.add(label);
                double y=numericToken(values[outcome],rows,header[outcome]),sy=numericToken(values[outcomeError],rows,header[outcomeError]);
                double[] x=new double[k],sx=new double[k];boolean missing=Double.isNaN(y)||Double.isNaN(sy);
                for(int j=0;j<k;j++){x[j]=numericToken(values[exposure[j]],rows,names.get(j));sx[j]=numericToken(values[exposureError[j]],rows,errors.get(j));missing|=Double.isNaN(x[j])||Double.isNaN(sx[j]);}
                if(!missing)complete++;ys.add(y);sys.add(sy);xs.add(x);sxs.add(sx);
            }
        }
        int k=names.size();long distributions=number(object(request.get("options")),"NbDistribution"),work;
        try{
            long perRow=Math.addExact(Math.multiplyExact((long)complete,k*k),Math.multiplyExact((long)k*k,k));
            work=Math.multiplyExact(Math.multiplyExact(distributions,complete),perRow);
            if(MrPressoWorkflowContracts.bool(object(request.get("options")),"DISTORTIONtest",false))work=Math.addExact(work,Math.multiplyExact(Math.multiplyExact(distributions,(long)complete*complete),k));
        }catch(ArithmeticException excessive){throw new IOException("Estimated native work overflows capacity; no analysis started",excessive);}
        require(work<=MrPressoWorkflowContracts.limit(request,"maximum_estimated_work"),"Estimated native simulation work exceeds declared capacity; increase the explicit limit if appropriate, never reduce data silently");
        var data=new MrPressoOriginal.Data(labels,names,ys.stream().mapToDouble(Double::doubleValue).toArray(),xs.toArray(double[][]::new),sys.stream().mapToDouble(Double::doubleValue).toArray(),sxs.toArray(double[][]::new));
        return new Prepared(data,map("raw_rows",rows,"complete_cases",complete,"exposures",k,"estimated_work",work,"format","UTF-8_UNQUOTED_TSV","input_identity",pin(raw)));
    }
    static MrPressoRandom random(Map<String,Object> request)throws IOException {
        var rng=object(request.get("rng"));
        if(rng.containsKey("seed"))return new MrPressoRandom((int)integer(rng.get("seed"),Integer.MIN_VALUE+1L,Integer.MAX_VALUE),"Rejection".equals(rng.get("sample_kind"))?MrPressoRandom.Sampling.REJECTION:MrPressoRandom.Sampling.ROUNDING);
        var values=list(rng.get("state"));int[] state=new int[626];for(int i=0;i<state.length;i++)state[i]=(int)integer(values.get(i),Integer.MIN_VALUE,Integer.MAX_VALUE);return new MrPressoRandom(state);
    }
    static MrPressoOriginal.Options options(Map<String,Object> request)throws IOException {
        var options=object(request.get("options"));return new MrPressoOriginal.Options(MrPressoWorkflowContracts.bool(options,"OUTLIERtest",false),MrPressoWorkflowContracts.bool(options,"DISTORTIONtest",false),probability(options.get("SignifThreshold")).doubleValue(),(int)number(options,"NbDistribution"));
    }
    private static int index(Map<String,Integer> columns,String name)throws IOException{require(columns.containsKey(name),"Missing exact association column: "+name);return columns.get(name);}
    private static double numericToken(String token,long row,String column)throws IOException {
        return switch(token){
            case "NA","NaN"->Double.NaN;case "Inf","+Inf","Infinity","+Infinity"->Double.POSITIVE_INFINITY;case "-Inf","-Infinity"->Double.NEGATIVE_INFINITY;
            default->{require(token.matches("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?"),"Unsupported numeric token at row "+row+", column "+column+"; no trimming/repair");yield Double.parseDouble(token);}
        };
    }
}
