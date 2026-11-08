import pandas as pd
import pickle
import os
import numpy as np
from einops import reduce
from PIL import Image
import seaborn as sns
import matplotlib.pyplot as plt
from matplotlib.colors import LinearSegmentedColormap
from matplotlib.colors import ListedColormap
import cv2 as cv
import skimage
import torch
from torch.utils.data import Dataset
import pytorch_lightning as pl
from torch.optim import Adam
from torch import nn
from torch.utils.data import DataLoader
from copy import deepcopy
from time import time
import tifffile as tiff
#
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
    #print(filename)
    return x

def load_tsv(filename, index=True):
    if index:
        index_col = 0
    else:
        index_col = None
    df = pd.read_csv(filename, sep='\t', header=0, index_col=index_col)
    #print(f'Dataframe loaded from {filename}')
    return df

def read_lines(filename):
    with open(filename, 'r') as file:
        lines = [line.rstrip() for line in file]
    return lines

def load_image(filename, verbose=False):
    if filename.endswith('.tiff'):
        img = tiff.imread(filename)
    else:
        img = Image.open(filename)
        img = np.array(img)
    if img.ndim == 3 and img.shape[-1] == 4:
        img = img[..., :3]  # remove alpha channel
    if verbose:
        print(f'Image loaded from {filename}')
    return img
###########
#from istar
def mat_to_img(
        x, white_background=True, transparent_background=False,
        cmap='turbo', minmax=None):
    mask = np.isfinite(x)
    x = x.astype(np.float32)
    if minmax is None:
        minmax = (np.nanmin(x), np.nanmax(x) + 1e-12)
    print('minmax:', minmax)
    x -= minmax[0]
    x /= minmax[1] - minmax[0]
    if isinstance(cmap, str):
        cmap = plt.get_cmap(cmap)
    x = cmap(x)
    if white_background:
        x[~mask] = 1.0
    if transparent_background:
        x[~mask, -1] = 0.0
    x = (x * 255).astype(np.uint8)
    return x
#
def save_image(img, filename):
    mkdir(filename)
    Image.fromarray(img).save(filename)
    print(filename)
#
def plot_matrix(x, outfile, **kwargs):
    img = mat_to_img(x, **kwargs)
    save_image(img, outfile)
#
def cmap_tab20(x):
    cmap = plt.get_cmap('tab20')
    x = x % 20
    x = (x // 10) + (x % 10) * 2
    return cmap(x)
#
def cmap_tab30(x):
    n_base = 20
    n_max = 30
    brightness = 0.7
    brightness = (brightness,) * 3 + (1.0,)
    isin_base = (x < n_base)[..., np.newaxis]
    isin_extended = ((x >= n_base) * (x < n_max))[..., np.newaxis]
    isin_beyond = (x >= n_max)[..., np.newaxis]
    color = (
        isin_base * cmap_tab20(x)
        + isin_extended * cmap_tab20(x-n_base) * brightness
        + isin_beyond * (0.0, 0.0, 0.0, 1.0))
    return color
#
def cmap_tab70(x):
    cmap_base = cmap_tab30
    brightness = 0.5
    brightness = np.array([brightness] * 3 + [1.0])
    color = [
        cmap_base(x),  # same as base colormap
        1 - (1 - cmap_base(x-20)) * brightness,  # brighter
        cmap_base(x-20) * brightness,  # darker
        1 - (1 - cmap_base(x-40)) * brightness**2,  # even brighter
        cmap_base(x-40) * brightness**2,  # even darker
        [0.0, 0.0, 0.0, 1.0],  # black
        ]
    x = x[..., np.newaxis]
    isin = [
        (x < 30),
        (x >= 30) * (x < 40),
        (x >= 40) * (x < 50),
        (x >= 50) * (x < 60),
        (x >= 60) * (x < 70),
        (x >= 70)]
    color_out = np.sum(
            [isi * col for isi, col in zip(isin, color)],
            axis=0)
    return color_out
#
def get_cmap_tab_multi(n_colors, n_shades, paired=True):
    cmap_base = cmap_tab20
    n_base = 10
    assert n_colors <= n_base

    def cmap(x):
        isin = x >= 0
        x = x * isin

        # lightness
        i = x // n_colors
        if paired:
            is_odd = i % 2 == 1
            i[~is_odd] //= 2
            i[is_odd] = n_shades - 1 - (i[is_odd] - 1) // 2

        # hue
        j = x % n_colors
        colors = np.stack(
                [cmap_base(k)[..., :3] for k in [j, j+n_base]])

        # compute color from hue and lightness
        weights = 1 - i / max(1, n_shades - 1)
        weights = np.stack([weights, 1-weights])
        col = (weights[..., np.newaxis] * colors).sum(0)
        col[~isin] = 0
        return col

    return cmap
#
def get_cmap_discrete(n_colors, cmap_name):
    cmap_base = plt.get_cmap(cmap_name)

    def cmap(x):
        x = x / float(n_colors-1)
        return cmap_base(x)

    return cmap
#
def interlaced_cmap(cmap, stop, stride, start=0):

    def cmap_new(x):
        isin = x >= 0
        x[isin] = (x[isin] * stride + start) % stop
        return cmap(x)

    return cmap_new


def reversed_cmap(cmap, stop):

    def cmap_new(x):
        isin = x >= 0
        x[isin] = stop - x[isin] - 1
        return cmap(x)

    return cmap_new
#
def plot_labels(
        labels, filename, cmap=None, white_background=True,
        transparent_background=False,
        interlace=False, reverse=False):
    if labels.ndim == 3:
        n_labels = labels[..., 0].max() + 1
        n_shades = labels[..., 1].max() + 1
        isin = (labels >= 0).all(-1)
        labels_uni = labels[..., 0].copy()
        labels_uni[isin] = (
                n_labels * labels[isin][..., -1]
                + labels[isin][..., 0])
        labels = labels_uni
    elif labels.ndim == 2:
        n_labels = labels.max() + 1
        n_shades = 1
    if cmap is None:
        if n_labels <= 70:
            cmap = 'tab70'
        else:
            cmap = 'turbo'
    if cmap == 'tab70':
        cmap = cmap_tab70
    elif cmap == 'turbo':
        cmap = plt.get_cmap('turbo')
        labels = labels / labels.max()
    elif cmap == 'multi':
        cmap = get_cmap_tab_multi(n_labels, n_shades)
    elif isinstance(cmap, str):
        cmap = plt.get_cmap(cmap)
    if interlace:
        cmap = interlaced_cmap(
                cmap, stop=n_labels, start=0, stride=9)
    if reverse:
        cmap = reversed_cmap(cmap, stop=n_labels)
    image = cmap(labels)
    mask_extra = labels < 0
    mask_background = (labels == labels.min()) * mask_extra
    image[mask_extra] = 0.5  # gray
    if white_background:
        background_color = 1.0  # white
    else:
        background_color = 0.0  # black
    image[mask_background] = background_color
    if transparent_background:
        image[mask_background, -1] = 0.0
    image = (image * 255).astype(np.uint8)
    if filename is not None:
        save_image(image, filename)
    return image
#
def plot_colorbar(cmap, n_labels, filename):

    size = (200, 200)

    if n_labels is None:
        x = np.linspace(0, 1, size[0]*10)
    else:
        x = np.arange(n_labels)
        x = np.repeat(x, size[0])

    x = np.tile(x[:, np.newaxis], size[1])
    x = x.swapaxes(0, 1)

    if n_labels is None:
        plot_matrix(x, filename, cmap=cmap)
    else:
        plot_labels(x, filename, cmap=cmap)
#
def impute_missing(x, mask, radius=3, method='ns'):

    method_dict = {
            'telea': cv.INPAINT_TELEA,
            'ns': cv.INPAINT_NS}
    method = method_dict[method]

    x = x.copy()
    if x.dtype == np.float64:
        x = x.astype(np.float32)

    x[mask] = 0
    mask = mask.astype(np.uint8)

    expand_dim = np.ndim(x) == 2
    if expand_dim:
        x = x[..., np.newaxis]
    channels = [x[..., i] for i in range(x.shape[-1])]
    y = [cv.inpaint(c, mask, radius, method) for c in channels]
    y = np.stack(y, -1)
    if expand_dim:
        y = y[..., 0]

    return y
#
def upsize(x, target_shape):
    mask = np.isfinite(x).all(tuple(range(2, x.ndim)))
    x = impute_missing(x, ~mask, radius=3)
    # TODO: Consider using pytorch with cuda to speed up
    # order: 0 == nearest neighbor, 1 == bilinear, 3 == bicubic
    dtype = x.dtype
    x = skimage.transform.resize(
            x, target_shape, order=3, preserve_range=True)
    x = x.astype(dtype)
    if not mask.all():
        mask = skimage.transform.resize(
                mask.astype(float), target_shape, order=3,
                preserve_range=True)
        mask = mask > 0.5
        x[~mask] = np.nan
    return x
#
def upscale(x, factor):
    if factor is not None and factor > 1:
        shape = np.array(x.shape) * factor
        x = upsize(x, shape)
    return x
#
def plot_scores(scores, mask, prefix, cmap):
    scores_arr = np.array(list(scores.values()))
    minmax = np.nanmin(scores_arr), np.nanmax(scores_arr)
    score_tot = np.sum(scores_arr, axis=0)
    score_tot[~mask] = 1.0
    normalized = {}
    for name, s in scores.items():
        plot_matrix(
                s,
                f'{prefix}{name}/score-scaled.png',
                minmax=minmax, cmap=cmap,
                white_background=True)
        s = s / score_tot
        plot_matrix(
                s,
                f'{prefix}{name}/score-normalized.png',
                minmax=(0.0, 1.0), cmap=cmap,
                white_background=True)
        normalized[name] = s
    return normalized
#
def cmap_myset(x):
    cmap = ListedColormap([
        '#17BECF',  # cyan
        '#FD8D3C',  # orange
        '#A6D854',  # light green
        '#9467BD',  # purple
        '#A5A5A5',  # gray
        '#F4CAE4',  # light pink
        '#C47A3D',  # brown
        '#FFF800',  # yellow
        ])
    return cmap(x)
#
def plot_cmap(
        cmap, n, filename, fmt='01d', label_inside=False, label_left=True):
    if isinstance(cmap, str):
        cmap = plt.get_cmap(cmap)
    colors = [cmap(i) for i in range(n)]
    cmap = LinearSegmentedColormap.from_list(
            'mycmap', colors, len(colors))
    df = pd.DataFrame(np.arange(n)[: np.newaxis])
    df.columns = ['Cluster']
    sns.heatmap(
            df, cmap=cmap,
            annot=label_inside, fmt=fmt, annot_kws={'fontsize': 20},
            square=True, linewidth=0.5, cbar=False)
    plt.tick_params(
            axis='both', which='major',
            bottom=False, labelbottom=False,
            top=False, labeltop=True,
            left=False, labelleft=label_left,
            right=False, labelright=False)
    if label_left:
        plt.yticks(rotation=0)
    plt.savefig(filename, dpi=300, bbox_inches='tight')
    plt.close()
    print(filename)
#
def get_centers(labels, x):
    n_labels = labels.max() + 1
    n_features = x.shape[-1]
    centers = np.full((n_labels, n_features), np.nan)
    for i in range(n_labels):
        isin = labels == i
        centers[i] = x[isin].mean(0)
    return centers
#
def detect_dominance(signi, probs, threshold):
    assert threshold >= 0.5
    for i in range(probs.shape[0]):
        if (probs[i] > threshold).any():
            j = probs[i].argmax()
            signi[i] = False
            signi[i, j] = True
    return signi
#
def remap_labels(labels, label_map):
    labels_new = labels.copy()
    for i in range(labels.max()+1):
        labels_new[labels == i] = label_map[i]
    return labels_new
#
def sort_labels(labels, descending=True):
    labels = labels.copy()
    isin = labels >= 0
    labels_uniq, labels[isin], counts = np.unique(
            labels[isin], return_inverse=True, return_counts=True)
    c = counts
    if descending:
        c = c * (-1)
    order = c.argsort()
    rank = order.argsort()
    labels[isin] = rank[labels[isin]]
    return labels, labels_uniq[order]

def draw_sample(x):
    mask = np.isfinite(x).all(-1)
    shape = x.shape[:-1]
    x = x[mask]
    r = np.random.rand(x.shape[0]) * x.sum(1)
    np.cumsum(x, axis=1)
    is_above = r[..., np.newaxis] > np.cumsum(x, axis=1)
    idx = np.sum(is_above, axis=1)
    label = np.full(shape, -1)
    label[mask] = idx
    return label

#
def write_lines(strings, filename):
    mkdir(filename)
    with open(filename, 'w') as file:
        for s in strings:
            file.write(f'{s}\n')
    print(filename)

def get_gene_counts(prefix, reorder_genes=True):
    cnts = load_tsv(f'{prefix}cnts.tsv')
    if reorder_genes:
        order = cnts.var().to_numpy().argsort()[::-1]
        cnts = cnts.iloc[:, order]
    return cnts


def get_embeddings(prefix):
    embs = load_pickle(f'{prefix}embeddings-hist.pickle')
    embs = np.concatenate([embs['cls'], embs['sub'], embs['rgb']])
    embs = embs.transpose(1, 2, 0)
    return embs

def get_locs(prefix, target_shape=None):

    locs = load_tsv(f'{prefix}locs.tsv')

    # change xy coordinates to ij coordinates
    locs = np.stack([locs['y'], locs['x']], -1)

    # match coordinates of embeddings and spot locations
    if target_shape is not None:
        wsi = load_image(f'{prefix}he.jpg')
        current_shape = np.array(wsi.shape[:2])
        rescale_factor = current_shape // target_shape
        locs = locs.astype(float)
        locs /= rescale_factor

    # find the nearest pixel
    locs = locs.round().astype(int)

    return locs

def normalize(embs, cnts):

    embs = embs.copy()
    cnts = cnts.copy()

    # TODO: check if adjsut_weights in extract_features can be skipped
    embs_mean = np.nanmean(embs, (0, 1))
    embs_std = np.nanstd(embs, (0, 1))
    embs -= embs_mean
    embs /= embs_std + 1e-12

    cnts_min = cnts.min(0)
    cnts_max = cnts.max(0)
    cnts -= cnts_min
    cnts /= (cnts_max - cnts_min) + 1e-12

    return embs, cnts, (embs_mean, embs_std), (cnts_min, cnts_max)

def get_model_kwargs(kwargs):
    return get_model(**kwargs)

class FeedForward(nn.Module):

    def __init__(
            self, n_inp, n_out,
            activation=None, residual=False):
        super().__init__()
        self.linear = nn.Linear(n_inp, n_out)
        if activation is None:
            # TODO: change activation to LeakyRelu(0.01)
            activation = nn.LeakyReLU(0.1, inplace=True)
        self.activation = activation
        self.residual = residual

    def forward(self, x, indices=None):
        if indices is None:
            y = self.linear(x)
        else:
            weight = self.linear.weight[indices]
            bias = self.linear.bias[indices]
            y = nn.functional.linear(x, weight, bias)
        y = self.activation(y)
        if self.residual:
            y = y + x
        return y


class ELU(nn.Module):

    def __init__(self, alpha, beta):
        super().__init__()
        self.activation = nn.ELU(alpha=alpha, inplace=True)
        self.beta = beta

    def forward(self, x):
        return self.activation(x) + self.beta


class ForwardSumModel(pl.LightningModule):

    def __init__(self, lr, n_inp, n_out):
        super().__init__()
        self.lr = lr
        self.net_lat = nn.Sequential(
                FeedForward(n_inp, 256),
                FeedForward(256, 256),
                FeedForward(256, 256),
                FeedForward(256, 256))
        self.net_out = FeedForward(
                256, n_out,
                activation=ELU(alpha=0.01, beta=0.01))
        self.save_hyperparameters()

    def inp_to_lat(self, x):
        return self.net_lat.forward(x)

    def lat_to_out(self, x, indices=None):
        x = self.net_out.forward(x, indices)
        return x

    def forward(self, x, indices=None):
        x = self.inp_to_lat(x)
        x = self.lat_to_out(x, indices)
        return x

    def training_step(self, batch, batch_idx):
        x, y_mean = batch
        y_pred = self.forward(x)
        y_mean_pred = y_pred.mean(-2)
        # TODO: try l1 loss
        mse = ((y_mean_pred - y_mean)**2).mean()
        loss = mse
        self.log('rmse', mse**0.5, prog_bar=True)
        return loss

    def configure_optimizers(self):
        optimizer = Adam(self.parameters(), lr=self.lr)
        return optimizer

def get_disk_mask(radius, boundary_width=None):
    radius_ceil = np.ceil(radius).astype(int)
    locs = np.meshgrid(
            np.arange(-radius_ceil, radius_ceil+1),
            np.arange(-radius_ceil, radius_ceil+1),
            indexing='ij')
    locs = np.stack(locs, -1)
    distsq = (locs**2).sum(-1)
    isin = distsq <= radius**2
    if boundary_width is not None:
        isin *= distsq >= (radius-boundary_width)**2
    return isin

def get_patches_flat(img, locs, mask):
    shape = np.array(mask.shape)
    center = shape // 2
    r = np.stack([-center, shape-center], -1)  # offset
    x_list = []
    for s in locs:
        #if ((s[0]+r[0][0])<0) | (s[0]+r[0][1]<0) | (s[1]+r[1][0]<0) | (s[1]+r[1][1]<0):
        #    x = np.zeros(patch.shape)
        #else:
        patch = img[
                s[0]+r[0][0]:s[0]+r[0][1],
                s[1]+r[1][0]:s[1]+r[1][1]]
        if mask.all():
            x = patch
        else:
            x = patch[mask]
        #x = patch
        x_list.append(x)
    x_list = np.stack(x_list)
    return x_list

def plot_spot_masked_image(
        locs, values, mask, size, outfile=None):
    mask_indices = np.stack(np.where(mask), -1)
    shape = np.array(mask.shape)
    offset = (-1) * shape // 2
    locs = locs + offset
    img = np.full(size, np.nan)
    for loc, val in zip(locs, values):
        indices = loc + mask_indices
        img[indices[:, 0], indices[:, 1]] = val
    plot_matrix(img, outfile)

class SpotDataset(Dataset):

    def __init__(self, x_all, y, locs, radius):
        super().__init__()
        mask = get_disk_mask(radius)
        x = get_patches_flat(x_all, locs, mask)
        isin = np.isfinite(x).all((-1, -2))
        self.x = x[isin]
        self.y = y[isin]
        self.locs = locs[isin]
        self.size = x_all.shape[:2]
        self.radius = radius
        self.mask = mask

    def __len__(self):
        return len(self.x)

    def __getitem__(self, idx):
        return self.x[idx], self.y[idx]

    def show(self, channel_x, channel_y, prefix):
        mask = self.mask
        size = self.size
        locs = self.locs
        xs = self.x
        ys = self.y

        plot_spot_masked_image(
                locs=locs, values=xs[:, :, channel_x], mask=mask, size=size,
                outfile=f'{prefix}x{channel_x:04d}.png')

        plot_spot_masked_image(
                locs=locs, values=ys[:, channel_y], mask=mask, size=size,
                outfile=f'{prefix}y{channel_y:04d}.png')

def plot_history(history, prefix):
    plt.figure(figsize=(16, 16))
    groups = set([e.split('_')[-1] for e in history[0].keys()])
    groups = np.sort(list(groups))
    for i, grp in enumerate(groups):
        plt.subplot(len(groups), 1, 1+i)
        for metric in history[0].keys():
            if metric.endswith(grp):
                hist = np.array([e[metric] for e in history])
                hmin, hmax = hist.min(), hist.max()
                label = f'{metric} ({hmin:+013.6f}, {hmax:+013.6f})'
                hist -= hmin
                hist /= hmax + 1e-12
                plt.plot(hist, label=label)
        plt.legend()
        plt.ylim(0, 1)
    outfile = f'{prefix}history.png'
    plt.savefig(outfile, dpi=300)
    plt.close()
    print(outfile)

class MetricTracker(pl.Callback):

    def __init__(self):
        self.collection = []

    def on_train_epoch_end(self, trainer, *args, **kwargs):
        metrics = deepcopy(trainer.logged_metrics)
        self.collection.append(metrics)

    def clean(self):
        keys = [set(e.keys()) for e in self.collection]
        keys = set().union(*keys)
        for elem in self.collection:
            for ke in keys:
                if ke in elem.keys():
                    if isinstance(elem[ke], torch.Tensor):
                        elem[ke] = elem[ke].item()
                else:
                    elem[ke] = float('nan')

def train_model(
        dataset, batch_size, epochs,
        model=None, model_class=None, model_kwargs={},
        device='cuda'):
    if model is None:
        model = model_class(**model_kwargs)
    dataloader = DataLoader(
            dataset, batch_size=batch_size,
            shuffle=True)
    tracker = MetricTracker()
    device_accelerator_dict = {
            'cuda': 'gpu',
            'cpu': 'cpu'}
    accelerator = device_accelerator_dict[device]
    trainer = pl.Trainer(
            max_epochs=epochs,
            callbacks=[tracker],
            deterministic=True,
            accelerator=accelerator,
            devices=1,
            logger=False,
            enable_checkpointing=False,
            enable_progress_bar=True)
    model.train()
    t0 = time()
    trainer.fit(model=model, train_dataloaders=dataloader)
    print(int(time() - t0), 'sec')
    tracker.clean()
    history = tracker.collection
    return model, history, trainer

def train_load_model(
        model_class, model_kwargs, dataset, prefix,
        epochs=None, device='cuda', load_saved=False, **kwargs):
    checkpoint_file = prefix + 'model.pt'
    history_file = prefix + 'history.pickle'

    # load model if exists
    if load_saved and os.path.exists(checkpoint_file):
        model = model_class.load_from_checkpoint(checkpoint_file)
        print(f'Model loaded from {checkpoint_file}')
        history = load_pickle(history_file)
    else:
        model = None
        history = []

    # train model
    if (epochs is not None) and (epochs > 0):
        model, hist, trainer = train_model(
            model=model,
            model_class=model_class, model_kwargs=model_kwargs,
            dataset=dataset, epochs=epochs, device=device,
            **kwargs)
        trainer.save_checkpoint(checkpoint_file)
        print(f'Model saved to {checkpoint_file}')
        history += hist
        save_pickle(history, history_file)
        print(f'History saved to {history_file}')
        plot_history(history, prefix)

    return model

def get_model(
        x, y, locs, radius, prefix, batch_size, epochs, lr,
        load_saved=False, device='cuda'):

    print('x:', x.shape, ', y:', y.shape)

    x = x.copy()

    dataset = SpotDataset(x, y, locs, radius)
    #breakpoint()
    dataset.show(
            channel_x=0, channel_y=0,
            prefix=f'{prefix}training-data-plots/')
    model = train_load_model(
            model_class=ForwardSumModel,
            model_kwargs=dict(
                n_inp=x.shape[-1],
                n_out=y.shape[-1],
                lr=lr),
            dataset=dataset, prefix=prefix,
            batch_size=batch_size, epochs=epochs,
            load_saved=load_saved, device=device)
    model.eval()
    if device == 'cuda':
        torch.cuda.empty_cache()
    return model, dataset

def get_most_frequent(x):
    # return the most frequent element in array
    uniqs, counts = np.unique(x, return_counts=True)
    return uniqs[counts.argmax()]
    
