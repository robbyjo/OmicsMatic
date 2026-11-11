#from .pixel import cluster, plot_super
from .utils import *
import numpy as np
from einops import reduce
from PIL import Image
import seaborn as sns
import matplotlib.pyplot as plt
from matplotlib.colors import LinearSegmentedColormap
from matplotlib.colors import ListedColormap
import cv2 as cv
import skimage
import multiprocessing
from sklearn.cluster import MiniBatchKMeans, KMeans, AgglomerativeClustering
from sklearn.mixture import GaussianMixture
from hdbscan import HDBSCAN
from scipy.ndimage import label as label_connected
#
Image.MAX_IMAGE_PIXELS = None
#
########################
#the reimputation part
#with structures, gene_signature
def get_gene_counts(prefix, reorder_genes=True):
    cnts = load_tsv(f'{prefix}cnts.tsv')
    #reorder_genes = [ganme for ganme in reorder_genes if ganme in cnts.columns]
    if reorder_genes:
        order = cnts.var().to_numpy().argsort()[::-1]
        cnts = cnts.iloc[:, order]
    return cnts

#
def get_data(genes,prefix):
    #gene_names = read_lines(f'{prefix}gene-names.txt')
    #gene_names = genes
    cnts = get_gene_counts(prefix)
    gene_names = [ganme for ganme in genes if ganme in cnts.columns]
    cnts = cnts[gene_names]
    embs = get_embeddings(prefix)
    # embs = embs[..., :192]  # use high-level features only
    # embs = reduce_embeddings(embs)
    locs = get_locs(prefix, target_shape=embs.shape[:2])
    # embs = add_coords(embs)
    return embs, cnts, locs
#
def predict_single_out(model, z, indices, names, y_range):
    z = torch.tensor(z, device=model.device)
    y = model.lat_to_out(z, indices=indices)
    y = y.cpu().detach().numpy()
    # y[y < 0.01] = 0.0
    # y[y > 1.0] = 1.0
    y *= y_range[:, 1] - y_range[:, 0]
    y += y_range[:, 0]
    return y


def predict_single_lat(model, x):
    x = torch.tensor(x, device=model.device)
    z = model.inp_to_lat(x)
    z = z.cpu().detach().numpy()
    return z
#
def predict(
        model_states, x_batches, name_list, y_range, prefix,
        device='cuda'):

    # states: different initial values for training
    # batches: subsets of observations
    # groups: subsets outcomes

    batch_size_outcome = 100

    model_states = [mod.to(device) for mod in model_states]

    # get features of second last layer
    z_states_batches = [
            [predict_single_lat(mod, x_bat) for mod in model_states]
            for x_bat in x_batches]
    z_point = np.concatenate([
        np.median(z_states, 0)
        for z_states in z_states_batches])
    z_dict = dict(cls=z_point.transpose(2, 0, 1))
    save_pickle(
            z_dict,
            prefix+'embeddings-gene.pickle')
    del z_point

    # predict and save y by batches in outcome dimension
    idx_list = np.arange(len(name_list))
    n_groups_outcome = len(idx_list) // batch_size_outcome + 1
    idx_groups = np.array_split(idx_list, n_groups_outcome)
    for idx_grp in idx_groups:
        name_grp = name_list[idx_grp]
        y_ran = y_range[idx_grp]
        y_grp = np.concatenate([
            np.median([
                predict_single_out(mod, z, idx_grp, name_grp, y_ran)
                for mod, z in zip(model_states, z_states)], 0)
            for z_states in z_states_batches])
        for i, name in enumerate(name_grp):
            save_pickle(y_grp[..., i], f'{prefix}cnts-super/{name}.pickle')

def impute(
        embs, cnts, locs, radius, epochs, batch_size, prefix,
        n_states=1, load_saved=False, device='cuda', n_jobs=1):

    names = cnts.columns
    cnts = cnts.to_numpy()
    cnts = cnts.astype(np.float32)

    __, cnts, __, (cnts_min, cnts_max) = normalize(embs, cnts)

    # mask = np.isfinite(embs).all(-1)
    # embs[~mask] = 0.0

    kwargs_list = [
            dict(
                x=embs, y=cnts, locs=locs, radius=radius,
                batch_size=batch_size, epochs=epochs, lr=1e-4,
                prefix=f'{prefix}states/{i:02d}/',
                load_saved=load_saved, device=device)
            for i in range(n_states)]

    if n_jobs is None or n_jobs < 1:
        n_jobs = n_states
    if n_jobs == 1:
        out_list = [get_model_kwargs(kwargs) for kwargs in kwargs_list]
    else:
        with multiprocessing.Pool(processes=n_jobs) as pool:
            out_list = pool.map(get_model_kwargs, kwargs_list)

    model_list = [out[0] for out in out_list]
    dataset_list = [out[1] for out in out_list]
    mask_size = dataset_list[0].mask.sum()

    # embs[~mask] = np.nan
    cnts_range = np.stack([cnts_min, cnts_max], -1)
    cnts_range /= mask_size

    batch_size_row = 50
    n_batches_row = embs.shape[0] // batch_size_row + 1
    embs_batches = np.array_split(embs, n_batches_row)
    del embs
    predict(
            model_states=model_list, x_batches=embs_batches,
            name_list=names, y_range=cnts_range,
            prefix=prefix, device=device)
#
def re_impute(genes,prefix,output_folder):
    embs, cnts, locs = get_data(genes,f'{prefix}/')
    factor = 16
    radius = int(read_lines(f'{prefix}/radius.txt')[0])
    radius //= factor
    n_train = cnts.shape[0]
    batch_size = min(128, n_train//16)
    impute(
        embs=embs, cnts=cnts, locs=locs, radius=radius,
        epochs=400, batch_size=batch_size,
        n_states=5, prefix=output_folder,
        device='cpu', n_jobs=1)

#


def plot_impute(prefix, gene, output_folder):
    mask = load_image(f'{prefix}mask.png') > 0
    cnts = load_pickle(f'{prefix}cnts-super/{gene}.pickle')
    mask = np.array(Image.fromarray(mask).resize((cnts.shape[1],cnts.shape[0])))
    cnts[~mask] = np.nan
    plot_super(cnts, f'{output_folder}/{gene}.png')

########################
#the analysis part
#with structures, gene_signature
def get_score(prefix,gene_list,factor = 2):
    all_imputed = os.listdir(f'{prefix}/cnts-super/')
    gene_names = gene_list
    cnts = [load_pickle(f'{prefix}/cnts-super/'+gname+'.pickle')
        for gname in gene_names if gname+'.pickle' in all_imputed]
    cnts = np.stack(cnts, -1)
    cnts = reduce(
        cnts, '(h0 h1) (w0 w1) c -> h0 w0 c', 'mean',
        h1=factor, w1=factor)
    cnts -= np.nanmin(cnts, (0, 1))
    cnts /= np.nanmax(cnts, (0, 1)) + 1e-12
    cnts = cnts.mean(-1)
    cnts = np.clip(
        cnts, np.nanquantile(cnts, 0.001),
        np.nanquantile(cnts, 0.999))
    return cnts
#
def load_scores(structures, gene_signature, prefix):
    for name, content in structures.items():
        #score = load_pickle(f'{prefix}{name}.pickle')
        score = get_score(prefix,gene_signature[name])
        content['score'] = score
        if 'subtypes' in content.keys():
            load_scores(content['subtypes'], gene_signature, prefix)
#
def multiclass(
        x, mask, n_clusters=10, adjust_clusters=False,
        threshold_dominance=0.6, threshold_significance=0.1):

    x = np.stack(x)
    x[:, ~mask] = np.nan

    labels, __ = cluster(x, n_clusters=n_clusters, method='km')
    probs = get_centers(labels, x.transpose(1, 2, 0))

    if adjust_clusters:
        signi = probs > threshold_significance
        signi = detect_dominance(signi, probs, threshold_dominance)
        __, label_map = np.unique(
                signi, axis=0, return_inverse=True)
        labels = remap_labels(labels, label_map)
        labels, __ = sort_labels(labels)
        probs = get_centers(labels, x.transpose(1, 2, 0))

    return labels, probs
#
def classify(
        x, mask, prefix,
        use_multiclass=False, n_clusters=10, use_sampling=False,
        adjust_clusters=False):

    if use_multiclass:
        labels, probs = multiclass(
                x, mask, n_clusters=n_clusters,
                adjust_clusters=adjust_clusters)
        out = labels, probs
    else:
        if use_sampling:
            labels = draw_sample(np.stack(x, -1))
        else:
            labels = np.argmax(x, axis=0)
        labels[~mask] = -1
        out = labels
    return out
#
def compare(scores, mask, prefix, cmap):
    normalized = plot_scores(scores, mask, prefix, cmap=cmap)
    normalized = list(normalized.values())
    names = np.array(list(scores.keys()))

    cmap = cmap_myset
    n_colors = len(names)
    plot_cmap(
            cmap=cmap, n=n_colors,
            filename=prefix+'labels-cmap.png')

    labels_maxprob = classify(
            normalized, mask, prefix, use_sampling=False)
    plot_labels(
            labels_maxprob, cmap=cmap, white_background=True,
            filename=prefix+'labels.png')
    write_lines(names, prefix+'labels-names.txt')
    labels_maxprob_sorted, indices_maxprob = sort_labels(labels_maxprob)
    plot_labels(
            labels_maxprob_sorted, cmap=cmap, white_background=True,
            filename=prefix+'labels-sorted.png')
    write_lines(
            names[indices_maxprob], prefix+'labels-sorted-names.txt')

    labels_sampled = classify(
            normalized, mask, prefix, use_sampling=True)
    plot_labels(
            labels_sampled, cmap=cmap, white_background=True,
            filename=prefix+'labels-sampled.png')
    write_lines(names, prefix+'labels-sampled-names.txt')
    labels_sampled_sorted, indices_sampled = sort_labels(labels_sampled)
    plot_labels(
            labels_sampled_sorted, cmap=cmap, white_background=True,
            filename=prefix+'labels-sampled-sorted.png')
    write_lines(
            names[indices_sampled], prefix+'labels-sorted-names.txt')

    # cat_names = list(scores.keys())
    # classify_multiclass(
    #         normalized, mask, cat_names=cat_names,
    #         adjust_clusters=False, cmap=cmap, prefix=prefix)
    # classify_multiclass(
    #         normalized, mask, cat_names=cat_names,
    #         adjust_clusters=True, cmap=cmap, prefix=prefix+'adjusted/')

    labels = labels_maxprob
    return labels
#
def analyze(
        structures, prefix, mask=None,
        fill_with_min=True, upscale_factor=None):
    cmap = 'turbo'
    plot_colorbar(
            cmap, n_labels=None, filename=prefix+'colorbar.png')
    score_dict = {}
    for name, content in structures.items():
        score = content['score'].copy()
        if upscale_factor is not None:
            score = upscale(score, factor=upscale_factor)
        filler_val = np.nan
        if fill_with_min:
            filler_val = np.nanmin(score)
        is_fin = np.isfinite(score)

        if mask is not None:
            score[(~mask)*is_fin] = filler_val
        plot_matrix(
                score, f'{prefix}{name}/score.png', cmap=cmap,
                white_background=True)

        score_dict[name] = score

        if 'threshold' in content.keys():
            threshold = content['threshold']
            mask_sub = score >= threshold
            if mask is not None:
                mask_sub = mask_sub * mask

            score_thresholded = score.copy()
            score_thresholded[(~mask_sub)*is_fin] = filler_val
            plot_matrix(
                    score_thresholded,
                    f'{prefix}{name}/score-thresholded.png',
                    cmap=cmap, white_background=True)
        else:
            mask_sub = mask

        if 'subtypes' in content.keys():
            score_sub_dict = analyze(
                    content['subtypes'], f'{prefix}{name}/',
                    mask=mask_sub)
            compare(
                    score_sub_dict, mask=mask_sub,
                    prefix=f'{prefix}{name}/', cmap=cmap)
    return score_dict
#
def analysis(structures, gene_signature,prefix,output_folder):
    load_scores(structures, gene_signature, prefix)
    analyze(structures, output_folder+'/')

###########
#cluster
def split_by_connected_size_single(labels, min_size):
    # return labels of small and large connected components
    # labels are binary
    labels = label_connected(labels)[0]
    labels -= 1
    labels = sort_labels(labels)[0]
    counts = np.unique(labels[labels >= 0], return_counts=True)[1]
    cut = np.sum(counts >= min_size)
    small = labels - cut
    small[small < 0] = -1
    large = labels.copy()
    large[labels >= cut] = -1
    return small, large

def split_by_connected_size(labels, min_size):
    # return labels of small and large connected components
    # labels can be multi-categorical
    labs_uniq = np.unique(labels[labels >= 0])
    small = np.full_like(labels, -1)
    large = np.full_like(labels, -1)
    for lab in labs_uniq:
        isin = labels == lab
        sma, lar = split_by_connected_size_single(isin, min_size)
        issma = sma >= 0
        islar = lar >= 0
        small[issma] = sma[issma] + small.max() + 1
        large[islar] = lar[islar] + large.max() + 1
    return small, large

def get_adjacent(ind):
    # return the eight adjacent indices
    adj = np.meshgrid([-1, 0, 1], [-1, 0, 1], indexing='ij')
    adj = np.stack(adj, -1)
    adj = adj.reshape(-1, adj.shape[-1])
    adj = adj[(adj != 0).any(-1)]
    adj += ind
    return adj

def relabel_small_connected(labels, min_size):
    # reassign labels of small connected components
    labels = labels.copy()
    small, __ = split_by_connected_size(labels, min_size)
    small = sort_labels(small, descending=False)[0]
    small_uniq = np.unique(small[small >= 0])
    lab_na = min(-1, labels.min() - 1)
    for lab_small in small_uniq:

        isin = small == lab_small
        lab = labels[isin][0]

        # find adjacent labels
        indices = np.stack(np.where(isin), -1)
        labs_adj = []
        labs_small_adj = []
        for ind in indices:
            adj = get_adjacent(ind)
            is_within = np.logical_and(
                    (adj < labels.shape).all(-1),
                    (adj >= 0).all(-1))
            adj[~is_within] = 0  # dummy index for out-of-bound
            la = labels[adj[:, 0], adj[:, 1]]
            lsa = small[adj[:, 0], adj[:, 1]]
            la[~is_within] = lab_na
            lsa[~is_within] = lab_na
            labs_adj.append(la)
            labs_small_adj.append(lsa)
        labs_adj = np.stack(labs_adj)
        labs_small_adj = np.stack(labs_small_adj)
        # eliminate background and identical labels
        is_other = (labs_adj >= 0) * (labs_adj != lab)
        if is_other.any():
            # find most frequent adjacent labels
            lab_new = get_most_frequent(labs_adj[is_other])
            # get location of new label
            i_new, i_adj_new = np.stack(
                    np.where(labs_adj == lab_new), -1)[0]
            ind_new = get_adjacent(indices[i_new])[i_adj_new]
            # update small components
            lab_small_new = small[ind_new[0], ind_new[1]]
        else:
            lab_new = lab
            lab_small_new = lab_small
        # relabel to most frequent neighboring label
        labels[isin] = lab_new
        small[isin] = lab_small_new

    return labels

def prepare_for_clustering(embs, location_weight):
    mask = np.all([np.isfinite(c) for c in embs], axis=0)
    embs = np.stack([c[mask] for c in embs], axis=-1)

    if location_weight is None:
        x = embs
    else:
        embs -= embs.mean(0)
        embs /= embs.var(0).sum()**0.5
        # get spatial coordinates
        locs = np.meshgrid(
                *[np.arange(mask.shape[i]) for i in range(mask.ndim)],
                indexing='ij')
        locs = np.stack(locs, -1).astype(float)
        locs = locs[mask]
        locs -= locs.mean(0)
        locs /= locs.var(0).sum()**0.5

        # balance embeddings and coordinates
        embs *= 1 - location_weight
        locs *= location_weight
        x = np.concatenate([embs, locs], axis=-1)
    return x, mask

def initial_cluster(
        embs, n_clusters, method='mbkm', location_weight=None,
        sort=True):

    x, mask = prepare_for_clustering(embs, location_weight)

    print(f'Clustering pixels using {method}...')
    t0 = time()
    if method == 'mbkm':
        model = MiniBatchKMeans(
                n_clusters=n_clusters,
                # batch_size=x.shape[0]//10, max_iter=1000,
                # max_no_improvement=100, n_init=10,
                random_state=0, verbose=0)
    elif method == 'km':
        model = KMeans(
                n_clusters=n_clusters,
                random_state=0, verbose=0)
    elif method == 'gm':
        model = GaussianMixture(
                n_components=n_clusters,
                covariance_type='diag', init_params='k-means++',
                random_state=0, verbose=1)
    # elif method == 'dbscan':
    #     eps = x.var(0).sum()**0.5 * 0.5
    #     min_samples = 5
    #     model = DBSCAN(eps=eps, min_samples=min_samples, n_jobs=64)
    elif method == 'hdbscan':
        min_cluster_size = min(1000, x.shape[0] // 400 + 1)
        min_samples = min_cluster_size // 10 + 1
        model = HDBSCAN(
                min_cluster_size=min_cluster_size,
                min_samples=min_samples,
                core_dist_n_jobs=64)
    elif method == 'agglomerative':
        # knn_graph = kneighbors_graph(x, n_neighbors=10, include_self=False)
        model = AgglomerativeClustering(
                n_clusters=n_clusters,
                linkage='ward', compute_distances=True)
    else:
        raise ValueError(f'Method `{method}` not recognized')
    print(x.shape)
    labels = model.fit_predict(x)
    print(int(time() - t0), 'sec')
    print('n_clusters:', np.unique(labels).size)

    if sort:
        labels = sort_labels(labels)[0]

    labels_arr = np.full(mask.shape, labels.min()-1, dtype=int)
    labels_arr[mask] = labels

    # if method == 'gm':
    #     probs = model.predict_proba(embs)
    #     probs = probs[:, order]
    #     assert (probs.argmax(-1) == labels).all()
    #     probs_arr = np.full(
    #             mask.shape+(n_clusters,), np.nan, dtype=np.float32)
    #     probs_arr[mask] = probs
    # else:
    #     probs_arr = None

    return labels_arr, model

def cluster(prefix, n_clusters, method='km',min_cluster_size=20):
    embs = load_pickle(prefix+'embeddings-gene.pickle')
    if isinstance(embs, dict):
        if 'cls' in embs.keys():
            x = embs['cls']
        else:
            x = embs['sub']
        x = np.array(x)
    else:
        x = embs
    #
    mask = load_image(prefix+'mask.png') > 0
    mask = np.array(Image.fromarray(mask).resize((x.shape[2],x.shape[1])))
    x[:, ~mask] = np.nan
    #
    labels, __ = initial_cluster(x, method=method, n_clusters=n_clusters)

    if min_cluster_size is not None:
        labels = relabel_small_connected(
                labels, min_size=min_cluster_size)
    return labels

def plot_super(
        x, outfile, underground=None, truncate=None):

    x = x.copy()
    mask = np.isfinite(x)

    if truncate is not None:
        x -= np.nanmean(x)
        x /= np.nanstd(x) + 1e-12
        x = np.clip(x, truncate[0], truncate[1])

    x -= np.nanmin(x)
    x /= np.nanmax(x) + 1e-12

    cmap = plt.get_cmap('turbo')
    # cmap = cmap_turbo_truncated
    if underground is not None:
        under = underground.mean(-1, keepdims=True)
        under -= under.min()
        under /= under.max() + 1e-12

    img = cmap(x)[..., :3]
    if underground is not None:
        img = img * 0.5 + under * 0.5
    img[~mask] = 1.0
    img = (img * 255).astype(np.uint8)
    save_image(img, outfile)