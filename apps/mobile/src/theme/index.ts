export { colors } from "./colors";
export { radii } from "./radii";
export { shadows } from "./shadows";
export { spacing } from "./spacing";
export { typography } from "./typography";

import { colors } from "./colors";
import { radii } from "./radii";
import { shadows } from "./shadows";
import { spacing } from "./spacing";
import { typography } from "./typography";

export const theme = {
  colors,
  radii,
  shadows,
  spacing,
  typography,
  control: {
    minimumTouchSize: 44,
    buttonHeight: 56,
    iconSmall: 18,
    icon: 24,
    iconLarge: 32,
  },
  layout: { screenGutter: 16, tabletGutter: 32, contentMaxWidth: 640 },
} as const;
