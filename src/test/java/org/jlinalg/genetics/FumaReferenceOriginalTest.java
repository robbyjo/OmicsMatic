package org.jlinalg.genetics;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

/** Reference preparation checked against actual original binary matrices/counts. */
class FumaReferenceOriginalTest {
    @TempDir static Path temp;
    private static String text(Map<String,byte[]> files,String name){return new String(files.get(name),StandardCharsets.UTF_8);}
    @TestFactory Stream<DynamicTest> builderUsesOriginalFounderFrequenciesLdAndWindowMembership()throws Exception{
        byte[] raw;try(var in=getClass().getResourceAsStream("hardcall-ld/original-controls.zip")){assertNotNull(in);raw=in.readNBytes(200000);assertEquals(-1,in.read());}
        assertEquals(140388,raw.length);assertEquals("06e002a1c8c23e462176d32726abaedcb6b71fae3a6f9f349959a6befed04719",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)));
        Map<String,byte[]> files=new HashMap<>();try(var zip=new ZipInputStream(new ByteArrayInputStream(raw))){long total=0;for(var e=zip.getNextEntry();e!=null;e=zip.getNextEntry()){assertFalse(e.isDirectory());assertFalse(e.getName().contains(".."));byte[] b=zip.readNBytes(1048577);total+=b.length;assertTrue(b.length<=1048576&&total<8388608);assertNull(files.put(e.getName(),b));}}
        return List.of("word-63","word-64","word-65","word-129","word-2504","explicit-keep","window-2","window-3","window-99999").stream().map(name->DynamicTest.dynamicTest(name,()->compare(files,name)));
    }
    private static void compare(Map<String,byte[]> files,String name)throws Exception{
        Path folder=Files.createDirectory(temp.resolve(name));for(String ext:List.of("bed","bim","fam"))Files.write(folder.resolve("input."+ext),files.get(name+"/input."+ext));Set<String> ids=new HashSet<>();text(files,name+"/input.bim").lines().forEach(line->ids.add(line.split("\\s+")[1]));Set<String> keep=new HashSet<>();if(files.containsKey(name+"/input.keep"))text(files,name+"/input.keep").lines().forEach(line->{var f=line.split("\\s+");keep.add(f[0]+"\t"+f[1]);});
        try(var data=PlinkBedDataset.open(folder.resolve("input"),ids)){
            var samples=data.samples();int[] selection=IntStream.range(0,samples.size()).filter(i->samples.get(i).founder()&&(keep.isEmpty()||keep.contains(samples.get(i).familyId()+"\t"+samples.get(i).individualId()))).toArray();var frame=HardcallLd.frame("actual-original-"+name,samples.size(),selection,HardcallLd.Limits.defaults(),()->{});var variants=data.selectedVariants();int n=variants.size();double[][] matrix=new double[n][n];var binary=ByteBuffer.wrap(files.get(name+"/original.ld.bin")).order(ByteOrder.LITTLE_ENDIAN);for(int i=0;i<n;i++)for(int j=0;j<n;j++)matrix[i][j]=binary.getDouble();assertFalse(binary.hasRemaining());
            double[] maf=new double[n];var frqx=text(files,name+"/original.frqx").lines().skip(1).toList();var retained=new ArrayList<PlinkBedDataset.Variant>();for(int i=0;i<n;i++){var f=frqx.get(i).split("\t");long a=Long.parseLong(f[4]),het=Long.parseLong(f[5]),b=Long.parseLong(f[6]);assertEquals(variants.get(i).id(),f[1]);maf[i]=(double)Math.min(2*a+het,2*b+het)/(2*(a+het+b));if(Double.isFinite(matrix[i][i])&&maf[i]>=.02)retained.add(variants.get(i));}
            int gap=name.startsWith("window-")?Integer.parseInt(name.substring(7))-1:3;long bp=name.startsWith("window-")?1000:500;double floor=name.startsWith("window-")?1:.05;
            var expected=new HashMap<String,Double>();for(int i=0;i<retained.size();i++)for(int j=i+1;j<retained.size();j++){var a=retained.get(i);var b=retained.get(j);if(a.chromosome().equals(b.chromosome())&&b.position()-a.position()<=bp&&j-i<=gap)expected.put(a.id()+"/"+b.id(),matrix[(int)a.ordinal()][(int)b.ordinal()]);}
            var observed=new HashMap<String,HardcallLd.Pair>();var stored=new HashSet<String>();var sink=new FumaReferencePreparation.Sink(){public void capture(PlinkBedDataset.Variant v,byte[] calls){}public void pair(PlinkBedDataset.Variant a,PlinkBedDataset.Variant b,HardcallLd.Pair r,boolean s){assertNull(observed.put(a.id()+"/"+b.id(),r));if(s)assertTrue(stored.add(a.id()+"/"+b.id()));}};
            var options=new FumaReferencePreparation.Options(.02,floor,new FumaLocusDefinition.LdWindow(bp,gap));
            if(expected.values().stream().anyMatch(value->Double.isNaN(value))){
                var failure=assertThrows(IllegalArgumentException.class,()->FumaReferencePreparation.prepare(variants,frame,data::readPackedRow,options,FumaReferencePreparation.Limits.defaults(),sink,()->{}));assertTrue(failure.getMessage().contains("Undefined eligible LD pair"));assertTrue(observed.values().stream().anyMatch(p->p.status()!=HardcallLd.Status.DEFINED));
                for(var e:observed.entrySet()){double source=expected.get(e.getKey());if(Double.isNaN(source)){assertNotEquals(HardcallLd.Status.DEFINED,e.getValue().status());assertFalse(stored.contains(e.getKey()));}else assertEquals(source,e.getValue().rSquared(),3e-15+1e-12*Math.abs(source));}
                return; // Retain source NaN; do not widen filtering to make this a success.
            }
            var r=FumaReferencePreparation.prepare(variants,frame,data::readPackedRow,options,FumaReferencePreparation.Limits.defaults(),sink,()->{});
            assertEquals(retained.stream().map(PlinkBedDataset.Variant::id).toList(),r.variants().stream().map(FumaLocusDefinition.ReferenceVariant::rsId).toList());for(var v:r.variants()){var original=variants.stream().filter(x->x.id().equals(v.rsId())).findFirst().orElseThrow();assertEquals(maf[(int)original.ordinal()],v.maf());}assertEquals(expected.keySet(),observed.keySet());for(var e:expected.entrySet()){assertEquals(e.getValue(),observed.get(e.getKey()).rSquared(),3e-15+1e-12*Math.abs(e.getValue()));assertEquals(e.getValue()>=floor,stored.contains(e.getKey()));}assertEquals(expected.size(),r.assessedPairs());assertEquals(stored.size(),r.storedPairs());
            if(name.startsWith("window-")){Set<String> source=new HashSet<>();text(files,name+"/window.ld").lines().skip(1).forEach(line->{var f=line.strip().split("\\s+");source.add(f[2]+"/"+f[5]);});assertEquals(source,stored);}
        }
    }
}
