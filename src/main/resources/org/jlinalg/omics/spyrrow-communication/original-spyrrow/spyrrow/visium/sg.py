import numpy as np
import matplotlib.pyplot as plt
from scipy.spatial import Voronoi, voronoi_plot_2d
from shapely.geometry import Polygon as ShapelyPolygon
from shapely.geometry import Point as ShapelyPoint
from shapely.ops import split
from matplotlib.patches import Polygon
from tqdm import tqdm
#
def voronoi_finite_polygons_2d(vor, radius=None):
    """
    Reconstruct infinite voronoi regions in a 2D diagram to finite
    regions.
    Parameters
    ----------
    vor : Voronoi
        Input diagram
    radius : float, optional
        Distance to 'points at infinity'.
    Returns
    -------
    regions : list of tuples
        Indices of vertices in each revised Voronoi regions.
    vertices : list of tuples
        Coordinates for revised Voronoi vertices. Same as coordinates
        of input vertices, with 'points at infinity' appended to the
        end.
    """
    if vor.points.shape[1] != 2:
        raise ValueError("Requires 2D input")
    new_regions = []
    new_vertices = vor.vertices.tolist()
    center = vor.points.mean(axis=0)
    if radius is None:
        radius = vor.points.ptp().max()*2
    # Construct a map containing all ridges for a given point
    all_ridges = {}
    for (p1, p2), (v1, v2) in zip(vor.ridge_points, vor.ridge_vertices):
        all_ridges.setdefault(p1, []).append((p2, v1, v2))
        all_ridges.setdefault(p2, []).append((p1, v1, v2))
    # Reconstruct infinite regions
    for p1, region in enumerate(vor.point_region):
        vertices = vor.regions[region]
        if all(v >= 0 for v in vertices):
            # finite region
            new_regions.append(vertices)
            continue
        # reconstruct a non-finite region
        ridges = all_ridges[p1]
        new_region = [v for v in vertices if v >= 0]
        for p2, v1, v2 in ridges:
            if v2 < 0:
                v1, v2 = v2, v1
            if v1 >= 0:
                # finite ridge: already in the region
                continue
            # Compute the missing endpoint of an infinite ridge
            t = vor.points[p2] - vor.points[p1] # tangent
            t /= np.linalg.norm(t)
            n = np.array([-t[1], t[0]])  # normal
            midpoint = vor.points[[p1, p2]].mean(axis=0)
            direction = np.sign(np.dot(midpoint - center, n)) * n
            far_point = vor.vertices[v2] + direction * radius
            new_region.append(len(new_vertices))
            new_vertices.append(far_point.tolist())
        # sort region counterclockwise
        vs = np.asarray([new_vertices[v] for v in new_region])
        c = vs.mean(axis=0)
        angles = np.arctan2(vs[:,1] - c[1], vs[:,0] - c[0])
        new_region = np.array(new_region)[np.argsort(angles)]
        # finish
        new_regions.append(new_region.tolist())
    return new_regions, np.asarray(new_vertices)
#
def flatten_list(nested_list):
    flat_list = []
    for item in nested_list:
        if isinstance(item, list):
            flat_list.extend(flatten_list(item))
        else:
            flat_list.append(item)
    return flat_list
#
def get_finite_region(point_list, points, radius):
    new_point_list = []
    for each in zip(point_list,points):
        polygon = ShapelyPolygon(each[0])
        circle_center = ShapelyPoint(each[1])
        circle_radius = radius
        circle = circle_center.buffer(circle_radius)
        intersection = polygon.intersection(circle)
        #sample the circle point
        inter_point = tuple(intersection.exterior.coords)
        inter_line = []
        sub_line = []
        poly_index = 0
        poly_list = []
        poly_index_list = []
        for inte in inter_point:
            if inte in each[0]:
                if len(sub_line)!=0:
                    #if poly_index ==0:
                    #    poly_index+=1 #aim: poly_index = 1
                    inter_line.append(sub_line)
                    sub_line = []
                    poly_index+=1
                poly_list.append(inte)
                poly_index_list.append(poly_index)
                poly_index+=1
            else:
                sub_line.append(inte)
        #
        trunct_line = []
        for sub in inter_line:
            index_33rd = int(0.33 * len(sub))
            index_66th = int(0.66 * len(sub))
            trunct_line.append([sub[0],sub[index_33rd],sub[index_66th],sub[-1]])
        #
        for i in range(len(poly_index_list)):
            trunct_line.insert(poly_index_list[i],poly_list[i])
        new_point_list.append(flatten_list(trunct_line))
    return new_point_list
#
def infer_finite_voronoi(points,radius):
    vor = Voronoi(points)
    regions, vertices = voronoi_finite_polygons_2d(vor)
    point_list = []
    for each in regions:
        if len(each)>1:
            point_list.append([tuple(vertices[vet]) for vet in each if vet>0])
    #
    new_point_list = get_finite_region(point_list, points, radius)
    return new_point_list
#
def plot_voronoi(voronoi_boundary,image, output ,figsize = (150,150)):
    progress_bar = tqdm(voronoi_boundary, desc="Processing", ncols=100)
    fig ,ax1 = plt.subplots(figsize=figsize, facecolor="white")
    ax1.imshow(image)
    for each in progress_bar:
        if len(each)>0:
            p2 = Polygon(each, facecolor = 'none',edgecolor = 'blue')
            ax1.add_patch(p2)
    progress_bar.close()
    plt.savefig(output)
#
