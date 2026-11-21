from pathlib import Path
import subprocess
R=Path('D:/projects/JLinAlg');B=R/'build/mr-3/build/twosamplemr-clump-1';text=(R/'build/prepare_twosamplemr_clump_controls_20261121.py').read_text().replace("O=B/'original-controls-1'","O=B/'original-controls-3'")
text=text.replace('def panel(prefix,bad=False):',"variants=sorted(variants,key=lambda row:(int(row[0]),row[2]))\ndef panel(prefix,bad=False):")
text=text.replace("(root/'effective-request.json').write_text", """old=B/'original-controls-2'/name/'inputs/reference'
    def decoded(prefix):
        bim=[line.split() for line in prefix.with_suffix('.bim').read_text().splitlines()];fam=prefix.with_suffix('.fam').read_bytes();raw=prefix.with_suffix('.bed').read_bytes();stride=(36+3)//4
        return fam,{row[1]:(row,raw[3+i*stride:3+(i+1)*stride]) for i,row in enumerate(bim)}
    assert decoded(old)==decoded(root/'inputs/reference'),'Per-variant genotype/allele/position or sample metadata changed'
    (root/'PANEL-ORDER-PROOF.json').write_text(json.dumps(dict(previous_invalid_panel=str(old),new_valid_order=True,samples_and_per_variant_metadata_and_genotypes_identical=True,original_invalid_panel_retained=True),indent=2)+'\\n',encoding='utf-8',newline='\\n')
    (root/'effective-request.json').write_text""")
path=B/'retry-sorted-panel.py';path.write_text(text,encoding='utf-8',newline='\n')
raise SystemExit(subprocess.run(['C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe','-X','utf8','-B',str(path)],cwd=R).returncode)
