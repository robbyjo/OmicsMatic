from pathlib import Path
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1'
for stem in ('run_squidpy_catalog_ui_20261122_149.py','close_squidpy_owned_browser_20261122_149.cjs','run_squidpy_catalog_phase_20261122_149.py','run_squidpy_catalog_checks_20261122_149.py','run_squidpy_catalog_controls_20261122_149.py'):
 text=(R/'build'/stem).read_text();text=text.replace('build/squidpy-catalog-149','build/mrpresso-catalog-178').replace('squidpy_catalog','mrpresso_catalog').replace('close_squidpy_owned_browser_20261122_149.cjs','close_mrpresso_owned_browser_20261123_181.cjs')
 text=text.replace('close_squidpy_catalog.py','close_mrpresso_catalog.py').replace('verify_squidpy_workflow_checkpoint.py','verify_mrpresso_workflow_checkpoint.py')
 text=text.replace('build/squidpy-1/workflow-normal-build-145/project-build/cli/omicsmatic-0.4.0.jar','build/mrpresso-1/normal-build-4/project-build/cli/omicsmatic-0.4.0.jar')
 text=text.replace('plan-windows-2/result','plan-windows-1/result').replace('PASS_SQUIDPY_UI_AND_GRACEFUL_OWN_BROWSER_CLOSURE','PASS_MRPRESSO_UI_AND_GRACEFUL_OWN_BROWSER_CLOSURE')
 target=stem.replace('squidpy','mrpresso').replace('20261122_149','20261123_181')
 (R/'build'/target).write_text(text,encoding='utf-8')
print('Prepared owned-profile UI, phase, cross-host tests and refusal runners; no existing browser/runtime was modified.')
