#!/usr/bin/env python3
# 生成「言行 Agent」应用图标（对话气泡 + 行动箭头）
# 概念：言（气泡/对话）+ 行（向上箭头/行动）
from PIL import Image, ImageDraw
import os

# 品牌色
TOP = (128, 105, 216)    # 浅紫
BOTTOM = (91, 75, 181)   # 深紫
BUBBLE = (255, 255, 255)
ARROW = (91, 75, 181)
TAIL = (230, 225, 250)

def make_icon(size):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # 1. 背景：圆角方形，垂直渐变
    radius = int(size * 0.20)
    # 用逐行渐变填充
    bg_layer = Image.new("RGB", (size, size))
    bd = ImageDraw.Draw(bg_layer)
    for y in range(size):
        t = y / (size - 1)
        r = int(TOP[0] + (BOTTOM[0] - TOP[0]) * t)
        g = int(TOP[1] + (BOTTOM[1] - TOP[1]) * t)
        b = int(TOP[2] + (BOTTOM[2] - TOP[2]) * t)
        bd.line([(0, y), (size, y)], fill=(r, g, b))
    # 应用圆角遮罩
    mask = Image.new("L", (size, size), 0)
    md = ImageDraw.Draw(mask)
    md.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    bg_layer.putalpha(mask)
    img = Image.alpha_composite(img, bg_layer.convert("RGBA"))
    draw = ImageDraw.Draw(img)

    # 坐标基于 size，使用比例
    s = size / 192.0

    # 2. 对话气泡（白色圆角矩形 + 底部尾巴）
    box = (36 * s, 52 * s, 156 * s, 120 * s)
    draw.rounded_rectangle(box, radius=24 * s, fill=BUBBLE)

    # 尾巴（三角形）
    tail = [
        (76 * s, 118 * s),
        (104 * s, 148 * s),
        (132 * s, 118 * s),
    ]
    draw.polygon(tail, fill=BUBBLE)

    # 3. 向上的行动箭头（品牌紫色，位于气泡内）
    # 箭头三角形头
    head = [
        (96 * s, 62 * s),   # 顶点
        (70 * s, 84 * s),   # 左下
        (122 * s, 84 * s),  # 右下
    ]
    draw.polygon(head, fill=ARROW)
    # 箭杆
    rod = [88 * s, 82 * s, 104 * s, 114 * s]
    draw.rounded_rectangle(rod, radius=7 * s, fill=ARROW)

    return img

sizes = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

base = "/var/minis/workspace/yanxing-agent/app/src/main/res"
for folder, px in sizes.items():
    out_dir = os.path.join(base, f"mipmap-{folder}")
    os.makedirs(out_dir, exist_ok=True)
    icon = make_icon(px)
    icon.save(os.path.join(out_dir, "ic_launcher.png"))
    print(f"wrote mipmap-{folder}/ic_launcher.png ({px}x{px})")

# 也生成一个 web/store 用的大图
store = make_icon(512)
os.makedirs("/var/minis/workspace/yanxing-agent/app/src/main/mipmap-store", exist_ok=True)
store.save("/var/minis/workspace/yanxing-agent/app/src/main/mipmap-store/ic_launcher_512.png")
print("wrote mipmap-store/ic_launcher_512.png (512x512)")