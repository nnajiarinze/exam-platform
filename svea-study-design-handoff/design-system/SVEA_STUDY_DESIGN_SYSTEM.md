---
name: Svea Study Premium
colors:
  surface: '#f6faff'
  surface-dim: '#d2dbe4'
  surface-bright: '#f6faff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#ecf5fe'
  surface-container: '#e6eff8'
  surface-container-high: '#e0e9f2'
  surface-container-highest: '#dbe4ed'
  on-surface: '#141d23'
  on-surface-variant: '#424751'
  inverse-surface: '#293138'
  inverse-on-surface: '#e9f2fb'
  outline: '#737782'
  outline-variant: '#c3c6d2'
  surface-tint: '#2c5ea5'
  primary: '#02458b'
  on-primary: '#ffffff'
  primary-container: '#2b5da4'
  on-primary-container: '#c4d8ff'
  inverse-primary: '#aac7ff'
  secondary: '#745b00'
  on-secondary: '#ffffff'
  secondary-container: '#fdd355'
  on-secondary-container: '#735a00'
  tertiary: '#444647'
  on-tertiary: '#ffffff'
  tertiary-container: '#5b5e5f'
  on-tertiary-container: '#d6d7d8'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#d6e3ff'
  primary-fixed-dim: '#aac7ff'
  on-primary-fixed: '#001b3e'
  on-primary-fixed-variant: '#04468c'
  secondary-fixed: '#ffe08b'
  secondary-fixed-dim: '#ebc246'
  on-secondary-fixed: '#241a00'
  on-secondary-fixed-variant: '#584400'
  tertiary-fixed: '#e1e3e4'
  tertiary-fixed-dim: '#c5c7c8'
  on-tertiary-fixed: '#191c1d'
  on-tertiary-fixed-variant: '#454748'
  background: '#f6faff'
  on-background: '#141d23'
  surface-variant: '#dbe4ed'
typography:
  display-lg:
    fontFamily: Hanken Grotesk
    fontSize: 48px
    fontWeight: '800'
    lineHeight: 56px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Hanken Grotesk
    fontSize: 36px
    fontWeight: '800'
    lineHeight: 42px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Hanken Grotesk
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Hanken Grotesk
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  body-lg:
    fontFamily: Hanken Grotesk
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Hanken Grotesk
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-md:
    fontFamily: Hanken Grotesk
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Hanken Grotesk
    fontSize: 12px
    fontWeight: '700'
    lineHeight: 16px
    letterSpacing: 0.05em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 8px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  2xl: 48px
  3xl: 64px
  container-max: 1200px
  gutter: 24px
---

## Brand & Style

The design system is rooted in the "New Nordic" philosophy—merging functionalism with high-end editorial clarity. It is designed for learners who seek a focused, distraction-free environment that feels both academically rigorous and emotionally supportive.

The aesthetic profile follows a **Corporate Minimalist** approach with **Tactile** influences. It utilizes expansive white space, precise typography, and intentional pops of "Achievement Gold" to reward user progress. The emotional response should be one of "quiet confidence"—the interface stays out of the way until the moment of success, where it celebrates with vibrant, high-contrast accents.

## Colors

The palette is anchored by **Deep Navy (#2B5DA4)**, used for primary actions, navigation, and structural headings to establish authority and trust. **Golden Yellow (#F2C94C)** serves as the high-energy accent for progress indicators, achievement badges, and primary call-to-actions, ensuring a motivating "reward" sensation.

Surface colors utilize a mix of **Pure White (#FFFFFF)** for content cards and an **Off-White/Cool Gray (#F8F9FA)** for background staging to reduce eye strain during long study sessions. All text pairings are strictly audited to exceed WCAG AA contrast ratios, specifically ensuring the Navy-on-White and Navy-on-Gold combinations remain highly legible.

## Typography

**Hanken Grotesk** is the sole typeface for the design system, chosen for its contemporary geometric construction and exceptional legibility at small sizes. 

Headings use a **Bold (700)** or **ExtraBold (800)** weight with slightly tightened letter-spacing to create a distinctive "editorial" impact. Body text maintains a generous line height (1.5x minimum) to ensure long-form educational content is digestible. Labels and small metadata use SemiBold or Bold weights with increased tracking to ensure clarity against colored backgrounds.

## Layout & Spacing

The design system adheres to a strict **8-point grid** to ensure mathematical harmony across all components. Layouts are primarily **Fluid Grid** based, transitioning to a centered **Fixed Grid** on desktop screens to maintain readability.

- **Mobile:** 4-column grid, 16px margins, 16px gutters.
- **Tablet:** 8-column grid, 32px margins, 24px gutters.
- **Desktop:** 12-column grid, max-width 1200px, 24px gutters.

Whitespace is treated as a core design element rather than "empty" space. Sections should be separated by at least `48px (2xl)` on mobile and `64px (3xl)` on desktop to maintain a premium, unhurried feel.

## Elevation & Depth

This design system uses **Tonal Layering** combined with **Soft Ambient Shadows** to create a sense of hierarchy. 

Depth is communicated through three tiers:
1. **Level 0 (Floor):** Surface color (#F8F9FA). No shadows.
2. **Level 1 (Card):** White (#FFFFFF) with a very soft, diffused shadow (Hex: #2B5DA4 at 4% opacity, 12px blur, 4px Y-offset). Used for standard content modules.
3. **Level 2 (Active/Floating):** White (#FFFFFF) with a more pronounced shadow (Hex: #2B5DA4 at 8% opacity, 24px blur, 8px Y-offset). Used for modals, dropdowns, and hovered states.

Subtle linear gradients (e.g., Deep Navy to a slightly lighter tint) may be used on primary action buttons to give them a slight 3D "pressable" quality without appearing skeuomorphic.

## Shapes

The shape language is defined by **Soft Roundedness**, specifically targeting a **12px (0.75rem)** or **16px (1rem)** radius for cards and major containers. This softness mitigates the "seriousness" of the Deep Navy color, making the educational experience feel approachable and modern.

- **Small Components (Buttons, Inputs):** 12px radius.
- **Medium Components (Cards, Modals):** 16px radius.
- **Full Rounded:** Used specifically for "Success" chips and progress bars to create a pill-shaped aesthetic.

## Components

### Buttons
- **Primary:** Deep Navy background, White text. 12px roundedness. Subtle 2px bottom-border shading in a darker navy to create a "pressable" feel.
- **Secondary:** White background, Deep Navy border (1.5px), Navy text.
- **Accent:** Golden Yellow background, Deep Navy text. Reserved for "Finish," "Level Up," or "Go Premium" actions.

### Cards
Cards are the primary container for study units. They must use the **Level 1 shadow** and **16px roundedness**. Borders should be non-existent or a very soft 1px gray (#E9ECEF) to define edges on white backgrounds.

### Inputs & Selection
- **Text Inputs:** 12px radius, 1.5px border. On focus, the border transitions to Deep Navy with a 3px soft outer glow.
- **Chips:** Pill-shaped (fully rounded). Used for tags or category selection.
- **Progress Bars:** Dual-tone. A light gray track with a Golden Yellow fill. The fill should have a rounded end-cap.

### Feedback Elements
- **Success State:** Uses a soft green tint for backgrounds but keeps text in Deep Navy for legibility.
- **Motivation Toasts:** Centered, floating Level 2 containers with Hanken Grotesk Bold headers to celebrate user milestones.