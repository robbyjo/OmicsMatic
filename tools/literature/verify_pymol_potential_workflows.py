#!/usr/bin/env python3
"""Run real packaged Linux PyMOL potential surfaces and scientific failure controls."""
from pathlib import Path
import argparse,datetime as dt,hashlib,json,math,os,re,subprocess

ROOT=Path(__file__).resolve().parents[2]
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def write(path,value):path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
def transformed_grid(source,target,multiplier=None,constant=None,invalid=None):
    text=source.read_text();match=re.search(r'items\s+(\d+)\s+data follows\s*\n',text);assert match;n=int(match[1]);tail=text[match.end():].split();values=[float(t) for t in tail[:n]];ending=text.find('attribute "dep"',match.end());assert ending>0
    if constant is not None:values=[constant]*n
    elif multiplier is not None:values=[x*multiplier for x in values]
    tokens=[format(x,'.17g') for x in values]
    if invalid is not None:tokens[-1]=invalid
    body='\n'.join(' '.join(tokens[i:i+3]) for i in range(0,n,3))+'\n';target.write_text(text[:match.end()]+body+text[ending:]);return n

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java',default='/usr/bin/java');p.add_argument('--jar',required=True,type=Path);p.add_argument('--home',required=True,type=Path);p.add_argument('--pqr',required=True,type=Path);p.add_argument('--grid',required=True,type=Path);p.add_argument('--reference-analysis',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();out=a.out.absolute();out.mkdir(parents=True,exist_ok=False);jar=a.jar.absolute();pqr=a.pqr.absolute();grid=a.grid.absolute();ref=a.reference_analysis.absolute();runs=[];record=dict(status='running',started_utc=dt.datetime.now(dt.timezone.utc).isoformat(),candidate_sha256=sha(jar),verifier_sha256=sha(__file__),source_pqr_sha256=sha(pqr),source_grid_sha256=sha(grid),runs=runs)
    common={'input':str(pqr),'grid':str(grid),'home':str(a.home.absolute()),'charge-model':'Original1CRN PARSE fixture;forcefield protonation;NPBE0.15M map;explicit display control','length-units':'angstrom','value-units':'kt-per-e','temperature':'298.15','surface-radii':'source-pqr','surface-solvent':'excluded','probe-radius':'1.4','sample-location':'surface','surface-quality':'1','ramp-range':'-5,0,5','low-color':'1,0,0','middle-color':'1,1,1','high-color':'0,0,1','background':'1,1,1','width':'1200','height':'1000','buffer':'6'}
    def run(name,changes,code,status):
        options={**common,**changes,'out':str(out/name)};options={k:v for k,v in options.items() if v is not None};recipe=out/('case-%03d-options.json'%len(runs));write(recipe,options);command=[a.java,'-Xmx768m','-jar',str(jar),'potential-surface','--options-file',str(recipe)];r=subprocess.run(command,cwd=ROOT,capture_output=True,timeout=300);(out/(name+'.stdout')).write_bytes(r.stdout);(out/(name+'.stderr')).write_bytes(r.stderr);row=dict(case=name,command=command,exit_code=r.returncode,expected_exit_code=code,stdout_sha256=sha(out/(name+'.stdout')),stderr_sha256=sha(out/(name+'.stderr')));runs.append(row);write(out/'progress.json',record);assert r.returncode==code,(name,r.returncode,r.stderr.decode(errors='replace'));target=out/name;assert json.loads((target/'analysis-status.json').read_text())['status']==status;assert 'Required action:' in (target/'WARNINGS.md').read_text();assert 'WARNINGS.md' in r.stderr.decode(errors='replace')
        if code:assert not (target/'surface.png').exists() and not (target/'figure.svg').exists() and not (target/'native-audit.json').exists()
        else:
            audit=json.loads((target/'native-audit.json').read_text());assert audit['atoms_checked']==642 and audit['grid_nodes_checked']==274625;original=json.loads((target/'original-surface.json').read_text());assert original['status']=='original-execution-completed-requires-native-audit';assert sha(target/'map-float32-le.bin')==audit['float32_field_sha256'];svg=(target/'figure.svg').read_text();assert 'data:image/png;base64,' in svg and '<script' not in svg;row.update(native_audit=audit,raw_surface_sha256=sha(target/'surface.png'),standalone_figure_sha256=sha(target/'figure.svg'),original_ledger_sha256=sha(target/'original-surface.json'))
        print(name,'passed exit',code,flush=True);write(out/'progress.json',record);return target,row
    try:
        base,row=run('source-pqr-excluded',{},0,'completed-with-warnings');baseline=json.loads((ref/'native-audit.json').read_text());assert row['native_audit']['float32_field_sha256']==baseline['float32_field_sha256'];assert row['native_audit']['surface_zero_radius_atoms']==236
        target,row=run('pymol-display-radii',{'surface-radii':'pymol'},0,'completed-with-warnings');assert row['native_audit']['surface_zero_radius_atoms']==0 and row['native_audit']['maximum_surface_radius_difference_from_PQR_angstrom']>1;assert 'DISPLAY RADII DIFFER' in (target/'WARNINGS.md').read_text()
        target,row=run('accessible-probe-offset-custom-background',{'surface-solvent':'accessible','sample-location':'probe-offset','background':'.1,.2,.3','low-color':'.2,.7,.9','middle-color':'.95,.95,.95','high-color':'.8,.3,.6'},0,'completed-with-warnings');assert row['native_audit']['actual_settings']['surface_solvent']==row['native_audit']['actual_settings']['surface_ramp_above_mode']==1
        factor=1.380649e-23*298.15/1.602176634e-19;volts=out/'explicit-volts.dx';transformed_grid(grid,volts,multiplier=factor);target,row=run('volts-asymmetric-ramp',{'grid':str(volts),'value-units':'volt','temperature':None,'ramp-range':'-.25,0,.125','low-color':'1,.5,0','middle-color':'1,1,1','high-color':'.5,0,1'},0,'completed-with-warnings');assert math.isclose(row['native_audit']['figure']['legend_middle_fraction'],2/3,abs_tol=1e-15);assert 'potential (V)' in (target/'figure.svg').read_text();row['explicit_input_grid_conversion_volts_per_kt_per_e']=factor
        zero=out/'synthetic-zero-scalar.dx';transformed_grid(grid,zero,constant=0);target,row=run('finite-zero-scalar',{'grid':str(zero),'value-units':'arbitrary','temperature':None,'ramp-range':'-1,0,1','charge-model':'Synthetic finite-zero scalar display control,not a PB solution'},0,'completed-with-warnings');assert 'arbitrary units' in (target/'figure.svg').read_text();assert row['native_audit']['underflowed_nonzero_source_values']==0
        source=out/'输入 空间.pqr';source.write_bytes(pqr.read_bytes());scalar=out/'场 空间.dx';scalar.write_bytes(grid.read_bytes());view=baseline['camera_view'];target,row=run('结果 显式相机',{'input':str(source),'grid':str(scalar),'width':'600','height':'500','view':','.join(map(str,view))},0,'completed-with-warnings');row['Unicode_source_output_and_explicit_camera_passed']=True
        run('missing-runtime',{'home':str(out/'absent-home')},3,'skipped-unavailable-dependency')
        shifted=out/'shifted-frame.pqr';lines=[]
        for line in pqr.read_text().splitlines():
            fields=line.split()
            if fields and fields[0] in {'ATOM','HETATM'}:fields[-5]=str(float(fields[-5])+1000);line=' '.join(fields)
            lines.append(line)
        shifted.write_text('\n'.join(lines)+'\n');run('mismatched-frame',{'input':str(shifted)},2,'failed')
        clipped=list(view);clipped[15]=1;clipped[16]=2;run('empty-clipped-camera',{'view':','.join(map(str,clipped))},2,'failed')
        invalid=out/'invalid-late-node.dx';transformed_grid(grid,invalid,invalid='NaN');run('late-NaN-node',{'grid':str(invalid)},2,'failed')
        overflow=out/'float32-overflow.dx';transformed_grid(grid,overflow,invalid='1e40');run('float32-overflow',{'grid':str(overflow)},2,'failed')
        run('float32-collapsed-ramp',{'ramp-range':'16777216,16777217,16777218'},2,'failed')
        target,row=run('owned-real-timeout',{'width':'4000','height':'3000','timeout':'1'},2,'failed');session=json.loads((target/'session.json').read_text());assert session.get('surface_timed_out');pid=session['surface_owned_pid']
        try:os.kill(pid,0)
        except ProcessLookupError:row['owned_process_verified_absent']=True
        else:raise AssertionError('Timed-out original worker remains live')
        assert sha(jar)==record['candidate_sha256'] and sha(pqr)==record['source_pqr_sha256'] and sha(grid)==record['source_grid_sha256'];record.update(status='passed-actual-packaged-original-potential-surfaces-and-failure-controls',successful_surface_cases=6,complete_original_grid_nodes_audited=6*274625,complete_atom_phase_checks=6*642*3,failure_or_skip_controls=7,scope='Actual original Linux PyMOL3.1.0 rendering through packaged commands. Both radius policies,excluded/accessible surfaces,normal probe offset,custom RGB/background,explicit physical-unit input conversion,asymmetric ramp,finite-zero scalar,Unicode paths and explicit camera are distinct. Native full-field/atom/frame/ramp audits and retained unit-labeled SVG figures. No charge preparation or PB solve is performed by the surface command itself.')
    except Exception as failure:record.update(status='failed-retained',error=repr(failure));raise
    finally:record['completed_utc']=dt.datetime.now(dt.timezone.utc).isoformat();write(out/'verification.json',record)
    print(json.dumps({key:record[key] for key in ['status','successful_surface_cases','failure_or_skip_controls']}))

if __name__=='__main__':main()
