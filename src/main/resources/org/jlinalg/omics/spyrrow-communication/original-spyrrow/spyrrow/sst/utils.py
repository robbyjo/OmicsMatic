import pandas as pd
import pickle
import os
#import tifffile
import tifffile as tiff
import numpy as np
from einops import reduce
from PIL import Image
from matplotlib.colors import to_hex
import matplotlib.pyplot as plt
import matplotlib.colors as mcolors
import matplotlib.patches as mpatches
#
#kwargs['cell_id','x_pixel','y_pixel']
def get_cell_boundary(polygons_csv,**kwargs):
    boundary = pd.read_csv(polygons_csv)
    if isinstance(kwargs['cell_id'], list):
        boundary['by'] = ['c_'+str(each[0])+'_'+str(each[1]) for each in zip(boundary[kwargs['cell_id'][0]],boundary[kwargs['cell_id'][1]])]
        grouped = boundary.groupby('by')
    else:
        grouped = boundary.groupby(kwargs['cell_id'])
    return {cID: [(loc[0],loc[1]) for loc in zip(group[kwargs['x_pixel']],group[kwargs['y_pixel']])] for cID, group in grouped}

def mkdir(path):
    dirname = os.path.dirname(path)
    if dirname != '':
        os.makedirs(dirname, exist_ok=True)

def save_pickle(x, filename):
    mkdir(filename)
    with open(filename, 'wb') as file:
        pickle.dump(x, file)


def load_pickle(filename, verbose=True):
    with open(filename, 'rb') as file:
        x = pickle.load(file)
    return x

def load_image(filename, verbose=False, level = 2):
    if filename.endswith('.ome.tiff') or filename.endswith('.ome.tif'):
        with tiff.TiffFile(filename) as tif:
            img = tif.series[0].levels[level].asarray()
    elif filename.endswith('.tiff'):
        img = tiff.imread(filename)
    else:
        img = Image.open(filename)
        img = np.array(img)
    if img.ndim == 3 and img.shape[-1] == 4:
        img = img[..., :3]  # remove alpha channel
    if verbose:
        print(f'Image loaded from {filename}')
    return img

def save_image(img, filename):
    mkdir(filename)
    Image.fromarray(img).save(filename)
    print(filename)

def get_color_map(annotation):
    return {annotation[clu]:to_hex(plt.cm.tab20(clu)) for clu in range(len(annotation))}

def plot_colorbar(adata, group = None, feature = None, col_map = None, cmap = 'turbo', output = None):
    if group is not None or col_map is not None :
        if col_map == None:
            col_map = get_color_map(adata.obs[group].unique())
        plt.figure(figsize=(5, len(col_map)))  # Adjust the figure size as needed
        bars = plt.barh(range(len(col_map)), [1]*len(col_map), color=[col_map[key] for key in col_map])
        # Add the keys as labels on the y-axis
        plt.yticks(range(len(col_map)), list(col_map.keys()))
        plt.ylim(-1, len(col_map)+1)
    else:
        plt.figure(figsize=(10, 2))
        plt.imshow(np.linspace(0, 100, 50).reshape(1, -1), cmap=cmap, aspect='auto')
        plt.axis('off')
    plt.gca().axes.get_xaxis().set_visible(False)
    plt.gca().spines['top'].set_visible(False)
    plt.gca().spines['right'].set_visible(False)
    plt.gca().spines['bottom'].set_visible(False)
    plt.gca().spines['left'].set_visible(False)
    plt.savefig(output)
