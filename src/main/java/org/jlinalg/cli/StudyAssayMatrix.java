/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Bounded UTF-8 delimited rows; never scans or retains a whole matrix on open. */
final class StudyAssayMatrix implements AutoCloseable {
    final List<String> header;
    private final BufferedReader reader;
    private final Path path;
    private final char delimiter;
    private final long maximumLine,maximumBytes;
    private long line,bytes;
    StudyAssayMatrix(Path path,Map<String,Object> p) throws IOException {
        this.path=path;maximumLine=((Number)p.get("maximum_record_mib")).longValue()*1048576;
        maximumBytes=((Number)p.get("maximum_source_mib")).longValue()*1048576;
        if(!Files.isRegularFile(path)||Files.size(path)>maximumBytes)throw new IOException("Assay source missing or exceeds maximum_source_mib");
        delimiter=path.toString().toLowerCase(Locale.ROOT).endsWith(".csv")?',':'\t';
        reader=new BufferedReader(new InputStreamReader(Files.newInputStream(path),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));
        try {var first=next();if(first==null||first.isEmpty())throw new IOException("Assay table needs a header");header=first.stream().map(String::trim).toList();}
        catch(IOException|RuntimeException invalid){reader.close();throw invalid;}
    }
    List<String> matrixSamples(Map<String,Object> p) throws IOException {
        if(header.size()<2||header.size()-1>((Number)p.get("maximum_samples")).intValue())throw new IOException("Assay sample header is empty or exceeds maximum_samples");
        var samples=header.subList(1,header.size());var seen=new HashSet<String>();for(String s:samples)if(!validId(s)||!seen.add(s))throw new IOException("Assay sample IDs must be unique, nonblank and free of control characters");return samples;
    }
    List<String> next() throws IOException {
        var text=new StringBuilder();boolean any=false;
        for(int c;(c=reader.read())!=-1;){any=true;if(++bytes>maximumBytes)throw new IOException("Assay byte bound exceeded");if(c=='\n')break;if(text.length()>=maximumLine)throw new IOException("Assay record exceeds maximum_record_mib");text.append((char)c);}
        if(!any)return null;if(!text.isEmpty()&&text.charAt(text.length()-1)=='\r')text.setLength(text.length()-1);
        int encoded=text.toString().getBytes(StandardCharsets.UTF_8).length;bytes+=encoded-text.length();if(bytes>maximumBytes||encoded>maximumLine)throw new IOException("Assay byte/record bound exceeded");
        String record=text.toString();if(line==0&&record.startsWith("\uFEFF"))record=record.substring(1);return DelimitedData.parse(record,delimiter,++line,path);
    }
    static boolean validId(String id){return !id.isBlank()&&id.chars().noneMatch(c->Character.isISOControl(c));}
    static String cell(String value){return value.indexOf('\t')>=0||value.indexOf('"')>=0||value.indexOf('\n')>=0||value.indexOf('\r')>=0?'"'+value.replace("\"","\"\"")+'"':value;}
    @Override public void close() throws IOException {reader.close();}
}
