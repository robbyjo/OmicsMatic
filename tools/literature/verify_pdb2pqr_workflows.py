#!/usr/bin/env python3
"""Check packaged typed preparation against independently retained original runs."""
from pathlib import Path
import argparse,datetime as dt,hashlib,json,math,struct,subprocess

ROOT=Path(__file__).resolve().parents[2]
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
def fingerprint(path):
    identity=hashlib.sha256();numbers=hashlib.sha256();count=0;charges=[];radii=[]
    for line in path.read_text().splitlines():
        f=line.split()
        if not f or f[0] not in {'ATOM','HETATM'}:continue
        assert len(f) in {10,11};offset=int(len(f)==11);chain=f[4] if offset else '';values=[float(x) for x in f[5+offset:]];assert len(values)==5 and all(map(math.isfinite,values));identity.update(('|'.join([f[0],chain,f[4+offset],f[3],f[2]])+'\n').encode());numbers.update(struct.pack('<i5d',int(f[1]),*values));count+=1;charges.append(values[3]);radii.append(values[4])
    return dict(atoms=count,identity_sha256=identity.hexdigest(),numeric_sha256=numbers.hexdigest(),net_charge_e=math.fsum(charges),zero_radius_atoms=sum(r==0 for r in radii))

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java',default='java');parser.add_argument('--java25');parser.add_argument('--jar',required=True,type=Path);parser.add_argument('--home',required=True,type=Path);parser.add_argument('--references',required=True,type=Path);parser.add_argument('--out',required=True,type=Path);args=parser.parse_args();out=args.out.absolute();out.mkdir(parents=True,exist_ok=False);jar=args.jar.absolute();home=args.home.absolute();references=args.references.absolute();original=json.loads((references/'verification.json').read_text());assert original['status']=='completed-original-preparation-matrix' and original['case_count']==14;runs=[];record=dict(status='running',started_utc=dt.datetime.now(dt.timezone.utc).isoformat(),candidate_sha256=sha(jar),verifier_sha256=sha(__file__),original_reference_verification_sha256=sha(references/'verification.json'),runs=runs)
    common={'home':str(home),'force-field':'PARSE','mode':'prepare','protonation':'forcefield','debump':'false','optimize':'false','water':'keep','repair':'error','unparameterized':'error','neutral-n':'false','neutral-c':'false','state':'1','alternate':'error','length-units':'angstrom','max-heavy-shift':'10'}
    def run(name,options,exit,status,java=None):
        target=out/name;recipe=out/('case-%03d-options.json'%len(runs));write(recipe,{**options,'out':str(target)});command=[java or args.java,'-Xmx768m','-jar',str(jar),'pdb2pqr-tools','--options-file',str(recipe)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=300);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);row=dict(case=name,command=command,exit_code=r.returncode,expected_exit_code=exit,stdout_sha256=sha(out/(name+'.stdout')),stderr_sha256=sha(out/(name+'.stderr')));runs.append(row);write(out/'progress.json',record);assert r.returncode==exit,(name,r.returncode,r.stderr.decode(errors='replace'));assert json.loads((target/'analysis-status.json').read_text())['status']==status;assert 'Required action:' in (target/'WARNINGS.md').read_text();assert 'WARNINGS.md' in r.stderr.decode(errors='replace')
        if exit:assert not (target/'prepared.pqr').exists() and not (target/'results.json').exists() and not (target/'atom-lineage.json').exists()
        print(name,'passed exit',exit,flush=True);return target,row
    try:
        atoms=0;net={}
        for case in original['cases']:
            name=case['case'];flags=case['flags'];options=dict(common,input=str(references/name/'source.pdb'));options['force-field']=flags[flags.index('--ff')+1];options['debump']='false' if '--nodebump' in flags else 'true';options['optimize']='false' if '--noopt' in flags else 'true'
            if '--assign-only' in flags:options['mode']='assign-only'
            if '--titration-state-method' in flags:options.update(protonation='propka',ph=flags[flags.index('--with-ph')+1])
            if '--neutraln' in flags:options['neutral-n']='true'
            if '--neutralc' in flags:options['neutral-c']='true'
            if 'repair-one' in name:options['repair']='allow'
            target,row=run(name,options,0,'completed-with-warnings');actual=fingerprint(target/'prepared.pqr');expected=fingerprint(references/name/'prepared.pqr');assert actual==expected,(name,actual,expected);row['complete_original_atom_fingerprint']=actual;atoms+=actual['atoms'];net[name]=actual['net_charge_e'];audit=json.loads((target/'results.json').read_text());assert audit['charged_atoms']==actual['atoms'] and audit['zero_radius_atoms']==actual['zero_radius_atoms'];assert math.isclose(audit['total_charge_e'],actual['net_charge_e'],abs_tol=1e-11);assert len(json.loads((target/'atom-lineage.json').read_text()))>=actual['atoms'];session=json.loads((target/'session.json').read_text());assert all(not p['alive_after_cleanup'] for p in session['processes']);assert [p['exit_code'] for p in session['processes']]==[0,0];row['native_audit']=audit;write(out/'progress.json',record)
        assert atoms==8988
        for name,expected in [('parse-propka-ph2',2),('parse-propka-ph7',0),('parse-propka-ph12',-2),('parse-neutral-n',-1),('parse-neutral-c',1)]:assert math.isclose(net[name],expected,abs_tol=1e-10)
        base=dict(common,input=str(references/'parse-forcefield/source.pdb'))
        for name,updates,code,status in [('missing-home',{'home':''},3,'skipped-unavailable-dependency'),('absent-home',{'home':str(out/'absent')},3,'skipped-unavailable-dependency'),('repair-forbidden',{'input':str(references/'parse-repair-one-oxygen/source.pdb')},2,'failed'),('ignored-ph-forbidden',{'mode':'assign-only','protonation':'propka','ph':'7'},2,'failed'),('missing-elements',{'input':str(out/'bad.pdb')},2,'failed')]:
            if name=='missing-elements':(out/'bad.pdb').write_text('ATOM invalid\n')
            run(name,{**base,**updates},code,status)
        # State IDs are deliberately noncontiguous;source order selects the second state.
        originalPdb=(references/'parse-forcefield/source.pdb').read_text();atomlines=''.join(line+'\n' for line in originalPdb.splitlines() if line[:6].strip() in {'ATOM','HETATM','TER'});multi=out/'two-models.pdb';multi.write_text('MODEL        9\n'+atomlines+'ENDMDL\nMODEL      100\n'+atomlines+'ENDMDL\nEND\n');target,row=run('state-ordinal-2',{**base,'input':str(multi),'state':'2'},0,'completed-with-warnings');assert fingerprint(target/'prepared.pqr')==fingerprint(references/'parse-forcefield/prepared.pqr');row['source_models']=[9,100]
        if args.java25:
            source=out/'输入 空间.pdb';source.write_bytes((references/'parse-forcefield/source.pdb').read_bytes());target,row=run('结果 空间-Java25',{**base,'input':str(source)},0,'completed-with-warnings',args.java25);assert fingerprint(target/'prepared.pqr')==fingerprint(references/'parse-forcefield/prepared.pqr');row['Unicode_source_output_recipe_passed']=True
        record.update(status='passed-packaged-original-preparation-matrix-and-policy-controls',original_comparison_cases=14,complete_original_atom_rows=atoms,complete_original_numeric_scalars=atoms*5,explicit_charge_controls={'propka_ph2':2,'propka_ph7':0,'propka_ph12':-2,'neutral_n':-1,'neutral_c':1},failure_or_skip_controls=5,state_ordinal_workflows=1,additional_Java25_Unicode_workflows=1 if args.java25 else 0,scope='Actual original six-force-field,explicit PROPKA pH,optimization,terminal neutralization,assign-only and missing-heavy repair comparisons. Every PQR coordinate,charge,radius,serial and identity matches the independently retained original invocation. No source-paper parameter reconstruction,experimental pKa/chemistry validation,custom ligand/field or alternative runtime profile is implied.')
    except Exception as failure:record.update(status='failed-retained',error=repr(failure));raise
    finally:record['completed_utc']=dt.datetime.now(dt.timezone.utc).isoformat();write(out/'verification.json',record)
    print(json.dumps({key:record[key] for key in ['status','original_comparison_cases','complete_original_atom_rows','failure_or_skip_controls']}))

if __name__=='__main__':main()
