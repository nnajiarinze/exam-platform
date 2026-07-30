import type { PropsWithChildren, ReactNode } from "react";
import {
  AccessibilityInfo,
  Animated,
  Easing,
  Pressable,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { useEffect, useRef, useState } from "react";
import Svg, { Circle } from "react-native-svg";
import { theme } from "../theme";
import { Icon } from "./ui";

export function SectionHeader({
  title,
  actionLabel,
  onAction,
}: {
  title: string;
  actionLabel?: string;
  onAction?: () => void;
}) {
  return (
    <View style={styles.sectionHeader}>
      <Text accessibilityRole="header" style={styles.sectionTitle}>
        {title}
      </Text>
      {actionLabel && onAction ? (
        <Pressable accessibilityRole="button" onPress={onAction} hitSlop={8}>
          <Text style={styles.sectionAction}>{actionLabel}</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

export function ReadinessRing({
  value,
  label = "Learning readiness",
  size = 144,
}: {
  value?: number;
  label?: string;
  size?: number;
}) {
  const normalized =
    value == null ? 0 : Math.max(0, Math.min(100, Math.round(value)));
  const radius = (size - 14) / 2;
  const circumference = 2 * Math.PI * radius;
  const animation = useRef(new Animated.Value(0)).current;
  const [reduceMotion, setReduceMotion] = useState(
    process.env.NODE_ENV === "test",
  );
  useEffect(() => {
    if (process.env.NODE_ENV === "test") return;
    let mounted = true;
    void AccessibilityInfo.isReduceMotionEnabled().then((enabled) => {
      if (mounted) setReduceMotion(enabled);
    });
    const subscription = AccessibilityInfo.addEventListener(
      "reduceMotionChanged",
      setReduceMotion,
    );
    return () => {
      mounted = false;
      subscription.remove();
    };
  }, []);
  useEffect(() => {
    if (reduceMotion) {
      animation.setValue(normalized);
      return;
    }
    Animated.timing(animation, {
      toValue: normalized,
      duration: 650,
      easing: Easing.out(Easing.cubic),
      useNativeDriver: false,
    }).start();
  }, [animation, normalized, reduceMotion]);
  const dashOffset = animation.interpolate({
    inputRange: [0, 100],
    outputRange: [circumference, 0],
  });
  const AnimatedCircle = Animated.createAnimatedComponent(Circle);
  return (
    <View
      accessibilityRole="progressbar"
      accessibilityLabel={label}
      accessibilityValue={{ min: 0, max: 100, now: normalized }}
      style={{ width: size, height: size }}
    >
      <Svg width={size} height={size} style={styles.ringSvg}>
        <Circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={theme.colors.surfaceContainer}
          strokeWidth={12}
        />
        <AnimatedCircle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke={theme.colors.accent}
          strokeWidth={12}
          strokeLinecap="round"
          strokeDasharray={`${circumference} ${circumference}`}
          strokeDashoffset={dashOffset}
          rotation="-90"
          origin={`${size / 2}, ${size / 2}`}
        />
      </Svg>
      <View style={styles.ringContent}>
        <Text style={styles.ringValue}>
          {value == null ? "—" : `${normalized}%`}
        </Text>
        <Text style={styles.ringLabel}>
          {value == null ? "NOT ENOUGH DATA" : "READINESS"}
        </Text>
      </View>
    </View>
  );
}

export function StatTile({
  icon,
  label,
  value,
  tone = "blue",
}: {
  icon: string;
  label: string;
  value: string;
  tone?: "blue" | "gold" | "green";
}) {
  return (
    <View style={styles.statTile}>
      <View
        style={[
          styles.statIcon,
          tone === "gold" && styles.goldIcon,
          tone === "green" && styles.greenIcon,
        ]}
      >
        <Icon
          name={icon}
          size={20}
          color={
            tone === "gold"
              ? theme.colors.accentStrong
              : tone === "green"
                ? theme.colors.success
                : theme.colors.primary
          }
        />
      </View>
      <View style={styles.statCopy}>
        <Text style={styles.statLabel}>{label}</Text>
        <Text style={styles.statValue}>{value}</Text>
      </View>
    </View>
  );
}

export function ActionCard({
  icon,
  title,
  description,
  onPress,
  accent = false,
}: {
  icon: string;
  title: string;
  description: string;
  onPress: () => void;
  accent?: boolean;
}) {
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={`${title}. ${description}`}
      onPress={onPress}
      style={({ pressed }) => [styles.actionCard, pressed && styles.pressed]}
    >
      <View style={[styles.actionIcon, accent && styles.goldIcon]}>
        <Icon
          name={icon}
          size={22}
          color={accent ? theme.colors.accentStrong : theme.colors.primary}
        />
      </View>
      <View style={styles.actionCopy}>
        <Text style={styles.actionTitle}>{title}</Text>
        <Text style={styles.actionDescription}>{description}</Text>
      </View>
      <Icon name="arrow" size={24} color={theme.colors.outline} />
    </Pressable>
  );
}

export function AnswerOption({
  index,
  text,
  selected,
  correct = false,
  incorrect = false,
  disabled = false,
  multiple = false,
  onPress,
}: {
  index: number;
  text: string;
  selected: boolean;
  correct?: boolean;
  incorrect?: boolean;
  disabled?: boolean;
  multiple?: boolean;
  onPress: () => void;
}) {
  return (
    <Pressable
      accessibilityRole={multiple ? "checkbox" : "radio"}
      accessibilityState={{ checked: selected, disabled }}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        styles.answer,
        selected && styles.answerSelected,
        correct && styles.answerCorrect,
        incorrect && styles.answerIncorrect,
        pressed && styles.pressed,
      ]}
    >
      <View
        style={[
          styles.answerLetter,
          selected && styles.answerLetterSelected,
          correct && styles.answerLetterCorrect,
          incorrect && styles.answerLetterIncorrect,
        ]}
      >
        <Text
          style={[
            styles.answerLetterText,
            (selected || correct || incorrect) && styles.answerLetterTextActive,
          ]}
        >
          {String.fromCharCode(65 + index)}
        </Text>
      </View>
      <Text style={styles.answerText}>{text}</Text>
      {selected || correct || incorrect ? (
        <Icon
          name={incorrect ? "close" : "check"}
          color={
            incorrect
              ? theme.colors.error
              : correct
                ? theme.colors.success
                : theme.colors.primary
          }
        />
      ) : null}
    </Pressable>
  );
}

export function Eyebrow({ children }: PropsWithChildren) {
  return <Text style={styles.eyebrow}>{children}</Text>;
}

export function MetricRow({ children }: { children: ReactNode }) {
  return <View style={styles.metricRow}>{children}</View>;
}

const styles = StyleSheet.create({
  sectionHeader: {
    alignItems: "center",
    flexDirection: "row",
    justifyContent: "space-between",
    marginTop: theme.spacing.xs,
  },
  sectionTitle: { color: theme.colors.text, ...theme.typography.subheading },
  sectionAction: { color: theme.colors.primary, ...theme.typography.label },
  ringSvg: { position: "absolute", transform: [{ rotate: "0deg" }] },
  ringContent: {
    alignItems: "center",
    bottom: 0,
    justifyContent: "center",
    left: 0,
    position: "absolute",
    right: 0,
    top: 0,
  },
  ringValue: {
    color: theme.colors.primary,
    fontSize: 28,
    fontWeight: "800",
    lineHeight: 34,
  },
  ringLabel: {
    color: theme.colors.muted,
    fontSize: 9,
    fontWeight: "700",
    letterSpacing: 0.7,
  },
  statTile: {
    alignItems: "center",
    backgroundColor: theme.colors.surface,
    borderColor: theme.colors.divider,
    borderRadius: theme.radii.xl,
    borderWidth: 1,
    flexDirection: "row",
    gap: 12,
    minHeight: 76,
    padding: 14,
    ...theme.shadows.card,
  },
  statIcon: {
    alignItems: "center",
    backgroundColor: theme.colors.surfaceLow,
    borderRadius: theme.radii.lg,
    height: 44,
    justifyContent: "center",
    width: 44,
  },
  goldIcon: { backgroundColor: "#FFF5D5" },
  greenIcon: { backgroundColor: theme.colors.successBackground },
  statCopy: { flex: 1 },
  statLabel: { color: theme.colors.muted, ...theme.typography.caption },
  statValue: {
    color: theme.colors.text,
    fontSize: 20,
    fontWeight: "700",
    lineHeight: 26,
  },
  actionCard: {
    alignItems: "center",
    backgroundColor: theme.colors.surfaceLow,
    borderColor: theme.colors.primaryFixed,
    borderRadius: theme.radii.xl,
    borderWidth: 1,
    flexDirection: "row",
    gap: 14,
    minHeight: 92,
    padding: 16,
  },
  actionIcon: {
    alignItems: "center",
    backgroundColor: theme.colors.primaryFixed,
    borderRadius: theme.radii.lg,
    height: 48,
    justifyContent: "center",
    width: 48,
  },
  actionCopy: { flex: 1 },
  actionTitle: {
    color: theme.colors.primary,
    fontSize: 17,
    fontWeight: "700",
    lineHeight: 23,
  },
  actionDescription: {
    color: theme.colors.muted,
    ...theme.typography.caption,
    marginTop: 2,
  },
  pressed: { opacity: 0.78, transform: [{ scale: 0.99 }] },
  answer: {
    alignItems: "center",
    backgroundColor: theme.colors.surface,
    borderColor: theme.colors.divider,
    borderRadius: theme.radii.xl,
    borderWidth: 1.5,
    flexDirection: "row",
    gap: 16,
    minHeight: 88,
    padding: 16,
    ...theme.shadows.card,
  },
  answerSelected: {
    backgroundColor: theme.colors.surfaceLow,
    borderColor: theme.colors.primary,
    borderWidth: 2,
  },
  answerCorrect: {
    backgroundColor: theme.colors.successBackground,
    borderColor: theme.colors.success,
  },
  answerIncorrect: {
    backgroundColor: theme.colors.errorBackground,
    borderColor: theme.colors.error,
  },
  answerLetter: {
    alignItems: "center",
    backgroundColor: theme.colors.surfaceContainer,
    borderRadius: theme.radii.lg,
    height: 48,
    justifyContent: "center",
    width: 48,
  },
  answerLetterSelected: { backgroundColor: theme.colors.primary },
  answerLetterCorrect: { backgroundColor: theme.colors.success },
  answerLetterIncorrect: { backgroundColor: theme.colors.error },
  answerLetterText: {
    color: theme.colors.primary,
    fontSize: 18,
    fontWeight: "700",
  },
  answerLetterTextActive: { color: theme.colors.onPrimary },
  answerText: {
    color: theme.colors.text,
    flex: 1,
    ...theme.typography.bodyLarge,
  },
  eyebrow: {
    color: theme.colors.accentStrong,
    ...theme.typography.caption,
    letterSpacing: 1.5,
  },
  metricRow: { flexDirection: "row", gap: theme.spacing.xs },
});
