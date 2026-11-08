#!/usr/bin/env python3
"""Gera o banner (320x180), o ícone e o logótipo da Estudo TV como vetores Android e SVG de pré-visualização.
Arte original: um ecrã com um livro aberto e um ▶, e as letras «ESTUDO TV» em píxeis.
Uso: python3 tools/arte.py   (escreve em app/src/main/res e em docs/arte/)"""
import os

FUNDO, FAIXA, VERDE, ROSA, BRANCO, AZULADO = "#1A1033", "#2A1650", "#7DFF8A", "#FF6FB5", "#FFFFFF", "#EAF6FF"

FONTE = {  # 5x7
    "E": ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    "S": ["01111", "10000", "10000", "01110", "00001", "00001", "11110"],
    "T": ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
    "U": ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    "D": ["11110", "10001", "10001", "10001", "10001", "10001", "11110"],
    "O": ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    "V": ["10001", "10001", "10001", "10001", "01010", "01010", "00100"],
}

def texto_pixel(txt, x, y, p):
    """Caminho (path data) com os píxeis de cada linha juntos num só retângulo (sem frestas)."""
    d = []
    for i, ch in enumerate(txt):
        for r, linha in enumerate(FONTE[ch]):
            c = 0
            while c < 5:
                if linha[c] == "1":
                    f = c
                    while f < 5 and linha[f] == "1":
                        f += 1
                    X, Y, W = x + (i * 6 + c) * p, y + r * p, (f - c) * p
                    d.append(f"M{X},{Y}h{W}v{p}h-{W}z")
                    c = f
                else:
                    c += 1
    return "".join(d)

def poli(pts):
    return "M" + "L".join(f"{a},{b}" for a, b in pts) + "Z"

def ret_arred(x, y, w, h, r):
    return (f"M{x+r},{y}H{x+w-r}A{r},{r} 0 0 1 {x+w},{y+r}V{y+h-r}A{r},{r} 0 0 1 {x+w-r},{y+h}"
            f"H{x+r}A{r},{r} 0 0 1 {x},{y+h-r}V{y+r}A{r},{r} 0 0 1 {x+r},{y}Z")

def emblema(ox, oy, s):
    """Ecrã + livro + ▶, desenhado numa caixa de 100x110 a partir de (ox, oy), escala s."""
    T = lambda pts: [(round(ox + a * s, 2), round(oy + b * s, 2)) for a, b in pts]
    k = lambda v: round(v * s, 2)
    return [
        # ecrã (contorno verde)
        ("stroke", VERDE, k(6), ret_arred(round(ox + 3 * s, 2), round(oy + 3 * s, 2), k(94), k(88), k(14))),
        # pé da TV
        ("fill", VERDE, 0, poli(T([(42, 94), (58, 94), (58, 102), (42, 102)]))),
        ("fill", VERDE, 0, poli(T([(26, 102), (74, 102), (74, 108), (26, 108)]))),
        # livro aberto
        ("fill", BRANCO, 0, poli(T([(14, 24), (48, 18), (50, 76), (14, 80)]))),
        ("fill", AZULADO, 0, poli(T([(52, 18), (86, 24), (86, 80), (50, 76)]))),
        # ▶ cor-de-rosa sobre as páginas
        ("fill", ROSA, 0, poli(T([(38, 32), (38, 66), (68, 49)]))),
    ]

def vetor(nome, w_dp, h_dp, vw, vh, formas, fundo=None):
    linhas = ['<?xml version="1.0" encoding="utf-8"?>',
              '<!-- Gerado por tools/arte.py: não editar à mão. -->',
              f'<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="{w_dp}dp" '
              f'android:height="{h_dp}dp" android:viewportWidth="{vw}" android:viewportHeight="{vh}">']
    if fundo:
        linhas.append(f'    <path android:fillColor="{fundo}" android:pathData="M0,0H{vw}V{vh}H0Z" />')
    for tipo, cor, larg, d in formas:
        if tipo == "fill":
            linhas.append(f'    <path android:fillColor="{cor}" android:pathData="{d}" />')
        else:
            linhas.append(f'    <path android:strokeColor="{cor}" android:strokeWidth="{larg}" android:fillColor="#00000000" android:pathData="{d}" />')
    linhas.append("</vector>")
    open(nome, "w").write("\n".join(linhas) + "\n")

def svg(nome, vw, vh, formas, fundo=None):
    s = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {vw} {vh}" width="{vw*2}" height="{vh*2}">']
    if fundo:
        s.append(f'<rect width="{vw}" height="{vh}" fill="{fundo}"/>')
    for tipo, cor, larg, d in formas:
        s.append(f'<path d="{d}" fill="{cor}"/>' if tipo == "fill" else f'<path d="{d}" fill="none" stroke="{cor}" stroke-width="{larg}"/>')
    s.append("</svg>")
    open(nome, "w").write("".join(s))

raiz = os.path.join(os.path.dirname(__file__), "..")
res = os.path.join(raiz, "app/src/main/res")
os.makedirs(os.path.join(raiz, "docs/arte"), exist_ok=True)

# Banner 320x180 (= 160x90 dp em xhdpi)
banner = [("fill", FAIXA, 0, poli([(0, 0), (150, 0), (90, 180), (0, 180)]))]
banner += emblema(18, 34, 1.0)
banner.append(("fill", BRANCO, 0, texto_pixel("ESTUDO", 132, 52, 5)))
banner.append(("fill", ROSA, 0, texto_pixel("TV", 132, 100, 5)))
vetor(os.path.join(res, "drawable/banner.xml"), 160, 90, 320, 180, banner, FUNDO)
svg(os.path.join(raiz, "docs/arte/banner.svg"), 320, 180, banner, FUNDO)

# Ícone adaptativo: primeiro plano 108x108 (zona segura: círculo de 66 ao centro)
icone = emblema(26.5, 23.7, 0.55)
vetor(os.path.join(res, "drawable/ic_launcher_frente.xml"), 108, 108, 108, 108, icone)
svg(os.path.join(raiz, "docs/arte/icone.svg"), 108, 108, icone, FUNDO)

# Logótipo (ecrã «Sobre»)
logo = emblema(5, 0, 1.0)
vetor(os.path.join(res, "drawable/logo.xml"), 96, 96, 110, 110, logo)
print("ok")
