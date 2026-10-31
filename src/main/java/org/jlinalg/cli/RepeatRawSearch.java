/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Complete, bounded original raw-result header/census checks, including historical trailing headers. */
final class RepeatRawSearch {
    private RepeatRawSearch() { }
    static final int METADATA_CHARACTERS=65536;
    private static final List<String> PREFIXES=List.of("RepeatMasker version","run with rmblastn version","RM Library:","Total Sequences:","Total Length:");
    private static final Pattern VERSION=Pattern.compile("RepeatMasker version ([0-9]+(?:\\.[0-9]+)+)\\s*,\\s*(default|sensitive|quick|rushjob) mode");
    private static final Pattern ENGINE=Pattern.compile("run with rmblastn version (\\S+)");
    private static final Pattern SEQUENCES=Pattern.compile("Total Sequences:\\s*([0-9]+)");
    private static final Pattern LENGTH=Pattern.compile("Total Length:\\s*([0-9]+)");
    record Report(String version,String speed,String engineVersion,String libraryLabel,long queries,long bases,
            boolean gzip,long decodedBytes,long decodedCharacters,long lines) { }
    static Report read(Path supplied,boolean gzip,String version,RepeatRecipe.Speed speed,long queries,long bases,
            long maximumBytes,int maximumMetadataCharacters,Runnable check)throws IOException{
        if(!Set.of("4.2.6","4.1.5").contains(version)||speed==null||queries<1||bases<1||maximumBytes<1||maximumMetadataCharacters<1)
            throw new IOException("Raw RepeatMasker census requires an explicit supported edition,mode,complete query population and capacities");
        Path path=supplied.toAbsolutePath().normalize();check.run();
        if(!Files.isRegularFile(path,LinkOption.NOFOLLOW_LINKS)||!path.toRealPath().equals(path)||Files.size(path)>maximumBytes)
            throw new IOException("Raw RepeatMasker result must be a complete canonical regular file within its byte capacity");
        var fields=new HashMap<String,String>();long characters=0,lines=0;var line=new CensusLine(maximumMetadataCharacters);long decoded;boolean unfinished=false,carriage=false;
        try(var raw=Files.newInputStream(path);var payload=gzip?new RepeatGzip(raw,check):raw;var input=new ByteLimit(payload,maximumBytes,check);
                var reader=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)),8192)){
            char[] buffer=new char[8192];int count;
            while((count=reader.read(buffer))>=0){
                check.run();characters=Math.addExact(characters,count);
                for(int i=0;i<count;i++){
                    char value=buffer[i];if(value=='\n'){
                        line.finish(fields);line.reset();unfinished=false;carriage=false;lines++;
                    }else{
                        if(carriage)throw new IOException("Unexpected carriage return inside an original raw-result line");
                        if(value<32&&value!='\t'&&value!='\r')throw new IOException("Unexpected control character in an original raw result");
                        unfinished=true;if(value=='\r')carriage=true;else line.take(value);
                    }
                }
            }
            if(unfinished)throw new IOException("Original raw result ends with an incomplete unterminated line");
            decoded=input.bytes;
        }
        if(!fields.keySet().equals(Set.of("version","speed","engine","library","queries","bases")))throw new IOException("Missing complete original raw-result version/engine/library/query census");
        String expectedSpeed=switch(speed){case DEFAULT->"default";case SENSITIVE->"sensitive";case QUICK->"quick";case RUSH->"rushjob";};
        long actualQueries=number(fields.get("queries")),actualBases=number(fields.get("bases"));
        if(!fields.get("version").equals(version)||!fields.get("speed").equals(expectedSpeed)||!fields.get("engine").equals("2.17.1+")
                ||actualQueries!=queries||actualBases!=bases)throw new IOException("Original raw result differs from the protected request: version expected="+version+" actual="+fields.get("version")+";mode expected="+expectedSpeed+" actual="+fields.get("speed")+";engine expected=2.17.1+ actual="+fields.get("engine")+";queries expected="+queries+" actual="+actualQueries+";bases expected="+bases+" actual="+actualBases);
        check.run();return new Report(version,expectedSpeed,fields.get("engine"),fields.get("library"),queries,bases,gzip,decoded,characters,lines);
    }
    private static void consume(String raw,Map<String,String> fields)throws IOException{
        String text=raw.startsWith("##")?raw.substring(2).stripLeading():raw;
        if(text.startsWith("RepeatMasker version")){
            var match=VERSION.matcher(text.stripTrailing());if(!match.matches())throw new IOException("Malformed original raw-result version/mode line");put(fields,"version",match.group(1));put(fields,"speed",match.group(2));
        }else if(text.startsWith("run with rmblastn version")){
            var match=ENGINE.matcher(text.stripTrailing());if(!match.matches())throw new IOException("Malformed original raw-result search-engine line");put(fields,"engine",match.group(1));
        }else if(text.startsWith("RM Library:")){
            String value=text.substring("RM Library:".length());if(value.startsWith(" "))value=value.substring(1);put(fields,"library",value);
        }
        else if(text.startsWith("Total Sequences:")){
            var match=SEQUENCES.matcher(text.stripTrailing());if(!match.matches())throw new IOException("Malformed original raw-result query-count line");put(fields,"queries",match.group(1));
        }else if(text.startsWith("Total Length:")){
            var match=LENGTH.matcher(text.stripTrailing());if(!match.matches())throw new IOException("Malformed original raw-result query-length line");put(fields,"bases",match.group(1));
        }
    }
    private static void put(Map<String,String> fields,String key,String value)throws IOException{
        if(fields.putIfAbsent(key,value)!=null)throw new IOException("Duplicate original raw-result census field: "+key);
    }
    private static long number(String value)throws IOException{
        try{return Long.parseLong(value);}catch(NumberFormatException invalid){throw new IOException("Original raw-result census exceeds the exact integer range",invalid);}
    }
    private static final class CensusLine {
        private final int maximum;private final StringBuilder value=new StringBuilder();private int state;private boolean ignored,matched;
        CensusLine(int maximum){this.maximum=maximum;}
        void reset(){value.setLength(0);state=0;ignored=false;matched=false;}
        void take(char character)throws IOException{
            if(state==0){if(character=='#'){state=1;return;}state=3;}
            else if(state==1){if(character=='#'){state=2;return;}state=3;append('#');}
            else if(state==2){if(Character.isWhitespace(character))return;state=3;}
            append(character);
        }
        private void append(char character)throws IOException{
            if(ignored)return;value.append(character);
            if(!matched){
                String prefix=value.toString();boolean possible=false;
                for(String expected:PREFIXES){if(expected.startsWith(prefix))possible=true;if(prefix.startsWith(expected)){matched=true;possible=true;break;}}
                if(!possible){ignored=true;value.setLength(0);return;}
            }
            if(value.length()>maximum)throw new IOException("Complete raw-result census metadata exceeds its retained character capacity");
        }
        void finish(Map<String,String> fields)throws IOException{if(!ignored&&value.length()>0)consume(value.toString(),fields);}
    }
    private static final class ByteLimit extends FilterInputStream {
        private final long maximum;private final Runnable check;private long bytes;
        ByteLimit(InputStream input,long maximum,Runnable check){super(input);this.maximum=maximum;this.check=check;}
        private void add(int count)throws IOException{check.run();if(count>0){bytes=Math.addExact(bytes,count);if(bytes>maximum)throw new IOException("Complete decoded raw result exceeds its byte capacity");}}
        @Override public int read()throws IOException{int value=in.read();add(value<0?0:1);return value;}
        @Override public int read(byte[] target,int offset,int length)throws IOException{int count=in.read(target,offset,length);add(count);return count;}
    }
}
