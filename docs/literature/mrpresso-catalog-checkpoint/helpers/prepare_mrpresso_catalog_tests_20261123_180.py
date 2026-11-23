from pathlib import Path
T=Path('D:/projects/JLinAlg/build/fu-1');source=(T/'tools/literature/squidpy_catalog/test_transaction.py').read_text()
source=source.replace('squidpy_catalog.primary','mrpresso_catalog.primary').replace('build/squidpy-catalog-149/transaction-tests','build/mrpresso-catalog-178/transaction-tests')
(T/'tools/literature/mrpresso_catalog/test_transaction.py').write_text(source,encoding='utf-8')
print('Prepared six inherited rollback/concurrent-write/path-boundary tests in the owned MR-PRESSO test prefix.')
