from pathlib import Path
import subprocess
R=Path('D:/projects/JLinAlg');B=R/'build/mr-3/build/twosamplemr-clump-1';text=(R/'build/prepare_twosamplemr_clump_controls_20261121.py').read_text().replace("O=B/'original-controls-1'","O=B/'original-controls-2'")
path=B/'retry-controls.py';path.write_text(text,encoding='utf-8',newline='\n')
raise SystemExit(subprocess.run(['C:/Users/User/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe','-X','utf8','-B',str(path)],cwd=R).returncode)
