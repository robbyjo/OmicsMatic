/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import jdistlib.Normal;
import jdistlib.ChiSquare;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.ScenicWorkflowFiles.*;

/** Independently decode original typed exports; retain unavailable/endpoint fields literally. */
final class GenomicsemAudit {
    private GenomicsemAudit() { }
    private record Cell(String token,String value,boolean missing,boolean nonfinite) {
        static Cell parse(String token,String kind)throws IOException {
            if(token.equals("N"))return new Cell(token,"NA",true,false);
            if(Set.of("Q","P","M").contains(token)){require(kind.equals("double"),"Nonfinite marker in a non-double original column");return new Cell(token,token,false,true);}
            require(token.length()>=2&&token.charAt(1)==':',"Malformed original typed cell");String x=token.substring(2);char type=token.charAt(0);
            if(type=='S'){require(kind.equals("character"),"Character token/column type mismatch");return new Cell(token,GenomicsemRuntime.unhex(x),false,false);}
            if(type=='B'){require(kind.equals("logical")&&Set.of("TRUE","FALSE").contains(x),"Logical token/column type mismatch");return new Cell(token,x,false,false);}
            require((type=='I'&&kind.equals("integer"))||(type=='D'&&kind.equals("double")),"Numeric token/column type mismatch");GenomicsemData.numeric(x);return new Cell(token,x,false,false);
        }
        double number()throws IOException{require(!missing&&!nonfinite,"Unavailable/nonfinite original inference field");return GenomicsemData.numeric(value);}
        boolean blank(){return missing||value.isEmpty();}
    }
    private interface Output extends AutoCloseable {void line(String text)throws IOException;void close()throws IOException;}
    private static Output output(Path file,boolean inspect)throws IOException {
        if(!inspect){var out=Files.newBufferedWriter(file,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);return new Output(){public void line(String s)throws IOException{out.write(s);out.write('\n');}public void close()throws IOException{out.close();}};}
        var in=new BufferedInputStream(Files.newInputStream(file));return new Output(){public void line(String s)throws IOException{for(byte b:(s+'\n').getBytes(StandardCharsets.UTF_8))require(in.read()==(b&255),"Changed derived inference export");}public void close()throws IOException{try{require(in.read()==-1,"Extra bytes in derived inference export");}finally{in.close();}}};
    }
    private static Cell get(Map<String,Cell> row,String key)throws IOException{Cell c=row.get(key);require(c!=null,"Missing original result column: "+key);return c;}
    private static boolean zero(Cell c)throws IOException{return !c.missing&&!c.nonfinite&&c.number()==0;}
    private static void close(double actual,double expected,String label)throws IOException {require(Double.isFinite(actual)&&Double.isFinite(expected)&&Math.abs(actual-expected)<=1e-14+1e-9*Math.abs(expected),"Original result relationship differs: "+label);}
    private static void probability(double actual,double expected,String label)throws IOException {require(Double.isFinite(actual)&&actual>=0&&actual<=1&&Double.isFinite(expected)&&Math.abs(actual-expected)<=1e-315+1e-8*Math.abs(expected),"Original tail probability differs: "+label);}
    private static void pvalue(Cell p,double z)throws IOException {
        double expected=2*Normal.cumulative(Math.abs(z),0,1,false,false);
        if(p.value.equals("< 5e-300")){require(expected<=5e-300,"Original bounded P label conflicts with estimate/SE");return;}
        probability(p.number(),expected,"two-sided normal P");
    }
    private static boolean fatal(String message){String s=message.toLowerCase(Locale.ROOT);return s.contains("not converg")||s.contains("failed to converge")||s.contains("convergence issues")||s.contains("estimation failed");}
    static Map<String,Object> evaluate(Path root,Map<String,Object> request,GenomicsemData.Inputs inputs,boolean inspect)throws IOException {
        String op=text(request.get("operation"),"operation");boolean association=GenomicsemContracts.association(op),user=GenomicsemContracts.user(op),tw=GenomicsemContracts.twas(op);var options=object(request.get("options"));
        var index=read(root.resolve("worker/table-index.json"));var tables=list(index.get("tables"));require(!tables.isEmpty()&&tables.size()<=1000,"No complete supported original output tables");
        Set<String> requestedPaths=new LinkedHashSet<>();for(Object p:list(read(root.resolve("worker/model-paths.json")).get("requested_paths")))require(requestedPaths.add(text(p,"requested original association path")),"Duplicated requested original path");
        if(association){require(!requestedPaths.isEmpty(),"No original association paths requested");if(user&&!list(options.get("sub")).isEmpty())require(requestedPaths.equals(new LinkedHashSet<>(list(options.get("sub")).stream().map(p->p.toString().replaceAll("\\s+","")).toList())),"Changed explicitly requested subset paths");if(!user)require(requestedPaths.equals(Set.of("F1~"+(tw?"Gene":"SNP"))),"Changed original common-factor path");}else require(requestedPaths.isEmpty(),"Unexpected association paths on a no-SNP model");
        long total=0,inferential=0,noInference=0,nonfinite=0,errors=0,warnings=0,bounded=0,zeroP=0,negativeVariance=0,smoothing=0,unavailableFit=0,qFactors=0;boolean fitSeen=false;Set<String> files=new HashSet<>(),seenUnits=new HashSet<>(),seenPaths=new HashSet<>();
        var conditions=read(root.resolve("worker/conditions.json"));require(number(conditions,"original_function_calls")==1,"No original function call recorded");
        for(Object raw:list(conditions.get("conditions"))){String m=text(object(raw).get("message"),"warning");warnings++;if(fatal(m))errors++;}
        try(var export=output(root.resolve("inference.tsv"),inspect);var human=output(root.resolve("results.tsv"),inspect)){
            export.line("unit_utf8_hex\tpanel_utf8_hex\tlhs_utf8_hex\top_utf8_hex\trhs_utf8_hex\testimate\tSE\toriginal_P_token_utf8_hex\tQ\tQ_df\toriginal_Q_P_token_utf8_hex");
            human.line("unit\tpanel\tlhs\toperator\trhs\testimate\tstandard_error\tp_value\theterogeneity\theterogeneity_df\theterogeneity_p_value");
            for(Object raw:tables){var table=object(raw);String file=text(table.get("file"),"table file"),path=text(table.get("path"),"source path");require(file.matches("worker/tables/table-[0-9]{6}\\.tsv")&&files.add(file),"Unsafe/duplicated original output table");
                var cols=list(table.get("columns"));var names=new ArrayList<String>();var kinds=new ArrayList<String>();for(Object col:cols){var c=object(col);names.add(text(c.get("name"),"column name"));kinds.add(text(c.get("kind"),"column type"));}require(names.size()>0&&new HashSet<>(names).size()==names.size(),"Invalid original column namespace");
                long count=0,lastGroup=0,lastRow=0,groups=number(table,"groups");
                try(var in=GenomicsemData.reader(root.resolve(file))){String header=GenomicsemData.line(in,GenomicsemContracts.cap(request,"maximum_line_characters"));var expected=new ArrayList<>(List.of("group","group_name_hex","row","row_name_hex"));for(String n:names)expected.add(hex(n));require(header!=null&&Arrays.asList(header.split("\t",-1)).equals(expected),"Changed typed original table header");String line;
                    while((line=GenomicsemData.line(in,GenomicsemContracts.cap(request,"maximum_line_characters")))!=null){require(++total<=GenomicsemContracts.cap(request,"maximum_result_rows"),"Original result row capacity exceeded");count++;String[] f=line.split("\t",-1);require(f.length==names.size()+4,"Original result width differs");
                        long group=parseInteger(f[0]),rowNumber=parseInteger(f[2]);require(group>=1&&group<=groups&&((group==lastGroup&&rowNumber==lastRow+1)||(group==lastGroup+1&&rowNumber==1)),"Changed original list/row ordering");lastGroup=group;lastRow=rowNumber;GenomicsemRuntime.unhex(f[1]);GenomicsemRuntime.unhex(f[3]);
                        var row=new LinkedHashMap<String,Cell>();for(int j=0;j<names.size();j++){Cell c=Cell.parse(f[j+4],kinds.get(j));if(c.nonfinite)nonfinite++;row.put(names.get(j),c);}
                        if(path.equals("result/modelfit")){
                            fitSeen=true;require(count==1,"Expected one source model-fit row");double df=get(row,"df").number();require(df>=0&&df==Math.rint(df),"Invalid original model degrees of freedom");
                            for(String name:List.of("chisq","AIC","SRMR","CFI","p_chisq")){Cell c=get(row,name);if(c.missing){require(df==0||(name.equals("CFI")&&user&&Boolean.FALSE.equals(options.get("CFIcalc"))),"Unexpected unavailable original model-fit index: "+name);unavailableFit++;}else c.number();}
                            if(df>0)probability(get(row,"p_chisq").number(),ChiSquare.cumulative(get(row,"chisq").number(),df,false,false),"model chi-square P");
                        }
                        if(path.equals("result/Q_Factor")){double df=get(row,"QFactor_df").number();require(df>=1&&df==Math.rint(df),"Requested factor heterogeneity is unestimable");probability(get(row,"QFactor_p").number(),ChiSquare.cumulative(get(row,"QFactor_chisq").number(),df,false,false),"original Q-factor P");qFactors++;}
                        if(!(association&&path.equals("result"))&&!(!association&&path.equals("result/results")))continue;
                        String lhs=get(row,"lhs").value,operator=get(row,"op").value,rhs=get(row,"rhs").value;GenomicsemData.Unit unit=null;String unitKey="";
                        if(association){
                            String id=get(row,tw?"Gene":"SNP").value,panel=tw?get(row,"Panel").value:null;unitKey=tw?id+"\u0000"+panel:id;unit=inputs.units().get(unitKey);require(unit!=null,"Original result refers to an unknown SNP or Gene/Panel");
                            if(tw)require(get(row,"HSQ").number()==GenomicsemData.numeric(unit.hsq()),"Changed original gene variance metadata");else{
                                require(get(row,"CHR").value.equals(unit.chromosome())&&get(row,"A1").value.equals(unit.allele1())&&get(row,"A2").value.equals(unit.allele2()),"Original chromosome/allele metadata differs");require(get(row,"BP").number()==GenomicsemData.numeric(unit.position())&&get(row,"MAF").number()==GenomicsemData.numeric(unit.maf()),"Original position/MAF metadata differs");
                            }
                            for(String k:List.of("error","fail"))if(row.containsKey(k)&&!zero(row.get(k)))errors++;
                            if(row.containsKey("warning")){Cell w=row.get("warning");if(!w.missing&&!Set.of("","0").contains(w.value)){warnings++;if(fatal(w.value))errors++;}}
                            if(row.containsKey("Z_smooth")&&!get(row,"Z_smooth").missing&&get(row,"Z_smooth").number()!=0)smoothing++;
                        }
                        String estName=association?"est":user?"Unstand_Est":"Unstandardized_Estimate",seName=association?(user?"SE":"se_c"):user?"Unstand_SE":"Unstandardized_SE",pName=association?"Pval_Estimate":"p_value";
                        double estimate=get(row,estName).number();Cell se=get(row,seName),p=get(row,pName);
                        if(operator.equals("~~")&&lhs.equals(rhs)&&estimate<0)negativeVariance++;
                        if(se.blank()&&p.missing){if(association)require(row.containsKey("free")&&zero(get(row,"free")),"Missing inference on a free original parameter");noInference++;continue;}
                        double standardError=se.number();require(standardError>0,"Nonpositive original inference SE");double z=estimate/standardError;require(Double.isFinite(z),"Nonfinite estimate/SE statistic");pvalue(p,z);if(p.value.equals("< 5e-300"))bounded++;else if(p.number()==0)zeroP++;
                        if(association&&row.containsKey("Z_Estimate"))close(get(row,"Z_Estimate").number(),z,"original Z=estimate/SE");
                        boolean effect=association&&operator.equals("~")&&rhs.equals(tw?"Gene":"SNP");if(association&&!effect)continue;
                        inferential++;String q="",df="",qp="";
                        if(effect){
                            require(requestedPaths.contains(lhs+'~'+rhs),"An unrequested original association path was exported");
                            seenUnits.add(unitKey);require(seenPaths.add(unitKey+'\u0000'+lhs+'\u0000'+operator+'\u0000'+rhs),"Duplicated original association path");
                            boolean requested=!user||Boolean.TRUE.equals(options.get("Q_SNP"));if(requested){String qName=user?"Q_SNP":"Q",dfName=user?"Q_SNP_df":"Q_df",qpName=user?"Q_SNP_pval":"Q_pval";Cell qc=get(row,qName),dc=get(row,dfName),pc=get(row,qpName);double d=dc.number();require(d>=1&&d==Math.rint(d),"Requested heterogeneity is unestimable;no complete requested result");double stat=qc.number();probability(pc.number(),ChiSquare.cumulative(stat,d,false,false),"original heterogeneity P");q=qc.value;df=dc.value;qp=hex(pc.token);}
                        }
                        export.line(String.join("\t",hex(unit==null?"":unit.id()),hex(unit==null||unit.panel()==null?"":unit.panel()),hex(lhs),hex(operator),hex(rhs),get(row,estName).value,se.value,hex(p.token),q,df,qp));
                        human.line(String.join("\t",quoted(unit==null?"":unit.id()),quoted(unit==null||unit.panel()==null?"":unit.panel()),quoted(lhs),quoted(operator),quoted(rhs),get(row,estName).value,se.value,quoted(p.value),q.isEmpty()?"NA":q,df.isEmpty()?"NA":df,qp.isEmpty()?"NA":quoted(displayToken(GenomicsemRuntime.unhex(qp)))));
                    }
                }require(count==number(table,"rows")&&lastGroup==groups,"Incomplete original table rows/groups");
            }
        }
        try(var stream=Files.list(root.resolve("worker/tables"))){Set<String> actual=new HashSet<>();for(Path p:stream.toList())actual.add(root.relativize(p).toString().replace('\\','/'));require(actual.equals(files),"Unindexed/omitted original table files");}
        require(total==number(index,"total_rows"),"Changed total source row count");if(association)require(seenUnits.equals(inputs.units().keySet()),"An input SNP or Gene/Panel lacks a complete original association");else require(fitSeen,"Missing original model-fit table");if(op.equals("USERMODEL")&&Boolean.TRUE.equals(options.get("Q_Factor")))require(qFactors>0,"Requested original Q-factor results are absent");
        if(association)for(String unit:inputs.units().keySet())for(String path:requestedPaths){int split=path.lastIndexOf('~');require(split>0&&seenPaths.contains(unit+'\u0000'+path.substring(0,split)+'\u0000'+"~"+'\u0000'+path.substring(split+1)),"An input unit lacks a requested association path: "+path);}
        var data=read(root.resolve("worker/data-audit.json"));boolean inputSmoothed=GenomicsemContracts.finite(data.get("S_min_eigenvalue"))<=0||GenomicsemContracts.finite(data.get("V_min_eigenvalue"))<=0;
        boolean admitted=inferential>0&&nonfinite==0&&errors==0&&(!"FAIL_FIT_WARNINGS".equals(options.get("warning_policy"))||warnings==0)&&(!"FAIL".equals(options.get("smoothing_policy"))||(!inputSmoothed&&smoothing==0));
        return map("status",admitted?"COMPLETE_SCOPED_ORIGINAL_RESULT":"FAIL2_NO_COMPLETE_REQUESTED_RESULT","results_admitted",admitted,"original_rows",total,"original_tables",tables.size(),"inferential_rows",inferential,"source_missing_or_fixed_inference_rows",noInference,"unavailable_source_model_fit_indices",unavailableFit,"Q_factor_tests",qFactors,"nonfinite_numeric_cells",nonfinite,"source_error_or_nonconvergence_fields",errors,"warning_occurrences",warnings,"bounded_source_P_labels",bounded,"zero_source_P_endpoints",zeroP,"negative_variance_estimates",negativeVariance,"nonzero_source_smoothing_diagnostics",smoothing,"S_or_V_required_upstream_smoothing",inputSmoothed,"association_units",seenUnits.size(),"requested_association_paths",new ArrayList<>(requestedPaths),"tail_probability_absolute_tolerance",1e-315,"tail_probability_relative_tolerance",1e-8,"numeric_metadata_exact",true,"original_RDS_interpreted_by_Java",false,"independent_statistical_refit",false,"complete_convergence_certificate",false);
    }
    private static long parseInteger(String s)throws IOException{require(s.matches("[1-9][0-9]*"),"Invalid original group/row ordinal");try{return Long.parseLong(s);}catch(NumberFormatException e){throw new IOException("Original row ordinal overflow",e);}}
    private static String quoted(String s){return s.indexOf('\t')>=0||s.indexOf('\n')>=0||s.indexOf('\r')>=0||s.indexOf('"')>=0?'"'+s.replace("\"","\"\"")+'"':s;}
    private static String displayToken(String token)throws IOException {require(token.length()>=2&&token.charAt(1)==':',"Missing original display token kind");return token.charAt(0)=='S'?GenomicsemRuntime.unhex(token.substring(2)):token.substring(2);}
}
