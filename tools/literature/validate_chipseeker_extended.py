# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Compare actual adapter strand/priority/one-query behavior with independent original calls."""
import argparse
import csv
import json
from pathlib import Path
import subprocess
from setup_deseq2_reference import ROOT, RSCRIPT, pin, save
from validate_mashr_native import JAVA21


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--artifact-build', type=Path, required=True); p.add_argument('--original', type=Path, required=True); p.add_argument('--out', type=Path, required=True)
    args = p.parse_args(); original = args.original.resolve(); out = args.out.resolve()
    assert out.is_relative_to(ROOT / 'build/literature-review/chipseeker-runtime') and not out.exists();out.mkdir()
    build = json.loads((args.artifact_build / 'COMPLETED.json').read_bytes());jar = ROOT / build['artifact']['path']
    assert pin(jar) == {k: build['artifact'][k] for k in ('bytes', 'sha256')}
    assert all(pin(ROOT / name) == expected for name, expected in build['source_pins'].items())
    source = json.loads((original / 'COMPLETED.json').read_bytes())
    assert source['status'] == 'captured-original-extended-ChIPseeker-controls-not-adapter-admission'
    assert all(pin(ROOT / name) == expected for name, expected in source['source_pins'].items())
    bindings = {Path(__file__).resolve().relative_to(ROOT).as_posix(): pin(Path(__file__))};runs=[];returned=errors=0
    def read(path):bindings[path.relative_to(ROOT).as_posix()] = pin(path);return json.loads(path.read_bytes())
    for edition in source['editions']:
        version = edition['version'];folder = original / version;plan = read(folder / 'PLAN.json');oracle = {r['id']:r for r in read(folder / 'ORIGINAL.json')['cases']}
        groups = {}
        for case in plan['cases']:groups.setdefault(case['group'], []).append(case)
        for group, cases in groups.items():
            directory = out / version / group;directory.mkdir(parents=True)
            assert all(case['peaks'] == cases[0]['peaks'] for case in cases)
            with (directory / 'peaks.tsv').open('w',encoding='utf-8',newline='') as stream:
                writer=csv.DictWriter(stream,fieldnames=['peak_id','contig','start','end','strand'],delimiter='\t',lineterminator='\n',quoting=csv.QUOTE_NONE,quotechar=None)
                writer.writeheader();writer.writerows(cases[0]['peaks'])
            analyses=[dict(id=c['id'],same_strand=c['same_strand'],annotation_priority=c['priority']) for c in cases]
            expected=2 if any(oracle[c['id']]['source_error'] is not None for c in cases) else 0
            request=dict(schema_version=1,mode='ANNOTATE',version=version,peaks='peaks.tsv',txdb=plan['txdb'],
                reference_build='SYNTHETIC_CHIPSEEKER_CONTROL_V1',coordinates='one-based-inclusive',out='result',rscript=str(RSCRIPT),
                r_libraries=plan['libraries'],analyses=analyses,timeout_seconds=300)
            save(directory/'request.json',request)
            command=[str(JAVA21/'java.exe'),'-Xmx768m','-XX:ActiveProcessorCount=2','-Dfile.encoding=UTF-8','-cp',str(jar),
                     'org.jlinalg.cli.ChipSeekerAnnotationCli','--request',str(directory/'request.json')]
            with (directory/'stdout.log').open('xb') as stdout,(directory/'stderr.log').open('xb') as stderr:
                run=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,stdout=stdout,stderr=stderr,timeout=600)
            save(directory/'process.json',dict(command=command,exit_code=run.returncode,expected_exit=expected))
            assert run.returncode==expected,str(directory)+': read retained actual-JAR diagnostic bundle'
            bundle=directory/'result';state=json.loads((bundle/'analysis-status.json').read_bytes());assert state['result_admitted']==(expected==0)
            worker=json.loads((bundle/'worker/worker-report.json').read_bytes());actual_cases={r['id']:r for r in worker['cases']}
            for case in cases:
                case_id=case['id'];expected_error=oracle[case_id]['source_error'];actual=actual_cases[case_id]
                if expected_error is not None:
                    assert actual['original_error']['message']==expected_error['message'];errors+=1
                else:
                    assert actual['original_error'] is None
                    expected_output=read(folder/case_id/'original-output.json')
                    assert json.loads((bundle/'worker/cases'/case_id/'original-output.json').read_bytes())==expected_output,(version,case_id,'original result differs')
                    if expected==0:
                        assert json.loads((bundle/'results'/case_id/'original-output.json').read_bytes())==expected_output
                        status=json.loads((bundle/'results'/case_id/'analysis-status.json').read_bytes())
                        expected_native='SKIPPED_NO_NATIVE_RESULT' if group=='stranded' else 'passed'
                        assert status['report']['native_nearest_audit']==expected_native
                    returned+=1
            if expected:
                assert not (bundle/'results').exists()
                # This is a mixed returned/error batch: even the returned member
                # remains diagnostic evidence and is not admitted downstream.
                assert any(r['original_error'] is None for r in worker['cases'])
            runs.append(dict(version=version,group=group,cases=len(cases),expected_exit=expected,observed_exit=run.returncode))
            print(json.dumps(runs[-1]),flush=True)
    assert all(pin(ROOT/name)==expected for name,expected in bindings.items())
    assert all(pin(ROOT/name)==expected for name,expected in build['source_pins'].items())
    save(out/'COMPLETED.json',dict(status='passed-actual-JAR-original-strand-priority-and-single-query-controls',artifact=build['artifact'],source_pins=bindings,
        runs=runs,original_returned_cases=returned,original_error_cases=errors,mixed_batch_rejects_all_members=True,
        project_root_or_catalog_admitted=False))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable actual-JAR or original-output checks')
    main()
