# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Derive isolated Sniffles process/host/ELF primitives from frozen admitted implementations.

Only class/diagnostic names and closed JSON/hash/path plumbing are changed. The
ownership, capacities, library parser and complete-census algorithms are retained.
"""
import argparse
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save


def generated():
    folder=ROOT/'src/main/java/org/jlinalg/cli'
    process=(folder/'RsemProcess.java').read_text(encoding='utf8')
    process=process.replace('RsemProcess','SnifflesProcess').replace('RSEM','Sniffles')
    assert process.count('RsemJson.json')==2
    process=process.replace('RsemJson.json','SnifflesWorkflowJson.encode').replace('repeatmasker-owned-process-shutdown','sniffles-owned-process-shutdown').replace('Interrupted repeat analysis','Interrupted Sniffles analysis').replace('interrupted repeat analysis','interrupted Sniffles analysis')
    host=(folder/'RepeatHostFiles.java').read_text(encoding='utf8').replace('RepeatHostFiles','SnifflesHostFiles')
    assert host.count('RepeatBundle.json')==1 and host.count('RepeatInputs.hash')==1
    host=host.replace('RepeatBundle.json','SnifflesWorkflowJson.bytes').replace('RepeatInputs.hash','SnifflesWorkflowFiles.hash')
    elf=(folder/'RsemElf.java').read_text(encoding='utf8').replace('RsemElf','SnifflesElf')
    assert elf.count('RsemPayload.originalPath')==1
    elf=elf.replace('RsemPayload.originalPath','SnifflesWorkflowFiles.originalPath')
    return {'SnifflesProcess.java':process,'SnifflesHostFiles.java':host,'SnifflesElf.java':elf}


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--out',required=True,type=Path);parser.add_argument('--apply',action='store_true');args=parser.parse_args()
    out=args.out.resolve();assert not out.exists() and out.is_relative_to(ROOT/'build/literature-review/sniffles-runtime');out.mkdir(parents=True)
    folder=ROOT/'src/main/java/org/jlinalg/cli';outputs={}
    for name,text in generated().items():
        raw=text.encode();(out/name).write_bytes(raw)
        if args.apply:
            target=folder/name;assert not target.exists();target.write_bytes(raw)
        outputs[name]=pin(out/name)
    save(out/'COMPLETED.json',dict(status='PASS_DETERMINISTIC_SNIFFLES_PRIMITIVE_SOURCE_DERIVATION_ONLY',generated=outputs,applied=args.apply,
        original_source_pins={p.relative_to(ROOT).as_posix():pin(p) for p in [folder/'RsemProcess.java',folder/'RepeatHostFiles.java',folder/'RsemElf.java']},
        implementation_credit=False,execution_validation_pending=True))
    print('Derived three isolated primitives; original source files unchanged')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact derivation checks')
    main()
