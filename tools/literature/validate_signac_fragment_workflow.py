# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise captured native fragment workflows, original results, replay and failure gates."""
import argparse
import copy
import csv
import gzip
import json
from pathlib import Path
import shutil
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,linux,pin
BASE=ROOT/'build/literature-review/signac-runtime'
CLI='org.jlinalg.cli.SignacFragmentsRequestCli'

def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def read(path):return json.loads(path.read_bytes())
def array(value):return [] if value is None else value if isinstance(value,list) else [value]
def table(path):return list(csv.DictReader(path.read_text(encoding='utf8').splitlines(),delimiter='\t'))
def csc(path):
    lines=[v for v in path.read_text().splitlines() if not v.startswith('%')];nr,nc,nnz=map(int,lines[0].split());triples=[tuple(map(int,v.split())) for v in lines[1:]];assert len(triples)==nnz
    p=[0];rows=[];values=[]
    for col in range(nc):
        selected=[v for v in triples if v[1]==col+1];rows.extend(v[0]-1 for v in selected);values.extend(v[2] for v in selected);p.append(len(rows))
    assert len(rows)==nnz
    return nr,nc,p,rows,values
def compare_matrix(path,expected):
    features=array(expected['features']);cells=array(expected['cells']);nr,nc,p,i,x=csc(path/'matrix.mtx')
    assert (path/'features.tsv').read_text(encoding='utf8').splitlines()==features
    assert (path/'cells.tsv').read_text(encoding='utf8').splitlines()==cells
    assert (nr,nc,p,i,x)==(len(features),len(cells),array(expected['p']),array(expected['i']),array(expected['x']))

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='native-fragment-request-workflows-in-progress'))
    try:
        build=read(BASE/'fragment-workflow-build-3/BUILD.json');assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        original=BASE/'fragments-original-4';cases=read(original/'cases.json');expected=read(original/'original.json');binary=BASE/'binarize-original-2';bcases=read(binary/'cases.json');bexpected=read(binary/'original.json')
        sources={str(Path(__file__).relative_to(ROOT)).replace('\\','/'):pin(Path(__file__))}
        for name in ['cases.json','original.json']:
            for directory in [original,binary]:sources[(directory/name).relative_to(ROOT).as_posix()]=pin(directory/name)
        save(out/'SOURCE-PINS.json',sources)
        for name in sources:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        def command(name,argv,code=0,timeout=120):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==code,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1000:]);return r
        def launch(name,request,code=0,platform=0):
            path=out/(name+'-request.json');save(path,request);_,java,convert=platforms[platform];return command(name,[*java,'-Xmx256m','-cp',convert(jar),CLI,'--request',convert(path)],code)
        def verify(name,path,code=0):
            for index,(platform,_,convert) in enumerate(platforms):launch(name+'-'+platform,dict(schema_version=1,mode='VERIFY',analysis=convert(path)),code,index)
        inputs=out/'Unicode α inputs';inputs.mkdir()
        for file in cases['files']:
            shutil.copyfile(original/(file['id']+'.tsv'),inputs/(file['id']+'.tsv'));shutil.copyfile(original/(file['id']+'.tsv.gz'),inputs/(file['id']+'.bgz'))
            (inputs/(file['id']+'.ordinary.gz')).write_bytes(gzip.compress((original/(file['id']+'.tsv')).read_bytes(),mtime=0))
        before={p.name:pin(p) for p in inputs.iterdir()};runs=[];failed=[];analyses={}
        count_ids=['boundary-all-0','boundary-whitelist-1','boundary-whitelist-0','random-all-0','six-column-whitelist-5','pooled-5']
        for index,name in enumerate(count_ids):
            case=next(v for v in cases['count_cases'] if v['id']==name);target=out/('count-'+name);suffix=['.tsv','.bgz','.ordinary.gz'][index%3];request=dict(schema_version=1,mode='COUNT_FRAGMENTS',out=str(target),files=[str(inputs/(v+suffix)) for v in case['files']],max_lines=case['lines'])
            if case['cells'] is not None:request['cells']=case['cells']
            launch('count-'+name,request);actual=table(target/'counts.tsv');want=next(v for v in expected['counts'] if v['id']==name)['result']['value'];want=array(want);assert [{k:(v if k=='CB' else int(v)) for k,v in row.items()} for row in actual]==want
            verify('verify-count-'+name,target);analyses['count-'+name]=target;runs.append(dict(mode='COUNT_FRAGMENTS',case=name,original_identical=True));print('passed count '+name,flush=True)
        matrix_ids=['boundary-discover-False-1','boundary-explicit-True-1','boundary-explicit-True-4','boundary-explicit-True-2000','boundary-mapped-False-2000','six-column-discover-False-2000','random-discover-False-2000','multi-disjoint-False','multi-disjoint-True','no-overlap-discover','no-overlap-explicit','mapping-filter','shared-cell-quantification','omitted-requested','empty-chunks','no-common-chromosome']
        def fragment_sources(case,index):
            values=[]
            for k,source in enumerate(case['sources']):
                value=dict(path=str(inputs/(source['file']+['.tsv','.bgz','.ordinary.gz'][index%3])))
                if source['map'] is not None:
                    if index%2:
                        path=inputs/(case['id']+'-'+str(k)+'-map.tsv');path.write_text('cell\tbarcode\n'+''.join(v['cell']+'\t'+v['barcode']+'\n' for v in source['map']),encoding='utf8',newline='\n');value['map_file']=str(path)
                    else:value['map']=source['map']
                values.append(value)
            return values
        for index,name in enumerate(matrix_ids):
            case=next(v for v in cases['matrix_cases'] if v['id']==name);target=out/('matrix-'+name);regions=inputs/(name+'-regions.tsv');regions.write_text('chromosome\tstart\tend\n'+''.join(f"{v['chromosome']}\t{v['start']}\t{v['end']}\n" for v in case['regions']),encoding='utf8',newline='\n')
            request=dict(schema_version=1,mode='FEATURE_MATRIX',out=str(target),sources=fragment_sources(case,index),regions=str(regions),genome_build='synthetic-controls-only',keep_all_features=case['keep'],process_n=case['chunk'],sort_records=16)
            if case['cells'] is not None:
                if index%2:cellfile=inputs/(name+'-cells.tsv');cellfile.write_text('\n'.join(case['cells'])+'\n',encoding='utf8',newline='\n');request['cells_file']=str(cellfile)
                else:request['cells']=case['cells']
            result=next(v for v in expected['quantification'] if v['id']==name)['result'];code=2 if 'error' in result['value'] else 0;launch('matrix-'+name,request,code)
            if code:verify('verify-failed-'+name,target,2);failed.append(name);continue
            compare_matrix(target,result['value']);verify('verify-matrix-'+name,target);analyses['matrix-'+name]=target;runs.append(dict(mode='FEATURE_MATRIX',case=name,original_identical=True));print('passed matrix '+name,flush=True)
        qc_names=['boundary-discover-True-2000','boundary-mapped-True-2000','random-discover-True-2000','multi-disjoint-False','undefined-nucleosome']
        for index,name in enumerate(qc_names):
            case=next(v for v in cases['matrix_cases'] if v['id']==name)
            for n in case['nucleosome_lines'][:2]:
                result=next(v for v in expected['nucleosome'] if v['id']==name and v['lines']==n)['result'];suffix=name+'-'+str(n);target=out/('qc-'+suffix);object_cells=case['object_cells']
                assert object_cells is not None
                request=dict(schema_version=1,mode='NUCLEOSOME_SIGNAL',out=str(target),sources=fragment_sources(case,index),max_lines=n)
                if index%2:cellfile=inputs/(suffix+'-objects.tsv');cellfile.write_text('\n'.join(object_cells)+'\n',encoding='utf8',newline='\n');request['object_cells_file']=str(cellfile)
                else:request['object_cells']=object_cells
                code=2 if 'error' in result['value'] else 0;launch('qc-'+suffix,request,code)
                if code:verify('verify-failed-qc-'+suffix,target,2);failed.append('qc-'+suffix);continue
                value=result['value'];rows=table(target/'nucleosome.tsv');assert [v['cell'] for v in rows]==array(value['cells'])
                for col,kind,expectedcol,expectedkind in [('nucleosome_signal','ratio_kind','ratio','ratio_kind'),('nucleosome_percentile','percentile_kind','percentile','percentile_kind')]:
                    assert [v[kind] for v in rows]==array(value[expectedkind])
                    for row,want in zip(rows,array(value[expectedcol])):
                        if row[kind]=='FINITE':assert float(row[col])==want
                        else:assert row[col]=={'NA':'NA','NAN':'NaN','POSITIVE_INFINITY':'Inf'}[row[kind]]
                verify('verify-qc-'+suffix,target);analyses['qc-'+suffix]=target;runs.append(dict(mode='NUCLEOSOME_SIGNAL',case=suffix,original_identical=True));print('passed qc '+suffix,flush=True)
        for name in ['hand-count-versus-document-frequency','zero-row-and-cell-stored','all-zero-implicit']:
            case=next(v for v in bcases['matrices'] if v['id']==name);data=inputs/('binary-'+name);data.mkdir();(data/'features.tsv').write_text('\n'.join(case['features'])+'\n',encoding='utf8',newline='\n');(data/'cells.tsv').write_text('\n'.join(case['cells'])+'\n',encoding='utf8',newline='\n')
            triples=[]
            for col in range(len(case['cells'])):
                for k in range(case['p'][col],case['p'][col+1]):triples.append(f"{case['i'][k]+1} {col+1} {case['x'][k]}")
            (data/'matrix.mtx').write_text('%%MatrixMarket matrix coordinate integer general\n'+f"{len(case['features'])} {len(case['cells'])} {len(triples)}\n"+'\n'.join(triples)+'\n',encoding='utf8',newline='\n');target=out/('binary-'+name)
            launch('binary-'+name,dict(schema_version=1,mode='BINARIZE_CSC',out=str(target),matrix=str(data/'matrix.mtx'),features=str(data/'features.tsv'),cells_file=str(data/'cells.tsv')))
            compare_matrix(target,next(v for v in bexpected['results'] if v['id']==name));verify('verify-binary-'+name,target);analyses['binary-'+name]=target;runs.append(dict(mode='BINARIZE_CSC',case=name,original_identical=True));print('passed binary '+name,flush=True)
        # Independently launch all four native modes on the other JVMs through UTF-8 requests.
        fresh=[]
        for platform in [1,2]:
            for key in ['count-boundary-all-0','matrix-random-discover-False-2000','qc-boundary-discover-True-2000-0','binary-zero-row-and-cell-stored']:
                source=analyses[key];request=read(out/(key+'-request.json'));_,_,convert=platforms[platform];target=out/(key+'-fresh-'+str(platform));request['out']=convert(target)
                for field in ['files']:
                    if field in request:request[field]=[convert(Path(v)) for v in request[field]]
                for field in ['regions','cells_file','object_cells_file','matrix','features']:
                    if field in request:request[field]=convert(Path(request[field]))
                for value in request.get('sources',[]):
                    for field in ['path','map_file']:
                        if field in value:value[field]=convert(Path(value[field]))
                launch(key+'-fresh-'+str(platform),request,platform=platform)
                for file in ['counts.tsv','matrix.mtx','features.tsv','cells.tsv','nucleosome.tsv','operation.json']:
                    if (source/file).exists():assert (source/file).read_bytes()==(target/file).read_bytes()
                fresh.append(dict(platform=platforms[platform][0],mode=request['mode'],original_result_preserved=True))
        def reseal(path):
            lines=['sha256\tpath']+[pin(p)['sha256']+'\t'+p.relative_to(path).as_posix() for p in sorted(path.rglob('*')) if p.is_file() and p.name!='artifact-checksums.tsv'];(path/'artifact-checksums.tsv').write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
        mutations=[('counts','count-boundary-all-0','counts.tsv'),('matrix','matrix-random-discover-False-2000','matrix.mtx'),('nucleosome','qc-boundary-discover-True-2000-0','nucleosome.tsv'),('operation','count-boundary-all-0','operation.json'),('options','matrix-random-discover-False-2000','request.json'),('manifest','count-boundary-all-0','manifest.json'),('source-request','count-boundary-all-0','source-request.json'),('warning-status','count-boundary-whitelist-1','analysis-status.json'),('warnings','matrix-boundary-explicit-True-1','WARNINGS.md')]
        rejections=[]
        for name,key,file in mutations:
            target=out/('tamper-'+name);shutil.copytree(analyses[key],target);path=target/file
            if name=='warnings':path.write_text('No warnings. All counts are complete.\n',encoding='utf8',newline='\n')
            elif file.endswith('.json'):
                value=read(path)
                if name=='operation':value['max_lines_per_file']=41
                elif name=='options':value['genome_build']='altered-label'
                elif name=='manifest':value['mode']='BINARIZE_CSC'
                elif name=='source-request':value['max_lines']=999
                else:value['status']='completed'
                save(path,value)
            else:
                lines=path.read_text().splitlines();fields=lines[-1].split('\t' if file.endswith('.tsv') else ' ')
                if name=='nucleosome':fields[1]='99.0'
                else:fields[-1]=str(int(fields[-1])+1)
                lines[-1]=('\t' if file.endswith('.tsv') else ' ').join(fields);path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            reseal(target);verify('reject-'+name,target,2);rejections.append(dict(mutation=name,rejected_platforms=3))
        target=out/'tamper-uninventoried-nested-checksum';shutil.copytree(analyses['count-boundary-all-0'],target);(target/'input/artifact-checksums.tsv').write_text('extra unlisted content\n',encoding='utf8',newline='\n');verify('reject-nested-checksum',target,2)
        # Existing destinations and parser/decompression failures retain explicit failed attempts.
        existing=analyses['count-boundary-all-0'];prior={p.relative_to(existing).as_posix():pin(p) for p in existing.rglob('*') if p.is_file()};launch('existing-destination',read(out/'count-boundary-all-0-request.json'),2);assert prior=={p.relative_to(existing).as_posix():pin(p) for p in existing.rglob('*') if p.is_file()}
        broken=inputs/'broken.gz';raw=bytearray((inputs/'boundary.ordinary.gz').read_bytes());raw[-8]^=1;broken.write_bytes(raw);target=out/'bad-gzip';launch('bad-gzip',dict(schema_version=1,mode='COUNT_FRAGMENTS',files=[str(broken)],out=str(target)),2);verify('verify-bad-gzip',target,2);failed.append('bad-gzip')
        for name,value in before.items():assert pin(inputs/name)==value
        assert not list(out.rglob('*.tmp')) and not any(p.name=='sort' for p in out.rglob('*'))
        # Feed an actual native exported matrix to the previously validated exact-package LSI route.
        matrix=analyses['matrix-random-discover-False-2000'];target=out/'native-matrix-to-LSI';request=dict(schema_version=1,mode='FIT',options={'counts':str(matrix/'matrix.mtx'),'features':str(matrix/'features.tsv'),'cells':str(matrix/'cells.tsv'),'out':str(target),'rscript':'C:/Program Files/R/R-4.6.1/bin/Rscript.exe','r-library':str(BASE/'reference-setup-1/R-lib'),'r-base-library':str(ROOT/'build/omics-sctransform-r'),'profile':'1.17.1-package-rspectra','components':2,'seed':4901})
        path=out/'lsi-chain-request.json';save(path,request);command('lsi-chain',[JAVA21/'java.exe','-Xmx512m','-cp',jar,'org.jlinalg.cli.SignacLsiRequestCli','--request',path])
        for platform,java,convert in platforms:
            path=out/('lsi-chain-verify-'+platform+'.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command('lsi-chain-verify-'+platform,[*java,'-cp',convert(jar),'org.jlinalg.cli.SignacLsiRequestCli','--request',convert(path)])
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        result=dict(status='passed-native-fragment-captured-workflows-and-original-comparisons',source_pins=sources,artifact=build['artifact'],original_comparisons=runs,captured_verifications=len(runs)*3,fresh_additional_platform_runs=fresh,rehashed_rejections=rejections,uninventoried_nested_file_rejections=3,failure_gates=failed+['existing-destination-preserved'],input_files_unchanged=True,owned_sort_scratch_removed=True,native_export_to_exact_package_LSI=dict(actual_fits=1,captured_verifications=3,components=2),full_Signac_workflow_available=False,catalog_implementation_credit=False)
        save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],original_comparisons=len(runs),captured_verifications=result['captured_verifications'],rehashed_rejections=len(rejections)*3)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_NATIVE_FRAGMENT_REQUEST_WORKFLOW',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable native workflow validation')
    main()
