#!/usr/bin/env python3
"""Run packaged APBS/grid workflows against original solver references,never mocks.

Requires an admitted Windows home and a NEW report directory. Reference runs must
come from an independently retained untouched original APBS invocation. This is a
development solver subset audit,not a source-paper reconstruction or release gate.
"""
from pathlib import Path
import argparse,array,datetime as dt,hashlib,json,math,re,subprocess,sys

ROOT=Path(__file__).resolve().parents[2]

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')

def grid(path):
    text=path.read_text(encoding='ascii');match=re.search(r'items\s+(\d+)\s+data follows\s*\n',text);assert match,path
    size=int(match[1]);tokens=text[match.end():].split();assert len(tokens)>=size
    values=array.array('d',(float(x) for x in tokens[:size]));assert len(values)==size and all(map(math.isfinite,values)),path
    if sys.byteorder!='little':values.byteswap()
    return dict(points=size,float64_le_sha256=hashlib.sha256(values.tobytes()).hexdigest(),minimum=min(values),maximum=max(values)),values

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java',default='java');parser.add_argument('--jar',required=True,type=Path);parser.add_argument('--home',required=True,type=Path);parser.add_argument('--reference-runs',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args()
    out=args.out.absolute();out.mkdir(parents=True,exist_ok=False);jar=args.jar.absolute();home=args.home.absolute();refs=args.reference_runs.absolute();original=json.loads((refs/'verification.json').read_text());assert original['original_program_sha256']=='118579e48bb2801eda53777e44f12834ba8c8299d293e0fe2c2b46e32cebfb3d'
    cases={case['case']:case for case in original['cases']};runs=[];energies={};vectors={};record=dict(status='running',started_utc=dt.datetime.now(dt.timezone.utc).isoformat(),candidate_sha256=sha(jar),verifier_sha256=sha(__file__),original_reference_sha256=sha(refs/'verification.json'),runs=runs)
    def command(name,subcommand,options,expected,status):
        recipe=out/(name+'-options.json');target=out/name;write(recipe,{**options,'out':str(target)});cmd=[args.java,'-Xmx768m','-jar',str(jar),subcommand,'--options-file',str(recipe)];result=subprocess.run(cmd,cwd=ROOT,capture_output=True,timeout=240);(out/(name+'.stdout')).write_bytes(result.stdout);(out/(name+'.stderr')).write_bytes(result.stderr)
        row=dict(case=name,command=cmd,exit_code=result.returncode,expected_exit_code=expected,stdout_sha256=sha(out/(name+'.stdout')),stderr_sha256=sha(out/(name+'.stderr')));runs.append(row);write(out/'progress.json',record)
        assert result.returncode==expected,(name,result.returncode,result.stderr.decode(errors='replace'))
        assert json.loads((target/'analysis-status.json').read_text())['status']==status,name
        warnings=(target/'WARNINGS.md').read_text();assert 'Required action:' in warnings and 'WARNINGS.md' in result.stderr.decode(errors='replace'),name
        if expected:assert not (target/'results.json').exists() and not (target/'potential.dx').exists() and not (target/'samples.tsv').exists(),name
        return target,row
    common={'home':str(home),'charge-model':'Explicit retained original-reference PQR;see source provenance,no inferred pH','length-units':'angstrom','equation':'lpbe','boundary':'sdh','nodes':'65,65,65','lengths':'20,20,20','center':'0,0,0','solute-dielectric':'2','solvent-dielectric':'80','probe-radius':'1.4','surface-density':'10','temperature':'298.15','salt-molarity':'0'}
    try:
        for name in ['sphere-q1-65','sphere-qminus1-65','sphere-q2-65','sphere-reference-65','sphere-solvated-33','sphere-reference-33','sphere-solvated-129','sphere-reference-129','protein-lpbe-salt','protein-npbe-salt']:
            case=cases[name];options=dict(common,input=str(refs/name/'source.pqr'),nodes=','.join([str(case['nodes'])]*3));options['solvent-dielectric']=str(case['solvent_dielectric'])
            if name.startswith('protein-'):options.update(equation=case['equation'],boundary='mdh',lengths='60,60,60',center='10,9,6',**{'salt-molarity':'.15','ion-radius':'2'})
            assert sha(Path(options['input']))==case['source_pqr_sha256'];target,row=command(name,'apbs-tools',options,0,'completed-with-warnings');actual=json.loads((target/'results.json').read_text());energy=actual['raw_total_electrostatic_energy_kj_per_mol'];assert math.isclose(energy,case['energy_kj_per_mol'],rel_tol=1e-10,abs_tol=1e-10),(name,energy,case['energy_kj_per_mol']);energies[name]=energy
            observed,values=grid(target/'potential.dx');reference,unused=grid(refs/name/'potential.dx');del unused;assert observed==reference,(name,observed,reference);row.update(full_grid_audit=observed,energy_kj_per_mol=energy,source_pqr_sha256=sha(target/'source.pqr'));assert sha(target/'source.pqr')==case['source_pqr_sha256']
            if name in {'sphere-q1-65','sphere-qminus1-65','sphere-q2-65'}:vectors[name]=values
            del values
            session=json.loads((target/'session.json').read_text());assert all(not p['alive_after_cleanup'] for p in session['processes']);assert [p['exit_code'] for p in session['processes']]==[13,0]
            write(out/'progress.json',record);print(name,'passed',observed['points'],flush=True)
        plus=vectors['sphere-q1-65'];minus=vectors['sphere-qminus1-65'];twice=vectors['sphere-q2-65'];assert len(plus)==274625
        assert all(a==-b for a,b in zip(plus,minus));maximum=max(abs(b-2*a)/max(1,abs(b)) for a,b in zip(plus,twice));assert maximum<1e-6
        record['charge_linearity']=dict(nodes=274625,sign_reversal_exact=True,double_charge_max_relative_rounding_error=maximum,energy_quadratic_relative_error=abs(energies['sphere-q2-65']/(4*energies['sphere-q1-65'])-1))
        e=1.602176634e-19;na=6.02214076e23;epsilon0=8.8541878188e-12;born=e*e*na/(8*math.pi*epsilon0*2e-10)*(1/80-1/2)/1000;convergence=[]
        for n in [33,65,129]:
            solute=energies['sphere-q1-65'] if n==65 else energies['sphere-solvated-'+str(n)];difference=solute-energies['sphere-reference-'+str(n)];error=abs(difference/born-1);convergence.append(dict(nodes=n,solvent_minus_reference_kj_per_mol=difference,analytic_born_kj_per_mol=born,relative_error=error))
        assert convergence[0]['relative_error']<.05 and convergence[1]['relative_error']<.01 and convergence[2]['relative_error']<.005 and all(a['relative_error']>b['relative_error'] for a,b in zip(convergence,convergence[1:]));record['independent_born_convergence']=convergence
        # Original process really exits0 for this failing scientific output;the adapter must reject its NaNs.
        zero=dict(common,input=str(refs/'sphere-q0-65/source.pqr'));target,row=command('zero-rhs-invalid','apbs-tools',zero,2,'failed');session=json.loads((target/'session.json').read_text());assert session['processes'][-1]['exit_code']==0;assert 'nonfinite' in (target/'WARNINGS.md').read_text();assert (target/'partial/potential.dx').is_file();row['upstream_exit0_invalid_output_quarantined']=True
        for name,mutations,expected,status in [('missing-home',{'home':''},3,'skipped-unavailable-dependency'),('absent-home',{'home':str(out/'absent')},3,'skipped-unavailable-dependency'),('bad-coverage',{'lengths':'2,2,2'},2,'failed'),('unsupported-nodes',{'nodes':'64,65,65'},2,'failed')]:
            command(name,'apbs-tools',dict(common,input=str(refs/'sphere-q1-65/source.pqr'),**mutations),expected,status)
        # Native packaged sampler consumes the real solver map,explicit physical units and missing rows.
        points=out/'points.tsv';points.write_text('point_id\tx\ty\tz\ncenter\t0\t0\t0\nedge\t10\t10\t10\noutside\t11\t0\t0\n',encoding='utf-8')
        native={'input':str(out/'sphere-q1-65/potential.dx'),'points':str(points),'outside':'missing','length-units':'angstrom','value-units':'kt-per-e','temperature':'298.15'}
        target,row=command('sample-original-map','electrostatic-grid',native,0,'completed-with-warnings');rows=(target/'samples.tsv').read_text().splitlines();center=rows[1].split('\t');edge=rows[2].split('\t');outside=rows[3].split('\t');assert float(center[5])==plus[(32*65+32)*65+32];assert float(edge[5])==plus[-1];assert outside[4]=='false' and outside[5]==outside[7]=='';assert math.isclose(float(center[7]),float(center[5])*1.380649e-23*298.15/1.602176634e-19,rel_tol=1e-14);row['independent_samples_checked']=3
        command('sample-outside-error','electrostatic-grid',dict(native,outside='error'),2,'failed')
        command('native-invalid-original','electrostatic-grid',dict(native,input=str(refs/'sphere-q0-65/potential.dx')),2,'failed')
        record.update(status='passed-packaged-original-apbs-and-native-grid-subset',successful_solver_cases=10,solver_failure_or_skip_controls=5,native_cases=3,canonical_scope='No automatic PDB2PQR preparation,surface rendering,historical paper parameters or alternate runtime profile is implied. Independently retained original references,charge linearity and analytic Born convergence support only this solver subset.')
    except Exception as failure:record.update(status='failed-retained',error=repr(failure));raise
    finally:record['completed_utc']=dt.datetime.now(dt.timezone.utc).isoformat();write(out/'verification.json',record)
    print(json.dumps({key:record[key] for key in ['status','successful_solver_cases','solver_failure_or_skip_controls','native_cases']}));return 0

if __name__=='__main__':raise SystemExit(main())
