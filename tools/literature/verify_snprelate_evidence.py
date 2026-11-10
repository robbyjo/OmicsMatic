# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Offline complete SNPRelate native reference replay, with live scientific-field mutation rejection."""
import argparse
import csv
import hashlib
import json
import os
import shutil
import subprocess
import traceback
import zipfile
from pathlib import Path,PurePosixPath
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'build/literature-review/snprelate-runtime'
HOME=ROOT/'docs/literature/snprelate-native-development'


def digest(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def pin(path):return digest(path.read_bytes())
def save(path,value):path.write_text(json.dumps(value,indent=2)+'\n',encoding='utf8',newline='\n')


def verify(home,out,java):
    proof=json.loads((home/'VALIDATION.json').read_bytes())
    assert proof['status']=='PASS_FROZEN_NATIVE_SNPRELATE146_SCALAR_QC_REFERENCE_EVIDENCE'
    assert not proof['new_catalog_credit'] and not proof['whole_goal_complete'] and not proof['project_analysis_admitted']
    assert not proof['original_software_author_data_or_article_prose_bundled']
    for name,value in proof['source_pins'].items():assert pin(ROOT/name)==value,('Changed source',name)
    assert pin(home/'controls.zip')==proof['archive']
    with zipfile.ZipFile(home/'controls.zip') as z:
        assert len(z.namelist())==len(set(z.namelist()))==len(proof['members']) and set(z.namelist())==set(proof['members'])
        total=0
        for item in z.infolist():
            name=item.filename;parts=PurePosixPath(name)
            assert not parts.is_absolute() and '..' not in parts.parts and not any(c in name for c in '\\:\0')
            assert name.startswith(('own-source/','summaries/','references/','native-consumers/'))
            assert not item.is_dir() and item.external_attr>>28!=0xa and item.file_size<=20000000
            total+=item.file_size;assert total<=200000000
            raw=z.read(item);assert digest(raw)==proof['members'][name]
            target=out/Path(*parts.parts);target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(raw)
    for name,value in proof['source_pins'].items():assert pin(out/'own-source'/name)==value
    artifacts={name:home/name for name in proof['artifacts']}
    for name,jar in artifacts.items():
        assert pin(jar)==proof['artifacts'][name]
        with zipfile.ZipFile(jar) as z:
            assert all(n.startswith(('META-INF/','org/jlinalg/genetics/')) for n in z.namelist())
            assert all(n.endswith('.class') or n.startswith('META-INF/') for n in z.namelist())
    summary=lambda name:json.loads((out/'summaries'/(name+'.json')).read_bytes())
    contracts=summary('contracts');assert contracts['test_methods']==32 and contracts['test_executions']==96 and len(contracts['consumers'])==4
    assert contracts['status'].startswith('PASS_') and not contracts['original_R_or_KING_analysis_executed']
    for compiler in [21,25]:
        build=summary('native'+str(compiler));assert build['status'].startswith('PASS_NATIVE_') and len(build['invocations'])==14
        assert sum(r['cases'] for r in build['invocations'])==256 and sum(r['complete_matrix_cells'] for r in build['invocations'])==29888
        assert sum(r['source_empty_selection_rejections'] for r in build['invocations'])==20
        assert sum(r['source_empty_selection_matrix_returns'] for r in build['invocations'])==20
    assert len(proof['references'])==7 and proof['original_declared_cases']==128
    records=[]
    def run(label,command,expected=0,needle=None):
        result=subprocess.run(command,cwd=ROOT,stdin=subprocess.DEVNULL,capture_output=True,timeout=180)
        (out/(label+'.stdout')).write_bytes(result.stdout);(out/(label+'.stderr')).write_bytes(result.stderr)
        save(out/(label+'.process.json'),dict(command=command,exit_code=result.returncode))
        assert (result.returncode==0) if expected==0 else (result.returncode!=0),(label,result.returncode,result.stderr[-2500:])
        if needle:assert needle.encode() in result.stderr,(label,result.stderr[-2500:])
        records.append(dict(case=label,expected_success=expected==0,actual_exit=result.returncode));return result
    matrix_cells=0;case_checks=0
    probes={name:jar for name,jar in artifacts.items() if name.startswith('snprelate-probe-')}
    assert len(probes)==2
    for ref in proof['references']:
        reference=out/'references'/ref['name'];metadata=json.loads((reference/'COMPLETED.json').read_bytes())
        assert metadata['status']=='PASS_ORIGINAL_SNPRELATE146_KING_MATRIX_REFERENCE_COLLECTION_ONLY'
        assert not metadata['project_analysis_admitted'] and metadata['preservation_checks']['all_unchanged']
        for ext,value in ref['input_pins'].items():assert pin(reference/'inputs'/('cohort.'+ext))==value
        for name,value in metadata['complete_original_files'].items():assert pin(reference/name)==value
        for name,jar in probes.items():
            label=ref['name']+'-'+name;report=out/(label+'.tsv')
            run(label,[str(java),'-Xmx1g','-cp',str(jar),'org.jlinalg.genetics.SnprelateReferenceProbe',str(reference/'inputs/cohort'),str(reference/'original-results'),str(report)])
            with report.open(encoding='utf8',newline='') as stream:rows=list(csv.DictReader(stream,delimiter='\t'))
            assert len(rows)==ref['cases'];case_checks+=len(rows);matrix_cells+=sum(int(r['matrix_cells']) for r in rows)
    # Deliberate changes happen after archive verification, on separate copies;
    # the native calculations must reject them even without a checksum gate.
    baseline=out/'references'/proof['references'][0]['name'];containers=sorted((baseline/'original-results').glob('*/original-case-dispositions.tsv'))
    assert containers
    relative=containers[0].parent.relative_to(baseline/'original-results')
    mutations=[('coefficient',relative/'all-singletons/original-complete-matrices.tsv','kinship_hex','0x1p-2','Complete original KING coefficient differs'),
        ('genotype',relative/'original-genotypes.tsv','1','99','Original GDS genotype transport differs'),
        ('statistics',relative/'original-variant-statistics.tsv','AlleleFreq_hex','0x1p+1','Allele frequency differs'),
        ('selection',relative/'all-singletons/original-selected-variants.tsv','input_row','0','Source selected SNP order/identity differs'),
        ('identity',relative/'original-sample-map.tsv','FID','changed-FID','Original FAM identity/order changed')]
    for kind,relative_file,column,value,needle in mutations:
        altered=out/'deliberate-mutations'/kind;shutil.copytree(baseline/'original-results',altered)
        target=altered/relative_file
        with target.open(encoding='utf8',newline='') as stream:rows=list(csv.reader(stream,delimiter='\t'))
        rows[1][rows[0].index(column)]=value
        with target.open('w',encoding='utf8',newline='') as stream:csv.writer(stream,delimiter='\t',lineterminator='\n').writerows(rows)
        for name,jar in probes.items():
            label='reject-'+kind+'-'+name
            run(label,[str(java),'-Xmx1g','-cp',str(jar),'org.jlinalg.genetics.SnprelateReferenceProbe',str(baseline/'inputs/cohort'),str(altered),str(out/(label+'.tsv'))],2,needle)
    for compiler in [21,25]:
        jar=artifacts['snprelate-native-javac'+str(compiler)+'.jar']
        result=run('consumer-'+str(compiler),[str(java),'-cp',os.pathsep.join(map(str,[out/'native-consumers'/str(compiler),jar])),'SnprelateProfileExample'])
        assert b'BETWEEN_OR_SINGLETONS kinship=NaN raw=-Infinity' in result.stdout
        assert b'WARNING: Uninformative chosen denominator' in result.stdout
    assert case_checks==256 and matrix_cells==29888 and len(records)==26
    return dict(status='PASS_OFFLINE_NATIVE_SNPRELATE_COMPLETE_FIELD_REPLAY_AND_LIVE_MUTATION_REJECTION',records=records,
        case_checks=case_checks,complete_matrix_cells=matrix_cells,live_scientific_mutations_rejected=10,
        original_R_or_KING_analysis_executed=False,project_analysis_admitted=False,new_catalog_credit=False,whole_goal_complete=False)


def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',type=Path,default=HOME)
    p.add_argument('--out',type=Path,required=True);p.add_argument('--java',type=Path)
    a=p.parse_args();out=a.out.resolve();assert not out.exists() and out.is_relative_to(BASE);out.mkdir(parents=True)
    state=dict(status='FAIL2_SNPRELATE_NATIVE_EVIDENCE',project_analysis_admitted=False,new_catalog_credit=False,whole_goal_complete=False);code=2
    try:
        java=a.java or shutil.which('java')
        if not java:
            state.update(status='SKIP3_JAVA_EVIDENCE_REPLAY_UNAVAILABLE',required_action='Supply permitted Java17+ and retry into a new directory. R is not needed for saved replay; do not claim validation passed.');code=3
        else:state=verify(a.home.resolve(),out,java);code=0
    except Exception as error:
        state['error']=repr(error);(out/'error.traceback').write_text(traceback.format_exc(),encoding='utf8')
    save(out/'COMPLETED.json',state);print(state['status'],state.get('error','')[:2600],state.get('required_action',''),flush=True);return code


if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable native complete-field and scientific mutation verification')
    raise SystemExit(main())
