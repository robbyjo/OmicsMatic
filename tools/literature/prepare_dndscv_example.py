# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare an explicit original normalskin demonstration from committed inputs and a checked private runtime."""
import argparse,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reference-home',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();reference=a.reference_home.resolve();out=a.out.resolve();assert not out.exists(),'Choose a NEW demonstration directory';state=json.loads((reference/'COMPLETED.json').read_bytes());start=json.loads((reference/'STARTED.json').read_bytes());assert state['status']=='installed-exact-original-dndscv-runtime-only' and state['preexisting_libraries_unchanged'] and not state['analysis_admitted'];fixture=ROOT/'src/test/resources/org/jlinalg/genetics/dndscv/workflow-transports-v2.zip'
    with zipfile.ZipFile(fixture) as z:files={n:z.read(n) for n in ['source-request.json','inputs/mutations.tsv','inputs/genes.tsv']}
    request=json.loads(files['source-request.json']);request['analyses']=[next(c for c in request['analyses'] if c['id']=='targeted-2')];request['mutations']='mutations.tsv';request['genes']['file']='genes.tsv';request['source']=start['command'][4];request['rscript']=start['command'][0];request['r_home']=str(Path(start['command'][0]).parent.parent);request['r_libraries']=[str(reference/'R-lib'),*start['libraries']];request['out']='result';out.mkdir(parents=True)
    for name in ['mutations.tsv','genes.tsv']:(out/name).write_bytes(files['inputs/'+name])
    save(out/'request.json',request);save(out/'EXAMPLE-PROVENANCE.json',dict(status='prepared-official-normalskin-demonstration-not-analysis',fixture=pin(fixture),reference_setup=pin(reference/'COMPLETED.json'),source='Official dndscv dataset_normalskin and dataset_normalskin_genes; complete3408 events, explicit75-gene source isoform expansion.',required_action='Run the direct typed CLI with the validated development or current source-build JAR. Require exit0 and result_admitted=true and review warnings/status/provenance/native event ledger. This is a demonstration with declared settings, not a reconstruction of a catalog paper. Preserve failures and retry NEW paths.'));print('Prepared original demonstration:',out/'request.json','; analysis not executed')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable prepared-profile checks')
    main()
