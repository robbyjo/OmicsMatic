# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run the documented synthetic example and a Unicode identity case using the actual LSI candidate."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import sys
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'tools/literature'))
from validate_mashr_native import JAVA21,pin
BASE=ROOT/'build/literature-review/signac-runtime'
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);args=p.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE.resolve()) and not out.exists();out.mkdir();save(out/'INCOMPLETE.json',dict(status='documented-example-and-Unicode-fit-checks-in-progress'))
    try:
        build=json.loads((BASE/'lsi-request-build-3/BUILD.json').read_bytes());assert all(pin(ROOT/n)==v for n,v in build['source_pins'].items());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']}
        examples=ROOT/'examples/signac-lsi';sources={p.relative_to(ROOT).as_posix():pin(p) for p in examples.iterdir() if p.is_file()};sources['tools/literature/validate_signac_lsi_example.py']=pin(Path(__file__))
        save(out/'SOURCE-PINS.json',sources)
        for name in ['counts.mtx','features.tsv','cells.tsv']:assert pin(examples/name)==pin(BASE/'lsi-probe-1'/name)
        reference=BASE/'lsi-workflow-5/1.17.1-package-rspectra-scaled';first=(reference/'exchange/features.tsv').read_text().splitlines()[0]
        unicode=out/'Unicode α inputs';unicode.mkdir();shutil.copyfile(examples/'counts.mtx',unicode/'counts.mtx')
        features=(examples/'features.tsv').read_text().splitlines();features[features.index(first)]='peak α_1';(unicode/'features.tsv').write_text('\n'.join(features)+'\n',encoding='utf8',newline='\n')
        cells=(examples/'cells.tsv').read_text().splitlines();cells[0]='cell β_1';(unicode/'cells.tsv').write_text('\n'.join(cells)+'\n',encoding='utf8',newline='\n')
        def command(name,argv):
            argv=list(map(str,argv));r=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=120,check=False)
            (out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=argv,exit_code=r.returncode));assert r.returncode==0,(name,r.returncode)
        runs=[]
        for name,data in [('documented',examples),('Unicode',unicode),('Unicode-java25-historical',unicode)]:
            target=out/name if name=='documented' else out/'Unicode β output'/name
            profile='1.14.0-source-irlba' if name.endswith('historical') else '1.17.1-package-rspectra'
            options=dict(counts=str(data/'counts.mtx'),features=str(data/'features.tsv'),cells=str(data/'cells.tsv'),out=str(target),rscript='C:/Program Files/R/R-4.6.1/bin/Rscript.exe',profile=profile,components=5,seed=4901)
            options.update({'r-library':str(BASE/'reference-setup-1/R-lib'),'r-base-library':str(ROOT/'build/omics-sctransform-r')})
            request=out/(name+'-request.json');save(request,dict(schema_version=1,mode='FIT',options=options))
            java=Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe') if name.endswith('historical') else JAVA21/'java.exe'
            command(name,[java,'-Xmx512m','-cp',jar,'org.jlinalg.cli.SignacLsiRequestCli','--request',request])
            if name=='documented':
                for file in ['result-embeddings.tsv','result-loadings.tsv','result-singular-values.tsv']:assert (target/file).read_bytes()==(reference/file).read_bytes()
            else:
                assert 'peak α_1\t' in (target/'result-loadings.tsv').read_text(encoding='utf8') and 'cell β_1\t' in (target/'result-embeddings.tsv').read_text(encoding='utf8')
                assert 'peak α_1\t' in (target/'feature-metadata.tsv').read_text(encoding='utf8')
            runs.append(dict(name=name,fresh_actual_fit=True,identities_preserved=True))
            for platform,jvm,convert in [('windows-java21',[JAVA21/'java.exe'],str),('windows-java25',[Path('C:/Program Files/Common Files/Oracle/Java/javapath/java.exe')],str),('linux-java21',['wsl.exe','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java'],lambda p:'/mnt/d/'+p.resolve().relative_to(Path('D:/')).as_posix())]:
                verify=out/(name+'-'+platform+'-verify.json');save(verify,dict(schema_version=1,mode='VERIFY',analysis=convert(target)))
                command(name+'-'+platform+'-verify',[*jvm,'-cp',convert(jar),'org.jlinalg.cli.SignacLsiRequestCli','--request',convert(verify)])
        assert all(pin(ROOT/n)==v for n,v in sources.items())
        save(out/'COMPLETED.json',dict(status='passed-documented-and-Unicode-Signac-LSI-examples',source_pins=sources,artifact=build['artifact'],runs=runs))
        (out/'INCOMPLETE.json').unlink();print(json.dumps(runs))
    except BaseException as e:save(out/'FAILURE.json',dict(status='FAILED_DOCUMENTED_OR_UNICODE_LSI_EXAMPLE',error=str(e)));raise
if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable example checks')
    main()
