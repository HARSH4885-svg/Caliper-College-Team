import type { CSSProperties, ReactNode } from 'react'

/* ---------- icons: one authored set, 24px grid, 1.75 stroke, round caps ---------- */
const PATHS: Record<string, string> = {
  home: 'M3 11l9-8 9 8M5 10v10h5v-6h4v6h5V10',
  classes: 'M4 5a2 2 0 012-2h13v16H6a2 2 0 00-2 2V5zM4 19a2 2 0 012-2h13',
  people: 'M16 20v-1.5a3.5 3.5 0 00-3.5-3.5h-5A3.5 3.5 0 004 18.5V20M10 11a3.5 3.5 0 100-7 3.5 3.5 0 000 7zM20 20v-1.5a3.5 3.5 0 00-2.5-3.35M15.5 4.2a3.5 3.5 0 010 6.6',
  task: 'M9 4h6v3H9zM7 5.5H6a2 2 0 00-2 2V20a2 2 0 002 2h12a2 2 0 002-2V7.5a2 2 0 00-2-2h-1M8 12h8M8 16h5',
  eye: 'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12zM12 15a3 3 0 100-6 3 3 0 000 6z',
  upload: 'M12 16V4M7 9l5-5 5 5M4 20h16',
  download: 'M12 4v12M7 11l5 5 5-5M4 20h16',
  check: 'M5 12.5l4.5 4.5L19 7.5',
  clock: 'M12 21a9 9 0 100-18 9 9 0 000 18zM12 7v5l3 2',
  plus: 'M12 5v14M5 12h14',
  archive: 'M3 5h18v4H3zM5 9v10h14V9M10 13h4',
  logout: 'M9 4H5a1 1 0 00-1 1v14a1 1 0 001 1h4M16 8l4 4-4 4M20 12H9',
  right: 'M9 6l6 6-6 6',
  left: 'M15 6l-6 6 6 6',
  code: 'M8 8l-4 4 4 4M16 8l4 4-4 4M13.5 5l-3 14',
  lock: 'M6 11h12v9H6zM8.5 11V8a3.5 3.5 0 017 0v3',
  flag: 'M5 21V4M5 4h11l-2 4 2 4H5',
  file: 'M7 3h7l5 5v13H7zM14 3v5h5',
  swap: 'M7 7h12M15 3l4 4-4 4M17 17H5M9 13l-4 4 4 4',
  copy: 'M9 9h10v11H9zM5 15V4h10',
  refresh: 'M20 11a8 8 0 10-2.3 6.3M20 4v7h-7',
  shield: 'M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z',
  moon: 'M20 14.5A8 8 0 019.5 4a8 8 0 1010.5 10.5z',
  sun: 'M12 16a4 4 0 100-8 4 4 0 000 8zM12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4',
  chart: 'M4 20V10M10 20V4M16 20v-7M22 20H2',
  grid: 'M3 3h7v7H3zM14 3h7v7h-7zM3 14h7v7H3zM14 14h7v7h-7z',
  net: 'M6 6a2 2 0 100-4 2 2 0 000 4zM18 8a2 2 0 100-4 2 2 0 000 4zM12 22a2 2 0 100-4 2 2 0 000 4zM7.5 5.5l9 1.5M6.5 6l4.5 12M17.5 8l-4.5 10',
  edit: 'M4 20h4L19 9l-4-4L4 16zM13.5 6.5l4 4',
  bell: 'M6 16V11a6 6 0 0112 0v5l2 2H4zM10 21h4',
  calendar: 'M4 6h16v15H4zM4 10h16M8 3v5M16 3v5',
  chat: 'M4 5h16v11H9l-5 4z',
  link: 'M10 14a4 4 0 005.7 0l3-3a4 4 0 00-5.7-5.7l-1 1M14 10a4 4 0 00-5.7 0l-3 3a4 4 0 005.7 5.7l1-1',
  search: 'M11 18a7 7 0 100-14 7 7 0 000 14zM21 21l-5-5',
  megaphone: 'M3 11v3a1 1 0 001 1h2l6 4V6L6 10H4a1 1 0 00-1 1zM16 8.5a4.5 4.5 0 010 7M19 6a8 8 0 010 12',
  pin: 'M9 4h6l-1 6 3 3H7l3-3zM12 13v8',
  trash: 'M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3',
  keyboard: 'M3 6h18v12H3zM7 10h.01M11 10h.01M15 10h.01M7 14h10',
  user: 'M12 12a4 4 0 100-8 4 4 0 000 8zM4 21a8 8 0 0116 0',
  copyplus: 'M9 9h10v11H9zM5 15V4h10M14 12v5M11.5 14.5h5',
  extend: 'M12 21a9 9 0 100-18 9 9 0 000 18zM12 7v5l3 2M19 3v4M17 5h4',
  info: 'M12 21a9 9 0 100-18 9 9 0 000 18zM12 11v5M12 8v.01',
}
export type IconName = keyof typeof PATHS
export function Icon({ name, size = 18, className }: { name: IconName; size?: number; className?: string }) {
  return (
    <svg className={`icon${className ? ' ' + className : ''}`} width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
      <path d={PATHS[name]} />
    </svg>
  )
}

/* ---------- brand mark: Caliper precision measurement emblem ---------- */
/** The mark: minimal geometric Caliper 'C' monogram with inward measuring jaws and central calibration point */
export function Mark({ size = 28, onDark = true }: { size?: number; onDark?: boolean }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true" focusable="false">
      <rect width="32" height="32" rx="8" fill="#0A1F3C" />
      <rect x="0.5" y="0.5" width="31" height="31" rx="7.5" stroke={onDark ? "rgba(255, 255, 255, 0.14)" : "rgba(27, 79, 224, 0.2)"} strokeWidth="1" />
      {/* Precision Caliper 'C' jaw path */}
      <path d="M22 13V9C22 8.45 21.55 8 21 8H13C9.69 8 7 10.69 7 14V18C7 21.31 9.69 24 13 24H21C21.55 24 22 23.55 22 23V19" stroke="#38BDF8" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
      {/* Calibrated measurement datum point */}
      <circle cx="16" cy="16" r="2" fill="#F59E0B" />
    </svg>
  )
}

export const Wordmark = ({ light = false }: { light?: boolean }) => (
  <span className={`wordmark${light ? ' light' : ''}`}><Mark size={28} onDark={light} /><span>Cal<b>iper</b></span></span>
)

/* ---------- course banner: rich generative technical & algorithmic cover art ---------- */
const PALETTES: [string, string, string][] = [
  ['#0A1F3C', '#1B4FE0', '#F4F6FA'], ['#F2B705', '#0A1F3C', '#FFF7DA'], ['#0E8F7E', '#0A1F3C', '#E3F6F2'],
  ['#5B3FD6', '#F4F6FA', '#F2B705'], ['#1B4FE0', '#F2B705', '#F4F6FA'], ['#16343A', '#0E8F7E', '#F2B705'],
]

interface BannerPalette {
  bgStart: string
  bgEnd: string
  primary: string
  secondary: string
  accent: string
  tint: string
}

const BANNER_PALETTES: BannerPalette[] = [
  { bgStart: '#061326', bgEnd: '#0D274C', primary: '#2563EB', secondary: '#60A5FA', accent: '#93C5FD', tint: 'rgba(37,99,235,0.18)' }, // Azure
  { bgStart: '#041614', bgEnd: '#0A332C', primary: '#059669', secondary: '#34D399', accent: '#6EE7B7', tint: 'rgba(5,150,105,0.18)' }, // Emerald
  { bgStart: '#111622', bgEnd: '#1C2638', primary: '#D97706', secondary: '#F59E0B', accent: '#FDE68A', tint: 'rgba(217,119,6,0.18)' }, // Amber Gold
  { bgStart: '#061520', bgEnd: '#0A314A', primary: '#0891B2', secondary: '#22D3EE', accent: '#A5F3FC', tint: 'rgba(8,145,178,0.18)' }, // Cyan
  { bgStart: '#0E0B1E', bgEnd: '#1E173E', primary: '#7C3AED', secondary: '#A78BFA', accent: '#DDD6FE', tint: 'rgba(124,58,237,0.18)' }, // Violet
  { bgStart: '#170C12', bgEnd: '#331725', primary: '#E11D48', secondary: '#FB7185', accent: '#FECDD3', tint: 'rgba(225,29,72,0.18)' }, // Crimson
  { bgStart: '#0A1F3C', bgEnd: '#153360', primary: '#1B4FE0', secondary: '#F2B705', accent: '#FFFFFF', tint: 'rgba(242,183,5,0.18)' }, // Harbor Navy
  { bgStart: '#081716', bgEnd: '#113530', primary: '#0D9488', secondary: '#2DD4BF', accent: '#99F6E4', tint: 'rgba(13,148,136,0.18)' }, // Deep Teal
]

export function bannerSeed(id: number) {
  return { g: Math.abs(id) % 8, p: Math.abs(Math.floor(id / 2)) % BANNER_PALETTES.length }
}

export function FlagBanner({ seed, className }: { seed: number; className?: string }) {
  const { g, p } = bannerSeed(seed)
  const pal = BANNER_PALETTES[p]
  const id = `cb-${seed}`

  const patterns: Record<number, ReactNode> = {
    // 0: Neural / Graph network (nodes, links, clusters)
    0: (
      <>
        <line x1="40" y1="30" x2="110" y2="75" stroke={pal.primary} strokeWidth="1.5" strokeOpacity="0.4" />
        <line x1="110" y1="75" x2="190" y2="35" stroke={pal.primary} strokeWidth="1.5" strokeOpacity="0.4" />
        <line x1="110" y1="75" x2="160" y2="95" stroke={pal.secondary} strokeWidth="1.2" strokeOpacity="0.5" />
        <line x1="190" y1="35" x2="270" y2="70" stroke={pal.secondary} strokeWidth="1.5" strokeOpacity="0.4" />
        <line x1="160" y1="95" x2="270" y2="70" stroke={pal.primary} strokeWidth="1.2" strokeOpacity="0.4" />
        <line x1="270" y1="70" x2="330" y2="40" stroke={pal.primary} strokeWidth="1.5" strokeOpacity="0.4" />
        <line x1="190" y1="35" x2="330" y2="40" stroke={pal.secondary} strokeWidth="1" strokeOpacity="0.3" strokeDasharray="3 3" />
        
        <circle cx="40" cy="30" r="5" fill={pal.primary} />
        <circle cx="110" cy="75" r="7" fill={pal.secondary} />
        <circle cx="110" cy="75" r="14" fill={pal.tint} />
        <circle cx="190" cy="35" r="6" fill={pal.accent} />
        <circle cx="160" cy="95" r="5" fill={pal.primary} />
        <circle cx="270" cy="70" r="8" fill={pal.secondary} />
        <circle cx="270" cy="70" r="16" fill={pal.tint} />
        <circle cx="330" cy="40" r="6" fill={pal.accent} />
        
        <circle cx="80" cy="20" r="2.5" fill={pal.secondary} opacity="0.6" />
        <circle cx="225" cy="100" r="3" fill={pal.primary} opacity="0.6" />
        <circle cx="310" cy="90" r="2" fill={pal.accent} opacity="0.7" />
      </>
    ),

    // 1: Harmonic Fourier Waves (interlocking mathematical sine waves)
    1: (
      <>
        <path d="M-20 60 Q 70 10, 160 60 T 340 60 T 400 60" fill="none" stroke={pal.secondary} strokeWidth="2.5" strokeOpacity="0.7" />
        <path d="M-20 75 Q 80 115, 180 70 T 380 65" fill="none" stroke={pal.primary} strokeWidth="2" strokeOpacity="0.5" />
        <path d="M-20 45 Q 90 20, 190 55 T 390 50" fill="none" stroke={pal.accent} strokeWidth="1.5" strokeOpacity="0.6" strokeDasharray="4 4" />
        <path d="M0 80 Q 90 30, 180 80 T 360 80 L 360 120 L 0 120 Z" fill={pal.tint} opacity="0.5" />
        <circle cx="160" cy="60" r="4" fill={pal.accent} />
        <circle cx="250" cy="35" r="3" fill={pal.secondary} />
        <circle cx="70" cy="35" r="3" fill={pal.primary} />
      </>
    ),

    // 2: Isometric 3D Grid & Stepped Data Columns
    2: (
      <>
        <g opacity="0.7" transform="translate(60, 10)">
          {/* Iso floor lines */}
          <path d="M0 60 L120 0 L240 60 L120 120 Z" fill="none" stroke={pal.secondary} strokeWidth="1" strokeOpacity="0.25" />
          <path d="M30 45 L150 105" stroke={pal.secondary} strokeWidth="1" strokeOpacity="0.15" />
          <path d="M60 30 L180 90" stroke={pal.secondary} strokeWidth="1" strokeOpacity="0.15" />
          <path d="M90 15 L210 75" stroke={pal.secondary} strokeWidth="1" strokeOpacity="0.15" />
          
          {/* Stepped column 1 */}
          <path d="M60 60 L90 45 L90 75 L60 90 Z" fill={pal.primary} />
          <path d="M90 45 L120 60 L120 90 L90 75 Z" fill={pal.secondary} />
          <path d="M60 60 L90 45 L120 60 L90 75 Z" fill={pal.accent} />
          
          {/* Stepped column 2 (taller) */}
          <path d="M110 35 L140 20 L140 65 L110 80 Z" fill={pal.primary} />
          <path d="M140 20 L170 35 L170 80 L140 65 Z" fill={pal.secondary} />
          <path d="M110 35 L140 20 L170 35 L140 50 Z" fill={pal.accent} />

          {/* Stepped column 3 */}
          <path d="M150 55 L175 42 L175 75 L150 88 Z" fill={pal.primary} />
          <path d="M175 42 L200 55 L200 88 L175 75 Z" fill={pal.secondary} />
          <path d="M150 55 L175 42 L200 55 L175 68 Z" fill={pal.accent} />
        </g>
      </>
    ),

    // 3: Digital Logic Bus & Circuit System (traces, vias, bus paths)
    3: (
      <>
        <g stroke={pal.primary} strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" fill="none" opacity="0.65">
          <path d="M-10 30 H120 L150 60 H260 L280 80 H380" />
          <path d="M-10 50 H90 L120 80 H210 L230 100 H380" stroke={pal.secondary} />
          <path d="M-10 70 H60 L80 90 H160" />
          <path d="M140 10 H220 L240 30 H380" stroke={pal.secondary} strokeWidth="1.2" />
          <path d="M190 60 V10 H260" stroke={pal.accent} strokeWidth="1.2" strokeDasharray="3 3" />
        </g>
        {/* Via pads */}
        <circle cx="120" cy="30" r="3.5" fill={pal.accent} />
        <circle cx="150" cy="60" r="4.5" fill={pal.secondary} />
        <circle cx="260" cy="60" r="3.5" fill={pal.accent} />
        <circle cx="90" cy="50" r="3" fill={pal.primary} />
        <circle cx="210" cy="80" r="4" fill={pal.accent} />
        <circle cx="280" cy="80" r="3" fill={pal.secondary} />
        {/* Chip badge */}
        <rect x="290" y="25" width="46" height="34" rx="4" fill={pal.bgStart} stroke={pal.secondary} strokeWidth="1.5" />
        <rect x="296" y="31" width="34" height="22" rx="2" fill={pal.tint} />
        <line x1="290" y1="34" x2="284" y2="34" stroke={pal.secondary} strokeWidth="1.5" />
        <line x1="290" y1="42" x2="284" y2="42" stroke={pal.secondary} strokeWidth="1.5" />
        <line x1="290" y1="50" x2="284" y2="50" stroke={pal.secondary} strokeWidth="1.5" />
      </>
    ),

    // 4: Faceted Prismatic Geometry (crystalline modern refraction)
    4: (
      <>
        <polygon points="50,15 130,40 90,95" fill={pal.primary} opacity="0.35" />
        <polygon points="130,40 210,20 180,85" fill={pal.secondary} opacity="0.4" />
        <polygon points="130,40 180,85 90,95" fill={pal.tint} opacity="0.5" />
        <polygon points="210,20 290,45 240,100" fill={pal.primary} opacity="0.3" />
        <polygon points="180,85 210,20 240,100" fill={pal.accent} opacity="0.3" />
        <polygon points="290,45 350,30 320,90" fill={pal.secondary} opacity="0.35" />
        
        {/* Facet lines */}
        <line x1="50" y1="15" x2="130" y2="40" stroke={pal.accent} strokeWidth="1.5" opacity="0.7" />
        <line x1="130" y1="40" x2="210" y2="20" stroke={pal.accent} strokeWidth="1.5" opacity="0.8" />
        <line x1="210" y1="20" x2="290" y2="45" stroke={pal.accent} strokeWidth="1.5" opacity="0.7" />
        <line x1="130" y1="40" x2="180" y2="85" stroke={pal.secondary} strokeWidth="1.5" opacity="0.7" />
        <line x1="90" y1="95" x2="180" y2="85" stroke={pal.primary} strokeWidth="1.2" opacity="0.6" />
        <line x1="180" y1="85" x2="240" y2="100" stroke={pal.secondary} strokeWidth="1.2" opacity="0.6" />
      </>
    ),

    // 5: Orbital Rings & Concentric Beacon (astronomical & quantum precision)
    5: (
      <>
        <ellipse cx="180" cy="60" rx="140" ry="46" fill="none" stroke={pal.primary} strokeWidth="1.2" strokeOpacity="0.35" transform="rotate(-12 180 60)" />
        <ellipse cx="180" cy="60" rx="100" ry="32" fill="none" stroke={pal.secondary} strokeWidth="1.5" strokeOpacity="0.5" transform="rotate(18 180 60)" />
        <ellipse cx="180" cy="60" rx="60" ry="20" fill="none" stroke={pal.accent} strokeWidth="1.8" strokeOpacity="0.7" />
        
        {/* Central Core */}
        <circle cx="180" cy="60" r="16" fill={pal.tint} />
        <circle cx="180" cy="60" r="8" fill={pal.secondary} />
        <circle cx="180" cy="60" r="3.5" fill="#FFFFFF" />
        
        {/* Orbital nodes */}
        <circle cx="85" cy="48" r="4.5" fill={pal.accent} />
        <circle cx="265" cy="74" r="5" fill={pal.secondary} />
        <circle cx="230" cy="38" r="3.5" fill={pal.primary} />
        <circle cx="125" cy="72" r="3" fill={pal.accent} />

        <line x1="180" y1="10" x2="180" y2="110" stroke={pal.primary} strokeWidth="0.8" strokeOpacity="0.3" strokeDasharray="4 4" />
        <line x1="100" y1="60" x2="260" y2="60" stroke={pal.primary} strokeWidth="0.8" strokeOpacity="0.3" strokeDasharray="4 4" />
      </>
    ),

    // 6: Token / Data Spectrum Columns (rhythmic equalizer matrix)
    6: (
      <>
        <g opacity="0.75">
          {Array.from({ length: 18 }, (_, i) => {
            const x = 30 + i * 18
            const h = 25 + Math.sin(i * 0.7) * 22 + Math.cos(i * 1.3) * 16
            const y = 95 - h
            return (
              <g key={i}>
                <rect x={x} y={y} width="10" height={h} rx="3" fill={i % 3 === 0 ? pal.secondary : pal.primary} opacity={0.65 + (i % 4) * 0.1} />
                <rect x={x} y={y} width="10" height="3" rx="1.5" fill={pal.accent} />
              </g>
            )
          })}
        </g>
        <line x1="20" y1="100" x2="350" y2="100" stroke={pal.secondary} strokeWidth="1" strokeOpacity="0.3" />
        <path d="M20 40 Q 120 15, 200 45 T 350 30" fill="none" stroke={pal.accent} strokeWidth="1.5" strokeOpacity="0.4" strokeDasharray="3 3" />
      </>
    ),

    // 7: Abstract Syntax Tree & Recursive Lattice
    7: (
      <>
        <g stroke={pal.primary} strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" fill="none" opacity="0.65">
          {/* Root to children */}
          <path d="M180 18 L100 52" />
          <path d="M180 18 L260 52" stroke={pal.secondary} />
          
          <path d="M100 52 L55 88" />
          <path d="M100 52 L135 88" stroke={pal.secondary} />
          
          <path d="M260 52 L225 88" stroke={pal.secondary} />
          <path d="M260 52 L305 88" />
          
          <path d="M55 88 L35 110" strokeOpacity="0.4" />
          <path d="M55 88 L75 110" strokeOpacity="0.4" />
          <path d="M135 88 L120 110" strokeOpacity="0.4" />
          <path d="M135 88 L150 110" strokeOpacity="0.4" />
        </g>
        {/* Nodes */}
        <circle cx="180" cy="18" r="7" fill={pal.accent} />
        <circle cx="100" cy="52" r="6" fill={pal.secondary} />
        <circle cx="260" cy="52" r="6" fill={pal.primary} />
        <circle cx="55" cy="88" r="4.5" fill={pal.accent} />
        <circle cx="135" cy="88" r="4.5" fill={pal.secondary} />
        <circle cx="225" cy="88" r="4.5" fill={pal.accent} />
        <circle cx="305" cy="88" r="4.5" fill={pal.secondary} />
      </>
    ),
  }

  return (
    <svg className={`flag${className ? ' ' + className : ''}`} viewBox="0 0 360 120" preserveAspectRatio="xMidYMid slice" aria-hidden="true" focusable="false">
      <defs>
        <linearGradient id={`${id}-bg`} x1="0" y1="0" x2="360" y2="120" gradientUnits="userSpaceOnUse">
          <stop stopColor={pal.bgStart} />
          <stop offset="1" stopColor={pal.bgEnd} />
        </linearGradient>
        <linearGradient id={`${id}-vignette`} x1="0" y1="0" x2="0" y2="120" gradientUnits="userSpaceOnUse">
          <stop stopColor="rgba(0,0,0,0)" offset="0.4" />
          <stop stopColor="rgba(0,0,0,0.5)" offset="1" />
        </linearGradient>
      </defs>
      <rect width="360" height="120" fill={`url(#${id}-bg)`} />
      {patterns[g] ?? patterns[0]}
      <rect width="360" height="120" fill={`url(#${id}-vignette)`} />
    </svg>
  )
}

/* ---------- avatar: initials on a deterministic flag colour ---------- */
export function Avatar({ name, size = 32 }: { name: string; size?: number }) {
  const initials = name.replace(/\(.*?\)/g, '').trim().split(/\s+/).slice(0, 2).map(w => w[0]?.toUpperCase() ?? '').join('') || '?'
  const h = [...name].reduce((n, ch) => (n * 31 + ch.charCodeAt(0)) >>> 0, 7) % PALETTES.length
  const [bg, , fg] = PALETTES[h]
  const dark = bg === '#F2B705' || bg === '#F4F6FA'
  const style: CSSProperties = { width: size, height: size, background: bg, color: dark ? '#0A1F3C' : fg, fontSize: size * 0.4 }
  return <span className="avatar" style={style} aria-hidden="true">{initials}</span>
}

/* ---------- pennant: verdict shown as a swallow-tailed signal flag ---------- */
export function Pennant({ code, tone }: { code: string; tone: 'ok' | 'warn' | 'bad' | 'muted' }) {
  return <span className={`pennant p-${tone}`} aria-hidden="true">{code}</span>
}

/* ---------- watch level: three ticks, lit by how much human attention a pair deserves (never red) ---------- */
export function Level({ lit, dashed = false }: { lit: 0 | 1 | 2 | 3; dashed?: boolean }) {
  return (
    <span className={`level${dashed ? ' dashed' : ''}`} aria-hidden="true">
      {[1, 2, 3].map(i => <i key={i} className={i <= lit ? 'on' : ''} style={{ height: 4 + i * 3 }} />)}
    </span>
  )
}

/** A string of signal pennants hung across a masthead. Purely decorative; colours cycle through the brand palette. */
export function Bunting() {
  const cols = ['#F2B705', '#4F7CFF', '#FFFFFF', '#0E8F7E', '#F2B705', '#7C5CFF', '#FFFFFF', '#4F7CFF']
  const n = 22, W = 1200
  const sag = (x: number) => 8 + 22 * Math.sin((x / W) * Math.PI)
  return (
    <svg className="bunting" viewBox={`0 0 ${W} 64`} preserveAspectRatio="xMidYMin slice" aria-hidden="true" focusable="false">
      <path d={`M0 ${sag(0)} ` + Array.from({ length: 41 }, (_, i) => `L${(i * W) / 40} ${sag((i * W) / 40)}`).join(' ')} fill="none" stroke="rgba(255,255,255,.35)" strokeWidth="1.4" />
      {Array.from({ length: n }, (_, i) => {
        const x = ((i + 0.6) * W) / n, y = sag(x)
        return <path key={i} d={`M${x} ${y} h26 l-8 9 8 9 h-26z`} fill={cols[i % cols.length]} opacity={0.92} />
      })}
    </svg>
  )
}
