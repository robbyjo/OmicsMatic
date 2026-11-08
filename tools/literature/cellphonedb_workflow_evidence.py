# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify exact sealed source/archive bytes and checkpoint scope; this does not rerun original methods or refit a scientific model."""
import hashlib,json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin


def main():
    proof=json.loads((ROOT/'docs/literature/cellphonedb-workflow-development/VALIDATION.json').read_bytes());assert proof['status']=='passed-scoped-original-CellPhoneDB-execution-and-native-audits-development-only' and proof['implementation_credit'] is False and proof['catalog_applied'] is False and proof['whole_goal_complete'] is False and proof['pickle_deserialized'] is False;assert all(pin(ROOT/n)==want for n,want in proof['source_pins'].items());archive=ROOT/proof['archive']['path'];assert pin(archive)=={k:proof['archive'][k] for k in ['bytes','sha256']}
    with zipfile.ZipFile(archive) as z:
        assert len(z.namelist())==len(set(z.namelist())) and set(z.namelist())==set(proof['archive_members']) and z.testzip() is None
        for n,want in proof['archive_members'].items():assert not Path(n).is_absolute() and '..' not in Path(n).parts;raw=z.read(n);assert dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())==want
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==proof['canonical_executable_unchanged'];artifact=ROOT/proof['artifact']['path']
    if artifact.exists():assert pin(artifact)=={k:proof['artifact'][k] for k in ['bytes','sha256']}
    assert proof['controls']['paired_cases']==19 and proof['controls']['deterministic_serial_complete_bytes_and_rng']==17 and len(proof['controls']['native_jvms'])==3 and all(p['cases']==19 for p in proof['controls']['native_jvms']) and proof['controls']['coherent_corruptions']==7;print('PASS_VERIFIED_CELLPHONEDB_WORKFLOW_DEVELOPMENT_BYTES_AND_SCOPE_ONLY; catalog/whole goal unfinished')


if __name__=='__main__':main()
