"""Validate the complete native CADD consequence-selection component, retaining source refusals."""
import argparse,gzip,hashlib,json,zipfile
from pathlib import Path
from verify_cadd_linear import verify as verify_linear,read,pin,require
ROOT=Path(__file__).resolve().parents[2];Q='docs/literature/cadd-hierarchy-validation/QUALIFICATION.json'

def science(private):
    corpus=private/'hierarchy-corpus-1';cases=read(corpus/'CASES.json');fixtures=read(corpus/'FIXTURES.json')
    require(len(fixtures)==21 and len({r['id']for r in fixtures})==21,'Complete fixture population differs')
    require(all(pin(corpus/r['file'])==r['pin']for r in fixtures),'An entire hierarchy input changed')
    require(len(cases)==168 and len({r['id']for r in cases})==168,'Complete source option population differs')
    for fixture in fixtures:
        selected=[r for r in cases if r['fixture']==fixture['id']]
        require(len(selected)==8 and {r['mask']for r in selected}==set(range(8)),'A source flag combination was omitted')
        for row in selected:
            require(row['input']==fixture['file']and row['flags']==[flag for bit,flag in [(1,'--all'),(2,'--noheader'),(4,'--PHRED')]if row['mask']&bit],'Source input or flags changed')
    roots=[private/'hierarchy-original-1',private/'hierarchy-native-Windows-1/results',private/'hierarchy-native-Linux-1/results']
    require(all(len(read(r/'TERMINAL.json')['cases'])==168 for r in roots),'A scientific run is incomplete')
    success=refusal=0
    for case in cases:
        paths=[r/case['id']for r in roots];results=[read(p/'RESULT.json')for p in paths]
        completed=[r['exit_code']==0 for r in results]
        require(completed[0]==completed[1]==completed[2],'Original/native refusal behavior differs: '+case['id'])
        outputs=[pin(p/'stdout.tsv')for p in paths]
        require(outputs[0]==outputs[1]==outputs[2],'Any source/native output byte differs, including incomplete failure prefixes: '+case['id'])
        if completed[0]:
            success+=1;require(read(paths[1]/'statistics.json')==read(paths[2]/'statistics.json'),'Native host statistics differ')
        else:refusal+=1;require(all(r['exit_code']==2 for r in results[1:]),'Native refusal did not surface FAIL2')
    require((success,refusal)==(116,52),'Complete success/refusal population differs')
    chain=[]
    for host in ['Windows','Linux']:
        work=private/('native-chain-'+host+'-4');receipt=read(work/'COMPLETED.json')
        require(receipt['status']=='PASS_NATIVE_LINEAR_HIERARCHY_CALIBRATION_EVERY_PUBLISHED_FIELD'and receipt['published_annotation_rows']==30 and receipt['all_original_rows_and_features_retained']and not receipt['full_variant_annotation_performed'],'Complete native chain receipt differs')
        outputs={}
        for assembly,rows,features in [('GRCh37',18,1029),('GRCh38',12,1028)]:
            actual=work/'results'/assembly/'native-calibrated.tsv';expected=private.parent/'tool-audit-916/model-controls-2'/('whole-original-annotated-'+assembly+'.tsv.gz')
            data=lambda text:[line.split('\t')for line in text.splitlines()if line and not line.startswith('#')]
            a=data(actual.read_text(encoding='utf-8'));e=data(gzip.open(expected,'rt',encoding='utf-8').read())
            require(len(a)==len(e)==rows and a==e,'Any original full annotation/raw/PHRED field differs')
            scientific=read(work/'results'/assembly/'COMPLETED.json');require(scientific['rows']==rows and scientific['features']==features and scientific['groups']==6,'Original rows/features/groups were reduced')
            outputs[assembly]=pin(actual)
        chain.append(outputs)
    require(chain[0]==chain[1],'Native complete-chain bytes differ across hosts')
    classes=read(private/'hierarchy-comparison-1/PACKAGED-CLASS-COMPARISON.json')
    require(not classes['changed']and not classes['removed']and classes['previous_classes']==21521 and len(classes['added'])==6 and all(n.startswith('org/jlinalg/genetics/CaddConsequenceHierarchy')for n in classes['added']),'An unrelated existing packaged class changed')
    return dict(cases=168,source_success=116,source_refusals=52,whole_published_annotation_rows_per_host=30)

def verify(root,private=None):
    q=read(root/Q);require(q['status']=='PASS_NATIVE_CADD16_CONSEQUENCE_HIERARCHY'and q['new_family_credit']==q['new_source_reviews']==q['recovered_observations']==0 and q['hierarchy_component_qualified']and not q['raw_variant_prediction_qualified']and not q['whole_goal_complete'],'Hierarchy component scope/credit differs')
    verify_linear(root)
    for name,value in q['sources'].items():require(pin(root/name)==value,'Hierarchy implementation/test/guide/verifier differs: '+name)
    require(q['unit_tests']==dict(tests=12,failures=0,errors=0,skipped=0)and q['scientific_populations']==dict(cases=168,source_success=116,source_refusals=52,whole_published_annotation_rows_per_host=30),'Qualified scientific populations differ')
    if private is not None:
        for name,value in q['private_evidence'].items():require(pin(private/name)==value,'Retained hierarchy/source/refusal/chain evidence differs: '+name)
        for key in ['producing_jars','producing_driver_classes']:
            for name,value in q[key].items():require(pin(private/name)==value,'Actual scientific producer differs: '+name)
        with zipfile.ZipFile(private/q['final_producer'])as z:
            for name,value in q['final_producer_members'].items():require(hashlib.sha256(z.read(name)).hexdigest()==value,'Producing JAR member differs')
        science(private)
    return dict(status=q['status'],**q['scientific_populations'],unit_tests=12,private_evidence_checked=private is not None,hierarchy_component_qualified=True,raw_variant_prediction_qualified=False,new_family_credit=0,whole_goal_complete=False)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--private',type=Path);a=p.parse_args()
    try:print(json.dumps(verify(a.root.resolve(),a.private.resolve()if a.private else None),indent=2))
    except Exception as error:raise SystemExit('FAIL2: hierarchy component is not qualified; preserve diagnostics, correct the named source/input/capacity/integrity cause and retry NEW output: '+str(error))
