package org.jlinalg.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Synthetic QC contrast: controls versus all, unresolved/all-missing calls, pedigree, order. */
final class PlinkQcFixture {
    private PlinkQcFixture() { }
    record Input(Path prefix,Path samples,List<String[]> fam) { }
    static void set(byte[] bed,int variant,int sample,int genotype){int offset=3+variant*32+sample/4,shift=2*(sample%4),code=genotype<0?1:genotype==2?0:genotype==1?2:3;bed[offset]=(byte)((bed[offset]&~(3<<shift))|(code<<shift));}
    static Input create(Path directory,boolean orderDifference,boolean noControlCalls,boolean finalFailure) throws Exception {
        var fixture=PlinkGlmInputsTest.fixture(directory,"linear","quantitative",false);Path prefix=fixture.prefix();byte[] bed=Files.readAllBytes(Path.of(prefix+".bed"));
        for(int v=0;v<96;v++)for(int i=0;i<128;i++){
            if(v==0)set(bed,v,i,i%2==0?1:i%4==1?0:2);
            if(v==1)set(bed,v,i,i%2==0?(i%8==0?0:i%8==2?2:1):1);
            if(v==2)set(bed,v,i,-1);if(v==3)set(bed,v,i,0);if(v==4)set(bed,v,i,i==8?1:0);
            if(orderDifference&&v>=5&&v<=10)set(bed,v,i,0);
            if(finalFailure&&v>=15)set(bed,v,i,0);
            if(noControlCalls&&v==12&&i%2==0)set(bed,v,i,-1);
            if(i<2&&v<45)set(bed,v,i,-1);
            if(orderDifference&&i==4&&v<=12)set(bed,v,i,-1);
            if(finalFailure&&i==6&&(v==5||v==6))set(bed,v,i,-1);
        }
        var fam=new ArrayList<String[]>();for(String line:Files.readAllLines(Path.of(prefix+".fam")))fam.add(line.split("\\s+"));fam.get(10)[2]="S008";fam.get(10)[3]="S009";fam.get(12)[2]="UNSAMPLED_FATHER";fam.get(2)[4]="0";
        String[][] pairs={{"A_B","C"},{"A","B_C"},{"家族","成员"}};for(int i=0;i<3;i++){fam.get(i)[0]=pairs[i][0];fam.get(i)[1]=pairs[i][1];}
        Files.writeString(Path.of(prefix+".fam"),String.join("\n",fam.stream().map(f->String.join(" ",f)).toList())+"\n");var bim=new ArrayList<String[]>();for(String line:Files.readAllLines(Path.of(prefix+".bim")))bim.add(line.split("\\s+"));bim.get(3)[4]="0";bim.get(95)[0]="X";Files.writeString(Path.of(prefix+".bim"),String.join("\n",bim.stream().map(f->String.join(" ",f)).toList())+"\n");Files.write(Path.of(prefix+".bed"),bed);
        Path samples=directory.resolve("qc-samples.tsv");var text=new StringBuilder("fid\tiid\tinclude\tgroup\n");for(int i=0;i<128;i++)text.append(fam.get(i)[0]).append('\t').append(fam.get(i)[1]).append('\t').append(i==127?"no":"yes").append('\t').append(i%2==0?"control":"case").append('\n');Files.writeString(samples,text);return new Input(prefix,samples,List.copyOf(fam));
    }
}
