import SpaGCN as spg
import os,csv,re
import pandas as pd
import numpy as np
import scanpy as sc
import math
from scipy.sparse import issparse
import random, torch
import warnings
warnings.filterwarnings("ignore")
import matplotlib.colors as clr
import matplotlib.pyplot as plt
import cv2
import tifffile as tiff
from PIL import Image
#
def call(adata,n_clusters = 8, s=1,b=49,p=0.5,histology=False, r_seed=100,n_seed=100,t_seed=100):
    x_pixel = adata.obs['pixel_col'].tolist()
    y_pixel = adata.obs['pixel_row'].tolist()
    img = ((list(adata.uns['spatial'].values())[0]['images']['hires'])*255).astype(np.uint8)
    # 
    #img_new=img.copy()
    #for i in range(len(x_pixel)):
    #    x=x_pixel[i]
    #    y=y_pixel[i]
    #    img_new[int(x-10):int(x+10), int(y-10):int(y+10),:]=0
    #Image.fromarray(img_new).save('/rsrch6/home/genomic_med/lwang22_lab/Yunhe/Francesca/test.png')
    #
    adj=spg.calculate_adj_matrix(x=x_pixel,y=y_pixel, x_pixel=x_pixel, y_pixel=y_pixel, image=img, beta=b, alpha=s, histology=histology)
    #print(adj)
    #
    spg.prefilter_genes(adata,min_cells=3) # avoiding all genes are zeros
    spg.prefilter_specialgenes(adata)
    #Normalize and take log for UMI
    sc.pp.normalize_per_cell(adata)
    sc.pp.log1p(adata)
    #Find the l value given p
    l=spg.search_l(p, adj, start=0.01, end=1000, tol=0.01, max_run=100)
    #do cluster
    res=spg.search_res(adata, adj, l, n_clusters, start=0.5, step=0.1, tol=5e-3, lr=0.05, max_epochs=20, r_seed=r_seed, t_seed=t_seed, n_seed=n_seed)
    #run the program
    clf=spg.SpaGCN()
    clf.set_l(l)
    #Set seed
    random.seed(r_seed)
    torch.manual_seed(t_seed)
    np.random.seed(n_seed)
    #Run
    clf.train(adata,adj,init_spa=True,init="louvain",res=res, tol=5e-3, lr=0.05, max_epochs=200)
    y_pred, prob=clf.predict()
    adata.obs["pred"]= y_pred
    adata.obs["pred"]=adata.obs["pred"].astype('category')


def call(adata,n_clusters = 3, group = None, subset = None, s=1,b=49,p=0.5,histology=False, r_seed=100,n_seed=100,t_seed=100):
    img = ((list(adata.uns['spatial'].values())[0]['images']['hires'])*255).astype(np.uint8)
    #
    if subset is None:
        x_pixel = adata.obs['pixel_col'].tolist()
        y_pixel = adata.obs['pixel_row'].tolist()
        # 
        #img_new=img.copy()
        #for i in range(len(x_pixel)):
        #    x=x_pixel[i]
        #    y=y_pixel[i]
        #    img_new[int(x-10):int(x+10), int(y-10):int(y+10),:]=0
        #Image.fromarray(img_new).save('/rsrch6/home/genomic_med/lwang22_lab/Yunhe/Francesca/test.png')
        #
        adj=spg.calculate_adj_matrix(x=x_pixel,y=y_pixel, x_pixel=x_pixel, y_pixel=y_pixel, image=img, beta=b, alpha=s, histology=histology)
        #print(adj)
        #
        spg.prefilter_genes(adata,min_cells=3) # avoiding all genes are zeros
        spg.prefilter_specialgenes(adata)
        #Normalize and take log for UMI
        sc.pp.normalize_per_cell(adata)
        sc.pp.log1p(adata)
        #Find the l value given p
        l=spg.search_l(p, adj, start=0.01, end=1000, tol=0.01, max_run=100)
        #do cluster
        res=spg.search_res(adata, adj, l, n_clusters, start=0.5, step=0.1, tol=5e-3, lr=0.05, max_epochs=20, r_seed=r_seed, t_seed=t_seed, n_seed=n_seed)
        #run the program
        clf=spg.SpaGCN()
        clf.set_l(l)
        #Set seed
        random.seed(r_seed)
        torch.manual_seed(t_seed)
        np.random.seed(n_seed)
        #Run
        clf.train(adata,adj,init_spa=True,init="louvain",res=res, tol=5e-3, lr=0.05, max_epochs=200)
        y_pred, prob=clf.predict()
        adata.obs["pred"]= y_pred
        adata.obs["pred"]=adata.obs["pred"].astype('category')
    else:
        index = adata.obs[group].isin(subset)
        x_pixel = adata.obs.loc[index,'pixel_col'].tolist()
        y_pixel = adata.obs.loc[index,'pixel_row'].tolist()
        adata_sub = adata[index].copy()
        # 
        #img_new=img.copy()
        #for i in range(len(x_pixel)):
        #    x=x_pixel[i]
        #    y=y_pixel[i]
        #    img_new[int(x-10):int(x+10), int(y-10):int(y+10),:]=0
        #Image.fromarray(img_new).save('/rsrch6/home/genomic_med/lwang22_lab/Yunhe/Francesca/test.png')
        #
        adj=spg.calculate_adj_matrix(x=x_pixel,y=y_pixel, x_pixel=x_pixel, y_pixel=y_pixel, image=img, beta=b, alpha=s, histology=histology)
        #print(adj)
        #
        spg.prefilter_genes(adata_sub,min_cells=3) # avoiding all genes are zeros
        spg.prefilter_specialgenes(adata_sub)
        #Normalize and take log for UMI
        sc.pp.normalize_per_cell(adata_sub)
        sc.pp.log1p(adata_sub)
        #Find the l value given p
        l=spg.search_l(p, adj, start=0.01, end=1000, tol=0.01, max_run=100)
        #do cluster
        res=spg.search_res(adata_sub, adj, l, n_clusters, start=0.5, step=0.1, tol=5e-3, lr=0.05, max_epochs=20, r_seed=r_seed, t_seed=t_seed, n_seed=n_seed)
        #run the program
        clf=spg.SpaGCN()
        clf.set_l(l)
        #Set seed
        random.seed(r_seed)
        torch.manual_seed(t_seed)
        
        np.random.seed(n_seed)
        #Run
        clf.train(adata_sub,adj,init_spa=True,init="louvain",res=res, tol=5e-3, lr=0.05, max_epochs=200)
        y_pred, prob=clf.predict()
        adata.obs["sub_pred"] = -1
        adata.obs.loc[index,"sub_pred"]= y_pred
        adata.obs["sub_pred"]=adata.obs["sub_pred"].astype('category')
