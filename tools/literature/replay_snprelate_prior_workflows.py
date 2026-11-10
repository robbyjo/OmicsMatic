# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Replay all240 preceding original SNPRelate bundles through the current four-workflow verifier, without R."""
import argparse
import json
import subprocess
import traceback
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from probe_king_runtime import DISTRO,linux
BASE=ROOT/'build/literature-review/snprelate-runtime'
JAVA=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe')


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--partition',default='0/1');a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_CURRENT_SNPRELATE_PREDECESSOR_REPLAY',new_catalog_credit=False,whole_goal_complete=False,original_R_analysis_executed=False);records=[]
    try:
        build=a.build.resolve();metadata=json.loads((build/'COMPLETED.json').read_bytes());assert metadata['status']=='PASS_ISOLATED_SNPRELATE_ADAPTER_BUILD_ONLY'
        jar=ROOT/metadata['artifact']['path'];assert pin(jar)=={k:metadata['artifact'][k] for k in ['bytes','sha256']}
        assert all(pin(ROOT/name)==value for name,value in metadata['source_pins'].items())
        producer=pin(Path(__file__));canonical=ROOT/'build/cli/omicsmatic-0.3.7.jar';before=pin(canonical);cases=[];original_records={}
        for part in range(4):
            folder=BASE/('adapter-census-1-part'+str(part));done=json.loads((folder/'COMPLETED.json').read_bytes())
            assert done['status']=='PASS_ORIGINAL_SNPRELATE_COMPLETE_TYPED_WORKFLOW_REFERENCE_CENSUS' and len(done['records'])==60
            original_records[str(folder/'COMPLETED.json')]=pin(folder/'COMPLETED.json')
            cases.extend((folder/r['case'],r['actual']) for r in done['records'])
        assert len(cases)==len({str(path) for path,_ in cases})==240
        part,total=map(int,a.partition.split('/'));assert 0<=part<total<=8;cases=cases[part::total]
        for bundle,expected in cases:
            for host in ['windows','linux']:
                label=bundle.name+'-'+host
                command=([str(JAVA),'-Xmx1g','-jar',str(jar),'--verify',str(bundle)] if host=='windows' else
                    ['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-Xmx1g','-jar',linux(jar),'--verify',linux(bundle)])
                r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=120)
                (out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr)
                save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode))
                assert r.returncode==expected,(label,r.returncode,expected,r.stderr[-4000:])
                records.append(dict(bundle=bundle.relative_to(ROOT).as_posix(),host=host,expected=expected,actual=r.returncode))
            print(json.dumps(dict(bundle=bundle.name,expected=expected,current_saved_checks=2)),flush=True)
        assert pin(jar)=={k:metadata['artifact'][k] for k in ['bytes','sha256']} and pin(canonical)==before and pin(Path(__file__))==producer
        assert all(pin(ROOT/name)==value for name,value in metadata['source_pins'].items()) and all(pin(Path(name))==value for name,value in original_records.items())
        state.update(status='PASS_CURRENT_SNPRELATE_EXACT_PREDECESSOR_PRODUCER_AND_NATIVE_REPLAY',records=records,partition=a.partition,total_declared_bundles=240,
            artifact=metadata['artifact'],source_pins=metadata['source_pins'],producer=producer,canonical_executable_unchanged=before,
            exact_preceding_worker_sha256='8c0c02a632d4b9c3484167ff381e116910098be69321717e7a375e7c0040acb9')
    except Exception as error:
        state.update(error=repr(error),records=records);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:3500],flush=True)
    return 0 if state['status'].startswith('PASS_CURRENT_') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact predecessor source/profile verification')
    raise SystemExit(main())
