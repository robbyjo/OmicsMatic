# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Resealed peak capture corruption refusals and a demonstrated statistical authenticity limit."""
import argparse
import shutil
from signac_peaks_validation import *


def edit(root,name,mutate):
    value=read(root/name);mutate(value);save(root/name,value)


def edit_table(root,name,key,value):
    rows=table(root/name);rows[0][key]=value
    with (root/name).open('w',encoding='utf8',newline='') as f:
        w=csv.DictWriter(f,fieldnames=rows[0].keys(),delimiter='\t',lineterminator='\n');w.writeheader();w.writerows(rows)


def reseal(root):
    # Rebuild every generic transaction hash too: refusal must come from semantic checks.
    generic=read(root/'manifest.json')
    generic['output_sha256']={n:v['sha256'] for n,v in files(root).items() if n not in ['manifest.yaml','capture-files.json']}
    save(root/'manifest.yaml',generic)  # JSON is also unambiguous safe YAML.
    save(root/'capture-files.json',dict(schema_version=1,authentication=False,
         files={n:v['sha256'] for n,v in files(root).items() if n!='capture-files.json'},
         directories=sorted(p.relative_to(root).as_posix() for p in root.rglob('*') if p.is_dir())))


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True)
    out=p.parse_args().out.resolve();assert out.is_relative_to(BASE) and not out.exists();out.mkdir()
    artifact=pin(JAR);sources={p.relative_to(ROOT).as_posix():pin(p) for p in [Path(__file__),ROOT/'tools/literature/signac_peaks_validation.py']}
    pooled=BASE/'peaks-workflow-smoke-1/analysis-结果';grouped=BASE/'peaks-workflow-smoke-2/analysis-结果'
    originals={str(p):files(p) for p in [pooled,grouped]}
    def raw_start(root):
        p=root/'macs/g1/g1_peaks.narrowPeak';rows=p.read_text().splitlines();v=rows[0].split('\t');v[1]=str(int(v[1])+1);rows[0]='\t'.join(v);p.write_text('\n'.join(rows)+'\n')
    def split(root):
        p=root/'prepared/g1.bed';v=p.read_bytes();p.write_bytes(v.replace(b'chr1\t',b'chr2\t',1))
    def input(root):
        p=root/'inputs/s1.fragments';p.write_bytes(p.read_bytes()+b'\n')
        edit(root,'manifest.json',lambda v:v['captured_input_sha256'].update({'inputs/s1.fragments':pin(p)['sha256']}))
    changes=[
        ('published-coordinate',pooled,lambda d:edit_table(d,'peaks.tsv','start','1')),
        ('published-probability',pooled,lambda d:edit_table(d,'peaks.tsv','negative_log10_pvalue','0.25')),
        ('raw-source-coordinate',pooled,raw_start),
        ('native-group-split',grouped,split),
        ('captured-invalid-input-with-updated-manifest',pooled,input),
        ('request-settings',pooled,lambda d:edit(d,'request.json',lambda v:v.update(qvalue=0.2))),
        ('source-bytes',pooled,lambda d:(d/'source/peaks-1.9.0.R').write_bytes(b'# changed\n')),
        ('runtime-source-pin',pooled,lambda d:edit(d,'runtime-binding.json',lambda v:v['fingerprints']['package_files']['__init__.py'].update(sha256='0'*64))),
        ('runtime-version',pooled,lambda d:edit(d,'runtime-binding.json',lambda v:v['metadata']['versions'].update(numpy='0.0'))),
        ('command',pooled,lambda d:edit(d,'process/g1/process.json',lambda v:v['command'].append('--nolambda'))),
        ('capacity',pooled,lambda d:edit(d,'process/g1/process.json',lambda v:v['limits'].update(cpuSeconds=99))),
        ('owned-live-process',pooled,lambda d:edit(d,'process/g1/process.json',lambda v:v.update(owned_live_after_cleanup=[123456]))),
        ('native-combined-membership',grouped,lambda d:edit_table(d,'combined-peaks.tsv','peak_called_in','forged')),
        ('required-warning-text',pooled,lambda d:(d/'WARNINGS.md').write_text('No warnings.\n')),
        ('diagnostic-rows',pooled,lambda d:edit(d,'diagnostics.json',lambda v:v.update(source_peak_rows=999))),
        ('completion-claim',pooled,lambda d:edit(d,'COMPLETED.json',lambda v:v.update(statistical_replay=True))),
        ('manifest-replay-claim',pooled,lambda d:edit(d,'manifest.json',lambda v:v.update(verify_replays_MACS_statistics=True))),
        ('nested-file',pooled,lambda d:(d/'source/unlisted.txt').write_text('unlisted')),
        ('empty-directory',pooled,lambda d:(d/'source/unlisted').mkdir()),
    ]
    runs=[]
    try:
        for name,original,mutate in changes:
            target=out/(name+'-TEST-ONLY');shutil.copytree(original,target);mutate(target);reseal(target)
            verify(out,name,target,2,('windows-java21',));runs.append(dict(control=name,files=files(target),expected_exit_code=2))
        # A consistent source-value forgery is deliberately admitted by capture consistency,
        # because neither the statistical model nor execution provenance is authenticated.
        target=out/'coordinated-statistics-TEST-ONLY-NOT-A-SCIENTIFIC-RESULT';shutil.copytree(pooled,target)
        p=target/'macs/g1/g1_peaks.narrowPeak';rows=p.read_text().splitlines();v=rows[0].split('\t');v[7]='0.25';rows[0]='\t'.join(v);p.write_text('\n'.join(rows)+'\n')
        edit_table(target,'peaks.tsv','negative_log10_pvalue','0.25');reseal(target);verify(out,'coordinated-forgery',target)
        (out/'COORDINATED-FORGERY-WARNING.md').write_text(
            '# TEST ONLY: FABRICATED PEAK STATISTICS ARE NOT A SCIENTIFIC RESULT\n\n'
            'This fixture changes one original narrowPeak negative-log10 p value and its published copy, '
            'then rebuilds transaction hashes and the capture seal. Three Java verifiers accept the '
            'consistent capture because they do not independently execute MACS statistical inference. '
            'The unchanged source spreadsheet is forensic evidence and is not numerically reconciled '
            'by VERIFY. Never interpret this fixture as a measured peak. Hashes do not authenticate '
            'execution, full host libraries or biological validity.\n',encoding='utf8')
        assert pin(JAR)==artifact and all(pin(ROOT/n)==v for n,v in sources.items())
        assert all(files(Path(p))==v for p,v in originals.items())
        save(out/'COMPLETED.json',dict(status='passed-resealed-peak-semantic-refusals-with-demonstrated-authentication-limit',
             artifact=artifact,source_pins=sources,semantic_refusals=19,runs=runs,originals_preserved=True,
             coordinated_forgery_can_pass=True,forgery_verifications=3,forged_fixture_is_scientific_result=False,
             forged_files=files(target)))
        print('19 resealed semantic corruptions refused; coordinated statistical forgery limitation demonstrated on3 JVMs')
    except Exception as e:save(out/'FAILURE.json',dict(status='FAIL2_TYPED_PEAK_INTEGRITY',error=str(e)));raise


if __name__=='__main__':main()
