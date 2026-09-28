#!/usr/bin/env python3
"""离线解析 spark .sparkprofile，不需要启动游戏或打开 spark 网页。

`.sparkprofile` 是**原始 protobuf**（不是 gzip），本脚本自带一个够用的 varint 解析器，
不依赖 protobuf 库。

用法::

    python spark_profile.py <profile>                 # 总览 + 无线物流拆解
    python spark_profile.py <profile> --top 30        # 全局自耗时前 30
    python spark_profile.py <profile> --sub StaffLink # 只看名字含 StaffLink 的帧
    python spark_profile.py <profile> --tree 帧名     # 打印某帧的调用子树

## 格式（实测于 spark for NeoForge 1.21.1）

顶层::

    f1  metadata
    f2  调用树（单条线程）
    f3  类表（可重复）  {1: 类名, 2: modid}
    f6  packed varint × 6 —— 6 个时间窗的起始 tick 号
    f7  窗口统计（可重复 × 6）

metadata::

    f2/f11  起止时间(ms)   f3 采样 tick 数   f7 平台串   f12 总 tick 数
    f13     模组列表（每个 {1: modid, 2: {1: modid, 2: 版本, 3: 描述}}）

窗口统计（f7 的 f2 子消息）::

    f1 ticks  f2/f3 CPU  f4 TPS  f5 MSPT  f6 MSPT 峰值
    f7 玩家数  f8 实体数  f9 方块实体数  f10 区块数  f11/f12 起止 ms  f13 时长 ms

调用树::

    f2 载荷: f1 = 线程名, f3 = 节点（可重复）
    节点:  f3 类名  f4 方法名  f6 行号  f7 签名
           f8 48 字节 = 6 个 little-endian double（每个时间窗的耗时 ms）
           f9 packed varint = 子节点下标

    **没有父指针** —— 父节点由「谁把孩子列在 f9 里」反推。
    自耗时 = 自身 6 个 double 之和 − 所有子节点之和。

采样间隔约 1 ms，所以「耗时 ms」≈ 采样点数；本文件的总和应等于采样墙钟时长。
"""
import io
import struct
import sys
from collections import defaultdict

# ---------------------------------------------------------------- protobuf

def _fields(buf):
    """返回 [(字段号, wire 类型, 值)]。wire: 0=int 1=fixed64 2=bytes 5=fixed32"""
    i = 0
    n = len(buf)
    out = []
    while i < n:
        r = 0
        s = 0
        while True:
            c = buf[i]
            i += 1
            r |= (c & 0x7F) << s
            if not (c & 0x80):
                break
            s += 7
        fn, w = r >> 3, r & 7
        if w == 0:
            v = 0
            s = 0
            while True:
                c = buf[i]
                i += 1
                v |= (c & 0x7F) << s
                if not (c & 0x80):
                    break
                s += 7
        elif w == 1:
            v = buf[i:i + 8]
            i += 8
        elif w == 2:
            ln = 0
            s = 0
            while True:
                c = buf[i]
                i += 1
                ln |= (c & 0x7F) << s
                if not (c & 0x80):
                    break
                s += 7
            v = buf[i:i + ln]
            i += ln
        elif w == 5:
            v = buf[i:i + 4]
            i += 4
        else:
            raise ValueError('未知 wire 类型 %d @%d' % (w, i))
        out.append((fn, w, v))
    return out


def _packed(buf):
    out = []
    i = 0
    while i < len(buf):
        r = 0
        s = 0
        while True:
            c = buf[i]
            i += 1
            r |= (c & 0x7F) << s
            if not (c & 0x80):
                break
            s += 7
        out.append(r)
    return out


def _dbl(b):
    return struct.unpack('<d', b)[0]


# ---------------------------------------------------------------- 载入

def load(path):
    d = io.open(path, 'rb').read()
    top = _fields(d)
    tree = windows = None
    ticks = []
    meta = {}
    for fn, w, v in top:
        if fn == 1:
            meta = {a: c for a, b, c in _fields(v)}
        elif fn == 2:
            tree = v
        elif fn == 6:
            windows = _packed(v)
        elif fn == 7:
            ticks.append(v)

    nodes = []
    for fn, w, v in _fields(tree):
        if fn != 3:
            continue
        nd = dict(cls='', method='', line=0, desc='', times=[0.0] * 6, children=[])
        for a, b, c in _fields(v):
            if a == 3:
                nd['cls'] = c.decode('utf-8', 'replace')
            elif a == 4:
                nd['method'] = c.decode('utf-8', 'replace')
            elif a == 6:
                nd['line'] = c
            elif a == 7:
                nd['desc'] = c.decode('utf-8', 'replace')
            elif a == 8:
                nd['times'] = [_dbl(c[k:k + 8]) for k in range(0, len(c) - 7, 8)]
            elif a == 9:
                nd['children'] = _packed(c)
        nodes.append(nd)

    parent = [-1] * len(nodes)
    for i, nd in enumerate(nodes):
        for c in nd['children']:
            parent[c] = i
    for nd in nodes:
        nd['total'] = sum(nd['times'])
    for nd in nodes:
        nd['self'] = nd['total'] - sum(nodes[c]['total'] for c in nd['children'])

    # 窗口统计：tick 号 -> 窗口序号
    win_meta = {}
    order = {w: i for i, w in enumerate(windows or [])}
    for t in ticks:
        no = st = None
        for a, b, c in _fields(t):
            if a == 1:
                no = c
            elif a == 2:
                st = c
        m = {}
        for a, b, c in _fields(st):
            if b == 0:
                m[a] = c
            elif b == 1:
                m[a] = _dbl(c)
        win_meta[order.get(no, -1)] = m
    return dict(meta=meta, nodes=nodes, parent=parent, windows=windows, win_meta=win_meta)


def label(nd):
    return '%s.%s' % (nd['cls'], nd['method'])


def find(nodes, needle, first=True):
    hits = [i for i, nd in enumerate(nodes) if needle.lower() in label(nd).lower()]
    hits.sort(key=lambda i: -nodes[i]['total'])
    return hits if not first else (hits[0] if hits else -1)


def subtree(nodes, root):
    seen = set()
    stack = [root]
    while stack:
        i = stack.pop()
        if i in seen:
            continue
        seen.add(i)
        stack.extend(nodes[i]['children'])
    return seen


def selfprofile(nodes, inside):
    acc = defaultdict(float)
    for i in inside:
        acc[label(nodes[i])] += nodes[i]['self']
    return acc


# ---------------------------------------------------------------- 输出

def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    path = sys.argv[1]
    args = sys.argv[2:]
    p = load(path)
    meta, nodes, parent = p['meta'], p['nodes'], p['parent']
    roots = [i for i in range(len(nodes)) if parent[i] < 0]

    print('=' * 78)
    print('文件       :', path)
    print('采样 tick  :', meta.get(3), '  总 tick:', meta.get(12))
    if meta.get(2) and meta.get(11):
        print('墙钟跨度   : %.1f s' % ((meta[11] - meta[2]) / 1000.0))
    plat = meta.get(7)
    if isinstance(plat, bytes):
        parts = []
        for a, b, c in _fields(plat):
            if b == 2:
                parts.append(c.decode('utf-8', 'replace'))
            elif b == 0:
                parts.append(str(c))
        print('平台       :', ' / '.join(parts))
    print('调用树节点 : %d   根: %s' % (len(nodes), label(nodes[roots[0]]) if roots else '-'))

    # 模组列表里找 useless_mod
    mods = meta.get(13)
    if isinstance(mods, bytes):
        for _, _, v in _fields(mods):
            kv = {a: c for a, b, c in _fields(v)}
            if kv.get(1, b'').decode('utf-8', 'replace') == 'useless_mod':
                sub = {a: c for a, b, c in _fields(kv[2])}
                print('本模组版本 :', sub.get(2, b'').decode('utf-8', 'replace'))

    # ---- 分窗口 ----
    tick_srv = find(nodes, 'IntegratedServer.tickServer')
    staff = find(nodes, 'EventHandler.onStaffLinkTick')
    if tick_srv >= 0 and p['win_meta']:
        print()
        print('%-4s %7s %8s %7s %7s %12s %12s %8s' % (
            '窗口', 'ticks', '时长(s)', 'TPS', 'MSPT', 'tickServer', 'onStaffLink', '份额'))
        T = S = 0.0
        for i in sorted(p['win_meta']):
            m = p['win_meta'][i]
            tt = nodes[tick_srv]['times'][i] if i < 6 else 0.0
            ss = nodes[staff]['times'][i] if staff >= 0 and i < 6 else 0.0
            T += tt
            S += ss
            print('%-4d %7d %8.1f %7.2f %7.2f %12.1f %12.1f %7.1f%%' % (
                i, m.get(1, 0), m.get(13, 0) / 1000.0, m.get(4, 0), m.get(5, 0),
                tt, ss, 100 * ss / tt if tt else 0))
        print('%-4s %7s %8s %7s %7s %12.1f %12.1f %7.1f%%' % (
            '合计', '', '', '', '', T, S, 100 * S / T if T else 0))

    # ---- 无线物流拆解 ----
    if staff >= 0:
        inside = subtree(nodes, staff)
        tot = nodes[staff]['total']
        print()
        print('=' * 78)
        print('无线物流 onStaffLinkTick = %.0f ms（占 tickServer %.1f%%）'
              % (tot, 100 * tot / nodes[tick_srv]['total'] if tick_srv >= 0 else 0))
        print('-- 自耗时归集（加起来 == 上面的总数）--')
        acc = selfprofile(nodes, inside)
        for k, v in sorted(acc.items(), key=lambda kv: -kv[1])[:20]:
            if v < tot * 0.005:
                break
            print('   %9.1f  %5.1f%%  %s' % (v, 100 * v / tot, k))
        print('-- 关键帧 inclusive --')
        keys = ['ItemEndpoint.snapshot', 'ItemEndpoint.findSlot', 'ItemEndpoint.extract',
                'LongResourceAdapters$1.insert', 'LongResourceAdapters$1.extract',
                'LongResourceAdapters$1.getStackInSlot', 'LongResourceAdapters$1.amountIn',
                'GenericStackItemStorage.insertItem', 'GenericStackItemStorage.extractItem',
                'AEItemKey.of', 'Platform.copyStackWithSize', 'GenericStackInv.insert',
                'InterfaceLogic.updatePlan', 'ItemStack.copyWithCount',
                'StaffLinkTargets.resolve', 'StaffLinkTargets.moveItems',
                'StaffLinkTargets.moveOneItemStack', 'SignalGetter.getBestNeighborSignal']
        for w in keys:
            h = [i for i in inside if label(nodes[i]).endswith(w)]
            if h:
                i = max(h, key=lambda j: nodes[j]['total'])
                print('   %9.1f  %5.1f%%  %s' % (nodes[i]['total'],
                                                 100 * nodes[i]['total'] / tot, w))

    # ---- 全局 ----
    if '--top' in args:
        n = int(args[args.index('--top') + 1]) if len(args) > args.index('--top') + 1 else 25
        print()
        print('-- 全局自耗时前 %d --' % n)
        for i in sorted(range(len(nodes)), key=lambda i: -nodes[i]['self'])[:n]:
            print('   self=%9.1f tot=%9.1f  %s' % (
                nodes[i]['self'], nodes[i]['total'], label(nodes[i])))

    if '--sub' in args:
        pat = args[args.index('--sub') + 1]
        print()
        print('-- 名字含 %r 的帧 --' % pat)
        for i in sorted(range(len(nodes)), key=lambda i: -nodes[i]['total']):
            if pat.lower() in label(nodes[i]).lower():
                print('   tot=%9.1f self=%9.1f  %s' % (
                    nodes[i]['total'], nodes[i]['self'], label(nodes[i])))

    if '--tree' in args:
        pat = args[args.index('--tree') + 1]
        r = find(nodes, pat)
        if r < 0:
            print('未找到', pat)
            return
        print()
        print('-- 子树 %s --' % label(nodes[r]))

        def walk(i, depth):
            nd = nodes[i]
            print('   %s%-68s tot=%9.1f self=%9.1f' % (
                '  ' * depth, label(nd)[-68:], nd['total'], nd['self']))
            if depth >= 6:
                return
            for c in sorted(nd['children'], key=lambda c: -nodes[c]['total'])[:8]:
                if nodes[c]['total'] < nd['total'] * 0.02:
                    continue
                walk(c, depth + 1)
        walk(r, 0)


if __name__ == '__main__':
    main()
