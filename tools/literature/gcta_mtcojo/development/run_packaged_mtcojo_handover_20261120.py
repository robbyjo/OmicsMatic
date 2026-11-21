from pathlib import Path
import ast
original=Path('D:/projects/JLinAlg/build/run_packaged_gcta_qualification_20261120.py')
source=original.read_text(encoding='utf-8')
replacements={
"T=R/'build/gc-4';B=T/'build/gcta-qualification-1'":"T=R/'build/gc-5';B=T/'build/gcta-mtcojo-1'",
"oldjar=PRE/'normal-gradle-3/project-build/cli/omicsmatic-0.4.0.jar'":"oldjar=R/'build/gc-4/build/gcta-qualification-1/normal-gradle-2/project-build/cli/omicsmatic-0.4.0.jar'",
"==18":"==24",
"shutil.copytree(B/'summary-red-baseline-2/junit',deps)":"shutil.copytree(R/'build/gc-4/build/gcta-qualification-1/packaged-qualification-1/junit',deps)",
"('cli','GctaWorkflowCliTest*.class')":"('cli','GctaWorkflowCliTest*.class'),('cli','MtcojoConditionalTest*.class'),('cli','GctaMtcojoContractsTest*.class'),('cli','GctaMtcojoWorkflowTest*.class')",
"'org/jlinalg/cli/gcta1941-reml-captures.zip'":"'org/jlinalg/cli/mtcojo1941-workflow-captures.zip','org/jlinalg/genetics/mtcojo1941-conditional-originals.zip','org/jlinalg/cli/gcta1941-reml-captures.zip'",
"PackagedQualificationTests":"PackagedMtcojoTests",
"GCTA_QUALIFICATION_REGRESSION_PASS tests=18":"GCTA_MTCOJO_HANDOVER_PASS tests=24",
"18 packaged tests":"24 packaged tests",
"packaged_tests=18":"packaged_tests=24",
"PASS_18_PACKAGED_TESTS":"PASS_24_PACKAGED_TESTS"
}
for old,new in replacements.items():
    assert old in source,old
    source=source.replace(old,new)
start=source.index('with zipfile.ZipFile(oldjar)')
end=source.index("deps=O/'junit'",start)
source=source[:start]+'''with zipfile.ZipFile(oldjar) as old,zipfile.ZipFile(jar) as new:
    assert len(new.namelist())==len(set(new.namelist()))
    changed=[n for n in old.namelist() if n in new.namelist() and old.read(n)!=new.read(n)]
    added=sorted(set(new.namelist())-set(old.namelist()));removed=sorted(set(old.namelist())-set(new.namelist()))
    assert not removed and all(n.startswith('org/jlinalg/cli/Gcta') or n.startswith('org/jlinalg/genetics/MtcojoConditional') for n in changed+added),(changed,added,removed)
    payloads=dict(old_payloads_unchanged=len(old.namelist())-len(changed),changed_classes=changed,added=added,removed=removed)
save(O/'PAYLOADS.json',payloads);print('Producer payload transition',payloads,flush=True)
''' +source[end:]
ast.parse(source)
exec(compile(source,__file__,'exec'))
