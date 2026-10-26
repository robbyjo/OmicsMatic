"""Actual deepTools3.5.6 CLI functions against independently known signals."""
from pathlib import Path
import datetime
import gzip
import hashlib
import importlib.metadata
import json
import math
import os
import shutil
import subprocess
import sys
import traceback

root = Path(__file__).resolve().parents[2]
evidence = root / 'build/literature-review/deeptools-runtime'
evidence.mkdir(parents=True, exist_ok=True)
folder = evidence / ('original-fixtures-' + datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'))
folder.mkdir()
progress = evidence / 'original-fixture-progress.json'
report = dict(status='running', directory=str(folder), steps=[], checks=[], failures=[])
if progress.exists():
    history = evidence / 'original-verifier-history'
    history.mkdir(exist_ok=True)
    (history / (hashlib.sha256(progress.read_bytes()).hexdigest()+'.json')).write_bytes(progress.read_bytes())
progress.write_text(json.dumps(report, indent=2)+'\n')

def save():
    progress.write_text(json.dumps(report, indent=2)+'\n')

# Explicit dependency initialization avoids the observed pyplot early exit.
# The original deepTools modules/scientific functions are not modified.
bridge = folder / 'original_entry.py'
shutil.copyfile(root/'src/main/resources/org/jlinalg/omics/deeptools_bridge.py',bridge)
report['bridge_sha256']=hashlib.sha256(bridge.read_bytes()).hexdigest()

def run(stage, module, arguments, expected):
    marker=folder/(stage+'-execution.json')
    command=[sys.executable,str(bridge),'--report',str(marker),'--module',module,'--',*map(str,arguments)]
    step=dict(stage=stage, module=module, command=command)
    report['steps'].append(step); save(); print('RUNNING',stage,flush=True)
    result=subprocess.run(command,cwd=folder,capture_output=True,text=True,timeout=180)
    (folder/(stage+'.log')).write_text(result.stdout+result.stderr)
    step.update(exit_code=result.returncode, marker=json.loads(marker.read_text()) if marker.exists() else None)
    save()
    assert result.returncode==0,(stage,result.returncode,(result.stdout+result.stderr)[-1200:])
    assert step['marker'] and step['marker']['status']=='original-call-completed',(stage,'Early exit without completion marker')
    lock=json.loads((root/'tools/literature/deeptools-runtime-lock.json').read_text())
    assert step['marker']['versions']==lock['critical_versions'],(stage,'Dependency versions differ')
    assert step['marker']['source_sha256']==lock['source_modules_sha256'],(stage,'Original sources differ')
    if step['marker'].get('python_core_dll_maps'):
        assert len(step['marker']['python_core_dll_maps'].splitlines())==1,(stage,'Multiple Python core DLLs loaded')
    for name in expected:
        assert (folder/name).is_file() and (folder/name).stat().st_size>0,(stage,'missing/empty output',name)
    print('FINISHED',stage,flush=True)

def check(name,function):
    try:
        function();report['checks'].append(name);print('PASSED',name,flush=True)
    except Exception:
        report['failures'].append({'check':name,'error':traceback.format_exc()});print('FAILED',name,report['failures'][-1]['error'],flush=True)
    save()

import numpy as np
import pysam
import pyBigWig
from PIL import Image
assert importlib.metadata.version('deeptools')=='3.5.6'
report['versions']={name:importlib.metadata.version(name) for name in ['deeptools','numpy','scipy','pysam','pyBigWig','matplotlib','contourpy','kiwisolver','Pillow']}
import deeptools
source=Path(deeptools.__file__).resolve().parent
lock=json.loads((root/'tools/literature/deeptools-runtime-lock.json').read_text())
identities={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in source.glob('*.py')}
assert identities == lock['source_modules_sha256']
report['unmodified_original_source_sha256']=identities

def bam(name,starts,paired=False):
    path=folder/name
    with pysam.AlignmentFile(str(path),'wb',header={'HD':{'VN':'1.6','SO':'coordinate'},'SQ':[{'SN':'chr1','LN':1000}]}) as output:
        for index,start in enumerate(starts):
            read=pysam.AlignedSegment();read.query_name='pair' if paired else 'read'+str(index);read.query_sequence='A'*10;read.query_qualities=pysam.qualitystring_to_array('I'*10)
            read.reference_id=0;read.reference_start=start;read.mapping_quality=40;read.cigarstring='10M';read.flag=(99 if index==0 else 147) if paired else 0
            if paired:read.next_reference_id=0;read.next_reference_start=starts[1-index];read.template_length=40 if index==0 else -40
            output.write(read)
    pysam.index(str(path));return path

first=bam('first.bam',[0,5,20]);second=bam('second.bam',[10,20,25]);paired=bam('paired.bam',[100,130],True)
for name,values in [('ramp.bw',np.arange(1,101,dtype=float)),('two.bw',np.repeat(2.,100))]:
    with pyBigWig.open(str(folder/name),'w') as output:
        output.addHeader([('chr1',1000)]);output.addEntries(['chr1']*100,list(range(0,1000,10)),ends=list(range(10,1001,10)),values=values.tolist())
(folder/'genes.bed').write_text('chr1\t200\t260\tplus\t0\t+\nchr1\t400\t460\tminus\t0\t-\n')

def dense_bedgraph(name):
    result=np.full(1000,np.nan)
    for line in (folder/name).read_text().splitlines():
        chromosome,start,end,value=line.split();assert chromosome=='chr1'
        result[int(start):int(end)]=float(value)
    return result

def coverage():
    raw=np.zeros(1000);raw[:10]=2;raw[10:30]=1
    for mode,factor in [('None',1.),('CPM',1e6/3),('RPKM',1e9/30),('BPM',1e6/3),('RPGC',100/30)]:
        name='coverage-'+mode+'.bedgraph';arguments=['-b',first,'-o',name,'--outFileFormat','bedgraph','--binSize','10','--numberOfProcessors','1','--normalizeUsing',mode,'--exactScaling']
        if mode=='RPGC':arguments+=['--effectiveGenomeSize','100']
        run('coverage-'+mode,'bamCoverage',arguments,[name]);np.testing.assert_allclose(dense_bedgraph(name),raw*factor,rtol=5e-6,atol=1e-8)
check('Five BAM coverage normalization modes with exact independently declared depth/read length',coverage)

def comparison():
    for operation in ['mean','subtract','log2']:
        name='comparison-'+operation+'.bedgraph';arguments=['-b1',folder/'ramp.bw','-b2',folder/'two.bw','-o',name,'--outFileFormat','bedgraph','--binSize','10','--operation',operation,'--numberOfProcessors','1','--scaleFactors','1:2','--pseudocount','1','1']
        run('compare-'+operation,'bigwigCompare',arguments,[name]);signal=np.repeat(np.arange(1,101,dtype=float),10)
        expected=(signal+4)/2 if operation=='mean' else signal-4 if operation=='subtract' else np.log2((signal+1)/5)
        np.testing.assert_allclose(dense_bedgraph(name),expected,rtol=5e-6,atol=1e-6)
check('Original BigWig mean/subtract/log2 with explicit scale then pseudocount',comparison)

def matrix_values(name):
    with gzip.open(folder/name,'rt') as source:
        header=json.loads(source.readline()[1:]);lines=[line.rstrip('\n').split('\t') for line in source]
    assert [fields[3] for fields in lines]==['plus','minus']
    return header,np.array([[float(value) for value in fields[6:]] for fields in lines])

def matrices():
    for point,expected in [('TSS',[[19,20,21,22],[48,47,46,45]]),('TES',[[25,26,27,28],[42,41,40,39]]),('center',[[22,23,24,25],[45,44,43,42]])]:
        name='matrix-'+point+'.gz';run('matrix-'+point,'computeMatrix',['reference-point','--referencePoint',point,'-R',folder/'genes.bed','-S',folder/'ramp.bw','-b','20','-a','20','--binSize','10','--sortRegions','keep','--numberOfProcessors','1','--samplesLabel','signal','-o',name],[name]);header,values=matrix_values(name);np.testing.assert_array_equal(values,expected)
    run('matrix-scale','computeMatrix',['scale-regions','-R',folder/'genes.bed','-S',folder/'ramp.bw','-b','20','-a','20','--regionBodyLength','30','--binSize','10','--sortRegions','keep','--numberOfProcessors','1','--samplesLabel','signal','-o','matrix-scale.gz'],['matrix-scale.gz'])
    header,values=matrix_values('matrix-scale.gz');np.testing.assert_array_equal(values,[[19,20,21.5,23.5,25.5,27,28],[48,47,45.5,43.5,41.5,40,39]])
check('Strand-aware TSS/TES/center matrices and actual scaled gene bodies with kept region order',matrices)

def plots():
    run('profile','plotProfile',['-m','matrix-TSS.gz','-out','profile.png','--plotType','lines','--averageType','mean','--outFileNameData','profile-data.tsv','--dpi','80'],['profile.png','profile-data.tsv'])
    run('heatmap','plotHeatmap',['-m','matrix-scale.gz','-out','heatmap.png','--sortRegions','keep','--dpi','80'],['heatmap.png'])
    profile=(folder/'profile-data.tsv').read_text().splitlines()[2].split('\t')
    assert profile[:2]==['signal','genes']
    np.testing.assert_array_equal([float(value) for value in profile[2:]],[33.5]*4)
    for name in ['profile.png','heatmap.png']:
        with Image.open(folder/name) as image:
            image.load();pixels=np.asarray(image.convert('RGB'));assert image.width>100 and image.height>100 and np.std(pixels)>10
check('Original profile/heatmap produce actual decodable nonconstant scientific figures',plots)

def ranks(values):
    order=sorted(range(len(values)),key=lambda i:values[i]);result=np.empty(len(values));start=0
    while start<len(order):
        end=start+1
        while end<len(order) and values[order[start]]==values[order[end]]:end+=1
        for index in range(start,end):result[order[index]]=(start+1+end)/2
        start=end
    return result

def summary():
    run('bam-summary','multiBamSummary',['bins','-b',first,second,'--binSize','10','--numberOfProcessors','1','--labels','A','B','-o','summary.npz','--outRawCounts','summary.tsv'],['summary.npz','summary.tsv'])
    expected=np.zeros((100,2));expected[:3,0]=[2,1,1];expected[:4,1]=[0,1,2,1]
    with np.load(folder/'summary.npz') as data:np.testing.assert_array_equal(data['matrix'],expected)
    run('spearman','plotCorrelation',['-in','summary.npz','--corMethod','spearman','--whatToPlot','heatmap','--outFileCorMatrix','spearman.tsv','-o','correlation.png'],['spearman.tsv','correlation.png'])
    lines=[line for line in (folder/'spearman.tsv').read_text().splitlines() if line and not line.startswith('#')];actual=np.array([[float(value) for value in line.split('\t')[1:]] for line in lines[1:]])
    first_rank,second_rank=ranks(expected[:,0]),ranks(expected[:,1]);first_rank-=first_rank.mean();second_rank-=second_rank.mean();truth=float(np.dot(first_rank,second_rank)/math.sqrt(np.dot(first_rank,first_rank)*np.dot(second_rank,second_rank)));np.testing.assert_allclose(actual,[[1,truth],[truth,1]],rtol=1e-4,atol=1e-4)
check('BAM bin-summary matrix and Spearman ties independently calculated from ranks',summary)

def shift():
    run('atac-shift','alignmentSieve',['-b',paired,'-o','shifted.bam','--ATACshift','--numberOfProcessors','1','--filterMetrics','shift-metrics.tsv'],['shifted.bam','shift-metrics.tsv'])
    with pysam.AlignmentFile(str(folder/'shifted.bam'),'rb') as source:
        records=list(source);assert [(r.reference_start,r.reference_end,r.template_length,r.next_reference_start) for r in records]==[(104,110,31,130),(130,135,-31,104)]
        assert all(r.query_sequence is None and r.query_qualities is None for r in records)
check('Original alignmentSieve Tn5 shifts exact positions/CIGAR/template/mates and drops SEQ/QUAL',shift)
report['status']='passed-original-scientific-subset' if not report['failures'] else 'failed-scientific-fixtures'
report['source_sha256_after']={p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in source.glob('*.py')}
assert report['source_sha256_after']==identities
report['scope']='Actual original deepTools3.5.6 CLI functions; no Java adapter/full gate or published-version/full-paper parity yet. Explicit dependency preloading is part of this private Cygwin probe.'
save();(folder/'verification.json').write_text(json.dumps(report,indent=2)+'\n');print(report['status'],flush=True)
sys.exit(0 if not report['failures'] else 1)
