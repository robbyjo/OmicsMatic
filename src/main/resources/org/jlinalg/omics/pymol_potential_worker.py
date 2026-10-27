"""Trusted original PyMOL scalar-map surface workflow;no user Python/PML/session."""
import hashlib,json,math,sys,traceback
from pathlib import Path
sys.path.insert(0,sys.argv[1])
import numpy as np
import pymol2

def write(path,value):path.write_text(json.dumps(value,allow_nan=False,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
def atoms(cmd):
    model=cmd.get_model('jl_model',state=1)
    return [dict(index=a.index,id=a.id,name=a.name,resn=a.resn,resi=a.resi,chain=a.chain,element=a.symbol,coord=list(a.coord),partial_charge=a.partial_charge,radius=a.vdw) for a in model.atom]

def main():
    recipe=json.loads(Path(sys.argv[2]).read_text(encoding='utf-8'));folder=Path(sys.argv[3])
    expected={'pqr','grid','source_atoms','grid_counts','grid_minimum','grid_maximum','maximum_atoms','maximum_grid_points','surface_radii','surface_solvent','probe_radius','ramp_above_mode','surface_quality','levels','colors','background','width','height','buffer','view'}
    if set(recipe)!=expected:raise ValueError('Unexpected/missing typed potential-surface recipe fields')
    if recipe['surface_radii'] not in {'source-pqr','pymol'} or recipe['surface_solvent'] not in {'excluded','accessible'}:raise ValueError('Unsupported explicit surface policy')
    with pymol2.PyMOL() as instance:
        c=instance.cmd;c.set('max_threads',1);c.set('retain_order',1);c.set('pdb_retain_ids',1);c.set('auto_zoom',0)
        c.load(recipe['pqr'],'jl_model',format='pqr',quiet=1)
        loaded=atoms(c)
        if len(loaded)!=len(recipe['source_atoms']) or not 0<len(loaded)<=recipe['maximum_atoms']:raise ValueError('Original PQR atom count differs/exceeds bound')
        for actual,source in zip(loaded,recipe['source_atoms']):
            if (actual['id'],actual['chain'],actual['resi'],actual['resn'],actual['name'])!=(source['serial'],source['chain'],source['residue_id'],source['residue'],source['name']):raise ValueError('Original PQR load changed atom order/identity before radius assignment')
        if recipe['surface_radii']=='source-pqr':
            c.alter_list('jl_model',[[a['index'],'vdw='+repr(float(source['radius']))] for a,source in zip(loaded,recipe['source_atoms'])],quiet=1)
        surface=atoms(c)
        if not any(a['radius']>0 for a in surface):raise ValueError('No positive surface radii;no molecular surface can be rendered')
        for a in surface:
            if not math.isfinite(a['radius']) or not 0<=a['radius']<=20:raise ValueError('Surface radius outside admitted0..20 Angstrom range')
            for axis in range(3):
                margin=a['radius']+recipe['probe_radius']*(1+recipe['ramp_above_mode'])
                if a['coord'][axis]-margin<recipe['grid_minimum'][axis]-1e-4 or a['coord'][axis]+margin>recipe['grid_maximum'][axis]+1e-4:raise ValueError('Map does not cover display radii+probe/ramp offset in the supplied coordinate frame')
        c.load(recipe['grid'],'jl_map',format='dx',quiet=1)
        field=c.get_volume_field('jl_map',state=1,copy=1)
        if list(field.shape)!=recipe['grid_counts'] or field.size>recipe['maximum_grid_points'] or not np.isfinite(field).all():raise ValueError('Original map shape/nonfinite data differs')
        binary=np.asarray(field,dtype='<f4',order='C').tobytes(order='C');(folder/'map-float32-le.bin').write_bytes(binary)
        map_record=dict(shape=list(field.shape),dtype=str(field.dtype),minimum=float(field.min()),maximum=float(field.max()),float32_le_sha256=hashlib.sha256(binary).hexdigest(),extent=c.get_extent('jl_map',state=1),object_matrix=c.get_object_matrix('jl_map',state=1))
        # Register float RGBs as named colors. Passing integer RGB vectors directly
        # through this original ramp wrapper is avoided;only controlled names enter it.
        for name,color in zip(['jl_low','jl_mid','jl_high'],recipe['colors']):c.set_color(name,[float(v) for v in color])
        c.ramp_new('jl_ramp','jl_map',range=[float(v) for v in recipe['levels']],color=['jl_low','jl_mid','jl_high'],state=1,quiet=1)
        names=[entry for entry in c.get_session(names='jl_ramp')['names'] if entry[0]=='jl_ramp']
        if len(names)!=1:raise ValueError('Original ramp object absent/ambiguous')
        # Original3.1.0 session schema is pinned with this runtime;export only the
        # ramp fields needed by Java,never execute/load a user session.
        payload=names[0][5]
        if len(payload)!=11 or payload[2]!=3:raise ValueError('Original ramp serialization schema differs')
        ramp=dict(levels=payload[3],colors=[payload[4][i:i+3] for i in (0,3,6)],type_code=payload[5],map=payload[6],state=payload[7]+1,object_type=c.get_type('jl_ramp'),registered_colors=[list(c.get_color_tuple(name)) for name in ['jl_low','jl_mid','jl_high']])
        c.hide('everything','all');c.show('surface','jl_model');c.set('surface_color','jl_ramp','jl_model');c.set('surface_mode',0,'jl_model');c.set('surface_solvent',int(recipe['surface_solvent']=='accessible'),'jl_model');c.set('solvent_radius',float(recipe['probe_radius']),'jl_model');c.set('surface_ramp_above_mode',recipe['ramp_above_mode'],'jl_model');c.set('surface_quality',recipe['surface_quality'],'jl_model');c.set('orthoscopic',1);c.set('ray_opaque_background',1);c.set_color('jl_background',[float(v) for v in recipe['background']]);c.bg_color('jl_background')
        c.disable('jl_ramp')  # Hide the unlabeled original GUI widget;Java writes a unit-labeled standalone SVG legend.
        if recipe['view'] is None:c.orient('jl_model',state=1);c.zoom('jl_model',buffer=float(recipe['buffer']),state=1,complete=1)
        else:c.set_view(recipe['view'])
        c.png(str(folder/'surface.png'),width=recipe['width'],height=recipe['height'],ray=1,quiet=1)
        final=atoms(c)
        settings={name:c.get_setting_int(name,'jl_model') for name in ['surface_mode','surface_solvent','surface_ramp_above_mode','surface_quality']};settings.update(solvent_radius=c.get_setting_float('solvent_radius','jl_model'),surface_color=c.get('surface_color','jl_model'),orthoscopic=c.get_setting_int('orthoscopic'),ray_opaque_background=c.get_setting_int('ray_opaque_background'))
        result=dict(status='original-execution-completed-requires-native-audit',pymol_version=list(c.get_version()),loaded_atoms=loaded,surface_atoms=surface,final_atoms=final,map=map_record,ramp=ramp,settings=settings,view=list(c.get_view()),image='surface.png',width=recipe['width'],height=recipe['height'],background=recipe['background'],surface_radii=recipe['surface_radii'],surface_solvent=recipe['surface_solvent'],scope='Original map-ramp surface rendering. PQR does not encode a bond graph/elements;PyMOL display inference is retained in ledgers. Source-PQR radius policy explicitly overrides loaded display radii,including zeros. No charge preparation,PB solve,energy or paper reconstruction occurs here.')
        write(folder/'original-surface.json',result)

try:main()
except BaseException as failure:
    traceback.print_exc();write(Path(sys.argv[3])/'original-surface-failure.json',dict(status='failed-original-execution',error=type(failure).__name__+': '+str(failure)));sys.exit(2)
