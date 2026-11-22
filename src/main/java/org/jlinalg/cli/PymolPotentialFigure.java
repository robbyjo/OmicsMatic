/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

/** Standalone scientific SVG:unchanged original raster plus an explicit unit-labeled ramp. */
final class PymolPotentialFigure {
    private PymolPotentialFigure() { }
    static Map<String,Object> write(Path directory,Map<String,Object> recipe,String units,double temperature)throws Exception {
        Path png=directory.resolve("surface.png");int width=PymolPotentialAudit.integer(recipe.get("width")),height=PymolPotentialAudit.integer(recipe.get("height"));
        try(var stream=ImageIO.createImageInputStream(png.toFile())) {
            if(stream==null)throw new IOException("Original surface image missing");var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IOException("Invalid original surface PNG");var reader=readers.next();
            try{reader.setInput(stream);if(!reader.getFormatName().equalsIgnoreCase("png")||reader.getWidth(0)!=width||reader.getHeight(0)!=height)throw new IOException("Original surface image format/dimensions differ");}finally{reader.dispose();}
        }
        var image=ImageIO.read(png.toFile());if(image==null)throw new IOException("Original surface PNG cannot decode");List<?> background=PymolPotentialAudit.list(recipe.get("background"));int[] bg=new int[3];for(int i=0;i<3;i++)bg[i]=(int)Math.round(255*PymolPotentialAudit.number(background.get(i)));long foreground=0;int left=width,top=height,right=-1,bottom=-1;
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){int rgba=image.getRGB(x,y);if((rgba>>>24)!=255)throw new IOException("Opaque surface rendering unexpectedly contains transparent pixels");if(Math.max(Math.max(Math.abs(((rgba>>>16)&255)-bg[0]),Math.abs(((rgba>>>8)&255)-bg[1])),Math.abs((rgba&255)-bg[2]))>5){foreground++;left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}}
        if(foreground<Math.max(16,width*(long)height/1000)||left<2||top<2||right>width-3||bottom>height-3)throw new IOException("Surface image is empty or touches frame bounds;review camera/buffer and rerun. Pixel framing is a QC heuristic,not biological validation");
        List<?> levels=PymolPotentialAudit.list(recipe.get("levels")),colors=PymolPotentialAudit.list(recipe.get("colors"));double low=PymolPotentialAudit.number(levels.get(0)),mid=PymolPotentialAudit.number(levels.get(1)),high=PymolPotentialAudit.number(levels.get(2)),fraction=(mid-low)/(high-low);int pad=Math.max(24,width/20),bar=width-2*pad;double middle=pad+bar*fraction,labelMiddle=Math.max(pad+bar*.15,Math.min(pad+bar*.85,middle));
        String title=units.equals("arbitrary")?"Supplied scalar (arbitrary units)":"Electrostatic potential ("+(units.equals("kt-per-e")?"kT/e":"V")+")";String subtitle=units.equals("kt-per-e")?"Declared temperature: "+value(temperature)+" K":"Grid values and units supplied by caller.";
        StringBuilder svg=new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" width=\"").append(width).append("\" height=\"").append(height+180).append("\" viewBox=\"0 0 ").append(width).append(' ').append(height+180).append("\">\n<title>").append(title).append(" on molecular surface</title>\n<desc>Original PyMOL raster in its supplied coordinate frame,with an explicit scalar ramp. Lighting changes apparent surface RGB;use the labeled scalar ramp and retained model provenance.</desc>\n<defs><linearGradient id=\"potentialRamp\" x1=\"0\" y1=\"0\" x2=\"1\" y2=\"0\" color-interpolation=\"sRGB\">");
        for(int i=0;i<3;i++)svg.append("<stop offset=\"").append(i==0?"0":i==2?"100":Double.toString(100*fraction)).append("%\" stop-color=\"").append(rgb(PymolPotentialAudit.list(colors.get(i)))).append("\"/>");
        svg.append("</linearGradient></defs>\n<image x=\"0\" y=\"0\" width=\"").append(width).append("\" height=\"").append(height).append("\" xlink:href=\"data:image/png;base64,").append(Base64.getEncoder().encodeToString(Files.readAllBytes(png))).append("\"/>\n<rect y=\"").append(height).append("\" width=\"").append(width).append("\" height=\"180\" fill=\"white\"/>\n<g font-family=\"Arial,Helvetica,sans-serif\" fill=\"#111\">");
        text(svg,pad,height+23,16,"start",title);text(svg,pad,height+42,12,"start",subtitle);
        svg.append("<rect x=\"").append(pad).append("\" y=\"").append(height+55).append("\" width=\"").append(bar).append("\" height=\"20\" fill=\"url(#potentialRamp)\" stroke=\"#111\"/>");
        double[] positions={pad,middle,width-pad};for(int i=0;i<3;i++){svg.append("<path d=\"M ").append(positions[i]).append(' ').append(height+75).append(" L ").append(i==1?labelMiddle:positions[i]).append(' ').append(height+84).append("\" stroke=\"#111\" fill=\"none\"/>");text(svg,i==1?labelMiddle:positions[i],height+101,13,i==0?"start":i==2?"end":"middle",value(PymolPotentialAudit.number(levels.get(i))));}
        text(svg,pad,height+123,12,"start","Surface radii: "+(recipe.get("surface_radii").equals("source-pqr")?"explicit source PQR":"original PyMOL display radii"));text(svg,pad,height+143,12,"start",recipe.get("surface_solvent")+" surface; probe radius "+value(PymolPotentialAudit.number(recipe.get("probe_radius")))+" Angstrom");text(svg,pad,height+163,12,"start",PymolPotentialAudit.integer(recipe.get("ramp_above_mode"))==0?"Map sampled at the original surface vertices.":"Map sampled one probe radius along the surface normal.");svg.append("</g></svg>\n");Files.writeString(directory.resolve("figure.svg"),svg,StandardCharsets.UTF_8);
        var result=new LinkedHashMap<String,Object>();result.put("raw_image","surface.png");result.put("standalone_figure","figure.svg");result.put("raw_width",width);result.put("raw_height",height);result.put("figure_height",height+180);result.put("foreground_pixels",foreground);result.put("foreground_pixel_bounds",List.of(left,top,right,bottom));result.put("pixel_QC_scope","Opaque PNG,size,nonempty body and2pixel frame clearance checked. This is an image-QC heuristic;inspect framing,occlusion and interpretation manually.");result.put("legend_units",units);if(units.equals("kt-per-e"))result.put("declared_temperature_kelvin",temperature);result.put("legend_middle_fraction",fraction);return result;
    }
    private static String rgb(List<?> values)throws IOException {if(values.size()!=3)throw new IOException("RGB requires three channels");return "rgb("+Math.round(255*PymolPotentialAudit.number(values.get(0)))+","+Math.round(255*PymolPotentialAudit.number(values.get(1)))+","+Math.round(255*PymolPotentialAudit.number(values.get(2)))+")";}
    private static String value(double value){return String.format(Locale.ROOT,"%.7g",value);}
    private static void text(StringBuilder svg,double x,double y,int size,String anchor,String text){svg.append("<text x=\"").append(x).append("\" y=\"").append(y).append("\" font-size=\"").append(size).append("\" text-anchor=\"").append(anchor).append("\">").append(text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")).append("</text>");}
}
