/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;

/** Original column-major matrix values and exact named TSV projections. */
final class GenomicsemPrepMatrices {
    private GenomicsemPrepMatrices() { }
    record Matrix(int rows,int columns,double[] data,List<String> rowNames,List<String> columnNames,long unavailable) {
        double get(int row,int column){return data[row+rows*column];}
        double maximumAsymmetry() {if(rows!=columns||unavailable!=0)return Double.NaN;double largest=0;for(int i=0;i<rows;i++)for(int j=0;j<i;j++)largest=Math.max(largest,Math.abs(get(i,j)-get(j,i)));return largest;}
        long nonpositiveDiagonal(){long count=0;for(int i=0;i<Math.min(rows,columns);i++)if(!(get(i,i)>0))count++;return count;}
    }
    static Matrix matrix(GenomicsemPrepObject.Tree tree,GenomicsemPrepObject.Node node,boolean allowUnavailable)throws IOException {
        require(node.kind.equals("atomic")&&Set.of("double","integer").contains(node.type),"Expected an original numeric matrix");long[] dimensions=node.attribute("dim").integers(tree);require(dimensions.length==2&&dimensions[0]>0&&dimensions[1]>0&&dimensions[0]*dimensions[1]==node.length&&node.length<=Integer.MAX_VALUE,"Invalid original matrix dimensions");
        int rows=Math.toIntExact(dimensions[0]),columns=Math.toIntExact(dimensions[1]);List<String> rnames=List.of(),cnames=List.of();var dn=node.attributes.get("dimnames");
        if(dn!=null){require(dn.kind.equals("list")&&dn.children.size()==2,"Changed original dimension-name structure");if(!dn.children.get(0).kind.equals("null"))rnames=dn.children.get(0).strings(tree);if(!dn.children.get(1).kind.equals("null"))cnames=dn.children.get(1).strings(tree);require((rnames.isEmpty()||rnames.size()==rows)&&(cnames.isEmpty()||cnames.size()==columns),"Changed original matrix labels");}
        double[] values=new double[(int)node.length];long unavailable=0;
        try(var in=tree.cursor(node)){for(int i=0;i<values.length;i++){var cell=in.next();require(cell!=null,"Incomplete original matrix");if(cell.missing()||cell.nonfinite()){require(allowUnavailable,"Unavailable/nonfinite original matrix field");unavailable++;values[i]=cell.token().equals("P")?Double.POSITIVE_INFINITY:cell.token().equals("M")?Double.NEGATIVE_INFINITY:Double.NaN;}else values[i]=cell.number();}}
        return new Matrix(rows,columns,values,rnames,cnames,unavailable);
    }
    static void square(Matrix matrix,int n)throws IOException {require(matrix.rows==n&&matrix.columns==n,"Original matrix has the wrong trait/sampling dimensions");}
    static void labels(Matrix matrix,List<String>labels)throws IOException {require(matrix.columnNames.equals(labels)&&(matrix.rowNames.isEmpty()||matrix.rowNames.equals(labels)),"Original trait labels/order differs from the request");}
    static void projection(Path file,Matrix original,List<String>labels,Map<String,Object>request)throws IOException {
        square(original,labels.size());require(original.unavailable==0,"A usable matrix projection requires finite original cells");
        try(var in=new GenomicsemPrepTables.Text(file,request,true)){
            var header=new ArrayList<>(List.of("id"));header.addAll(labels);require(in.header.equals(header),"Changed derived matrix headers/trait/vech order");int row=0;List<String> fields;
            while((fields=in.next())!=null){require(row<labels.size()&&fields.get(0).equals(labels.get(row)),"Changed derived matrix row labels/order");for(int j=0;j<labels.size();j++){double actual=GenomicsemData.numeric(fields.get(j+1));require(Double.doubleToRawLongBits(actual)==Double.doubleToRawLongBits(original.get(row,j)),"Derived matrix changed an original numeric value");}row++;}require(row==labels.size(),"Incomplete derived matrix product");
        }
    }
    static void close(double actual,double expected,String label)throws IOException {require(Double.isFinite(actual)&&Double.isFinite(expected)&&Math.abs(actual-expected)<=1e-14+1e-10*Math.abs(expected),"Original/derived scalar relationship differs: "+label);}
    static Map<String,Object> spectrum(Matrix matrix)throws IOException {
        require(matrix.rows==matrix.columns,"A spectral diagnostic requires a square matrix");
        if(matrix.unavailable!=0)return map("status","UNAVAILABLE_SOURCE_CELLS","dimension",matrix.rows,"minimum_eigenvalue",null,"original_cells_changed",false);
        double scale=0;for(double d:matrix.data)scale=Math.max(scale,Math.abs(d));double asymmetry=matrix.maximumAsymmetry();
        if(asymmetry>1e-12*Math.max(scale,Double.MIN_NORMAL))return map("status","ASYMMETRIC_SOURCE_MATRIX","dimension",matrix.rows,"maximum_asymmetry",asymmetry,"minimum_eigenvalue",null,"original_cells_changed",false);
        if(matrix.rows>256)return map("status","NATIVE_DIAGNOSTIC_CAPACITY_NOT_CHECKED","dimension",matrix.rows,"minimum_eigenvalue",null,"original_cells_changed",false);
        // Explicit diagnostic copy from the lower triangle. Neither the source
        // object nor its downstream product is averaged, clipped or overwritten.
        double[] symmetric=new double[matrix.data.length];for(int i=0;i<matrix.rows;i++)for(int j=0;j<matrix.rows;j++)symmetric[i*matrix.rows+j]=matrix.get(Math.max(i,j),Math.min(i,j));
        double minimum;
        try{minimum=new jdistlib.accelerator.CpuComputeBackend().dsyev(symmetric,matrix.rows).eigenvalues()[0];}
        catch(RuntimeException e){throw new IOException("Independent Java FP64 eigen diagnostic failed;no fallback or convergence repair",e);}
        require(Double.isFinite(minimum),"Nonfinite independent eigen diagnostic");double margin=1e-12*Math.max(scale,Double.MIN_NORMAL)*matrix.rows;
        return map("status",minimum>margin?"POSITIVE_DEFINITE_DIAGNOSTIC":minimum< -margin?"NONPOSITIVE_DEFINITE_DIAGNOSTIC":"NUMERICALLY_MARGINAL_DIAGNOSTIC","dimension",matrix.rows,"minimum_eigenvalue",minimum,"maximum_asymmetry",asymmetry,"marginal_tolerance",margin,"diagnostic_triangle","lower","backend","Java_CPU_FP64","original_cells_changed",false,"complete_convergence_or_biological_certificate",false);
    }
}
