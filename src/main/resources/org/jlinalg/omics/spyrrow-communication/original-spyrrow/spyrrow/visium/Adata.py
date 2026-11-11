import scanpy as sc
from collections import Counter
import math
#from .visual import plot_category, plot_continuous
#sc.pl.my_custom_plot = plot_category
class SpyrrowData(sc.AnnData):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        #get the scaled pixle level location
        self.obs['pixel_row'] = self.obsm['spatial'][:,1]
        self.obs['pixel_col'] = self.obsm['spatial'][:,0]
        scale_factor = list(self.uns['spatial'].values())[0]['scalefactors']['tissue_hires_scalef']
        #original
        row_ori = self.obs['pixel_row'].copy()*scale_factor
        #for row in Counter(self.obs['array_row']):
        #    index = self.obs['array_row']==row
        #    row_ori.loc[index] = \
        #        self.obs.loc[index]['pixel_row'].median()*scale_factor
        col_ori = self.obs['pixel_col'].copy()*scale_factor
        #for col in Counter(self.obs['array_col']):
        #    index = self.obs['array_col']==col
        #    col_ori.loc[index] = \
        #        self.obs.loc[index]['pixel_col'].median()*scale_factor
        #same direction
        row_num = self.obs['array_row'].max()-self.obs['array_row'].min()+1
        col_num = self.obs['array_col'].max()-self.obs['array_col'].min()+1
        row_step = (self.obs['pixel_row'].max() - self.obs['pixel_row'].min())/(row_num-1)
        col_step = (self.obs['pixel_col'].max() - self.obs['pixel_col'].min())/(col_num-1)
        row_range = [scale_factor*(self.obs['pixel_row'].min()+i*row_step) for i in range(row_num)]
        col_range = [scale_factor*(self.obs['pixel_col'].min()+i*col_step) for i in range(col_num)]
        #self.obs['pixel_row'] = [row_range[index - self.obs['array_row'].min()] for index in self.obs['array_row']]
        #self.obs['pixel_col'] = [col_range[index - self.obs['array_col'].min()] for index in self.obs['array_col']]
        self.obs['pixel_row'] = [min(row_range, key=lambda x: abs(x - each)) for each in row_ori]
        self.obs['pixel_col'] = [min(col_range, key=lambda x: abs(x - each)) for each in col_ori]
        #transpose
        #
        self.uns['hexagon'] = {}
        self.uns['hexagon']['gridsize'] = (math.ceil((max(self.obs['array_col']) - min(self.obs['array_col']))/2),
                                           math.ceil((max(self.obs['array_row']) - min(self.obs['array_row']))/2))
        self.uns['hexagon']['array_extent'] = (min(self.obs['array_col']),max(self.obs['array_col']) + 1,
                                               min(self.obs['array_row']),max(self.obs['array_row']) + 1)
        self.uns['hexagon']['pixel_extent'] = (min(self.obs['pixel_col']),max(self.obs['pixel_col']) + col_step ,
                                               min(self.obs['pixel_row']),max(self.obs['pixel_row']) + row_step)
        #
