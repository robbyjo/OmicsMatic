from pathlib import Path
import ast,hashlib,importlib.util,json,subprocess
R=Path('D:/projects/JLinAlg');T=R/'build/mr-5';B=T/'build/twosamplemr-historical-1';O=R/'build/git-publish/20261121-historical-handover-87'
O.mkdir(parents=True,exist_ok=False)
spec=importlib.util.spec_from_file_location('inv',O.parent/'20261115-all-main-3/inventory.py');inv=importlib.util.module_from_spec(spec);spec.loader.exec_module(inv)
def save(path,value):
    with path.open('x',encoding='utf-8',newline='\n') as f:json.dump(value,f,indent=2);f.write('\n')
prior=json.loads((O.parent/'20261121-twosamplemr-clump-86/WORKTREES-EXPECTED.json').read_bytes())
states=[]
for block in subprocess.check_output(['git','worktree','list','--porcelain'],cwd=R).decode().strip().split('\n\n'):
    root=Path(block.splitlines()[0][9:]);state=inv.snapshot(root);states.append(state)
    if root.resolve()!=T.resolve():
        expected=next(x for x in prior if Path(x['root']).resolve()==root.resolve());assert expected==state,str(root)
assert len(states)==43 and len(prior)==42
save(O/'WORKTREES-BEFORE.json',states)
save(O/'PREDECESSOR-PRESERVATION.json',dict(preceding_worktrees_exact=42,total_worktrees=43,raw_historical_states_saved_in='docs/literature/handover-2026-11-21-worktrees',predecessor_commit='00b29b144709d2bcda23f6edabdc69af68818adb'))
source=T/'tools/literature/twosamplemr_clump/PackagedTwoSampleMrClumpTests.java'
text=source.read_text(encoding='utf-8').replace('PackagedTwoSampleMrClumpTests','PackagedTwoSampleMrHistoricalTests').replace('707','769')
text=text.replace('new String[]{','new String[]{"TwoSampleMrHistoricalCli","TwoSampleMrHistoricalContracts","TwoSampleMrHistoricalFiles","TwoSampleMrHistoricalAudit","TwoSampleMrHistoricalRuntime",')
text=text.replace('DiscoverySelectors.selectClass("org.jlinalg.cli.TwoSampleMrClumpTest")','DiscoverySelectors.selectClass("org.jlinalg.cli.TwoSampleMrClumpTest"),DiscoverySelectors.selectClass("org.jlinalg.cli.TwoSampleMrHistoricalTest")')
(T/'tools/literature/twosamplemr_historical/PackagedTwoSampleMrHistoricalTests.java').write_text(text,encoding='utf-8',newline='\n')
s=(R/'build/run_packaged_twosamplemr_clump_20261121.py').read_text(encoding='utf-8')
s=s.replace("T=R/'build/mr-4';B=T/'build/twosamplemr-clump-adapter-1'","T=R/'build/mr-5';B=T/'build/twosamplemr-historical-1'").replace("P=B/'normal-gradle-2'","P=B/'normal-gradle-3'").replace('707','769')
s=s.replace("'tools/literature/twosamplemr_clump/PackagedTwoSampleMrClumpTests.java'","'src/test/java/org/jlinalg/cli/TwoSampleMrHistoricalTest.java','tools/literature/twosamplemr_historical/PackagedTwoSampleMrHistoricalTests.java'")
s=s.replace("'org/jlinalg/cli/twosamplemr-clump-0.7.14.zip'","'org/jlinalg/cli/twosamplemr-clump-0.7.14.zip','org/jlinalg/cli/twosamplemr-historical.zip'")
s=s.replace("R/'build/mr-2/build/twosamplemr-workflow-1/normal-gradle-4/project-build/cli/omicsmatic-0.4.0.jar'","R/'build/mr-4/build/twosamplemr-clump-adapter-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar'").replace('22320','22335')
s=s.replace('PackagedTwoSampleMrClumpTests','PackagedTwoSampleMrHistoricalTests').replace('TWOSAMPLEMR_CLUMP','TWOSAMPLEMR_HISTORICAL')
ast.parse(s);(R/'build/run_packaged_twosamplemr_historical_handover_87.py').write_text(s,encoding='utf-8',newline='\n')
print('Prepared handover publication, preserved all 42 prior worktrees; 43 total',flush=True)
