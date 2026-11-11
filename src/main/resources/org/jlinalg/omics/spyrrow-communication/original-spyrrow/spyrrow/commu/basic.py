import pandas as pd
import numpy as np
import cv2
from scipy.spatial import ConvexHull
import matplotlib.pyplot as plt
from matplotlib.patches import Polygon
from shapely.geometry import Polygon as ShapelyPolygon
import pickle
import os
from scipy.spatial import KDTree
import math
from functools import partial
from collections import Counter
import networkx as nx
from scipy.spatial import Delaunay
#
class CustomError(Exception):
    pass
#
def find_nearest_neighbors(point_index, points, num_neighbors=50):
    point = points[point_index]
    distances = np.linalg.norm(points - point, axis=1)
    nearest_indices = np.argsort(distances)[1:num_neighbors+1]  # Exclude the point itself
    return nearest_indices
#get neighbor cells
def get_neighbors(points, index_list = None, methods = 'circle',radius = 10,neighbor = 10):
    neighbors_list = []
    if index_list is None:
        index_list = list(range(len(points)))
    if methods == 'circle':
        tree = KDTree(points)
        for index in index_list:
            #x_min, y_min, x_max, y_max = points[index][0]-radius,points[index][1]-radius,points[index][0]+radius,points[index][1]+radius
            #condition = (points[:, 0] >= x_min) & ((points[:, 0] <= x_max)) & (points[:, 1] >= y_min) & ((points[:, 1] <= y_max))
            #sub_indexs = np.where(condition)[0]
            indices = tree.query_ball_point(points[index], radius)
            #neighbors_list.append([points[i] for i in indices])
            neighbors_list.append(indices)
    elif methods == 'k-nearest':
        tree = KDTree(points)
        for index in index_list:
            indices = tree.query(points[index], k = neighbor)
            #neighbors_list.append([points[i] for i in indices])
            neighbors_list.append(indices)
    elif methods == 'delaunay':
        tri = Delaunay(points)
        #points[tri.simplices]
        for index in index_list:
            row_indices, col_indices = np.where(tri.simplices == index)
            whole_nei = [tri.simplices[i,j] for i, j in zip(row_indices, col_indices)]
            neighbors_list.append(whole_nei.unique())
    return neighbors_list
#get paired cells
def get_point_pairs(points, points_id, threshold):
    pairs = []
    for i in range(len(points)):
        for j in range(i+1, len(points)):
            distance = math.dist(points[i], points[j])
            if distance < threshold:
                pairs.append((points_id[i], points_id[j]))
    return pairs
#
def interaction_activity(a_exp, b_exp, gene_nam ,interaction):
    a2b_activity = {}
    b2a_activity = {}
    for inter in zip(interaction[0],interaction[1]):
        if (inter[0] in gene_nam) and (inter[1] in gene_nam):
            a2b_activity[inter[0]+':'+inter[1]] = math.sqrt(a_exp[gene_nam.index(inter[0])] * b_exp[gene_nam.index(inter[1])])
            b2a_activity[inter[0]+':'+inter[1]] = math.sqrt(b_exp[gene_nam.index(inter[0])] * a_exp[gene_nam.index(inter[1])])
    #
    av_ligand = [each.split(':')[0] for each in a2b_activity]
    av_receptor = [each.split(':')[1] for each in a2b_activity]
    av_ligand_dic = {each:[av_receptor[i] for i in range(len(av_ligand)) if av_ligand[i]==each] for each in set(av_ligand)}
    av_receptor_dic = {each:[av_ligand[i] for i in range(len(av_receptor)) if av_receptor[i]==each] for each in set(av_receptor)}
    a_activity = {'ligand':[],'receptor':[]}
    b_activity = {'ligand':[],'receptor':[]}
    for ligand in av_ligand_dic:
        a_activity['ligand'].append(sum([a2b_activity[ligand+':'+each] for each in av_ligand_dic[ligand]]))
        b_activity['ligand'].append(sum([b2a_activity[ligand+':'+each] for each in av_ligand_dic[ligand]]))
    for receptor in av_receptor_dic:
        a_activity['receptor'].append(sum([b2a_activity[each+':'+receptor] for each in av_receptor_dic[receptor]]))
        b_activity['receptor'].append(sum([a2b_activity[each+':'+receptor] for each in av_receptor_dic[receptor]]))
    return a2b_activity, b2a_activity, a_activity, b_activity, list(av_ligand_dic.keys()), list(av_receptor_dic.keys())
#
def cal_cell_communication(count,pos_x,pos_y,interaction,dist_thresh):
    point_pair = get_point_pairs([each for each in zip(pos_x,pos_y)]
                                , count.index.tolist(), dist_thresh)
    partial_function = partial(interaction_activity, gene_nam= count.columns.tolist(),interaction=interaction)
    a_list = [count.loc[each[0]].tolist() for each in point_pair]
    b_list = [count.loc[each[1]].tolist() for each in point_pair]
    whole_interaction_results = list(map(partial_function, a_list, b_list))
    #
    point_pair_a2b = {point_pair[i][0]+'|'+point_pair[i][1]:whole_interaction_results[i][0] for i in range(len(point_pair))}
    point_pair_b2a = {point_pair[i][0]+'|'+point_pair[i][1]:whole_interaction_results[i][1] for i in range(len(point_pair))}
    point_pair_a_ligand = {point_pair[i][0]+'|'+point_pair[i][1]:whole_interaction_results[i][2]['ligand'] for i in range(len(point_pair))}
    point_pair_a_receptor = {point_pair[i][0]+'|'+point_pair[i][1]:whole_interaction_results[i][2]['receptor'] for i in range(len(point_pair))}
    point_pair_b_ligand = {point_pair[i][0]+'|'+point_pair[i][1]:whole_interaction_results[i][3]['ligand'] for i in range(len(point_pair))}
    point_pair_b_receptor = {point_pair[i][0]+'|'+point_pair[i][1]:whole_interaction_results[i][3]['receptor'] for i in range(len(point_pair))}
    #
    point_pair_a2b = pd.DataFrame.from_dict(point_pair_a2b).T
    point_pair_b2a = pd.DataFrame.from_dict(point_pair_b2a).T
    point_pair_a2b.columns = [each+'_a2b' for each in point_pair_a2b.columns]
    point_pair_b2a.columns = [each+'_b2a' for each in point_pair_b2a.columns]
    point_pair_a_ligand = pd.DataFrame.from_dict(point_pair_a_ligand).T
    point_pair_a_receptor = pd.DataFrame.from_dict(point_pair_a_receptor).T
    point_pair_b_ligand = pd.DataFrame.from_dict(point_pair_b_ligand).T
    point_pair_b_receptor = pd.DataFrame.from_dict(point_pair_b_receptor).T
    point_pair_a_ligand.columns = [each+'_al' for each in whole_interaction_results[0][4]]
    point_pair_a_receptor.columns = [each+'_ar' for each in whole_interaction_results[0][5]]
    point_pair_b_ligand.columns = [each+'_bl' for each in whole_interaction_results[0][4]]
    point_pair_b_receptor.columns = [each+'_br' for each in whole_interaction_results[0][5]]
    #
    whole_combined = pd.concat([point_pair_a2b,point_pair_b2a,point_pair_a_ligand,point_pair_a_receptor,point_pair_b_ligand,point_pair_b_receptor], axis=1)
    return whole_combined
#

