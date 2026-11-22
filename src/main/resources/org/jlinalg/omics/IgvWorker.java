/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
// Launched by a private Java21 source launcher against authenticated original IGV jars.
// Original IGV classes/algorithms are not patched or placed in the project's classpath.
import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import javax.swing.*;
import com.google.gson.*;
import org.broad.igv.*;
import org.broad.igv.batch.*;
import org.broad.igv.feature.BasicFeature;
import org.broad.igv.feature.genome.GenomeManager;
import org.broad.igv.prefs.*;
import org.broad.igv.renderer.*;
import org.broad.igv.sam.*;
import org.broad.igv.track.*;
import org.broad.igv.ui.*;
import org.broad.igv.ui.panel.FrameManager;
import org.broad.igv.ui.util.SnapshotUtilities;
import org.broad.igv.util.ResourceLocator;

public final class IgvWorker {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().create();
    private static final class HiddenFrame extends JFrame {
        private static final long serialVersionUID=1L;
        @Override public void setVisible(boolean visible) { }
    }
    private record Group(JsonObject request,List<Track> tracks,Map<Track,String> samples) { }
    public static void main(String[] args)throws Exception {
        if(Runtime.version().feature()!=21)throw new IOException("The admitted original IGV profile requires a full Java21 JDK");
        if(args.length==1&&args[0].equals("--probe")) {
            if(GraphicsEnvironment.isHeadless())throw new IOException("A usable Windows Java graphics environment is unavailable");
            SwingUtilities.invokeAndWait(()->{HiddenFrame frame=new HiddenFrame();frame.dispose();});
            System.out.println(JSON.toJson(Map.of("igv",Globals.VERSION,"build",Globals.BUILD,"java_feature",Runtime.version().feature(),"java_home",System.getProperty("java.home"),"java_version",System.getProperty("java.version"),"os_arch",System.getProperty("os.arch"),"headless",false)));return;
        }
        if(args.length!=1)throw new IOException("Require one trusted native-generated UTF8 worker recipe");
        JsonObject recipe=JsonParser.parseString(Files.readString(Path.of(args[0]),StandardCharsets.UTF_8)).getAsJsonObject();Path directory=Path.of(text(recipe,"output_directory"));
        var blocked=Collections.synchronizedList(new ArrayList<String>());
        URL.setURLStreamHandlerFactory(protocol->Set.of("http","https","ftp").contains(protocol)?new URLStreamHandler(){
            @Override protected URLConnection openConnection(URL url)throws IOException {blocked.add(url.toExternalForm());throw new IOException("Local IGV profile forbids network access");}
        }:null);
        Throwable[] failure={null};
        SwingUtilities.invokeAndWait(()->{
            HiddenFrame frame=null;
            try {
                if(GraphicsEnvironment.isHeadless())throw new IOException("Windows graphics environment unavailable");
                Globals.setBatch(true);Globals.setSuppressMessages(true);Path igvHome=Files.createDirectories(directory.resolve("igv-private"));DirectoryManager.setIgvDirectory(igvHome.toFile());
                PreferencesManager.getPreferences();Path preferences=directory.resolve("igv-preferences.properties");StringBuilder prefs=new StringBuilder("PORT_ENABLED=false\nAUTOSAVES_TO_KEEP=0\nAUTOSAVE_ON_EXIT=false\nAUTOLOAD_LAST_AUTOSAVE=false\nSHOW_GENOME_SERVER_WARNING=false\nENABLE_GOOGLE_MENU=false\nPROVISIONING_URL_DEFAULT=\nDEFAULT_GENOME_KEY=").append(text(recipe,"reference")).append('\n');
                prefs.append("SAM.DOWNSAMPLE_READS=false\nSAM.MAX_VISIBLE_RANGE=1000\nSAM.SHOW_COV_TRACK=true\nSESSION.RELATIVE_PATH=true\n");
                // SequenceTrack overrides isVisible() and ignores the inherited
                // visibility flag. Its actual public resolution preference is
                // therefore required to implement an explicit show/hide choice.
                prefs.append("MAX_SEQUENCE_RESOLUTION=").append(bool(recipe,"sequence")?Integer.MAX_VALUE:0).append('\n');
                if(recipe.has("reads")) {
                    JsonObject r=recipe.getAsJsonObject("reads");prefs.append("SAM.QUALITY_THRESHOLD=").append(number(r,"minimum_mapq")).append('\n');
                    for(String[] pair:new String[][]{{"duplicates","SAM.FILTER_DUPLICATES"},{"failed","SAM.FILTER_FAILED_READS"},{"secondary","SAM.FILTER_SECONDARY_ALIGNMENTS"},{"supplementary","SAM.FILTER_SUPPLEMENTARY_ALIGNMENTS"}})prefs.append(pair[1]).append('=').append(!bool(r,pair[0])).append('\n');
                    prefs.append("SAM.SHOW_SOFT_CLIPPED=").append(bool(r,"soft_clips")).append("\nSAM.SHOW_JUNCTION_TRACK=").append(bool(r,"junctions")).append("\nSAM.COUNT_DELETED_BASES_COVERED=").append(bool(r,"deletions_in_coverage")).append("\nSAM.SHOW_ALL_BASES=").append(text(r,"color_by").equals("base")).append('\n');
                }
                Files.writeString(preferences,prefs,StandardCharsets.UTF_8);PreferencesManager.loadOverrides(preferences.toString());frame=new HiddenFrame();Main.open(frame);frame.setSize(number(recipe,"width"),number(recipe,"height"));frame.validate();IGV igv=IGV.getInstance();
                var groups=new ArrayList<Group>();int maximum=number(recipe,"maximum_records");
                for(JsonElement element:recipe.getAsJsonArray("tracks")) {
                    JsonObject request=element.getAsJsonObject();String id=text(request,"id"),kind=text(request,"kind");Set<Track> before=Collections.newSetFromMap(new IdentityHashMap<>());before.addAll(igv.getAllTracks());ResourceLocator locator=new ResourceLocator(text(request,"file"));if(!kind.equals("seg"))locator.setName(id);if(request.has("index")&&!Path.of(text(request,"index")).equals(Path.of(text(request,"file")+".bai")))throw new IOException("Native-staged explicit BAM index must be adjacent for portable original sessions");igv.loadResources(List.of(locator));List<Track> added=igv.getAllTracks().stream().filter(t->!before.contains(t)).toList();if(added.isEmpty())throw new IOException("Original IGV loaded no tracks:"+id);var samples=new IdentityHashMap<Track,String>();
                    for(Track track:added) {
                        String sample=kind.equals("seg")?track.getName():"";samples.put(track,sample);String role=role(track);track.setName(id+"/"+(sample.isEmpty()?role:sample));track.setVisible(!role.equals("junction")||recipe.has("reads")&&bool(recipe.getAsJsonObject("reads"),"junctions"));track.setHeight(number(request,"height"),true);track.setDisplayMode(Track.DisplayMode.valueOf(text(request,"display").toUpperCase(Locale.ROOT)));track.setColor(color(request.getAsJsonArray("color")));track.setAltColor(color(request.getAsJsonArray("negative_color")));
                        if(track instanceof AbstractTrack numeric)numeric.setAutoScale(false);
                        if(track instanceof DataSourceTrack data&&!kind.equals("seg"))data.setWindowFunction(WindowFunction.none);
                        if(request.getAsJsonArray("range").size()==2&&!(track instanceof AlignmentTrack)&&!role.equals("junction")) {
                            float lo=request.getAsJsonArray("range").get(0).getAsFloat(),hi=request.getAsJsonArray("range").get(1).getAsFloat();track.setDataRange(new DataRange(lo,0,hi));
                            if(kind.equals("seg"))track.setColorScale(new ContinuousColorScale(lo,0,hi,color(request.getAsJsonArray("negative_color")),Color.WHITE,color(request.getAsJsonArray("color"))));
                        }
                        if(track instanceof AlignmentTrack alignment){JsonObject r=recipe.getAsJsonObject("reads");alignment.getRenderOptions().setViewPairs(bool(r,"pairs"));alignment.setColorOption(text(r,"color_by").equals("strand")?AlignmentTrack.ColorOption.READ_STRAND:AlignmentTrack.ColorOption.NONE);alignment.setVisibilityWindow(1_000_000);}
                    }
                    groups.add(new Group(request,added,samples));
                }
                for(Track track:igv.getAllTracks())if(track instanceof SequenceTrack)track.setVisible(bool(recipe,"sequence"));
                CommandExecutor commands=new CommandExecutor(igv,directory.toString());SnapshotUtilities.setMaxPanelHeight(number(recipe,"height"));var views=new ArrayList<Map<String,Object>>();
                for(JsonElement element:recipe.getAsJsonArray("views")) {
                    JsonObject requested=element.getAsJsonObject();String id=text(requested,"id"),contig=text(requested,"contig");int start=number(requested,"start"),end=number(requested,"end");BatchRunner.setIsBatchMode(true);String response=commands.execute("goto "+contig+":"+(start+1)+"-"+end);if(!response.equalsIgnoreCase("OK"))throw new IOException("Original view command failed:"+response);frame.validate();
                    Path image=directory.resolve(id+".png");String snapshot=igv.createSnapshotNonInteractive(igv.getMainPanel(),image.toFile(),true);if(snapshot!=null&&!snapshot.equalsIgnoreCase("OK"))throw new IOException("Original snapshot failed:"+snapshot);
                    var view=FrameManager.getDefaultFrame();var row=new LinkedHashMap<String,Object>();row.put("id",id);row.put("contig",view.getChrName());row.put("start",view.getOrigin());row.put("end",view.getEnd());row.put("scale",view.getScale());row.put("width",view.getWidthInPixels());row.put("reference_sequence_sha256",sequence(contig,start,end));var tracks=new ArrayList<Map<String,Object>>();
                    for(Group group:groups)for(Track track:group.tracks) {
                        JsonObject request=group.request;String kind=text(request,"kind"),trackId=text(request,"id");var item=new LinkedHashMap<String,Object>();item.put("track_id",trackId);item.put("sample",group.samples.get(track));item.put("role",role(track));item.put("name",track.getName());item.put("original_id",track.getId());item.put("class",track.getClass().getName());item.put("height",track.getHeight());item.put("display",track.getDisplayMode().toString());item.put("visible",track.isVisible());item.put("color",rgb(track.getColor()));item.put("negative_color",rgb(track.getAltColor()));if(track instanceof AbstractTrack a)item.put("auto_scale",a.getAutoScale());DataRange range=track.getDataRange();if(range!=null)item.put("range",List.of(range.getMinimum(),range.getBaseline(),range.getMaximum()));
                        item.put("track_type",track.getTrackType().toString());item.put("renderer",track.getRenderer()==null?"track-owned-render-method":track.getRenderer().getClass().getName());
                        if(kind.equals("seg")){float lo=request.getAsJsonArray("range").get(0).getAsFloat(),hi=request.getAsJsonArray("range").get(1).getAsFloat();var palette=track.getColorScale();item.put("palette",Map.of("minimum",palette.getMinimum(),"maximum",palette.getMaximum(),"negative_start",palette.getNegStart(),"positive_start",palette.getPosStart(),"minimum_color",rgb(palette.getMinColor()),"middle_color",rgb(palette.getMidColor()),"maximum_color",rgb(palette.getMaxColor()),"double_gradient",palette.isUseDoubleGradient()));item.put("color_scale_at_min_zero_max",List.of(rgb(palette.getColor(lo)),rgb(palette.getColor(0)),rgb(palette.getColor(hi))));item.put("rendered_score_at_0_1_2",List.of(track.logScaleData(0),track.logScaleData(1),track.logScaleData(2)));}
                        if(track instanceof DataSourceTrack data) {
                            item.put("window",String.valueOf(data.getWindowFunction()));List<org.broad.igv.feature.LocusScore> scores=data.getSummaryScores(contig,start,end,view.getZoom()).getFeatures();if(scores==null)scores=List.of();Path values=directory.resolve(id+"-"+trackId+"-"+tracks.size()+"-signal.tsv");int count=0;
                            try(var out=Files.newBufferedWriter(values,StandardCharsets.UTF_8)){out.write("start\tend\tvalue\n");for(var score:scores){if(score.getEnd()<=start||score.getStart()>=end)continue;if(++count>maximum||!Float.isFinite(score.getScore()))throw new IOException("Nonfinite/excessive original signal values");out.write(score.getStart()+"\t"+score.getEnd()+"\t"+Float.toString(score.getScore())+"\n");}}item.put("signal_file",values.getFileName().toString());item.put("returned_signal_intervals",count);
                        }
                        if(kind.equals("bed6")&&track instanceof FeatureTrack features) {
                            var found=new ArrayList<Map<String,Object>>();for(Object value:features.getFeatures(contig,start,end)){if(found.size()>=maximum)throw new IOException("Original feature count exceeds bound");var feature=(htsjdk.tribble.Feature)value;var f=new LinkedHashMap<String,Object>();f.put("contig",feature.getContig());f.put("start",feature.getStart());f.put("end",feature.getEnd());if(feature instanceof BasicFeature basic){f.put("name",basic.getName());f.put("strand",basic.getStrand().toString());f.put("score",basic.getScore());}found.add(f);}item.put("features",found);
                        }
                        if(track instanceof AlignmentTrack alignment) {
                            JsonObject r=recipe.getAsJsonObject("reads");item.put("view_pairs",alignment.getRenderOptions().isViewPairs());item.put("color_by",alignment.getRenderOptions().getColorOption().toString());var interval=alignment.getDataManager().getLoadedInterval(view,true);if(interval==null)throw new IOException("Original BAM interval was not loaded for requested view");if(!interval.getDownsampledIntervals().isEmpty())throw new IOException("Original downsampled reads despite explicit policy");var observations=new ArrayList<Map<String,Object>>();
                            for(Alignment a:interval.getAlignments()){if(a.getAlignmentEnd()<=start||a.getAlignmentStart()>=end)continue;if(observations.size()>=maximum)throw new IOException("Original loaded-read count exceeds bound");var observed=new LinkedHashMap<String,Object>();observed.put("name",a.getReadName());observed.put("start",a.getAlignmentStart());observed.put("end",a.getAlignmentEnd());observed.put("sequence",a.getReadSequence());observed.put("cigar",a.getCigarString());observed.put("mapq",a.getMappingQuality());observed.put("negative",a.isNegativeStrand());observed.put("paired",a.isPaired());observed.put("first",a.isFirstOfPair());observed.put("second",a.isSecondOfPair());observed.put("duplicate",a.isDuplicate());observed.put("secondary",!a.isPrimary());observed.put("supplementary",a.isSupplementary());observed.put("failed",a.isVendorFailedRead());for(String tag:List.of("MM","Mm","ML","Ml","MN","SA","HP","PS")){Object tagValue=a.getAttribute(tag);if(tagValue instanceof byte[] bytes)observed.put(tag,Base64.getEncoder().encodeToString(bytes));else if(tagValue!=null)observed.put(tag,tagValue);}observations.add(observed);}
                            item.put("alignments",observations);Path counts=directory.resolve(id+"-"+trackId+"-counts.tsv");AlignmentCounts data=interval.getCounts();if(!data.hasBaseCounts()||data.getBucketSize()!=1)throw new IOException("Original alignment counts are not base-resolved");
                            try(var out=Files.newBufferedWriter(counts,StandardCharsets.US_ASCII)){out.write("position\ttotal\tpositive\tnegative\tA\tC\tG\tT\tN\tdeletion\tinsertion\n");for(int pos=start;pos<end;pos++)out.write(pos+"\t"+data.getTotalCount(pos)+"\t"+data.getTotalPositiveCount(pos)+"\t"+data.getTotalNegativeCount(pos)+"\t"+data.getCount(pos,(byte)'A')+"\t"+data.getCount(pos,(byte)'C')+"\t"+data.getCount(pos,(byte)'G')+"\t"+data.getCount(pos,(byte)'T')+"\t"+data.getCount(pos,(byte)'N')+"\t"+data.getDelCount(pos)+"\t"+data.getInsCount(pos)+"\n");}item.put("counts_file",counts.getFileName().toString());
                        }
                        tracks.add(item);
                    }
                    row.put("tracks",tracks);views.add(row);
                    igv.saveSession(directory.resolve(id+".session.xml").toFile());
                }
                if(frame.isShowing()||!blocked.isEmpty())throw new IOException("Original hidden/local runtime boundary changed");var result=new LinkedHashMap<String,Object>();result.put("status","original-execution-completed-requires-native-audit");result.put("igv_version",Globals.VERSION);result.put("igv_build",Globals.BUILD);result.put("frame_showing",frame.isShowing());result.put("frame_width",frame.getWidth());result.put("frame_height",frame.getHeight());result.put("network_attempts_blocked",blocked);result.put("views",views);result.put("actual_preferences",preferences());var dictionary=new LinkedHashMap<String,Integer>();var genome=GenomeManager.getInstance().getCurrentGenome();for(String chromosome:genome.getChromosomeNames())if(!chromosome.equals(Globals.CHR_ALL))dictionary.put(chromosome,genome.getChromosome(chromosome).getLength());result.put("reference_dictionary",dictionary);result.put("sequence_tracks",igv.getAllTracks().stream().filter(t->t instanceof SequenceTrack).map(t->Map.of("name",t.getName(),"visible",t.isVisible())).toList());Files.writeString(directory.resolve("original-igv.json"),JSON.toJson(result)+"\n",StandardCharsets.UTF_8);
            }catch(Throwable error){failure[0]=error;error.printStackTrace();}
            finally{if(frame!=null)frame.dispose();}
        });
        if(failure[0]!=null)System.exit(2);else System.exit(0);
    }
    private static String sequence(String chr,int start,int end)throws Exception {MessageDigest digest=MessageDigest.getInstance("SHA-256");for(int position=start;position<end;){int stop=Math.min(end,position+1048576);byte[] bases=GenomeManager.getInstance().getCurrentGenome().getSequence(chr,position,stop);if(bases==null||bases.length!=stop-position)throw new IOException("Original reference sequence length differs");digest.update(bases);position=stop;}return HexFormat.of().formatHex(digest.digest());}
    private static Map<String,String> preferences(){var result=new LinkedHashMap<String,String>();for(String key:List.of("PORT_ENABLED","AUTOSAVES_TO_KEEP","PROVISIONING_URL_DEFAULT","SAM.DOWNSAMPLE_READS","SAM.QUALITY_THRESHOLD","SAM.FILTER_DUPLICATES","SAM.FILTER_FAILED_READS","SAM.FILTER_SECONDARY_ALIGNMENTS","SAM.FILTER_SUPPLEMENTARY_ALIGNMENTS","SAM.SHOW_SOFT_CLIPPED","SAM.SHOW_JUNCTION_TRACK","SAM.COUNT_DELETED_BASES_COVERED","SAM.SHOW_ALL_BASES")){String value=PreferencesManager.getPreferences().get(key);result.put(key,value==null?"":value);}return result;}
    private static String role(Track track){return track instanceof AlignmentTrack?"alignment":track instanceof CoverageTrack?"coverage":track instanceof SpliceJunctionTrack?"junction":track instanceof FeatureTrack?"feature":track instanceof DataSourceTrack?"signal":"other";}
    private static Color color(JsonArray array){return new Color(array.get(0).getAsInt(),array.get(1).getAsInt(),array.get(2).getAsInt());}
    private static List<Integer> rgb(Color color){return color==null?List.of():List.of(color.getRed(),color.getGreen(),color.getBlue());}
    private static String text(JsonObject object,String key){return object.get(key).getAsString();}
    private static int number(JsonObject object,String key){return object.get(key).getAsInt();}
    private static boolean bool(JsonObject object,String key){return object.get(key).getAsBoolean();}
}
