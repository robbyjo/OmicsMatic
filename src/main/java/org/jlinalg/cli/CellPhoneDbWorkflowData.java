/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.*;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Primitive data-only decoding; no Python object construction or pickle support. */
final class CellPhoneDbWorkflowData {
    private CellPhoneDbWorkflowData() { }
    record Vector(String dtype,List<Integer> shape,ByteBuffer bytes,List<?> objects,int size) {
        Object value(int index)throws IOException {
            require(index>=0&&index<size,"Original vector index outside its complete shape");if(bytes==null)return objects.get(index);char kind=dtype.charAt(1);int width=Integer.parseInt(dtype.substring(2));int at=Math.multiplyExact(index,width);
            return switch(kind){
                case 'b' -> {int value=Byte.toUnsignedInt(bytes.get(at));require(width==1&&(value==0||value==1),"Invalid original boolean representation");yield value==1;}
                case 'i' -> switch(width){case 1->(long)bytes.get(at);case 2->(long)bytes.getShort(at);case 4->(long)bytes.getInt(at);case 8->bytes.getLong(at);default->throw new IOException("Unsupported signed dtype");};
                case 'u' -> switch(width){case 1->(long)Byte.toUnsignedInt(bytes.get(at));case 2->(long)Short.toUnsignedInt(bytes.getShort(at));case 4->Integer.toUnsignedLong(bytes.getInt(at));case 8->new NextflowParameters.NumberToken(new BigInteger(Long.toUnsignedString(bytes.getLong(at))).toString());default->throw new IOException("Unsupported unsigned dtype");};
                case 'f' -> {require(width==4||width==8,"Unsupported original floating dtype");yield width==4?(double)bytes.getFloat(at):bytes.getDouble(at);}
                default -> throw new IOException("Unsupported original primitive dtype");
            };
        }
        double number(int index)throws IOException {return numeric(value(index));}
    }
    record Frame(List<?> index,List<?> columns,List<String> dtypes,List<Vector> data) {
        int rows(){return index.size();}
        int column(String name)throws IOException {int at=columns.indexOf(name);require(at>=0&&columns.lastIndexOf(name)==at,"Missing/ambiguous original column: "+name);return at;}
        Object value(int row,int column)throws IOException{return data.get(column).value(row);}
        Object value(int row,String column)throws IOException{return value(row,column(column));}
        double number(int row,String column)throws IOException{return numeric(value(row,column));}
        List<String> rowIds()throws IOException {var result=new ArrayList<String>();for(Object value:index)result.add(identity(value));return List.copyOf(result);}
        List<String> columnLabels()throws IOException {var result=new ArrayList<String>();for(Object value:columns)result.add(text(value));return List.copyOf(result);}
        double[][] matrix()throws IOException {double[][] values=new double[rows()][columns.size()];for(int i=0;i<rows();i++)for(int j=0;j<columns.size();j++)values[i][j]=numeric(value(i,j));return values;}
    }
    static Vector vector(Object raw,long maximumCells)throws IOException {
        var value=object(raw);String kind=text(value.get("kind")),dtype=text(value.get("dtype"));var shape=new ArrayList<Integer>();long cells=1;for(Object n:list(value.get("shape"))){int length=Math.toIntExact(integer(n,0,Integer.MAX_VALUE));shape.add(length);cells=Math.multiplyExact(cells,length);require(cells<=maximumCells&&cells<=Integer.MAX_VALUE,"Complete original vector capacity exceeded");}require(!shape.isEmpty(),"Scalar ndarray is outside this typed vector profile");
        if(kind.equals("numeric-array")){require(dtype.matches("[<|][biuf](?:1|2|4|8)"),"Unsupported primitive dtype/endianness");int width=Integer.parseInt(dtype.substring(2));byte[] bytes;try{bytes=Base64.getDecoder().decode(text(value.get("base64")));}catch(IllegalArgumentException bad){throw new IOException("Invalid data-only base64 vector",bad);}require(bytes.length==Math.multiplyExact(cells,width),"Complete binary vector byte length differs from dtype/shape");return new Vector(dtype,List.copyOf(shape),ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN),List.of(),Math.toIntExact(cells));}
        require(kind.equals("object-array")&&shape.size()==1,"Unsupported executable/nested object representation; no reconstruction");var values=list(value.get("values"));require(values.size()==cells,"Complete original object vector length differs");return new Vector(dtype,List.copyOf(shape),null,Collections.unmodifiableList(new ArrayList<>(values)),Math.toIntExact(cells));
    }
    static Frame frame(Object raw,long maximumCells)throws IOException {
        var value=object(raw);require(text(value.get("kind")).equals("dataframe"),"Original value is not a dataframe instance; class/empty states remain distinct");var index=list(value.get("index"));var columns=list(value.get("columns"));require((long)index.size()*columns.size()<=maximumCells,"Complete original dataframe capacity exceeded");var dtypes=new ArrayList<String>();for(Object dtype:list(value.get("dtypes")))dtypes.add(text(dtype));var data=new ArrayList<Vector>();for(Object column:list(value.get("column_data"))){var vector=vector(column,maximumCells);require(vector.shape().size()==1&&vector.size()==index.size(),"Original dataframe column/row shape mismatch");data.add(vector);}require(columns.size()==dtypes.size()&&columns.size()==data.size(),"Complete dataframe dtype/column family mismatch");return new Frame(List.copyOf(index),List.copyOf(columns),List.copyOf(dtypes),List.copyOf(data));
    }
    static boolean missing(Object raw)throws IOException {if(raw==null)return true;if(raw instanceof Double number)return Double.isNaN(number);if(raw instanceof Map<?,?>){var value=object(raw);return "pandas-NA".equals(value.get("kind"))||"nonfinite-scalar".equals(value.get("kind"))&&"NaN".equals(value.get("value"));}return false;}
    static String optional(Object raw)throws IOException{return missing(raw)?null:text(raw);}
    static String identity(Object raw)throws IOException {if(raw instanceof String value)return value;return Long.toString(integer(raw,Long.MIN_VALUE,Long.MAX_VALUE));}
    static double numeric(Object raw)throws IOException {
        if(raw instanceof Double number)return number;if(raw instanceof Float number)return number.doubleValue();if(raw instanceof Number number)return number.doubleValue();if(raw instanceof NextflowParameters.NumberToken token){try{return Double.parseDouble(token.text());}catch(NumberFormatException bad){throw new IOException("Invalid original numeric token",bad);}}
        if(raw instanceof Map<?,?>){var value=object(raw);require(text(value.get("kind")).equals("nonfinite-scalar"),"Object cannot be coerced to original numeric value");return switch(text(value.get("value"))){case "NaN"->Double.NaN;case "+Inf"->Double.POSITIVE_INFINITY;case "-Inf"->Double.NEGATIVE_INFINITY;default->throw new IOException("Unknown explicit floating state");};}throw new IOException("Original value is not numeric; no coercion or repair");
    }
}
