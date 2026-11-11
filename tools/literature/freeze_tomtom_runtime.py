# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze current-host source/host/Perl/fresh-health component evidence, not portable tickets."""
import argparse
import hashlib
import json
from pathlib import Path
import stat
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/meme-runtime'


def pin(path):
    with path.open('rb') as f:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(f,'sha256').hexdigest())


def read(path):return json.loads(path.read_bytes())


def check_capture(matrix):
    inventory=read(matrix/'NATIVE-INVENTORY.json');actual={}
    for path in sorted((matrix/'captured').rglob('*')):
        mode=path.lstat().st_mode;name=path.relative_to(matrix/'captured').as_posix()
        if stat.S_ISDIR(mode):actual[name]=dict(kind='directory')
        else:assert stat.S_ISREG(mode);actual[name]=dict(kind='file',**pin(path))
    assert set(actual)==set(inventory)
    for name,value in inventory.items():assert {k:v for k,v in value.items() if k!='mode'}==actual[name]
    return inventory


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--matrix',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    matrix=args.matrix.resolve();out=args.out.resolve();assert matrix.is_relative_to(BASE.resolve()) and out.is_relative_to((ROOT/'docs/literature').resolve()) and not out.exists()
    proof=read(matrix/'COMPLETED.json');assert proof['status']=='passed-exact-source-three-JVM-Tomtom-native-source-host-Perl-health-admission'
    assert sorted(r['name'] for r in proof['runs'])==['linux-java21','windows-java21','windows-java25']
    assert all(r['tests']==r['passed']==206 and r['skipped']==0 for r in proof['runs'])
    assert proof['exact_source_bytecode_classes']==49 and len(proof['added_classes'])==13 and len(proof['added_resources'])==5 and len(proof['added_resource_directories'])==1
    assert proof['runtime_admitted'] and (proof['native_admissions'],proof['fresh_health_fits'],proof['downstream_synthetic_fits'])==(2,4,2)
    assert not any(proof[k] for k in ['user_analysis_admitted','public_execution_adapter_available','implementation_credit','released','pushed','deployed','whole_goal_complete'])
    sources=dict(proof['source_pins'])
    for path in [Path(__file__),ROOT/'docs/literature/MEME_NATIVE_RUNTIME_DEVELOPMENT.md']:sources[path.relative_to(ROOT).as_posix()]=pin(path)
    assert all(pin(ROOT/name)==value for name,value in sources.items())
    assert pin(ROOT/proof['artifact']['path'])=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    native=read(matrix/'captured/COMPLETED.json');assert native['native_admissions']==len(native['profiles'])==2 and native['fresh_health_fits']==4 and native['downstream_synthetic_fits']==2
    assert all(p['runtime_admitted_current_host'] and p['fresh_health_fits']==2 and p['downstream_synthetic_fits']==1 and p['complete_downstream_pairs']==p['complete_downstream_rows']==9 and not p['owned_live_after_cleanup'] and p['stale_evidence_rejected'] and p['mode_mismatch_skipped_before_launch'] and not p['user_analysis_admitted'] for p in native['profiles'])
    final_inventory=check_capture(matrix)
    for name in ['matrix-2','matrix-3']:check_capture(BASE/'tomtom-native-runtime-1'/name)
    prior=ROOT/'docs/literature/meme-prepared-comparison-development';old=read(prior/'VALIDATION.json');assert pin(prior/'controls.zip')=={k:old['archive'][k] for k in ['bytes','sha256']}
    entries={}
    def add(path,name):assert path.is_file() and not path.is_symlink() and name not in entries;entries[name]=path
    def tree(folder,prefix):
        for path in sorted(folder.rglob('*')):
            if path.is_file():add(path,prefix+'/'+path.relative_to(folder).as_posix())
    def direct(folder,prefix):
        for path in sorted(folder.iterdir()):
            if path.is_file():add(path,prefix+'/'+path.name)
    for name in sources:add(ROOT/name,'source/'+name)
    direct(matrix,'validation');tree(matrix/'source-compiled','validation/exact-source-compiled');tree(matrix/'captured','native-runtime-controls')
    for name in ['matrix-1','matrix-2','matrix-3']:
        folder=BASE/'tomtom-native-runtime-1'/name;direct(folder,'history/'+name)
        if (folder/'source-snapshot').exists():tree(folder/'source-snapshot','history/'+name+'/source-snapshot')
        if (folder/'captured').exists():tree(folder/'captured','history/'+name+'/captured')
    first=BASE/'tomtom-native-runtime-1/package-1';add(first/'FAILURE.json','history/package-1/FAILURE.json');tree(first/'source-snapshot','history/package-1/source-snapshot');tree(first/'failed-test-results','history/package-1/test-results')
    tree(ROOT/'build/test-results/test','gradle-focused-test-results')
    add(prior/'VALIDATION.json','prior-preparation-component/VALIDATION.json');add(prior/'SHA256SUMS','prior-preparation-component/SHA256SUMS')
    out.mkdir();archive=out/'controls.zip';members={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,path in sorted(entries.items()):
            raw=path.read_bytes();info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(stat.S_IFREG|0o644)<<16;z.writestr(info,raw);members[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.infolist())==len(set(z.namelist()))==len(members)
        for name,value in members.items():raw=z.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==value
    assert archive.stat().st_size<95000000 and all(pin(ROOT/name)==value for name,value in sources.items())
    result=dict(schema_version=1,status='passed-native-Tomtom-current-host-source-ELF-Perl-and-fresh-health-components',source_pins=sources,artifact=proof['artifact'],platform_validation=proof['runs'],archive=dict(path=archive.name,**pin(archive),entries=len(members)),archive_files=members,
        exact_source_bytecode_classes=49,added_classes=proof['added_classes'],added_resources=proof['added_resources'],added_resource_directories=proof['added_resource_directories'],unchanged_prior_archive_entries=proof['unchanged_prior_archive_entries'],original_parent=proof['original_parent'],canonical_executable_unchanged=proof['canonical_executable_unchanged'],
        native_admissions=2,fresh_health_fits=4,complete_original_health_rows=36,downstream_synthetic_fits=2,complete_downstream_rows=18,complete_native_control_nodes=len(final_inventory),runtime_admitted_during_native_controls=True,copied_record_is_runtime_ticket=False,
        prior_preparation_proof=dict(validation=pin(prior/'VALIDATION.json'),archive=pin(prior/'controls.zip'),unchanged=True),scope=proof['scope'],
        user_analysis_admitted=False,public_execution_adapter_available=False,implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,
        required_action='Read MEME_NATIVE_RUNTIME_DEVELOPMENT.md. Runtime capability tickets must be freshly created and reverified on the current permitted Linux x86_64 host; copied metadata cannot authorize user analysis. Preserve exact source/build/prefix/host/helper/library/Perl identities and every original numerical/population warning. Named missing/forbidden dependencies require SKIP3 with instructions to supply them on a permitted host; integrity/capacity/cleanup failures remain FAIL2. Analysis never installs or silently changes mode/settings. Complete setup/export, requested output-family semantics, portable final bundle, public CLI/example and catalog gates before integration credit.')
    (out/'VALIDATION.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
    (out/'SHA256SUMS').write_text(pin(archive)['sha256']+'  controls.zip\n'+pin(out/'VALIDATION.json')['sha256']+'  VALIDATION.json\n',encoding='ascii',newline='\n');(out/'.gitattributes').write_text('* -text\n',encoding='ascii',newline='\n')
    print(json.dumps(dict(status=result['status'],archive=result['archive'],checks_per_jvm=206,native_admissions=2,fresh_health_fits=4,downstream_synthetic_fits=2,implementation_credit=False)))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete exact-source/native evidence checks')
    main()
