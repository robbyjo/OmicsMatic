from pathlib import Path
import json
from handover_inventory_20261122_111 import pin,save
T=Path('D:/projects/JLinAlg/build/fu-1');B=T/'build/smartpca-2';old=B/'original-analysis-reference-1'
generator=(old/'generator.py').read_text();assert "hiprec='YES'" in generator
case=json.loads((old/'outlier-and-shrink/reference.json').read_bytes());assert case['parameters']['hiprec']=='YES' and case['original_coordinate_decimal_places']==6
line=next(row for row in case['original_evec'].splitlines() if 'sample000' in row);assert line.split()[1]=='-0.0052'
source=T/'build/smartpca-1/source-intake-1/original-source/src/eigensrc/smartpca.c'
assert 'getint (ph, "hiprecision:", &hiprec)' in source.read_text()
save(old/'PRECISION-METADATA-INVALIDATED.json',dict(status='RETAINED_ORIGINAL_RUNS_WITH_INCORRECT_PRECISION_METADATA_NOT_FOR_SIX_DECIMAL_QUALIFICATION',
 requested_unrecognized_key='hiprec',actual_recognized_key='hiprecision',source=pin(source),old_generator=pin(old/'generator.py'),
 actual_original_coordinates_decimal_places=4,old_metadata_claimed=6,
 native_calculation_not_shown_incorrect=True,old_native_comparison='analysis-check-1',original_outputs_modified=False,
 required_action='Retain all original runs. Use NEW output with the recognized source parameter and repeat unchanged original program comparisons.'))
path=Path('D:/projects/JLinAlg/build/build_smartpca_analysis_20261123_218.py');text=path.read_text()
text=text.replace("out=B/'analysis-check-1'","out=B/'analysis-check-2'")
text=text.replace("sources+[fixture.relative_to(T).as_posix()]","sources+[fixture.relative_to(T).as_posix(),'src/test/resources/org/jlinalg/genetics/smartpca-original-analysis-reference.zip']")
target=Path('D:/projects/JLinAlg/build/build_smartpca_analysis_20261123_219.py');assert not target.exists();target.write_text(text,encoding='utf-8')
print('Preserved first raw runs and failed comparisons; exact source option is hiprecision.')
