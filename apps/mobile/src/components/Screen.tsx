import type { PropsWithChildren } from "react";
import { RefreshControl, ScrollView, StyleSheet, View } from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { theme } from "../theme/theme";

export function Screen({
  children,
  scroll = true,
  bottomInset = false,
  refreshing = false,
  onRefresh,
}: PropsWithChildren<{
  scroll?: boolean;
  bottomInset?: boolean;
  refreshing?: boolean;
  onRefresh?: () => void;
}>) {
  const content = <View style={styles.content}>{children}</View>;
  return (
    <SafeAreaView style={styles.safe} edges={["top", "left", "right"]}>
      {scroll ? (
        <ScrollView
          contentContainerStyle={[
            styles.scroll,
            bottomInset && styles.bottomInset,
          ]}
          keyboardShouldPersistTaps="handled"
          refreshControl={
            onRefresh ? (
              <RefreshControl
                refreshing={refreshing}
                onRefresh={onRefresh}
                tintColor={theme.colors.primary}
              />
            ) : undefined
          }
        >
          {content}
        </ScrollView>
      ) : (
        content
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safe: { flex: 1, backgroundColor: theme.colors.background },
  scroll: { flexGrow: 1 },
  content: {
    flex: 1,
    paddingHorizontal: theme.layout.screenGutter,
    paddingVertical: theme.spacing.sm,
    gap: theme.spacing.sm,
    width: "100%",
    maxWidth: theme.layout.contentMaxWidth,
    alignSelf: "center",
  },
  bottomInset: { paddingBottom: 104 },
});
