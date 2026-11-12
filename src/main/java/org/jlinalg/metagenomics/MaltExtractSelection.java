/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.metagenomics;

import java.io.*;
import java.util.*;

/** Source1.7 target selection, attributed to Ron Huebler's NCBI_MapReader /
 * NCBI_TreeReader / RMA6Processor in the GPL HOPS0.35 MaltExtract1.7 distribution
 * (jar SHA256 8a5be1b5ae43f329995a4647466a7ca3aa698d180cef94dec974ffe91bb3a2ca).
 * The source's changing-depth ancestor loop is preserved, not replaced by a
 * generic complete-subtree selection. Public method results are sets; the
 * original RMA6Processor also combines them in a set.
 */
public final class MaltExtractSelection {
    private MaltExtractSelection() { }
    public record MapLimits(long characters,long rows,int lineCharacters,int targets,int wantedIds) {
        public MapLimits {if(characters<1||rows<1||lineCharacters<1||targets<1||wantedIds<1)throw new IllegalArgumentException("Positive complete-map capacities required");}
    }
    public record Target(int index,String supplied,String lookup,Integer taxon,long sourceRow,String literalId,int matchingRows) {
        public Map<String,Object> document() {
            var value=new LinkedHashMap<String,Object>();value.put("index",index);value.put("supplied",supplied);value.put("lookup",lookup);value.put("taxon",taxon);value.put("source_row",sourceRow);value.put("literal_id",literalId);value.put("matching_rows",matchingRows);return value;
        }
    }
    public record Names(List<Target> targets,Map<Integer,String> displayNames,long rows,long characters,long extraColumnRows) {
        public Names {targets=List.copyOf(targets);displayNames=Collections.unmodifiableMap(new TreeMap<>(displayNames));}
        public Set<Integer> resolvedIds(){var ids=new TreeSet<Integer>();for(var target:targets)if(target.taxon()!=null)ids.add(target.taxon());return Collections.unmodifiableSet(ids);}
        public List<String> missingNames(){return targets.stream().filter(t->t.taxon()==null).map(Target::lookup).toList();}
        public Map<String,Object> document(){var out=new LinkedHashMap<String,Object>();out.put("targets",targets.stream().map(Target::document).toList());out.put("map_rows",rows);out.put("map_characters",characters);out.put("extra_column_rows",extraColumnRows);var names=new TreeMap<String,Object>();displayNames.forEach((id,name)->names.put(Integer.toString(id),name));out.put("display_names",names);return out;}
    }
    private record Hit(int taxon,long row,String literal,int matches) { }
    /** Exact Scanner.hasNext/nextLine, TAB splitting and Integer.parseInt behaviour
     * of the original map reader. Every row is consumed; only requested names and
     * IDs are retained. Name and ID collisions keep the last source assignment.
     * The source target-file reader trims each line; raw and lookup names remain
     * separately visible. Unknown targets are returned for an explicit admission
     * decision, never silently omitted from the requested list.
     */
    public static Names names(Reader input,List<String> suppliedNames,Set<Integer> requestedDisplayIds,MapLimits limits)throws IOException {
        Objects.requireNonNull(input);Objects.requireNonNull(suppliedNames);Objects.requireNonNull(requestedDisplayIds);Objects.requireNonNull(limits);
        if(suppliedNames.isEmpty()||suppliedNames.size()>limits.targets()||requestedDisplayIds.size()>limits.wantedIds())throw new IOException("Complete requested name/ID population exceeds capacity or is empty");
        var wanted=new HashSet<String>();for(String value:suppliedNames)wanted.add(Objects.requireNonNull(value).trim());
        var displayWanted=new HashSet<>(requestedDisplayIds);var display=new HashMap<Integer,String>();var hits=new HashMap<String,Hit>();
        CountingReader counted=new CountingReader(input,limits.characters());long rows=0,extra=0;
        try(var scanner=new Scanner(counted)) {
            while(scanner.hasNext()) {
                String line=scanner.nextLine();if(++rows>limits.rows()||line.length()>limits.lineCharacters())throw new IOException("Complete map row/line capacity exceeded");
                String[] fields=line.split("\t");if(fields.length<2)throw new IOException("Source map row lacks its literal ID/name fields at row"+rows);
                final int id;try{id=Integer.parseInt(fields[0]);}catch(NumberFormatException failure){throw new IOException("Source map integer field cannot be parsed at row"+rows,failure);}
                if(fields.length>2)extra++;
                String name=fields[1];if(wanted.contains(name)) {
                    Hit prior=hits.get(name);hits.put(name,new Hit(id,rows,fields[0],prior==null?1:Math.addExact(prior.matches(),1)));displayWanted.add(id);
                    if(displayWanted.size()>limits.wantedIds())throw new IOException("Complete selected display-ID population exceeds capacity");
                }
                if(displayWanted.contains(id))display.put(id,name);
            }
            if(scanner.ioException()!=null)throw new IOException("Original-style map scan did not consume a complete input",scanner.ioException());
        }
        var targets=new ArrayList<Target>();for(int i=0;i<suppliedNames.size();i++){String raw=suppliedNames.get(i),name=raw.trim();Hit hit=hits.get(name);targets.add(new Target(i,raw,name,hit==null?null:hit.taxon(),hit==null?0:hit.row(),hit==null?null:hit.literal(),hit==null?0:hit.matches()));}
        return new Names(targets,display,rows,counted.count,extra);
    }
    /** Immutable preorder index. A candidate is returned by the original loop
     * exactly when one of its descendant leaves is shallow enough to be reached
     * by that loop's changing depth: minLeafDepth <=2*candidateDepth-targetDepth+1.
     * This avoids the original leaves-times-classification-keys nested scan.
     */
    public static final class Model {
        private final int[] ids,depths,end,minLeaf;
        private final long[] lookup;
        public Model(MaltExtractTaxonomy tree) {
            Objects.requireNonNull(tree);ids=tree.ids();depths=new int[ids.length];end=new int[ids.length];minLeaf=new int[ids.length];lookup=new long[ids.length];Arrays.fill(minLeaf,Integer.MAX_VALUE);
            var stack=new ArrayDeque<Integer>();
            for(int i=0;i<ids.length;i++) {
                depths[i]=tree.depth(ids[i]);lookup[i]=((long)ids[i]<<32)|(i&0xffffffffL);
                while(!stack.isEmpty()&&depths[stack.peek()]>=depths[i])close(stack,i);
                if(tree.childCount(ids[i])==0)minLeaf[i]=depths[i];stack.push(i);
            }
            while(!stack.isEmpty())close(stack,ids.length);Arrays.sort(lookup);
        }
        private void close(ArrayDeque<Integer> stack,int after) {
            int at=stack.pop();end[at]=after;if(minLeaf[at]==Integer.MAX_VALUE)throw new IllegalArgumentException("Complete taxonomy node has no external descendant");if(!stack.isEmpty())minLeaf[stack.peek()]=Math.min(minLeaf[stack.peek()],minLeaf[at]);
        }
        private int at(int taxon) {
            int low=0,high=lookup.length;while(low<high){int middle=(low+high)>>>1;int id=(int)(lookup[middle]>>32);if(id<taxon)low=middle+1;else high=middle;}return low<lookup.length&&(int)(lookup[low]>>32)==taxon?(int)lookup[low]:-1;
        }
        public Set<Integer> sourceDescendants(int target,Set<Integer> keys)throws IOException {
            Objects.requireNonNull(keys);int query=at(target);if(query<0)throw new IOException("Source target is absent from the complete supplied taxonomy: "+target);
            var result=new TreeSet<Integer>();
            for(int key:keys) {
                int candidate=at(key);if(candidate<query||candidate>=end[query])continue;
                if(candidate==query&&end[query]!=query+1)continue;
                if((long)minLeaf[candidate]<=2L*depths[candidate]-depths[query]+1)result.add(key);
            }
            return Collections.unmodifiableSet(result);
        }
        /** RMA6Processor always includes each explicitly resolved target itself. */
        public Set<Integer> processedIds(Collection<Integer> targets,Set<Integer> keys)throws IOException {
            var result=new TreeSet<Integer>();for(int target:targets){result.add(target);result.addAll(sourceDescendants(target,keys));}return Collections.unmodifiableSet(result);
        }
    }
    private static final class CountingReader extends FilterReader {
        private final long maximum;private long count;
        CountingReader(Reader reader,long maximum){super(reader);this.maximum=maximum;}
        @Override public int read()throws IOException {int value=super.read();if(value!=-1&&++count>maximum)throw new IOException("Complete map character capacity exceeded");return value;}
        @Override public int read(char[] buffer,int offset,int length)throws IOException {int n=super.read(buffer,offset,length);if(n>0){count=Math.addExact(count,n);if(count>maximum)throw new IOException("Complete map character capacity exceeded");}return n;}
    }
}
