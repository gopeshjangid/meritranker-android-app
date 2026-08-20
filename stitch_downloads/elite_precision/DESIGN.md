---
name: Elite Precision
colors:
  surface: '#0a1322'
  surface-dim: '#0a1322'
  surface-bright: '#31394a'
  surface-container-lowest: '#050e1d'
  surface-container-low: '#131c2b'
  surface-container: '#17202f'
  surface-container-high: '#212a3a'
  surface-container-highest: '#2c3545'
  on-surface: '#dae2f8'
  on-surface-variant: '#bbc9cc'
  inverse-surface: '#dae2f8'
  inverse-on-surface: '#283141'
  outline: '#859396'
  outline-variant: '#3c494c'
  surface-tint: '#28d9f3'
  primary: '#6ce9ff'
  on-primary: '#00363e'
  primary-container: '#00cfe8'
  on-primary-container: '#00545f'
  inverse-primary: '#006876'
  secondary: '#bcc7de'
  on-secondary: '#263143'
  secondary-container: '#3e495d'
  on-secondary-container: '#aeb9d0'
  tertiary: '#acdfff'
  on-tertiary: '#00354a'
  tertiary-container: '#59c8ff'
  on-tertiary-container: '#005270'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#9eefff'
  primary-fixed-dim: '#28d9f3'
  on-primary-fixed: '#001f24'
  on-primary-fixed-variant: '#004e59'
  secondary-fixed: '#d8e3fb'
  secondary-fixed-dim: '#bcc7de'
  on-secondary-fixed: '#111c2d'
  on-secondary-fixed-variant: '#3c475a'
  tertiary-fixed: '#c4e7ff'
  tertiary-fixed-dim: '#7bd0ff'
  on-tertiary-fixed: '#001e2c'
  on-tertiary-fixed-variant: '#004c69'
  background: '#0a1322'
  on-background: '#dae2f8'
  surface-variant: '#2c3545'
typography:
  headline-lg:
    fontFamily: Hanken Grotesk
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Hanken Grotesk
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  headline-md:
    fontFamily: Hanken Grotesk
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  headline-sm:
    fontFamily: Hanken Grotesk
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
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
  body-sm:
    fontFamily: Hanken Grotesk
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-md:
    fontFamily: Hanken Grotesk
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.05em
  label-sm:
    fontFamily: Hanken Grotesk
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 4px
  gutter-desktop: 24px
  margin-desktop: 40px
  gutter-mobile: 16px
  margin-mobile: 16px
  container-max: 1280px
---

## Brand & Style

The design system is engineered for high-achieving students, conveying an "Elite" atmosphere that is both technologically advanced and academically rigorous. The aesthetic sits at the intersection of **Corporate Modern** and **Minimalism**, prioritizing clarity and focus to reduce cognitive load during intense study sessions.

The brand personality is:
- **Authoritative:** Instilling confidence through precise alignment and clear hierarchy.
- **Intelligent:** Utilizing high-contrast accents to highlight AI-driven insights.
- **Accessible:** Ensuring that while the interface feels premium, it remains intuitive and readable for long-term usage.

The visual mood is established through deep, immersive backgrounds that minimize glare, paired with surgical precision in the application of vibrant cyan accents to guide the user's eye to primary actions.

## Colors

This design system utilizes a "Deep-Sea" palette to create a focused, low-strain environment. 

- **Primary (#00CFE8):** A vibrant cyan used exclusively for primary calls-to-action, active states, and AI-powered highlights. 
- **Neutral/Background (#101928):** The base canvas. This deep navy provides a sophisticated, high-contrast backdrop that makes the cyan and white text "pop."
- **Secondary/Surface (#1E293B):** Used for cards, input fields, and sidebar containers to create subtle depth against the primary background.
- **Success/Info (#38BDF8):** A softer blue used for informational badges and secondary progress indicators.

Text colors are strictly managed: High-emphasis text uses White (#FFFFFF), medium-emphasis uses a cool gray (#94A3B8), and disabled states use a muted navy (#334155).

## Typography

**Hanken Grotesk** is the sole typeface for this design system, chosen for its exceptional legibility and modern, clean geometry. 

- **Headlines:** Use Bold (700) or SemiBold (600) weights with slightly tightened letter-spacing to create a strong, authoritative presence.
- **Body Text:** Standardized at 16px for optimal readability. Use the Regular (400) weight for most content to ensure the interface feels airy.
- **Labels:** Small labels and metadata should use Medium (500) or SemiBold (600) weights. Navigation items and section headers should utilize uppercase styling with increased letter-spacing to distinguish them from body content.

## Layout & Spacing

The layout follows a **Fluid Grid** model with a strictly enforced 4px baseline shift. 

- **Desktop (1200px+):** A 12-column grid with 24px gutters. Sidebars are fixed at 280px, while the main content area remains fluid up to a maximum width of 1280px.
- **Tablet (768px - 1199px):** Transitions to an 8-column grid. Sidebars may collapse into an icon-only "rail" or a hidden drawer.
- **Mobile (Under 768px):** A 4-column grid. Margins are reduced to 16px to maximize the narrow viewport. 

Spacing between components (vertical rhythm) should follow the 8px/16px/24px/32px scale to maintain a sense of structured hierarchy.

## Elevation & Depth

This design system uses **Tonal Layering** supplemented by **Low-Contrast Outlines** to define hierarchy, avoiding traditional heavy shadows.

- **Level 0 (Base):** The primary navy background (#101928).
- **Level 1 (Cards/Containers):** Surfaces elevated one tier (#1E293B) with a subtle 1px border (#2D3748).
- **Level 2 (Modals/Popovers):** These surfaces use a slightly lighter navy and a soft, diffused outer glow in the primary cyan color at 5% opacity to signify a "floating" state.
- **Glassmorphism:** Navigation bars and header elements should use a backdrop blur (20px) with 80% opacity of the surface color to maintain context while scrolling.

## Shapes

The shape language is **Rounded (0.5rem)**, striking a balance between professional geometry and modern softness.

- **Small Elements:** Checkboxes, radio buttons, and small tags use a 4px (Soft) radius.
- **Standard Elements:** Buttons, input fields, and standard cards use an 8px (Rounded) radius.
- **Large Elements:** Featured banners, main content containers, and large modals use a 16px (Rounded-LG) radius.
- **Pill Shapes:** Reserved exclusively for status indicators (e.g., "Active," "Completed") and search bars to make them instantly recognizable.

## Components

### Buttons
- **Primary:** Solid Cyan (#00CFE8) background with dark navy text. No border. High-gloss finish.
- **Secondary:** Transparent background with a 1.5px Cyan border and Cyan text.
- **Tertiary/Ghost:** No background or border. Gray text that shifts to White on hover.

### Input Fields
- Dark surfaces (#1E293B) with a 1px border. On focus, the border glows Cyan and the internal label shifts to a floating state.

### Cards
- Used for "Doubt Solver" modules and "Answer Info." Cards should have a consistent 24px internal padding. Headers within cards are separated by a 1px divider (#2D3748).

### Chips/Tags
- Small, pill-shaped elements. Active tags use a light Cyan tint background with Cyan text; inactive tags use a dark gray tint.

### AI Indicators
- Any AI-generated content or actions (like "Ask") should be accompanied by a subtle "sparkle" icon and potentially a very faint Cyan gradient border to signify machine-enhanced precision.