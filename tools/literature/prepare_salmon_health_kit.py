# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a synthetic setup-only Salmon health corpus from immutable successful controls."""
import hashlib
import io
import json
from pathlib import Path
import tarfile
import zipfile

ROOT=Path(__file__).resolve().parents[2]


def sha(raw):return hashlib.sha256(raw).hexdigest()


def main():
    target=ROOT/'src/main/resources/org/jlinalg/omics/salmon-runtime-setup';assert not target.exists()
    base=ROOT/'docs/literature/salmon-native-development';proof=json.loads((base/'VALIDATION.json').read_bytes());archive=base/'controls.zip';assert sha(archive.read_bytes())==proof['archive']['sha256']
    target.mkdir(parents=True);inputs=target/'health-inputs';inputs.mkdir();files={}
    with zipfile.ZipFile(archive) as z:
        for name in ['gentrome.fa','transcripts.fa','decoys.txt','reads_1.fastq','reads_2.fastq']:
            original='evidence/reference-protocols-3/inputs/'+name;raw=z.read(original);pin=proof['archive_files'][original];assert len(raw)==pin['bytes'] and sha(raw)==pin['sha256']
            (inputs/name).write_bytes(raw);files[name]=dict(bytes=len(raw),sha256=sha(raw))
        tar=z.read('official-source/1.8.0/source.tar.gz')
        with tarfile.open(fileobj=io.BytesIO(tar)) as t:license_bytes=t.extractfile('salmon-1.8.0/LICENSE').read()
    (target/'LICENSE-Salmon.txt').write_bytes(license_bytes)
    names=[];name=None;sequence=[]
    for line in (inputs/'transcripts.fa').read_text().splitlines():
        if line.startswith('>'):
            if name is not None:names.append(dict(name=name,length=len(''.join(sequence))))
            name=line[1:].split()[0];sequence=[]
        else:sequence.append(line)
    names.append(dict(name=name,length=len(''.join(sequence))));assert len(names)==40
    executable='@HOME@/bin/salmon';data='@REPORT@/inputs/';index='@REPORT@/health/index'
    commands=[dict(label='health-index',command=[executable,'--no-version-check','index','-t',data+'gentrome.fa','-d',data+'decoys.txt','-i',index,'-k','31','-p','2','--keepDuplicates'])]
    for label,library,optimizer,samples in [('health-em','A','--useEM',50),('health-vb','ISR','--useVBOpt',100)]:
        commands.append(dict(label=label,command=[executable,'--no-version-check','quant','-i',index,'-l',library,'-1',data+'reads_1.fastq','-2',data+'reads_2.fastq',optimizer,'--seqBias','--gcBias','--posBias','--numBootstraps',str(samples),'-p','2','-o','@REPORT@/health/'+label],
            output='health/'+label,expected=dict(processed_fragments=10020,mapped_fragments=8520,valid_targets=40,decoy_targets=1,bootstraps=samples,optimizer='em' if optimizer=='--useEM' else 'vb',count_precision=3)))
    lock=dict(schema_version=1,status='synthetic-setup-health-only',source_checkpoint_sha256=sha((base/'VALIDATION.json').read_bytes()),
        files=files,targets=names,commands=commands,source_warning='This tiny synthetic corpus is below original burn-in thresholds; retain raw observed-mapping effective-length fallback and all runtime warnings. Setup health does not admit a user analysis or certify biological accuracy.')
    raw=(json.dumps(lock,indent=2)+'\n').encode();(target/'health-lock.json').write_bytes(raw)
    (target/'README.txt').write_text('Salmon runtime setup only. Use install_salmon.py --help. Installation requires explicit --allow-install; network acquisition independently requires --allow-download. Complete offline archives use --archive-dir. Choose an exact profile and NEW private Linux home/report paths. Runtime health uses synthetic inputs only and does not admit user quantification. Read every WARNINGS.md and retained failure record. Missing/forbidden dependencies skip dependent analysis (SKIP3); input/integrity/process/numerical/capacity failures admit no result (FAIL2). Cell Ranger remains excluded.\n',encoding='utf-8',newline='\n')
    print(json.dumps(dict(health_lock_bytes=len(raw),health_lock_sha256=sha(raw),input_files=len(files),input_bytes=sum(v['bytes'] for v in files.values()),targets=40)))


if __name__=='__main__':main()
