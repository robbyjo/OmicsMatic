# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Bind the selected exact original source/data/function and prepared dependency profile; not workflow admission."""
import json
from setup_deseq2_reference import ROOT,pin,save


def main():
    base=ROOT/'build/literature-review/dndscv-runtime';source=base/'source-intake-1';receipt=json.loads((source/'SOURCE.json').read_bytes());assert receipt['commit']=='43c5e2f1beaaf3ac26df9c046b281ceb0edbfff7';assert all(pin(source/'source'/n)==v for n,v in receipt['source_members'].items());prepared=json.loads((base/'reference-setup-3/COMPLETED.json').read_bytes());assert prepared['status']=='installed-source-bound-original-dndscv-reference-only';profile=prepared['profile'];target=ROOT/'src/main/resources/org/jlinalg/omics/dndscv-workflow/SOURCE-PROFILE.json';assert not target.exists();target.parent.mkdir(parents=True,exist_ok=True);save(target,dict(schema_version=1,status='pinned-original-dndscv-profile-not-workflow-validation',profile='source-'+receipt['commit'],upstream='https://github.com/im3sanger/dndscv',commit=receipt['commit'],version='0.0.1.0',license='GPL-3',R_version=profile['R_version'],platform=profile['platform'],packages=profile['versions'],files=receipt['source_members'],guarded_functions=profile['guarded_functions'],paper_commit_identified=False,version_label_alone_identifies_source=False));print('Pinned original dndscv source/data and exact private R/dependency profile')


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable exact source/dependency checks')
    main()
