/* Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later */
package org.jlinalg.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jlinalg.plot.DiagramPlots;
import org.jlinalg.plot.PlotOptions;
import org.jlinalg.plot.Plots;
import org.jlinalg.protein.ProteinStructure.Assessment;

/** Self-contained local C-alpha view; numeric/source artifacts remain authoritative. */
final class StudyProteinViews {
    private StudyProteinViews() { }
    static void write(Path folder,Assessment assessment,double[][] pae,int maximum) throws IOException {
        var coordinates=new ArrayList<Object>();
        for(var mapped:assessment.residues()){var r=mapped.residue();coordinates.add(List.of(mapped.sequencePosition(),String.valueOf(r.aminoAcid()),r.x(),r.y(),r.z(),Double.isFinite(r.plddt())?r.plddt():-1));}
        String data=StudyAssistance.json(coordinates).replace("<","\\u003c").replace(">","\\u003e").replace("&","\\u0026");
        Files.writeString(folder.resolve("structure-view.html"),HTML.replace("__COORDINATES__",data));
        if(assessment.confidenceResidues()>0){var measured=assessment.residues().stream().filter(m->Double.isFinite(m.residue().plddt())).toList();double[] x=measured.stream().mapToDouble(m->m.sequencePosition()).toArray(),y=measured.stream().mapToDouble(m->m.residue().plddt()).toArray();var plot=Plots.scatter(x,y,PlotOptions.builder().size(1100,440).theme(org.jlinalg.plot.PlotTheme.MINIMAL).markerSize(4).xRange(0,Math.max(1,assessment.targetLength())).title("Local model confidence; no functional inference").xLabel("Target sequence position (1-based)").yLabel("pLDDT (0-100)").yRange(0,100).build());plot.save(folder.resolve("plddt.png"));plot.save(folder.resolve("plddt.pdf"));}
        if(pae!=null){int n=pae.length,k=Math.min(n,maximum),stride=(n+k-1)/k;k=(n+stride-1)/stride;double[][] binned=new double[k][k];var labels=new ArrayList<String>();int tick=Math.max(1,(k+7)/8);for(int i=0;i<k;i++)labels.add(i%tick==0||i==k-1?String.valueOf(i*stride+1):"");
            for(int i=0;i<k;i++)for(int j=0;j<k;j++){double mean=0;int count=0;for(int x=i*stride;x<Math.min(n,(i+1)*stride);x++)for(int y=j*stride;y<Math.min(n,(j+1)*stride);y++){count++;mean+=(pae[x][y]-mean)/count;}binned[i][j]=mean;}
            var plot=DiagramPlots.heatmap(new DiagramPlots.HeatmapData(labels,labels,binned),DiagramPlots.ClusterMode.NONE,PlotOptions.builder().size(850,800).title("PAE (Angstrom); "+(stride==1?"full matrix":"mean of "+stride+"-residue bins")).xLabel("Evaluated residue / matrix index (1-based)").yLabel("Alignment-frame residue / matrix index (1-based)").build());plot.save(folder.resolve("pae.png"));plot.save(folder.resolve("pae.pdf"));
            StudyPublicEvidence.write(folder.resolve("plot-provenance.yaml"),Map.of("pae_original_dimension",n,"pae_display_bin_width",stride,"pae_display_aggregation","arithmetic mean, solely for display; directional full matrix is retained","row_axis","aligned-on residue","column_axis","evaluated residue","structure_view","Target-chain C-alpha trace; discontinuous sequence positions are not joined. Coordinates and sequences are local private artifacts."));
        }
    }
    private static final String HTML="""
<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Protein C-alpha context</title><style>body{font:16px system-ui;margin:24px;background:#f4f6fa;color:#152137}main{max-width:1100px;margin:auto}canvas{width:100%;height:560px;background:white;border:1px solid #bcc7d9;touch-action:none}button{font:inherit;padding:7px 15px}small{display:block;margin:12px 0}#status{min-height:24px}</style>
<main><h1>Protein C-alpha context</h1><p>Drag to rotate; scroll to zoom. This local view shows the selected target chain. Lines join consecutive observed residues; they are a trace, not atomic bonds.</p>
<button id="reset" type="button">Reset view</button> <button id="confidence" type="button" aria-pressed="true">Confidence colours: on</button>
<small>pLDDT: <span style="color:#0053d6">90-100 very high</span> · <span style="color:#168bba">70-90 confident</span> · <span style="color:#947400">50-70 low</span> · <span style="color:#c74f00">0-50 very low</span> · grey unavailable. Scores describe local model confidence, not pathogenicity, binding, or biological activity.</small>
<canvas id="view" width="1100" height="560" aria-label="Interactive selected-chain C-alpha trace"></canvas><p id="status" aria-live="polite"></p>
<p>Consult the retained sequence mapping, residue table, PAE and source metadata. Missing coordinates remain gaps. Confidence does not validate a proposed complex interface or connect a statistical isoform-ratio signal to a protein mechanism.</p></main>
<script>"use strict";const points=__COORDINATES__,canvas=document.getElementById('view'),ctx=canvas.getContext('2d'),status=document.getElementById('status');let ax=.3,ay=.3,zoom=1,drag=null,colours=true,projected=[];
const centre=[2,3,4].map(k=>points.reduce((s,p)=>s+p[k],0)/points.length),radius=Math.max(1,...points.map(p=>Math.hypot(p[2]-centre[0],p[3]-centre[1],p[4]-centre[2])));
function colour(v){return !colours?'#314e7e':v<0?'#8793a5':v<50?'#c74f00':v<70?'#947400':v<90?'#168bba':'#0053d6';}
function draw(){ctx.clearRect(0,0,1100,560);const cx=Math.cos(ax),sx=Math.sin(ax),cy=Math.cos(ay),sy=Math.sin(ay),scale=240*zoom/radius;projected=points.map(p=>{const x=p[2]-centre[0],y=p[3]-centre[1],z=p[4]-centre[2],xx=x*cy+z*sy,zz=-x*sy+z*cy;return [550+scale*xx,280+scale*(y*cx-zz*sx),y*sx+zz*cx,p];});ctx.lineWidth=2;for(let i=1;i<projected.length;i++){const a=projected[i-1],b=projected[i];if(b[3][0]!==a[3][0]+1)continue;ctx.strokeStyle=colour(b[3][5]);ctx.beginPath();ctx.moveTo(a[0],a[1]);ctx.lineTo(b[0],b[1]);ctx.stroke();}for(const p of [...projected].sort((a,b)=>a[2]-b[2])){ctx.fillStyle=colour(p[3][5]);ctx.beginPath();ctx.arc(p[0],p[1],3,0,2*Math.PI);ctx.fill();}}
canvas.onpointerdown=e=>{drag=[e.clientX,e.clientY];canvas.setPointerCapture(e.pointerId);};canvas.onpointerup=()=>{drag=null;};canvas.onpointercancel=()=>{drag=null;};canvas.onpointermove=e=>{if(drag){ay+=(e.clientX-drag[0])*.01;ax+=(e.clientY-drag[1])*.01;drag=[e.clientX,e.clientY];draw();}const box=canvas.getBoundingClientRect(),x=(e.clientX-box.left)*1100/box.width,y=(e.clientY-box.top)*560/box.height;let nearest=null,d=144;for(const p of projected){const v=(p[0]-x)**2+(p[1]-y)**2;if(v<d){d=v;nearest=p[3];}}status.textContent=nearest?'Target residue '+nearest[0]+' '+nearest[1]+'; '+(nearest[5]<0?'pLDDT unavailable':'pLDDT '+nearest[5]):'';};
canvas.onwheel=e=>{e.preventDefault();zoom=Math.max(.1,Math.min(10,zoom*Math.exp(-e.deltaY*.001)));draw();};document.getElementById('reset').onclick=()=>{ax=.3;ay=.3;zoom=1;draw();};document.getElementById('confidence').onclick=e=>{colours=!colours;e.target.textContent='Confidence colours: '+(colours?'on':'off');e.target.setAttribute('aria-pressed',String(colours));draw();};draw();</script></html>
""";
}
