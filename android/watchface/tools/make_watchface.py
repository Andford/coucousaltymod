#!/usr/bin/env python3
"""Builds Mochi's watch face (Watch Face Format 2) — res/raw/watchface.xml,
the cookie backdrop and the preview. Run from android/watchface:
    python3 tools/make_watchface.py
Mochi is drawn with WFF shapes (flat, like the app's 2D look); the cookie
and the preview are rendered here with Pillow."""
import math, os
from PIL import Image, ImageDraw, ImageFont

RES = os.path.join(os.path.dirname(__file__), '..', 'src', 'main', 'res')

# ── Colours (Material You-ish pairs: accent, dark container) ─────────────────
THEMES = [("lavanda", "#C4B5FD", "#2A2440"), ("rosa", "#F9A8D4", "#3A1F2E"), ("menta", "#86EFAC", "#15301F"),
          ("cielo", "#93C5FD", "#172A40"), ("durazno", "#FDBA74", "#3A2615")]
MOCHIS = [("blanco", "#F5F6F8", "#6E7080"), ("lila", "#DDD6FE", "#6D5BB0"), ("rosa", "#FBCFE8", "#B0577F"),
          ("menta", "#BBF7D0", "#3F8A5C"), ("cielo", "#BFDBFE", "#4A6FA8"), ("durazno", "#FED7AA", "#B0703A")]

# ── Mochi geometry (450 × 450) ────────────────────────────────────────────────
MX, MY, MW, MH = 140, 238, 170, 128          # body box
EYE_W, EYE_H = 18, 26
EYE_L, EYE_R, EYE_Y = MX + 48, MX + MW - 48 - EYE_W, MY + 44
C = lambda k, i: f"[CONFIGURATION.{k}.{i}]"
BOB = "4 * sin(6.28318 * ((([SECOND] % 4) * 1000 + [MILLISECOND]) / 4000.0))"
SPIN = "(((([MINUTE] % 2) * 60 + [SECOND]) * 1000 + [MILLISECOND]) / 120000.0) * 360"

def font(size, weight="BOLD", color="#FFFFFF"):
    return f'<Font family="SYNC_TO_DEVICE" size="{size}" weight="{weight}" color="{color}"/>'

def text(x, y, w, h, tmpl, params, size, weight="BOLD", color="#FFFFFF", align="CENTER", extra=""):
    ps = ''.join(f'<Parameter expression="{p}"/>' for p in params)
    return (f'<PartText x="{x}" y="{y}" width="{w}" height="{h}">{extra}<Text align="{align}" ellipsis="TRUE">'
            f'<Font family="SYNC_TO_DEVICE" size="{size}" weight="{weight}" color="{color}">'
            f'<Template><![CDATA[{tmpl}]]>{ps}</Template></Font></Text></PartText>')

def eyes(kind):
    """open | closed (sleepy lines) | happy (^ ^)"""
    out = []
    for ex in (EYE_L, EYE_R):
        if kind == "open":
            out.append(f'<Ellipse x="{ex}" y="{EYE_Y}" width="{EYE_W}" height="{EYE_H}"><Fill color="#1B1A22"/></Ellipse>')
        elif kind == "closed":
            out.append(f'<Line startX="{ex - 2}" startY="{EYE_Y + 16}" endX="{ex + EYE_W + 2}" endY="{EYE_Y + 16}"><Stroke color="#1B1A22" thickness="5" cap="ROUND"/></Line>')
        else:
            out.append(f'<Arc centerX="{ex + EYE_W / 2}" centerY="{EYE_Y + 18}" width="{EYE_W + 8}" height="{EYE_W + 8}" startAngle="-70" endAngle="70"><Stroke color="#1B1A22" thickness="5" cap="ROUND"/></Arc>')
    return f'<PartDraw x="0" y="0" width="450" height="450">{"".join(out)}</PartDraw>'

def complication(slot, x, y, name, default, dtype):
    s = 92
    bg = f'<PartDraw x="0" y="0" width="{s}" height="{s}"><Ellipse x="0" y="0" width="{s}" height="{s}"><Fill color="{C("theme", 1)}"/></Ellipse></PartDraw>'
    def t(yy, expr, size):
        return text(8, yy, s - 16, 28, "%s", [expr], size, "SEMI_BOLD", C("theme", 0))
    img = lambda yy, sz: f'<PartImage x="{(s - sz) // 2}" y="{yy}" width="{sz}" height="{sz}" tintColor="{C("theme", 0)}"><Image resource="[COMPLICATION.MONOCHROMATIC_IMAGE]"/></PartImage>'
    ranged = (f'<PartDraw x="6" y="6" width="{s - 12}" height="{s - 12}">'
              f'<Arc centerX="{(s - 12) / 2}" centerY="{(s - 12) / 2}" width="{s - 12}" height="{s - 12}" startAngle="-140" endAngle="140"><Stroke color="#40FFFFFF" thickness="7" cap="ROUND"/></Arc>'
              f'<Arc centerX="{(s - 12) / 2}" centerY="{(s - 12) / 2}" width="{s - 12}" height="{s - 12}" startAngle="-140" endAngle="140"><Stroke color="{C("theme", 0)}" thickness="7" cap="ROUND"/>'
              f'<Transform target="endAngle" value="-140 + (clamp([COMPLICATION.RANGED_VALUE_VALUE], [COMPLICATION.RANGED_VALUE_MIN], [COMPLICATION.RANGED_VALUE_MAX]) - [COMPLICATION.RANGED_VALUE_MIN]) / ([COMPLICATION.RANGED_VALUE_MAX] - [COMPLICATION.RANGED_VALUE_MIN]) * 280"/></Arc></PartDraw>')
    return f'''<ComplicationSlot x="{x}" y="{y}" width="{s}" height="{s}" slotId="{slot}" displayName="{name}" supportedTypes="SHORT_TEXT RANGED_VALUE MONOCHROMATIC_IMAGE SMALL_IMAGE EMPTY">
  <DefaultProviderPolicy defaultSystemProvider="{default}" defaultSystemProviderType="{dtype}"/>
  <BoundingOval x="0" y="0" width="{s}" height="{s}"/>
  <Complication type="SHORT_TEXT">{bg}{img(16, 26)}{t(46, "[COMPLICATION.TEXT]", 22)}</Complication>
  <Complication type="RANGED_VALUE">{bg}{ranged}{img(20, 22)}{t(44, "[COMPLICATION.TEXT]", 20)}</Complication>
  <Complication type="MONOCHROMATIC_IMAGE">{bg}{img(24, 44)}</Complication>
  <Complication type="SMALL_IMAGE">{bg}<PartImage x="18" y="18" width="56" height="56"><Image resource="[COMPLICATION.SMALL_IMAGE]"/></PartImage></Complication>
</ComplicationSlot>'''

def watchface():
    opts = lambda arr: ''.join(f'<ColorOption id="{i}" displayName="{n}" colors="{a} {b}"/>' for i, (n, a, b) in enumerate(arr))
    body = (f'<PartDraw x="0" y="0" width="450" height="450">'
            f'<RoundRectangle x="{MX}" y="{MY}" width="{MW}" height="{MH}" cornerRadiusX="62" cornerRadiusY="56"><Fill color="{C("mochi", 0)}"/></RoundRectangle>'
            f'<RoundRectangle x="{MX}" y="{MY}" width="{MW}" height="{MH}" cornerRadiusX="62" cornerRadiusY="56"><Stroke color="{C("mochi", 1)}" thickness="4"/></RoundRectangle>'
            f'<Ellipse x="{EYE_L - 22}" y="{EYE_Y + 30}" width="30" height="14"><Fill color="#55F472B6"/></Ellipse>'
            f'<Ellipse x="{EYE_R + 10}" y="{EYE_Y + 30}" width="30" height="14"><Fill color="#55F472B6"/></Ellipse>'
            f'</PartDraw>')
    ambient_body = (f'<PartDraw x="0" y="0" width="450" height="450">'
                    f'<RoundRectangle x="{MX}" y="{MY}" width="{MW}" height="{MH}" cornerRadiusX="62" cornerRadiusY="56"><Stroke color="#9A9AA6" thickness="4"/></RoundRectangle>'
                    f'</PartDraw>')
    sleepy = "([HOUR_0_23] &gt;= 23) || ([HOUR_0_23] &lt; 6)"
    happy = "[STEP_PERCENT] &gt;= 100"
    blink = "(([SECOND] % 6) == 0) &amp;&amp; ([MILLISECOND] &lt; 160)"
    face = f'''<Condition>
      <Expressions>
        <Expression name="sleepy"><![CDATA[([HOUR_0_23] >= 23) || ([HOUR_0_23] < 6)]]></Expression>
        <Expression name="happy"><![CDATA[[STEP_PERCENT] >= 100]]></Expression>
        <Expression name="blink"><![CDATA[(([SECOND] % 6) == 0) && ([MILLISECOND] < 160)]]></Expression>
      </Expressions>
      <Compare expression="sleepy">{eyes("closed")}</Compare>
      <Compare expression="happy">{eyes("happy")}</Compare>
      <Compare expression="blink">{eyes("closed")}</Compare>
      <Default>{eyes("open")}</Default>
    </Condition>'''
    steps_arc = (f'<PartDraw x="0" y="0" width="450" height="450">'
                 f'<Arc centerX="225" centerY="225" width="426" height="426" startAngle="125" endAngle="235"><Stroke color="{C("theme", 1)}" thickness="12" cap="ROUND"/></Arc>'
                 f'<Arc centerX="225" centerY="225" width="426" height="426" startAngle="125" endAngle="235"><Stroke color="{C("theme", 0)}" thickness="12" cap="ROUND"/>'
                 f'<Transform target="endAngle" value="125 + clamp([STEP_PERCENT], 0, 100) / 100.0 * 110"/></Arc>'
                 f'</PartDraw>')
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<!-- Mochi · Material 3 Expressive. Generated by tools/make_watchface.py — edit that, not this. -->
<WatchFace width="450" height="450">
  <Metadata key="CLOCK_TYPE" value="DIGITAL"/>
  <Metadata key="PREVIEW_TIME" value="10:08:32"/>
  <UserConfigurations>
    <ColorConfiguration id="theme" displayName="theme_label" defaultValue="0">{opts(THEMES)}</ColorConfiguration>
    <ColorConfiguration id="mochi" displayName="mochi_label" defaultValue="0">{opts(MOCHIS)}</ColorConfiguration>
  </UserConfigurations>
  <Scene backgroundColor="#FF000000">
    <!-- The date, small, above. -->
    <Group name="date" x="0" y="0" width="450" height="450">
      {text(0, 34, 450, 34, "%s", ['icuText(&quot;EEE d&quot;)'], 24, "SEMI_BOLD", C("theme", 0))}
    </Group>
    <!-- The time: hour in the accent, minutes in white, big and bold. -->
    <DigitalClock x="0" y="0" width="450" height="450">
      <TimeText format="hh" hourFormat="SYNC_TO_DEVICE" align="END" x="20" y="78" width="192" height="130">
        <Font family="SYNC_TO_DEVICE" size="122" weight="BLACK" color="{C("theme", 0)}"/>
      </TimeText>
      <TimeText format="mm" align="START" x="238" y="78" width="192" height="130">
        <Font family="SYNC_TO_DEVICE" size="122" weight="BLACK" color="#FFFFFFFF"/>
      </TimeText>
    </DigitalClock>
    <!-- The cookie behind Mochi, turning slowly. -->
    <PartImage x="105" y="182" width="240" height="240" tintColor="{C("theme", 1)}">
      <Variant mode="AMBIENT" target="alpha" value="0"/>
      <Transform target="angle" value="{SPIN}"/>
      <Image resource="cookie"/>
    </PartImage>
    <!-- Mochi, breathing; tap him to open Mochi. -->
    <Group name="mochi" x="0" y="0" width="450" height="450">
      <Launch target="mx.loperstack.mochi/mx.loperstack.mochi.wear.WearActivity"/>
      <Transform target="y" value="{BOB}"/>
      <Group name="mochi_color" x="0" y="0" width="450" height="450">
        <Variant mode="AMBIENT" target="alpha" value="0"/>
        {body}
      </Group>
      <Group name="mochi_ambient" x="0" y="0" width="450" height="450" alpha="0">
        <Variant mode="AMBIENT" target="alpha" value="255"/>
        {ambient_body}
      </Group>
      <Group name="mochi_face" x="0" y="0" width="450" height="450">
        {face}
      </Group>
    </Group>
    <!-- Steps, as an expressive arc along the bottom. -->
    <Group name="steps" x="0" y="0" width="450" height="450">
      <Variant mode="AMBIENT" target="alpha" value="0"/>
      {steps_arc}
    </Group>
    {complication(0, 34, 256, "left_label", "HEART_RATE", "SHORT_TEXT")}
    {complication(1, 324, 256, "right_label", "WATCH_BATTERY", "RANGED_VALUE")}
  </Scene>
</WatchFace>
'''

def cookie(path, size=400, n=12, depth=0.045):
    s = size * 4
    img = Image.new("L", (s, s), 0)
    d = ImageDraw.Draw(img)
    R = s * 0.46
    pts = []
    for i in range(720):
        a = 2 * math.pi * i / 720
        r = R * (1 - depth + depth * math.cos(n * a))
        pts.append((s / 2 + r * math.cos(a), s / 2 + r * math.sin(a)))
    d.polygon(pts, fill=255)
    img = img.resize((size, size), Image.LANCZOS)
    out = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    out.putalpha(img)
    out.save(path)

def preview(path):
    s = 450 * 2
    k = 2
    img = Image.new("RGB", (s, s), "#000000")
    d = ImageDraw.Draw(img)
    acc, cont = THEMES[0][1], THEMES[0][2]
    body, line = MOCHIS[0][1], MOCHIS[0][2]
    try:
        big = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 118 * k)
        small = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 24 * k)
    except OSError:
        big = small = ImageFont.load_default()
    d.text((225 * k, 50 * k), "mié 21", fill=acc, font=small, anchor="mt")
    d.text((212 * k, 140 * k), "10", fill=acc, font=big, anchor="rm")
    d.text((238 * k, 140 * k), "08", fill="#FFFFFF", font=big, anchor="lm")
    ck = Image.open(os.path.join(RES, "drawable", "cookie.png")).resize((240 * k, 240 * k))
    tint = Image.new("RGBA", ck.size, cont)
    tint.putalpha(ck.getchannel("A"))
    img.paste(tint, (105 * k, 182 * k), tint)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([MX * k, MY * k, (MX + MW) * k, (MY + MH) * k], radius=58 * k, fill=body, outline=line, width=4 * k)
    for ex in (EYE_L, EYE_R):
        d.ellipse([ex * k, EYE_Y * k, (ex + EYE_W) * k, (EYE_Y + EYE_H) * k], fill="#1B1A22")
    d.ellipse([(EYE_L - 22) * k, (EYE_Y + 30) * k, (EYE_L + 8) * k, (EYE_Y + 44) * k], fill="#F7C6DD")
    d.ellipse([(EYE_R + 10) * k, (EYE_Y + 30) * k, (EYE_R + 40) * k, (EYE_Y + 44) * k], fill="#F7C6DD")
    d.arc([12 * k, 12 * k, 438 * k, 438 * k], 35, 145, fill=cont, width=12 * k)
    d.arc([12 * k, 12 * k, 438 * k, 438 * k], 35, 95, fill=acc, width=12 * k)
    for cx in (34 + 46, 324 + 46):
        d.ellipse([(cx - 46) * k, 256 * k, (cx + 46) * k, 348 * k], fill=cont)
    d.text(((34 + 46) * k, 300 * k), "72", fill=acc, font=small, anchor="mm")
    d.text(((324 + 46) * k, 300 * k), "84%", fill=acc, font=small, anchor="mm")
    mask = Image.new("L", (s, s), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, s, s], fill=255)
    out = Image.new("RGB", (s, s), "#000000")
    out.paste(img, (0, 0), mask)
    out.resize((450, 450), Image.LANCZOS).save(path)

if __name__ == "__main__":
    os.makedirs(os.path.join(RES, "raw"), exist_ok=True)
    os.makedirs(os.path.join(RES, "drawable"), exist_ok=True)
    open(os.path.join(RES, "raw", "watchface.xml"), "w").write(watchface())
    cookie(os.path.join(RES, "drawable", "cookie.png"))
    preview(os.path.join(RES, "drawable", "preview.png"))
    print("ok")
