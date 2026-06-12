import { createTheme } from '@mui/material/styles'

// Define CSS custom properties for global access
export const designTokens = {
  // Color Palette - Kinetic Premium
  colors: {
    primary: {
      dark: '#0F1419',      // Deep charcoal
      main: '#1A1F2E',      // Midnight navy
      light: '#2D3748',     // Slate
      lighter: '#404854',   // Charcoal
    },
    accent: {
      electric: '#00FF88',  // Electric lime
      cyan: '#00D9FF',      // Cyan
      magenta: '#FF006E',   // Hot magenta
      gold: '#FFD60A',      // Premium gold
    },
    semantic: {
      success: '#00C875',
      error: '#FF4444',
      warning: '#FFA500',
      info: '#00D9FF',
    },
    neutral: {
      white: '#FFFFFF',
      light: '#F5F7FA',
      medium: '#8B92A6',
      dark: '#3F4554',
    },
  },

  // Typography - Distinctive pairing
  typography: {
    display: "'Space Mono', monospace",        // Bold, geometric display font
    body: "'Inter', -apple-system, sans-serif", // Clean, efficient body
  },

  // Spacing system (8px base)
  spacing: {
    xs: '4px',
    sm: '8px',
    md: '16px',
    lg: '24px',
    xl: '32px',
    xxl: '48px',
  },

  // Border radius - geometric
  radius: {
    none: 0,
    xs: 4,
    sm: 8,
    md: 12,
    lg: 20,
    full: 9999,
  },

  // Border radius in pixels for CSS (for backward compatibility)
  radiusPx: {
    none: '0px',
    xs: '4px',
    sm: '8px',
    md: '12px',
    lg: '20px',
    full: '9999px',
  },

  // Shadow system - depth with purpose
  shadow: {
    none: 'none',
    sm: '0 2px 8px rgba(0, 0, 0, 0.12)',
    md: '0 4px 16px rgba(0, 0, 0, 0.16)',
    lg: '0 8px 32px rgba(0, 0, 0, 0.2)',
    xl: '0 16px 48px rgba(0, 0, 0, 0.24)',
    glow: '0 0 24px rgba(0, 255, 136, 0.2)',
  },

  // Animation timings
  animation: {
    fast: '150ms cubic-bezier(0.2, 0, 0.8, 1)',
    base: '250ms cubic-bezier(0.2, 0, 0.8, 1)',
    slow: '400ms cubic-bezier(0.2, 0, 0.8, 1)',
  },
}

// Material-UI Theme
export const theme = createTheme({
  palette: {
    primary: {
      main: designTokens.colors.accent.electric,
      dark: designTokens.colors.primary.dark,
      light: designTokens.colors.accent.cyan,
    },
    secondary: {
      main: designTokens.colors.accent.magenta,
      dark: designTokens.colors.primary.main,
      light: designTokens.colors.accent.gold,
    },
    success: {
      main: designTokens.colors.semantic.success,
    },
    error: {
      main: designTokens.colors.semantic.error,
    },
    warning: {
      main: designTokens.colors.semantic.warning,
    },
    info: {
      main: designTokens.colors.semantic.info,
    },
    background: {
      default: designTokens.colors.primary.dark,
      paper: designTokens.colors.primary.main,
    },
    text: {
      primary: designTokens.colors.neutral.white,
      secondary: designTokens.colors.neutral.medium,
    },
  },
  typography: {
    fontFamily: designTokens.typography.body,
    h1: {
      fontFamily: designTokens.typography.display,
      fontSize: '48px',
      fontWeight: 700,
      lineHeight: 1.1,
      letterSpacing: '-2px',
    },
    h2: {
      fontFamily: designTokens.typography.display,
      fontSize: '36px',
      fontWeight: 700,
      lineHeight: 1.2,
      letterSpacing: '-1px',
    },
    h3: {
      fontFamily: designTokens.typography.display,
      fontSize: '28px',
      fontWeight: 700,
      lineHeight: 1.3,
    },
    h4: {
      fontFamily: designTokens.typography.display,
      fontSize: '24px',
      fontWeight: 600,
      lineHeight: 1.3,
    },
    h5: {
      fontFamily: designTokens.typography.display,
      fontSize: '20px',
      fontWeight: 600,
      lineHeight: 1.4,
    },
    h6: {
      fontFamily: designTokens.typography.display,
      fontSize: '16px',
      fontWeight: 600,
      lineHeight: 1.4,
    },
    body1: {
      fontSize: '16px',
      fontWeight: 400,
      lineHeight: 1.5,
    },
    body2: {
      fontSize: '14px',
      fontWeight: 400,
      lineHeight: 1.5,
    },
    button: {
      fontFamily: designTokens.typography.display,
      fontSize: '14px',
      fontWeight: 600,
      textTransform: 'uppercase',
      letterSpacing: '0.5px',
    },
  },
  components: {
    MuiButton: {
      styleOverrides: {
        root: {
          borderRadius: designTokens.radius.md,
          textTransform: 'uppercase',
          fontWeight: 600,
          fontSize: '14px',
          padding: '12px 24px',
          transition: designTokens.animation.base,
          '&:hover': {
            transform: 'translateY(-2px)',
          },
        },
        contained: {
          boxShadow: designTokens.shadow.md,
          '&:hover': {
            boxShadow: designTokens.shadow.lg,
          },
        },
        outlined: {
          borderWidth: '2px',
        },
      },
    },
    MuiCard: {
      styleOverrides: {
        root: {
          backgroundColor: designTokens.colors.primary.main,
          borderRadius: designTokens.radius.lg,
          border: `1px solid ${designTokens.colors.primary.lighter}`,
          boxShadow: designTokens.shadow.md,
          transition: designTokens.animation.base,
          '&:hover': {
            boxShadow: designTokens.shadow.lg,
            borderColor: designTokens.colors.accent.electric,
          },
        },
      },
    },
    MuiTextField: {
      styleOverrides: {
        root: {
          '& .MuiOutlinedInput-root': {
            borderRadius: designTokens.radius.md,
            transition: designTokens.animation.base,
            '& fieldset': {
              borderColor: designTokens.colors.primary.lighter,
            },
            '&:hover fieldset': {
              borderColor: designTokens.colors.accent.electric,
            },
            '&.Mui-focused fieldset': {
              borderColor: designTokens.colors.accent.cyan,
              borderWidth: '2px',
              boxShadow: `0 0 16px ${designTokens.colors.accent.cyan}40`,
            },
          },
        },
      },
    },
    MuiChip: {
      styleOverrides: {
        root: {
          borderRadius: designTokens.radius.full,
          fontFamily: designTokens.typography.display,
          fontWeight: 600,
        },
      },
    },
  },
  shape: {
    borderRadius: designTokens.radius.md,
  },
})

// Export CSS variables for use in CSS files
export const getCSSVariables = () => `
:root {
  /* Colors */
  --color-primary-dark: ${designTokens.colors.primary.dark};
  --color-primary-main: ${designTokens.colors.primary.main};
  --color-primary-light: ${designTokens.colors.primary.light};
  --color-primary-lighter: ${designTokens.colors.primary.lighter};

  --color-accent-electric: ${designTokens.colors.accent.electric};
  --color-accent-cyan: ${designTokens.colors.accent.cyan};
  --color-accent-magenta: ${designTokens.colors.accent.magenta};
  --color-accent-gold: ${designTokens.colors.accent.gold};

  --color-success: ${designTokens.colors.semantic.success};
  --color-error: ${designTokens.colors.semantic.error};
  --color-warning: ${designTokens.colors.semantic.warning};
  --color-info: ${designTokens.colors.semantic.info};

  --color-white: ${designTokens.colors.neutral.white};
  --color-light: ${designTokens.colors.neutral.light};
  --color-medium: ${designTokens.colors.neutral.medium};
  --color-dark: ${designTokens.colors.neutral.dark};

  /* Typography */
  --font-display: ${designTokens.typography.display};
  --font-body: ${designTokens.typography.body};

  /* Spacing */
  --spacing-xs: ${designTokens.spacing.xs};
  --spacing-sm: ${designTokens.spacing.sm};
  --spacing-md: ${designTokens.spacing.md};
  --spacing-lg: ${designTokens.spacing.lg};
  --spacing-xl: ${designTokens.spacing.xl};
  --spacing-xxl: ${designTokens.spacing.xxl};

  /* Radius */
  --radius-none: ${designTokens.radiusPx.none};
  --radius-xs: ${designTokens.radiusPx.xs};
  --radius-sm: ${designTokens.radiusPx.sm};
  --radius-md: ${designTokens.radiusPx.md};
  --radius-lg: ${designTokens.radiusPx.lg};
  --radius-full: ${designTokens.radiusPx.full};

  /* Shadows */
  --shadow-none: ${designTokens.shadow.none};
  --shadow-sm: ${designTokens.shadow.sm};
  --shadow-md: ${designTokens.shadow.md};
  --shadow-lg: ${designTokens.shadow.lg};
  --shadow-xl: ${designTokens.shadow.xl};
  --shadow-glow: ${designTokens.shadow.glow};

  /* Animation */
  --animation-fast: ${designTokens.animation.fast};
  --animation-base: ${designTokens.animation.base};
  --animation-slow: ${designTokens.animation.slow};
}
`
