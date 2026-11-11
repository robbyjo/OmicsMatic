# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a separate four-edition runtime kit from exact captured originals; never update old scientific bindings."""
import argparse,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin,save

def transformed_setup():
    s=(ROOT/'src/main/resources/org/jlinalg/omics/scfates-workflow/setup/prepare.py').read_text();s=s.replace('SCFATES','GSEAPY').replace('scFates','GSEApy').replace('scfates','gseapy')
    old="dependencies=prefix/'dependencies';execute('install-exact-dependencies',[python,'-I','-B','-m','pip','install','--no-index','--no-deps','--no-compile','--target',dependencies,*locked])"
    assert s.count(old)==1
    s=s.replace(old,"\n        for edition,archive in zip(profile['editions'],locked,strict=True):\n            execute('install-original-'+edition,[python,'-I','-B','-m','pip','install','--no-index','--no-deps','--no-compile','--target',prefix/'editions'/edition,archive])")
    old="CPU Linux x86_64 only; optional R/GAM, GPU and unrelated GSEApy analyses are not admitted."
    assert s.count(old)==1;s=s.replace(old,'Linux x86_64 CPython3.11 only. Four original editions are installed separately. Local prepared libraries are distinct from historical remote Enrichr responses; no paper version or input is inferred.')
    return s.encode()

def write(path,raw):
    path.parent.mkdir(parents=True,exist_ok=True)
    if path.exists():assert path.read_bytes()==raw,'Existing setup source protected: '+str(path)
    else:path.write_bytes(raw)

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/gseapy-runtime');out.mkdir(parents=True);resources=ROOT/'src/main/resources/org/jlinalg/omics/gseapy-workflow';prior=ROOT/'src/main/resources/org/jlinalg/omics/scfates-workflow';intake=ROOT/'build/literature-review/gseapy-runtime/source-intake-1';source=json.loads((intake/'SOURCE.json').read_bytes());assert source['status']=='passed-four-paper-reported-official-GSEApy-source-wheels';members={'.gitattributes':b'* -text whitespace=-blank-at-eol,-blank-at-eof,-space-before-tab,cr-at-eol\n','setup/prepare.py':transformed_setup(),'setup/inventory.py':(prior/'setup/inventory.py').read_bytes()};editions={};archives=[]
    for version,profile in source['versions'].items():
        wheel=profile['wheel'];path=intake/version/wheel['filename'];assert pin(path)=={k:wheel[k] for k in ['bytes','sha256']};members['original/'+wheel['filename']]=path.read_bytes();editions[version]=dict(version=version,wheel=wheel,members=profile['members']);archives.append(dict(name='gseapy',version=version,**wheel))
        for name,want in profile['members'].items():
            if name.endswith('/LICENSE'):path=intake/version/'wheel-source'/name;assert pin(path)==want;members['original/'+version+'/'+name]=path.read_bytes()
    members['NOTICE.txt']=b'Original GSEApy1.0.4/1.1.2/1.1.3/1.1.8 CPython3.11 Linux wheels and MIT licenses are retained unchanged. Official PyPI source provenance and exact SHA-256 are in profiles.json. Four original namespaces are installed separately over the read-only bound project scientific Python base. New setup/worker/Java code is GPL-3.0-or-later; process/inventory supervision is derived from the project scFates adapter with explicit GSEApy edition handling. Original scientific code is never patched. No historical remote Enrichr response or full paper reproduction is implied.\n'
    members['SETUP.md']=b'''# GSEApy runtime setup

This kit installs four pinned original editions into separate namespaces. It
does not run an enrichment analysis. Linux x86_64 CPython 3.11.17 is required.
Use the read-only project scientific Python base and its exact successful
CellPhoneDB setup receipt. No CellPhoneDB analysis is needed. Read the base
preparation guide if that base is missing; never substitute another environment
or refresh its saved hashes.

Run with that base Python from the exported kit:

```sh
/ABS/BASE/bin/python -I -B setup/prepare.py \\
  --base-runtime /ABS/BASE-SETUP/COMPLETED.json \\
  --prefix /ABS/NEW-CASE-SENSITIVE-PREFIX \\
  --out /ABS/NEW-SETUP-RECEIPTS --allow-install
```

The four original wheels are bundled, so setup can work offline. Installation
requires --allow-install. If a pinned archive is absent, supply an exact
--archive-cache directory or use --allow-download only where permitted.
Downloads are restricted to the exact official archive URL and hash. Otherwise
SKIP3 skips all dependent analysis and records the missing dependency/action.
Do not install into the shared base. Use NEW paths and retain failed prefixes.
Full original imports and complete before/after inventories precede admission.
Require exit0 AND PASS_BOUND_ORIGINAL_GSEAPY_WORKFLOW_RUNTIME_ONLY in COMPLETED.json.

RUN installs nothing. An execution failure admits no earlier partial batch.
Source errors, duplicate/case preprocessing, nonfinite results and exact library
contents must remain explicit. This kit does not recover historical Enrichr
libraries or infer paper modes. The1.0.4 package has no GSVA API. Preserve its
legacy parameter and report differences. Whole-package/paper reproduction is
not claimed. Read WARNINGS.md and every original/process log.
'''
    for name,raw in members.items():write(resources/name,raw)
    health=resources/'setup/health.py';assert health.is_file();members['setup/health.py']=health.read_bytes();base=json.loads((prior/'profiles.json').read_bytes())['base_profile'];profile=dict(schema_version=1,editions=editions,dependency_archives=archives,base_profile=base,files={n:pin(resources/n) for n in sorted(members)});write(resources/'profiles.json',(json.dumps(profile,indent=2)+'\n').encode())
    files=[Path(__file__).resolve(),*[p for p in resources.rglob('*') if p.is_file()]];pins={p.relative_to(ROOT).as_posix():pin(p) for p in files}
    for path in files:copy=out/'source-snapshot'/path.relative_to(ROOT);copy.parent.mkdir(parents=True,exist_ok=True);copy.write_bytes(path.read_bytes())
    save(out/'COMPLETED.json',dict(status='passed-prepared-four-edition-GSEApy-runtime-sources-only',source_pins=pins,runtime_prepared=False,implementation_credit=False,whole_goal_complete=False));print('Prepared exact four-edition setup sources; no software installed or analysis admitted')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact setup source checks')
    main()
