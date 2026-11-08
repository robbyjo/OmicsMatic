# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Record an unexecuted draft setup correction before its first runtime preparation; preserve the prior draft."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/palantir-runtime') and not out.exists();out.mkdir(parents=True);kit=ROOT/'src/main/resources/org/jlinalg/omics/palantir-workflow';file=kit/'profiles.json';raw=file.read_bytes();before=json.loads(raw);(out/'prior-unexecuted-profile.json').write_bytes(raw);changed={n for n,want in before['files'].items() if pin(kit/n)!=want};assert changed=={'setup/prepare.py'} and not(ROOT/'build/literature-review/palantir-runtime/workflow-setup-1').exists();updated=dict(before,files={n:pin(kit/n) for n in before['files']});save(file,updated);save(out/'COMPLETED.json',dict(status='PASS_UNEXECUTED_DRAFT_SETUP_IMPORT_AND_SOURCE_PIN_CORRECTION',changed_members=sorted(changed),before_profile=pin(out/'prior-unexecuted-profile.json'),after_profile=pin(file),old_runtime_or_analysis_receipt_refreshed=False,reason='Explicit safe script import path and complete setup/base-inventory source rechecks before any execution',whole_goal_complete=False));print('Corrected unexecuted draft; no runtime/analysis receipt changed')

if __name__=='__main__':main()
