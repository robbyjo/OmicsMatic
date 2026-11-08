# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Export the complete synthetic SMR controls and an editable Linux request bound to a privately prepared runtime."""
import argparse,json
from pathlib import Path
from setup_deseq2_reference import ROOT,pin,save
from signac_scan_evidence import unpack,byte_pin
from setup_smr_cmake import linux
from probe_smr_workflow import analysis


def local(path):return linux(path) if path.drive else str(path.resolve())


def request(version,inputs,out,cases):
    return dict(schema_version=1,mode='ANALYZE',profile='original-SMR-Linux-x86_64-1.3.1-1.4.3',version=version,gwas=local(inputs/'gwas.ma'),qtl=dict(kind='qfile',file=local(inputs/'qtl.qfile'),storage='sparse',precision='source-binary32',preparation_policy='source-version-with-ledger'),reference=dict(kind='plink-bed',prefix=local(inputs/'reference'),assembly_label='synthetic-coordinate-system',population_label='synthetic512-not-a-human-reference',coordinate_policy='require-equal',missing_genotypes='source-mean-impute-LD'),probe_selection=dict(kind='all'),summary_assembly_label='synthetic-coordinate-system',exposure_units='synthetic-exposure-effect',outcome_units='synthetic-outcome-effect',allele_policy='source-exact-or-swap',analyses=cases,out=local(out),threads=1,timeout_millis=60000)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--runtime-home',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.resolve();assert out.is_relative_to(ROOT/'build/literature-review/smr-runtime') and not out.exists();home=a.runtime_home.resolve();prepared=json.loads((home/'COMPLETED.json').read_bytes());assert prepared['status']=='PASS_PREPARED_EXACT_ORIGINAL_SMR_RUNTIME_ONLY';version=prepared['version'];profile=json.loads((ROOT/'src/main/resources/org/jlinalg/omics/smr-workflow/SOURCE-PROFILE.json').read_bytes())['editions'][version];assert all(pin(home/'runtime'/n)==v for n,v in profile['runtime_files'].items()) and all(pin(home/'source'/n)==v for n,v in profile['source_files'].items());folder=ROOT/'docs/literature/smr-statistics-development';proof=json.loads((folder/'VALIDATION.json').read_bytes());assert pin(folder/'controls.zip')==proof['archive'];files=unpack(folder/'controls.zip',proof);out.mkdir(parents=True);inputs=out/'inputs';inputs.mkdir();origin='build/literature-review/smr-runtime/synthetic-controls-1/'
    for name in ['reference.bed','reference.bim','reference.fam','gwas.ma','qtl.qfile','GENERATED.json']:(inputs/name).write_bytes(files[origin+name])
    q=request(version,inputs,out/'result',[analysis(version,'single-new'),analysis(version,'multi',kind='multi')]);q['source']=prepared['source'];q['runtime']=prepared['runtime'];save(out/'request.json',q);save(out/'COMPLETED.json',dict(status='prepared-synthetic-SMR-workflow-demonstration-not-analysis',version=version,request=pin(out/'request.json'),inputs={n:byte_pin(files[origin+n]) for n in ['reference.bed','reference.bim','reference.fam','gwas.ma','qtl.qfile','GENERATED.json']},reference_sample_count=512,variants=32,probes=5,QTL_rows=90,original_runtime=pin(home/'COMPLETED.json'),source_pins={Path(__file__).resolve().relative_to(ROOT).as_posix():pin(Path(__file__)), 'tools/literature/probe_smr_workflow.py':pin(ROOT/'tools/literature/probe_smr_workflow.py')},model_or_paper_result_admitted=False,whole_goal_complete=False));(out/'WARNINGS.md').write_text('# Synthetic SMR demonstration only\n\n512 synthetic samples,32 variants,5 probes and90 QTL observations. No human reference, paper settings, ancestry or biological calibration is inferred. Read SMR_WORKFLOWS.md. Execute Linux x86_64 Java with request.json; require exit0 and result_admitted=true in the NEW result directory before using numerical results. Missing/forbidden dependencies mean SKIP3 and dependent analysis skipped; FAIL2 admits no atomic-batch result.\n',encoding='utf8',newline='\n');print('Prepared complete synthetic request:',local(out/'request.json'))


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/runtime/input checks')
    main()
