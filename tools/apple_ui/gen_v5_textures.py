"""Generate the shared rounded-rectangle mask used by AppleUi.nine."""
from PIL import Image, ImageDraw
import json, os
D = os.path.join(os.path.dirname(__file__), '../../src/main/resources/assets/useless_mod/textures/gui/apple')
S = 8
def save(name, alpha):
    im = Image.new('RGBA', alpha.size, (255, 255, 255, 0)); im.putalpha(alpha); im.save(os.path.join(D, name + '.png'))
    json.dump({"texture": {"blur": True, "clamp": True}}, open(os.path.join(D, name + '.png.mcmeta'), 'w'))
big = Image.new('L', (128 * S, 128 * S), 0)
ImageDraw.Draw(big).rounded_rectangle([0, 0, 128 * S - 1, 128 * S - 1], 32 * S, fill=255)
save('v5_round', big.resize((128, 128), Image.LANCZOS))
