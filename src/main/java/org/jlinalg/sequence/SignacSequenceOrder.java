/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.sequence;

import java.util.*;
import java.util.regex.Pattern;

/** Seqinfo 1.2.0 C-locale rankSeqlevels behavior used by prepared Signac peak tables. */
public final class SignacSequenceOrder {
    private SignacSequenceOrder() { }
    public enum SexChromosomeX { AUTO, TRUE, FALSE }
    public record Ranked(int[] ranks,List<String> warnings) {
        public Ranked {ranks=ranks.clone();warnings=List.copyOf(warnings);}
        @Override public int[] ranks(){return ranks.clone();}
    }
    private record Item(String name,String suffix,int category,long key) { }
    private static final Pattern SHORT=Pattern.compile("[1-9][0-9]*");
    private static final Pattern ROMAN=Pattern.compile("M{0,9}D?C{0,6}L?X{0,6}V?I{0,6}");
    /** R/C collation on valid Unicode uses UTF-8/code-point order rather than UTF-16 code-unit order. */
    public static int compareC(String a,String b){int i=0,j=0;while(i<a.length()&&j<b.length()){int x=a.codePointAt(i),y=b.codePointAt(j);if(x!=y)return Integer.compare(x,y);i+=Character.charCount(x);j+=Character.charCount(y);}return Integer.compare(a.length()-i,b.length()-j);}
    public static Ranked rank(List<String> names,SexChromosomeX sex) {
        Objects.requireNonNull(sex);var levels=new TreeSet<String>(SignacSequenceOrder::compareC);for(String name:names){SignacFragments.identity(name);levels.add(name);}
        var provisional=new HashMap<String,Integer>();var processed=new HashSet<String>();Integer last=0;boolean overflow=false;
        for(String prefix:List.of("CHR","chr","CH","ch","")) {
            List<String> selected=levels.stream().filter(n->!processed.contains(n)&&n.startsWith(prefix)).toList();
            boolean hasY=selected.stream().anyMatch(n->n.substring(prefix.length()).equals("Y"));
            boolean hasNumeric=selected.stream().anyMatch(n->shortNumberPrefix(n.substring(prefix.length())));
            boolean sexual=sex==SexChromosomeX.TRUE||sex==SexChromosomeX.AUTO&&(hasY||hasNumeric);
            List<Item> items=new ArrayList<>();for(String name:selected){String s=name.substring(prefix.length());if(s.equals("M")){items.add(new Item(name,s,0,1000));items.add(new Item(name,s,7,0));}else items.add(classify(name,s,sexual));}
            for(int category=0;category<=17;category++) {
                final int c=category;var group=items.stream().filter(x->x.category==c).toList();if(group.isEmpty())continue;
                var postfixes=new TreeSet<String>(SignacSequenceOrder::compareC);if(category==9)for(var item:group)postfixes.add(item.suffix.substring(digits(item.suffix)));
                var postfixRanks=new HashMap<String,Integer>();int index=0;for(String p:postfixes)postfixRanks.put(p,++index);
                List<Long> keys=new ArrayList<>();for(var item:group){long key=item.key;if(category==9){int n=digits(item.suffix);key=(long)(postfixRanks.size()+1)*Long.parseLong(item.suffix.substring(0,n))+postfixRanks.get(item.suffix.substring(n));}keys.add(key);}
                boolean invalid=keys.stream().anyMatch(k->k<Integer.MIN_VALUE+1L||k>Integer.MAX_VALUE);long min=keys.stream().mapToLong(Long::longValue).min().orElseThrow();Integer maximum=null;
                for(int i=0;i<group.size();i++) {
                    Long value=last==null||invalid?null:(long)last+keys.get(i);
                    if(value!=null&&(value>Integer.MAX_VALUE||value<Integer.MIN_VALUE+1L)){value=null;overflow=true;}
                    if(value!=null){value-=min;if(value>Integer.MAX_VALUE||value<Integer.MIN_VALUE+1L){value=null;overflow=true;}}
                    if(value!=null){value++;if(value>Integer.MAX_VALUE){value=null;overflow=true;}}
                    String name=group.get(i).name;provisional.put(name,value==null?null:value.intValue());processed.add(name);
                    if(value!=null&&(maximum==null||value>maximum))maximum=value.intValue();
                }
                if(invalid)overflow=true;
                // R's max does not remove NA: a single overflow poisons subsequent provisional offsets.
                last=group.stream().anyMatch(x->provisional.get(x.name)==null)?null:maximum;
            }
        }
        var sorted=new ArrayList<>(levels);sorted.sort((a,b)->{Integer x=provisional.get(a),y=provisional.get(b);int c=x==null?(y==null?0:1):y==null?-1:Integer.compare(x,y);return c!=0?c:compareC(a,b);});
        var ranks=new HashMap<String,Integer>();for(int i=0;i<sorted.size();i++)ranks.put(sorted.get(i),i+1);int[] result=new int[names.size()];for(int i=0;i<result.length;i++)result[i]=ranks.get(names.get(i));
        return new Ranked(result,overflow?List.of("SOURCE_SEQUENCE_RANK_INTEGER_OVERFLOW: R provisional integer ranks contain NA; remaining rank order retains source C-locale missing-last behavior. Review chromosome names; no identifier repair occurred."):List.of());
    }
    public static List<String> levels(List<String> names){var unique=new ArrayList<>(new LinkedHashSet<>(names));var rank=rank(unique,SexChromosomeX.AUTO);int[] values=rank.ranks;List<Integer> indices=new ArrayList<>();for(int i=0;i<unique.size();i++)indices.add(i);indices.sort(Comparator.comparingInt(i->values[i]));return indices.stream().map(unique::get).toList();}
    private static Item classify(String name,String s,boolean sexual) {
        Integer roman=roman(s);if(roman!=null&&!(sexual&&s.equals("X")))return new Item(name,s,0,roman);
        int n=digits(s);if(n>0&&n<=6&&s.charAt(0)!='0'){
            String suffix=s.substring(n);int offset=List.of("","A","a","B","b","L","R").indexOf(suffix);if(offset>=0)return new Item(name,s,1,7L*Long.parseLong(s.substring(0,n))+offset);
        }
        int direct=switch(s){case "W"->2;case "Z"->3;case "X"->sexual?4:17;case "Y"->5;case "U"->6;case "M"->7;case "MT"->8;default->-1;};if(direct>=0)return new Item(name,s,direct,0);
        if(shortNumberPrefix(s))return new Item(name,s,9,0);
        for(var pair:List.of(Map.entry("W",10),Map.entry("Z",11),Map.entry("X",12),Map.entry("Y",13),Map.entry("U",14),Map.entry("MT",16),Map.entry("M",15)))if(s.startsWith(pair.getKey()))return new Item(name,s,pair.getValue(),0);
        return new Item(name,s,17,0);
    }
    private static int digits(String s){int i=0;while(i<s.length()&&s.charAt(i)>='0'&&s.charAt(i)<='9')i++;return i;}
    private static boolean shortNumberPrefix(String s){int n=digits(s);return n>0&&n<=6&&s.charAt(0)!='0'&&SHORT.matcher(s.substring(0,n)).matches();}
    private static Integer roman(String s){if(s.isEmpty()||!s.equals(s.toUpperCase(Locale.ROOT)))return null;String expanded=s.replace("CM","DCCCC").replace("CD","CCCC").replace("XC","LXXXX").replace("XL","XXXX").replace("IX","VIIII").replace("IV","IIII");if(!ROMAN.matcher(expanded).matches())return null;int result=0;for(char c:expanded.toCharArray())result+=switch(c){case 'M'->1000;case 'D'->500;case 'C'->100;case 'L'->50;case 'X'->10;case 'V'->5;case 'I'->1;default->throw new IllegalArgumentException("Unrecognized Roman numeral");};return result;}
}
