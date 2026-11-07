# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze native bigWig API and all independent reference discrepancies without public-tool credit."""
import argparse
import hashlib
import json
from pathlib import Path
import stat
import zipfile

ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/bigwig-native'


def read(path):return json.loads(path.read_bytes())
def pin(path):
    with path.open('rb') as stream:return dict(bytes=path.stat().st_size,sha256=hashlib.file_digest(stream,'sha256').hexdigest())


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--matrix',type=Path,required=True);parser.add_argument('--out',type=Path,required=True);args=parser.parse_args();matrix,out=args.matrix.resolve(),args.out.resolve();assert matrix.is_relative_to(BASE.resolve()) and out.is_relative_to((ROOT/'docs/literature').resolve()) and not out.exists();proof=read(matrix/'COMPLETED.json');assert proof['status']=='passed-exact-source-native-bigWig-writer-three-JVM-and-independent-original-readers'
    assert proof['exact_source_classes']==len(proof['new_classes'])==9 and proof['unchanged_parent_entries']==19505 and proof['protocols']==22 and proof['complete_native_intervals']==79948
    assert sorted(r['name'] for r in proof['runs'])==['linux-java21','windows-java21','windows-java25'] and all(r['tests']==r['passed']==28 and r['skipped']==0 for r in proof['runs']);assert not any(proof[k] for k in ['public_workflow_available','implementation_credit','released','pushed','deployed','whole_goal_complete'])
    cases=proof['original_reader_results']['cases'];assert len(cases)==22 and all(r['complete_UCSC_unsigned_coordinates_exact'] for r in cases);success=[r for r in cases if r['comparison']['original_conversion_exit']==0];assert len(success)==20 and all(r['comparison']['original_raw_float32_exact'] for r in success);failed=[r for r in cases if r['comparison']['original_conversion_exit']!=0];assert {r['id'] for r in failed}=={'empty-declared-track','unsigned32-coordinates'}
    caveats=[r for r in cases if r['original_pyBigWig_signed_coordinate_views']];assert len(caveats)==1 and caveats[0]['id']=='unsigned32-coordinates' and len(caveats[0]['original_pyBigWig_signed_coordinate_views'])==2;guards=[r['id'] for r in cases if r['comparison'].get('original_header_unavailable')];assert len(guards)==9
    intake=BASE/'source-intake-1';source=read(intake/'COMPLETED.json');assert source['kent_commit']=='c00932ec61a581a351713b7ff301f9f1532c5a23'
    for name,value in source['files'].items():assert pin(intake/name)=={k:value[k] for k in ['bytes','sha256']}
    sources=dict(proof['source_pins']);extra=[Path(__file__),ROOT/'docs/literature/BIGWIG_NATIVE_DEVELOPMENT.md',ROOT/'tools/literature/audit_bigwig_paper_uses.py',ROOT/'tools/literature/prepare_bigwig_use_reviews.py',ROOT/'tools/literature/bigwig-use-reviews.json']
    for path in extra:sources[path.relative_to(ROOT).as_posix()]=pin(path)
    assert all(pin(ROOT/n)==value for n,value in sources.items()) and pin(ROOT/proof['artifact']['path'])=={k:proof['artifact'][k] for k in ['bytes','sha256']} and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged'];audit=read(BASE/'primary-use-audit-1/PRIMARY-USES.json');assert audit['papers_scanned']==250 and len(audit['records'])==5 and len(read(ROOT/'tools/literature/bigwig-use-reviews.json'))==5
    entries={}
    def add(path,name):assert path.is_file() and not path.is_symlink() and name not in entries;entries[name]=path
    def tree(folder,prefix):
        for path in sorted(folder.rglob('*')):
            if path.is_file():add(path,prefix+'/'+path.relative_to(folder).as_posix())
    def direct(folder,prefix):
        for path in sorted(folder.iterdir()):
            if path.is_file():add(path,prefix+'/'+path.name)
    for name in sources:add(ROOT/name,'source/'+name)
    direct(matrix,'validation');tree(matrix/'source-compiled','validation/exact-source-compiled');tree(matrix/'native-controls','native-output-controls');tree(matrix/'original-controls','original-UCSC-controls');tree(BASE/'primary-use-audit-1','primary-source-audit')
    for name in ['matrix-1','matrix-2','matrix-3','matrix-4']:
        area=matrix.parent/name;direct(area,'history/'+name)
        for folder in ['source-snapshot','native-controls','original-controls']:
            if (area/folder).exists():tree(area/folder,'history/'+name+'/'+folder)
    add(intake/'COMPLETED.json','private-reference-intake/COMPLETED.json');add(intake/'kent/LICENSE','private-reference-intake/UPSTREAM-LICENSE');add(intake/'kent-commit.json','private-reference-intake/kent-commit.json');tree(ROOT/'build/test-results/test','gradle-focused-test-results');out.mkdir();archive=out/'controls.zip';members={}
    with zipfile.ZipFile(archive,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,path in sorted(entries.items()):
            raw=path.read_bytes();info=zipfile.ZipInfo(name,(1980,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED;info.external_attr=(stat.S_IFREG|0o644)<<16;z.writestr(info,raw);members[name]=dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    with zipfile.ZipFile(archive) as z:
        assert z.testzip() is None and len(z.infolist())==len(set(z.namelist()))==len(members)
        for name,value in members.items():raw=z.read(name);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==value
    assert archive.stat().st_size<95000000 and all(pin(ROOT/n)==value for n,value in sources.items());result=dict(schema_version=1,status='passed-native-bigWig-API-and-complete-original-reference-controls-public-workflow-open',source_pins=sources,artifact=proof['artifact'],platform_validation=proof['runs'],archive=dict(path=archive.name,**pin(archive),entries=len(members)),archive_files=members,exact_source_classes=9,new_classes=proof['new_classes'],unchanged_parent_entries=19505,parent_artifact=proof['parent_artifact'],canonical_executable_unchanged=proof['canonical_executable_unchanged'],native_protocols=22,complete_native_intervals=79948,complete_UCSC_decoded_coordinates_exact=True,successful_original_converter_protocols=20,original_converter_failures=failed,original_pyBigWig_signed_coordinate_caveat=caveats,original_header_crash_case_directly_observed='float32-boundaries',original_nonfinite_header_guards=guards,total_summary_discrepancies=[r for r in cases if r['comparison'].get('total_summary_differences')],original_reader_results=proof['original_reader_results'],private_reference_intake=proof['original_reference_intake'],independent_reader_pins=proof['independent_reader_pins'],source_papers_scanned=250,named_converter_source_roles=5,pending_new_role_reviews=4,existing_role_review_preserved=True,source_reviews_applied=False,public_workflow_available=False,implementation_credit=False,released=False,pushed=False,deployed=False,whole_goal_complete=False,scope=proof['scope'],required_action='Read BIGWIG_NATIVE_DEVELOPMENT.md. API output is not a completed public analysis bundle or integration credit. Complete typed public capture/precision/reference ledgers, actionable warnings, portable CLI verification, packaged examples and catalog/browser gates. Native output contains complete raw data without zoom approximations; double total summaries differ from original float arithmetic. Preserve exact original nonfinite values, observed original-header crash, signed-coordinate reader views and converter timeout/failure. Missing/forbidden requirements must skip dependent analysis; invalid input/precision/capacity/I/O/cancellation admits no result. Preserve partial files and use NEW paths. Cell Ranger remains excluded; the whole250-paper implementation goal remains active.')
    (out/'VALIDATION.json').write_text(json.dumps(result,indent=2,allow_nan=False)+'\n',encoding='utf-8',newline='\n');(out/'SHA256SUMS').write_text(pin(archive)['sha256']+'  controls.zip\n'+pin(out/'VALIDATION.json')['sha256']+'  VALIDATION.json\n',encoding='ascii',newline='\n');(out/'.gitattributes').write_text('* -text\n',encoding='ascii',newline='\n');print(json.dumps(dict(status=result['status'],archive=result['archive'],checks_per_JVM=28,protocols=22,complete_intervals=79948,implementation_credit=False)))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete native/reference evidence checks')
    main()
