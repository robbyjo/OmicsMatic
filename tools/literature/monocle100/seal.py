"""Seal complete synthetic/source qualification evidence; omit installed runtimes and build products."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,zipfile
from setup import ROOT,pin,require,save

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--evidence',type=Path,required=True);args=parser.parse_args();base=args.evidence.resolve();folder=ROOT/'docs/literature/monocle100-weights-checkpoint';folder.mkdir(parents=True,exist_ok=False)
    normal=json.loads((base/'normal-gradle-3/RESULT.json').read_bytes());require(normal['status']=='PASS_NORMAL_JAR_MONOCLE100_SOURCE_CONTROLS' and normal['tests']==636,'Incomplete normal build')
    require(json.loads((base/'packaged-1/RESULT.json').read_bytes())['status']=='PASS_PACKAGED_MONOCLE100_636_TESTS_THREE_JVMS','Incomplete packaged tests')
    require(json.loads((base/'examples-1/RESULT.json').read_bytes())['status']=='PASS_THREE_HOST_PUBLIC_WEIGHT_TO_MORAN_EXAMPLES','Incomplete examples')
    require(json.loads((base/'context-1/RESULT.json').read_bytes())['status']=='PASS_FRESH_MONOCLE9_AND_RETAINED_CATALOG_BOTH_HOSTS_WITH_THREE_SETUP_REFUSALS','Incomplete current contexts')
    for name,wanted in json.loads((base/'normal-gradle-3/STARTED.json').read_bytes())['source_inputs'].items():require(pin(ROOT/name)==wanted,'Changed qualified build source: '+name)
    files={};omitted={}
    excluded={'project-build','project-cache','test-classes','R-lib','source-stage','home','tmp','cache','__pycache__'}
    for current,dirs,names in os.walk(base):
        for name in list(dirs):
            if name in excluded:
                dirs.remove(name);omitted['current/'+(Path(current)/name).relative_to(base).as_posix()]='Installed runtime, staged/compiler output, cache or generated classes; retain privately.'
        for name in sorted(names):
            path=Path(current)/name
            if path.suffix=='.class':omitted['current/'+path.relative_to(base).as_posix()]='Generated class; source and strict compile/build evidence retained.';continue
            files['current/'+path.relative_to(base).as_posix()]=path
    for attempt in ['normal-gradle-1','normal-gradle-2','normal-gradle-3']:
        for path in (base/attempt/'project-build/test-results/test').glob('TEST-*.xml'):files['current/'+attempt+'/raw-test-results/'+path.name]=path
    source=ROOT.parent/'literature-review/monocle-runtime/source-intake-1/1.0.0'
    files['upstream/monocle3-source.tar.gz']=source/'monocle3-source.tar.gz';files['upstream/SOURCE.json']=source/'SOURCE.json'
    helper=ROOT.parent/'prepare_monocle100_example_20261121.py';require(helper.is_file(),'Retain the one-time example producer');files['development/'+helper.name]=helper
    inventory={};blobs={}
    for name,path in sorted(files.items()):
        require(path.is_file() and not path.is_symlink() and path.stat().st_size<=64*1024*1024,'Unsupported saved file: '+name)
        identity=pin(path);inventory[name]=identity;blobs.setdefault(identity['sha256'],path)
    with zipfile.ZipFile(folder/'checks.zip','x',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
        for sha,path in sorted(blobs.items()):archive.write(path,'blobs/'+sha)
    save(folder/'INVENTORY.json',inventory)
    producer=base/'normal-gradle-3/project-build/cli/omicsmatic-0.4.0.jar'
    save(folder/'PRIVATE-TRANSFER.json',dict(not_a_complete_machine_backup=True,current_private_evidence=str(base),current_private_runtime=str(base/'install-1/R-lib'),producer_jar=dict(path=str(producer),**pin(producer)),prior_manifest='docs/literature/twosamplemr-catalog-checkpoint/PRIVATE-TRANSFER.json',omitted=omitted,action='Separately transfer permitted ignored runtimes, full text, author source, producer JAR and complete private evidence with SHA256 verification. Preserve all48 worktrees and failed/null/superseded evidence. Public source archives and synthetic controls do not replace a runtime or machine backup.'))
    (folder/'.gitattributes').write_bytes(b'* -text whitespace=cr-at-eol\n')
    shutil.copyfile(source/'source/LICENSE',folder/'MONOCLE-LICENSE-DECLARATION.txt')
    shutil.copyfile(Path('C:/Program Files/R/R-4.6.1/share/licenses/MIT'),folder/'LICENSE-MIT.txt')
    shutil.copyfile(Path('C:/Program Files/R/R-4.6.1/share/licenses/GPL-2'),folder/'LICENSE-GPL-2.txt')
    (folder/'UPSTREAM_CREDITS.md').write_text('''# Original sources and licenses

The retained original Monocle3 1.0.0 source archive is commit004c096f3a3bdaf1e6709a65fb36f40ba775f1ab from https://github.com/cole-trapnell-lab/monocle3,166 exact members. Its DESCRIPTION declares MIT + file LICENSE; the original declaration names2019 Cole Trapnell. The original declaration and complete MIT text are included. Original source/license bytes are retained without relicensing them as project-authored code.

Original spdep1.4-1 source is the official CRAN archive https://cran.r-project.org/src/contrib/Archive/spdep/spdep_1.4-1.tar.gz, SHA2564d0beca3b8b696373a74670f916cca3e77cbe97d002f3be62f172815774f5588. Its DESCRIPTION declares GPL (>=2). The archive retains original copyright/license declarations; complete GPL2 text is included. The selected installed lm.morantest body/formals match the source in that archive. Original R and all installed dependency binaries are separately provisioned, not bundled here.

RANN2.6.2, Matrix1.6-5, slam0.1-55, igraph2.0.3 and R4.3.3 supply the declared original runtime context. Their version/source boundaries are recorded; this packet does not claim all dependency source or a historical paper-era environment is authenticated. New project Java/scripts are GPL-3.0-or-later under the project license. No current author pipeline source or primary full-text article is redistributed or executed by these controls. All new analysis inputs are synthetic.
''',encoding='utf-8',newline='\n')
    (folder/'WARNINGS.md').write_text('''# Warnings and required actions

**PREPARED-INPUT STAGE ONLY.** Java selects neighbors and constructs original-edition weights from supplied complete original rankings/graph state. It does not learn neighbors/graphs, prepare paper responses/covariates or reproduce P021. Preserve source-selected self-links; the current residual-Moran method refuses them.25 successful original graphs and eight original failures are retained. Catalog credit remains zero for this component.

**SKIP3: DEPENDENT ANALYSIS SKIPPED.** Provide the named exact permitted runtime, input or accessible path, or retain the skip if installation/execution is disallowed. Private downloads/installs remain authorized where permitted. The native weight/bridge example itself needs no R. Missing installation is not permission to substitute an edition.

**FAIL2: NO COMPLETE RESULT.** Keep raw/partial/null/failed outputs and original warnings/errors. Correct the named source, population, numerical, capacity or process cause and retry NEW output. Do not change ties, drop cells/genes or repair identifiers to force admission. Require complete markers and the correct weight/test admission flag; a weight graph alone is not a statistical result.

Original RDS objects retain full binary values; rounded numeric JSON views are not exact weight oracles.17-digit values exported from all25 original listw objects are retained separately. Failed dense construction, named-list transport assertions and rounded-JSON assertions remain alongside the final successful controls. No original scientific source was patched.

**PRIVATE TRANSFER REQUIRED.** GitHub is not a machine backup. Transfer permitted runtimes, full text, author source, compiled producers and complete private evidence separately using recorded SHA256 values. Preserve all48 worktrees. Both literature reports remain off Pages; the inherited full-site citation failure remains open. No release, whole-Monocle, complete author pipeline or whole-goal completion is claimed.
''',encoding='utf-8',newline='\n')
    (folder/'README.md').write_text('''# Saved Monocle1.0.0 weight-stage checkpoint

Run `python -B tools/literature/monocle100/verify.py --out build/NEW-saved-check` with Python3.11+ without `-O`. Add `--restore` to extract evidence into NEW output. The reader verifies current source/canonical identities, all838 catalog manifest members, archive populations, complete original-source inventory, exact binary-weight projections and recorded normal/three-host/source/context qualification. It executes no Git, Java, R, XML parsing or scientific model. It does not authenticate a producer or repeat original fitting.

The packet retains33 original controls (25 successful/eight failed), raw failed/successful build and test records, actual three-host examples, original source archives/licenses and protected-library before/after hashes.636 tests pass per normal/packaged JVM, with all22378 preceding JAR payloads exact. The public example preserves74 binary64 weights and compares21 original-spdep fields for all three genes. Both-host fresh source/cumulative catalog and three setup refusal controls pass. New family credit is zero; counts stay144 scoped,1630 reviewed,1092 pending.

`INVENTORY.json` maps logical paths to SHA256-addressed members in `checks.zip`. Identical bytes are stored once. Installed runtimes, generated builds/classes/caches and large producers remain private, listed in `PRIVATE-TRANSFER.json`. The first unlogged manual example is retained as an earlier attempt; the formal three-host example supplies captured process transcripts and exit statuses. Failed compilation/harness/source-control attempts remain retained, never promoted as scientific results.

Read [warnings and required actions](WARNINGS.md), the [current checkpoint](../MONOCLE100_CHECKPOINT_2026-11-21.md), [API guide](../MONOCLE100_WEIGHTS.md) and [public example](../../../examples/monocle100-weights/README.md). The whole goal remains active and unfinished. Both reports stay off Pages; no release is created.
''',encoding='utf-8',newline='\n')
    previous=ROOT.parent/'git-publish/20261121-twosamplemr-catalog-91/CANONICAL-AFTER.json';canonical=json.loads(previous.read_bytes());require(len(canonical)==30,'Different preceding canonical population')
    for name,wanted in canonical.items():require(pin(ROOT/name)==wanted,'Changed preceding canonical file: '+name)
    sources=[path for pattern in ['src/main/java/org/jlinalg/**/Monocle100*.java','src/test/java/org/jlinalg/cli/Monocle100*.java','src/test/resources/org/jlinalg/singlecell/monocle100/*','tools/literature/monocle100/*','examples/monocle100-weights/*'] for path in ROOT.glob(pattern) if path.is_file()]
    sources += [ROOT/name for name in ['docs/literature/MONOCLE100_WEIGHTS.md','docs/literature/MONOCLE100_CHECKPOINT_2026-11-21.md','docs/literature/HANDOVER_CURRENT.md','src/test/resources/org/jlinalg/spatial/regression-moran/PROVENANCE.json']]
    packet_files={path.name:pin(path) for path in folder.iterdir() if path.is_file()}
    profile=dict(status='PASS_MONOCLE100_NATIVE_WEIGHT_STAGE_AND_SOURCE_CHECKED_BRIDGE',source_inputs={path.relative_to(ROOT).as_posix():pin(path) for path in sorted(set(sources))},canonical_unchanged=canonical,packet_files=packet_files,logical_files=len(inventory),unique_blobs=len(blobs),tests_per_JVM=636,JVMs=3,source_controls=33,source_successes=25,source_errors=8,worktrees_preserved=48,new_catalog_credit=0,whole_goal_complete=False)
    save(folder/'PROFILE.json',profile);print('SEALED',len(inventory),'logical files;',len(blobs),'blobs;',pin(folder/'checks.zip')['bytes'],'archive bytes',flush=True);return 0

if __name__=='__main__':raise SystemExit(main())
