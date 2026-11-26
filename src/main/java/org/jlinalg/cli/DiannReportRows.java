/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Streaming original report cells. Empty text, SQL null and nonfinite numbers remain distinct.
 * This reader does not admit a numerical result, infer a schema or refit DIA-NN.
 */
final class DiannReportRows {
    private DiannReportRows() { }
    record Limits(long rows,long bytes,int columns,int cellChars,int arrayMembers,long parquetMemoryBytes) {
        Limits {
            if(rows<1||rows>1000000000L||bytes<1||bytes>1000000000000L||columns<1||columns>1024
                    ||cellChars<1||cellChars>1048576||arrayMembers<1||arrayMembers>1048576
                    ||parquetMemoryBytes<67108864||parquetMemoryBytes>1099511627776L)
                throw new IllegalArgumentException("Invalid explicit DIA-NN table capacities");
        }
    }
    record Column(String name,String storageType) { }
    interface Rows extends AutoCloseable {
        List<Column> columns();
        /** Null indicates EOF; a SQL-null cell is a null member of the returned list. */
        List<Object> next() throws IOException;
        @Override void close() throws IOException;
    }
    static Rows open(Path supplied,String format,Path newSpill,Limits limits,Runnable check)throws IOException {
        check.run();Path path=supplied.toAbsolutePath().normalize();
        require(path.toRealPath().equals(path)&&Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS),"Require canonical original/captured regular report file");
        require(Files.size(path)<=limits.bytes(),"Complete original report exceeds byte capacity");
        return switch(format) {
            case "literal-utf8-tsv" -> new TextRows(path,limits,check);
            case "parquet" -> new ParquetRows(path,newSpill,limits,check);
            default -> throw new IOException("Declare literal-utf8-tsv or parquet; the report format is not guessed");
        };
    }
    private abstract static class Bounded implements Rows {
        final Limits limits;final Runnable check;List<Column> columns;long rows;boolean closed;
        Bounded(Limits limits,Runnable check){this.limits=limits;this.check=check;}
        @Override public List<Column> columns(){return columns;}
        final void header(List<Column> values)throws IOException {
            require(!values.isEmpty()&&values.size()<=limits.columns(),"Original report column capacity exceeded");
            var seen=new HashSet<String>();
            for(Column col:values)require(!col.name().isEmpty()&&col.name().length()<=limits.cellChars()
                    &&col.name().chars().noneMatch(c->c<32||c==127)&&seen.add(col.name()),"Blank/control/duplicate original report header");
            columns=List.copyOf(values);
        }
        final void before()throws IOException {require(!closed,"Original report reader is closed");check.run();}
        final List<Object> row(List<Object> value)throws IOException {
            require(++rows<=limits.rows(),"Complete original report exceeds row capacity");
            require(value.size()==columns.size(),"Original report field count differs");
            return Collections.unmodifiableList(value);
        }
    }
    private static final class TextRows extends Bounded {
        final Reader input;long chars;
        TextRows(Path path,Limits limits,Runnable check)throws IOException {
            super(limits,check);
            input=new BufferedReader(new InputStreamReader(Files.newInputStream(path),StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
            try {
                String first=line();require(first!=null&&!first.startsWith("\uFEFF"),"Original generated TSV requires a literal header without BOM");
                var cols=new ArrayList<Column>();for(String name:first.split("\t",-1))cols.add(new Column(name,"original-text"));header(cols);
            }catch(IOException|RuntimeException|Error e){try{input.close();}catch(IOException close){e.addSuppressed(close);}throw e;}
        }
        String line()throws IOException {
            var out=new StringBuilder();int field=0,count=1;
            for(int c;(c=input.read())!=-1;){
                if((++chars&4095)==0)check.run();
                require(chars<=limits.bytes(),"Original UTF8 character population exceeds byte bound");
                if(c=='\n')return out.toString();
                if(c=='\r'){
                    input.mark(1);int next=input.read();if(next!='\n'&&next!=-1)input.reset();else if(next=='\n')chars++;
                    return out.toString();
                }
                require(c=='\t'||c>=32&&c!=127,"Unexpected control in original generated TSV");
                if(c=='\t'){field=0;require(++count<=limits.columns(),"Original TSV column capacity exceeded");}
                else require(++field<=limits.cellChars(),"Original TSV cell capacity exceeded");
                out.append((char)c);
            }
            return out.isEmpty()?null:out.toString();
        }
        @Override public List<Object> next()throws IOException {
            before();String value=line();if(value==null)return null;
            return row(new ArrayList<>(Arrays.asList(value.split("\t",-1))));
        }
        @Override public void close()throws IOException {if(!closed){closed=true;input.close();}}
    }
    private static final class ParquetRows extends Bounded {
        Connection connection;PreparedStatement query;ResultSet data;
        ParquetRows(Path path,Path spill,Limits limits,Runnable check)throws IOException {
            super(limits,check);
            // DuckDB file readers interpret glob metacharacters, including those in parent paths.
            require(path.toString().chars().noneMatch(c->c=='*'||c=='?'||c=='['||c==']'),"A literal Parquet path cannot contain glob metacharacters");
            require(spill!=null,"Require a NEW owned Parquet spill directory");
            Path scratch=spill.toAbsolutePath().normalize();
            require(scratch.getParent()!=null&&scratch.getParent().toRealPath().equals(scratch.getParent()),"Require canonical existing spill parent");
            require(!path.startsWith(scratch)&&!scratch.startsWith(path),"Parquet spill and original file must be separate");
            Files.createDirectory(scratch);
            try {
                connection=DriverManager.getConnection("jdbc:duckdb:");
                try(var settings=connection.createStatement()) {
                    settings.execute("SET threads=2");
                    settings.execute("SET memory_limit='"+limits.parquetMemoryBytes()+"B'");
                    settings.execute("SET autoinstall_known_extensions=false");
                    settings.execute("SET autoload_known_extensions=false");
                    settings.execute("SET temp_directory='"+scratch.toString().replace('\\','/').replace("'","''")+"'");
                }
                query=connection.prepareStatement("SELECT * FROM read_parquet(?)");query.setString(1,path.toString().replace('\\','/'));data=query.executeQuery();
                var metadata=data.getMetaData();var cols=new ArrayList<Column>();
                for(int i=1;i<=metadata.getColumnCount();i++)cols.add(new Column(metadata.getColumnName(i),metadata.getColumnTypeName(i)));
                header(cols);
            }catch(SQLException|IOException|RuntimeException|Error e){try{close();}catch(IOException close){e.addSuppressed(close);}throw new IOException("Cannot read the complete original Parquet report; preserve diagnostics and provide a compatible packaged DuckDB runtime where allowed",e);}
        }
        private Object cell(Object value,int depth)throws SQLException,IOException {
            require(depth<=4,"Original Parquet nesting capacity exceeded");
            if(value==null||value instanceof Boolean||value instanceof Number)return value;
            if(value instanceof String text){require(text.length()<=limits.cellChars(),"Original Parquet text capacity exceeded");return text;}
            if(value instanceof java.sql.Array array){try{return cell(array.getArray(),depth+1);}finally{array.free();}}
            if(value.getClass().isArray()) {
                int n=java.lang.reflect.Array.getLength(value);require(n<=limits.arrayMembers(),"Original Parquet array capacity exceeded");
                var result=new ArrayList<Object>(n);for(int i=0;i<n;i++){if((i&4095)==0)check.run();result.add(cell(java.lang.reflect.Array.get(value,i),depth+1));}
                return Collections.unmodifiableList(result);
            }
            throw new IOException("Unqualified original Parquet value type: "+value.getClass().getName());
        }
        @Override public List<Object> next()throws IOException {
            before();try{if(!data.next())return null;var values=new ArrayList<Object>(columns.size());for(int i=1;i<=columns.size();i++)values.add(cell(data.getObject(i),0));return row(values);}
            catch(SQLException e){throw new IOException("Original Parquet read failed; no complete result admitted",e);}
        }
        @Override public void close()throws IOException {
            if(closed)return;closed=true;SQLException failure=null;
            AutoCloseable[] resources={data==null?()->{}:data,query==null?()->{}:query,connection==null?()->{}:connection};
            for(AutoCloseable item:resources)try{item.close();}catch(Exception e){if(failure==null)failure=new SQLException(e);else failure.addSuppressed(e);}
            if(failure!=null)throw new IOException("Could not close original Parquet reader",failure);
        }
    }
    private static void require(boolean condition,String message)throws IOException {if(!condition)throw new IOException(message);}
}
