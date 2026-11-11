from .utils import *
from .istar import *
from collections import Counter
import cv2
import matplotlib.pyplot as plt
import matplotlib.patches as patches
import tifffile as tiff
from matplotlib.colors import to_hex
#
Image.MAX_IMAGE_PIXELS = None
#
def remove_mask(pickle_or_fig, output, prefix = None, mask = None):
    if mask is None:
        mask = load_image(f'{prefix}mask.png') > 0
    else:
        mask = load_image(mask) > 0
    if pickle_or_fig.endswith('pickle'):
        cnts = load_pickle(pickle_or_fig)
        mask = np.array(Image.fromarray(mask).resize((cnts.shape[1],cnts.shape[0])))
        cnts[~mask] = np.nan
        plot_super(cnts, output)
    else:
        img = load_image(pickle_or_fig)
        mask = np.array(Image.fromarray(mask).resize((img.shape[1],img.shape[0])))
        img[~mask] = np.nan
        save_image(img, output)


def feature_cluster(pickle_file, n_clusters, method='km',min_cluster_size=20, prefix = None, mask = None):
    x = load_pickle(pickle_file)
    #
    if mask is None:
        mask = load_image(f'{prefix}mask.png') > 0
    else:
        mask = load_image(mask) > 0
    mask = np.array(Image.fromarray(mask).resize((x.shape[1],x.shape[0])))
    x[~mask] = np.nan
    x = x[np.newaxis,...]
    #
    labels, __ = initial_cluster(x, method=method, n_clusters=n_clusters)

    if min_cluster_size is not None:
        labels = relabel_small_connected(
                labels, min_size=min_cluster_size)
    return labels

def label_segmentation(prefix):
    pass

def pixle_to_spot(adata,pickle_or_fig, prefix):
    if pickle_or_fig.endswith('pickle'):
        cnts = load_pickle(pickle_or_fig)
    else:
        img = load_image(pickle_or_fig)
    #
    loc = pd.read_csv(prefix+'locs.tsv',sep='\t',index_col=0)
    loc['x'] = round(loc['x']/16).astype(np.int32)
    loc['y'] = round(loc['y']/16).astype(np.int32)
    clu_num = 8
    for lab in labels_list:
        loc_w = loc.copy()
        loc_w['pred'] = ''
        for i in range(loc.shape[0]):
            sub_x,sub_y = loc_w['x'][i],loc_w['y'][i]
            index = [[sub_y-1,sub_y+1,sub_y,sub_y,sub_y],[sub_x,sub_x,sub_x,sub_x+1,sub_x-1]]
            index = [(each[0],each[1]) for each in zip(index[0],index[1])]
            col_po = [lab[each[0],each[1]] for each in index]
            if max(Counter(col_po), key=Counter(col_po).get) == -1:
                continue
            loc_w['pred'][i] = str(max(Counter(col_po), key=Counter(col_po).get))
        loc_w.to_csv('/rsrch6/home/genomic_med/lwang22_lab/Yunhe/Gastric_Cancer/SpatialClusterScore/'+each+'/istar/istar_cluster_'+str(clu_num)+'.csv')
        clu_num += 1

#crop the spot region
def get_HE_spot(adata,image, shape = 'circle', size_ratio = 1):
    if type(image) is str:
        image = load_image(image)
    #
    crop_dict = {}
    radius = round(size_ratio*list(adata.uns['spatial'].values())[0]['scalefactors']['spot_diameter_fullres']/2)
    #
    image = np.array(image)
    for each,cell_index in zip(adata.obsm['spatial'],adata.obs_names):
        y1, x1, y2, x2 = each[0]-radius, each[1]-radius, each[0]+radius, each[1]+radius
        cropped_region = image[x1:x2, y1:y2]
        if (cropped_region.shape[0] != cropped_region.shape[1]) | (cropped_region.shape[0] != radius*2):
            continue
        if shape == 'circle':
            mask = np.zeros_like(cropped_region)
            mask = cv2.circle(mask, (radius,radius), radius, (255, 255, 255), thickness=-1)
            mask = mask.mean(2)<10
            cropped_region[mask] = [0,0,0]
            crop_dict[cell_index] = [(each,radius),cropped_region]
        elif shape == 'square':
            crop_dict[cell_index] = [(y1, x1, y2, x2),cropped_region]
    adata.uns['HE_spot'] = crop_dict
    adata.uns['raw_img_size'] = image.shape[:2]

#get feature from model
def get_featue_from_model(adata,feature_list):
    crop_dict = {}
    y_scale = adata.uns['raw_img_size'][0]/feature_list[0].shape[0]
    x_scale = adata.uns['raw_img_size'][1]/feature_list[0].shape[1]
    for key, value in adata.uns['HE_spot'].items():
        y1, x1, y2, x2 = value[0]
        y1, x1, y2, x2 = round(y1/y_scale), round(x1/x_scale), round(y2/y_scale), round(x2/x_scale)
        for each in feature_list:
            if key not in crop_dict:
                crop_dict[key] = []
            crop_dict[key].append(each[x1:x2, y1:y2].mean())
    df = pd.DataFrame.from_dict(crop_dict, orient='index')
    df = df.loc[adata.obs_names]
    adata.obsm['HE_feature'] = np.array(df)
#
def plot_crop_region(adata, output, figsize = (50,50)):
    #scale the polygon to hire
    fig,ax = plt.subplots(figsize=figsize)
    ax.imshow(list(adata.uns['spatial'].values())[0]['images']['hires'])
    scale_factor = list(adata.uns['spatial'].values())[0]['scalefactors']['tissue_hires_scalef']
    for key, value in adata.uns['HE_spot'].items():
        if len(value[0])==2:
            p2 = patches.Circle(scale_factor*value[0][0], scale_factor*value[0][1], edgecolor='none', facecolor='grey')
        else:
            #xy, width, height
            p2 = patches.Rectangle((value[0][0]*scale_factor,value[0][1]*scale_factor), scale_factor*(value[0][2]-value[0][0]), scale_factor*(value[0][3]-value[0][1]), edgecolor='none', facecolor='grey')
        ax.add_patch(p2)
    plt.savefig(output)
#
def plot_HE_spot(adata,output):
    #combine all croped region
    pixel_row = np.array([sorted(adata.obs['pixel_row'].unique()).index(each) for each in adata.obs['pixel_row']])
    pixel_col = np.array([sorted(adata.obs['pixel_col'].unique()).index(each) for each in adata.obs['pixel_col']])
    row_num = pixel_row.max()-pixel_row.min()+1
    col_num = pixel_col.max()-pixel_col.min()+1
    size = list(adata.uns['HE_spot'].values())[0][1].shape[0]
    img = Image.new('RGB',(size*row_num,size*col_num))
    for index, value in adata.uns['HE_spot'].items():
        img.paste(Image.fromarray(value[1])
                  ,(size*(pixel_row[np.where(adata.obs_names == index)[0][0]]),size*(pixel_col[np.where(adata.obs_names == index)[0][0]])))
    img.save(output)
#
def plot_feature_spot(adata,out_folder):
    #combine all croped region
    pixel_row = np.array([sorted(adata.obs['pixel_row'].unique()).index(each) for each in adata.obs['pixel_row']])
    pixel_col = np.array([sorted(adata.obs['pixel_col'].unique()).index(each) for each in adata.obs['pixel_col']])
    row_num = pixel_row.max()-pixel_row.min()+1
    col_num = pixel_col.max()-pixel_col.min()+1
    size = 10
    if not (os.path.exists(out_folder) and os.path.isdir(out_folder)):
        mkdir(out_folder)
    for i in range(adata.obsm['HE_feature'].shape[1]):
        img = Image.new('RGB',(size*row_num,size*col_num))
        cmap = plt.get_cmap('turbo')
        value = cmap(adata.obsm['HE_feature'][:,i])[...,:3]
        for j in range(len(value)):
            sub_img = Image.new('RGB',(size,size),color = tuple((value[j]*255).astype(np.int)))
            img.paste(sub_img
                    ,(size*(pixel_row[j]),size*(pixel_col[j])))
        img.save(out_folder+'/fea_'+str(i+1)+'.png')

def plot_cluster_spot(adata,group,output):
    col_map = {adata.obs[group].unique()[clu]:to_hex(plt.cm.tab20(clu)) for clu in range(len(adata.obs[group].unique()))}
    #combine all croped region
    pixel_row = np.array([sorted(adata.obs['pixel_row'].unique()).index(each) for each in adata.obs['pixel_row']])
    pixel_col = np.array([sorted(adata.obs['pixel_col'].unique()).index(each) for each in adata.obs['pixel_col']])
    row_num = pixel_row.max()-pixel_row.min()+1
    col_num = pixel_col.max()-pixel_col.min()+1
    size = 10
    img = Image.new('RGB',(size*row_num,size*col_num))
    index = 0
    for each in adata.obs[group]:
        sub_img = Image.new('RGB',(size,size),color = col_map[each])
        img.paste(sub_img
                ,(size*(pixel_row[index]),size*(pixel_col[index])))
        index+=1
    img.save(output)
