import gc
import scanpy as sc
from scipy.sparse import csr_matrix
from collections import Counter
import math
import pandas as pd
import os
from .utils import get_cell_boundary
import tifffile
#
class SpyrrowData(sc.AnnData):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        
# https://anndata.dynverse.org/reference/AnnData.html
# https://www.biorxiv.org/content/10.1101/2021.12.16.473007v1.full.pdf
def read_cosmx(files_path, read_transcripts=False): 
    
    files = os.listdir(files_path)
    
    #X:colname:gene;rowname:sample
    expr_file = next(each for each in files if 'exprMat_file' in each and not each.startswith('._'))
    data = pd.read_csv(os.path.join(files_path, expr_file))
    
    if 'cell' not in data.columns:
        data.index = ['c_'+str(each)[0]+'_'+str(each)[1] for each in zip(data['fov'],data['cell_ID'])]
        data = data.iloc[:,(data.columns.tolist().index('cell_ID')+1):]
    else:
        data.index = data['cell']
        data = data.iloc[:,(data.columns.tolist().index('cell')+1):]
        
    columns_to_drop = [col for col in data.columns if ('Control' in col) or ('Negative' in col)]
    data.drop(columns_to_drop, axis=1, inplace=True)

    # Convert to sparse matrix
    data = csr_matrix(data.values.astype(np.float64))
    
    #obs
    metadata_file = next(each for each in files if 'metadata_file' in each and not each.startswith('._'))
    obs = pd.read_csv(os.path.join(files_path, metadata_file))
    obs.index = obs['cell']
    
    #obsm: polygon
    polygons_file = next(each for each in files if 'polygons' in each and not each.startswith('._'))
    cell_boundary = get_cell_boundary(os.path.join(files_path, polygons_file),
                                      cell_id='cell', x_pixel='x_global_px', y_pixel='y_global_px')
    
    cell_boundary = pd.DataFrame({'boundary':list(cell_boundary.values())}, index=cell_boundary.keys())
    cell_boundary['boundary'] = [repr(each) for each in cell_boundary['boundary']]
    
    #align them
    intersect_cell = list(set(data.index).intersection(set(obs.index), set(cell_boundary.index)))
    data = data.loc[intersect_cell]
    obs = obs.loc[intersect_cell]
    cell_boundary = cell_boundary.loc[intersect_cell]
    obs['boundary'] = cell_boundary['boundary']
    
    del cell_boundary
    gc.collect()
    
    #var
    # nothing here
    
    #align them
    adata = SpyrrowData(X = data,
                        obs = obs)
                        #obsm = cell_boundary) it should be dict or numpy, test it latter, subset is aim
    del data, obs
    gc.collect()
    
    #uns
    transcripts = None
    if read_transcripts:
        tx_file = next(each for each in files if 'tx_file' in each and not each.startswith('._'))
        transcripts = pd.read_csv(os.path.join(files_path, tx_file))
    
    adata.uns = {}
    if read_transcripts and transcripts is not None:
        adata.uns = {'transcripts': transcripts}
        del transcripts
        gc.collect()
    
    return adata

def read_xenium(files_path):
    #expression
    adata = sc.read_10x_h5(files_path+'/cell_feature_matrix.h5')
    #obs
    obs = pd.read_csv(files_path+'/cells.csv.gz')
    obs.index = obs['cell_id']
    adata.obs = obs
    #cell boundary
    cell_boundary = get_cell_boundary(files_path+'/cell_boundaries.csv.gz',
                        cell_id='cell_id',x_pixel='vertex_x',y_pixel='vertex_y')
    cell_boundary = pd.DataFrame({'boundary':list(cell_boundary.values())}, index=cell_boundary.keys())
    cell_boundary['boundary'] = [repr(each) for each in cell_boundary['boundary']]
    adata.obs['boundary'] = cell_boundary['boundary']
    #transcripts
    transcripts = pd.read_csv(files_path+'/transcripts.csv.gz')
    adata.uns['transcripts'] = transcripts
    #background image
    mipurl = files_path+'/morphology_mip.ome.tif'
    with tifffile.TiffFile(mipurl) as tif:
        image = tif.series[0].levels[2].asarray()
        if tif.is_imagej:
            for key, value in tif.imagej_metadata.items():
                print(key, value)
    adata.uns['back_DAPI'] = image
    #img_he = Image.open(file_path+'match/'+'0004384_15-03566-2_adc_b'+'_trans_HE3.png')
    #img_he = np.array(img_he)
    #adata.uns['back_HE'] = img_he
    return adata

