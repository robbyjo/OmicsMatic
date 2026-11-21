from pathlib import Path
import hashlib,json,os,shutil,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-3';B=T/'build/twosamplemr-clump-1';O=B/'original-controls-1';O.mkdir()
I=R/'build/twosamplemr-clump-intake-20261121-1';library=R/'build/twosamplemr-runtime-intake-20261121-1/library';plink=R/'build/omics-plink-official/plink1/plink.exe'
assert hashlib.sha256(plink.read_bytes()).hexdigest()=='efc55270aba7216e4fcebdf781ee04e9fa556c2fe5be1f71c836ba73930a6b6b'
worker=(B/'common.R').read_text()+'\n'+(R/'build/twosamplemr-clump-worker-body-20261121.R').read_text()
(O/'worker-source.R').write_text(worker,encoding='utf-8',newline='\n')
founders=[(i,j) for i in range(3) for j in range(3) for _ in range(3)];a=[i for i,j in founders]+[0,1,2]*3;b=[i for i,j in founders]+[2,1,0]*3;c=[j for i,j in founders]+[0,2,1]*3
variants=[('1','a',1000,a),('1','b',1100,b),('1','c',1200,c),('1','far',20000000,a),('2','otherchr',1000,a),('1','mono',1300,[0]*36),('1','missing',1400,[-1]*36),('1','coincident',1000,a)]
def panel(prefix,bad=False):
    rows=['\t'.join(['F',f'I{i+1}','0' if i<27 else 'I1','0' if i<27 else 'I2',str(1+i%2),'-9']) for i in range(36)]
    prefix.with_suffix('.fam').write_text('\n'.join(rows)+'\n',encoding='utf-8',newline='\n')
    prefix.with_suffix('.bim').write_text('\n'.join(f'{"badContig" if bad else chrom}\t{snp}\t0\t{pos}\tA\tC' for chrom,snp,pos,g in variants)+'\n',encoding='utf-8',newline='\n')
    raw=bytearray([0x6c,0x1b,1])
    for chrom,snp,pos,g in variants:
        for start in range(0,36,4):
            value=0
            for k,dosage in enumerate(g[start:start+4]):value|={-1:1,0:3,1:2,2:0}[dosage]<<(2*k)
            raw.append(value)
    prefix.with_suffix('.bed').write_bytes(raw)
def data(rows,columns=('SNP','id.exposure','pval.exposure')):return list(columns),[list(row) for row in rows]
cases={
'basic':(*data([('a','g',1e-8),('b','g',2e-8),('c','g',3e-8),('far','g',4e-8),('otherchr','g',5e-8),('absent','g',6e-8)]),{}),
'high-p-linked':(*data([('a','g',.8),('b','g',.9)]),{}),
'single-absent-above-p':(*data([('absent','g',.9)]),{'clump_p1':5e-8}),
'single-present-above-p':(*data([('a','g',.9)]),{'clump_p1':5e-8}),
'empty-threshold':(*data([('a','g',.1),('b','g',.2)]),{'clump_p1':5e-8}),
'multiple-groups':(*data([('a','g1',1e-8),('b','g1',2e-8),('c','g1',3e-8),('a','g2',2e-8),('b','g2',1e-8),('c','g2',3e-8),('absent','g3',.9)]),{}),
'both-p-columns':(*data([('a','g',1e-8,.8),('b','g',2e-8,1e-10)],('SNP','id.exposure','pval.exposure','pval.outcome')),{}),
'outcome-p-fallback':(*data([('a','g',1e-8),('b','g',2e-8)],('SNP','id.exposure','pval.outcome')),{}),
'default-p':(*data([('a','g'),('b','g'),('c','g')],('SNP','id.exposure')),{}),
'generated-id':(*data([('a',1e-8),('b',2e-8),('c',3e-8)],('SNP','pval.exposure')),{}),
'duplicate-input':(*data([('a','g',3e-8),('a','g',1e-8),('b','g',2e-8),('c','g',4e-8)]),{}),
'tied-p-reversed-input':(*data([('b','g',1e-8),('a','g',1e-8),('c','g',2e-8)]),{}),
'missing-p':(*data([('a','g','NA'),('b','g',2e-8),('c','g',3e-8)]),{}),
'all-missing-p':(*data([('a','g','NA'),('b','g','NA')]),{}),
'monomorphic-missing-genotypes':(*data([('a','g',1e-8),('mono','g',2e-8),('missing','g',3e-8)]),{}),
'index-boundary':(*data([('a','g',5e-8),('b','g',6e-8),('c','g',7e-8)]),{'clump_p1':5e-8}),
'r2-one':(*data([('a','g',1e-8),('b','g',2e-8)]),{'clump_r2':1}),
'r2-zero':(*data([('a','g',1e-8),('b','g',2e-8),('c','g',3e-8)]),{'clump_r2':0}),
'coincident-window-zero':(*data([('a','g',1e-8),('coincident','g',2e-8)]),{'clump_kb':0}),
'p-format-near-tie':(*data([('a','g',1.234567890123456e-8),('b','g',1.234567890123455e-8)]),{}),
'bad-reference-chromosome':(*data([('a','g',1e-8),('b','g',2e-8)]),{}),
'space path':(*data([('a','group one',1e-8),('b','group one',2e-8),('c','group one',3e-8)]),{})}
results={}
for name,(columns,rows,options) in cases.items():
    root=O/name;root.mkdir();(root/'inputs').mkdir();(root/'worker/owned-tmp').mkdir(parents=True);(root/'source/reference').mkdir(parents=True);(root/'source/clump-reference').mkdir()
    panel(root/'inputs/reference',name=='bad-reference-chromosome')
    (root/'inputs/instruments').write_text('\n'.join(['\t'.join(columns),*['\t'.join(map(str,row)) for row in rows]])+'\n',encoding='utf-8',newline='\n')
    shutil.copyfile(T/'src/main/resources/org/jlinalg/omics/twosamplemr-workflow/source.zip',root/'source/reference/source.zip');shutil.copyfile(I/'ieugwasr/R/ld_clump.R',root/'source/clump-reference/ieugwasr-ld_clump.R');shutil.copyfile(O/'worker-source.R',root/'source/worker.R')
    request=dict(seed=20261121,clump_kb=10000,clump_r2=.001,clump_p1=1,pop='EUR');request.update(options)
    (root/'effective-request.json').write_text(json.dumps(request,indent=2)+'\n',encoding='utf-8',newline='\n');(root/'worker/runtime-paths.txt').write_text(library.as_posix()+'\n',encoding='utf-8',newline='\n');(root/'worker/plink-binding.json').write_text(json.dumps(dict(executable=str(plink)))+'\n',encoding='utf-8',newline='\n')
    env=dict(os.environ,LANG='C',LC_ALL='C',TEMP=str(root/'worker/owned-tmp'),TMP=str(root/'worker/owned-tmp'),TMPDIR=str(root/'worker/owned-tmp'))
    with (root/'stdout').open('xb') as stdout,(root/'stderr').open('xb') as stderr:p=subprocess.run(['C:/Program Files/R/R-4.6.1/bin/Rscript.exe','--vanilla','../source/worker.R'],cwd=root/'worker',env=env,stdout=stdout,stderr=stderr,timeout=120)
    report=json.loads((root/'worker/report.json').read_bytes()) if (root/'worker/report.json').exists() else {}
    results[name]=dict(exit_code=p.returncode,population=report.get('population'),error=report.get('error'),restored=report.get('ieugwasr_bindings_restored'),observer_failures=report.get('observation_failures'),local_calls=len([v for v in report.get('ld_calls',[]) if v['name']=='ld_clump_local']))
    print(name,json.dumps(results[name]),flush=True)
(O/'RESULT.json').write_text(json.dumps(dict(results=results,original_source_only=True,new_catalog_credit=0,whole_goal_complete=False),indent=2)+'\n',encoding='utf-8',newline='\n')
