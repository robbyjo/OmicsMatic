# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run actual original Hail core/MatrixTable/liftover/Batch workflows with complete literal inputs. No integration credit or cloud execution."""
import argparse,gzip,json,struct,subprocess,zlib
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import linux

def fixture_inputs(out):
    inputs=out/'literal-inputs';inputs.mkdir();header='''##fileformat=VCFv4.2
##contig=<ID=1,length=249250621>
##FORMAT=<ID=GT,Number=1,Type=String,Description="Genotype">
##FORMAT=<ID=DP,Number=1,Type=Integer,Description="Read depth">
##FORMAT=<ID=GQ,Number=1,Type=Integer,Description="Genotype quality">
#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tS3\tS1\tS2\tS4\tS5\tS6\tS7\tS8
''';rows=[];calls=[['0/0','0/1','1/1','0/1','0/0','1/1','0/1','0/0'],['0/0','0/1','1/1','0/1','0/0','1/1','0/1','0/0'],['1/1','0/0','0/1','1/1','0/1','0/0','0/1','0/0'],['0/1','1/1','0/0','0/1','1/1','0/0','0/1','0/0'],['0/0','1/1','0/1','1/1','0/0','0/1','0/1','0/0'],['0/1','0/0','1/1','0/0','0/1','0/0','1/1','0/1'],['1/1','0/1','0/0','0/1','0/0','0/0','0/1','1/1'],['0/0','0/1','1/1','0/0','1/1','0/1','0/0','0/1']]
    for i,genotypes in enumerate(calls):rows.append('\t'.join(['1',str(1000+i*10),'rs'+str(i),'A','G','.','PASS','.','GT:DP:GQ',*[gt+':'+str(5+(i+j)%6*5)+':'+str(10+(i+j)%4*15) for j,gt in enumerate(genotypes)]]))
    (inputs/'biallelic.vcf').write_text(header+'\n'.join(rows)+'\n',encoding='utf8',newline='\n');mixed=rows+[ '\t'.join(['1','1200','tri','A','G,C','.','PASS','.','GT:DP:GQ','0/2:30:40','1/2:20:50','2/2:10:30','.:12:10','0:5:20','1:40:30','0|1:50:50','./.:.:.'])];(inputs/'mixed.vcf').write_text(header+'\n'.join(mixed)+'\n',encoding='utf8',newline='\n')
    def bgzf(raw):
        encoder=zlib.compressobj(wbits=-15);payload=encoder.compress(raw)+encoder.flush();size=18+len(payload)+8;assert len(raw)<=65536 and size<=65536;return struct.pack('<BBBBIBBH',31,139,8,4,0,0,255,6)+b'BC'+struct.pack('<HH',2,size-1)+payload+struct.pack('<II',zlib.crc32(raw)&0xffffffff,len(raw))
    original=(inputs/'mixed.vcf').read_bytes();compressed=bgzf(original)+bgzf(b'');assert gzip.decompress(compressed)==original and compressed[-28:]==bytes.fromhex('1f8b08040000000000ff0600424302001b0003000000000000000000');(inputs/'mixed.vcf.bgz').write_bytes(compressed)
    chain='chain 1000 1 249250621 + 1000 1100 chr1 248956422 + 100 200 1\n100\n\nchain 900 1 249250621 + 2000 2100 chr1 248956422 - 300 400 2\n100\n\n';(inputs/'synthetic.chain').write_text(chain,encoding='utf8',newline='\n');save(inputs/'loci.json',[dict(id='site-'+str(i),contig='1',position=position,alleles=['A','G']) for i,position in enumerate([1000,1001,1050,1100,1101,2001,2050,2100,2101])]);(inputs/'chunk-one.tsv').write_text('gene-A\t1\ngene-B\t2\ngene-A\t3\n',encoding='utf8',newline='\n');(inputs/'chunk-two.tsv').write_text('gene-B\t5\ngene-C\t7\n',encoding='utf8',newline='\n');program='''import collections,json,pathlib,sys
data=collections.defaultdict(int)
for name in sys.argv[2:]:
 for line in pathlib.Path(name).read_text().splitlines():
  key,value=line.split('\\t');data[key]+=int(value)
pathlib.Path(sys.argv[1]).write_text(''.join(key+'\\t'+str(value)+'\\n' for key,value in sorted(data.items())))
''';(inputs/'sum_rows.py').write_text(program,encoding='utf8',newline='\n');return inputs

def cases(out,inputs,python,version):
    def field(axis,*path):return dict(field=dict(axis=axis,path=list(path)))
    def literal(value):return dict(literal=value)
    def binary(op,left,right):return dict(operator=op,left=left,right=right)
    def path(name):return linux(inputs/name)
    def vcf(name):return dict(kind='vcf',path=path(name),reference_genome='GRCh37',force_bgz=False,contig_recoding={})
    def op(id,operation,**params):return dict(id=id,operation=operation,**params)
    raw=dict(id='raw-mixed',kind='matrix-pipeline',input=vcf('mixed.vcf'),steps=[])
    qc=dict(id='filtered-qc',kind='matrix-pipeline',input=vcf('mixed.vcf'),steps=[op('sample-before','sample_qc',name='sample_before'),op('entry-mask','filter_entries',predicate=binary('and',binary('ge',field('entry','DP'),literal(10)),binary('ge',field('entry','GQ'),literal(20))),keep=True),op('sample-after','sample_qc',name='sample_after'),op('variant-after','variant_qc',name='variant_after'),op('maf','annotate',axis='rows',fields={'minor_allele_frequency':dict(function='min',args=[field('row','variant_after','AF')])}),op('row-mask','filter_rows',predicate=binary('ge',field('row','variant_after','call_rate'),literal(.5)),keep=True),op('sample-mask','filter_cols',predicate=binary('gt',field('col','sample_after','n_called'),literal(0)),keep=True)])
    ld=dict(id='ld-pca',kind='matrix-pipeline',input=vcf('biallelic.vcf'),steps=[op('variant-qc','variant_qc',name='variant_qc'),op('ld','ld_matrix',radius=1000000,block_size=16),op('pca','hwe_normalized_pca',k=2,compute_loadings=True),op('prune','ld_prune',r2=.05,bp_window_size=1000000,memory_per_core=128,keep_higher_maf=True,block_size=16)])
    readback=dict(id='matrix-roundtrip',kind='matrix-pipeline',input={'kind':'matrix-table','path':linux(out/'filtered-qc/result.mt')},steps=[])
    synthetic_summary=dict(id='make-synthetic-summary',kind='matrix-pipeline',input=vcf('biallelic.vcf'),steps=[op('gene-id','annotate',axis='rows',fields={'gene_id':dict(function='str',args=[field('row','locus','position')])}),op('phenotype','annotate',axis='cols',fields={'phenotype_id':field('col','s')}),op('summary-entry','annotate',axis='entries',fields={'p_value':literal(.1),'source_missing':dict(missing='float64'),'source_nan':dict(function='float64',args=[literal('NaN')])}),op('gene-key','key_rows_by',fields=['gene_id']),op('phenotype-key','key_cols_by',fields=['phenotype_id']),op('only-gene','select_rows',fields=[]),op('only-phenotype','select_cols',fields=[]),op('only-summary','select_entries',fields=['p_value','source_missing','source_nan'])])
    summary=dict(id='summary-resource-read',kind='matrix-pipeline',input={'kind':'matrix-table','path':linux(out/'make-synthetic-summary/result.mt')},steps=[])
    lift=dict(id='liftover-strand-missing',kind='liftover',input=path('loci.json'),chain=path('synthetic.chain'),source_reference='GRCh37',destination_reference='GRCh38',min_match=.95,unmapped_policy='KEEP_MISSING',allele_policy='KEEP_SOURCE_REPORT_STRAND')
    batch=dict(id='batch-resource-dag',kind='batch-local',backend='LOCAL',inputs=[dict(id=id,path=path(name)) for id,name in [('program','sum_rows.py'),('one','chunk-one.tsv'),('two','chunk-two.tsv')]],jobs=[dict(id='left',argv=[python,{'input':'program'},{'output':'table'},{'input':'one'}],outputs=['table'],depends_on=[]),dict(id='right',argv=[python,{'input':'program'},{'output':'table'},{'input':'two'}],outputs=['table'],depends_on=[]),dict(id='reduce',argv=[python,{'input':'program'},{'output':'table'},{'job':'left','output':'table'},{'job':'right','output':'table'}],outputs=['table'],depends_on=['left','right']),dict(id='report',argv=[python,{'input':'program'},{'output':'table'},{'job':'reduce','output':'table'}],outputs=['table'],depends_on=[])])
    axis=dict(id='axis-filtering',kind='matrix-pipeline',input=vcf('mixed.vcf'),steps=[op('locus-mask','filter_rows',predicate=binary('le',field('row','locus','position'),literal(1050)),keep=True),op('column-mask','filter_cols',predicate=binary('ne',field('col','s'),literal('S8')),keep=True)])
    compressed=dict(id='bgzf-roundtrip',kind='matrix-pipeline',input=vcf('mixed.vcf.bgz'),steps=[]);return dict(schema_version=1,expected_hail=version,limits=dict(maximum_rows=100,maximum_columns=100,maximum_entries=10000,maximum_dense_ld_cells=10000),cases=[raw,qc,ld,readback,synthetic_summary,summary,lift,batch,axis,compressed])

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--setup',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/hail-runtime') and not out.exists();out.mkdir(parents=True);source=ROOT/'src/main/resources/org/jlinalg/omics/hail-workflow/worker.py';supervisor=ROOT/'src/main/resources/org/jlinalg/omics/hail-workflow/supervisor.py';sources=[Path(__file__).resolve(),source,supervisor];pins={p.relative_to(ROOT).as_posix():pin(p) for p in sources};state=dict(status='SKIP3_HAIL_WORKFLOW_REFERENCE_UNAVAILABLE',source_pins=pins,implementation_credit=False,whole_goal_complete=False)
    for path in sources:(out/path.name).write_bytes(path.read_bytes())
    try:
        setup=json.loads((a.setup/'COMPLETED.json').read_bytes());assert setup['status']=='PASS_BOUND_ORIGINAL_HAIL_WORKFLOW_REFERENCE_ONLY' and setup['runtime_trees_unchanged'] is True;inputs=fixture_inputs(out);save(out/'literal-inputs.json',{p.name:pin(p) for p in inputs.iterdir()});save(out/'cases.json',cases(out,inputs,setup['runtime_python'],setup['health']['hail']));state['status']='FAIL2_HAIL_WORKFLOW_REFERENCE_INCOMPLETE';command=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/python3','-I','-B',linux(out/supervisor.name),'--runtime',linux(a.setup.resolve()/'COMPLETED.json'),'--out',linux(out),'--worker',linux(out/source.name),'--request',linux(out/'cases.json'),'--timeout','1200']
        with (out/'supervisor.stdout').open('xb') as stdout,(out/'supervisor.stderr').open('xb') as stderr:r=subprocess.run(command,cwd=ROOT,stdout=stdout,stderr=stderr,stdin=subprocess.DEVNULL,creationflags=subprocess.CREATE_NO_WINDOW)
        save(out/'supervisor.process.json',dict(command=command,exit_code=r.returncode));report=json.loads((out/'COMPLETED.json').read_bytes());assert r.returncode==0 and report['status']=='PASS_COMPLETE_ORIGINAL_HAIL_WORKFLOW_CANDIDATE_ONLY' and len(report['cases'])==10;assert json.loads((out/'original.process.json').read_bytes())['owned_live_after_cleanup']==[];assert all(pin(ROOT/name)==value for name,value in pins.items());report.update(source_pins=pins);save(out/'COMPLETED.json',report);print(report['status'],len(report['cases']),'complete original workflows')
    except Exception as failure:state.update(error=repr(failure),required_action='No result from this atomic batch is admitted. Preserve literal inputs, complete provisional native Hail outputs, stdout/stderr, original-error.traceback and owned-process receipts. Correct the named request/runtime/capacity failure and repeat in NEW paths. If required installation is forbidden or unavailable, skip dependent analysis (SKIP3). This development worker is not a public CLI or full Hail integration.');save(out/'SUPERVISOR-ERROR.json',state);(out/'WARNINGS.md').write_text('# '+state['status']+'\n\n'+state['required_action']+'\n\n'+repr(failure)+'\n',encoding='utf8');print(state['status'],repr(failure));return 3 if state['status'].startswith('SKIP3') else 2
    return 0

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original workflow checks')
    raise SystemExit(main())
