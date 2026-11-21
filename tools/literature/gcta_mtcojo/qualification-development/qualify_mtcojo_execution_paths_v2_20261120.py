from pathlib import Path
import ast,json,shutil
R=Path('D:/projects/JLinAlg');source=R/'build/qualify_mtcojo_execution_paths_20261120.py';receipt=R/'build/mtcojo-qualification-start-20261120-1/initial-execution-helper-failure.json'
with receipt.open('x',encoding='utf-8',newline='\n') as stream:json.dump(dict(status='FAILED_BEFORE_INPUT_PREPARATION_OR_TOOL_EXECUTION',helper=str(source),cause='New worktree has no build parent; mkdir without parents=True raised FileNotFoundError',analysis_executed=False,failed_helper_preserved=True),stream,indent=2);stream.write('\n')
text=source.read_text(encoding='utf-8');assert text.count('B.mkdir(exist_ok=False)')==1;text=text.replace('B.mkdir(exist_ok=False)','B.mkdir(parents=True,exist_ok=False)');ast.parse(text);exec(compile(text,__file__,'exec'))
