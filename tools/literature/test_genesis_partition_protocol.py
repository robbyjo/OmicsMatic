# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Exercise missing-Java/output preservation, resealed scientific refusals and a complete Java API example."""
import argparse
import copy
import json
import shutil
import subprocess
import sys
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,byte_pin
from verify_genesis_partition_evidence import HOME,inspect,BASE as PREFIX
from probe_king_runtime import DISTRO,linux
BASE=ROOT/'build/literature-review/genesis-runtime'
TOOLS=Path('C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin')


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve()
    assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    proof,files=inspect();original={p:pin(ROOT/p) for p in proof['source_pins']};original.update({str((HOME/n).relative_to(ROOT)):pin(HOME/n) for n in [*proof['artifacts'],'controls.zip','VALIDATION.json']})
    records=[]
    def run(label,command,expected):
        r=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=240)
        (out/(label+'.stdout')).write_bytes(r.stdout);(out/(label+'.stderr')).write_bytes(r.stderr);save(out/(label+'.process.json'),dict(command=command,exit_code=r.returncode))
        assert r.returncode==expected,(label,r.returncode,expected,r.stdout[-4000:],r.stderr[-3500:]);records.append(dict(case=label,expected=expected,actual=r.returncode));return r
    verifier=ROOT/'tools/literature/verify_genesis_partition_evidence.py'
    run('missing-java',[sys.executable,str(verifier),'--java',str(out/'absent-java'),'--out',str(out/'missing-java')],3)
    assert json.loads((out/'missing-java/COMPLETED.json').read_bytes())['status'].startswith('SKIP3_')
    prior=BASE/'native-partition-offline-win21-1';prior_pin=pin(prior/'COMPLETED.json')
    run('existing-output',[sys.executable,str(verifier),'--java',str(TOOLS/'java.exe'),'--out',str(prior)],2);assert pin(prior/'COMPLETED.json')==prior_pin
    for label,relative,needle in [('resealed-partition-ID','path-ties/original-partition.tsv','Complete source partition/identity/order differs'),
        ('resealed-R64-sum','original-scalar-sums.tsv','Original R64 sum differs')]:
        home=out/label;home.mkdir();mutated=copy.copy(files);name=PREFIX+'original-partition-controls-6/'+relative
        lines=mutated[name].decode('utf8').splitlines();header=lines[0].split('\t');values=lines[1].split('\t')
        if 'partition.tsv' in relative:values[header.index('id_utf8_hex')]='altered-owned-id'.encode().hex()
        else:values[header.index('source_sum_hex')]='0x1p+0'
        lines[1]='\t'.join(values);mutated[name]=('\n'.join(lines)+'\n').encode('utf8')
        completed_name=PREFIX+'original-partition-controls-6/COMPLETED.json';completed=json.loads(mutated[completed_name]);completed['files'][relative]=byte_pin(mutated[name]);mutated[completed_name]=(json.dumps(completed,indent=2)+'\n').encode()
        for artifact in proof['artifacts']:shutil.copyfile(HOME/artifact,home/artifact)
        packing=pack(home/'controls.zip',mutated);replacement=dict(proof,**packing,archive=pin(home/'controls.zip'));save(home/'VALIDATION.json',replacement)
        run(label,[sys.executable,str(verifier),'--home',str(home),'--java',str(TOOLS/'java.exe'),'--out',str(home/'replay')],2)
        result=json.loads((home/'replay/COMPLETED.json').read_bytes());assert result['status'].startswith('FAIL2_') and needle in result['cause']
    source=ROOT/'examples/genesis-partition/GenesisPartitionDemo.java';source_pin=pin(source);classes=out/'example-classes';classes.mkdir();api=HOME/'genesis-partition-api-javac21.jar'
    run('compile-example',[str(TOOLS/'javac.exe'),'--release','17','-Xlint:all','-Werror','-g','-encoding','UTF-8','-cp',str(api),'-d',str(classes),str(source)],0)
    for host in ['windows','linux']:
        command=([str(TOOLS/'java.exe'),'-cp',str(classes)+';'+str(api),'GenesisPartitionDemo'] if host=='windows' else
            ['wsl.exe','-d',DISTRO,'--exec','/usr/bin/java','-cp',linux(classes)+':'+linux(api),'GenesisPartitionDemo'])
        result=run('run-example-'+host,command,0);assert b'Related: [A, C]' in result.stdout and b'Unrelated: [B, D]' in result.stdout and not result.stderr
    assert pin(source)==source_pin and all(pin(ROOT/n)==v for n,v in original.items())
    save(out/'COMPLETED.json',dict(status='PASS7_NATIVE_GENESIS_SKIP_PRESERVATION_RESEALED_REFUSAL_AND_API_EXAMPLE_CONTRACTS',records=records,
        source_pins={str(p.relative_to(ROOT)):pin(p) for p in [Path(__file__),source,ROOT/'examples/genesis-partition/README.md']},
        original_sources_and_evidence_preserved=True,original_R_analysis_executed=False,new_catalog_credit=False,whole_goal_complete=False))
    print('PASS7_NATIVE_GENESIS_SKIP_PRESERVATION_RESEALED_REFUSAL_AND_API_EXAMPLE_CONTRACTS',flush=True)


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable original preservation or resealed semantic refusals')
    main()
