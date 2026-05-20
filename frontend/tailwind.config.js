/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#f5f7ff',
          100: '#ebf0ff',
          200: '#d6e0ff',
          300: '#b3c7ff',
          400: '#85a2ff',
          500: '#5374ff',
          600: '#2b44ff',
          700: '#1c2dfc',
          800: '#1420d4',
          900: '#111b9e',
        }
      }
    },
  },
  plugins: [],
}
