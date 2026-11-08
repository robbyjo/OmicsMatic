import warnings
warnings.filterwarnings('ignore')
import os,csv,re, time
import pickle
import random
import pandas as pd
import numpy as np
import geopandas as gpd
import scanpy as sc
import cv2
import math
from collections import Counter
import tables
from IPython.display import Image
import seaborn as sns
from scanpy import read_10x_h5
from scipy import stats
import scipy.sparse
import scipy.sparse as sp_sparse
from scipy.sparse import issparse
from scipy.ndimage import gaussian_filter
import PIL
from PIL import Image as IMAGE
import matplotlib.pyplot as plt
from matplotlib.patches import Polygon
from matplotlib.colors import to_hex
import shapely
from shapely.geometry import Polygon as ShapelyPolygon
from tqdm import tqdm
import inspect
import re
from .register import adjust_edge
#import time
#
def get_plot_limit(boundary,keys):
    min_x, min_y, max_x, max_y = 1000000000,1000000000,-1000000000,-1000000000
    for each in keys:
        boundary[each]
        min_x, min_y, max_x, max_y = min(np.array(boundary[each])[:,0].tolist()+[min_x]), \
                                    min(np.array(boundary[each])[:,1].tolist()+[min_y]), \
                                    max(np.array(boundary[each])[:,0].tolist()+[max_x]), \
                                    max(np.array(boundary[each])[:,1].tolist()+[max_y])
    return min_x, min_y, max_x, max_y
#
def hex_to_rgb(value):
    value = value.lstrip('#')
    lv = len(value)
    return tuple(int(value[i:i + lv // 3], 16) for i in range(0, lv, lv // 3))
#
def darken_color(hex_color, factor=0.7):
    # Convert the hexadecimal color to RGB components
    hex_color = hex_color.lstrip('#')
    rgb_color = tuple(int(hex_color[i:i+2], 16) for i in (0, 2, 4))
    # Darken the color by reducing the RGB components
    new_rgb = tuple(int(component * factor) for component in rgb_color)
    # Convert the darkened RGB components back to hexadecimal format
    dark_hex_color = '#{:02X}{:02X}{:02X}'.format(*new_rgb)
    return dark_hex_color
#
def get_color_map(annotation):
    return {annotation[clu]:to_hex(plt.cm.tab20(clu)) for clu in range(len(annotation))}
#
def do_eval(boundary_string):
    if 'array' in boundary_string:
        #match = re.match(r'array\((.*?)\)', boundary_string)
        return eval(boundary_string.replace('array(','').replace(')',''))
    else:
        return eval(boundary_string)
#
#######for FOV array visualization
def plot_cell_patch(adata,output,group=None,col_map=None,figure_size = (200, 200),
                    FOV=None,FOV_col_num = 1, back = 'black', back_img = None, move = [0,0,0,0],img_scale = 1,
                    Only_boundary = False, boundary_width = 1, **kwargs):
    #
    cell_index = adata.obs_names.tolist()
    if group is not None:
        if col_map is None:
            col_map = get_color_map(adata.obs[group].unique())
        cols = {cID:col_map[adata.obs.loc[cID][group]] for cID in cell_index}
    else:
        cols = {cID:'#FFFFFF' for cID in cell_index}
    #
    cell_boundary = dict(adata.obs['boundary'])
    cell_boundary = {each:do_eval(cell_boundary[each]) for each in cell_index}
    #
    if FOV is None:
        fig ,ax = plt.subplots(figsize=figure_size)
        if back_img is not None:
            if len(adata.uns[back_img].shape)==2:
                img = adata.uns[back_img].copy()
                img = IMAGE.fromarray(img).resize((round(img_scale*img.shape[1]),round(img_scale*img.shape[0])))
                ax.imshow(np.array(img), cmap='gray')
            else:
                #empty_blank = (np.ones((adata.uns[back_img].shape[0],move[0], 3))*0).astype(np.int)
                #combined_matrix = np.concatenate((empty_blank,adata.uns[back_img]), axis=1)
                #if move[1]>0:
                #    empty_blank = (np.ones((move[1], combined_matrix.shape[1], 3))*0).astype(np.int)
                #    combined_matrix = np.concatenate((empty_blank,combined_matrix), axis=0)
                #else:
                #    combined_matrix = combined_matrix[-move[1]:,:,:]
                #img = combined_matrix.copy()
                img = adjust_edge(adata.uns[back_img],move)
                img = IMAGE.fromarray(img.astype(np.uint8)).resize((round(img_scale*img.shape[1]),round(img_scale*img.shape[0])))
                ax.imshow(np.array(img))
        else:
            min_x, min_y, max_x, max_y = get_plot_limit(cell_boundary, cell_index)
            ax.set_xlim((min_x,max_x))
            ax.set_ylim((min_y,max_y))
            ax.set_facecolor(back)
        progress_bar = tqdm(cell_index, desc="Processing", ncols=100)
        for cID in progress_bar:
            if 'subset' in kwargs:
                if adata.obs.loc[cID][group] not in kwargs['subset']:
                    continue
            if Only_boundary:
                p2 = Polygon(cell_boundary[cID], facecolor = 'none', edgecolor = cols[cID], lw = boundary_width)
            else:
                p2 = Polygon(cell_boundary[cID], facecolor = cols[cID])
            ax.add_patch(p2)
        progress_bar.close()
    else:
        row_num = len(FOV)//FOV_col_num + int(not len(FOV)%FOV_col_num==0)
        fig, axs = plt.subplots(row_num, FOV_col_num,figsize=figure_size)
        axs = np.array([axs]).flatten()
        pointer_index = 0
        for fov in FOV:
            fov_index = adata.obs_names[adata.obs[kwargs['FOV_col']]==fov].tolist()
            progress_bar = tqdm(fov_index, desc='FOV: '+str(fov)+" is processing", ncols=100)
            #
            min_x, min_y, max_x, max_y = get_plot_limit(cell_boundary,fov_index)
            axs[pointer_index].set_xlim((min_x,max_x))
            axs[pointer_index].set_ylim((min_y,max_y))
            axs[pointer_index].set_facecolor('black')
            #
            for cID in progress_bar:
                if 'subset' in kwargs:
                    if adata.obs.loc[cID][group] not in kwargs['subset']:
                        continue
                if Only_boundary:
                    p2 = Polygon(cell_boundary[cID], facecolor = 'none', edgecolor = cols[cID], lw = boundary_width)
                else:
                    p2 = Polygon(cell_boundary[cID], facecolor = cols[cID])
                axs[pointer_index].add_patch(p2)
            progress_bar.close()
            pointer_index += 1
    plt.savefig(output)

#
def plot_RNA_on_cell(adata,output,group=None,col_map=None,figure_size = (200, 200),
                    RNA_name = None, RNA_color = None, RNA_size = 3,scale_size=False,
                    inside_dark_factor = 0.5, patch_line_width = 2, FOV=None,FOV_col_num = 1, back = 'white' , **kwargs):
    cell_index = adata.obs_names.tolist()
    if group is not None:
        if col_map is None:
            col_map = get_color_map(adata.obs[group].unique())
        cols = {cID:col_map[adata.obs.loc[cID][group]] for cID in cell_index}
    else:
        cols = {cID:'#FFFFFF' for cID in cell_index}
    #
    if group is not None:
        col_map_inside = col_map.copy()
        darker_cell = list(col_map_inside.keys()) if 'RNA_within_celltype' not in kwargs else kwargs['RNA_within_celltype']
        for cell in darker_cell:
            col_map_inside[cell] = darken_color(col_map_inside[cell],factor = inside_dark_factor)
        cols_line = {cID:col_map_inside[adata.obs.loc[cID][group]] for cID in cell_index}
    else:
        cols_line = {cID:darken_color('#FFFFFF',factor = inside_dark_factor) for cID in cell_index}
    #
    cell_boundary = dict(adata.obs['boundary'])
    cell_boundary = {each:do_eval(cell_boundary[each]) for each in cell_index}
    #
    if scale_size:
        RNA_size_dict = {}
        for tra in RNA_name:
            if isinstance(adata.X, np.ndarray):
                sub_e = adata.X[:,adata.var_names == tra]
            else:
                sub_e = adata.X[:,adata.var_names == tra].toarray()
            sub_e = RNA_size*(sub_e-sub_e.min())/(sub_e.max()-sub_e.min())
            RNA_size_dict[tra] = pd.DataFrame(sub_e, index=adata.obs_names, columns=['Values'])
    if FOV is None:
        fig ,ax = plt.subplots(figsize=figure_size)
        min_x, min_y, max_x, max_y = get_plot_limit(cell_boundary,cell_index)
        ax.set_xlim((min_x,max_x))
        ax.set_ylim((min_y,max_y))
        ax.set_facecolor(back)
        #
        progress_bar = tqdm(cell_index, desc="Processing", ncols=100)
        for cID in progress_bar:
            if 'subset' in kwargs:
                if adata.obs.loc[cID][group] not in kwargs['subset']:
                    continue
            p2 = Polygon(cell_boundary[cID], edgecolor = cols[cID], facecolor=cols_line[cID], linewidth=patch_line_width)
            ax.add_patch(p2)
        progress_bar.close()
        #
        transcripts = adata.uns['transcripts'].copy()
        sub_trans = transcripts.loc[transcripts[kwargs['RNA_name_col']].isin(RNA_name)]
        #print(sub_trans.shape[0])
        if RNA_color is None:
            RNA_color = get_color_map(RNA_name)
        if 'RNA_within_celltype' in kwargs:
            sub_trans = sub_trans.loc[sub_trans[kwargs['RNA_cell_id']].isin(adata.obs_names[adata.obs[group].isin(kwargs['RNA_within_celltype'])])]
            #print(sub_trans.shape[0])
        for tra in RNA_name:
            sub_sub_trans = sub_trans.loc[sub_trans[kwargs['RNA_name_col']].isin([tra])]
            if scale_size:
                RNA_size = RNA_size_dict[tra].loc[sub_sub_trans[kwargs['RNA_cell_id']].tolist()]['Values'].tolist()
            plt.scatter(sub_sub_trans[kwargs['RNA_x_px']],sub_sub_trans[kwargs['RNA_y_px']], s=RNA_size, c=RNA_color[tra])
    else:
        transcripts = adata.uns['transcripts'].copy()
        transcripts = transcripts.loc[transcripts[kwargs['RNA_name_col']].isin(RNA_name)]
        if RNA_color is None:
            RNA_color = get_color_map(RNA_name)
        if 'RNA_within_celltype' in kwargs:
            transcripts = transcripts.loc[transcripts[kwargs['RNA_cell_id']].isin(adata.obs_names[adata.obs[group].isin(kwargs['RNA_within_celltype'])])]
        #
        row_num = len(FOV)//FOV_col_num + int(not len(FOV)%FOV_col_num==0)
        fig, axs = plt.subplots(row_num, FOV_col_num,figsize=figure_size)
        axs = np.array([axs]).flatten()
        pointer_index = 0
        for fov in FOV:
            fov_index = adata.obs_names[adata.obs[kwargs['FOV_col']]==fov].tolist()
            progress_bar = tqdm(fov_index, desc='FOV: '+str(fov)+" is processing", ncols=100)
            #
            min_x, min_y, max_x, max_y = get_plot_limit(cell_boundary,fov_index)
            axs[pointer_index].set_xlim((min_x,max_x))
            axs[pointer_index].set_ylim((min_y,max_y))
            axs[pointer_index].set_facecolor(back)
            #
            for cID in progress_bar:
                if 'subset' in kwargs:
                    if adata.obs.loc[cID][group] not in kwargs['subset']:
                        continue
                p2 = Polygon(cell_boundary[cID], edgecolor = cols[cID], facecolor=cols_line[cID], linewidth=patch_line_width)
                axs[pointer_index].add_patch(p2)
            progress_bar.close()
            #
            sub_trans = transcripts.loc[transcripts[kwargs['FOV_col']].isin([fov])]
            #print(sub_trans.shape[0])
            for tra in RNA_name:
                sub_sub_trans = sub_trans.loc[sub_trans[kwargs['RNA_name_col']].isin([tra])]
                if scale_size:
                    RNA_size = RNA_size_dict[tra].loc[sub_sub_trans[kwargs['RNA_cell_id']].tolist()]['Values'].tolist()
                axs[pointer_index].scatter(sub_sub_trans[kwargs['RNA_x_px']],sub_sub_trans[kwargs['RNA_y_px']], s=RNA_size, c=RNA_color[tra])
            pointer_index += 1
    plt.savefig(output)

def plot_feature_cell(adata, feature = None,figsize=(150, 150),output = None,Only_boundary=False,
                      FOV = None,FOV_col_num=1,back_img = None, move=[0,0,0,0], img_scale=1, boundary_width = 1, **kwargs):
    cell_index = adata.obs_names.tolist()
    cmap = plt.get_cmap('turbo')
    cell_boundary = dict(adata.obs['boundary'])
    cell_boundary = {each:do_eval(cell_boundary[each]) for each in cell_boundary}
    if feature in adata.var_names:
        gene_index = adata.var_names.get_loc(feature)
        x = adata.X[:, gene_index]
        if issparse(x):
            x = x.toarray().ravel().tolist()
        else:
            x = np.array(x).ravel().tolist()
    else:
        x = adata.obs[feature].tolist()
    #
    x = np.array(x).astype(np.float64)
    minmax = (np.nanmin(x), np.nanmax(x) + 1e-12)
    x -= minmax[0]
    x /= minmax[1]
    x = cmap(x)[..., :3]
    #
    if FOV is None:
        fig ,ax = plt.subplots(figsize=figsize)
        if back_img is not None:
            if len(adata.uns[back_img].shape)==2:
                img = adata.uns[back_img].copy()
                img = IMAGE.fromarray(img).resize((round(img_scale*img.shape[1]),round(img_scale*img.shape[0])))
                ax.imshow(np.array(img), cmap='gray')
            else:
                #empty_blank = (np.ones((adata.uns[back_img].shape[0],move[0], 3))*0).astype(np.int)
                #combined_matrix = np.concatenate((empty_blank,adata.uns[back_img]), axis=1)
                #if move[1]>0:
                #    empty_blank = (np.ones((move[1], combined_matrix.shape[1], 3))*0).astype(np.int)
                #    combined_matrix = np.concatenate((empty_blank,combined_matrix), axis=0)
                #else:
                #    combined_matrix = combined_matrix[-move[1]:,:,:]
                #img = combined_matrix.copy()
                img = adjust_edge(adata.uns[back_img],move)
                img = IMAGE.fromarray(img.astype(np.uint8)).resize((round(img_scale*img.shape[1]),round(img_scale*img.shape[0])))
                ax.imshow(np.array(img))
        else:
            min_x, min_y, max_x, max_y = get_plot_limit(cell_boundary, cell_index)
            ax.set_xlim((min_x,max_x))
            ax.set_ylim((min_y,max_y))
            ax.set_facecolor('black')
        #
        progress_bar = tqdm(range(len(adata.obs_names)), desc="Processing", ncols=100)
        for cell_index in progress_bar:
            if Only_boundary:
                p2 = Polygon(cell_boundary[adata.obs_names[cell_index]], facecolor = 'none', edgecolor = x[cell_index], lw = boundary_width)
            else:
                p2 = Polygon(cell_boundary[adata.obs_names[cell_index]], facecolor = x[cell_index])
            ax.add_patch(p2)
        progress_bar.close()
    else:
        row_num = len(FOV)//FOV_col_num + int(not len(FOV)%FOV_col_num==0)
        fig, axs = plt.subplots(row_num, FOV_col_num,figsize=figsize)
        axs = np.array([axs]).flatten()
        pointer_index = 0
        for fov in FOV:
            fov_index = [index for index, value in enumerate(adata.obs[kwargs['FOV_col']]==fov) if value]
            progress_bar = tqdm(fov_index, desc='FOV: '+str(fov)+" is processing", ncols=100)
            #
            min_x, min_y, max_x, max_y = get_plot_limit(cell_boundary,adata.obs_names[fov_index])
            axs[pointer_index].set_xlim((min_x,max_x))
            axs[pointer_index].set_ylim((min_y,max_y))
            axs[pointer_index].set_facecolor('black')
            #
            for cell_index in progress_bar:
                if Only_boundary:
                    p2 = Polygon(cell_boundary[adata.obs_names[cell_index]], facecolor = 'none', edgecolor = x[cell_index], lw = boundary_width)
                else:
                    p2 = Polygon(cell_boundary[adata.obs_names[cell_index]], facecolor = x[cell_index])
                axs[pointer_index].add_patch(p2)
            progress_bar.close()
            pointer_index += 1
    plt.savefig(output)


def plot_feature_pixel(adata, features = None, square_size = 40, 
                       output=None, FOV = None, FOV_col_num=1,back_img=None, move=[0,0],img_scale=1, **kwargs):
    cmap = plt.get_cmap('turbo')
    transcripts = adata.uns['transcripts'].copy()
    sub_data = transcripts.loc[transcripts[kwargs['RNA_name_col']].isin(features)]
    if FOV is None:
        points = np.array(sub_data[[kwargs['RNA_x_px'],kwargs['RNA_y_px']]])
        #
        if back_img is not None:
            #empty_blank = (np.ones((adata.uns[back_img].shape[0],move[0], 3))*0).astype(np.int16)
            #combined_matrix = np.concatenate((empty_blank,adata.uns[back_img]), axis=1)
            #if move[1]>0:
            #    empty_blank = (np.ones((move[1], combined_matrix.shape[1], 3))*0).astype(np.int16)
            #    combined_matrix = np.concatenate((empty_blank,combined_matrix), axis=0)
            #else:
            #    combined_matrix = combined_matrix[-move[1]:,:,:]
            img = adjust_edge(adata.uns[back_img],move)
            x_min, x_max = 0,img.shape[0]
            y_min, y_max = 0,img.shape[1]
        else:
            x_min, x_max = points[:,0].min(),points[:,0].max()
            y_min, y_max = points[:,1].min(),points[:,1].max()
        square_size = square_size  # e.g., each square is 1 by 1 unit
        x_bins = np.arange(x_min, x_max + square_size, square_size)
        y_bins = np.arange(y_min, y_max + square_size, square_size)
        if back_img is not None:
            counts, _, _ = np.histogram2d(points[:,1]/img_scale, points[:,0]/img_scale, bins=(x_bins, y_bins))
        else:
            counts, _, _ = np.histogram2d(points[:,0], points[:,1], bins=(x_bins, y_bins))
        x = counts
        mask = counts!=0
        minmax = (np.nanmin(x), np.nanmax(x) + 1e-12)
        x -= minmax[0]
        x /= minmax[1]
        x = cmap(x)[..., :3]
        x[~mask] = 1.0
        x = (x * 255).astype(np.uint8)
        if back_img is not None:
            back_g = np.array(IMAGE.fromarray(img.astype(np.uint8)).resize((x.shape[1],x.shape[0])))
            sub_b = np.zeros((x.shape[0], x.shape[1], 4), dtype=np.uint8)
            sub_b[..., :3] = x
            sub_b[..., 3] = 0
            sub_b[mask, 3] = 255
            fig,ax = plt.subplots(figsize=(50, 50))
            # Display the first image
            ax.imshow(back_g, origin='upper')
            # Overlay the second image with transparency
            ax.imshow(sub_b, origin='upper')
            #
            ax.axis("off")
            ax.set_frame_on(False)
            plt.savefig(output)
        else:
            IMAGE.fromarray(x).save(output)
    else:
        x_list = []
        size_list = []
        for fov in FOV:
            sub_sub_data = sub_data.loc[sub_data[kwargs['FOV_col']]==fov]
            if len(sub_sub_data)==0:
                continue
            points = np.array(sub_sub_data[[kwargs['RNA_x_px'],kwargs['RNA_y_px']]])
            #
            x_min, x_max = points[:,0].min(),points[:,0].max()
            y_min, y_max = points[:,1].min(),points[:,1].max()
            square_size = square_size  # e.g., each square is 1 by 1 unit
            x_bins = np.arange(x_min, x_max + square_size, square_size)
            y_bins = np.arange(y_min, y_max + square_size, square_size)
            counts, _, _ = np.histogram2d(points[:,0], points[:,1], bins=(x_bins, y_bins))
            original_shape = counts.shape
            flattened_array = counts.reshape(-1)
            x_list = x_list+flattened_array.copy().tolist()
            size_list.append(original_shape)
        row_num = len(FOV)//FOV_col_num + int(not len(x_list)%FOV_col_num==0)
        x = np.array(x_list)
        mask = x!=0
        minmax = (np.nanmin(x), np.nanmax(x) + 1e-12)
        x -= minmax[0]
        x /= minmax[1]
        x = cmap(x)[..., :3]
        x[~mask] = 1.0
        x = (x * 255).astype(np.uint8)
        #
        x_list = x.tolist()
        #
        img_list = []
        size = [0,0]
        start_index = 0
        for fov in range(len(FOV)):
            index = size_list[fov][0]*size_list[fov][1]
            size[0], size[1] = max(size[0],size_list[fov][0]), max(size[1],size_list[fov][1])
            sub_list = x_list[start_index:(start_index+index)]
            start_index+=index
            reshaped_back_array = np.array(sub_list).reshape(list(size_list[fov])+[3])
            img_list.append(reshaped_back_array.copy())
        final_imag = IMAGE.new('RGB',(size[0]*row_num+(row_num-1)*2,size[1]*FOV_col_num+(FOV_col_num-1)*2))
        for fov in range(len(FOV)):
            final_imag.paste(IMAGE.fromarray(img_list[fov].astype(np.uint8)),((size[0]+2)*(fov%row_num),(size[1]+2)*(fov//row_num)))
        final_imag.save(output)

def adjust_numbers(numbers, polarize_factor = 10):
    # Apply power transformation to adjust the numbers
    adjusted_numbers = [np.exp(polarize_factor*num) for num in numbers]
    # Calculate the sum of the adjusted numbers
    adjusted_sum = sum(adjusted_numbers)
    # Determine the normalization factor to maintain the same sum
    normalization_factor = sum(numbers) / adjusted_sum
    # Normalize the adjusted numbers
    normalized_adjusted_numbers = [num * normalization_factor for num in adjusted_numbers]
    return normalized_adjusted_numbers

def plot_multiple_features(adata,features, col_map=None,FOV = None,
                           group=None, FOV_col_num = 2, output = None,
                           figure_size = (200, 200), **kwargs):
    #pre parameter
    bin = 10
    vector = [0.0,0.2,0.4,0.6,0.7,0.8,0.85,0.9,0.95,1.0]
    alpha_dic = {i: vector[i] for i in range(bin)}
    if col_map is None:
        color_use = ['FF0000','00FFFF','FFFF00','00FF00','FFFFFF','FF00FF']
        col_map = {features[i]:color_use[i] for i in range(len(features))}
    ###
    cell_index = adata.obs_names.tolist()
    alpha_list = [pd.cut(adata.X[:,adata.var_names == features[i]].toarray().flatten(), bins=bin, labels=False) if features[i] in adata.var_names else pd.cut(np.array(adata.obs[features[i]]).flatten(), bins=bin, labels=False) for i in range(len(features))]
    ratio_r = [tuple(np.array([alpha_dic[ii] for ii in value])/(0.01+np.array([alpha_dic[ii] for ii in value]).sum())) if np.array([alpha_dic[ii] for ii in value]).sum()>=1 else [alpha_dic[ii] for ii in value] for value in zip(*alpha_list)]
    #use active function to polarize the ratio
    ratio_r = [adjust_numbers(each) for each in ratio_r]
    #
    color1 = {each[0]:sum([np.array(hex_to_rgb(col_map[features[i]]))*each[1][i] for i in range(len(features))])/255 for each in zip(cell_index,ratio_r)}
    ###similar to the cell patch plot
    cell_boundary = dict(adata.obs['boundary'])
    cell_boundary = {each:do_eval(cell_boundary[each]) for each in cell_index}
    ###
    if FOV is None:
        fig ,ax = plt.subplots(figsize=figure_size)
        min_x, min_y, max_x, max_y = get_plot_limit(cell_boundary,cell_index)
        ax.set_xlim((min_x,max_x))
        ax.set_ylim((min_y,max_y))
        ax.set_facecolor('black')
        #
        progress_bar = tqdm(cell_index, desc="Processing", ncols=100)
        for cID in progress_bar:
            if 'subset' in kwargs:
                if adata.obs.loc[cID][group] not in kwargs['subset']:
                    continue
            p2 = Polygon(cell_boundary[cID], facecolor=color1[cID])
            ax.add_patch(p2)
        progress_bar.close()
    else:
        row_num = len(FOV)//FOV_col_num + int(not len(FOV)%FOV_col_num==0)
        fig, axs = plt.subplots(row_num, FOV_col_num,figsize=figure_size)
        axs = np.array([axs]).flatten()
        pointer_index = 0
        for fov in FOV:
            fov_index = adata.obs_names[adata.obs[kwargs['FOV_col']]==fov].tolist()
            progress_bar = tqdm(fov_index, desc='FOV: '+str(fov)+" is processing", ncols=100)
            #
            min_x, min_y, max_x, max_y = get_plot_limit(cell_boundary,fov_index)
            axs[pointer_index].set_xlim((min_x,max_x))
            axs[pointer_index].set_ylim((min_y,max_y))
            axs[pointer_index].set_facecolor('black')
            #
            for cID in progress_bar:
                if 'subset' in kwargs:
                    if adata.obs.loc[cID][group] not in kwargs['subset']:
                        continue
                p2 = Polygon(cell_boundary[cID], facecolor = color1[cID])
                axs[pointer_index].add_patch(p2)
            progress_bar.close()
            pointer_index += 1
    plt.savefig(output)

def plot_cell_density(adata, group = None, cell_type = None, loc = ['x_loc_px','y_loc_px'], square_size = 40, 
                       output=None, FOV = None, FOV_col_num=1,back_img=None, move=[0,0],img_scale=1, **kwargs):
    cmap = plt.get_cmap('turbo')
    sub_data = adata.obs.loc[adata.obs[group].isin(cell_type)]
    if FOV is None:
        points = np.array(sub_data[loc])
        #
        if back_img is not None:
            #empty_blank = (np.ones((adata.uns[back_img].shape[0],move[0], 3))*0).astype(np.int16)
            #combined_matrix = np.concatenate((empty_blank,adata.uns[back_img]), axis=1)
            #if move[1]>0:
            #    empty_blank = (np.ones((move[1], combined_matrix.shape[1], 3))*0).astype(np.int16)
            #    combined_matrix = np.concatenate((empty_blank,combined_matrix), axis=0)
            #else:
            #    combined_matrix = combined_matrix[-move[1]:,:,:]
            img = adjust_edge(adata.uns[back_img],move)
            x_min, x_max = 0,img.shape[0]
            y_min, y_max = 0,img.shape[1]
        else:
            x_min, x_max = points[:,0].min(),points[:,0].max()
            y_min, y_max = points[:,1].min(),points[:,1].max()
        square_size = square_size  # e.g., each square is 1 by 1 unit
        x_bins = np.arange(x_min, x_max + square_size, square_size)
        y_bins = np.arange(y_min, y_max + square_size, square_size)
        if back_img is not None:
            counts, _, _ = np.histogram2d(points[:,1]/img_scale, points[:,0]/img_scale, bins=(x_bins, y_bins))
        else:
            counts, _, _ = np.histogram2d(points[:,0], points[:,1], bins=(x_bins, y_bins))
        x = counts
        mask = counts!=0
        minmax = (np.nanmin(x), np.nanmax(x) + 1e-12)
        x -= minmax[0]
        x /= minmax[1]
        x = cmap(x)[..., :3]
        x[~mask] = 1.0
        x = (x * 255).astype(np.uint8)
        if back_img is not None:
            back_g = np.array(IMAGE.fromarray(img.astype(np.uint8)).resize((x.shape[1],x.shape[0])))
            sub_b = np.zeros((x.shape[0], x.shape[1], 4), dtype=np.uint8)
            sub_b[..., :3] = x
            sub_b[..., 3] = 0
            sub_b[mask, 3] = 255
            fig,ax = plt.subplots(figsize=(50, 50))
            # Display the first image
            ax.imshow(back_g, origin='upper')
            # Overlay the second image with transparency
            ax.imshow(sub_b, origin='upper')
            #
            ax.axis("off")
            ax.set_frame_on(False)
            plt.savefig(output)
        else:
            IMAGE.fromarray(x).save(output)
    else:
        x_list = []
        size_list = []
        for fov in FOV:
            sub_sub_data = sub_data.loc[sub_data[kwargs['FOV_col']]==fov]
            if len(sub_sub_data)==0:
                continue
            points = np.array(sub_sub_data[loc])
            #
            x_min, x_max = points[:,0].min(),points[:,0].max()
            y_min, y_max = points[:,1].min(),points[:,1].max()
            square_size = square_size  # e.g., each square is 1 by 1 unit
            x_bins = np.arange(x_min, x_max + square_size, square_size)
            y_bins = np.arange(y_min, y_max + square_size, square_size)
            counts, _, _ = np.histogram2d(points[:,0], points[:,1], bins=(x_bins, y_bins))
            original_shape = counts.shape
            flattened_array = counts.reshape(-1)
            x_list = x_list+flattened_array.copy().tolist()
            size_list.append(original_shape)
        row_num = len(FOV)//FOV_col_num + int(not len(x_list)%FOV_col_num==0)
        x = np.array(x_list)
        mask = x!=0
        minmax = (np.nanmin(x), np.nanmax(x) + 1e-12)
        x -= minmax[0]
        x /= minmax[1]
        x = cmap(x)[..., :3]
        x[~mask] = 1.0
        x = (x * 255).astype(np.uint8)
        #
        x_list = x.tolist()
        #
        img_list = []
        size = [0,0]
        start_index = 0
        for fov in range(len(FOV)):
            index = size_list[fov][0]*size_list[fov][1]
            size[0], size[1] = max(size[0],size_list[fov][0]), max(size[1],size_list[fov][1])
            sub_list = x_list[start_index:(start_index+index)]
            start_index+=index
            reshaped_back_array = np.array(sub_list).reshape(list(size_list[fov])+[3])
            img_list.append(reshaped_back_array.copy())
        final_imag = IMAGE.new('RGB',(size[0]*row_num+(row_num-1)*2,size[1]*FOV_col_num+(FOV_col_num-1)*2))
        for fov in range(len(FOV)):
            final_imag.paste(IMAGE.fromarray(img_list[fov].astype(np.uint8)),((size[0]+2)*(fov%row_num),(size[1]+2)*(fov//row_num)))
        final_imag.save(output)
        
