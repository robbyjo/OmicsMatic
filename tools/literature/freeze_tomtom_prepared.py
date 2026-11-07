# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze typed preparation/text/supervised original component evidence without runtime credit."""
import argparse
import hashlib
import json
from pathlib import Path
import stat
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/meme-runtime'


def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def read(path):return json.loads(path.read_bytes())


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--matrix',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args()
    matrix=args.matrix.resolve();out=args.out.resolve();assert matrix.is_relative_to(BASE.resolve()) and out.is_relative_to((ROOT/'docs/literature').resolve()) and not out.exists()
    proof=read(matrix/'COMPLETED.json');assert proof['status']=='passed-exact-source-three-JVM-typed-Tomtom-preparation-text-and-original-component-runs'
    assert sorted(r['name'] for r in proof['runs'])==['linux-java21','windows-java21','windows-java25']
    assert all(r['tests']==r['passed']==180 and r['skipped']==0 for r in proof['runs'])
    assert proof['exact_source_bytecode_classes']==36 and len(proof['added_classes'])==11 and proof['native_fits']==49
    assert (proof['full_report_fits'],proof['text_fits'],proof['native_rows'],proof['native_pairs'])==(20,29,194,558)
    flags=['runtime_admitted','user_analysis_admitted','public_execution_adapter_available','implementation_credit','released','pushed','deployed','whole_goal_complete']
    assert not any(proof[k] for k in flags)
    sources=dict(proof['source_pins'])
    for path in [Path(__file__),ROOT/'docs/literature/MEME_PREPARED_COMPARISON_DEVELOPMENT.md']:sources[path.relative_to(ROOT).as_posix()]=pin(path)
    assert all(pin(ROOT/name)==value for name,value in sources.items())
    assert pin(ROOT/proof['artifact']['path'])=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged']
    prior=ROOT/'docs/literature/meme-native-input-development';old=read(prior/'VALIDATION.json');assert pin(prior/'controls.zip')=={k:old['archive'][k] for k in ['bytes','sha256']}
    census=read(matrix/'NATIVE-INVENTORY.json');captured=matrix/'captured';actual={}
    for path in sorted(captured.rglob('*')):
        mode=path.lstat().st_mode;name=path.relative_to(captured).as_posix()
        if stat.S_ISDIR(mode):actual[name]=dict(kind='directory')
        else:assert stat.S_ISREG(mode);actual[name]=dict(kind='file',**pin(path))
    assert set(actual)==set(census)
    for name,value in census.items():assert {k:v for k,v in value.items() if k!='mode'}==actual[name]
    native=read(captured/'COMPLETED.json');assert native['fits']==len(native['protocols'])==49
    assert all(not p['runtime_admitted'] and not p['user_analysis_admitted'] and not p['owned_live_after_cleanup'] for p in native['protocols'])
    entries={}
    def add(path,name):assert path.is_file() and not path.is_symlink() and name not in entries;entries[name]=path
    def tree(folder,prefix):
        for path in sorted(folder.rglob('*')):
            if path.is_file():add(path,prefix+'/'+path.relative_to(folder).as_posix())
    def direct(folder,prefix):
        for path in sorted(folder.iterdir()):
            if path.is_file():add(path,prefix+'/'+path.name)
    for name in sources:add(ROOT/name,'source/'+name)
    direct(matrix,'validation');tree(matrix/'source-compiled','validation/exact-source-compiled');tree(captured,'original-component-runs')
    for name in ['package-1','package-2']:
        folder=BASE/'tomtom-prepared-development-1'/name;add(folder/'FAILURE.json','history/'+name+'/FAILURE.json');tree(folder/'source-snapshot','history/'+name+'/source-snapshot');tree(folder/'failed-test-results','history/'+name+'/test-results')
    tree(ROOT/'build/test-results/test','gradle-focused-test-results')
    for name in ['org/jlinalg/sequence/MemeXml.html','org/jlinalg/sequence/MemeMotifInputs.html','org/jlinalg/sequence/TomtomResults.html','org/jlinalg/sequence/TomtomResults.TextAudit.html']:
        add(ROOT/'build/docs/javadoc'/name,'javadoc/'+name)
    add(prior/'VALIDATION.json','prior-input-component/VALIDATION.json');add(prior/'SHA256SUMS','prior-input-component/SHA256SUMS')
    out.mkdir();archive=out/'controls.zip';members={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,path in sorted(entries.items()):
            raw=path.read_bytes();info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(stat.S_IFREG|0o644)<<16;z.writestr(info,raw);members[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.infolist())==len(set(z.namelist()))==len(members)
        for name,value in members.items():raw=z.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==value
    assert archive.stat().st_size<95_000_000 and all(pin(ROOT/name)==value for name,value in sources.items())
    result=dict(schema_version=1,status='passed-typed-Tomtom-preparation-text-and-owned-original-component-controls',source_pins=sources,artifact=proof['artifact'],platform_validation=proof['runs'],archive=dict(path=archive.name,**pin(archive),entries=len(members)),archive_files=members,
        exact_source_bytecode_classes=36,added_classes=proof['added_classes'],changed_classes=proof['changed_classes'],unchanged_prior_archive_entries=proof['unchanged_prior_archive_entries'],original_parent=proof['original_parent'],canonical_executable_unchanged=proof['canonical_executable_unchanged'],
        native_fits=49,full_report_fits=20,text_fits=29,native_reported_rows=194,native_declared_pairs=558,complete_original_payload_unchanged=True,
        earlier_input_proof=dict(validation=pin(prior/'VALIDATION.json'),archive=pin(prior/'controls.zip'),unchanged=True),scope=proof['scope'],
        runtime_admitted=False,user_analysis_admitted=False,public_execution_adapter_available=False,implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,
        required_action='Read MEME_PREPARED_COMPARISON_DEVELOPMENT.md. These are exact-source typed/captured/text/output and synthetic owned-process components, not a current-host runtime ticket or public user workflow. Preserve original formats, source populations, numerical warnings and every failed attempt. Missing/forbidden dependencies require named skips and instructions to supply them on a permitted host; analysis never installs. Do not add full-report dependencies to requested text mode or silently change backgrounds, output mode, pseudocounts or exclusions. Complete native ELF/helper/loader/Perl host closure, fresh runtime health, installer, requested report family, portable final bundle, public CLI/example and catalog gates before execution credit.')
    (out/'VALIDATION.json').write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8',newline='\n')
    (out/'SHA256SUMS').write_text(pin(archive)['sha256']+'  controls.zip\n'+pin(out/'VALIDATION.json')['sha256']+'  VALIDATION.json\n',encoding='ascii',newline='\n')
    (out/'.gitattributes').write_text('* -text\n',encoding='ascii',newline='\n')
    print(json.dumps(dict(status=result['status'],archive=result['archive'],checks_per_jvm=180,native_original_fits=49,native_reported_rows=194,implementation_credit=False)))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete frozen evidence identity checks')
    main()
