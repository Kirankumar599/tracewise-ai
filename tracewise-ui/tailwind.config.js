/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        midnight: {
          950: '#060911',
          900: '#0B0F19',
          850: '#0F1626',
          800: '#141D30',
          700: '#1E293B',
          600: '#334155'
        },
        brand: {
          cyan: '#06B6D4',
          turquoise: '#14B8A6',
          sky: '#38BDF8',
          emerald: '#10B981',
          amber: '#F59E0B',
          rose: '#EF4444'
        }
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace']
      }
    },
  },
  plugins: [],
}
