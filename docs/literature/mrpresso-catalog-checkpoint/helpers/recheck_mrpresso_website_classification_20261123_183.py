from pathlib import Path
import subprocess,sys
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/mrpresso-catalog-178';old=R/'build/check_mrpresso_catalog_website_20261123_182.py'
(B/'website/classifier-original.py').write_bytes(old.read_bytes())
text=old.read_text().replace("D=T/'build/mrpresso-catalog-178/website';D.mkdir()","D=T/'build/mrpresso-catalog-178/website-2';D.mkdir()")
text=text.replace("assert code!=0 and 'lacks a scientific citation panel' in combined and 'broad-gsea-tools' in combined",
 "save(D/'PROCESS-RESULT.json',dict(exit_code=code,seconds=time.monotonic()-start))\nassert code!=0 and 'Markdown vignette lacks scientific citations: broad-gsea-tools.md' in combined")
path=R/'build/check_mrpresso_catalog_website_20261123_183.py';path.write_text(text,encoding='utf-8')
raise SystemExit(subprocess.call([sys.executable,'-X','utf8','-B',str(path)],cwd=T))
