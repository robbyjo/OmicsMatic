# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Package complete successful original SMR transports for portable adversarial native-audit tests."""
import argparse,json,re,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime-probe',required=True,type=Path,action='append');p.add_argument('--out',required=True,type=Path);p.add_argument('--fixture-name',default='workflow-controls-v2.zip');a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/smr-runtime') and not out.exists();out.mkdir(parents=True);files={};protocols=[];records=[]
    for given in a.runtime_probe:
      probe=given.resolve();record=json.loads((probe/'COMPLETED.json').read_bytes());assert record['status'].startswith('passed-') and all(r['passed'] for r in record['protocols']);records.append(dict(path=(probe/'COMPLETED.json').relative_to(ROOT).as_posix(),**pin(probe/'COMPLETED.json')))
      for row in record['protocols']:
        if row['exit_code']!=0:continue
        name=row['id'];assert name not in protocols;protocols.append(name);home=probe/name;request=json.loads((home/'request.json').read_bytes());files[name+'/request.json']=(home/'request.json').read_bytes()
        for file in (home/'analysis/inputs').iterdir():
            if file.is_file():files[name+'/inputs/'+file.name]=file.read_bytes()
        prepared=home/'analysis/worker/prepare/original' if request['qtl']['kind']=='qfile' else home/'analysis/inputs/qtl'
        for suffix in ['.besd','.epi','.esi']:files[name+'/prepared/original'+suffix]=Path(str(prepared)+suffix).read_bytes()
        for case in request['analyses']:
            original=home/'analysis/worker/cases'/case['id']
            for file in original.glob('original.*'):files[name+'/cases/'+case['id']+'/'+file.name]=file.read_bytes()
    def fingerprint(raw):
        import hashlib
        return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    index=dict(schema_version=1,kind='complete-original-SMR-tables-and-literal-input-transports',protocols=protocols,files={n:fingerprint(raw) for n,raw in sorted(files.items())},runtime_probes=records,scientific_outputs_modified=False,fixture_implies_runtime_or_workflow_admission=False);files['INDEX.json']=(json.dumps(index,indent=2)+'\n').encode();target=ROOT/'src/test/resources/org/jlinalg/mr/smr'/a.fixture_name;assert re.fullmatch(r'workflow-controls-v[1-9][0-9]*[.]zip',a.fixture_name) and not target.exists()
    with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,raw in sorted(files.items()):z.writestr(name,raw)
    save(out/'COMPLETED.json',dict(status='packed-complete-original-SMR-native-audit-transports',fixture=dict(path=target.relative_to(ROOT).as_posix(),**pin(target)),protocols=protocols,source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},runtime_probes=records,files=len(files),adapter_admitted=False));print('Packed',len(protocols),'original protocols;',len(files),'complete transport files')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable transport/source checks')
    main()
