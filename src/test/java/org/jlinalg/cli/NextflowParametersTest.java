/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NextflowParametersTest {
    @TempDir Path temp;
    private static Object parse(String text)throws IOException{return NextflowParameters.parse(text,1048576,10000);}
    @Test void largeIntegerAndDecimalLexemesSurviveWithoutDoubleConversion()throws Exception{
        String source="{\"big\":900719925474099312345678901,\"precise\":0.123456789012345678901234567890,\"exponent\":1e+00030,\"negative\":-0,\"tiny\":-5E-99999}";
        assertEquals(source,NextflowJson.parameters(parse(source)));
    }
    @Test void parameterObjectOrderIsPreservedWhileDiagnosticKeysAreCanonical()throws Exception{
        Object source=parse("{\"z\":1,\"a\":2}");assertEquals("{\"z\":1,\"a\":2}",NextflowJson.parameters(source));assertEquals("{\"a\":2,\"z\":1}",NextflowJson.json(source));
    }
    @Test void onlyDeclaredBindingObjectsAreRewritten()throws Exception{
        Object source=parse("{\"in\":{\"$input\":\"reads\",\"path\":\"sample/a.fq\"},\"out\":{\"$output\":\"reports\"},\"threshold\":0.00000000000000000012345,\"text\":\"$input reads\"}");
        Path inputs=temp.resolve("capture"),out=temp.resolve("output");Map<?,?> bound=NextflowFiles.object(NextflowParameters.bind(source,Map.of("reads",inputs),out));
        assertEquals(inputs.resolve("sample/a.fq").toString(),bound.get("in"));assertEquals(out.resolve("reports").toString(),bound.get("out"));assertEquals("$input reads",bound.get("text"));assertTrue(NextflowJson.parameters(bound).contains("0.00000000000000000012345"));
    }
    @Test void literalEscapePreservesReservedObjects()throws Exception{
        Object source=parse("{\"$literal\":{\"$input\":\"not-a-binding\"}}");assertEquals("{\"$input\":\"not-a-binding\"}",NextflowJson.parameters(NextflowParameters.bind(source,Map.of(),temp)));
    }
    @Test void missingAndAmbiguousBindingsAreRejected()throws Exception{
        for(String source:List.of("{\"$input\":\"missing\"}","{\"$input\":\"reads\",\"typo\":1}","{\"$output\":\"x\",\"extra\":1}"))assertThrows(IOException.class,()->NextflowParameters.bind(parse(source),Map.of("reads",temp),temp));
    }
    @Test void bindingTraversalAndAbsolutePathsAreRejected()throws Exception{
        for(String member:List.of("../outside","/absolute","a/../b","a//b","a/./b","a\\b")){
            String source="{\"$output\":"+NextflowJson.json(member)+"}";assertThrows(IOException.class,()->NextflowParameters.bind(parse(source),Map.of(),temp));
        }
    }
    @Test void duplicatesIncludeUnicodeEscapedKeys()throws Exception{
        assertThrows(IOException.class,()->parse("{\"a\":1,\"a\":2}"));assertThrows(IOException.class,()->parse("{\"a\":1,\"\\u0061\":2}"));
    }
    @Test void malformedNumbersAndTrailingSyntaxNeverReturnAPrefix(){
        for(String text:List.of("01","-01","+1",".1","1.","1e","NaN","Infinity","true false","[1,]","{\"a\":1,}","[1]garbage"))assertThrows(IOException.class,()->parse(text),text);
    }
    @Test void booleansNullEscapesAndUnicodeHaveTheSameValuesAfterRendering()throws Exception{
        String source="[true,false,null,\"α😀\\n\\t\\u0041\"]";Object value=parse(source);assertEquals(value,parse(NextflowJson.parameters(value)));
    }
    @Test void invalidSurrogatesAndNonAsciiHexEscapesAreRejected(){
        for(String text:List.of("\"\\ud800\"","\"\\udc00\"","\"\\uＦＦＦＦ\""))assertThrows(IOException.class,()->parse(text));
        assertThrows(IOException.class,()->NextflowJson.json(new String(new char[]{(char)0xd800})));
    }
    @Test void characterNodeAndNestingCapacitiesRejectTheWholeRequest(){
        assertThrows(IOException.class,()->NextflowParameters.parse("[1,2]",3,100));assertThrows(IOException.class,()->NextflowParameters.parse("[1,2]",100,2));assertThrows(IOException.class,()->parse("[".repeat(82)+"0"+"]".repeat(82)));
    }
    @Test void primitiveArraysAreJsonArraysRatherThanIdentityStrings()throws Exception{assertEquals("[1,3]",NextflowJson.json(new int[]{1,3}));assertEquals("[[true,false],[0,2]]",NextflowJson.json(new Object[]{new boolean[]{true,false},new long[]{0,2}}));}
    @Test void nonfiniteUnknownAndNonStringKeyMetadataAreRejected(){
        for(Object value:List.of(Double.NaN,Double.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,new Object()))assertThrows(IOException.class,()->NextflowJson.json(value));
        var keys=new LinkedHashMap<Object,Object>();keys.put(1,"numeric");keys.put("1","text");assertThrows(IOException.class,()->NextflowJson.json(keys));
    }
    @Test void integerMetadataNeverSilentlyRoundsDecimals()throws Exception{
        assertEquals(10000,NextflowFiles.integer(parse("10000"),0,10000));assertThrows(IOException.class,()->NextflowFiles.integer(parse("1.0"),0,10000));assertThrows(IOException.class,()->NextflowFiles.integer(parse("9223372036854775808"),0,Long.MAX_VALUE));
    }
}
