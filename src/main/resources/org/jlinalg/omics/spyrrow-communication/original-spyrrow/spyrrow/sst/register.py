from scipy.ndimage import affine_transform
import numpy as np
import PIL
from PIL import Image
import matplotlib.pyplot as plt
import cv2
from .utils import load_image
#/rsrch4/scratch/genomic_med/yliu43/data/4lungcancer/code/20231011_affine_transformation.py
def linear_call(aligment_matrix,image_file,output,level=2, dsize = (100000,100000)):
    if isinstance(aligment_matrix,np.ndarray):
        transfer = aligment_matrix
    else:
        transfer = np.loadtxt(aligment_matrix,
                        delimiter=",", dtype=float)
    T_opencv = np.float32(transfer.flatten()[:6].reshape(2,3))
    #
    image2 = load_image(image_file,level=level)
    empty_blank = (np.ones((2000,image2.shape[1], 3))*0).astype(np.int)
    combined_matrix = np.concatenate((empty_blank,image2), axis=0)
    img_transformed = cv2.warpAffine(combined_matrix.astype(np.uint8), T_opencv, dsize)
    mask = img_transformed.sum(2)
    img_transformed2 = img_transformed[mask.sum(1)>0]
    img_transformed2 = img_transformed2[:,mask.sum(0)>0]
    Image.fromarray(img_transformed2).save(output)
#
def adjust_edge(image, edge = (0,0,0,0)): #left, top, right, bottom; since most coordination is set the left top point as original point
    #left and right
    if edge[0]>0:
        empty_blank = (np.ones((image.shape[0],edge[0], 3))*0).astype(np.int)
        image = np.concatenate((empty_blank,image), axis=1)
    elif edge[0]<0:
        image = image[:,-edge[0]:,:]
    if edge[2]>0:
        empty_blank = (np.ones((image.shape[0],edge[2], 3))*0).astype(np.int)
        image = np.concatenate((image,empty_blank), axis=1)
    elif edge[2]<0:
        image = image[:,:(image.shape[1]+edge[2]),:]
    #top and bottom
    if edge[1]>0:
        empty_blank = (np.ones((edge[1], image.shape[1], 3))*0).astype(np.int)
        image = np.concatenate((empty_blank,image), axis=0)
    elif edge[1]<0:
        image = image[-edge[1]:,:,:]
    if edge[3]>0:
        empty_blank = (np.ones((edge[3], image.shape[1], 3))*0).astype(np.int)
        image = np.concatenate((image,empty_blank), axis=0)
    elif edge[3]<0:
        image = image[:(image.shape[0]+edge[3]),:,:]
    return image
