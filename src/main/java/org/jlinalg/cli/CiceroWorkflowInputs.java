/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import static org.jlinalg.cli.Monocle3WorkflowRequest.*;

/** Literal sparse/dense input intake and declared raw-to-original genomic/name mapping. */
final class CiceroWorkflowInputs {
    private CiceroWorkflowInputs() { }
    static final long EXACT_INTEGER=9007199254740991L;
    static final Comparator<String> C_ORDER=(a,b)->Arrays.compareUnsigned(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));
    record Peak(String id,String chromosome,long start,long end){String sourceChromosome(){return chromosome.replace("chr","");}}
    record Genome(String chromosome,long length){String sourceChromosome(){return chromosome.replace("chr","");}}
    record Population(List<Peak> peaks,List<String> cells,List<Genome> genome,double[][] embedding,double[][] metadata,Map<Long,Double> counts,long storedEntries,long explicitZeros,long zeroPeaks){
        double count(int peak,int cell,boolean binarize){double x=counts.getOrDefault((long)peak*cells.size()+cell,0.0);return binarize&&x>0?1:x;}
    }
    static Population validate(Path inputs,CiceroWorkflowRequest.Request r)throws IOException {
        var genomeRows=tsv(inputs.resolve("genome.tsv"),r);require(Arrays.equals(genomeRows.get(0),new String[]{"chromosome","length"}),"Require chromosome<TAB>length genome header");var genome=new ArrayList<Genome>();var lengths=new HashMap<String,Long>();
        for(int i=1;i<genomeRows.size();i++){var row=genomeRows.get(i);require(row.length==2,"Ragged genome");var g=new Genome(row[0],coordinate(row[1],1,2147483647));require(!g.sourceChromosome().isEmpty()&&lengths.putIfAbsent(g.sourceChromosome(),g.length())==null,"Duplicate original normalized chromosome; no alias merging");genome.add(g);}
        require(!genome.isEmpty()&&genome.size()<=100000,"Require bounded explicit chromosome lengths");
        var rows=tsv(inputs.resolve("peaks.tsv"),r);require(Arrays.equals(rows.get(0),new String[]{"peak_id","chromosome","start","end"}),"Require peak_id chromosome start end metadata");require(rows.size()>=3&&rows.size()-1<=r.maximumPeaks(),"Peak population outside capacity");var peaks=new ArrayList<Peak>();var ids=new HashSet<String>();var name=Pattern.compile("^(.*)[:_-]([0-9]+)[:_-]([0-9]+)$");
        for(int i=1;i<rows.size();i++){var row=rows.get(i);require(row.length==4&&ids.add(row[0]),"Ragged/duplicate peak metadata");long start=coordinate(row[2],r.coordinates().equals("one-based-closed")?1:0,2147483647),end=coordinate(row[3],1,2147483647);require(r.coordinates().equals("one-based-closed")?end>=start:end>start,"Invalid declared peak interval");var p=new Peak(row[0],row[1],start,end);var m=name.matcher(p.id());require(m.matches()&&m.group(1).equals(p.chromosome())&&coordinate(m.group(2),0,2147483647)==start&&coordinate(m.group(3),0,2147483647)==end,"Peak ID must encode the exact declared chromosome/endpoints using original : - or _ separators; no renaming/conversion");require(lengths.containsKey(p.sourceChromosome())&&end<=lengths.get(p.sourceChromosome()),"Peak chromosome/end outside explicit genome");peaks.add(p);}
        var cellRows=tsv(inputs.resolve("cells.tsv"),r);var header=new ArrayList<String>();header.add("cell_id");header.addAll(r.numericMetadata());require(Arrays.asList(cellRows.get(0)).equals(header),"Cell header must match declared numeric metadata order");require(cellRows.size()>=4&&cellRows.size()-1<=r.maximumCells(),"Cell population outside capacity");var cells=new ArrayList<String>();ids.clear();double[][] metadata=new double[cellRows.size()-1][r.numericMetadata().size()];
        for(int i=1;i<cellRows.size();i++){var row=cellRows.get(i);require(row.length==header.size()&&ids.add(row[0]),"Ragged/duplicate literal cell ID");cells.add(row[0]);for(int j=1;j<row.length;j++)metadata[i-1][j-1]=numeric(row[j]);}
        var coords=tsv(inputs.resolve("embedding.tsv"),r);require(coords.size()==cellRows.size(),"Embedding and cell populations differ");require(coords.get(0).length>=3&&coords.get(0).length<=51&&coords.get(0)[0].equals("cell_id"),"Embedding needs cell_id and2..50 explicit dimensions");require(new HashSet<>(Arrays.asList(coords.get(0))).size()==coords.get(0).length,"Duplicate embedding headers");double[][] embedding=new double[cells.size()][coords.get(0).length-1];
        for(int i=1;i<coords.size();i++){var row=coords.get(i);require(row.length==embedding[0].length+1&&row[0].equals(cells.get(i-1)),"Embedding cell IDs must match metadata exactly, without reordering");for(int j=1;j<row.length;j++){embedding[i-1][j-1]=numeric(row[j]);require(Math.abs(embedding[i-1][j-1])<=1e150,"Embedding coordinates exceed finite squared-distance domain");}}
        var counts=new HashMap<Long,Double>();long[] rowTotals=new long[peaks.size()];double[] colTotals=new double[cells.size()];long[] entries={0,0};
        if(r.format().equals("dense-tsv")){
            require((long)peaks.size()*cells.size()<=r.inputEntries(),"Dense matrix exceeds maximum_input_entries");var matrix=tsv(inputs.resolve("counts"),r);require(matrix.size()==peaks.size()+1,"Dense peak population differs");String[] h=matrix.get(0);require(h.length==cells.size()+1&&h[0].equals("peak_id")&&Arrays.asList(h).subList(1,h.length).equals(cells),"Dense count cell IDs must match metadata order");
            for(int i=1;i<matrix.size();i++){var row=matrix.get(i);require(row.length==h.length&&row[0].equals(peaks.get(i-1).id()),"Dense peak IDs must match metadata order");for(int j=1;j<row.length;j++)entry(counts,rowTotals,colTotals,entries,i-1,j-1,row[j],cells.size());}
        }else{
            var lines=content(inputs.resolve("counts"),r).lines().iterator();require(lines.hasNext(),"Missing Matrix Market banner");String banner=lines.next();require(Set.of("%%MatrixMarket matrix coordinate integer general","%%MatrixMarket matrix coordinate real general").contains(banner),"Require general coordinate integer/real Matrix Market; no expansion or implicit aggregation");String dimensions=next(lines);var shape=dimensions.trim().split("\\s+");require(shape.length==3,"Require Matrix Market dimensions");long nr=coordinate(shape[0],2,r.maximumPeaks()),nc=coordinate(shape[1],3,r.maximumCells()),nnz=coordinate(shape[2],0,r.inputEntries());require(nr==peaks.size()&&nc==cells.size(),"Sparse axes differ from ordered metadata");
            while(lines.hasNext()){String line=lines.next();if(line.startsWith("%"))continue;require(!line.isBlank()&&entries[0]<nnz,"Excess/blank sparse entry");var f=line.trim().split("\\s+");require(f.length==3,"Require row column value sparse entry");entry(counts,rowTotals,colTotals,entries,(int)coordinate(f[0],1,nr)-1,(int)coordinate(f[1],1,nc)-1,f[2],cells.size());}require(entries[0]==nnz,"Sparse entry count differs");
        }
        for(double total:colTotals)require(total>0&&Double.isFinite(total),"All-zero/nonfinite cell total refused; no cell deletion or imputation");
        for(var a:r.analyses()){
            require(integer(a.get("k"),2,1000000)<=cells.size(),"Aggregation k exceeds cell population");long window=integer(object(a.get("model")).get("window"),2,1000000000),windows=0;for(var g:genome){require(g.length()+window-1<=2147483647L,"Original IRanges window endpoints overflow");windows+=2*(g.length()-1)/window+1;}require(windows<=r.windows(),"Generated windows exceed maximum_windows");
        }
        return new Population(List.copyOf(peaks),List.copyOf(cells),List.copyOf(genome),embedding,metadata,Collections.unmodifiableMap(counts),entries[0],entries[1],Arrays.stream(rowTotals).filter(x->x==0).count());
    }
    private static void entry(Map<Long,Double> counts,long[] rows,double[] cols,long[] entries,int row,int col,String raw,int nc)throws IOException {
        double value=Monocle2DifferentialInputs.numeric(raw,"raw-integer-counts");long integer=(long)value;require(integer<=EXACT_INTEGER-rows[row],"Per-peak count sum exceeds exact binary64 integer domain");require(counts.putIfAbsent((long)row*nc+col,value)==null,"Duplicate sparse coordinate refused; no implicit summation");rows[row]+=integer;cols[col]+=value;entries[0]++;if(value==0)entries[1]++;
    }
    static double numeric(String raw)throws IOException{return Monocle2DifferentialInputs.numeric(raw,"continuous");}
    static long coordinate(String raw,long min,long max)throws IOException {require(raw.matches("[0-9]+"),"Require literal nonnegative genomic/index integer");try{long n=Long.parseLong(raw);require(n>=min&&n<=max,"Genomic/index integer outside declared domain");return n;}catch(NumberFormatException e){throw new IOException("Genomic/index integer overflow",e);}}
    private static String next(Iterator<String> lines)throws IOException {while(lines.hasNext()){String s=lines.next();if(!s.startsWith("%"))return s;}throw new IOException("Missing Matrix Market dimensions");}
    static String content(Path path,CiceroWorkflowRequest.Request r)throws IOException {String s=NextflowFiles.utf8(NextflowFiles.bytes(path,(int)r.inputBytes())).replace("\r\n","\n");require(!s.contains("\r"),"Require LF or CRLF records");return s;}
    static List<String[]> tsv(Path path,CiceroWorkflowRequest.Request r)throws IOException {var out=new ArrayList<String[]>();for(String line:content(path,r).lines().toList()){require(!line.isEmpty(),"Blank TSV record");var fields=line.split("\t",-1);for(String field:fields)label(field);out.add(fields);require(out.size()<=Math.max(r.maximumPeaks(),r.maximumCells())+1,"TSV row capacity exceeded");}require(!out.isEmpty(),"Empty TSV");return out;}
}
