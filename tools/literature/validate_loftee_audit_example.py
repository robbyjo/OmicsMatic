# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compile and run the exact documented audit-only example against a complete retained original VCF."""
import argparse,json,re,subprocess,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from validate_mashr_native import JAVA21,linux


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/loftee-runtime') and not out.exists();out.mkdir(parents=True);build=json.loads((a.build/'COMPLETED.json').read_bytes());jar=ROOT/build['artifact']['path'];assert pin(jar)=={k:build['artifact'][k] for k in ['bytes','sha256']};guide=ROOT/'docs/literature/LOFTEE_AUDIT_DEVELOPMENT.md';fixture=ROOT/'src/test/resources/org/jlinalg/genetics/loftee-original/controls-v1.zip';pins={f.relative_to(ROOT).as_posix():pin(f) for f in [Path(__file__).resolve(),guide,fixture]};blocks=re.findall(r'```java\n(.*?)```',guide.read_text(encoding='utf8'),re.S);assert len(blocks)==1;source=out/'LofteeAuditExample.java';source.write_text(blocks[0],encoding='utf8',newline='\n');case='original-probe-4-original-default';files=out/'controls';files.mkdir()
    with zipfile.ZipFile(fixture) as z:
        index=json.loads(z.read('INDEX.json'));assert case in index['cases']
        for name in z.namelist():
            if not name.startswith(case+'/'):continue
            relative=Path(name);assert not relative.is_absolute() and '..' not in relative.parts;raw=z.read(name);import hashlib;assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==index['files'][name];path=files/relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(raw)
    classes=out/'classes';classes.mkdir();commands={'compile':[str(JAVA21/'javac.exe'),'--release','17','-Xlint:all','-Werror','-cp',str(jar),'-d',str(classes),str(source)]};arguments=[files/case/'inputs/input.vcf',files/case/'original/original.vcf',files/case/'original/vep.stdout',files/case/'original/vep.stderr'];commands['windows-java21']=[str(JAVA21/'java.exe'),'-cp',str(classes)+';'+str(jar),'LofteeAuditExample',*map(str,arguments)];commands['linux-java21']=['wsl','-d','JLinAlg-Omics-SAIGE-20261026','--exec','/usr/bin/java','-cp',linux(classes)+':'+linux(jar),'LofteeAuditExample',*[linux(p) for p in arguments]]
    for name,command in commands.items():
        r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=120);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);save(out/(name+'.process.json'),dict(command=command,exit_code=r.returncode));assert r.returncode==0,r.stderr.decode('utf8',errors='replace')
        if name!='compile':assert r.stdout.decode('utf8').strip()=='AUDIT PASSED: 15 variants, 80 annotations. No new annotation model was run.'
    assert all(pin(ROOT/n)==v for n,v in pins.items());save(out/'COMPLETED.json',dict(status='passed-exact-documented-LOFTEE-native-audit-example',artifact=build['artifact'],source_pins=pins,JVMs=['windows-java21','linux-java21'],new_original_annotation_executed=False,LOFTEE_annotation_admitted=False,implementation_credit=False));print('Documented audit-only Java example passed on Windows and Linux')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact example/source checks')
    main()
