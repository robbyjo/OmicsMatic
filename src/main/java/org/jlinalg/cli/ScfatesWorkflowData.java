/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.IOException;
import java.nio.*;
import java.nio.file.Path;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Primitive content-addressed arrays and categorical frames; never reconstruct Python objects. */
final class ScfatesWorkflowData {
    private ScfatesWorkflowData() { }
    record Frame(CellPhoneDbWorkflowData.Frame values,List<Map<String,Object>> dtypes) {
        int rows(){return values.rows();}
        Object value(int row,String column)throws IOException{return values.value(row,column);}
        double number(int row,String column)throws IOException{return values.number(row,column);}
        List<String> ids()throws IOException{return values.rowIds();}
        double[][] matrix()throws IOException{return values.matrix();}
    }
    static Map<String,Object> read(Path path)throws IOException{return PalantirWorkflowRuntime.read(path,16777216);}
    static double scalar(Object value)throws IOException{return CellPhoneDbWorkflowData.numeric(value);}
    static CellPhoneDbWorkflowData.Vector vector(Path home,Object raw,long maximum)throws IOException {
        var value=object(raw);if(!"binary-numeric-array".equals(value.get("kind")))return CellPhoneDbWorkflowData.vector(raw,maximum);String dtype=text(value.get("dtype"));require(dtype.matches("[<|][biuf](?:1|2|4|8)"),"Unsupported primitive dtype/endianness");int width=Integer.parseInt(dtype.substring(2));var shape=new ArrayList<Integer>();long count=1;for(Object n:list(value.get("shape"))){int size=Math.toIntExact(integer(n,0,10000000));shape.add(size);count=Math.multiplyExact(count,size);require(count<=maximum&&count<=Integer.MAX_VALUE,"Complete original primitive array capacity exceeded");}require(!shape.isEmpty()&&shape.size()<=2,"Rank-one/two original arrays required");String member=NextflowFiles.member(text(value.get("path")));require(member.matches("objects/[0-9a-f]{64}\\.bin"),"Bound data-only object member required");long limit=Math.multiplyExact(maximum,8);int byteLimit=Math.toIntExact(Math.min(1000000000L,limit));var expected=NextflowFiles.fingerprint(value,byteLimit);require(member.equals("objects/"+expected.sha256()+".bin"),"Array object name differs from bound content hash");byte[] bytes=NextflowFiles.bytes(home.resolve(member),byteLimit);require(bytes.length==Math.multiplyExact(count,width)&&expected.equals(new NextflowFiles.Pin(bytes.length,NextflowFiles.sha(bytes))),"Complete array bytes, shape or source hash differs");return new CellPhoneDbWorkflowData.Vector(dtype,List.copyOf(shape),ByteBuffer.wrap(bytes).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN),List.of(),Math.toIntExact(count));
    }
    static Frame frame(Path home,Object raw,long maximum)throws IOException {
        var value=object(raw);require(text(value.get("kind")).equals("dataframe"),"Complete original dataframe required");var index=list(value.get("index"));var columns=list(value.get("columns"));require((long)index.size()*columns.size()<=maximum,"Complete frame capacity exceeded");var names=new ArrayList<String>();var descriptors=new ArrayList<Map<String,Object>>();var arrays=new ArrayList<CellPhoneDbWorkflowData.Vector>();for(Object d:list(value.get("dtypes"))){var dtype=object(d);String name=text(dtype.get("name"));names.add(name);if(name.equals("category")){list(dtype.get("categories"));bool(dtype.get("ordered"));}descriptors.add(Collections.unmodifiableMap(dtype));}for(Object c:list(value.get("column_data"))){var v=vector(home,c,maximum);require(v.shape().size()==1&&v.size()==index.size(),"Complete frame column shape differs");arrays.add(v);}require(columns.size()==names.size()&&columns.size()==arrays.size(),"Complete frame column/dtype family differs");return new Frame(new CellPhoneDbWorkflowData.Frame(List.copyOf(index),List.copyOf(columns),List.copyOf(names),List.copyOf(arrays)),List.copyOf(descriptors));
    }
    static double[][] matrix(Path home,Object raw,long maximum)throws IOException {
        var value=object(raw);if("dataframe".equals(value.get("kind")))return frame(home,raw,maximum).matrix();var v=vector(home,raw,maximum);require(v.shape().size()==2,"Complete rank-two matrix required");int n=v.shape().get(0),p=v.shape().get(1);double[][] result=new double[n][p];for(int i=0;i<n;i++)for(int j=0;j<p;j++)result[i][j]=v.number(i*p+j);return result;
    }
    static int[][] adjacency(Path home,Object raw,long maximum)throws IOException {var v=vector(home,raw,maximum);require(v.shape().size()==2&&v.shape().get(0).equals(v.shape().get(1)),"Complete square adjacency required");int n=v.shape().get(0);int[][] result=new int[n][n];for(int i=0;i<n;i++)for(int j=0;j<n;j++){double d=v.number(i*n+j);require(d==0||d==1,"Original adjacency is not binary");result[i][j]=(int)d;}for(int i=0;i<n;i++)for(int j=0;j<n;j++)require(result[i][j]==result[j][i]&&(i!=j||result[i][j]==0),"Require symmetric original adjacency with zero diagonal");return result;}
    static double[][] transpose(double[][] values){int p=values.length==0?0:values[0].length;double[][] result=new double[p][values.length];for(int i=0;i<values.length;i++)for(int j=0;j<p;j++)result[j][i]=values[i][j];return result;}
}
