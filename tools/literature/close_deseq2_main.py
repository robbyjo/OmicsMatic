# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Freeze actual main routes and immutable parent evidence; catalog closure is separate."""
import json,zipfile
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import pack,unpack
BASE=ROOT/'build/literature-review/deseq2-runtime'

def read(p):return json.loads(p.read_bytes())
def main():
    folder=ROOT/'docs/literature/deseq2-tools-development';assert not folder.exists() or not any(folder.iterdir());folder.mkdir(exist_ok=True)
    matrix=read(BASE/'public-main-matrix-2/COMPLETED.json');assert matrix['status']=='passed-DESeq2-actual-main-original-and-native-workflows' and (matrix['original_R_fits'],matrix['native_main_analyses'],matrix['actual_main_capture_verifications'],matrix['finite_numeric_values'],matrix['nonfinite_kind_values'],matrix['maximum_relative_error'])==(4,6,12,35457,665,0)
    assert matrix['only_changed_packaged_entry']==['org/jlinalg/cli/JLinAlgCli.class'] and matrix['dependency_skips']==matrix['failed_capture_rejections']==1 and matrix['existing_output_refusals']==3
    sources=dict(matrix['source_pins']);parents={}
    for name in ['deseq2-inference-development','deseq2-normalization-development']:
        parent=ROOT/'docs/literature'/name;proof=read(parent/'VALIDATION.json');assert pin(parent/'controls.zip')=={k:proof['archive'][k] for k in ['bytes','sha256']}
        with zipfile.ZipFile(parent/'controls.zip') as z:assert len(z.namelist())==len(set(z.namelist()))==proof['archive_members'] and z.testzip() is None
        sources.update(proof['source_pins']);parents[name]=dict(validation=pin(parent/'VALIDATION.json'),archive=pin(parent/'controls.zip'))
    for name in ['tools/literature/close_deseq2_main.py','tools/literature/deseq2_dispatch_bridge.py','docs/literature/DESEQ2_TOOLS.md','examples/deseq2-tools/README.md']:
        sources[name]=pin(ROOT/name)
    assert all(pin(ROOT/n)==v for n,v in sources.items()) and pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==matrix['canonical_executable_preserved']
    files={}
    def add(p,name=None):
        assert not p.is_symlink();name=name or p.relative_to(ROOT).as_posix();raw=p.read_bytes();assert name not in files or files[name]==raw;files[name]=raw
    for name in sources:add(ROOT/name,'current-source/'+name)
    for sub in ['public-main-matrix-1','public-main-matrix-2','public-workflow-1']:
        for p in (BASE/sub).rglob('*'):
            if p.is_file():add(p)
    for sub in ['paired','shrinkage','lrt']:
        for p in (ROOT/('build/deseq2-tools-example-'+sub)).rglob('*'):
            if p.is_file():add(p)
    add(ROOT/'docs/literature/omics-2023-2026/deseq2-dispatch-shared-source-compatibility.json','dispatch-compatibility.json')
    warnings='''# DESeq2 main workflow: read before analysis

SKIP3 means skip dependent analysis when the named exact R4.6.1/DESeq2 1.52.0 runtime or optional package is unavailable or forbidden. Install only where permitted using the new-private-library installer; use forbid when installation is prohibited. Setup success is not analysis completion. No automatic installation or historical edition substitution occurs.

FAIL2 admits no result, including partial tables. Preserve attempts, inspect WARNINGS.md, analysis-status.json, manifest, worker/worker-report.json and worker stdout/stderr; correct the named input/model/source/process/capacity cause and use NEW output paths. Existing outputs are refused. TPM/log/residual values are not raw counts; estimated-count R_round and technical replicate collapsing require explicit requests.

VERIFY is structural and Java-only: it does not rerun NB fitting, shrinkage, iterative normalization or transformations, or authenticate coordinated fabricated worker/published values. Source guards do not authenticate installed DLLs; logical output limits are not RSS limits. Preserve source NA/NaN/Inf and biological replicate identities.

Current source/development JAR includes deseq2-tools and deseq2-normalize; older canonical/released JARs lack these routes. Scientific classes/resources and canonical executable remain unchanged. The first main harness attempt's Windows absolute VERIFY path failed on Linux; preserve that attempt. Corrected relative request paths passed without changing scientific code. Actual R fits are Windows-only; native/VERIFY also pass Linux.

Paper-specific editions, unresolved P082 role, P088 normalization-before-limma distinction and count-unit discrepancies remain explicit. No historical whole-package, whole-paper, biological-accuracy, release, push or deployment claim. Read DESEQ2_TOOLS.md. Cell Ranger remains excluded and the full goal remains incomplete.
'''
    (folder/'WARNINGS.md').write_text(warnings,encoding='utf8',newline='\n');layout=pack(folder/'controls.zip',files)
    result=dict(schema_version=1,status='passed-public-DESeq2-main-original-and-native-workflows',source_pins=sources,archive=pin(folder/'controls.zip'),warnings_document=pin(folder/'WARNINGS.md'),**layout,parents=parents,artifact=matrix['artifact'],canonical_executable_preserved=matrix['canonical_executable_preserved'],normal_Gradle_executableJar=True,root_dispatch_available=True,only_changed_packaged_entry=matrix['only_changed_packaged_entry'],original_R_fits=4,native_main_analyses=6,actual_main_capture_verifications=12,finite_numeric_values=35457,nonfinite_kind_values=665,maximum_relative_error=0,help_protocols=6,existing_output_refusals=3,dependency_skips=1,failed_capture_rejections=1,retained_first_harness_failure=True,scientific_sources_changed_after_parent_validation=False,Linux_R_fits_executed=False,independent_Java_NB_replay=False,installed_DLL_source_authentication=False,quantifier_import_pipeline_available=False,historical_whole_packages_executed=False,whole_paper_reproduced=False,biological_accuracy_certified=False,released=False,pushed=False,deployed=False,whole_goal_complete=False)
    save(folder/'VALIDATION.json',result);unpack(folder/'controls.zip',result);print(json.dumps(dict(status=result['status'],source_pins=len(sources),archive=result['archive'],logical_files=layout['logical_files'])))

if __name__=='__main__':main()
