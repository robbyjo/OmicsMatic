# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Prove the final setup exporter/kit addition preserves every previously executed Hail analysis JAR member exactly."""
import argparse,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--before',type=Path,required=True);p.add_argument('--after',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/hail-runtime') and not out.exists();out.mkdir(parents=True);before=json.loads((a.before/'COMPLETED.json').read_bytes());after=json.loads((a.after/'COMPLETED.json').read_bytes());first=ROOT/before['artifact']['path'];second=ROOT/after['artifact']['path'];assert pin(first)=={k:before['artifact'][k] for k in ['bytes','sha256']} and pin(second)=={k:after['artifact'][k] for k in ['bytes','sha256']};expected={'org/jlinalg/cli/HailWorkflowSetupCli.class','org/jlinalg/omics/hail-workflow/setup/prepare.py','org/jlinalg/omics/hail-workflow/setup/inventory.py','org/jlinalg/omics/hail-workflow/setup/dependency-locks.json'}
    with zipfile.ZipFile(first) as old,zipfile.ZipFile(second) as new:
        assert len(old.namelist())==len(set(old.namelist())) and len(new.namelist())==len(set(new.namelist()));assert set(new.namelist())-set(old.namelist())==expected and set(old.namelist())<=set(new.namelist());assert all(old.read(name)==new.read(name) for name in old.namelist()),'An executed analysis class/resource/library changed';count=len(old.namelist())
    for name,value in before['source_pins'].items():assert pin(ROOT/name)==value and after['source_pins'][name]==value,name
    save(out/'COMPLETED.json',dict(status='PASS_ADDITIVE_HAIL_SETUP_ONLY_ALL_ANALYSIS_MEMBER_BYTES_UNCHANGED',before=before['artifact'],after=after['artifact'],unchanged_members=count,added_entries=sorted(expected),scientific_scope='Complete analysis classes, Python workers, original wheel profiles/licenses and inherited library entries are byte-identical. This proves the additive setup delta; it does not substitute for the separately retained original analysis, independent output audits or setup execution checks.',source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__))},implementation_credit=False,whole_goal_complete=False));print('Verified',count,'unchanged analysis/library members; exactly4 setup-only additions')

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable complete additive member/source checks')
    main()
