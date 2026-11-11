# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Validate original annotation results, captured replay, scientific corruption and a native gene-to-LSI chain."""
import argparse
import copy
import csv
import json
import math
from pathlib import Path
import shutil
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,linux,pin
from validate_signac_fragment_workflow import compare_matrix
BASE=ROOT/'build/literature-review/signac-runtime'
CLI='org.jlinalg.cli.SignacAnnotationsRequestCli'
def read(path):return json.loads(path.read_bytes())
def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def array(value):return [] if value is None else value if isinstance(value,list) else [value]
def table(path):return list(csv.DictReader(path.read_text(encoding='utf8').splitlines(),delimiter='\t'))
def compare_tss(path,want):
    rows=table(path/'tss.tsv');assert [r['cell'] for r in rows]==array(want['cells'])
    for row,score,kind,percent,pkind in zip(rows,array(want['score']),array(want['score_kind']),array(want['percentile']),array(want['percentile_kind']),strict=True):
        assert row['score_kind']==kind and row['percentile_kind']==pkind
        if kind=='FINITE':assert math.isclose(float(row['TSS.enrichment']),score,rel_tol=2e-12,abs_tol=2e-12)
        else:assert row['TSS.enrichment']=={'NA':'NA','NAN':'NaN','POSITIVE_INFINITY':'Inf'}[kind]
        if pkind=='FINITE':assert float(row['TSS.percentile'])==percent
        else:assert row['TSS.percentile']=={'NA':'NA','NAN':'NaN','POSITIVE_INFINITY':'Inf'}[pkind]
    if want.get('profile') is not None:
        profile=want['profile'];rows=table(path/'profile.tsv');width=len(profile['positions']);assert len(rows)==len(profile['cells'])*width
        for index,row in enumerate(rows):
            r=index//width;c=index%width;assert int(row['row'])==r+1 and row['cell']==profile['cells'][r] and row['position']==profile['positions'][c] and row['number_kind']=='FINITE'
            assert math.isclose(float(row['normalized']),profile['values'][r][c],rel_tol=2e-12,abs_tol=2e-12)
            if r<len(want['raw_profile']['values']):assert int(row['raw_count'])==want['raw_profile']['values'][r][c]
            else:assert row['raw_count']==''
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='annotation-workflow-controls-in-progress'))
    try:
        build=read(BASE/'annotation-workflow-build-1/BUILD.json');assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        original=BASE/'annotation-original-2';cases=read(original/'cases.json');expected=read(original/'original.json');names=['tools/literature/validate_signac_annotation_workflow.py','tools/literature/validate_signac_fragment_workflow.py'];sources={n:pin(ROOT/n) for n in names};sources.update({(original/n).relative_to(ROOT).as_posix():pin(original/n) for n in ['cases.json','original.json']});save(out/'SOURCE-PINS.json',sources)
        for name in sources:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        platforms=[('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]
        def command(name,argv,code=0):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==code,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-900:])
        def launch(name,request,code=0,platform=0):
            path=out/(name+'-request.json');save(path,request);_,java,convert=platforms[platform];command(name,[*java,'-Xmx512m','-cp',convert(jar),CLI,'--request',convert(path)],code)
        def verify(name,target,code=0):
            for index,(platform,_,convert) in enumerate(platforms):launch(name+'-'+platform,dict(schema_version=1,mode='VERIFY',analysis=convert(target)),code,index)
        inputs=out/'Unicode α inputs';inputs.mkdir()
        for file in cases['files']:
            for suffix in ['.tsv','.tsv.gz']:shutil.copyfile(original/(file['id']+suffix),inputs/(file['id']+suffix))
        before={p.name:pin(p) for p in inputs.iterdir()};runs=[];failed=[];analyses={};requests={}
        def common(case,index,target,mode):
            result=dict(schema_version=1,mode=mode,out=str(target),genome_build='synthetic-controls-not-an-assembly',process_n=case['process_n']);result['sources']=[]
            for k,source in enumerate(case['sources']):
                value=dict(path=str(inputs/(source['file']+('.tsv' if index%2==0 else '.tsv.gz'))))
                if source['map'] is not None:
                    if index%2:
                        path=inputs/(case['id']+'-'+str(k)+'-map.tsv');path.write_text('cell\tbarcode\n'+''.join(v['cell']+'\t'+v['barcode']+'\n' for v in source['map']),encoding='utf8',newline='\n');value['map_file']=str(path)
                    else:value['map']=source['map']
                result['sources'].append(value)
            if index%2:
                path=inputs/(case['id']+'-cells.tsv');path.write_text('\n'.join(case['cells'])+'\n',encoding='utf8',newline='\n');result['object_cells_file']=str(path)
            else:result['object_cells']=case['cells']
            return result
        tss_ids=['endpoints-True-2000','endpoints-False-2000','random-True-4','random-False-2000','center-only-True-2000','center-only-False-2000','left-trim-True','right-trim-True','extension-False-500','cells-argument-True','mapped-False','subset-mapping-False','multi-shared-False','multi-disjoint-True','absent-True','endpoints-True-1','only-mito-True','multi-pruning-False']
        for index,name in enumerate(tss_ids):
            case=next(v for v in cases['tss_cases'] if v['id']==name);want=next(v['result']['value'] for v in expected['tss'] if v['id']==name);target=out/('tss-'+name);request=common(case,index,target,'TSS_FAST' if case['fast'] else 'TSS_PROFILE');path=inputs/(name+'-positions.tsv');lengths=case.get('seqlengths',{});path.write_text('chromosome\tstart\tend\tstrand\tsequence_length\n'+''.join(f"{v['chromosome']}\t{v['start']}\t{v['end']}\t{v['strand']}\t{lengths.get(v['chromosome'],'.')}\n" for v in case['positions']),encoding='utf8',newline='\n');request.update(positions=str(path),region_extension=case['extension'])
            if case['n'] is not None:request['first_n']=case['n']
            if case.get('selected_cells') is not None:request['cells']=case['selected_cells']
            code=2 if 'error' in want else 0;launch('tss-'+name,request,code);verify('verify-tss-'+name,target,code)
            if code:failed.append('tss-'+name);continue
            compare_tss(target,want);runs.append(dict(mode=request['mode'],case=name,original_result_identical_with_tolerance=True));analyses['tss-'+name]=target;requests['tss-'+name]=request;print('passed TSS request '+name,flush=True)
        gene_ids=['default-2000-False','all-biotypes','lincRNA','symbol-filter','width500','coordinate-aliases-multi','mapped','multi-disjoint','empty-feature-list','missing-biotype']
        for index,name in enumerate(gene_ids):
            case=next(v for v in cases['gene_cases'] if v['id']==name);want=next(v['result']['value'] for v in expected['gene_activity'] if v['id']==name);target=out/('gene-'+name);request=common(case,index,target,'GENE_ACTIVITY');path=inputs/(name+'-annotation.tsv');path.write_text('chromosome\tstart\tend\tstrand\tgene_id\tgene_name\tgene_biotype\n'+''.join(f"{v['chromosome']}\t{v['start']}\t{v['end']}\t{v['strand']}\t{v['gene_id']}\t{v['gene_name'] or ''}\t{v['gene_biotype']}\n" for v in case['annotation']),encoding='utf8',newline='\n');request.update(annotation=str(path),biotypes=case['biotypes'],max_width=case['max_width'],upstream=case['upstream'],downstream=case['downstream'],gene_ids=case['gene_id'],sort_records=16)
            if case['features'] is not None:
                if index%2:
                    path=inputs/(name+'-features.tsv');path.write_text('\n'.join(case['features'])+('\n' if case['features'] else ''),encoding='utf8',newline='\n');request['features_file']=str(path)
                else:request['features']=case['features']
            code=2 if 'error' in want else 0;launch('gene-'+name,request,code);verify('verify-gene-'+name,target,code)
            if code:failed.append('gene-'+name);continue
            compare_matrix(target,want['matrix']);runs.append(dict(mode='GENE_ACTIVITY',case=name,original_result_identical=True));analyses['gene-'+name]=target;requests['gene-'+name]=request;print('passed gene request '+name,flush=True)
        fresh=[]
        for platform in [1,2]:
            for key in ['tss-endpoints-True-2000','tss-endpoints-False-2000','gene-default-2000-False']:
                request=copy.deepcopy(requests[key]);_,_,convert=platforms[platform];target=out/(key+'-fresh-'+str(platform));request['out']=convert(target)
                for field in ['positions','annotation','object_cells_file','features_file']:
                    if field in request:request[field]=convert(Path(request[field]))
                for value in request['sources']:
                    for field in ['path','map_file']:
                        if field in value:value[field]=convert(Path(value[field]))
                launch(key+'-fresh-'+str(platform),request,platform=platform)
                for file in ['tss.tsv','profile.tsv','matrix.mtx','features.tsv','cells.tsv','selected-tss.tsv','selected-genes.tsv','operation.json','WARNINGS.md']:
                    if (analyses[key]/file).exists():assert (analyses[key]/file).read_bytes()==(target/file).read_bytes()
                fresh.append(dict(platform=platforms[platform][0],mode=request['mode'],identities_and_results_preserved=True))
        def reseal(path):
            inventory=path/'artifact-checksums.tsv';inventory.write_text('sha256\tpath\n'+''.join(pin(p)['sha256']+'\t'+p.relative_to(path).as_posix()+'\n' for p in sorted(path.rglob('*')) if p.is_file() and p!=inventory),encoding='utf8',newline='\n')
        mutations=[('score','tss-endpoints-True-2000','tss.tsv'),('profile','tss-endpoints-False-2000','profile.tsv'),('gene-counts','gene-default-2000-False','matrix.mtx'),('selected-tss','tss-endpoints-True-2000','selected-tss.tsv'),('selected-genes','gene-default-2000-False','selected-genes.tsv'),('options','tss-endpoints-True-2000','request.json'),('operation','gene-default-2000-False','operation.json'),('warnings','tss-endpoints-True-2000','WARNINGS.md'),('manifest','tss-endpoints-True-2000','manifest.json'),('source-request','tss-endpoints-True-2000','source-request.json')];rejections=[]
        for name,key,file in mutations:
            target=out/('tamper-'+name);shutil.copytree(analyses[key],target);path=target/file
            if name=='warnings':path.write_text('All results are safe. No action needed.\n',encoding='utf8',newline='\n')
            elif file.endswith('.json'):
                value=read(path)
                if name=='options':value['genome_build']='altered'
                elif name=='operation':value['passes'][0]['records']+=1
                elif name=='manifest':value['mode']='GENE_ACTIVITY'
                else:value['region_extension']=999
                save(path,value)
            else:
                lines=path.read_text(encoding='utf8').splitlines();delimiter=' ' if file=='matrix.mtx' else '\t';changed_line=2 if file=='matrix.mtx' else 1;fields=lines[changed_line].split(delimiter)
                if name=='score':fields[1]='999.0'
                elif name=='profile':fields[4]='999.0'
                elif name=='gene-counts':fields[2]=str(int(fields[2])+1)
                elif name=='selected-tss':fields[1]=str(int(fields[1])+1)
                else:fields[4]='altered-gene-id'
                lines[changed_line]=delimiter.join(fields);path.write_text('\n'.join(lines)+'\n',encoding='utf8',newline='\n')
            reseal(target);verify('reject-'+name,target,2);rejections.append(dict(mutation=name,rejected_platforms=3))
        target=out/'tamper-nested-inventory';shutil.copytree(analyses['tss-endpoints-True-2000'],target);(target/'input/artifact-checksums.tsv').write_text('unlisted\n',encoding='utf8',newline='\n');verify('reject-nested-inventory',target,2)
        existing=analyses['gene-default-2000-False'];before_existing={p.relative_to(existing).as_posix():pin(p) for p in existing.rglob('*') if p.is_file()};launch('existing-output',requests['gene-default-2000-False'],2);assert before_existing=={p.relative_to(existing).as_posix():pin(p) for p in existing.rglob('*') if p.is_file()}
        unicode_runs=[]
        for key,kind in [('tss-mapped-False','tss'),('gene-mapped','gene')]:
            request=copy.deepcopy(requests[key]);mapping={'object-A':'cell β','object-B':'cell α','object-Z':'cell γ'};request.pop('object_cells_file',None);case_name='mapped-False' if kind=='tss' else 'mapped';case=next(v for v in cases['tss_cases' if kind=='tss' else 'gene_cases'] if v['id']==case_name);request['object_cells']=[mapping[v] for v in case['cells']]
            request['sources'][0].pop('map_file',None);request['sources'][0]['map']=[dict(cell=mapping[v['cell']],barcode=v['barcode']) for v in case['sources'][0]['map']]
            target=out/'Unicode β output'/kind;request['out']=str(target);want=copy.deepcopy(next(v['result']['value'] for v in expected['tss' if kind=='tss' else 'gene_activity'] if v['id']==case_name))
            if kind=='gene':
                path=inputs/'Unicode gene annotation.tsv';path.write_text(Path(request['annotation']).read_text(encoding='utf8').replace('DUP','DUPα'),encoding='utf8',newline='\n');request['annotation']=str(path);want['matrix']['cells']=[mapping[v] for v in want['matrix']['cells']];want['matrix']['features']=[v.replace('DUP','DUPα') for v in want['matrix']['features']]
            else:
                want['cells']=[mapping[v] for v in want['cells']];want['profile']['cells']=[mapping.get(v,v) for v in want['profile']['cells']]
            launch('Unicode-'+kind,request);verify('verify-Unicode-'+kind,target)
            if kind=='gene':compare_matrix(target,want['matrix'])
            else:compare_tss(target,want)
            unicode_runs.append(dict(mode=request['mode'],exact_UTF8_ids_preserved=True,captured_verifications=3))
        # Annotation-derived TSS is a distinct request path; original reduction is separately controlled.
        derived=copy.deepcopy(requests['gene-default-2000-False']);derived={k:v for k,v in derived.items() if k in ['schema_version','sources','object_cells','object_cells_file','genome_build','annotation']};derived.update(mode='TSS_FAST',out=str(out/'annotation-derived-tss'));launch('annotation-derived-tss',derived);verify('verify-annotation-derived-tss',out/'annotation-derived-tss')
        # Omit the explicitly zero synthetic Z column for a usable downstream SVD demonstration.
        gene_request=copy.deepcopy(requests['gene-default-2000-False']);gene_request['object_cells']=['D','B','A','C'];gene_request.pop('object_cells_file',None);matrix=out/'gene-to-LSI-counts';gene_request['out']=str(matrix);launch('gene-to-LSI-counts',gene_request)
        target=out/'gene-to-LSI';path=out/'lsi-request.json';save(path,dict(schema_version=1,mode='FIT',options={'counts':str(matrix/'matrix.mtx'),'features':str(matrix/'features.tsv'),'cells':str(matrix/'cells.tsv'),'out':str(target),'rscript':'C:/Program Files/R/R-4.6.1/bin/Rscript.exe','r-library':str(BASE/'reference-setup-1/R-lib'),'r-base-library':str(ROOT/'build/omics-sctransform-r'),'profile':'1.17.1-package-rspectra','components':2,'seed':4901}));command('gene-to-LSI',[JAVA21/'java.exe','-Xmx512m','-cp',jar,'org.jlinalg.cli.SignacLsiRequestCli','--request',path])
        for platform,java,convert in platforms:
            path=out/('LSI-verify-'+platform+'.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command('gene-to-LSI-verify-'+platform,[*java,'-cp',convert(jar),'org.jlinalg.cli.SignacLsiRequestCli','--request',convert(path)])
        assert all(pin(inputs/n)==v for n,v in before.items()) and not any(p.name=='sort' for p in out.rglob('*')) and all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items())
        result=dict(status='passed-captured-native-Signac-annotation-workflows',source_pins=sources,artifact=build['artifact'],original_comparisons=runs,captured_verifications=len(runs)*3,fresh_additional_platform_runs=fresh,rehashed_rejections=rejections,uninventoried_nested_file_rejections=3,unicode_identity_runs=unicode_runs,retained_failure_gates=failed+['existing-output-preserved'],annotation_derived_TSS_actual_run=True,annotation_derived_TSS_verifications=3,gene_to_LSI_actual_fits=1,gene_to_LSI_verifications=3,inputs_preserved=True,sort_scratch_removed=True,full_Signac_workflow_available=False,catalog_implementation_credit=False);save(out/'COMPLETED.json',result);(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status=result['status'],original_comparisons=len(runs),captured_verifications=result['captured_verifications'],rehashed_rejections=len(rejections)*3)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_CAPTURED_ANNOTATION_WORKFLOW',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable captured annotation verification')
    main()
