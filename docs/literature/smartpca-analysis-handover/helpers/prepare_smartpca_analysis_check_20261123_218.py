from pathlib import Path
R=Path('D:/projects/JLinAlg');T=R/'build/fu-1';B=T/'build/smartpca-2'
import shutil
shutil.copyfile(B/'original-analysis-reference-1/original-analysis-reference.zip',T/'src/test/resources/org/jlinalg/genetics/smartpca-original-analysis-reference.zip')
source=(R/'build/build_smartpca_extensions_20261123_216.py').read_text()
source=source.replace("out=B/'component-check-1'","out=B/'analysis-check-1'")
source=source.replace("('SmartpcaOutliers','SmartpcaShrinkage')","('SmartpcaOutliers','SmartpcaShrinkage','SmartpcaAnalysis')")
source=source.replace("['src/test/java/org/jlinalg/genetics/SmartpcaExtensionsTest.java']","['src/test/java/org/jlinalg/genetics/SmartpcaExtensionsTest.java','src/test/java/org/jlinalg/genetics/SmartpcaAnalysisReferenceTest.java']")
source=source.replace("'org.jlinalg.genetics.SmartpcaExtensionsTest'","'org.jlinalg.genetics.SmartpcaAnalysisReferenceTest'")
target=R/'build/build_smartpca_analysis_20261123_218.py';assert not target.exists();target.write_text(source,encoding='utf-8')
print('Prepared isolated native analysis comparison checks.')
