# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare the native planner to actual public original getBM submissions, complete source chunks, warnings and error admission on three JVMs."""
import argparse,concurrent.futures,json,subprocess
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,linux

def vector(v):return v if isinstance(v,list) else [v]

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--reference',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/biomart-runtime') and not out.exists();out.mkdir(parents=True);build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};reference=a.reference.resolve();original=json.loads((reference/'COMPLETED.json').read_bytes());assert original['status']=='passed-original-public-getBM-protocol-reference-capture-only';source=ROOT/'tools/literature/BioMartQueryMatrix.java';pins={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),source]};pins.update(build['source_pins']);state=dict(status='FAIL2_NATIVE_BIOMART_QUERY_COMPARISONS_INCOMPLETE',artifact=build['artifact'],source_pins=pins,JVMs=[],annotation_client_admitted=False,implementation_credit=False,whole_goal_complete=False)
    for name in pins:
        target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
    def execute(name,command):
        r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=300);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode));return r.returncode
    try:
        classes=out/'driver-classes';classes.mkdir();assert execute('compile',[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-encoding','UTF-8','-cp',str(jar),'-d',str(classes),str(source)])==0;cases=reference/'cases.json';commands={'windows-java21':[str(JAVA21/'java.exe'),'-cp',str(classes)+';'+str(jar),'org.jlinalg.cli.BioMartQueryMatrix',str(jar),str(cases),str(out/'windows-java21.json')],'windows-java25':['C:/Program Files/Common Files/Oracle/Java/javapath/java.exe','-cp',str(classes)+';'+str(jar),'org.jlinalg.cli.BioMartQueryMatrix',str(jar),str(cases),str(out/'windows-java25.json')],'linux-java21':['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-cp',linux(classes)+':'+linux(jar),'org.jlinalg.cli.BioMartQueryMatrix',linux(jar),linux(cases),linux(out/'linux-java21.json')]}
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:results=list(pool.map(lambda item:(item[0],execute(*item)),commands.items()))
        assert all(code==0 for _,code in results),results;http=json.loads((reference/'http-records.json').read_bytes());cases=json.loads(cases.read_bytes());outputs=[]
        for name,_ in results:
            rows=json.loads((out/(name+'.json')).read_bytes());assert len(rows)==2*len(cases);complete=0;errors=0;batch_count=0;requests=0
            for row in rows:
                version,id=row['version'],row['id'];source=json.loads((reference/(version+'-'+id)/'RESULT.json').read_bytes());recorded=[r['query'] for r in http if r['version']==version and r['case']==id]
                if row['status']=='NATIVE_QUERY_PLAN_ERROR':assert source['status']=='ORIGINAL_GETBM_ERROR',(version,id,source);errors+=1;continue
                assert source['status']=='PASS_ORIGINAL_PUBLIC_GETBM_PROTOCOL_ONLY',(version,id,source);batches=row['batches'];xml=[b['xml'] for b in batches];unique=list(dict.fromkeys(xml));assert unique==recorded,(version,id,'Complete original public getBM submitted XML differs');source_chunks=vector(source['original_filter_XML']);assert len(source_chunks)==len(batches),(version,id,'source batch count')
                for x,f in zip(xml,source_chunks):assert x.endswith(f+'</Dataset></Query>'),(version,id,'Complete source filter XML differs')
                warnings=[w for b in batches for w in b['source_warnings']];assert warnings==vector(source['source_warnings']),(version,id,'Original numeric coercion warnings differ',warnings,source['source_warnings']);assert source['returned_rows']==len(batches),(version,id,'Original duplicate rows across chunks were unexpectedly collapsed');complete+=1;batch_count+=len(batches);requests+=len(recorded)
            outputs.append(rows);state['JVMs'].append(dict(name=name,complete_original_public_cases=complete,matching_original_failures=errors,complete_query_batches=batch_count,complete_original_HTTP_submissions=requests,exit_code=0));print(name,'complete public-source cases',complete,'matching source failures',errors,flush=True)
        assert outputs[0]==outputs[1]==outputs[2] and all(pin(ROOT/n)==v for n,v in pins.items());state['status']='passed-native-biomaRt-query-planning-original-public-operation-comparisons'
    except Exception as error:state['error']=repr(error)
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete original-operation comparisons')
    raise SystemExit(main())
