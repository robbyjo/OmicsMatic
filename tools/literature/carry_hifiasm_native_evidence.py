# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind unchanged native class families to the current candidate without changing any earlier proof or hash."""
import argparse,json,traceback,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--build',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/hifiasm-runtime');out.mkdir(parents=True);state=dict(status='FAIL2_HIFIASM_NATIVE_EVIDENCE_CARRY',components={},implementation_credit=False,analysis_admitted=False)
    try:
        built=json.loads((a.build/'COMPLETED.json').read_bytes());current=ROOT/built['artifact']['path'];assert pin(current)=={k:built['artifact'][k] for k in ['bytes','sha256']};base=ROOT/'build/literature-review/hifiasm-runtime';definitions=[('parental-native-2','HifiasmParentalKmers','passed-three-JVM-original-parental-primitives-and-native-YAK-reader-compatibility-only'),('gfa-native-3','HifiasmGfa','passed-three-JVM-complete-246-original-hifiasm-graph-and-FASTA-views'),('read-native-3','HifiasmReads','passed-three-JVM-complete-original-kseq-fields-and-native-admission-contracts-only')]
        for directory,name,status in definitions:
            receipt=base/directory/'COMPLETED.json';report=json.loads(receipt.read_bytes());assert report['status']==status,(name,report['status']);parent=report['artifact'] if name!='HifiasmGfa' else next(iter(report['partitions'].values()))['report']['artifact'];jar=ROOT/parent['path'];assert pin(jar)=={k:parent[k] for k in ['bytes','sha256']};prefix='org/jlinalg/assembly/'+name;classes={}
            with zipfile.ZipFile(current) as after,zipfile.ZipFile(jar) as before:
                names=lambda z:{n for n in z.namelist() if (n==prefix+'.class' or n.startswith(prefix+'$')) and n.endswith('.class')};assert names(after)==names(before) and names(after)
                for member in sorted(names(after)):
                    assert after.read(member)==before.read(member),(name,member);raw=after.read(member);classes[member]=dict(bytes=len(raw),sha256=__import__('hashlib').sha256(raw).hexdigest())
            source='src/main/java/org/jlinalg/assembly/'+name+'.java';assert source in built['source_pins'] and pin(ROOT/source)==built['source_pins'][source];state['components'][name]=dict(earlier_receipt=dict(path=receipt.relative_to(ROOT).as_posix(),**pin(receipt)),earlier_artifact=parent,current_artifact=built['artifact'],unchanged_complete_class_family=classes,current_source_pin=built['source_pins'][source],earlier_proof_modified=False)
        assert all(pin(ROOT/n)==v for n,v in built['source_pins'].items());state.update(status='passed-unchanged-complete-native-class-families-bound-to-current-hifiasm-candidate',artifact=built['artifact'],source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__).resolve())})
    except Exception as error:state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error',''));return 0 if state['status'].startswith('passed-') else 2


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable native class-family identity checks')
    raise SystemExit(main())
