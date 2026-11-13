#!/usr/bin/env python3
# Copyright (C) 2026 JLinAlg contributors; SPDX-License-Identifier: GPL-3.0-or-later
"""Run complete original image/macro controls independently and exercise packaged workflow refusals."""
from pathlib import Path
import argparse,copy,hashlib,json,os,shutil,subprocess,time,traceback,zipfile
HERE=Path(__file__).resolve().parent
ACTION='Required action: provide permitted Java/Javac21/25, complete original1.54t --original-jar, the standalone --jar, AWT display support and NEW output. Missing/forbidden display/runtime prerequisites SKIP3 skip dependent analysis. Retain all original and adapter outputs and diagnostics; resolve FAIL2 without changing original controls. These are synthetic controls of the complete original Java macro engine, not paper reproductions or external-plugin qualification.'
def save(p,v):p.write_text(json.dumps(v,indent=2)+'\n',encoding='utf8',newline='\n')
def pin(p):return dict(bytes=p.stat().st_size,sha256=hashlib.sha256(p.read_bytes()).hexdigest())
def main():
 p=argparse.ArgumentParser(description=__doc__,epilog=ACTION);p.add_argument('--java-bin',type=Path,required=True);p.add_argument('--original-jar',type=Path,required=True);p.add_argument('--jar',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();out=a.out.resolve();owned=False;calls=[]
 try:
  out.mkdir(parents=True,exist_ok=False);owned=True;jdk=a.java_bin.resolve();suffix='.exe' if os.name=='nt' else '';java=jdk/('java'+suffix);javac=jdk/('javac'+suffix);original=a.original_jar.resolve();jar=a.jar.resolve();env={k:v for k,v in os.environ.items() if k.upper() not in ['JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH']};before={str(x):pin(x) for x in [original,jar,HERE/'OriginalControl.java',HERE/'test_workflow.py']}
  def run(label,command,cwd=out,expected=0,needle=None):
   command=list(map(str,command));start=time.time()
   with (out/(label+'.stdout')).open('xb') as so,(out/(label+'.stderr')).open('xb') as se:
    process=subprocess.Popen(command,cwd=cwd,env=env,stdin=subprocess.DEVNULL,stdout=so,stderr=se)
    try:code=process.wait(timeout=180)
    except subprocess.TimeoutExpired:
     process.kill();process.wait();raise ValueError('Control observation timeout; retained process diagnostics: '+label)
   row=dict(label=label,command=command,pid=process.pid,exit_code=code,expected_exit=expected,elapsed_seconds=time.time()-start);calls.append(row);save(out/(label+'.process.json'),row)
   raw=(out/(label+'.stdout')).read_bytes()+(out/(label+'.stderr')).read_bytes()
   if code==3 and expected==0:raise OSError('Required original display/runtime prerequisite unavailable at '+label)
   if code!=expected or needle and needle.encode() not in raw:raise ValueError('Control differs: '+label)
   return raw
  classes=out/'control-classes';classes.mkdir();run('original-compile',[javac,'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',original,'-d',classes,HERE/'OriginalControl.java']);cp=str(classes)+os.pathsep+str(original)
  run('original-inputs',[java,'-Djava.awt.headless=true','-cp',cp,'OriginalControl','generate',out/'fixtures'])
  case_specs={
   'byte':dict(input='byte.tif',commands=['Remove Outliers...','Median...','Gaussian Blur...','Z Project...','Measure'],body='run("Remove Outliers...", "radius=1 threshold=50 which=Bright stack"); saveAs("Tiff",d+"/outliers.tif"); run("Median...", "radius=0.5 stack"); saveAs("Tiff",d+"/median.tif"); run("Gaussian Blur...", "sigma=0.5 stack"); setMinAndMax(0,180); saveAs("Tiff",d+"/gaussian.tif"); run("Z Project...", "projection=[Max Intensity]"); saveAs("Tiff",d+"/max.tif"); makeRectangle(1,1,4,3); run("Measure"); saveAs("Results",d+"/measurements.csv");',files={'outliers.tif':'TIFF','median.tif':'TIFF','gaussian.tif':'TIFF','max.tif':'TIFF','measurements.csv':'FILE'}),
   'hyper':dict(input='ushort.tif',commands=['Gaussian Blur...','Make Composite','Red','Green','Z Project...'],body='run("Gaussian Blur...", "sigma=0.5 stack"); run("Make Composite"); Stack.setChannel(1); setMinAndMax(0,2000); run("Red"); Stack.setChannel(2); setMinAndMax(0,3000); run("Green"); saveAs("Tiff",d+"/composite.tif"); run("Z Project...", "start=1 stop=3 projection=[Max Intensity] all"); saveAs("Tiff",d+"/max.tif");',files={'composite.tif':'TIFF','max.tif':'TIFF'}),
   'float':dict(input='float.tif',commands=['Gaussian Blur...','Z Project...'],body='saveAs("Tiff",d+"/unmodified.tif"); run("Gaussian Blur...", "sigma=0.5 stack"); saveAs("Tiff",d+"/gaussian.tif"); run("Z Project...", "projection=[Max Intensity]"); saveAs("Tiff",d+"/max.tif");',files={'unmodified.tif':'TIFF','gaussian.tif':'TIFF','max.tif':'TIFF'}),
   'rgb':dict(input='rgb.tif',commands=['Gaussian Blur...','Z Project...'],body='run("Gaussian Blur...", "sigma=0.5 stack"); saveAs("Tiff",d+"/gaussian.tif"); run("Z Project...", "projection=[Max Intensity]"); saveAs("Tiff",d+"/max.tif");',files={'gaussian.tif':'TIFF','max.tif':'TIFF'}),
   'particles':dict(input='particles.tif',commands=['Analyze Particles...','Set Measurements...'],body='setOption("BlackBackground",true); setAutoThreshold("Default dark"); run("Set Measurements...", "area mean centroid perimeter shape integrated decimal=6"); run("Analyze Particles...", "size=1-Infinity circularity=0.00-1.00 show=Nothing display clear"); saveAs("Results",d+"/particles.csv"); saveAs("Tiff",d+"/threshold.tif");',files={'particles.csv':'FILE','threshold.tif':'TIFF'})}
  requests=out/'requests';requests.mkdir();comparisons={};base=None
  for name,spec in case_specs.items():
   folder=out/('original-'+name);folder.mkdir();(folder/'inputs').mkdir();(folder/'results').mkdir();(folder/'home').mkdir();(folder/'plugins').mkdir();shutil.copyfile(out/'fixtures'/spec['input'],folder/'inputs'/spec['input']);macro='setBatchMode(true); d=getArgument()+"/results"; open(getArgument()+"/inputs/'+spec['input']+'"); '+spec['body']+' return "complete-original-'+name+'";';macrofile=requests/(name+'.ijm');macrofile.write_text(macro,encoding='utf8',newline='\n')
   run('original-'+name,[java,'-Djava.awt.headless=false','-Duser.home='+str(folder/'home'),'-Dplugins.dir='+str(folder/'plugins'),'-cp',cp,'OriginalControl','run',macrofile,folder],cwd=folder)
   req=dict(schema_version=1,version='1.54t',mode='MACRO',macro=str(macrofile),inputs={spec['input']:str(out/'fixtures'/spec['input'])},plugins_directory=None,required_commands=spec['commands'],expected_outputs={n:dict(kind=k,minimum_bytes=1) for n,k in spec['files'].items()},input_provenance='Complete independently generated synthetic original Java TIFF fixture; no biological inference',analysis_population='Every original input C/Z/T plane, explicitly selected by the retained macro',execution_policy='ALLOW',display_policy='ALLOW',out=str(out/name),timeout_seconds=30,heap_megabytes=512,maximum_decoded_pixels=1000000,expected_return='complete-original-'+name)
   request=requests/(name+'.json');save(request,req);raw=run('adapter-'+name,[java,'-jar',jar,'--request',request],needle='PASS_ORIGINAL_IMAGEJ154T_COMPLETE_DECLARED_MACRO_OUTPUTS')
   if b'Required action:' not in raw:raise ValueError('Missing prominent recovery guidance')
   status=json.loads((out/name/'analysis-status.json').read_bytes());worker=json.loads((out/name/'worker-status.json').read_bytes())
   if not status['result_admitted'] or status['exit_code']!=0:raise ValueError('Successful complete original macro not admitted')
   rows={}
   for n,kind in spec['files'].items():
    reference=folder/'results'/n;actual=out/name/'results'/n;equal=reference.read_bytes()==actual.read_bytes();rows[n]=dict(original=pin(reference),adapter=pin(actual),complete_file_byte_identical=equal)
    if kind=='TIFF':
     run('dump-reference-'+name+'-'+n,[java,'-Djava.awt.headless=true','-cp',cp,'OriginalControl','dump',reference,folder/(n+'.pixels.tsv')]);run('dump-adapter-'+name+'-'+n,[java,'-Djava.awt.headless=true','-cp',cp,'OriginalControl','dump',actual,out/name/(n+'.pixels.tsv')]);rows[n]['complete_original_pixels_axes_calibration_identical']=(folder/(n+'.pixels.tsv')).read_bytes()==(out/name/(n+'.pixels.tsv')).read_bytes()
     if not rows[n]['complete_original_pixels_axes_calibration_identical']:raise ValueError('Complete independently decoded original pixels/axes/calibration differ: '+name+'/'+n)
     if worker['outputs'][n]['decoded_image']['complete_pixels']<1:raise ValueError('No complete TIFF pixel population audited')
    if not equal:raise ValueError('Complete original output file differs: '+name+'/'+n)
   comparisons[name]=rows
   if name=='byte':base=req
  def bad(label,mutate,expected=2,needle=None):
   req=copy.deepcopy(base);req['out']=str(out/label);mutate(req);request=requests/(label+'.json');save(request,req);raw=run(label,[java,'-jar',jar,'--request',request],expected=expected,needle=needle)
   if b'Required action:' not in raw:raise ValueError('Refusal omitted recovery guidance: '+label)
   target=out/label
   if (target/'analysis-status.json').exists():
    status=json.loads((target/'analysis-status.json').read_bytes())
    if status['result_admitted'] or status['exit_code']!=expected:raise ValueError('Refusal admitted partial analysis: '+label)
   return req
  bad('execution-forbid',lambda r:r.update(execution_policy='FORBID'),3,'SKIP3')
  bad('display-forbid',lambda r:r.update(display_policy='FORBID'),3,'SKIP3')
  bad('missing-macro',lambda r:r.update(macro=str(out/'not-present.ijm')),3,'SKIP3')
  bad('missing-input',lambda r:r.update(inputs={'byte.tif':str(out/'not-present.tif')}),3,'SKIP3')
  bad('missing-plugins',lambda r:r.update(plugins_directory=str(out/'not-present-plugins'),plugins_provenance='Declared missing complete plugin provider'),3,'SKIP3')
  bad('missing-command',lambda r:r.update(required_commands=['Unavailable test plugin command']),3,'SKIP3')
  bad('wrong-edition',lambda r:r.update(version='2.14.0'),2,'FAIL2')
  bad('unknown-field',lambda r:r.update(implicit_edition_substitution=True),2,'FAIL2')
  bad('existing-output',lambda r:r.update(out=str(out/'byte')),2,'FAIL2')
  bad('input-overlap',lambda r:r.update(out=str(out/'fixtures')),2,'FAIL2')
  bad('input-capacity',lambda r:r.update(maximum_input_bytes=2),2,'FAIL2')
  bad('pixel-capacity',lambda r:r.update(maximum_decoded_pixels=1),2,'FAIL2')
  bad('wrong-return',lambda r:r.update(expected_return='not-original-return'),2,'FAIL2')
  bad('missing-output',lambda r:r.update(expected_outputs={'never-written.csv':dict(kind='FILE',minimum_bytes=1)}),2,'FAIL2')
  bad('extra-output',lambda r:r['expected_outputs'].pop('max.tif'),2,'FAIL2')
  for label,code in [('macro-error','setBatchMode(true); impossibleUndefinedFunction();'),('timeout','while(true) {}'),('invalid-tiff','File.saveString("This is not a TIFF",getArgument()+"/results/invalid.tif");')]:
   macro=requests/(label+'.ijm');macro.write_text(code,encoding='utf8',newline='\n')
   def mutate(r,macro=macro,label=label):
    r.update(macro=str(macro),required_commands=[],timeout_seconds=2 if label=='timeout' else 30);r.pop('expected_return',None)
    if label=='invalid-tiff':r['expected_outputs']={'invalid.tif':dict(kind='TIFF',minimum_bytes=1)}
   bad(label,mutate,2,'FAIL2')
  plugin_source=out/'plugin-source';plugin_source.mkdir();plugin_classes=out/'plugin-classes';plugin_classes.mkdir();plugins=out/'fixture-plugins';plugins.mkdir()
  fixtures={'Fixture_Echo':'ij.IJ.log("Synthetic normal plugin log; not an exception");','Fixture_Throws':'throw new IllegalStateException("original caught fixture exception");','Fixture_Exit_Zero':'System.exit(0);'}
  for name,body in fixtures.items():(plugin_source/(name+'.java')).write_text('public final class '+name+' implements ij.plugin.PlugIn { public void run(String arg) { '+body+' } }\n',encoding='utf8',newline='\n')
  run('fixture-plugin-compile',[javac,'--release','17','-encoding','UTF-8','-Xlint:all','-Werror','-cp',original,'-d',plugin_classes,*sorted(plugin_source.glob('*.java'))]);(plugin_classes/'plugins.config').write_text('Plugins>OmicsMatic Test, "Fixture Echo", Fixture_Echo\nPlugins>OmicsMatic Test, "Fixture Throws", Fixture_Throws\nPlugins>OmicsMatic Test, "Fixture Exit Zero", Fixture_Exit_Zero\n',encoding='utf8',newline='\n');run('fixture-plugin-jar',[jdk/('jar'+suffix),'--create','--file',plugins/'Fixture_Tests.jar','-C',plugin_classes,'.'])
  for label,command,expected in [('plugin-success','Fixture Echo',0),('plugin-caught-error','Fixture Throws',2),('plugin-exit-zero','Fixture Exit Zero',2)]:
   macro=requests/(label+'.ijm');macro.write_text('setBatchMode(true); File.saveString("retained complete file",getArgument()+"/results/plugin.txt"); run("'+command+'", ""); return "complete fixture";',encoding='utf8',newline='\n');req=copy.deepcopy(base);req.update(out=str(out/label),macro=str(macro),plugins_directory=str(plugins),plugins_provenance='Synthetic local interface-test fixtures; not an upstream scientific plugin',required_commands=[command],expected_outputs={'plugin.txt':dict(kind='FILE',minimum_bytes=1)},expected_return='complete fixture');request=requests/(label+'.json');save(request,req);run(label,[java,'-jar',jar,'--request',request],expected=expected,needle='PASS_' if expected==0 else 'FAIL2');status=json.loads((out/label/'analysis-status.json').read_bytes())
   if status['result_admitted']!=(expected==0):raise ValueError('Plugin error/exit handling differs: '+label)
  caught=json.loads((out/'plugin-caught-error/worker-status.json').read_bytes())
  if not any('original caught fixture exception' in s for s in caught['recorded_original_failures']):raise ValueError('Original caught command exception was not retained structurally')
  if (out/'plugin-exit-zero/worker-status.json').exists() or json.loads((out/'plugin-exit-zero/process-exit.json').read_bytes())['exit_code']!=0:raise ValueError('No-status zero-exit original fixture did not execute as intended')
  override=out/'override-plugins';override.mkdir()
  with zipfile.ZipFile(override/'Core_Override.jar','w') as z:z.writestr('ij/IJ.class',b'forbidden core override fixture')
  bad('core-override',lambda r:r.update(plugins_directory=str(override),plugins_provenance='Synthetic forbidden core override control'),2,'FAIL2')
  macro_error=json.loads((out/'macro-error/worker-status.json').read_bytes())
  if not macro_error['macro_error'] or 'Undefined' not in macro_error['original_error']:raise ValueError('Original interpreter error state/text was not retained')
  live=json.loads((out/'timeout/process-live.json').read_bytes())
  if os.name=='nt':
   probe=subprocess.run(['tasklist','/FI','PID eq '+str(live['pid']),'/FO','CSV','/NH'],capture_output=True,timeout=30)
   if ('"'+str(live['pid'])+'"').encode() in probe.stdout:raise ValueError('Timed-out original worker is still live')
  elif Path('/proc',str(live['pid'])).exists():raise ValueError('Timed-out original worker is still live')
  if any(pin(Path(n))!=v for n,v in before.items()):raise ValueError('Source/provider changed during complete controls')
  result=dict(status='PASS_COMPLETE_ORIGINAL_IMAGEJ154T_MACROS_PIXELS_METADATA_AND_REFUSALS',calls=calls,complete_original_cases=comparisons,original_macro_cases=len(comparisons),complete_original_outputs=sum(len(x) for x in comparisons.values()),refusal_cases=21,synthetic_plugin_success_cases=1,original_caught_command_exception_retained=True,zero_exit_without_terminal_status_unadmitted=True,original_source_patched=False,independent_reference_uses_project_interpreter_shim=False,external_plugins_qualified=False,paper_reproduction=False,new_tool_credit=0,whole_goal_complete=False);save(out/'COMPLETED.json',result);(out/'WARNINGS.md').write_text(ACTION+'\n',encoding='utf8',newline='\n');print(result['status'],flush=True);return 0
 except Exception as e:
  code=3 if isinstance(e,OSError) else 2;v=dict(status='SKIP3_ORIGINAL_PREREQUISITE_UNAVAILABLE' if code==3 else 'FAIL2_IMAGEJ154T_CONTROLS',cause=repr(e),traceback=traceback.format_exc(),calls=calls,required_action=ACTION)
  if owned:save(out/'FAILURE.json',v);(out/'WARNINGS_FAILURE.md').write_text(v['status']+'\n'+repr(e)+'\n'+ACTION+'\n',encoding='utf8',newline='\n')
  print(v['status'],repr(e),'\n'+ACTION,flush=True);return code
if __name__=='__main__':raise SystemExit(main())
