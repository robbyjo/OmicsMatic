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

#kwargs['anno_col','subset']
def plot_cell_patch(cell_boundary,output,annotation=None,col_map=None,figure_size = (200, 200),**kwargs):
    if annotation is not None:
        cell_index = annotation.index.tolist()
        if col_map is not None:
            cols = {cID:col_map[annotation.loc[cID][kwargs['anno_col']]] for cID in cell_index}
        else:
            col_map = get_color_map(annotation[kwargs['anno_col']].unique())
            cols = {cID:col_map[annotation.loc[cID][kwargs['anno_col']]] for cID in cell_index}
    else:
        cell_index = list(cell_boundary.keys())
        cols = {cID:'#FFFFFF' for cID in cell_index}
    fig ,ax = plt.subplots(figsize=figure_size)
    min_x, min_y, max_x, max_y = get_plot_limit(cell_boundary,cell_index)
    ax.set_xlim((min_x,max_x))
    ax.set_ylim((min_y,max_y))
    ax.set_facecolor('black')
    #
    progress_bar = tqdm(cell_index, desc="Processing", ncols=100)
    for cID in progress_bar:
        if 'subset' in kwargs:
            if annotation.loc[cID][kwargs['anno_col']] not in kwargs['subset']:
                continue
        p2 = Polygon(cell_boundary[cID], facecolor = cols[cID])
        ax.add_patch(p2)
    progress_bar.close()
    plt.savefig(output)

#kwargs['anno_col','subset','RNA_name_col','RNA_cell_id','RNA_x_px','RNA_y_px','RNA_within_celltype']
def plot_RNA_on_cell(cell_boundary,output,annotation=None,col_map=None,figure_size = (200, 200),
                    transcripts = None, RNA_name = None, RNA_color = None, RNA_size = 3,
                    inside_dark_factor = 0.5, patch_line_width = 2, **kwargs):
    if annotation is not None:
        cell_index = annotation.index.tolist()
        if col_map is not None:
            cols = {cID:col_map[annotation.loc[cID][kwargs['anno_col']]] for cID in cell_index}
        else:
            col_map = get_color_map(annotation[kwargs['anno_col']].unique())
            cols = {cID:col_map[annotation.loc[cID][kwargs['anno_col']]] for cID in cell_index}
    else:
        cell_index = list(cell_boundary.keys())
        cols = {cID:'#FFFFFF' for cID in cell_index}
    #
    col_map_inside = col_map.copy()
    if annotation is not None:
        if 'RNA_within_celltype' in kwargs:
            for cell in kwargs['RNA_within_celltype']:
                col_map_inside[cell] = darken_color(col_map_inside[cell],factor = inside_dark_factor)
        else:
            for cell in col_map_inside:
                col_map_inside[cell] = darken_color(col_map_inside[cell],factor = inside_dark_factor)
        cols_line = {cID:col_map_inside[annotation.loc[cID][kwargs['anno_col']]] for cID in cell_index}
    else:
        cols_line = {cID:darken_color('#FFFFFF',factor = inside_dark_factor) for cID in cell_index}
    #
    fig ,ax = plt.subplots(figsize=figure_size)
    min_x, min_y, max_x, max_y = get_plot_limit(cell_boundary,cell_index)
    ax.set_xlim((min_x,max_x))
    ax.set_ylim((min_y,max_y))
    ax.set_facecolor('black')
    #
    progress_bar = tqdm(cell_index, desc="Processing", ncols=100)
    for cID in progress_bar:
        if 'subset' in kwargs:
            if annotation.loc[cID][kwargs['anno_col']] not in kwargs['subset']:
                continue
        p2 = Polygon(cell_boundary[cID], edgecolor = cols[cID], facecolor=cols_line[cID], linewidth=patch_line_width)
        ax.add_patch(p2)
    progress_bar.close()
    #
    sub_trans = transcripts.loc[transcripts[kwargs['RNA_name_col']].isin(RNA_name)]
    #print(sub_trans.shape[0])
    if RNA_color is None:
        RNA_color = get_color_map(RNA_name)
    if 'RNA_within_celltype' in kwargs:
        sub_trans = sub_trans.loc[sub_trans[kwargs['RNA_cell_id']].isin(annotation.index[annotation[kwargs['anno_col']].isin(kwargs['RNA_within_celltype'])])]
        #print(sub_trans.shape[0])
    for tra in RNA_name:
        sub_sub_trans = sub_trans.loc[sub_trans[kwargs['RNA_name_col']].isin([tra])]
        plt.scatter(sub_sub_trans[kwargs['RNA_x_px']],sub_sub_trans[kwargs['RNA_y_px']], s=RNA_size, c=RNA_color[tra])
    plt.savefig(output)
#
#######for FOV array visualization
def plot_cell_patch(cell_boundary,output,annotation=None,col_map=None,figure_size = (200, 200),FOV=None,FOV_col_num = 2 ,**kwargs):
    if annotation is not None:
        cell_index = annotation.index.tolist()
        if col_map is not None:
            cols = {cID:col_map[annotation.loc[cID][kwargs['anno_col']]] for cID in cell_index}
        else:
            col_map = get_color_map(annotation[kwargs['anno_col']].unique())
            cols = {cID:col_map[annotation.loc[cID][kwargs['anno_col']]] for cID in cell_index}
    else:
        cell_index = list(cell_boundary.keys())
        cols = {cID:'#FFFFFF' for cID in cell_index}
    #
    if FOV is None:
        fig ,ax = plt.subplots(figsize=figure_size)
        min_x, min_y, max_x, max_y = get_plot_limit(cell_boundary,cell_index)
        ax.set_xlim((min_x,max_x))
        ax.set_ylim((min_y,max_y))
        ax.set_facecolor('black')
        progress_bar = tqdm(cell_index, desc="Processing", ncols=100)
        for cID in progress_bar:
            if 'subset' in kwargs:
                if annotation.loc[cID][kwargs['anno_col']] not in kwargs['subset']:
                    continue
            p2 = Polygon(cell_boundary[cID], facecolor = cols[cID])
            ax.add_patch(p2)
        progress_bar.close()
    else:
        row_num = len(FOV)//FOV_col_num + int(not len(FOV)%FOV_col_num==0)
        fig, axs = plt.subplots(row_num, FOV_col_num,figsize=figure_size)
        pointer_index = 0
        for fov in FOV:
            fov_index = annotation.index[annotation[kwargs['FOV_col']]==fov].tolist()
            progress_bar = tqdm(fov_index, desc='FOV: '+str(fov)+" is processing", ncols=100)
            #
            min_x, min_y, max_x, max_y = get_plot_limit(cell_boundary,fov_index)
            if (FOV_col_num==1) and (row_num==1):
                axs.set_xlim((min_x,max_x))
                axs.set_ylim((min_y,max_y))
                axs.set_facecolor('black')
            elif (FOV_col_num==1) or (row_num==1):
                axs[pointer_index].set_xlim((min_x,max_x))
                axs[pointer_index].set_ylim((min_y,max_y))
                axs[pointer_index].set_facecolor('black')
            else:
                axs[pointer_index//FOV_col_num, pointer_index%FOV_col_num].set_xlim((min_x,max_x))
                axs[pointer_index//FOV_col_num, pointer_index%FOV_col_num].set_ylim((min_y,max_y))
                axs[pointer_index//FOV_col_num, pointer_index%FOV_col_num].set_facecolor('black')
            for cID in progress_bar:
                if 'subset' in kwargs:
                    if annotation.loc[cID][kwargs['anno_col']] not in kwargs['subset']:
                        continue
                p2 = Polygon(cell_boundary[cID], facecolor = cols[cID])
                if (FOV_col_num==1) and (row_num==1):
                    axs.add_patch(p2)
                elif (FOV_col_num==1) or (row_num==1):
                    axs[pointer_index].add_patch(p2)
                else:
                    axs[pointer_index//FOV_col_num, pointer_index%FOV_col_num].add_patch(p2)
            progress_bar.close()
            pointer_index += 1
    plt.savefig(output)

#
def plot_RNA_on_cell(cell_boundary,output,annotation=None,col_map=None,figure_size = (200, 200),
                    transcripts = None, RNA_name = None, RNA_color = None, RNA_size = 3,
                    inside_dark_factor = 0.5, patch_line_width = 2, FOV=None,FOV_col_num = 2, **kwargs):
    if annotation is not None:
        cell_index = annotation.index.tolist()
        if col_map is not None:
            cols = {cID:col_map[annotation.loc[cID][kwargs['anno_col']]] for cID in cell_index}
        else:
            col_map = get_color_map(annotation[kwargs['anno_col']].unique())
            cols = {cID:col_map[annotation.loc[cID][kwargs['anno_col']]] for cID in cell_index}
    else:
        cell_index = list(cell_boundary.keys())
        cols = {cID:'#FFFFFF' for cID in cell_index}
    #
    col_map_inside = col_map.copy()
    if annotation is not None:
        if 'RNA_within_celltype' in kwargs:
            for cell in kwargs['RNA_within_celltype']:
                col_map_inside[cell] = darken_color(col_map_inside[cell],factor = inside_dark_factor)
        else:
            for cell in col_map_inside:
                col_map_inside[cell] = darken_color(col_map_inside[cell],factor = inside_dark_factor)
        cols_line = {cID:col_map_inside[annotation.loc[cID][kwargs['anno_col']]] for cID in cell_index}
    else:
        cols_line = {cID:darken_color('#FFFFFF',factor = inside_dark_factor) for cID in cell_index}
    #
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
                if annotation.loc[cID][kwargs['anno_col']] not in kwargs['subset']:
                    continue
            p2 = Polygon(cell_boundary[cID], edgecolor = cols[cID], facecolor=cols_line[cID], linewidth=patch_line_width)
            ax.add_patch(p2)
        progress_bar.close()
        #
        sub_trans = transcripts.loc[transcripts[kwargs['RNA_name_col']].isin(RNA_name)]
        #print(sub_trans.shape[0])
        if RNA_color is None:
            RNA_color = get_color_map(RNA_name)
        if 'RNA_within_celltype' in kwargs:
            sub_trans = sub_trans.loc[sub_trans[kwargs['RNA_cell_id']].isin(annotation.index[annotation[kwargs['anno_col']].isin(kwargs['RNA_within_celltype'])])]
            #print(sub_trans.shape[0])
        for tra in RNA_name:
            sub_sub_trans = sub_trans.loc[sub_trans[kwargs['RNA_name_col']].isin([tra])]
            plt.scatter(sub_sub_trans[kwargs['RNA_x_px']],sub_sub_trans[kwargs['RNA_y_px']], s=RNA_size, c=RNA_color[tra])
    else:
        transcripts = transcripts.loc[transcripts[kwargs['RNA_name_col']].isin(RNA_name)]
        if RNA_color is None:
            RNA_color = get_color_map(RNA_name)
        if 'RNA_within_celltype' in kwargs:
            transcripts = transcripts.loc[transcripts[kwargs['RNA_cell_id']].isin(annotation.index[annotation[kwargs['anno_col']].isin(kwargs['RNA_within_celltype'])])]
        #
        row_num = len(FOV)//FOV_col_num + int(not len(FOV)%FOV_col_num==0)
        fig, axs = plt.subplots(row_num, FOV_col_num,figsize=figure_size)
        pointer_index = 0
        for fov in FOV:
            fov_index = annotation.index[annotation[kwargs['FOV_col']]==fov].tolist()
            progress_bar = tqdm(fov_index, desc='FOV: '+str(fov)+" is processing", ncols=100)
            #
            min_x, min_y, max_x, max_y = get_plot_limit(cell_boundary,fov_index)
            if (FOV_col_num==1) and (row_num==1):
                axs.set_xlim((min_x,max_x))
                axs.set_ylim((min_y,max_y))
                axs.set_facecolor('black')
            elif (FOV_col_num==1) or (row_num==1):
                axs[pointer_index].set_xlim((min_x,max_x))
                axs[pointer_index].set_ylim((min_y,max_y))
                axs[pointer_index].set_facecolor('black')
            else:
                axs[pointer_index//FOV_col_num, pointer_index%FOV_col_num].set_xlim((min_x,max_x))
                axs[pointer_index//FOV_col_num, pointer_index%FOV_col_num].set_ylim((min_y,max_y))
                axs[pointer_index//FOV_col_num, pointer_index%FOV_col_num].set_facecolor('black')
            for cID in progress_bar:
                if 'subset' in kwargs:
                    if annotation.loc[cID][kwargs['anno_col']] not in kwargs['subset']:
                        continue
                p2 = Polygon(cell_boundary[cID], edgecolor = cols[cID], facecolor=cols_line[cID], linewidth=patch_line_width)
                if (FOV_col_num==1) and (row_num==1):
                    axs.add_patch(p2)
                elif (FOV_col_num==1) or (row_num==1):
                    axs[pointer_index].add_patch(p2)
                else:
                    axs[pointer_index//FOV_col_num, pointer_index%FOV_col_num].add_patch(p2)
            progress_bar.close()
            #
            sub_trans = transcripts.loc[transcripts[kwargs['FOV_col']].isin([fov])]
            #print(sub_trans.shape[0])
            for tra in RNA_name:
                sub_sub_trans = sub_trans.loc[sub_trans[kwargs['RNA_name_col']].isin([tra])]
                if (FOV_col_num==1) and (row_num==1):
                    axs.scatter(sub_sub_trans[kwargs['RNA_x_px']],sub_sub_trans[kwargs['RNA_y_px']], s=RNA_size, c=RNA_color[tra])
                elif (FOV_col_num==1) or (row_num==1):
                    axs[pointer_index].scatter(sub_sub_trans[kwargs['RNA_x_px']],sub_sub_trans[kwargs['RNA_y_px']], s=RNA_size, c=RNA_color[tra])
                else:
                    axs[pointer_index//FOV_col_num, pointer_index%FOV_col_num].scatter(sub_sub_trans[kwargs['RNA_x_px']],sub_sub_trans[kwargs['RNA_y_px']], s=RNA_size, c=RNA_color[tra])
            pointer_index += 1
    plt.savefig(output)
