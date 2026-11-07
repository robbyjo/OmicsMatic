/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.plot;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import org.jlinalg.sequence.SignacCoverage;
import org.jlinalg.sequence.SignacCoverageAnnotations;
import org.jlinalg.sequence.SignacFragments;

/** Deterministic native SVG geometry and Java2D preview; styling is not a ggplot pixel replica. */
public final class SignacCoveragePlot {
    private SignacCoveragePlot() { }
    public record Images(byte[] svg,byte[] png,int width,int height) {
        public Images{svg=svg.clone();png=png.clone();}
        @Override public byte[] svg(){return svg.clone();}@Override public byte[] png(){return png.clone();}
    }
    public static Images render(SignacCoverage.Result coverage,SignacCoverageAnnotations.Layout annotation,SignacFragments.Region region,String title,int width,int maximumHeight,boolean transcriptLabels,boolean raster)throws IOException {
        if(title==null||title.length()>4094||width<600||width>4000||maximumHeight<300||maximumHeight>20000)throw new IllegalArgumentException("Invalid bounded plot dimensions/title");
        if(coverage.points().isEmpty()||!Double.isFinite(coverage.ymax())||coverage.ymax()<=0||coverage.points().stream().anyMatch(p->!Double.isFinite(p.coverage())||p.coverage()<0))throw new IOException("SOURCE_UNPLOTTABLE_COVERAGE: source sampled signal/limits are empty, zero or nonfinite; retain data and choose explicit justified populations/settings before plotting");
        int lanes=annotation.labels().stream().mapToInt(p->p.body().dodge()).max().orElse(0);int height=135+coverage.groups().size()*110+(lanes==0?0:65+lanes*30);
        if(height>maximumHeight)throw new IOException("Complete track/gene layout exceeds maximum_plot_height; no tracks or models were silently dropped");
        var canvas=new Canvas(width,height,raster);double left=150,right=width-35,span=Math.max(1,region.end()-region.start());
        canvas.text(25,28,title,17,"#142536",false);canvas.text(25,48,"Normalized source cut signal • one-based closed "+region.name(),11,"#526271",false);
        String[] colors={"#356d9d","#b85f3e","#3d8175","#8f5d9f","#88702f","#48746b"};
        for(int g=0;g<coverage.groups().size();g++){
            String group=coverage.groups().get(g);double top=80+g*110,base=top+75;List<SignacCoverage.Point> points=coverage.points().stream().filter(p->p.group().equals(group)).sorted(Comparator.comparingInt(SignacCoverage.Point::position)).toList();
            if(points.isEmpty())throw new IOException("SOURCE_UNPLOTTABLE_GROUP: no sampled points remain for "+group+"; no group was silently omitted");
            canvas.text(15,(top+base)/2,group,12,"#142536",false);canvas.text(left-8,top,Double.toString(coverage.ymax()),10,"#526271",false);canvas.line(left,base,right,base,"#a6b1b9",0.7);
            double[][] polygon=new double[points.size()+2][2];polygon[0]=new double[]{left+(points.get(0).position()-region.start())/span*(right-left),base};
            for(int i=0;i<points.size();i++){var p=points.get(i);polygon[i+1]=new double[]{left+(p.position()-region.start())/span*(right-left),base-p.coverage()/coverage.ymax()*70};}
            polygon[polygon.length-1]=new double[]{left+(points.get(points.size()-1).position()-region.start())/span*(right-left),base};canvas.polygon(polygon,colors[g%colors.length]);
        }
        double axis=80+coverage.groups().size()*110-15;for(int i=0;i<=5;i++){double x=left+(right-left)*i/5;canvas.line(x,axis-4,x,axis,"#526271",0.6);canvas.text(x,axis+16,Long.toString(Math.round(region.start()+(region.end()-region.start())*i/5.0)),10,"#526271",true);}
        if(lanes>0){double genes=axis+65;canvas.text(15,genes,"Genes",12,"#142536",false);
            for(var label:annotation.labels()){var b=label.body();double y=genes+(b.dodge()-1)*30;String color=b.strand().equals("-")?"#336e43":"#234f89";canvas.line(x(b.start(),region,left,right),y,x(b.end(),region,left,right),y,color,1);
                canvas.text(x(label.position(),region,left,right),y-8,transcriptLabels?b.transcriptId():b.geneName(),10,color,true);}
            for(var exon:annotation.exons()){if(exon.start()<region.start()||exon.end()>region.end())continue;double y=genes+(exon.dodge()-1)*30;canvas.line(x(exon.start(),region,left,right),y,x(exon.end(),region,left,right),y,exon.strand().equals("-")?"#336e43":"#234f89",5);}
            for(var segment:concat(annotation.plus(),annotation.minus())){if(!segment.type().equals("arrow")||segment.start()<region.start()||segment.end()>region.end())continue;double y=genes+(segment.dodge()-1)*30,tip=x(segment.strand().equals("-")?segment.start():segment.end(),region,left,right),direction=segment.strand().equals("-")?1:-1;String color=segment.strand().equals("-")?"#336e43":"#234f89";canvas.line(tip+3*direction,y-3,tip,y,color,0.8);canvas.line(tip+3*direction,y+3,tip,y,color,0.8);}
        }
        canvas.text(25,height-25,"Native rendering of source-profile data; read WARNINGS.md. Styling and fonts are not ggplot pixel equivalence.",10,"#526271",false);
        return canvas.finish();
    }
    private static List<SignacCoverageAnnotations.Segment> concat(List<SignacCoverageAnnotations.Segment>a,List<SignacCoverageAnnotations.Segment>b){var out=new ArrayList<>(a);out.addAll(b);return out;}
    private static double x(double p,SignacFragments.Region r,double left,double right){return left+(p-r.start())/Math.max(1,r.end()-r.start())*(right-left);}
    private static final class Canvas {
        final int width,height;final StringBuilder svg=new StringBuilder();final BufferedImage image;final Graphics2D g;
        Canvas(int width,int height,boolean raster){this.width=width;this.height=height;image=raster?new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB):null;g=raster?image.createGraphics():null;if(g!=null){g.setColor(Color.WHITE);g.fillRect(0,0,width,height);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);}svg.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(width).append("\" height=\"").append(height).append("\" viewBox=\"0 0 ").append(width).append(' ').append(height).append("\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/>\n");}
        void line(double x1,double y1,double x2,double y2,String color,double thickness){svg.append("<line x1=\"").append(x1).append("\" y1=\"").append(y1).append("\" x2=\"").append(x2).append("\" y2=\"").append(y2).append("\" stroke=\"").append(color).append("\" stroke-width=\"").append(thickness).append("\"/>\n");if(g!=null){g.setColor(Color.decode(color));g.setStroke(new BasicStroke((float)thickness));g.draw(new Line2D.Double(x1,y1,x2,y2));}}
        void polygon(double[][] points,String color){svg.append("<polygon fill=\"").append(color).append("\" points=\"");var path=new Path2D.Double();for(int i=0;i<points.length;i++){double[] p=points[i];svg.append(p[0]).append(',').append(p[1]).append(' ');if(i==0)path.moveTo(p[0],p[1]);else path.lineTo(p[0],p[1]);}svg.append("\"/>\n");path.closePath();if(g!=null){g.setColor(Color.decode(color));g.fill(path);}}
        void text(double x,double y,String value,int size,String color,boolean center){svg.append("<text x=\"").append(x).append("\" y=\"").append(y).append("\" fill=\"").append(color).append("\" font-family=\"sans-serif\" font-size=\"").append(size).append("\" text-anchor=\"").append(center?"middle":"start").append("\">").append(escape(value)).append("</text>\n");if(g!=null){g.setColor(Color.decode(color));g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,size));g.drawString(value,(float)(center?x-g.getFontMetrics().stringWidth(value)/2.0:x),(float)y);}}
        Images finish()throws IOException{svg.append("</svg>\n");byte[] png=new byte[0];if(g!=null){g.dispose();var out=new ByteArrayOutputStream();if(!ImageIO.write(image,"png",out))throw new IOException("Native PNG encoder is unavailable");png=out.toByteArray();}return new Images(svg.toString().getBytes(StandardCharsets.UTF_8),png,width,height);}
        static String escape(String text){if(text.codePoints().anyMatch(c->c<32||c>=0xd800&&c<=0xdfff||c==0xfffe||c==0xffff))throw new IllegalArgumentException("Text outside valid XML character domain; no replacement");return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    }
}
