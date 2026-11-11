# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Pack the exact complete admitted original worker transports for offline native semantic tests."""
import json,zipfile
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import byte_pin


def main():
    base=ROOT/'build/literature-review/dndscv-runtime/workflow-probe-1';proof=json.loads((base/'COMPLETED.json').read_bytes());assert proof['status']=='passed-typed-original-dndscv-workflow-probe' and proof['preexisting_libraries_unchanged'];result=base/'result';assert json.loads((result/'analysis-status.json').read_bytes())['result_admitted'];target=ROOT/'src/test/resources/org/jlinalg/genetics/dndscv/workflow-transports-v2.zip';assert not target.exists();files={};external={}
    for path in result.rglob('*'):
        if not path.is_file():continue
        name=path.relative_to(result).as_posix()
        if path.name in ['original-return.rds','poisson-model.rds','nb-model.rds','indel-model.rds']:external[path.relative_to(ROOT).as_posix()]=pin(path);continue
        if name=='source-request.json' or name.startswith('inputs/') or name.startswith('worker/cases/') and not name.endswith('native-event-ledger.tsv') or name.startswith(('worker/reference-input/','worker/constructed-mutations.')) or name in ['worker/reference-selection.json','worker/reference-input-L.f64be.gz','worker/worker-report.json']:files[name]=path.read_bytes()
    index={n:byte_pin(raw) for n,raw in files.items()};files['INDEX.json']=(json.dumps(dict(schema_version=1,files=index),sort_keys=True,indent=2)+'\n').encode('utf8')
    with zipfile.ZipFile(target,'x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,raw in sorted(files.items()):z.writestr(name,raw)
    save(target.parent/'WORKFLOW-ORIGINS.json',dict(status='exact-complete-original-dndscv-typed-worker-transports',fixture=pin(target),logical_files=len(index),original_case_ids=['targeted-192','targeted-2','one-sided-selection'],runtime_probe=pin(base/'COMPLETED.json'),artifact=proof['artifact'],original_opaque_model_and_return_RDS_retained_locally=external,scope='Complete captured typed tables, table RDS, IEEE arrays, model diagnostics and reference/input records from the admitted prototype. Large opaque model/whole-return RDS remain unchanged locally with exact receipts, outside this focused native-test fixture. Read-only tests do not fit models, launch R or admit a new analysis.',implementation_credit=False));print('Packed',len(index),'exact original typed transport files;',pin(target)['bytes'],'bytes; retained',len(external),'opaque RDS files locally')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/transport checks')
    main()
