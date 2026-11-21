"""Check fresh source/cumulative catalog contexts and actual install-policy refusals."""
from pathlib import Path
import argparse,concurrent.futures,json,subprocess,sys
from setup import ROOT,pin,require,save
from packaged import mounted

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--cache',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    out=args.out.resolve();require(out.is_relative_to(ROOT/'build') and out!=ROOT/'build','Use NEW build output');out.mkdir(parents=True,exist_ok=False)
    windows=[sys.executable,'-X','utf8','-B'];linux=['wsl.exe','-d','JLinAlg-Omics-PyMOL-20261026','-u','root','--','python3','-B']
    jobs=[('source-windows',windows+[str(Path(__file__).with_name('audit_primary.py')),'--cache',str(args.cache.resolve()),'--out',str(out/'source-windows-result')]),
          ('source-linux',linux+[mounted(Path(__file__).with_name('audit_primary.py')),'--cache',mounted(args.cache),'--out',mounted(out/'source-linux-result')]),
          ('catalog-windows',windows+[str(ROOT/'tools/literature/verify_twosamplemr_catalog_checkpoint.py'),'--out',str(out/'catalog-windows-result')]),
          ('catalog-linux',linux+[mounted(ROOT/'tools/literature/verify_twosamplemr_catalog_checkpoint.py'),'--out',mounted(out/'catalog-linux-result')])]
    def run(job):
        name,command=job
        with (out/(name+'.stdout')).open('xb') as stdout,(out/(name+'.stderr')).open('xb') as stderr:process=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=180)
        row=dict(command=command,exit_code=process.returncode);save(out/(name+'.PROCESS.json'),row);print(name,process.returncode,flush=True);return name,row
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:rows=dict(pool.map(run,jobs))
    require(all(row['exit_code']==0 for row in rows.values()),'Inspect retained fresh source/catalog failures')
    controls=[];common=ROOT.parent.parent/'build/literature-review/monocle-runtime'
    # ROOT is D:/projects/JLinAlg/build/mo-1; reach the shared private base explicitly.
    common=ROOT.parent/'literature-review/monocle-runtime'
    base=common/'monocle133-reference-setup-1';source=common/'source-intake-1/1.0.0';genome=common/'monocle133-original-install-1/R-lib'
    bad=out/'contradictory-source';bad.mkdir();receipt=json.loads((source/'SOURCE.json').read_bytes());receipt['version']='1.3.3';save(bad/'SOURCE.json',receipt)
    for name,selected_base,selected_source,allow,expected in [('no-install-selection',base,source,False,3),('missing-base',out/'absent-base',source,True,3),('contradictory-source',base,bad,True,2)]:
        target=out/(name+'-result');command=windows+[str(Path(__file__).with_name('setup.py')),'--base',str(selected_base),'--source-candidate',str(selected_source),'--genome-library',str(genome),'--out',str(target)]+(['--allow-install'] if allow else [])
        row=run((name,command))[1];require(row['exit_code']==expected,'Different actual setup refusal: '+name)
        result=json.loads((target/'RESULT.json').read_bytes());require(not result['runtime_admitted'] and not result['scientific_analysis_admitted'] and not (target/'STARTED.json').exists(),'A setup refusal launched installation or admitted analysis')
        controls.append(dict(name=name,expected_exit=expected,process=row,result=result))
    for host in ['windows','linux']:
        current=json.loads((out/('catalog-'+host+'-result/RESULT.json')).read_bytes());require(current['integration_families']==144 and current['reviewed']==1630 and current['pending']==1092 and current['manifest_entries']==838,'Changed cumulative catalog scope')
    save(out/'RESULT.json',dict(status='PASS_FRESH_MONOCLE9_AND_RETAINED_CATALOG_BOTH_HOSTS_WITH_THREE_SETUP_REFUSALS',processes=rows,setup_controls=controls,source_papers_per_host=250,source_roles=9,catalog_families=144,reviewed=1630,pending=1092,manifest_entries=838,new_catalog_credit=0,whole_goal_complete=False));return 0

if __name__=='__main__':raise SystemExit(main())
