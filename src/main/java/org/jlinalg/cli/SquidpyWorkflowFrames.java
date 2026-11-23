/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import static org.jlinalg.cli.GprofilerWorkflowContracts.*;
import static org.jlinalg.cli.SquidpyWorkflowFiles.*;

/** Streaming typed-frame/Matrix-Market checks; original missing tokens stay distinct. */
final class SquidpyWorkflowFrames {
    private SquidpyWorkflowFrames() { }
    static final class Frame implements AutoCloseable {
        final Path directory;final Map<String,Object> schema;final BufferedReader reader;final long rows;final int columns;long position;
        Frame(Path directory,Map<String,Object> request)throws IOException {
            this.directory=directory;schema=read(directory.resolve("schema.json"));
            keys(schema,Set.of("schema_version","rows","columns","index_levels","column_levels","index_names","column_names","column_labels","dtypes","categories"));
            integer(schema.get("schema_version"),1,1);rows=integer(schema.get("rows"),0,SquidpyWorkflowContracts.cap(request,"maximum_entries"));
            columns=(int)integer(schema.get("columns"),0,50000);long indexLevels=integer(schema.get("index_levels"),1,16),columnLevels=integer(schema.get("column_levels"),1,16);
            require(list(schema.get("index_names")).size()==indexLevels&&list(schema.get("column_names")).size()==columnLevels,"Typed axis level counts differ");
            for(String key:List.of("index_names","column_names","column_labels"))for(Object value:list(schema.get(key)))scalar(value,0);
            require(list(schema.get("column_labels")).size()==columns&&list(schema.get("dtypes")).size()==columns,"Typed column population differs");
            for(Object value:list(schema.get("dtypes")))require(text(value,"source dtype").length()<=1024,"Invalid dtype description");
            for(var category:object(schema.get("categories")).entrySet()){
                int column;try{column=Integer.parseInt(category.getKey());}catch(NumberFormatException e){throw new IOException("Invalid category column",e);}
                require(column>=0&&column<columns,"Category column outside frame");var c=object(category.getValue());keys(c,Set.of("values","ordered"));require(c.get("ordered") instanceof Boolean,"Category order flag missing");for(Object value:list(c.get("values")))scalar(value,0);
            }
            reader=Files.newBufferedReader(directory.resolve("rows.jsonl"),StandardCharsets.UTF_8);
        }
        Map<String,Object> next()throws IOException {
            String line=line(reader,16777216);
            if(line==null){require(position==rows,"Typed source frame is incomplete");return null;}
            require(position<rows,"Excess typed source rows");var row=object(org.jlinalg.gprofiler.GprofilerJson.read(line.getBytes(StandardCharsets.UTF_8)));
            keys(row,Set.of("position","index","cells"));require(integer(row.get("position"),0,Long.MAX_VALUE)==position++,"Typed row position differs");
            scalar(row.get("index"),0);var cells=list(row.get("cells"));require(cells.size()==columns,"Typed row width differs");for(Object value:cells)scalar(value,0);return row;
        }
        int column(String name)throws IOException {
            int found=-1;var labels=list(schema.get("column_labels"));for(int i=0;i<labels.size();i++){
                var label=object(labels.get(i));if("string".equals(label.get("kind"))&&name.equals(label.get("value"))){require(found<0,"Duplicate scientific result column");found=i;}
            }return found;
        }
        @Override public void close()throws IOException {reader.close();}
    }
    static void scalar(Object value,int depth)throws IOException {
        require(depth<=16,"Typed scalar nesting exceeded");var token=object(value);String kind=text(token.get("kind"),"scalar kind");
        if(Set.of("None","pandas.NA","pandas.NaT").contains(kind)){require(token.keySet().equals(Set.of("kind")),"Missing token has unexpected content");return;}
        require(token.keySet().equals(Set.of("kind","value")),"Typed scalar has unknown/missing fields");
        switch(kind){
            case "string"->text(token.get("value"),"literal source text");
            case "bool"->require(token.get("value") instanceof Boolean,"Invalid source Boolean");
            case "integer"->{String raw=text(token.get("value"),"integer token");require(raw.matches("-?(0|[1-9][0-9]*)")&&raw.length()<=128,"Invalid exact source integer");}
            case "float"->floating(token);
            case "tuple"->{for(Object part:list(token.get("value")))scalar(part,depth+1);}
            default->throw new IOException("Unsupported source scalar tag: "+kind);
        }
    }
    static double floating(Object scalar)throws IOException {
        var token=object(scalar);String kind=text(token.get("kind"),"numeric kind");
        if(Set.of("None","pandas.NA","pandas.NaT").contains(kind))return Double.NaN;
        require(Set.of("float","integer").contains(kind),"Require a typed scientific number");String raw=text(token.get("value"),"source number");
        if(raw.equals("NaN"))return Double.NaN;if(raw.equals("Inf"))return Double.POSITIVE_INFINITY;if(raw.equals("-Inf"))return Double.NEGATIVE_INFINITY;
        require(raw.length()<=128&&raw.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?"),"Invalid source numeric text");
        try{double value=Double.parseDouble(raw);require(Double.isFinite(value),"Unlabelled numeric overflow");return value;}catch(NumberFormatException error){throw new IOException("Invalid source number",error);}
    }
    static String line(BufferedReader reader,int maximum)throws IOException {
        var out=new StringBuilder();int value;
        while((value=reader.read())!=-1){if(value=='\n')return out.toString();require(out.length()<maximum,"Export line capacity exceeded");require(value!='\r',"Typed exports must retain canonical LF separators");require(value>=32||value=='\t',"Unexpected control byte in original typed/matrix export; no trimming or repair is admitted");out.append((char)value);}
        return out.isEmpty()?null:out.toString();
    }
    static Map<String,Object> matrix(Path directory,Map<String,Object> declaration,Map<String,Object> request)throws IOException {
        String name=relative(text(declaration.get("file"),"matrix export"));require(Path.of(name).getNameCount()==1,"Matrix export must be a direct member");
        var shape=list(declaration.get("shape"));require(shape.size()==2,"Matrix shape must be two-dimensional");long nr=integer(shape.get(0),0,1000000),nc=integer(shape.get(1),0,1000000);
        long expected=number(declaration,"stored_entries"),count=0,nonfinite=0,negative=0;
        require(expected<=Math.max(SquidpyWorkflowContracts.cap(request,"maximum_entries"),SquidpyWorkflowContracts.cap(request,"maximum_dense_entries")),"Exported matrix entry capacity exceeded");
        try(var reader=Files.newBufferedReader(directory.resolve(name),StandardCharsets.US_ASCII)){
            String header=line(reader,4096);require(header!=null,"Missing Matrix Market header");String[] h=header.split(" ");
            require(h.length==5&&h[0].equals("%%MatrixMarket")&&h[1].equals("matrix")&&Set.of("coordinate","array").contains(h[2])&&Set.of("real","integer").contains(h[3])&&h[4].equals("general"),"Unsupported original matrix export header");
            String dimensions;do{dimensions=line(reader,4096);}while(dimensions!=null&&dimensions.startsWith("%"));require(dimensions!=null,"Missing source matrix dimensions");
            String[] d=dimensions.trim().split("\\s+");require(d.length==(h[2].equals("coordinate")?3:2),"Invalid source matrix dimensions");
            require(Long.parseLong(d[0])==nr&&Long.parseLong(d[1])==nc,"Source matrix dimension declaration differs");
            long records=h[2].equals("coordinate")?Long.parseLong(d[2]):Math.multiplyExact(nr,nc);require(records==expected,"Source matrix population declaration differs");
            String row;while((row=line(reader,4096))!=null){require(++count<=records,"Excess source matrix entries");String[] values=row.trim().split("\\s+");require(values.length==(h[2].equals("coordinate")?3:1),"Invalid original matrix entry");
                if(values.length==3){long i=Long.parseLong(values[0]),j=Long.parseLong(values[1]);require(i>=1&&i<=nr&&j>=1&&j<=nc,"Original coordinate outside declared dimensions");}
                String raw=values[values.length-1];double value=switch(raw.toLowerCase(Locale.ROOT)){case "nan"->Double.NaN;case "inf","+inf","infinity","+infinity"->Double.POSITIVE_INFINITY;case "-inf","-infinity"->Double.NEGATIVE_INFINITY;default->Double.parseDouble(raw);};
                if(!Double.isFinite(value))nonfinite++;if(value<0)negative++;
                if(h[3].equals("integer"))require(raw.matches("-?(0|[1-9][0-9]*)"),"Integer matrix was not retained as exact integer text");
            }require(count==records,"Incomplete source matrix entries");
        }catch(NumberFormatException|ArithmeticException error){throw new IOException("Invalid original matrix dimensions/value",error);}
        return map("rows",nr,"columns",nc,"stored_entries",count,"nonfinite_entries",nonfinite,"negative_entries",negative);
    }
}
