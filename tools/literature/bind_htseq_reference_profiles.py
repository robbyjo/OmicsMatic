# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind five actual counting references to unchanged scientific sources and complete private-home inventories."""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path
from setup_htseq_reference import ROOT,BASE,DISTRO,linux,pin


INVENTORY='''import hashlib,json,sys
from pathlib import Path
root=Path(sys.argv[1]);files={};links={};total=0
for path in sorted(root.rglob('*')):
 name=path.relative_to(root).as_posix()
 if path.is_symlink():
  resolved=path.resolve(strict=True)
  if resolved.is_dir():links[name]=dict(kind='directory',target=str(path.readlink()),resolved=str(resolved))
  else:
   with resolved.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
   links[name]=dict(kind='file',target=str(path.readlink()),resolved=str(resolved),bytes=resolved.stat().st_size,sha256=digest)
 elif path.is_file():
  size=path.stat().st_size;total+=size
  if total>20000000000 or len(files)>=100000:raise RuntimeError('Complete private runtime inventory exceeds capture bounds')
  with path.open('rb') as stream:digest=hashlib.file_digest(stream,'sha256').hexdigest()
  files[name]=dict(bytes=size,sha256=digest)
print(json.dumps(dict(files=files,symlinks=links,total_bytes=total,bytecode_included=True),sort_keys=True))
'''


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',type=Path,required=True)
    parser.add_argument('--reuse-inventories',type=Path,help='Reuse pinned complete inventories from an earlier interrupted binding capture; not a fresh runtime scan')
    args=parser.parse_args();out=args.out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    reuse=args.reuse_inventories.resolve() if args.reuse_inventories else None
    if reuse:assert reuse.is_relative_to(BASE) and reuse.is_dir() and reuse!=out
    def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')
    profiles=[]
    for folder in ['historical-py27-setup-1','historical-py39-setup-2']:
        setup=json.loads((BASE/folder/'COMPLETED.json').read_bytes())
        assert setup['status']=='completed-private-historical-HTSeq-reference-setup'
        for row in setup['profiles']:profiles.append(dict(version=row['version'],home=row['home'],profile=row['profile'],setup_metadata=pin((BASE/folder/'COMPLETED.json').read_bytes())))
    current=json.loads((BASE/'reference-inventory-recovery-2/COMPLETED.json').read_bytes())
    profiles.append(dict(version='2.1.2',home=current['python'].rsplit('/bin/',1)[0],profile=current['profile'],
        setup_metadata=pin((BASE/'reference-inventory-recovery-2/COMPLETED.json').read_bytes())))
    assert {r['version'] for r in profiles}=={'0.5.3','0.6.0','0.11.1','0.13.5','2.1.2'}
    (out/'inventory.py').write_text(INVENTORY,encoding='utf8',newline='\n')
    bound=[]
    for row in profiles:
        folder=out/row['version'];folder.mkdir()
        home=ROOT/row['home'].removeprefix('/mnt/d/projects/JLinAlg/')
        assert home.is_relative_to(BASE)
        source_parent=BASE/'source-intake-2'/row['version']/'source'
        roots=list(source_parent.iterdir());assert len(roots)==1
        original=roots[0]/('python3' if row['version']=='0.11.1' else '')
        py='python2.7' if row['version'] in ['0.5.3','0.6.0'] else 'python3.9' if row['version'] in ['0.11.1','0.13.5'] else 'python3.12'
        site=home/'lib'/py/'site-packages'
        bindings=[];omitted=[]
        for source in sorted((original/'HTSeq').rglob('*.py')):
            relative=source.relative_to(original).as_posix()
            # These two files are upstream build-generated bindings/metadata;
            # all scientific Python modules, including count/GTF/pairing, bind.
            if source.name in ['StepVector.py','_version.py']:continue
            installed=site/relative
            if not installed.is_file():
                omitted.append(relative);continue
            expected=pin(source.read_bytes());actual=pin(installed.read_bytes())
            assert expected==actual,(row['version'],relative,'Installed scientific Python source differs from original')
            bindings.append(dict(original_path=source.relative_to(ROOT).as_posix(),installed_path=relative,**expected))
        # The published 2.1.2 wheel omits this obsolete source-only command.
        # It is not executed or represented as installed. No other omission or
        # added scientific Python module is accepted.
        assert omitted==(['HTSeq/scripts/count_old.py'] if row['version']=='2.1.2' else []),(row['version'],omitted)
        for installed in (site/'HTSeq').rglob('*.py'):
            if installed.name not in ['StepVector.py','_version.py']:
                assert (original/installed.relative_to(site)).is_file(),('Added installed scientific source',installed)
        assert any(b['installed_path']=='HTSeq/scripts/count.py' for b in bindings)
        prior=reuse/row['version'] if reuse else None
        reused=bool(prior and (prior/'PROFILE.json').is_file())
        if reused:
            old=json.loads((prior/'PROFILE.json').read_bytes())
            assert all(old[k]==row[k] for k in row) and old['scientific_source_bindings']==bindings
            raw=(prior/'RUNTIME-FILES.json').read_bytes();assert pin(raw)==old['runtime_inventory']
            inventory=json.loads(raw)
            save(folder/'inventory.reuse.json',dict(source_profile=str(prior/'PROFILE.json'),source_profile_pin=pin((prior/'PROFILE.json').read_bytes()),
                source_inventory_pin=pin(raw),fresh_runtime_scan=False))
        else:
            argv=['wsl.exe','-d',DISTRO,'--','/usr/bin/python3','-I',linux(out/'inventory.py'),row['home']]
            with (folder/'inventory.stdout').open('xb') as stdout,(folder/'inventory.stderr').open('xb') as stderr:
                result=subprocess.run(argv,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=300)
            save(folder/'inventory.process.json',dict(command=argv,exit_code=result.returncode));assert result.returncode==0,row['version']
            inventory=json.loads((folder/'inventory.stdout').read_bytes())
        assert inventory['files'] and inventory['bytecode_included']
        for binding in bindings:
            name='lib/'+py+'/site-packages/'+binding['installed_path']
            assert inventory['files'][name]=={k:binding[k] for k in ['bytes','sha256']},('Source/inventory disagreement',name)
        save(folder/'RUNTIME-FILES.json',inventory)
        record=dict(row,scientific_source_bindings=bindings,source_modules_unchanged=len(bindings),runtime_files=len(inventory['files']),
            runtime_symlinks=len(inventory['symlinks']),runtime_inventory=pin((folder/'RUNTIME-FILES.json').read_bytes()),
            includes_bytecode=True,full_host_or_DLL_authentication=False,source_archive_modules_not_installed=omitted,
            inventory_reused_from_prior_capture=reused)
        save(folder/'PROFILE.json',record);bound.append(record)
    checks={}
    for name in ['count-original-1','count-fraction-original-1','historical-py27-counts-2','historical-py39-counts-1']:
        path=BASE/name/'COMPLETED.json';checks[name]=pin(path.read_bytes())
    save(out/'COMPLETED.json',dict(schema_version=1,status='bound-five-original-HTSeq-counting-reference-profiles',profiles=bound,
        original_control_records=checks,source=pin(Path(__file__).read_bytes()),scientific_Python_sources_unchanged=True,
        compiled_bindings_and_toolchains_explicit=True,historical_whole_environments_reproduced=False,
        execution_authentication=False,shared_or_system_python_modified=False,implementation_credit=False))
    print(json.dumps(dict(status='bound-five-original-HTSeq-reference-profiles',profiles=[dict(version=r['version'],source_modules=r['source_modules_unchanged'],runtime_files=r['runtime_files']) for r in bound])))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original-source binding checks')
    main()
