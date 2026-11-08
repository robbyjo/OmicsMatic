/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Exact inverse of the enumerated original prefix substitutions; no general source/binary exception. */
final class LofteeRuntimePrefix {
    private LofteeRuntimePrefix() { }
    static byte[] baseline(byte[] actual,Map<String,Object> description,String currentRoot)throws IOException {
        String old=text(description.get("old_prefix"));byte[] before=old.getBytes(StandardCharsets.US_ASCII),now=currentRoot.getBytes(StandardCharsets.US_ASCII);require(currentRoot.matches("/[A-Za-z0-9_./-]+")&&now.length<=before.length,"Require a portable runtime prefix no longer than the captured prefix; prepare a NEW shorter runtime path");if(Arrays.equals(before,now))return actual;
        require(find(actual,before,0)<0,"An old runtime prefix remains; partial relocation/fallback is refused");String kind=text(description.get("kind"));
        if(kind.equals("exact-text")){require(find(actual,now,0)>=0,"Expected exact text prefix substitution is absent");return replace(actual,now,before);}
        require(kind.equals("exact-NUL-terminated-binary-fields-shorter-or-equal-prefix"),"Unknown runtime relocation policy");byte[] result=actual.clone();long last=-1;
        for(Object raw:list(description.get("regions"))){var region=object(raw);int offset=(int)integer(region.get("offset"),0,actual.length),length=(int)integer(region.get("length"),1,actual.length);require(offset>last&&length<actual.length-offset,"Overlapping/out-of-range binary prefix region");int end=offset;while(end<offset+length&&actual[end]!=0)end++;for(int i=end;i<=offset+length;i++)require(actual[i]==0,"Unexpected bytes in exact binary prefix padding");byte[] field=Arrays.copyOfRange(actual,offset,end);require(find(field,now,0)>=0,"Expected exact binary prefix substitution is absent");byte[] restored=replace(field,now,before);require(restored.length==length,"Binary prefix field length differs from captured source");System.arraycopy(restored,0,result,offset,length);result[offset+length]=0;last=(long)offset+length;}
        return result;
    }
    private static int find(byte[] data,byte[] needle,int start){outer:for(int i=start;i<=data.length-needle.length;i++){for(int j=0;j<needle.length;j++)if(data[i+j]!=needle[j])continue outer;return i;}return -1;}
    private static byte[] replace(byte[] data,byte[] from,byte[] to)throws IOException {
        var result=new ByteArrayOutputStream();int at=0,found;
        while((found=find(data,from,at))>=0){require((long)result.size()+found-at+to.length<=536870912,"Normalized runtime file capacity exceeded");result.write(data,at,found-at);result.write(to);at=found+from.length;}
        result.write(data,at,data.length-at);return result.toByteArray();
    }
}
