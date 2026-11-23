from pathlib import Path
import hashlib,json,shutil,subprocess,sys
from handover_inventory_20261122_111 import save,pin
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/mrpresso-1';D=B/'small-unit-verifier-probe-1';D.mkdir()
original=B/'packaged-smoke-1';table=D/'associations.tsv';lines=(original/'associations.tsv').read_text().splitlines();header=lines[0].split('\t');output=[lines[0]]
for line in lines[1:]:
    values=line.split('\t')
    for name in ('Y','SY'):values[header.index(name)]=format(float(values[header.index(name)])*1e-20,'.17g')
    output.append('\t'.join(values))
table.write_text('\n'.join(output)+'\n',encoding='utf-8');request=json.loads((original/'request.json').read_bytes());request['out']=str(D/'analysis');request['inputs']['associations']=str(table);save(D/'request.json',request)
java='C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot/bin/java.exe';jar=B/'normal-build-1/project-build/cli/omicsmatic-0.4.0.jar'
def run(label,args):
    result=subprocess.run([java,'-jar',str(jar),'mrpresso-tools',*args],cwd=T,capture_output=True,timeout=60)
    (D/(label+'.stdout')).write_bytes(result.stdout);(D/(label+'.stderr')).write_bytes(result.stderr);save(D/(label+'.RESULT.json'),dict(exit_code=result.returncode));return result.returncode
assert run('run-small',['--request',str(D/'request.json')])==0
assert run('verify-valid',['--verify',str(D/'analysis')])==0
bad=D/'resealed-false-small-coefficients';shutil.copytree(D/'analysis',bad)
path=bad/'native-result.json';value=json.loads(path.read_bytes())
for name in ('raw_mr','corrected_mr'):
    for key in ('beta','standard_error'):
        for cell in value[name][key]:
            assert cell['kind']=='FINITE' and abs(cell['value'])<1e-18
            cell['value']*=1_000_000
save(path,value)
manifest=bad/'capture-files.json';saved=json.loads(manifest.read_bytes())
for name in saved['files']:saved['files'][name]=pin(bad/name)
save(manifest,saved);marker=json.loads((bad/'COMPLETED.json').read_bytes());marker['seal_identity']=pin(manifest);save(bad/'COMPLETED.json',marker)
code=run('verify-contradiction',['--verify',str(bad)])
save(D/'FINDING.json',dict(status='PRELIMINARY_VERIFIER_FALSE_ACCEPTANCE_CONFIRMED' if code==0 else 'PRELIMINARY_VERIFIER_REFUSED',
    producer=pin(jar),incorrect_coefficient_and_standard_error_multiplier=1_000_000,source_inputs_rng_counts_and_test_statistics_changed=False,
    complete_source_calculation_correct=True,issue='Fixed absolute finite-cell tolerance can admit arbitrarily large relative coefficient/SE errors when outcome units are tiny.',
    required_action='Replace the fixed absolute floor by relative/ULP comparisons and exact numeric-zero handling, add this regression and rerun qualification on a NEW producer.',
    full_workflow_qualification_allowed=False,new_family_credit=0,new_reviews=0,whole_goal_complete=False))
print(json.dumps(dict(status='CONFIRMED_SMALL_UNIT_VERIFIER_CONTRADICTION',exit_code=code)),flush=True)
assert code==0,'Inspect observed refusal before changing verifier policy'
