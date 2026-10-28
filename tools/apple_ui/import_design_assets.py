#!/usr/bin/env python3
"""Import the supplied Liquid Glass slices without altering their artwork."""
import argparse
import json
from pathlib import Path
import shutil
from PIL import Image


def geometry(key, image):
    width, height = image.size
    category = key.split('_')[0]
    box = [0, 0, width, height]
    corner = 8
    fixed = False
    if category == 'ninepatch':
        corner = {'window': 25, 'card': 17, 'group': 12, 'field': 10, 'slot': 8, 'pill': 16, 'progress': 4}.get(key.split('_')[1], 16)
        if key == 'ninepatch_field_focus': box = [4, 4, 32, 32]; corner = 10
        elif key == 'ninepatch_pill_blue': box = [16, 16, 40, 32]
        elif key in ('ninepatch_popup', 'ninepatch_tooltip', 'ninepatch_window_panel_shadow'):
            body = 48 if key == 'ninepatch_popup' else 40 if key == 'ninepatch_tooltip' else 72
            box = [120, 120, body, body]; corner = 16 if body == 48 else 12 if body == 40 else 25
        fixed = key in ('ninepatch_window_panel', 'ninepatch_card', 'ninepatch_group_list', 'ninepatch_popup', 'ninepatch_tooltip', 'ninepatch_window_panel_shadow')
    elif category in ('window', 'dropdown', 'dialog', 'tooltip', 'toast') and 'item_selected' not in key:
        # Shadow alpha stays below the body alpha. Store bounds at import time,
        # never scan pixels per frame or infer dimensions from filename alone.
        bounds = image.getchannel('A').point(lambda a: 255 if a >= 100 else 0).getbbox()
        if bounds: box = [bounds[0], bounds[1], bounds[2]-bounds[0], bounds[3]-bounds[1]]
        corner = {'window': 25, 'dropdown': 17, 'dialog': 17, 'tooltip': 13, 'toast': 18}[category]
        fixed = category != 'toast'
    elif category == 'button':
        corner = 13 if '_sm_' in key else 16
        if 'primary' in key and 'disabled' not in key: box = [9, 6, 72 if '_sm_' in key else 120, 26 if '_sm_' in key else 32]
    elif category == 'segmented':
        corner = 8 if 'thumb' in key else 10
        if 'thumb' in key: box = [6, 3, 116, 28]
    elif category == 'switch':
        box = [0, 0, 44, 26]; corner = 13
        if 'disabled' in key: box = [0, 0, 44, 26]
    elif category == 'slot':
        size = int(key.rsplit('_', 1)[1]); pad = 2 if 'selected' in key or 'search_hit' in key else 1 if 'hover' in key else 0
        box = [pad, pad, size, size]; corner = 8 if size >= 32 else 4
    elif category == 'field':
        if 'focus' in key or 'error' in key: box = [3, 3, 200, 32]
        corner = 10
    elif category == 'card':
        corner = 16; fixed = True
        if key == 'card_white_400x200': box = [0, 0, 400, 200]
    elif category in ('badge','progress','energybar','pager','iconbutton','icontile','row','scrollbar','stepper','titlebar'):
        corner = {'badge':12,'progress':4,'energybar':5,'pager':8,'iconbutton':10,'icontile':8,'row':12,'scrollbar':3,'stepper':10,'titlebar':24}[category]
        if category == 'icontile': corner = {20:6,28:8,40:11,56:14}[width]
        if category == 'row': fixed = True
    else:
        corner = 0
    return {'body':box, 'corner':corner, 'fixedCorners':fixed}


def main():
    parser=argparse.ArgumentParser(); parser.add_argument('source',type=Path)
    parser.add_argument('--html-reference', type=Path, help='Also import the original item icons used by the reference pages')
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[2]/'src/main/resources/assets/useless_mod'
    target=root/'textures/gui/design'; target.mkdir(parents=True,exist_ok=True)
    meta=root/'design'
    catalog=meta/'slice_geometry.json'
    required=set(json.loads(catalog.read_text(encoding='utf-8')))
    slices={}
    for source in sorted((args.source/'slices').rglob('*.png')):
        if '@' not in source.stem: continue
        stem,density=source.stem.rsplit('@',1)
        key=stem.lower()
        if key not in required: continue
        relative=source.relative_to(args.source/'slices')
        destination=target/str(relative).replace('@','_').lower()
        with Image.open(source) as image:
            if density=='1x':
                slices[key]={'path':destination.relative_to(target).as_posix().replace('_1x.png',''), 'size':list(image.size), **geometry(key,image.convert('RGBA'))}
        if density not in ('2x','4x'): continue
        destination.parent.mkdir(parents=True,exist_ok=True)
        shutil.copy2(source,destination)
        destination.with_suffix('.png.mcmeta').write_text('{"texture":{"blur":true,"clamp":true}}\n',encoding='utf-8')
    reference = args.html_reference or args.source.parent/'html_reference'
    if (reference/'tex').is_dir():
        for source in sorted((reference/'tex').glob('*.png')):
            key = 'reference_' + source.stem.lower()
            if key not in required: continue
            destination = target/'reference'/source.name.lower()
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(source, destination)
            destination.with_suffix('.png.mcmeta').write_text('{"texture":{"blur":false,"clamp":true}}\n', encoding='utf-8')
            with Image.open(source) as image:
                slices[key] = {'path':'reference/'+source.stem.lower(), 'size':list(image.size), 'body':[0,0,*image.size], 'corner':0, 'fixedCorners':False}
    missing=required-slices.keys()
    if missing: raise ValueError(f'Missing required components: {sorted(missing)}')
    for key,data in slices.items():
        names=[data['path']+'.png'] if key.startswith('reference_') else [data['path']+'_'+density+'.png' for density in ('2x','4x')]
        for name in names:
            if not (target/name).is_file(): raise ValueError(f'Missing required artwork: {name}')
    catalog.write_text(json.dumps(slices,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
    print(f'Imported {len(slices)} required components at their runtime densities.')


if __name__=='__main__':main()
