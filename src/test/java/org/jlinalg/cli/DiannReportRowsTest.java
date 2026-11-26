/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

class DiannReportRowsTest {
    @TempDir Path tmp;
    static DiannReportRows.Limits limits(long rows,int columns,int chars,int arrays){return new DiannReportRows.Limits(rows,16777216,columns,chars,arrays,67108864);}
    static DiannReportRows.Limits normal(){return limits(100,64,1024,100);}
    Path text(String name,String value)throws IOException{return Files.writeString(tmp.resolve(name),value,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);}
    Path parquet(String name,String sql)throws Exception {
        Path path=tmp.resolve(name);
        try(var c=DriverManager.getConnection("jdbc:duckdb:");var s=c.createStatement()){
            s.execute("SET autoinstall_known_extensions=false");s.execute("SET autoload_known_extensions=false");
            s.execute("COPY ("+sql+") TO '"+path.toString().replace('\\','/').replace("'","''")+"' (FORMAT PARQUET)");
        }
        return path;
    }
    @Test void literalTsvPreservesEmptyQuotesUnicodeAndNonfiniteText()throws Exception {
        Path path=text("original.tsv","ID\tEmpty\tZero\tNonfinite\r\n\"蛋白\"\t\t-0\tNaN\r\n");
        byte[] original=Files.readAllBytes(path);
        try(var rows=DiannReportRows.open(path,"literal-utf8-tsv",null,normal(),()->{})){
            assertEquals(List.of("ID","Empty","Zero","Nonfinite"),rows.columns().stream().map(DiannReportRows.Column::name).toList());
            assertEquals(List.of("\"蛋白\"","","-0","NaN"),rows.next());
            assertNull(rows.next());
        }
        assertArrayEquals(original,Files.readAllBytes(path));
    }
    @Test void tsvReadsAllLineEndingsAndUnterminatedLastRecord()throws Exception {
        Path path=text("endings.tsv","A\tB\rx\t\r\ny\t\nz\tlast");
        try(var rows=DiannReportRows.open(path,"literal-utf8-tsv",null,normal(),()->{})){
            assertEquals(List.of("x",""),rows.next());assertEquals(List.of("y",""),rows.next());assertEquals(List.of("z","last"),rows.next());assertNull(rows.next());
        }
    }
    @Test void invalidTsvIsRejectedWithoutReplacingBytes()throws Exception {
        for(String value:List.of("\uFEFFA\nx\n","A\tA\nx\ty\n","A\nx\ttoo-many\n","A\nx\u0000y\n")){
            Path path=text("bad-"+UUID.randomUUID()+".tsv",value);byte[] before=Files.readAllBytes(path);
            assertThrows(IOException.class,()->{try(var rows=DiannReportRows.open(path,"literal-utf8-tsv",null,normal(),()->{})){while(rows.next()!=null){ }}});
            assertArrayEquals(before,Files.readAllBytes(path));
        }
        Path invalid=Files.write(tmp.resolve("invalid-utf8.tsv"),new byte[]{'A','\n',(byte)0xc3,(byte)0x28,'\n'});
        assertThrows(IOException.class,()->{try(var rows=DiannReportRows.open(invalid,"literal-utf8-tsv",null,normal(),()->{})){while(rows.next()!=null){ }}});
    }
    @Test void tsvCapacityAndCancellationDoNotBecomeCleanEof()throws Exception {
        Path path=text("two.tsv","A\nx\ny\n");
        try(var rows=DiannReportRows.open(path,"literal-utf8-tsv",null,limits(1,1,3,1),()->{})){
            assertEquals(List.of("x"),rows.next());assertThrows(IOException.class,rows::next);
        }
        Path large=text("large.tsv","A\n1234\n");
        assertThrows(IOException.class,()->{try(var rows=DiannReportRows.open(large,"literal-utf8-tsv",null,limits(2,1,3,1),()->{})){rows.next();}});
        assertThrows(IOException.class,()->DiannReportRows.open(path,"literal-utf8-tsv",null,new DiannReportRows.Limits(2,1,1,3,1,67108864),()->{}));
        AtomicBoolean cancelled=new AtomicBoolean();
        try(var rows=DiannReportRows.open(path,"literal-utf8-tsv",null,normal(),()->{if(cancelled.get())throw new CancellationException("controlled cancellation");})){
            cancelled.set(true);assertThrows(CancellationException.class,rows::next);
        }
    }
    @Test void parquetPreservesSqlNullSignedZeroNonfiniteAndArrayCells()throws Exception {
        Path path=parquet("original.parquet","SELECT 9223372036854775807::BIGINT ordinal, '蛋白'::VARCHAR identifier, '-0.0'::FLOAT quantity, NULL::FLOAT missing, 'NaN'::FLOAT nonfinite_nan, 'Infinity'::DOUBLE nonfinite_infinity, [1.0::FLOAT, NULL::FLOAT, 'NaN'::FLOAT] fragments, true::BOOLEAN flag");
        byte[] before=Files.readAllBytes(path);
        try(var rows=DiannReportRows.open(path,"parquet",tmp.resolve("spill"),normal(),()->{})){
            var row=rows.next();assertEquals(8,row.size());assertEquals(Long.MAX_VALUE,row.get(0));assertEquals("蛋白",row.get(1));
            assertInstanceOf(Float.class,row.get(2));assertEquals(0x80000000,Float.floatToRawIntBits((Float)row.get(2)));
            assertNull(row.get(3));assertTrue(Float.isNaN((Float)row.get(4)));assertEquals(Double.POSITIVE_INFINITY,row.get(5));
            var array=(List<?>)row.get(6);assertEquals(3,array.size());assertEquals(1.0f,array.get(0));assertNull(array.get(1));assertTrue(Float.isNaN((Float)array.get(2)));
            assertEquals(true,row.get(7));assertThrows(UnsupportedOperationException.class,()->row.set(0,0L));assertThrows(UnsupportedOperationException.class,()->array.remove(0));assertNull(rows.next());
        }
        assertArrayEquals(before,Files.readAllBytes(path));
    }
    @Test void parquetRejectsIncompletePopulationAndArrayCapacity()throws Exception {
        Path path=parquet("bounded.parquet","SELECT range::BIGINT ordinal, [1::BIGINT,2::BIGINT,3::BIGINT] fragments FROM range(2)");
        try(var rows=DiannReportRows.open(path,"parquet",tmp.resolve("row-spill"),limits(1,2,32,3),()->{})){
            assertNotNull(rows.next());assertThrows(IOException.class,rows::next);
        }
        try(var rows=DiannReportRows.open(path,"parquet",tmp.resolve("array-spill"),limits(2,2,32,2),()->{})){assertThrows(IOException.class,rows::next);}
        assertThrows(IOException.class,()->DiannReportRows.open(path,"parquet",tmp.resolve("column-spill"),limits(2,1,32,3),()->{}));
    }
    @Test void parquetRequiresLiteralPathAndNewSeparateSpill()throws Exception {
        Path path=parquet("literal.parquet","SELECT 1::BIGINT ordinal");
        Path existing=Files.createDirectory(tmp.resolve("existing"));
        assertThrows(IOException.class,()->DiannReportRows.open(path,"parquet",existing,normal(),()->{}));
        assertThrows(IOException.class,()->DiannReportRows.open(path,"parquet",null,normal(),()->{}));
        Path glob=tmp.resolve("glob[1].parquet");Files.copy(path,glob);
        assertThrows(IOException.class,()->DiannReportRows.open(glob,"parquet",tmp.resolve("glob-spill"),normal(),()->{}));
        assertFalse(Files.exists(tmp.resolve("glob-spill")));
        assertThrows(IOException.class,()->DiannReportRows.open(path,"guess",null,normal(),()->{}));
    }
    @Test void closedReadersAndInvalidCapacitiesAreExplicitFailures()throws Exception {
        Path path=text("closed.tsv","A\nx\n");var rows=DiannReportRows.open(path,"literal-utf8-tsv",null,normal(),()->{});rows.close();rows.close();assertThrows(IOException.class,rows::next);
        assertThrows(IllegalArgumentException.class,()->limits(0,1,1,1));assertThrows(IllegalArgumentException.class,()->limits(1,0,1,1));assertThrows(IllegalArgumentException.class,()->limits(1,1,0,1));assertThrows(IllegalArgumentException.class,()->limits(1,1,1,0));
    }
}
