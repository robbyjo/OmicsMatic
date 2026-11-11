# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Check actual original LinkPeaks with Unicode cells/genes, not only Unicode filesystem paths."""
import argparse
import copy
import json
from pathlib import Path
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,linux,pin
from prepare_signac_link_probe import prepare
from validate_signac_link_workflow import compare
BASE=ROOT/'build/literature-review/signac-runtime'
def read(path):return json.loads(path.read_bytes())
def save(path,value):path.write_text(json.dumps(value,indent=2,ensure_ascii=False)+'\n',encoding='utf8',newline='\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--build',default='links-workflow-build-3');args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists() and '/' not in args.build and '\\' not in args.build;out.mkdir();save(out/'INCOMPLETE.json',dict(status='actual-Unicode-LinkPeaks-fit-in-progress'))
    try:
        build=read(BASE/args.build/'BUILD.json');assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};names=['tools/literature/probe_signac_links_unicode.py','tools/literature/prepare_signac_link_probe.py','tools/literature/validate_signac_link_workflow.py'];sources={n:pin(ROOT/n) for n in names};save(out/'SOURCE-PINS.json',sources)
        for name in names:
            target=out/'source-snapshot'/name;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes((ROOT/name).read_bytes())
        data=read(BASE/'links-original-4/cases.json');case=next(c for c in data['cases'] if c['id']=='pearson-6-1');reference=copy.deepcopy(next(r['result']['value'] for r in read(BASE/'links-original-4/original.json')['results'] if r['profile']=='1.17.1-package' and r['id']==case['id']));mapping={'G1':'gene α','G2':'gene β','G3':'gene γ'};data['datasets']['base']['cells']=['cell α'+str(i) for i in range(16)]
        for gene in data['datasets']['base']['genes']:gene['gene_name']=mapping[gene['gene_name']]
        for link in reference['links']:link['gene']=mapping[link['gene']]
        target=out/'Unicode β analysis';request=prepare(data,case,'1.17.1-package',out/'Unicode α inputs',target);path=out/'request.json';save(path,request)
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode,r.stderr.decode('utf8',errors='replace')[-1500:])
        command('fit',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe'),'-Xmx768m','-cp',jar,'org.jlinalg.cli.SignacLinksRequestCli','--request',path]);compare(target,reference)
        for platform,java,convert in [('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],linux)]:
            path=out/(platform+'-verify.json');save(path,dict(schema_version=1,mode='VERIFY',analysis=convert(target)));command(platform,[*java,'-cp',convert(jar),'org.jlinalg.cli.SignacLinksRequestCli','--request',convert(path)])
        assert all(pin(ROOT/n)==v for n,v in sources.items()) and all(pin(ROOT/n)==v for n,v in build['source_pins'].items());save(out/'COMPLETED.json',dict(status='passed-actual-original-LinkPeaks-Unicode-identities',source_pins=sources,artifact=build['artifact'],actual_R_fit=True,fresh_java_platform='windows-java25',exact_UTF8_cell_and_gene_IDs_preserved=True,original_link_numbers_and_RNG_states_match=True,captured_verifications=3));(out/'INCOMPLETE.json').unlink();print(json.dumps(dict(status='passed-actual-original-LinkPeaks-Unicode-identities',captured_verifications=3)),flush=True)
    except BaseException as error:save(out/'FAILURE.json',dict(status='FAILED_ORIGINAL_LINKPEAKS_UNICODE_IDENTITIES',error=str(error)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable Unicode LinkPeaks checks')
    main()
