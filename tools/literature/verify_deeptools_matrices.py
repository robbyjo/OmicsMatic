"""Actual packaged region/plot/summary workflows with independent known truth."""
import argparse
import csv
import datetime as dt
import gzip
import hashlib
import json
import math
from pathlib import Path
import shutil
import subprocess
import traceback
import yaml

ROOT=Path(__file__).resolve().parents[2]
sha=lambda path:hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--jar',type=Path,required=True)
    parser.add_argument('--runtime-home',type=Path,default=ROOT/'build/omics-deeptools-runtime-v2')
    parser.add_argument('--report',type=Path,required=True)
    parser.add_argument('--samtools',type=Path,default=ROOT/'build/omics-hts/ucrt64/bin/samtools.exe')
    parser.add_argument('--resume',type=Path,help='Revalidate completed same-JAR bundle hashes and resume a terminal failed verifier')
    args=parser.parse_args()
    prior=json.loads(args.resume.read_text()) if args.resume else None
    if prior:
        assert prior['status']=='failed' and prior['jar_sha256']==sha(args.jar),'Only a terminal same-JAR failed verifier may resume'
    folder=Path(prior['directory']) if prior else ROOT/'build/literature-review'/('deeptools-matrices-packaged-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ'))
    assert folder.resolve().parent==(ROOT/'build/literature-review').resolve() and folder.name.startswith('deeptools-matrices-packaged-')
    folder.mkdir(exist_ok=bool(prior));sources={};report=dict(status='running',jar_sha256=sha(args.jar),verifier_sha256=sha(__file__),directory=str(folder),steps=[],checks=[])
    cached={step['name']:step for step in prior['steps']} if prior else {}
    if prior:report['resume_history']=prior.get('resume_history',[])+[{key:value for key,value in prior.items() if key!='resume_history'}]
    qa_root=folder/'rendered'/('resume-'+dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')) if prior else folder/'rendered'
    def save():
        args.report.parent.mkdir(parents=True,exist_ok=True)
        args.report.write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    def file(name,body):
        path=folder/name;content=body.encode('utf-8')
        if path.exists():assert path.read_bytes()==content,('Existing fixture differs; preserve it',path)
        else:path.write_bytes(content)
        sources[path]=sha(path);return path
    def run(name,method,options,expected=0,runtime=None):
        output=folder/name
        command=['java','-jar',str(args.jar.resolve()),'deep-tools','--method',method,'--runtime-home',str(runtime or args.runtime_home.resolve()),'--out',str(output),'--no-config',*map(str,options)]
        log=folder/(name+'.console.log')
        if name in cached:
            step=cached[name];assert step['command']==command and step['exit_code']==expected and step['console_sha256']==sha(log),name
            manifest=yaml.safe_load((output/'manifest.yaml').read_text())
            for relative,digest in manifest['output_sha256'].items():assert sha(output/relative)==digest,(name,relative)
            report['steps'].append({**step,'revalidated_same_jar_bundle':True})
        else:
            child=subprocess.run(command,capture_output=True,text=True,cwd=ROOT,timeout=480)
            log.write_text(child.stdout+child.stderr,encoding='utf-8')
            report['steps'].append(dict(name=name,command=command,exit_code=child.returncode,expected_exit=expected,console_sha256=sha(log)))
            save();assert child.returncode==expected,(name,child.stdout,child.stderr,(output/'WARNINGS.md').read_text() if (output/'WARNINGS.md').exists() else '')
        save()
        status=json.loads((output/'analysis-status.json').read_text())['status']
        assert status.startswith('completed') if expected==0 else status==('skipped-dependency' if expected==3 else 'failed')
        assert 'Required action:' in (output/'WARNINGS.md').read_text()
        if expected:
            assert not(output/'model.json').exists() and not(output/'figure.png').exists() and not(output/'results.tsv').exists()
        else:
            for marker in output.glob('*-execution.json'):
                record=json.loads(marker.read_text());assert record['status'] in ['probe-completed','audit-completed','original-call-completed']
                assert record['versions']==lock['critical_versions'] and record['source_sha256']==lock['source_modules_sha256']
                if record.get('python_core_dll_maps'):assert len(record['python_core_dll_maps'].splitlines())==1
        print('REVALIDATED' if name in cached else 'PASSED',name,flush=True)
        return output
    def matrix_options(mode='reference-point',point='TSS',missing='error',regions=None,tracks=None,up=20,down=20,skip=False):
        options=['--samples',tracks or samples,'--sizes',sizes,'--genome-build','SYNTHETIC','--regions',regions or genes,'--group-label','genes','--mode',mode,'--bin-size','10','--upstream',str(up),'--downstream',str(down),'--missing',missing,'--skip-zero-rows',str(skip).lower(),'--bin-average','mean']
        return [*options,'--body-length','30'] if mode=='scale-regions' else [*options,'--reference-point',point]
    def values(output):
        with gzip.open(output/'matrix.gz','rt') as source:
            header=json.loads(source.readline()[1:]);rows=[line.rstrip('\n').split('\t') for line in source]
        return header,rows
    def compare(actual,truth,tolerance=1e-6):
        assert len(actual)==len(truth)
        for a,b in zip(actual,truth):assert math.isfinite(a) and math.isclose(a,b,rel_tol=tolerance,abs_tol=tolerance),(a,b)
    def image(path):
        from PIL import Image
        with Image.open(path) as bitmap:
            bitmap.load();assert bitmap.width>100 and bitmap.height>100
            assert any(left!=right for left,right in bitmap.convert('RGB').getextrema())
    def render(output,format):
        source=output/('figure.'+format)
        if format=='png':image(source);return
        qa=qa_root/output.name;qa.mkdir(parents=True)
        if format=='pdf':
            binary=shutil.which('pdftoppm');assert binary,'Bundled Poppler pdftoppm required for actual rendered export verification'
            prefix=qa/'rendered-pdf';process=subprocess.run([binary,'-r','90','-singlefile','-png',str(source),str(prefix)],capture_output=True,text=True,timeout=90)
            (qa/'pdf-render.log').write_text(process.stdout+process.stderr);assert process.returncode==0,(source,process.stdout,process.stderr)
            image(prefix.with_suffix('.png'))
            report.setdefault('rendered_pdf_files',[]).append(str(prefix.with_suffix('.png')))
        else:
            # Rasterize the actual self-contained SVG in an independent Chromium process.
            node=ROOT/'build/literature-review/render_deeptools_svg_inline.cjs'
            if not node.exists():
                node.write_text("const fs=require('fs');const {chromium}=require(process.argv[2]+'/playwright');(async()=>{const b=await chromium.launch({executablePath:process.argv[3],headless:true,args:['--disable-gpu']});try{const p=await b.newPage({viewport:{width:1200,height:1000}});await p.route('**/*',r=>r.abort());const s=fs.readFileSync(process.argv[4],'utf8');const i=s.indexOf('<svg');if(i<0)throw Error('Missing SVG root');await p.setContent('<!doctype html><html><body style=\"margin:0;background:white\">'+s.slice(i)+'</body></html>',{waitUntil:'domcontentloaded'});await p.locator('svg').screenshot({path:process.argv[5],timeout:60000,animations:'disabled'});}finally{await b.close();}})().catch(e=>{console.error(e);process.exitCode=1;});")
            report['svg_renderer_sha256']=sha(node)
            base=Path('C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies')
            command=[str(base/'node/bin/node.exe'),str(node),str(base/'node/node_modules'),'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',str(source),str(qa/'rendered-svg.png')]
            process=subprocess.run(command,capture_output=True,text=True,timeout=90)
            (qa/'svg-render.log').write_text(process.stdout+process.stderr);assert process.returncode==0,(source,process.stdout,process.stderr)
            image(qa/'rendered-svg.png')
            report.setdefault('rendered_svg_files',[]).append(str(qa/'rendered-svg.png'))
    try:
        lock=json.loads((ROOT/'tools/literature/deeptools-runtime-lock.json').read_text())
        sizes=file('sizes','chr1\t1000\n')
        genes=file('genes.bed','chr1\t200\t260\tplus\t0\t+\r\nchr1\t400\t460\tminus\t0\t-\r\n')
        track=ROOT/'src/test/resources/deeptools/ramp.bw';control=ROOT/'src/test/resources/deeptools/two.bw';gap=ROOT/'src/test/resources/deeptools/gapped.bw'
        for path in [track,control,gap]:sources[path]=sha(path)
        samples=file('tracks.tsv','sample\tpath\r\n1\t'+str(track)+'\r\ncontrol\t'+str(control)+'\r\n')
        references={'TSS':[[19,20,21,22],[48,47,46,45]],'TES':[[25,26,27,28],[42,41,40,39]],'center':[[22,23,24,25],[45,44,43,42]],'scale':[[19,20,21.5,23.5,25.5,27,28],[48,47,45.5,43.5,41.5,40,39]]}
        models={}
        for point,truth in references.items():
            output=run('matrix-'+point,'matrix',matrix_options('scale-regions' if point=='scale' else 'reference-point',point));models[point]=output
            header,rows=values(output);assert header['sample_labels']==['1','control'] and [row[3] for row in rows]==['plus','minus']
            for row,expected in zip(rows,truth):compare([float(value) for value in row[6:]],[*expected,*([2.]*len(expected))])
        report['checks'].append('Actual strand-aware TSS,TES,center and scaled-body matrices match independent signal coordinates; CRLF and numeric sample IDs remain exact')
        reductions={'mean':[33.5]*4,'median':[33.5]*4,'min':[19,20,21,22],'max':[48,47,46,45],'sum':[67]*4,'std':[14.5,13.5,12.5,11.5]}
        for average,truth in reductions.items():
            output=run('profile-'+average,'plot-profile',['--model',models['TSS']/'model.json','--average',average])
            with (output/'profile-data.tsv').open() as source:rows=list(csv.reader(source,delimiter='\t'))
            assert rows[2][:2]==['1','genes'];compare([float(value) for value in rows[2][2:]],truth)
            render(output,'png')
        report['checks'].append('All six original profile reductions match hand-calculated bin values and produce decoded nonconstant PNGs')
        for format in ['pdf','svg']:
            output=run('profile-'+format,'plot-profile',['--model',models['TSS']/'model.json','--average','mean','--format',format,'--per-group','true','--plot-type','se']);render(output,format)
        for order,format,expected in [('keep','png',['plus','minus']),('ascend','pdf',['plus','minus']),('descend','svg',['minus','plus'])]:
            output=run('heatmap-'+order,'plot-heatmap',['--model',models['TSS']/'model.json','--average','mean','--sort-regions',order,'--sort-using','mean','--format',format]);render(output,format)
            rows=[row.split('\t') for row in (output/'plotted-regions.bed').read_text().splitlines() if row and not row.startswith('#')];assert [row[3] for row in rows]==expected
        report['checks'].append('Explicit heatmap keep/ascending/descending order and original profile/heatmap PDF/SVG exports survive independent rasterization')
        bams=[]
        report['samtools_executable_sha256']=sha(args.samtools)
        fixture_hashes={Path(name).resolve():digest for name,digest in (prior or {}).get('fixture_files_sha256',{}).items()}
        if prior and not fixture_hashes and (folder/'summary-bins/session.yaml').exists():
            fixture_hashes={Path(name).resolve():digest for name,digest in yaml.safe_load((folder/'summary-bins/session.yaml').read_text())['source_sha256'].items()}
        if prior:report['fixture_steps']=prior.get('fixture_steps',[])
        for name,positions in [('first',[1,6,21]),('second',[11,21,26])]:
            header='@HD\tVN:1.6\tSO:unsorted\n@SQ\tSN:chr1\tLN:1000\n'
            sam=file(name+'.sam',header+''.join(f'read{index}\t0\tchr1\t{position}\t40\t10M\t*\t0\t0\tAAAAAAAAAA\tIIIIIIIIII\n' for index,position in enumerate(positions)))
            bam=folder/(name+'.bam')
            if bam.exists() or Path(str(bam)+'.bai').exists():
                for path in [bam,Path(str(bam)+'.bai')]:assert fixture_hashes.get(path.resolve())==sha(path),('Existing fixture identity differs; preserve it',path)
            else:
                for command in [[str(args.samtools),'sort','-o',str(bam),str(sam)],[str(args.samtools),'index',str(bam)]]:
                    child=subprocess.run(command,capture_output=True,text=True,timeout=120)
                    report.setdefault('fixture_steps',[]).append(dict(command=command,exit_code=child.returncode))
                    save();assert child.returncode==0,(command,child.stdout,child.stderr)
            for path in [bam,Path(str(bam)+'.bai')]:
                sources[path]=sha(path)
                report.setdefault('fixture_files_sha256',{})[str(path.resolve())]=sha(path)
            bams.append(bam)
        bam_samples=file('bams.tsv','sample\tpath\nA\tfirst.bam\nB\tsecond.bam\n')
        common=['--samples',bam_samples,'--sizes',sizes,'--genome-build','SYNTHETIC']
        summaries={}
        for name,extra in [('bins',['--mode','bins','--bin-size','10']),('spaced',['--mode','bins','--bin-size','10','--distance-between-bins','7']),('regions',['--mode','regions','--regions',file('windows.bed','chr1\t0\t20\twin0\t0\t+\nchr1\t20\t40\twin1\t0\t+\n')])]:
            output=run('summary-'+name,'bam-summary',[*common,*extra]);summaries[name]=output
            with (output/'results.tsv').open() as source:rows=list(csv.DictReader(source,delimiter='\t'))
            if name=='regions':assert [(float(row['A']),float(row['B'])) for row in rows]==[(2.,1.),(1.,2.)]
            else:
                step=10 if name=='bins' else 17;assert len(rows)==math.ceil(1000/step)
                for index,row in enumerate(rows):
                    start=index*step;stop=min(start+10,1000);assert (int(row['start']),int(row['end']))==(start,stop)
                    for label,starts in [('A',[0,5,20]),('B',[10,20,25])]:assert float(row[label])==sum(pos<stop and pos+10>start for pos in starts)
        report['checks'].append('BAM summaries match independent half-open overlap counts for contiguous bins,17bp spacing and exact supplied BED windows')
        for method,zero,plot,format in [('pearson','keep','heatmap','png'),('spearman','keep','heatmap','svg'),('pearson','omit','scatterplot','pdf'),('spearman','omit','heatmap','png')]:
            output=run('correlation-'+method+'-'+zero,'correlation',['--model',summaries['bins']/'model.json','--correlation-method',method,'--what-to-plot',plot,'--zero-rows',zero,'--format',format]);render(output,format)
            with (output/'results.tsv').open() as source:rows=list(csv.DictReader(source,delimiter='\t'))
            assert len(rows)==4 and {(row['sample1'],row['sample2']) for row in rows}=={('A','A'),('A','B'),('B','A'),('B','B')}
            for row in rows:
                actual,truth=float(row['correlation']),float(row['independent_reference']);assert abs(actual-truth)<=5.1e-5
                if zero=='omit' and row['sample1']!=row['sample2']:assert abs(actual+.5)<1e-8
        report['checks'].append('Pearson/Spearman and explicit keep/omit policies preserve exact sample pairs; removing shared zero bins gives the independent-0.5coefficient')
        gap_samples=file('gap-tracks.tsv','sample\tpath\ngap\t'+str(gap)+'\n')
        gap_regions=file('gap-regions.bed','chr1\t10\t20\tmissing\t0\t+\nchr1\t20\t30\tsignal\t0\t+\n')
        for missing,skip,expected in [('retain',False,0),('zero',True,0),('error',False,2)]:
            output=run('missing-'+missing,'matrix',matrix_options(missing=missing,regions=gap_regions,tracks=gap_samples,up=0,down=10,skip=skip),expected)
            if not expected:
                header,rows=values(output)
                if missing=='retain':assert len(rows)==2 and math.isnan(float(rows[0][6])) and float(rows[1][6])==2
                else:
                    assert len(rows)==1 and rows[0][3]=='signal';assert 'missing\tomitted-by-original\tNA' in (output/'results.tsv').read_text()
        report['checks'].append('Missing retain/error and explicit zero-row filtering preserve NaN or every omitted-region status without invented observations')
        run('resource-limit','matrix',[*matrix_options(),'--max-cells','1'],2)
        run('missing-runtime','matrix',matrix_options(),3,runtime=folder/'absent-runtime')
        changed=folder/'changed-model-input'
        if changed.exists():assert (changed/'matrix.gz').read_bytes()==(models['TSS']/'matrix.gz').read_bytes()+b'changed'
        else:
            shutil.copytree(models['TSS'],changed)
            (changed/'matrix.gz').write_bytes((changed/'matrix.gz').read_bytes()+b'changed')
        run('changed-model-failure','plot-profile',['--model',changed/'model.json','--average','mean'],2)
        report['checks'].append('Resource limits,unavailable dependencies and model tampering fail or skip with explicit actions and no successful result')
        for path,digest in sources.items():assert sha(path)==digest,path
        assert sha(args.jar)==report['jar_sha256']
        report.update(status='passed-original-matrix-plot-summary-correlation-subset',source_sha256={str(path):digest for path,digest in sources.items()},scope='Actual five typed original3.5.6 workflows and tested options; no clustering,GTF/BED12,multiple region groups,multiBigwigSummary,plotPCA,interactive Plotly,historical or full-paper parity.')
    except BaseException:
        report.update(status='failed',error=traceback.format_exc());print(report['error'],flush=True)
    save();print(report['status'],len(report['steps']),'steps',len(report['checks']),'checks',flush=True)
    return 0 if report['status'].startswith('passed') else 1


if __name__=='__main__':
    raise SystemExit(main())
