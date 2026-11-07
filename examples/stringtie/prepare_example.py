# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prepare a complete synthetic StringTie example from an exported standalone kit; run no analysis."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil

LOCK = dict(bytes=28867, sha256='b2697399b8cd7e716131a54a6e75ed2b1641eb68e137c373bca52736d0200e3d')

def pin(path):
    with path.open('rb') as stream: return dict(bytes=path.stat().st_size, sha256=hashlib.file_digest(stream, 'sha256').hexdigest())

def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--kit', type=Path, required=True); parser.add_argument('--out', type=Path, required=True); parser.add_argument('--profile', default='linux-x86_64-stringtie-3.0.3')
    args = parser.parse_args(); kit, out = args.kit.resolve(), args.out.resolve()
    if out.exists() or not out.parent.is_dir(): raise RuntimeError('Choose a NEW output directory with an existing parent; existing inputs are never overwritten')
    if pin(kit / 'stringtie-runtime-lock.json') != LOCK: raise RuntimeError('Exact exported StringTie kit lock differs; export a fresh kit from the recorded development JAR')
    lock = json.loads((kit / 'stringtie-runtime-lock.json').read_bytes())
    if args.profile not in lock['profiles']: raise RuntimeError('Choose an exact listed profile; versions are never substituted')
    if not str(out).startswith('/'): raise RuntimeError('Prepare this execution example with Linux Python, including permitted Linux Python inside WSL')
    out.mkdir(mode=0o700); (out / 'INCOMPLETE.json').write_text('{"status":"EXAMPLE_PREPARATION_IN_PROGRESS"}\n'); (out / 'inputs').mkdir()
    try:
        inputs = {}
        for name in ['reads.bam', 'guide.gtf', 'reference.sizes']:
            member = 'health-inputs/' + name; expected = lock['resources'][member]
            if pin(kit / member) != expected: raise RuntimeError('Exact complete synthetic health input differs: ' + member)
            shutil.copyfile(kit / member, out / 'inputs' / name)
            if pin(out / 'inputs' / name) != expected: raise RuntimeError('Captured example bytes differ: ' + member)
            inputs[name] = expected
        parameters = dict(trimming='ORIGINAL_DEFAULT', minimum_length=None, minimum_isoform_fraction=None, minimum_coverage=None, minimum_FPKM=None, minimum_TPM=None)
        for mode in ['ASSEMBLE', 'ESTIMATE', 'MERGE']:
            request = dict(schema_version=1, profile=args.profile, mode=mode, bam=None if mode == 'MERGE' else str(out / 'inputs/reads.bam'), reference_sizes=str(out / 'inputs/reference.sizes'), guide=str(out / 'inputs/guide.gtf'),
                merge_inputs=[str(out / 'assembly/fit/transcripts.gtf')] if mode == 'MERGE' else [], genome_build='synthetic-two-contigs-not-human', strand='UNSTRANDED',
                bam_policies=None if mode == 'MERGE' else dict(missing_NH='ERROR', missing_XS='ERROR', sort_header='REQUIRE_COORDINATE'), population='COMPLETE' if mode == 'ESTIMATE' else None, threads=1, parameters=parameters, limits={})
            (out / (mode.lower() + '.json')).write_text(json.dumps(request, indent=2) + '\n', encoding='utf-8')
        (out / 'EXAMPLE.json').write_text(json.dumps(dict(status='COMPLETE_SYNTHETIC_EXAMPLE_INPUTS_ONLY', profile=args.profile, inputs=inputs, artificial_mapped_reads=840, artificial_spliced_reads=120, guide_transcripts=4, genes=3, user_analysis_performed=False, instructions='Run ASSEMBLE before MERGE, which references its complete output. ESTIMATE is independent. Keep runtime and setup report outside this example. Read the example README and every analysis WARNINGS.md.'), indent=2) + '\n', encoding='utf-8')
        (out / 'INCOMPLETE.json').unlink(); print('Prepared complete synthetic inputs and three typed recipes at ' + str(out) + '; no analysis performed.')
    except BaseException as error:
        (out / 'FAILURE.json').write_text(json.dumps(dict(status='FAIL2_EXAMPLE_PREPARATION_FAILED', error=str(error), required_action='Preserve this directory, inspect the named error and exact kit, correct the cause and retry into a NEW output directory. No analysis was performed.'), indent=2) + '\n', encoding='utf-8'); raise

if __name__ == '__main__': main()
