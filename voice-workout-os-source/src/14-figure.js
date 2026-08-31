/* ============================================================
   14-figure.js : the body figure
   Every muscle is an authored outline that follows its real origin,
   belly and insertion — the pec fans from the sternum and converges
   to a narrow tendon at the humerus, the deltoid's three heads meet
   at the deltoid tuberosity a third of the way down the arm, the lat
   sweeps from the lumbar spine up to a narrow armpit insertion, the
   vastus medialis is a low teardrop and the gastrocnemius is two
   heads sitting over the soleus.

   Coordinates are authored once against a reference male in a
   240 x 540 box. The female figure is the same anatomy under a
   y-dependent horizontal warp: narrower shoulders and ribcage,
   wider hips.
   ============================================================ */
(function (root) {
  'use strict';
  const VOS = (root.VOS = root.VOS || {});

  const VIEWBOX = { w: 240, h: 540 };
  const CX = 120;

  /* ---------- Catmull-Rom through the points, as cubic beziers ---------- */
  function smooth(pts, closed, tension) {
    const t = tension == null ? 0.5 : tension;
    if (!pts || pts.length < 3) return '';
    const p = closed ? pts.concat([pts[0]]) : pts;
    const n = p.length;
    let d = `M ${p[0][0].toFixed(1)} ${p[0][1].toFixed(1)}`;
    for (let i = 0; i < n - 1; i++) {
      const p0 = p[i - 1] || (closed ? p[n - 2] : p[0]);
      const p1 = p[i], p2 = p[i + 1];
      const p3 = p[i + 2] || (closed ? p[1] : p2);
      const c1x = p1[0] + (p2[0] - p0[0]) / 6 * t * 2;
      const c1y = p1[1] + (p2[1] - p0[1]) / 6 * t * 2;
      const c2x = p2[0] - (p3[0] - p1[0]) / 6 * t * 2;
      const c2y = p2[1] - (p3[1] - p1[1]) / 6 * t * 2;
      d += ` C ${c1x.toFixed(1)} ${c1y.toFixed(1)}, ${c2x.toFixed(1)} ${c2y.toFixed(1)}, ${p2[0].toFixed(1)} ${p2[1].toFixed(1)}`;
    }
    return d + (closed ? ' Z' : '');
  }

  const mirror = pts => pts.map(([x, y]) => [2 * CX - x, y]);

  /* ============================================================
     FRONT — right side of the body, authored on the reference male
     ============================================================ */
  const FRONT = {
    /* sternocleidomastoid: mastoid behind the ear to the sternal notch */
    neck: [
      [[125, 72], [132, 79], [131, 93], [126, 102], [121.5, 102], [121.5, 80]]
    ],

    /* upper trapezius: only the slope from neck to acromion */
    trap_upper: [
      [[122, 80], [130, 86], [143, 94], [154, 101], [147, 106], [133, 104], [123, 97]]
    ],

    /* pectoralis major, clavicular head: medial clavicle converging to a
       narrow insertion on the humerus, which is why the fibres run upward */
    chest_upper: [
      [[124, 104], [133, 102], [142, 104], [150, 111], [155, 120], [156, 127],
       [149, 124], [141, 118], [132, 112], [125, 110]]
    ],
    /* sternal head: broad vertical origin down the sternum, same insertion */
    chest_mid: [
      [[124, 114], [131, 115], [139, 119], [147, 125], [152, 132], [154, 140],
       [152, 148], [146, 153], [138, 157], [131, 160], [126, 162], [124, 160],
       [124, 145], [124, 128]]
    ],

    /* deltoid heads capping the shoulder and converging to the tuberosity */
    delt_front: [
      [[146, 100], [157, 99], [165, 112], [168, 134], [166, 158], [158, 160], [154, 132], [148, 110]]
    ],
    delt_side: [
      [[158, 98], [170, 105], [175, 124], [173, 146], [168, 162], [161, 159], [163, 132], [160, 110]]
    ],

    /* serratus slips interdigitating with the oblique */
    serratus: [
      [[145, 149], [152, 152], [147, 160], [137, 157]],
      [[145, 162], [152, 165], [147, 173], [136, 170]],
      [[144, 175], [150, 178], [145, 186], [135, 182]]
    ],

    /* rectus abdominis: a narrow column with three blocks above the navel */
    abs_upper: [
      [[122, 167], [132, 168], [133, 181], [122, 181]],
      [[122, 184], [133, 185], [133.5, 198], [122, 198]],
      [[122, 201], [133.5, 202], [133, 215], [122, 215]],
      [[118, 167], [108, 168], [107, 181], [118, 181]],
      [[118, 184], [107, 185], [106.5, 198], [118, 198]],
      [[118, 201], [106.5, 202], [107, 215], [118, 215]]
    ],
    abs_lower: [
      [[122, 218], [133, 219], [131, 240], [122, 247]],
      [[118, 218], [107, 219], [109, 240], [118, 247]]
    ],

    /* external oblique: a flank band from the lower ribs to the iliac crest */
    oblique: [
      [[135, 175], [140, 184], [143, 196], [144, 210], [142, 224], [138, 236],
       [135, 240], [133.5, 228], [133, 212], [133, 196], [134, 184]]
    ],

    /* biceps spindle plus brachialis showing lateral of it near the elbow */
    biceps: [
      [[165, 124], [159, 140], [157, 164], [161, 188], [170, 204], [178, 200],
       [177, 176], [175, 150], [172, 130]],
      [[176, 172], [184, 178], [186, 200], [178, 202]]
    ],
    /* flexors medial, extensors lateral, both inside the sleeve */
    forearm: [
      [[170, 209], [178, 208], [184, 240], [192, 275], [196, 292], [189, 294],
       [183, 268], [175, 236], [169, 215]],
      [[182, 207], [192, 206], [198, 238], [204, 272], [205, 290], [197, 292],
       [196, 262], [190, 232], [184, 212]]
    ],

    /* vastus lateralis high and outer, rectus femoris central, vastus
       medialis as the low teardrop, sartorius crossing them diagonally */
    quad: [
      [[147, 258], [152, 270], [154, 287], [153, 305], [149, 326], [145, 346],
       [142, 341], [143, 318], [144, 298], [144, 279], [143.5, 264]],
      [[136, 256], [141, 263], [142.5, 281], [142.5, 302], [141, 324], [139, 348],
       [135.5, 349], [134, 323], [134, 301], [134, 280], [134, 263]],
      [[130, 310], [135, 317], [137, 332], [136, 348], [132, 358], [128, 356],
       [126, 342], [126.5, 326], [128, 316]],
      [[136, 253], [139, 258], [134, 300], [129, 338], [126.5, 354], [124, 350],
       [127, 314], [131.5, 274]]
    ],
    adductor: [
      [[125, 254], [133, 259], [134, 292], [130, 316], [124, 313], [123, 282]]
    ],

    /* tibialis anterior lateral to the shin bone */
    tibialis: [
      [[132, 393], [136, 399], [138, 414], [138, 432], [136, 448], [133, 456],
       [131, 448], [130.5, 430], [131, 412]]
    ]
  };

  /* ============================================================
     BACK
     ============================================================ */
  const BACK = {
    neck: [
      [[122, 74], [129, 79], [130, 93], [126, 101], [121.5, 101]]
    ],

    trap_upper: [
      [[121, 78], [130, 84], [144, 93], [155, 102], [148, 110], [132, 110], [121.5, 102]]
    ],
    /* middle and lower trapezius converging on the lower thoracic spine */
    trap_mid: [
      [[121.5, 114], [131, 116], [140, 120], [146, 126], [140, 133], [130, 137], [121.5, 136]],
      [[121.5, 141], [130, 144], [137, 150], [136, 161], [129, 176], [121.5, 190]]
    ],

    delt_rear: [
      [[147, 101], [158, 101], [166, 114], [168, 136], [166, 158], [158, 160], [154, 130]]
    ],
    delt_side: [
      [[159, 99], [170, 106], [175, 125], [173, 147], [168, 162], [161, 159], [163, 133], [161, 111]]
    ],

    /* latissimus: lumbar origin sweeping up to a narrow armpit insertion */
    lat: [
      [[150, 133], [153, 146], [153, 162], [150, 178], [145, 193], [138, 206],
       [131, 212], [127, 206], [126, 192], [126.5, 176], [128, 160], [131, 148],
       [136, 139], [143, 134]]
    ],

    /* erector spinae columns, thickest through the lumbar */
    back_lower: [
      [[122, 168], [131, 172], [133, 204], [131, 236], [123, 240], [121.5, 206]],
      [[118, 168], [109, 172], [107, 204], [109, 236], [117, 240], [118.5, 206]]
    ],

    /* long head medial, lateral head outside, common tendon at the elbow */
    triceps: [
      [[162, 120], [169, 124], [172, 158], [176, 194], [168, 202], [164, 164], [159, 132]],
      [[171, 126], [180, 132], [185, 166], [186, 196], [178, 200], [177, 164], [173, 138]],
      [[170, 196], [183, 195], [184, 208], [172, 209]]
    ],
    forearm: [
      [[170, 210], [178, 209], [184, 241], [192, 276], [196, 293], [189, 295],
       [183, 269], [175, 237], [169, 216]],
      [[182, 208], [192, 207], [198, 239], [204, 273], [205, 291], [197, 293],
       [196, 263], [190, 233], [184, 213]]
    ],

    glute: [
      [[121, 244], [136, 242], [148, 255], [150, 277], [142, 295], [126, 299], [121, 287]]
    ],
    abductor: [
      [[136, 237], [148, 242], [151, 259], [143, 263], [135, 252]]
    ],

    hamstring: [
      [[133, 297], [140, 301], [144, 316], [145, 334], [143, 352], [140, 363],
       [137, 360], [137, 340], [136, 322], [134, 308]],
      [[127, 297], [132, 302], [133, 318], [132, 336], [130, 352], [127, 359],
       [124.5, 352], [124, 334], [124.5, 316], [125.5, 304]]
    ],

    /* two gastrocnemius heads sitting over the soleus */
    calf: [
      [[124, 387], [130, 390], [133, 404], [134, 418], [131, 432], [126, 434],
       [123, 420], [122.5, 403]],
      [[135, 388], [141, 393], [142, 406], [141, 419], [137, 428], [134, 422], [133, 404]],
      [[129, 428], [138, 426], [139, 442], [136, 458], [131, 459], [128, 444]]
    ]
  };

  /* ============================================================
     FEMALE = the same anatomy under a y-dependent horizontal warp
     ============================================================ */
  const F_SCALE = [
    [70, 0.92], [96, 0.87], [120, 0.88], [160, 0.90], [200, 0.92],
    [232, 1.00], [252, 1.09], [285, 1.08], [330, 1.02], [380, 0.97], [540, 0.95]
  ];
  function fScale(y) {
    if (y <= F_SCALE[0][0]) return F_SCALE[0][1];
    for (let i = 1; i < F_SCALE.length; i++) {
      if (y <= F_SCALE[i][0]) {
        const [y0, s0] = F_SCALE[i - 1], [y1, s1] = F_SCALE[i];
        return s0 + (s1 - s0) * (y - y0) / (y1 - y0);
      }
    }
    return F_SCALE[F_SCALE.length - 1][1];
  }
  const warp = (pts, sex) => sex === 'female'
    ? pts.map(([x, y]) => [CX + (x - CX) * fScale(y), y])
    : pts;

  /* ============================================================
     SILHOUETTE — traced round the same landmarks the muscles use.
     Torso and legs are one shape, each arm is its own, so the armpit
     reads as a gap rather than a crease.
     ============================================================ */
  const TORSO_RIGHT = [
    [126, 74], [131, 92], [146, 96], [159, 100], [170, 116], [174, 136], [172, 158],
    [162, 170], [156, 180], [150, 198], [146, 216], [145, 234], [151, 250],
    [157, 272], [157, 302], [151, 337], [146, 361], [143, 378], [142, 393],
    [145, 418], [140, 450], [136, 470], [139, 488], [130, 493]
  ];
  const TORSO_INNER = [
    [124, 493], [121.5, 474], [120.8, 448], [121.5, 414], [120.5, 390],
    [121.5, 374], [121.5, 332], [121, 294], [120.5, 262]
  ];

  /* shoulder, elbow, wrist, hand — the same chain the arm muscles sit on */
  const ARM = { sj: [163, 118], el: [181, 206], wr: [197, 294], tip: [205, 328] };

  function perp(a, b) {
    const dx = b[0] - a[0], dy = b[1] - a[1], L = Math.hypot(dx, dy) || 1;
    return [-dy / L, dx / L];
  }
  const lerp = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
  const off = (p, n, d) => [p[0] + n[0] * d, p[1] + n[1] * d];

  function armOutline() {
    const { sj, el, wr, tip } = ARM;
    const n1 = perp(sj, el), n2 = perp(el, wr), n3 = perp(wr, tip);
    const outer = [
      off(sj, n1, 11.5), off(lerp(sj, el, 0.42), n1, 13.5), off(el, n2, 13),
      off(lerp(el, wr, 0.32), n2, 15), off(lerp(el, wr, 0.75), n2, 11),
      off(wr, n3, 8.5), off(lerp(wr, tip, 0.6), n3, 9.5), tip
    ];
    const inner = [
      off(lerp(wr, tip, 0.5), n3, -9), off(wr, n2, -9),
      off(lerp(el, wr, 0.5), n2, -13), off(el, n2, -12),
      off(lerp(sj, el, 0.5), n1, -12.5), off(sj, n1, -10)
    ];
    return outer.concat(inner);
  }

  function silhouette(sex) {
    const r = warp(TORSO_RIGHT, sex).concat(warp(TORSO_INNER, sex));
    const l = mirror(r).reverse();
    return smooth(r.concat(l), true, 0.34);
  }

  function arms(sex) {
    const a = warp(armOutline(), sex);
    return [smooth(a, true, 0.4), smooth(mirror(a), true, 0.4)];
  }

  const HEAD = sex => {
    const rx = sex === 'female' ? 16 : 17.5, ry = sex === 'female' ? 25 : 26;
    return `M ${CX} ${47 - ry} a ${rx} ${ry} 0 1 0 0.1 0 Z`;
  };
  /* The head was floating. Two open curves from the jaw to the
     shoulder line read as a neck without closing a shape that would
     draw a seam across the collar. */
  const NECK = sex => {
    /* start on the jaw line, where the head ellipse is actually that
       wide, so the curves leave the face instead of crossing it */
    const w  = sex === 'female' ? 8.6 : 10;
    const sh = sex === 'female' ? 15 : 17;
    return `M ${CX - w} 68 C ${CX - w - 1} 75, ${CX - sh + 3} 79, ${CX - sh} 84` +
           ` M ${CX + w} 68 C ${CX + w + 1} 75, ${CX + sh - 3} 79, ${CX + sh} 84`;
  };
  const HAIR = sex => sex !== 'female' ? '' :
    `M ${CX - 19} 52 C ${CX - 21} 8, ${CX + 21} 8, ${CX + 19} 52 C ${CX + 20} 80, ${CX - 20} 80, ${CX - 19} 52 Z`;

  /* ============================================================
     RENDER
     ============================================================ */
  function svg(opts) {
    opts = opts || {};
    const sex = opts.sex === 'female' ? 'female' : 'male';
    const back = !!opts.back;
    const regions = back ? BACK : FRONT;
    const idp = opts.idPrefix || '';

    let body = '';
    Object.keys(regions).forEach(id => {
      regions[id].forEach((shape, i) => {
        const w = warp(shape, sex);
        const centre = shape.some(p => p[0] < CX);      // authored across the midline already
        body += `<path class="mz" data-m="${id}" id="${idp}${id}-r${i}" d="${smooth(w, true, 0.28)}"/>`;
        if (!centre) body += `<path class="mz" data-m="${id}" id="${idp}${id}-l${i}" d="${smooth(mirror(w), true, 0.28)}"/>`;
      });
    });

    /* thin lines that read as tendon and bone rather than as muscle */
    const S = y => CX + 0 * y;
    const detail = back
      ? `<path class="ln" d="M ${CX} 100 V 240"/>`                       // spine
      : `<path class="ln" d="M ${CX} 104 V 164"/>` +                     // sternum
        `<path class="ln" d="M ${CX} 167 V 246"/>` +                     // linea alba
        `<path class="ln" d="M 108 96 Q ${CX} 104 132 96"/>`;            // clavicles

    return `<svg class="figure" viewBox="0 0 ${VIEWBOX.w} ${VIEWBOX.h}" xmlns="http://www.w3.org/2000/svg" data-sex="${sex}" data-view="${back ? 'back' : 'front'}">
  <g class="silhouette">${HAIR(sex) ? `<path class="hairline" d="${HAIR(sex)}"/>` : ''}<path class="body" d="${HEAD(sex)}"/><path class="body neck" d="${NECK(sex)}"/><path class="body" d="${silhouette(sex)}"/>${arms(sex).map(a => `<path class="body" d="${a}"/>`).join('')}</g>
  <g class="zones">${body}</g>
  <g class="details">${detail}</g>
</svg>`;
  }

  function idsFor(sex, back) { return Object.keys(back ? BACK : FRONT); }

  VOS.Figure = { svg, idsFor, VIEWBOX, smooth, FRONT, BACK, fScale, warp };
})(typeof globalThis !== 'undefined' ? globalThis : this);
