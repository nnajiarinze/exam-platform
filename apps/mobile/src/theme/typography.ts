export const typography = {
  display: {
    fontSize: 36,
    lineHeight: 42,
    fontWeight: "800" as const,
    letterSpacing: -0.7,
  },
  heading: {
    fontSize: 32,
    lineHeight: 40,
    fontWeight: "700" as const,
    letterSpacing: -0.3,
  },
  subheading: { fontSize: 24, lineHeight: 32, fontWeight: "700" as const },
  bodyLarge: { fontSize: 18, lineHeight: 28, fontWeight: "400" as const },
  body: { fontSize: 16, lineHeight: 24, fontWeight: "400" as const },
  label: {
    fontSize: 14,
    lineHeight: 20,
    fontWeight: "600" as const,
    letterSpacing: 0.28,
  },
  caption: {
    fontSize: 12,
    lineHeight: 16,
    fontWeight: "700" as const,
    letterSpacing: 0.35,
  },
} as const;
