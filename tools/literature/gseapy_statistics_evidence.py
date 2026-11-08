# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Verify native GSEApy statistics, original source/runtime references and unapplied primary-role drafts."""
import ast,hashlib,json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from setup_deseq2_reference import ROOT,pin
from signac_scan_evidence import unpack,byte_pin

STATUS='passed-native-four-edition-GSEApy-statistics-and-six-primary-role-drafts-only'
BASE='build/literature-review/gseapy-runtime/'

def verify(emit=True,folder=None):
    folder=ROOT/'docs/literature/gseapy-statistics-development' if folder is None else Path(folder);p=json.loads((folder/'VALIDATION.json').read_bytes())
    assert p['status']==STATUS and not any(p[k] for k in ['implementation_credit','full_gseapy_adapter_admitted','whole_package_implemented','whole_paper_reproduced','whole_goal_complete'])
    assert pin(folder/'controls.zip')==p['archive'];files=unpack(folder/'controls.zip',p)
    for name,want in p['source_pins'].items():assert pin(ROOT/name)==byte_pin(files['current-source/'+name])==want,name
    for name,want in p['parent_proofs'].items():assert pin(ROOT/name)==want,name
    get=lambda n:json.loads(files[BASE+n]);source=get('source-intake-1/SOURCE.json');assert source['status']=='passed-four-paper-reported-official-GSEApy-source-wheels' and set(source['versions'])=={'1.0.4','1.1.2','1.1.3','1.1.8'}
    asts={}
    for version,profile in source['versions'].items():
        wheel=files[BASE+'source-intake-1/'+version+'/'+profile['wheel']['filename']];assert byte_pin(wheel)=={k:profile['wheel'][k] for k in ['bytes','sha256']}
        for name,want in profile['members'].items():assert byte_pin(files[BASE+'source-intake-1/'+version+'/wheel-source/'+name])==want,name
        raw=files[BASE+'source-intake-1/'+version+'/wheel-source/gseapy/stats.py'];asts[version]={f.name:hashlib.sha256(ast.dump(f,include_attributes=False).encode()).hexdigest() for f in ast.parse(raw).body if isinstance(f,ast.FunctionDef)}
        resource='src/main/resources/org/jlinalg/omics/gseapy-statistics/original/'+version+'/gseapy/stats.py';assert files['current-source/'+resource]==raw
    assert asts['1.1.2']==asts['1.1.3']==asts['1.1.8'] and asts['1.0.4']['calc_pvalues']!=asts['1.1.2']['calc_pvalues']
    built=get('statistics-build-3/COMPLETED.json');assert built['status']=='passed-compiled-native-GSEApy-stats-component-only' and built['artifact']==p['artifact'] and built['parent_artifact']==p['parent_artifact'] and built['unchanged_parent_entries']==p['unchanged_parent_entries']
    assert get('statistics-build-3/compile.json')['exit_code']==0 and '--release' in get('statistics-build-3/compile.json')['command'] and '-Werror' in get('statistics-build-3/compile.json')['command']
    reference=get('statistics-reference-2/COMPLETED.json');assert reference['status']=='passed-original-four-edition-GSEApy-stats-reference-only' and reference['complete_runtime_unchanged']
    assert (reference['ora_cases'],reference['tail_cases'],reference['correction_cases'],reference['independent_exact_small_tails'])==(432,27608,648,27600) and reference['maximum_scipy_vs_exact_relative_error']<=5e-14
    fixture=files[BASE+'statistics-reference-2/cases.json'];assert byte_pin(fixture)==reference['cases'];cases=json.loads(fixture);assert (len(cases['cases']),len(cases['tails']),len(cases['corrections']))==(432,27608,648)
    assert files[BASE+'statistics-reference-1/cases.json']==fixture,'Prior source reference values changed'
    workflow=json.loads((ROOT/'docs/literature/scfates-workflow-development/VALIDATION.json').read_bytes());baseline=BASE.replace('gseapy-runtime/','scfates-runtime/')+'workflow-setup-3/base-after.json'
    assert reference['runtime_baseline']==workflow['logical_file_pins'][baseline]==pin(ROOT/baseline)
    assert get('statistics-reference-2/runtime-before.json')==get('statistics-reference-2/runtime-after.json')==json.loads((ROOT/baseline).read_bytes())
    assert reference['runtime_inventory_source']==pin(ROOT/'src/main/resources/org/jlinalg/omics/scfates-workflow/setup/inventory.py') and reference['producer']==pin(ROOT/'tools/literature/gseapy_statistics_reference.py')
    assert reference['source_profile']==byte_pin(files[BASE+'source-intake-1/SOURCE.json'])
    for version,want in reference['source_stats_pins'].items():assert want==source['versions'][version]['members']['gseapy/stats.py']
    native=get('statistics-native-3/COMPLETED.json');assert native['status']=='passed-three-JVM-four-edition-native-GSEApy-stats-component-only' and native['artifact']==p['artifact'] and len(native['runs'])==3
    assert {r['jvm'] for r in native['runs']}=={'windows21','windows25','linux21'}
    for run in native['runs']:
        report=run['report'];assert report==get('statistics-native-3/'+run['jvm']+'/audit.json') and get('statistics-native-3/'+run['jvm']+'/process.json')['exit_code']==0
        assert report['status']=='passed-complete-four-edition-native-GSEApy-stats-component-only' and (report['ora_cases'],report['tail_cases'],report['correction_cases'],report['numeric_states'],report['invalid_inputs_rejected'])==(432,27608,648,163461,13)
        assert not report['implementation_credit'] and not report['high_level_adapter_admitted'] and report['nan_states']>0 and report['exact_zero_states']>0
    for prior in ['statistics-native-1','statistics-native-2']:assert get(prior+'/COMPLETED.json')['status']=='FAIL2_GSEAPY_NATIVE_STATISTICS_VALIDATION'
    example=get('statistics-example-1/COMPLETED.json');assert example['status']=='passed-exact-guide-native-GSEApy-example-on-three-JVMs' and len(example['runs'])==3 and example['artifact']==p['artifact']
    for report in [built,native,example]:
        for name,want in report['source_pins'].items():assert pin(ROOT/name)==want,name
    drafts=get('primary-role-drafts-1/COMPLETED.json');assert drafts['status']=='passed-250-XML-DOI-and-six-GSEApy-primary-role-drafts' and drafts['source_records']==250 and drafts['papers']==['P009','P053','P062','P079','P098','P161'] and not drafts['catalog_changed']
    assert drafts['papers_pin']==pin(ROOT/'docs/literature/omics-2023-2026/papers.json') and drafts['reviews_pin']==pin(ROOT/'tools/literature/reviewed-uses.json')
    draft=files[BASE+'primary-role-drafts-1/gseapy-use-drafts.json'];assert byte_pin(draft)==drafts['draft'];review=json.loads(draft);assert len(review['reviews'])==6 and not review['reviews_applied'] and not review['registry_applied']
    p98=next(r for r in review['reviews'] if r['paper_id']=='P098');assert 'two-tailed' in p98['primary_evidence'][0]['text'] and 'one-sided' in p98['implementation_scope_note'] and 'unresolved' in p98['implementation_scope_note']
    summary=json.loads((ROOT/'docs/literature/omics-2023-2026/summary.json').read_bytes());counts=summary['implementation_status_counts'];assert (counts['native-subset-validated'],counts['upstream-adapter-validated'],counts['missing'])==(17,70,254)
    assert next(r for r in json.loads((ROOT/'docs/literature/omics-2023-2026/tool-inventory.json').read_bytes()) if r['id']=='gseapy')['implementation_status']=='missing'
    assert pin(ROOT/'build/cli/omicsmatic-0.3.7.jar')==p['canonical_executable_unchanged']
    if emit:print('Verified native GSEApy1.0.4/1.1.2/1.1.3/1.1.8 local stats:432 ORA cases,27608 tails,648 corrections,27600 exact controls;163461 numeric states/JVM across Windows21/25 and Linux21. Six roles remain unapplied; no GSEApy catalog credit.')
    return p

if __name__=='__main__':
    if not __debug__:raise RuntimeError('Never disable source/numerical evidence gates')
    verify()
