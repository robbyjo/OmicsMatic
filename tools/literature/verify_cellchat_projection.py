# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Read-only CellChat projection evidence verification; no R/Java execution or install."""
import argparse,csv,hashlib,io,json,math,zipfile
from pathlib import Path,PurePosixPath
ROOT=Path(__file__).resolve().parents[2]

def bytepin(raw):return dict(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
def table(raw):
    rows=list(csv.reader(io.StringIO(raw.decode('utf8')),delimiter='\t'));assert len(rows)>=2
    columns=rows[0];assert columns[0]=='gene_id' and len(columns)==len(set(columns))
    assert all(len(r)==len(columns) for r in rows);names=[r[0] for r in rows[1:]];assert len(names)==len(set(names))
    values=[[float(x) for x in r[1:]] for r in rows[1:]];assert all(math.isfinite(x) for r in values for x in r)
    return columns,names,values

def verify(home,check_current=False):
    proof=json.loads((home/'VALIDATION.json').read_bytes());assert proof['status']=='PASS_NATIVE_CELLCHAT_PPI_PROJECTION_DEVELOPMENT_ONLY' and not proof['new_catalog_credit'] and not proof['whole_goal_complete']
    assert (proof['new_scoped_integrations'],proof['including_existing_adapters'],proof['missing'])==(93,96,248)
    for name,pin in proof['development_jars'].items():assert bytepin((home/name).read_bytes())==pin
    raw=(home/'controls.zip').read_bytes();assert bytepin(raw)==proof['archive']
    if check_current:
        for name,pin in {**proof['source_pins'],**proof['catalog_unchanged']}.items():assert bytepin((ROOT/name).read_bytes())==pin,name
        assert bytepin((ROOT/'build/cli/omicsmatic-0.3.7.jar').read_bytes())==proof['canonical_executable_unchanged']
    checked=0;maximum=0
    with zipfile.ZipFile(io.BytesIO(raw)) as z:
        names=z.namelist();assert len(names)==len(set(names)) and set(names)==set(proof['members'])
        assert len(names)<30000 and sum(i.file_size for i in z.infolist())<1073741824
        for name in names:
            path=PurePosixPath(name);assert not path.is_absolute() and '..' not in path.parts and '\\' not in name
            assert bytepin(z.read(name))==proof['members'][name],name
        for name,pin in proof['source_pins'].items():assert bytepin(z.read('source/'+name))==pin
        cases=json.loads(z.read('evidence/projection-reference-4/cases.json'));assert len(cases)==50
        assert json.loads(z.read('evidence/projection-reference-4/R-RESULT.json'))['status']=='PASS_ORIGINAL_CELLCHAT_1_1_3_PROJECTDATA_CONTROLS_ONLY'
        refusals=json.loads(z.read('evidence/projection-reference-4/original-refusals.json'));assert set(refusals)=={'alpha0','alpha1','zero_row','zero_column'} and all(v!='Unexpected success' for v in refusals.values())
        for label in proof['native_suites']:
            report=json.loads(z.read('evidence/'+label+'/COMPLETED.json'))
            assert report['status']=='PASS_NATIVE_CELLCHAT_PROJECTION_CONTROLS_ONLY' and (report['protocols'],report['reference_cases'],report['expected_analysis_refusals'],report['tamper_refusals'],report['api_checks'])==(126,50,19,6,19)
            assert len(report['records'])==126 and all(r['exit_code']==r['expected_exit'] for r in report['records'])
            for case in cases:
                prefix='evidence/'+label+'/runs/'+case['id']+'/'
                manifest=json.loads(z.read(prefix+'COMPLETED.json'));assert manifest['result_admitted'] is True
                files={'request.json','expression.tsv','ppi.tsv','projected.tsv','projection.json','WARNINGS.md'};assert set(manifest['files'])==files
                assert {n[len(prefix):] for n in names if n.startswith(prefix)}==files|{'COMPLETED.json'}
                for name,pin in manifest['files'].items():assert bytepin(z.read(prefix+name))==pin
                ref='evidence/projection-reference-4/'+case['id']+'/'
                for name in ['expression.tsv','ppi.tsv']:assert z.read(prefix+name)==z.read(ref+name)
                expected=table(z.read(ref+'original-projected.tsv'));actual=table(z.read(prefix+'projected.tsv'));assert actual[:2]==expected[:2]
                error=max((abs(x-y)/max(1,abs(y)) for ar,er in zip(actual[2],expected[2]) for x,y in zip(ar,er)),default=0);assert error<=(5e-9 if case['alpha']>.999 else 2e-12)
                maximum=max(maximum,error);checked+=1
                diagnostic=json.loads(z.read(prefix+'projection.json'));assert 0<=diagnostic['relative_residual']<=diagnostic['residual_tolerance']
                for name,key in [('expression.tsv','expression_pin'),('ppi.tsv','ppi_pin'),('projected.tsv','projected_pin')]:assert bytepin(z.read(prefix+name))==diagnostic[key]
                assert diagnostic['warnings'] and b'raw.use=FALSE' in z.read(prefix+'WARNINGS.md')
                expression=table(z.read(prefix+'expression.tsv'));network=set(table(z.read(prefix+'ppi.tsv'))[1])
                assert expression[:2]==actual[:2]
                for i,gene in enumerate(expression[1]):
                    if gene not in network:assert expression[2][i]==actual[2][i]
        skipped=json.loads(z.read('evidence/edition-forbidden-control-1/COMPLETED.json'));assert skipped['status']=='SKIP3_ORIGINAL_CELLCHAT_EDITION_UNAVAILABLE' and not skipped['analysis_admitted']
        setup=json.loads(z.read('evidence/edition-1.1.3-setup-1/COMPLETED.json'));assert setup['status']=='INSTALLED_ORIGINAL_CELLCHAT_EDITION_ONLY' and setup['preexisting_libraries_unchanged'] and setup['source_code_data_unchanged'] and not setup['analysis_admitted']
    assert checked==proof['original_numerical_comparisons']==300 and maximum==proof['maximum_scaled_error']
    print(json.dumps(dict(status='PASS_READ_ONLY_CELLCHAT_PROJECTION_EVIDENCE',numerical_comparisons=checked,synthetic_families=50,maximum_scaled_error=maximum,new_catalog_credit=False,whole_goal_complete=False),indent=2))
    return proof

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable evidence verification')
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--home',type=Path,default=ROOT/'docs/literature/cellchat-projection-development');p.add_argument('--check-current-source',action='store_true');a=p.parse_args()
    try:verify(a.home,a.check_current_source)
    except Exception as error:raise SystemExit('FAIL2 CELLCHAT PROJECTION EVIDENCE: '+str(error)+'\nRequired action: preserve the evidence, correct the named missing/mismatching source or file, and repeat verification. No analysis admission or tool promotion.')
