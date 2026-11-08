# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Add focused current-runner checks; retain all previous general browser assertions."""
from setup_deseq2_reference import ROOT

def generated():
    tools=ROOT/'tools/literature';text=(tools/'check_cellphonedb_review_ui.cjs').read_text(encoding='utf8').replace('CellPhoneDbWorkflowCli','CellPhoneDbWorkflowCliV2').replace('CellPhoneDbSetupCli','CellPhoneDbSetupCliV2').replace('CELLPHONEDB_WORKFLOWS.md','CELLPHONEDB_HASH_SEED.md');marker="assert.equal(errors.length,0,errors.join('\\n'));";assert text.count(marker)==1;text=text.replace(marker,marker+"\n for(const term of ['revision2','PYTHONHASHSEED','python-startup.json','never relabel','-B -P -s'])assert(w.includes(term.toLowerCase()),term);checks.push('current warnings require effective startup evidence and retain the first-generation defect without relabelling runs');\n const current=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/omics-2023-2026/tool-inventory.json'))).find(t=>t.id==='cellphonedb');assert.equal(current.runner_revision,2);assert.equal(current.python_hash_seed_effective,true);assert.equal(current.development_cli_class,'org.jlinalg.cli.CellPhoneDbWorkflowCliV2');assert.equal(current.runtime_setup_cli_class,'org.jlinalg.cli.CellPhoneDbSetupCliV2');const proof=JSON.parse(fs.readFileSync(path.join(root,'docs/literature/cellphonedb-hash-seed-development/VALIDATION.json')));assert.equal(current.development_candidate,proof.artifact.path);assert.equal(current.development_candidate_sha256,proof.artifact.sha256);checks.push('catalog points to the exact separately validated revision2 artifact and both V2 entry points');")
    wrapper=(tools/'validate_cellphonedb_catalog_ui.py').read_text(encoding='utf8').replace('check_cellphonedb_review_ui.cjs','check_cellphonedb_effective_review_ui.cjs')
    return {tools/'check_cellphonedb_effective_review_ui.cjs':text.encode(),tools/'validate_cellphonedb_effective_ui.py':wrapper.encode()}

def verify():
    for path,raw in generated().items():assert path.read_bytes()==raw,'Current focused UI derivation changed: '+str(path)

if __name__=='__main__':
    for path,raw in generated().items():
        if path.exists():assert path.read_bytes()==raw,'Existing UI checks protected'
        else:path.write_bytes(raw)
    verify();print('Prepared unchanged299 general assertions and8 focused revision2 checks')
