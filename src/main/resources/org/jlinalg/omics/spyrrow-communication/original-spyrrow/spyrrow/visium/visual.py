from matplotlib.colors import to_hex
import matplotlib as mp
import matplotlib.pyplot as plt
import numpy as np
import pandas as pd
import pylab
import warnings
warnings.filterwarnings("ignore")
import cv2
import tifffile as tiff
from PIL import Image
import SpaGCN as spg
import os,csv,re
import scanpy as sc
import math
from scipy.sparse import issparse
from collections import Counter
import seaborn as sns
from matplotlib.colors import ListedColormap
from matplotlib.cm import ScalarMappable
from matplotlib.patches import Patch
#
def plot_category(adata, group, output, col_map = None,
                  subset = None,
                  backgroud = False, figsize = (50, 50)):
    if col_map is None:
        col_map = {adata.obs[group].unique()[clu]:to_hex(plt.cm.tab20(clu)) for clu in range(len(adata.obs[group].unique()))}
    #
    if subset is None:
        col_map = {each:col_map[each] for each in col_map if each in adata.obs[group].tolist()}
        col_order = list(col_map.keys())
        color_values = [col_map[t] for t in col_order]
        temp_color_cmap_pa = mp.colors.ListedColormap(color_values)
        color_vector = [col_order.index(each) for each in adata.obs[group]]
    else:
        index = adata.obs[group].isin(subset)
        col_map = {each:col_map[each] for each in col_map if each in adata.obs.loc[index][group].tolist()}
        col_order = list(col_map.keys())
        color_values = [col_map[t] for t in col_order]
        temp_color_cmap_pa = mp.colors.ListedColormap(color_values)
        color_vector = [col_order.index(each) for each in adata.obs.loc[index][group]]
    #
    if subset is None:
        pixel_row = adata.obs['pixel_row']
        pixel_col = adata.obs['pixel_col']
        array_row = adata.obs['array_row']
        array_col = adata.obs['array_col']
    else:
        index = adata.obs[group].isin(subset)
        pixel_row = adata.obs['pixel_row'].loc[index]
        pixel_col = adata.obs['pixel_col'].loc[index]
        array_row = adata.obs['array_row'].loc[index]
        array_col = adata.obs['array_col'].loc[index]
        #color_vector = [color_vector[i] for i in range(len(index)) if index[i]]
    #
    fig,ax = plt.subplots(figsize=figsize)
    if backgroud:
        ax.imshow(list(adata.uns['spatial'].values())[0]['images']['hires'])
        img_ax = ax.hexbin(pixel_col, pixel_row, C = color_vector,
                gridsize = adata.uns['hexagon']['gridsize'],
                edgecolors = "face", linewidth = 0.0,
                cmap=temp_color_cmap_pa,
                extent = adata.uns['hexagon']['pixel_extent'],
            )
    else:
        img_ax = ax.hexbin(array_col, array_row, C = color_vector,
                gridsize = adata.uns['hexagon']['gridsize'],
                edgecolors = "face", linewidth = 0.0,
                cmap=temp_color_cmap_pa,
                extent = adata.uns['hexagon']['array_extent'],
            )
    ax.axis("off")
    ax.set_frame_on(False)
    #cbar_ax = fig.add_axes([0.96, 0.4, 0.03, 0.3]) #[lower_left_x, lower_left_y, width, height]
    #cmap = ListedColormap(list(col_map.values()))
    #sm = ScalarMappable(cmap=cmap)
    #cb = fig.colorbar(sm, cax=cbar_ax, location='right',ticks=range(len(col_map)))
    #cb.ax.set_yticklabels(col_map.keys())
    legend_handles = [Patch(color=color, label=category) for category, color in col_map.items()]
    legend = ax.legend(handles=legend_handles, loc='center', title = group, bbox_to_anchor=(1.05, 0.5), fontsize='large')
    #for handle in legend.legendHandles:
    #    handle.set_height(30)  # Adjust height as needed
    #    handle.set_width(50)
    plt.savefig(output) 
    plt.cla()
#
def plot_continuous(adata, variable, output, cmap = 'turbo',
                    subset = None, group=None,
                    backgroud = False, figsize = (50, 50)):
    if variable in adata.var_names:
        gene_index = adata.var_names.get_loc(variable)
        score = adata.X[:, gene_index]
        if issparse(score):
            score = score.toarray().ravel().tolist()
        else:
            score = np.array(score).ravel().tolist()
    else:
        score = adata.obs[variable].tolist()
    #
    if subset is None:
        pixel_row = adata.obs['pixel_row']
        pixel_col = adata.obs['pixel_col']
        array_row = adata.obs['array_row']
        array_col = adata.obs['array_col']
    else:
        index = adata.obs[group].isin(subset)
        pixel_row = adata.obs['pixel_row'].loc[index]
        pixel_col = adata.obs['pixel_col'].loc[index]
        array_row = adata.obs['array_row'].loc[index]
        array_col = adata.obs['array_col'].loc[index]
        score = [score[i] for i in range(len(index)) if index[i]]
    #
    fig,ax = plt.subplots(figsize=figsize)
    if backgroud:
        ax.imshow(list(adata.uns['spatial'].values())[0]['images']['hires'])
        img_ax = ax.hexbin(pixel_col, pixel_row, C = score,
                gridsize = adata.uns['hexagon']['gridsize'],
                edgecolors = "face", linewidth = 0.0,
                cmap=cmap,
                extent = adata.uns['hexagon']['pixel_extent'],
            )
    else:
        img_ax = ax.hexbin(array_col, array_row, C = score,
                gridsize = adata.uns['hexagon']['gridsize'],
                edgecolors = "face", linewidth = 0.0,
                cmap=cmap,
                extent = adata.uns['hexagon']['array_extent'],
            )
    ax.axis("off")
    ax.set_frame_on(False)
    cbar_ax = fig.add_axes([0.96, 0.4, 0.03, 0.3]) #[lower_left_x, lower_left_y, width, height]
    fig.colorbar(img_ax, cax=cbar_ax, location='right')
    plt.savefig(output)
    plt.cla()

def plot_multi_continuous(adata, variable, output, cmap = 'turbo',
                    subset = None, group=None, Col_num = 2,
                    backgroud = False, figsize = (100, 100)):
    score = [adata.obs[each].tolist() if each not in adata.var_names else adata.X[:, adata.var_names.get_loc(each)].toarray().ravel().tolist() if issparse(adata.X[:, adata.var_names.get_loc(each)]) else np.array(adata.X[:, adata.var_names.get_loc(each)]).ravel().tolist() for each in variable]
    #
    if subset is None:
        pixel_row = adata.obs['pixel_row']
        pixel_col = adata.obs['pixel_col']
        array_row = adata.obs['array_row']
        array_col = adata.obs['array_col']
    else:
        index = adata.obs[group].isin(subset)
        pixel_row = adata.obs['pixel_row'].loc[index]
        pixel_col = adata.obs['pixel_col'].loc[index]
        array_row = adata.obs['array_row'].loc[index]
        array_col = adata.obs['array_col'].loc[index]
        for ind in len(score):
            score[ind] = [score[ind][i] for i in range(len(index)) if index[i]]
    #
    row_num = len(score)//Col_num + int(not len(score)%Col_num==0)
    fig, axs = plt.subplots(row_num, Col_num,figsize=figsize)
    axs = np.array([axs]).flatten()
    pointer_index = 0
    for score_i in score:
        if backgroud:
            axs[pointer_index].imshow(list(adata.uns['spatial'].values())[0]['images']['hires'])
            img_ax = axs[pointer_index].hexbin(pixel_col, pixel_row, C = score_i,
                    gridsize = adata.uns['hexagon']['gridsize'],
                    edgecolors = "face", linewidth = 0.0,
                    cmap=cmap,
                    extent = adata.uns['hexagon']['pixel_extent'],
                )
        else:
            img_ax = axs[pointer_index].hexbin(array_col, array_row, C = score_i,
                    gridsize = adata.uns['hexagon']['gridsize'],
                    edgecolors = "face", linewidth = 0.0,
                    cmap=cmap,
                    extent = adata.uns['hexagon']['array_extent'],
                )
        axs[pointer_index].set_title(variable[pointer_index],fontsize=90)
        axs[pointer_index].axis("off")
        axs[pointer_index].set_frame_on(False)
        pointer_index += 1
    plt.savefig(output)
