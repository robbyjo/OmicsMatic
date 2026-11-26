/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.broad.igv.tdf.*;
public class TdfDump {
  static String name(String text){return text==null?"NULL":Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));}
  public static void main(String[] args)throws Exception {
    TDFReader reader=TDFReader.getReader(args[0]);if(reader==null)throw new IllegalStateException("No original TDF reader");
    try(var out=Files.newBufferedWriter(Path.of(args[1]),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW)) {
      String[] tracks=reader.getTrackNames();out.write("TDF\t"+reader.getVersion()+"\t"+tracks.length+"\n");for(String track:tracks)out.write("TRACK\t"+name(track)+"\n");
      var datasets=new ArrayList<>(reader.getDatasetNames());Collections.sort(datasets);
      for(String label:datasets){var data=reader.getDataset(label);out.write("DATASET\t"+name(label)+"\t"+data.getDataType()+"\t"+data.getTileWidth()+"\n");for(var tile:data.getTiles()){
        if(tile==null)continue;out.write("TILE\t"+tile.getTileStart()+"\t"+tile.getSize()+"\n");for(int row=0;row<tile.getSize();row++){out.write("FEATURE\t"+tile.getStartPosition(row)+"\t"+tile.getEndPosition(row)+"\t"+name(tile.getName(row)));for(int t=0;t<tracks.length;t++)out.write("\t"+Float.toHexString(tile.getValue(t,row)));out.write("\n");}
      }}
    } finally {reader.close();}
    if(args.length==3)Files.writeString(Path.of(args[2]),Files.readString(Path.of("/proc/self/maps")),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
  }
}
