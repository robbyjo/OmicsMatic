/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jlinalg.sequence.HomerMotifs;

/** Original genomic-library matrix values, without inferred row normalization. */
final class HomerGenomeMatrices {
    private HomerGenomeMatrices() { }
    record Matrix(int ordinal,String consensus,String name,double threshold,Double sourceLogP,
                  List<String> extraHeader,double[][] values,boolean originalWeights,int nonunitRows) {
        Matrix {extraHeader=List.copyOf(extraHeader);values=Arrays.stream(values).map(double[]::clone).toArray(double[][]::new);}
        @Override public double[][] values(){return Arrays.stream(values).map(double[]::clone).toArray(double[][]::new);}
        int width(){return values.length;}
    }
    static List<Matrix> read(Path path,HomerGenomeData.Limits limits,boolean originalWeights)throws Exception {
        var models=new ArrayList<Matrix>();var rows=new ArrayList<double[]>();String[] header=null;long totalRows=0,retained=0;int lineNumber=0;
        try(var in=HomerGenomeData.referenceReader(path)){
            for(String line;(line=HomerGenomeData.next(in,limits.line()))!=null;){lineNumber++;if(line.isBlank())continue;
                if(line.startsWith(">")){
                    if(header!=null)models.add(finish(models.size()+1,header,rows,originalWeights));
                    if(models.size()>=limits.motifs())throw new IOException("Genomic motif family count capacity exceeded");
                    header=line.substring(1).split("\t",-1);if(header.length>1000)throw new IOException("Genomic motif header exceeds original 1000-field parser capacity");if(header.length<3||header[0].isBlank()||header[1].isBlank())throw new IOException("Genomic motif header requires consensus,name,threshold");for(String field:header)if(field.codePoints().anyMatch(Character::isISOControl))throw new IOException("Control in genomic motif metadata");
                    retained=Math.addExact(retained,512+8L*line.length());rows=new ArrayList<>();
                }else{
                    if(header==null)throw new IOException("Genomic motif rows before header");String[] f=line.split("\t",-1);if(f.length!=4)throw new IOException("Genomic matrix requires four A/C/G/T fields at line "+lineNumber);var row=new double[4];double sum=0;
                    for(int i=0;i<4;i++){row[i]=HomerGenomeCli.number(f[i],0,originalWeights?1e300:1);sum+=row[i];}
                    if(!originalWeights&&Math.abs(sum-1)>.002000000001)throw new IOException("Nonunit probability row at line "+lineNumber+";choose explicit --matrix-values original-weights to retain original weights without normalization");
                    if(rows.size()>=4096||++totalRows>limits.motifRows())throw new IOException("Genomic motif width/family-row capacity exceeded");rows.add(row);retained=Math.addExact(retained,256);
                }
                if(retained>Runtime.getRuntime().maxMemory()/8)throw new IOException("Genomic motif metadata/row heap capacity exceeded");
            }
        }
        if(header!=null)models.add(finish(models.size()+1,header,rows,originalWeights));if(models.isEmpty())throw new IOException("Empty genomic motif family");return List.copyOf(models);
    }
    private static Matrix finish(int ordinal,String[] header,List<double[]> rows,boolean originalWeights)throws IOException {
        if(rows.isEmpty())throw new IOException("Empty genomic motif matrix");double threshold=HomerGenomeCli.number(header[2],-Double.MAX_VALUE,Double.MAX_VALUE);Double logP=header.length>3&&!header[3].isEmpty()?HomerGenomeCli.number(header[3],-Double.MAX_VALUE,Double.MAX_VALUE):null;var extra=header.length>4?List.of(Arrays.copyOfRange(header,4,header.length)):List.<String>of();int nonunit=0;
        for(var row:rows){double sum=Arrays.stream(row).sum();if(Math.abs(sum-1)>.002000000001)nonunit++;}
        return new Matrix(ordinal,header[0],header[1],threshold,logP,extra,rows.toArray(double[][]::new),originalWeights,nonunit);
    }
    static final class Scorer {
        private final double[][] scores;
        private final double threshold;
        Scorer(Matrix model){scores=model.values();threshold=model.threshold;for(var row:scores)for(int j=0;j<4;j++)row[j]=StrictMath.log(Math.max(.001,row[j])/.25);}
        boolean present(String dna,HomerMotifs.Strands strands)throws IOException {
            for(int i=0;i<dna.length();i++)if("ACGTN".indexOf(dna.charAt(i))<0)throw new IOException("Genomic score audit requires normalized A/C/G/T/N");
            for(int left=0;left+scores.length<=dna.length();left++){
                boolean valid=true;for(int j=0;j<scores.length;j++)if(dna.charAt(left+j)=='N'){valid=false;break;}if(!valid)continue;
                for(int orientation=0;orientation<2;orientation++){
                    if(orientation==0&&strands==HomerMotifs.Strands.REVERSE||orientation==1&&strands==HomerMotifs.Strands.FORWARD)continue;double score=0;
                    for(int j=0;j<scores.length;j++){int index="ACGT".indexOf(dna.charAt(left+(orientation==0?j:scores.length-j-1)));score+=scores[j][orientation==0?index:3-index];}
                    if(score>=threshold)return true;
                }
            }return false;
        }
    }
}
