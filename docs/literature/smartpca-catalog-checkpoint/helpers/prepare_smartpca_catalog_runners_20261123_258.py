from pathlib import Path
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1'
for old,new in [('run_mrpresso_catalog_checks_20261123_181.py','run_smartpca_catalog_checks_20261123_258.py'),('run_mrpresso_catalog_phase_20261123_181.py','run_smartpca_catalog_phase_20261123_258.py'),('run_mrpresso_catalog_controls_20261123_181.py','run_smartpca_catalog_controls_20261123_258.py'),('run_mrpresso_catalog_ui_20261123_181.py','run_smartpca_catalog_ui_20261123_258.py'),('check_mrpresso_catalog_website_20261123_183.py','check_smartpca_catalog_website_20261123_258.py')]:
 code=(R/'build'/old).read_text(encoding='utf-8').replace('mrpresso-catalog-178','smartpca-catalog-255').replace('mrpresso_catalog','smartpca_catalog').replace('close_mrpresso_catalog.py','close_smartpca_catalog.py').replace('MRPRESSO','SMARTPCA')
 code=code.replace('build/mrpresso-1/normal-build-4','build/smartpca-4/normal-build-2').replace('verify_mrpresso_workflow_checkpoint.py','verify_smartpca_fst_checkpoint.py').replace('plan-windows-2/result','plan-windows-1/result')
 (R/'build'/new).write_text(code,encoding='utf-8',newline='\n')
attrs=T/'.gitattributes'
with attrs.open('a',encoding='utf-8',newline='\n') as f:f.write('''
# Exact smartpca six-role native-family catalog admission and validation.
tools/literature/smartpca_catalog/** -text whitespace=cr-at-eol
tools/literature/audit_smartpca_catalog_sources.py -text whitespace=cr-at-eol
tools/literature/close_smartpca_catalog.py -text whitespace=cr-at-eol
tools/literature/verify_smartpca_catalog_checkpoint.py -text whitespace=cr-at-eol
docs/literature/smartpca-catalog-sources/** -text whitespace=cr-at-eol
docs/literature/smartpca-catalog-checkpoint/** -text whitespace=cr-at-eol
docs/literature/SMARTPCA_CATALOG_CHECKPOINT_2026-11-23.md -text whitespace=cr-at-eol
''')
print('Prepared owned runners; no canonical catalog edits.')
