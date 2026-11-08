#!/usr/bin/env python
import re
import pathlib
import setuptools

# extract the current version
init_file = pathlib.Path(__file__).parent.resolve().joinpath('spyrrow/__init__.py')
init_text = open(init_file, 'rt').read()
pattern = r"^__version__ = ['\"]([^'\"]*)['\"]"
match = re.search(pattern, init_text, re.M)
if not match:
    raise RuntimeError(f'Unable to find __version__ in {init_file}')
version = match.group(1)

# run setup
setuptools.setup(
    name='spyrrow',
    version=version,
    license='Apache 2.0',
    description='Spatial data analysis',
    url='https://github.com/liuyunho/Spyrrow',
    keywords=['spatail data', 'visualization', 'cell-cell communication'],
    packages=setuptools.find_packages(),
    python_requires='>=3.7',
    classifiers=[
        'Intended Audience :: Science/Research',
        'Programming Language :: Python :: 3',
        'License :: OSI Approved :: Apache Software License',
        'Operating System :: OS Independent',
    ],
    install_requires=[
        'packaging',
        'scanpy',
        'pandas',
        'tifffile',
        'numpy',
        'geopandas',
        'opencv-python-headless',  # 'cv2' package name in PyPI
        'tables',
        'IPython',
        'seaborn',
        'scipy',
        'Pillow',  # 'PIL' package name in PyPI
        'matplotlib',
        'shapely',
        'tqdm',
        'einops',
        'SpaGCN',
        'scikit-image',  # 'skimage' package name in PyPI
        'torch',
        'pytorch-lightning',
    ]
)
