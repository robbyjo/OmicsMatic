/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Owned scalar-audit admission and bounded, typed TSV export; no original node repair. */
final class FavorAnnotationAudit {
    static final String SCHEMA="favor-annotation-scalars-v1";
    private static final String NULL_ATTRIBUTES="71557d1c8bac9bbe3cbec8d00bb223a2f372279827064095447e569fbf5a760a";
    enum Kind { UTF8_LENGTH_INT32_LE_NA_NEGATIVE1, LOGICAL_INT32_LE, INT32_LE, FLOAT64_LE_TAGGED }
    record Limits(int metadataBytes,int columns,int rows,long cells,int scalarBytes,long canonicalBytes,
            int blockCells,long bufferBytes,long outputBytes) {
        Limits {
            if(metadataBytes<1||columns<2||columns>4096||rows<1||cells<1||scalarBytes<1||scalarBytes>16777216||canonicalBytes<1||canonicalBytes>4_503_599_627_370_496L||blockCells<1||bufferBytes<1024||outputBytes<1)throw new IllegalArgumentException("Invalid complete FAVOR annotation capacity");
        }
    }
    record Captured(Path path,long bytes,String sha256) { }
    record Column(int index,String name,Kind kind,List<String> sourceClass,String sourceAttributesJson,
            String sourceAttributesSha256,boolean attributesLost,long sourceNa,long sourceNan,long storedNa,
            long storedNan,long convertedCharacterNa,Captured source,Captured expected,Captured stored) {
        Column {sourceClass=List.copyOf(sourceClass);}
    }
    record Prepared(Captured audit,int rows,List<Column> columns,long canonicalBytes,long originalFrameRows,
            String csvSha256,String gdsSha256,Map<String,String> runtime,List<String> warnings) {
        Prepared {columns=List.copyOf(columns);runtime=Map.copyOf(runtime);warnings=List.copyOf(warnings);}
        void verify(Runnable check)throws IOException {
            verifyFile(audit,check);for(var c:columns)for(var p:List.of(c.source(),c.expected(),c.stored()))verifyFile(p,check);
        }
    }
    record Export(int rows,int columns,long values,long convertedCharacterNa,Path valuesTsv,Path statesTsv,
            String valuesSha256,String statesSha256,long bytes,List<String> warnings,Path manifest,Path readme) {
        Export {warnings=List.copyOf(warnings);}
    }
    private record Cell(String state,String text,long bits) { }
    private FavorAnnotationAudit() { }

    static Prepared read(Path path,String csvHash,String gdsHash,List<String> headers,int rows,Limits limits,Runnable check)throws IOException {
        check.run();if(rows<1||rows>limits.rows()||headers.size()<2||headers.size()>limits.columns()||(long)rows*headers.size()>limits.cells()||!headers.get(0).equals("VarInfo")||!headers.contains("variant_vcf")||new HashSet<>(headers).size()!=headers.size())throw new IOException("Invalid complete native annotation population/header");
        for(String name:headers)if(name.isBlank()||name.length()>1024||name.codePoints().anyMatch(Character::isISOControl))throw new IOException("Invalid exact annotation column name");
        path=path.toAbsolutePath().normalize();Path root=path.getParent();byte[] raw=AdmixtureInputs.raw(path,limits.metadataBytes());Map<?,?> m=AdmixtureInputs.decode(raw);
        keys(m,Set.of("schema","rows","columns","canonical_bytes","joined_csv_sha256","gds_sha256","original_frame_rows","warnings","runtime"));
        if(!SCHEMA.equals(text(m,"schema"))||whole(m,"rows",1,limits.rows())!=rows||!hash(m,"joined_csv_sha256").equals(csvHash)||!hash(m,"gds_sha256").equals(gdsHash))throw new IOException("Annotation audit differs from protected CSV/GDS identities or complete native row population");
        if(!(m.get("columns") instanceof List<?> list)||list.size()!=headers.size())throw new IOException("Incomplete annotation column population");
        var columns=new ArrayList<Column>();var warnings=new ArrayList<String>();long total=0;
        for(int i=0;i<list.size();i++) {
            check.run();Map<?,?> c=object(list.get(i));keys(c,Set.of("index","name","kind","values","source_type","source_class","source_attributes","source_attributes_sha256","stored_type","stored_class","stored_attributes_sha256","source_attributes_lost","source_na","source_nan","stored_na","stored_nan","converted_character_na","source","expected","stored"));
            String name=text(c,"name");if(whole(c,"index",1,headers.size())!=i+1||!name.equals(headers.get(i))||whole(c,"values",1,rows)!=rows)throw new IOException("Annotation column identity/order/population differs");
            Kind kind;try{kind=Kind.valueOf(text(c,"kind"));}catch(IllegalArgumentException invalid){throw new IOException("Unknown canonical annotation scalar kind",invalid);}
            String type=switch(kind){case UTF8_LENGTH_INT32_LE_NA_NEGATIVE1->"character";case LOGICAL_INT32_LE->"logical";case INT32_LE->"integer";case FLOAT64_LE_TAGGED->"double";};
            String base=type.equals("double")?"numeric":type;List<String> sourceClass=strings(c.get("source_class"));
            boolean special=sourceClass.equals(List.of("Date"))||sourceClass.equals(List.of("POSIXct","POSIXt"))||sourceClass.equals(List.of("hms","difftime"));
            if(!text(c,"source_type").equals(type)||!text(c,"stored_type").equals(type)||!strings(c.get("stored_class")).equals(List.of(base))||special&&kind!=Kind.FLOAT64_LE_TAGGED||!special&&!sourceClass.equals(List.of(base)))throw new IOException("Unsupported or changed original scalar type/class: "+name);
            boolean lost=flag(c,"source_attributes_lost");Object attributes=c.get("source_attributes");String attributesHash=hash(c,"source_attributes_sha256");
            if(lost!=special||!hash(c,"stored_attributes_sha256").equals(NULL_ATTRIBUTES))throw new IOException("Unverified original scalar attribute projection: "+name);
            if(!special){if(attributes!=null||!attributesHash.equals(NULL_ATTRIBUTES))throw new IOException("Unexpected original scalar attributes: "+name);}
            else {
                Map<?,?> a=object(attributes);Set<String> expected=sourceClass.equals(List.of("Date"))?Set.of("class"):sourceClass.get(0).equals("POSIXct")?Set.of("class","tzone"):Set.of("class","units");keys(a,expected);
                if(!attributeStrings(a.get("class")).equals(sourceClass)||a.containsKey("tzone")&&!attributeStrings(a.get("tzone")).equals(List.of("UTC"))||a.containsKey("units")&&!attributeStrings(a.get("units")).equals(List.of("secs")))throw new IOException("Unsupported original date/time unit or timezone provenance: "+name);
                warnings.add("SOURCE ATTRIBUTE LOSS: "+name+" "+sourceClass+" is stored as numeric. Required action: use retained original class/unit/timezone provenance;exported numbers do not restore those classes.");
            }
            long sna=whole(c,"source_na",0,rows),snn=whole(c,"source_nan",0,rows),tna=whole(c,"stored_na",0,rows),tnn=whole(c,"stored_nan",0,rows),converted=whole(c,"converted_character_na",0,rows);
            boolean character=kind==Kind.UTF8_LENGTH_INT32_LE_NA_NEGATIVE1;
            if(sna+snn>rows||tna+tnn>rows||snn!=tnn||kind!=Kind.FLOAT64_LE_TAGGED&&snn!=0||character&&(tna!=0||converted!=sna)||!character&&(converted!=0||sna!=tna))throw new IOException("Invalid complete scalar missingness projection: "+name);
            var captured=new ArrayList<Captured>();
            for(String role:List.of("source","expected","stored")){
                Captured file=capture(root,object(c.get(role)),role,i+1,limits,check);
                if(file.bytes()>limits.canonicalBytes()-total)throw new IOException("Complete canonical annotation byte capacity exceeded");total+=file.bytes();captured.add(file);
            }
            if(captured.get(1).bytes()!=captured.get(2).bytes()||!captured.get(1).sha256().equals(captured.get(2).sha256()))throw new IOException("Expected and stored complete scalar hashes differ: "+name);
            columns.add(new Column(i+1,name,kind,sourceClass,RegenieRuntime.json(attributes),attributesHash,lost,sna,snn,tna,tnn,converted,captured.get(0),captured.get(1),captured.get(2)));
        }
        if(whole(m,"canonical_bytes",0,limits.canonicalBytes())!=total)throw new IOException("Incomplete canonical annotation byte ledger");
        Map<?,?> r=object(m.get("runtime"));keys(r,Set.of("R","readr","gdsfmt","SeqArray","digest"));var runtime=new LinkedHashMap<String,String>();for(String key:List.of("R","readr","gdsfmt","SeqArray","digest"))runtime.put(key,text(r,key));
        List<String> observedWarnings=strings(m.get("warnings"));if(observedWarnings.size()!=warnings.size())throw new IOException("Source attribute-loss warnings are incomplete");
        long frame=whole(m,"original_frame_rows",0,rows);if(frame!=rows)warnings.add("ORIGINAL FRAME METADATA: reports "+frame+" rows while every audited scalar column has "+rows+". Required action: use this explicit scalar export;do not claim the original table exporter succeeded.");
        var result=new Prepared(new Captured(path,raw.length,AdmixtureInputs.digest(raw)),rows,columns,total,frame,csvHash,gdsHash,runtime,warnings);result.verify(check);return result;
    }

    static Export export(Prepared prepared,List<String> variantKeys,Path output,Limits limits,Runnable check)throws IOException {
        if(variantKeys.size()!=prepared.rows()||prepared.columns().size()>limits.columns()||prepared.rows()>limits.rows()||(long)prepared.rows()*prepared.columns().size()>limits.cells())throw new IOException("Complete native scalar export population exceeds its declared capacity");
        int nc=prepared.columns().size();if(limits.blockCells()<nc)throw new IOException("One complete annotation row exceeds export block-cell capacity");
        prepared.verify(check);output=output.toAbsolutePath().normalize();Files.createDirectory(output);
        Path valuesPath=output.resolve("values.tsv"),statesPath=output.resolve("cell-states.tsv");var budget=new Budget(limits.outputBytes());
        long[] sourceOffsets=new long[nc],storedOffsets=new long[nc],sna=new long[nc],snn=new long[nc],tna=new long[nc],tnn=new long[nc],converted=new long[nc];
        int block=Math.max(1,Math.min(prepared.rows(),limits.blockCells()/nc));long cells=0,convertedTotal=0;
        try(OutputStream values=new BufferedOutputStream(Files.newOutputStream(valuesPath,StandardOpenOption.CREATE_NEW));OutputStream states=new BufferedOutputStream(Files.newOutputStream(statesPath,StandardOpenOption.CREATE_NEW))) {
            List<String> headers=prepared.columns().stream().map(Column::name).toList();row(values,headers,budget);row(states,headers,budget);
            for(int start=0;start<prepared.rows();) {
                check.run();int size=Math.min(block,prepared.rows()-start);long retained=128L*nc*size;
                if(retained>limits.bufferBytes())throw new IOException("Complete annotation export block exceeds buffer capacity");
                String[][] texts=new String[nc][size],statuses=new String[nc][size];
                for(int col=0;col<nc;col++) {
                    Column definition=prepared.columns().get(col);
                    try(var source=new Cursor(definition.source(),sourceOffsets[col]);var stored=new Cursor(definition.stored(),storedOffsets[col])) {
                        for(int at=0;at<size;at++) {
                            check.run();int scalar=(int)Math.min(limits.scalarBytes(),Math.max(0,(limits.bufferBytes()-retained)/8));
                            Cell a=source.next(definition.kind(),scalar),b=stored.next(definition.kind(),scalar);
                            if(a.state().equals("NA"))sna[col]++;if(a.state().equals("NaN"))snn[col]++;if(b.state().equals("NA"))tna[col]++;if(b.state().equals("NaN"))tnn[col]++;
                            boolean change=definition.kind()==Kind.UTF8_LENGTH_INT32_LE_NA_NEGATIVE1&&a.state().equals("NA");
                            if(change){if(!b.equals(new Cell("value","",0)))throw new IOException("Missing character did not retain source GDS empty-string projection: "+definition.name());converted[col]++;convertedTotal++;}
                            else if(!a.equals(b))throw new IOException("Complete source/stored scalar value or IEEE bits differ: "+definition.name()+", row "+(start+at+1));
                            if(col==0&&(!b.state().equals("value")||!b.text().equals(variantKeys.get(start+at))))throw new IOException("Scalar export VarInfo differs from native original variant order");
                            texts[col][at]=b.text();statuses[col][at]=change?"source-character-NA-to-empty":b.state();
                            retained+=2L*b.text().length();if(retained>limits.bufferBytes())throw new IOException("Complete annotation export block exceeds decoded buffer capacity");cells++;
                        }
                        sourceOffsets[col]=source.position;storedOffsets[col]=stored.position;
                        if(start+size==prepared.rows()){source.end();stored.end();}
                    }
                }
                for(int at=0;at<size;at++) {var valuesRow=new ArrayList<String>(nc);var statesRow=new ArrayList<String>(nc);for(int col=0;col<nc;col++){valuesRow.add(texts[col][at]);statesRow.add(statuses[col][at]);}row(values,valuesRow,budget);row(states,statesRow,budget);}
                start+=size;
            }
        }
        for(int i=0;i<nc;i++){Column c=prepared.columns().get(i);if(sna[i]!=c.sourceNa()||snn[i]!=c.sourceNan()||tna[i]!=c.storedNa()||tnn[i]!=c.storedNan()||converted[i]!=c.convertedCharacterNa())throw new IOException("Actual canonical missingness differs from complete scalar ledger: "+c.name());}
        prepared.verify(check);String valuesHash=FollowupSupport.hash(valuesPath),statesHash=FollowupSupport.hash(statesPath);
        var warnings=new ArrayList<>(prepared.warnings());
        if(convertedTotal>0)warnings.add("CHARACTER MISSINGNESS CONVERSION: "+convertedTotal+" source character NA cells are stored as empty strings. Required action: use cell-states.tsv and the original joined CSV;empty annotation values do not establish absence of biological effect.");
        var schema=new ArrayList<Map<String,Object>>();
        for(var column:prepared.columns()) {
            var description=new LinkedHashMap<String,Object>();description.put("index",column.index());description.put("name",column.name());description.put("kind",column.kind().name());description.put("source_class",column.sourceClass());description.put("source_attributes",SimpleJson.parse(column.sourceAttributesJson()));description.put("source_attributes_sha256",column.sourceAttributesSha256());description.put("source_attributes_lost",column.attributesLost());
            description.put("source",reference(output,column.source()));description.put("expected",reference(output,column.expected()));description.put("stored",reference(output,column.stored()));schema.add(description);
        }
        var manifest=new LinkedHashMap<String,Object>();manifest.put("schema","favor-annotation-tsv-v1");manifest.put("status","complete-scalar-export");manifest.put("upstream_workflow_certified",false);manifest.put("rows",prepared.rows());manifest.put("columns",schema);manifest.put("values",cells);manifest.put("converted_character_na",convertedTotal);manifest.put("source_csv_sha256",prepared.csvSha256());manifest.put("source_gds_sha256",prepared.gdsSha256());manifest.put("source_audit",reference(output,prepared.audit()));manifest.put("runtime",new java.util.TreeMap<>(prepared.runtime()));manifest.put("values_tsv_sha256",valuesHash);manifest.put("cell_states_tsv_sha256",statesHash);manifest.put("warnings",warnings);
        Path readme=output.resolve("README.txt"),complete=output.resolve("export.json"),temporary=output.resolve("export.json.partial");
        String explanation="FAVOR AUDITED SCALAR EXPORT\n\n"+String.join("\n\n",warnings)+"\n\nFiles: values.tsv contains quoted UTF-8 stored scalar values;cell-states.tsv distinguishes value,NA,NaN,and source-character-NA-to-empty. Numeric values retain signed zero and infinity. Date/time columns contain the stored numbers;their original classes,units and timezone are retained in export.json. Referenced canonical files retain every IEEE bit and missingness tag. Keep the named audit/canonical files with this export.\n\nSCOPE: This is a native scalar export,not a certification of the upstream workflow. Required parent checks include protected runtime/source identity,native join alignment,complete GDS preservation and every nested command event. Complete scalar values can remain after a failed upstream command. The original GDS/table metadata was not repaired.\n";
        try(OutputStream stream=Files.newOutputStream(readme,StandardOpenOption.CREATE_NEW)){budget.write(stream,explanation);}
        try(OutputStream stream=Files.newOutputStream(temporary,StandardOpenOption.CREATE_NEW)){budget.write(stream,RegenieRuntime.json(manifest)+"\n");}
        prepared.verify(check);Files.move(temporary,complete,java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        return new Export(prepared.rows(),nc,cells,convertedTotal,valuesPath,statesPath,valuesHash,statesHash,budget.bytes,warnings,complete,readme);
    }
    private static final class Cursor implements AutoCloseable {
        private final DataInputStream input;
        private long position;
        Cursor(Captured file,long offset)throws IOException {
            var channel=FileChannel.open(file.path(),StandardOpenOption.READ);
            try{channel.position(offset);input=new DataInputStream(new BufferedInputStream(Channels.newInputStream(channel)));position=offset;}catch(IOException|RuntimeException failure){channel.close();throw failure;}
        }
        Cell next(Kind kind,int scalar)throws IOException {
            if(kind==Kind.UTF8_LENGTH_INT32_LE_NA_NEGATIVE1){int size=Integer.reverseBytes(input.readInt());position+=4;if(size==-1)return new Cell("NA","NA",0);if(size<0||size>scalar)throw new IOException("Invalid or oversized complete UTF8 canonical scalar");byte[] raw=input.readNBytes(size);if(raw.length!=size)throw new IOException("Truncated canonical UTF8 scalar");position+=size;String value=StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(raw)).toString();return new Cell("value",value,0);}
            if(kind==Kind.FLOAT64_LE_TAGGED){int tag=input.readUnsignedByte();long bits=Long.reverseBytes(input.readLong());position+=9;double value=Double.longBitsToDouble(bits);if(tag>2||tag==0&&Double.isNaN(value)||tag!=0&&!Double.isNaN(value))throw new IOException("Invalid canonical floating missingness tag/IEEE value");return new Cell(tag==1?"NA":tag==2?"NaN":"value",tag==1?"NA":Double.toString(value),bits);}
            int value=Integer.reverseBytes(input.readInt());position+=4;if(value==Integer.MIN_VALUE)return new Cell("NA","NA",value);if(kind==Kind.LOGICAL_INT32_LE&&value!=0&&value!=1)throw new IOException("Invalid canonical logical scalar");return new Cell("value",kind==Kind.LOGICAL_INT32_LE?(value==0?"FALSE":"TRUE"):Integer.toString(value),value);
        }
        void end()throws IOException {if(input.read()!=-1)throw new IOException("Extra values after complete canonical scalar population");}
        @Override public void close()throws IOException{input.close();}
    }
    private static final class Budget {
        private final long maximum;private long bytes;
        Budget(long maximum){this.maximum=maximum;}
        void write(OutputStream output,String text)throws IOException {byte[] raw=text.getBytes(StandardCharsets.UTF_8);if(raw.length>maximum-bytes)throw new IOException("Complete scalar TSV export byte capacity exceeded");output.write(raw);bytes+=raw.length;}
    }
    private static void row(OutputStream output,List<String> cells,Budget budget)throws IOException {for(int i=0;i<cells.size();i++){if(i>0)budget.write(output,"\t");budget.write(output,"\""+cells.get(i).replace("\"","\"\"")+"\"");}budget.write(output,"\n");}
    private static Captured capture(Path root,Map<?,?> map,String role,int index,Limits limits,Runnable check)throws IOException {
        keys(map,Set.of("path","bytes","sha256"));String relative=role+"/c"+String.format(java.util.Locale.ROOT,"%04d",index)+".bin";
        if(!text(map,"path").equals(relative))throw new IOException("Canonical annotation path differs from owned fixed column plan");
        var result=new Captured(root.resolve(relative),whole(map,"bytes",0,limits.canonicalBytes()),hash(map,"sha256"));verifyFile(result,check);return result;
    }
    private static Map<String,Object> reference(Path output,Captured file) {String path=output.getRoot().equals(file.path().getRoot())?output.relativize(file.path()).toString():file.path().toString();var value=new LinkedHashMap<String,Object>();value.put("path",path.replace('\\','/'));value.put("bytes",file.bytes());value.put("sha256",file.sha256());return value;}
    private static void verifyFile(Captured file,Runnable check)throws IOException {check.run();if(!Files.isRegularFile(file.path(),LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(file.path())||!file.path().toRealPath().equals(file.path())||Files.size(file.path())!=file.bytes()||!FollowupSupport.hash(file.path()).equals(file.sha256()))throw new IOException("Protected annotation audit/canonical file is missing,linked,changed or incomplete: "+file.path());}
    private static Map<?,?> object(Object value)throws IOException {if(!(value instanceof Map<?,?> map))throw new IOException("Require owned annotation JSON object");return map;}
    private static void keys(Map<?,?> map,Set<String> keys)throws IOException {if(!map.keySet().equals(keys))throw new IOException("Unknown or missing complete annotation audit fields");}
    private static String text(Map<?,?> map,String key)throws IOException {if(!(map.get(key) instanceof String s)||s.isEmpty()||s.length()>8192||s.codePoints().anyMatch(Character::isISOControl))throw new IOException("Invalid annotation audit text: "+key);return s;}
    private static String hash(Map<?,?> map,String key)throws IOException {String s=text(map,key);if(!s.matches("[a-f0-9]{64}"))throw new IOException("Invalid annotation SHA256: "+key);return s;}
    private static boolean flag(Map<?,?> map,String key)throws IOException {if(!(map.get(key) instanceof Boolean value))throw new IOException("Invalid annotation boolean: "+key);return value;}
    private static long whole(Map<?,?> map,String key,long min,long max)throws IOException {Object v=map.get(key);if(!(v instanceof Number n)||!Double.isFinite(n.doubleValue())||n.doubleValue()!=Math.rint(n.doubleValue())||n.doubleValue()<min||n.doubleValue()>max)throw new IOException("Invalid complete annotation count: "+key);return (long)n.doubleValue();}
    private static List<String> strings(Object value)throws IOException {if(!(value instanceof List<?> list))throw new IOException("Require annotation string array");var result=new ArrayList<String>();for(Object item:list){if(!(item instanceof String s)||s.isEmpty()||s.length()>8192||s.codePoints().anyMatch(Character::isISOControl))throw new IOException("Invalid annotation string array value");result.add(s);}return List.copyOf(result);}
    private static List<String> attributeStrings(Object value)throws IOException {return value instanceof String s?List.of(s):strings(value);}
}
