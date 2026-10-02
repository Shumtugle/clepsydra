# Draws the foreground of the launcher icon: a round flask, half full,
# a ring of the maze inside it, a drop falling into the neck.
from PIL import Image, ImageDraw
import math

S = 432 * 4
img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(img)
c = S / 2
R = S * 0.19
cy = c + S * 0.06
water = Image.new('RGBA', (S, S), (0, 0, 0, 0))
ImageDraw.Draw(water).ellipse([c - R * 0.95, cy - R * 0.95, c + R * 0.95, cy + R * 0.95], fill=(76, 179, 228, 255))
mask = Image.new('L', (S, S), 0)
ImageDraw.Draw(mask).rectangle([0, cy + R * 0.15, S, S], fill=255)
img.paste(water, (0, 0), mask)
glass = (214, 236, 250, 255)
w = int(S * 0.021)
d.ellipse([c - R, cy - R, c + R, cy + R], outline=glass, width=w)
r2 = R * 0.58
d.arc([c - r2, cy - r2, c + r2, cy + r2], start=-15, end=255, fill=glass, width=int(w * 0.8))
a = math.radians(100)
d.line([c + r2 * math.cos(a), cy + r2 * math.sin(a), c + R * math.cos(a) * 0.97, cy + R * math.sin(a) * 0.97],
       fill=glass, width=int(w * 0.8))
nx = R * math.sin(0.2)
ny = R * math.cos(0.2)
d.rectangle([c - nx - w / 2, cy - R * 1.3, c - nx + w / 2, cy - ny], fill=glass)
d.rectangle([c + nx - w / 2, cy - R * 1.3, c + nx + w / 2, cy - ny], fill=glass)
d.rectangle([c - nx + w / 2, cy - R - w, c + nx - w / 2, cy - R + w], fill=(0, 0, 0, 0))
dy = cy - R * 1.55
d.ellipse([c - w * 1.3, dy - w * 1.3, c + w * 1.3, dy + w * 1.3], fill=(76, 179, 228, 255))
img.resize((432, 432), Image.LANCZOS).save('res/drawable-nodpi/ic_fg.png')
