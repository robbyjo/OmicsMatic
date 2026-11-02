/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Lossless JSON parameters: numeric lexemes and object order survive binding. */
final class NextflowParameters {
    private NextflowParameters() { }
    private static final Pattern NUMBER=Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?");
    record NumberToken(String text) {
        NumberToken { if(text==null||!NUMBER.matcher(text).matches())throw new IllegalArgumentException("Invalid JSON numeric token"); }
    }
    static Object parse(String text,int maximumChars,int maximumNodes)throws IOException {
        if(text.length()>maximumChars||maximumChars<1||maximumNodes<1)throw new IOException("Nextflow JSON character/node capacity exceeded");
        var parser=new Parser(text,maximumNodes);Object value=parser.value(0);parser.space();if(parser.offset!=text.length())throw parser.error("Trailing JSON content");return value;
    }
    static Object bind(Object value,Map<String,Path> inputs,Path output)throws IOException {
        if(!output.isAbsolute()||!output.normalize().equals(output))throw new IOException("Output binding root must be absolute and normalized");
        return bind(value,inputs,output,0);
    }
    private static Object bind(Object value,Map<String,Path> inputs,Path output,int depth)throws IOException {
        if(depth>80)throw new IOException("Nextflow binding nesting capacity exceeded");
        if(value instanceof Map<?,?> map){
            if(map.keySet().equals(Set.of("$literal")))return map.get("$literal");
            if(map.containsKey("$input")){
                if(!map.keySet().equals(Set.of("$input"))&&!map.keySet().equals(Set.of("$input","path")))throw new IOException("Input binding requires $input and optional path only; use $literal for literal reserved keys");
                String id=text(map.get("$input"));Path base=inputs.get(id);if(base==null)throw new IOException("Undeclared captured input binding: "+id);
                if(!base.isAbsolute()||!base.normalize().equals(base))throw new IOException("Captured input root is not absolute/normalized");
                return relative(base,map.containsKey("path")?text(map.get("path")):"").toString();
            }
            if(map.containsKey("$output")){
                if(!map.keySet().equals(Set.of("$output")))throw new IOException("Output binding requires $output only; use $literal for literal reserved keys");
                return relative(output,text(map.get("$output"))).toString();
            }
            var result=new LinkedHashMap<String,Object>();for(var entry:map.entrySet())result.put(text(entry.getKey()),bind(entry.getValue(),inputs,output,depth+1));return result;
        }
        if(value instanceof java.util.List<?> list){var result=new ArrayList<Object>();for(Object item:list)result.add(bind(item,inputs,output,depth+1));return result;}
        return value;
    }
    private static Path relative(Path root,String member)throws IOException {
        if(member.isEmpty())return root;
        if(member.startsWith("/")||member.indexOf('\\')>=0||member.chars().anyMatch(c->c<32||c==127))throw new IOException("Invalid relative binding path");
        for(String part:member.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals(".."))throw new IOException("Binding path must have complete relative components");
        try{Path value=root.resolve(member).normalize();if(!value.startsWith(root)||value.equals(root))throw new IOException("Binding path escapes its declared root");return value;}
        catch(java.nio.file.InvalidPathException invalid){throw new IOException("Invalid binding path",invalid);}
    }
    private static String text(Object value)throws IOException {if(!(value instanceof String text))throw new IOException("Expected JSON text binding/key");return text;}
    static void unicode(String value)throws IOException {
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(Character.isHighSurrogate(c)){if(i+1>=value.length()||!Character.isLowSurrogate(value.charAt(++i)))throw new IOException("Unpaired Unicode surrogate in Nextflow JSON");}
            else if(Character.isLowSurrogate(c))throw new IOException("Unpaired Unicode surrogate in Nextflow JSON");
        }
    }
    private static final class Parser {
        final String source;final int maximumNodes;int offset,nodes;
        Parser(String source,int maximumNodes){this.source=source;this.maximumNodes=maximumNodes;}
        IOException error(String message){return new IOException(message+" at JSON character "+offset);}
        void space(){while(offset<source.length()&&" \t\r\n".indexOf(source.charAt(offset))>=0)offset++;}
        boolean take(char c){if(offset<source.length()&&source.charAt(offset)==c){offset++;return true;}return false;}
        Object value(int depth)throws IOException {
            if(depth>80||++nodes>maximumNodes)throw error("Nextflow JSON nesting/node capacity exceeded");space();if(offset==source.length())throw error("Missing JSON value");
            char c=source.charAt(offset);
            if(c=='"')return string();
            if(c=='{'){
                offset++;var result=new LinkedHashMap<String,Object>();space();if(take('}'))return result;
                for(;;){space();if(offset==source.length()||source.charAt(offset)!='"')throw error("Expected object key");String key=string();space();if(!take(':'))throw error("Expected colon");if(result.containsKey(key))throw error("Duplicate object key");result.put(key,value(depth+1));space();if(take('}'))return result;if(!take(','))throw error("Expected comma or object end");}
            }
            if(c=='['){offset++;var result=new ArrayList<Object>();space();if(take(']'))return result;for(;;){result.add(value(depth+1));space();if(take(']'))return result;if(!take(','))throw error("Expected comma or array end");}}
            for(String literal:new String[]{"true","false","null"})if(source.startsWith(literal,offset)){offset+=literal.length();return literal.equals("null")?null:literal.equals("true");}
            int start=offset;while(offset<source.length()&&" \t\r\n,]}".indexOf(source.charAt(offset))<0)offset++;
            String token=source.substring(start,offset);if(!NUMBER.matcher(token).matches())throw error("Invalid JSON number/literal");return new NumberToken(token);
        }
        String string()throws IOException {
            offset++;var result=new StringBuilder();
            while(offset<source.length()){
                char c=source.charAt(offset++);if(c=='"'){String value=result.toString();unicode(value);return value;}
                if(c<32)throw error("Unescaped control character");
                if(c!='\\'){result.append(c);continue;}if(offset==source.length())throw error("Incomplete JSON escape");
                char escaped=source.charAt(offset++);
                switch(escaped){
                    case '"','\\','/'->result.append(escaped);case 'b'->result.append('\b');case 'f'->result.append('\f');case 'n'->result.append('\n');case 'r'->result.append('\r');case 't'->result.append('\t');
                    case 'u'->{if(offset+4>source.length())throw error("Incomplete Unicode escape");int code=0;for(int i=0;i<4;i++){char hex=source.charAt(offset++);int digit=Character.digit(hex,16);if(hex>127||digit<0)throw error("Invalid Unicode escape");code=(code<<4)|digit;}result.append((char)code);}
                    default->throw error("Unknown JSON escape");
                }
            }
            throw error("Unterminated JSON string");
        }
    }
}
