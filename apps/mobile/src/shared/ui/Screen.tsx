import type { ReactNode } from 'react';
import { ScrollView, View, type StyleProp, type ViewStyle } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTranslation } from 'react-i18next';

import { makeStyles } from '../theme';
import { AppText } from './AppText';
import { IconButton } from './Button';

interface ScreenProps {
  children: ReactNode;
  scroll?: boolean;
  padded?: boolean;
  /** Adds the top safe-area inset (screens without a header). */
  safeTop?: boolean;
  footer?: ReactNode;
  header?: ReactNode;
  style?: StyleProp<ViewStyle>;
  testID?: string;
}

export function Screen({ children, scroll = false, padded = true, safeTop = false, footer, header, style, testID }: ScreenProps) {
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const contentStyle = [padded && styles.padded, style];
  return (
    <View style={[styles.root, safeTop && { paddingTop: insets.top }]} testID={testID}>
      {header}
      {scroll ? (
        <ScrollView contentContainerStyle={contentStyle} keyboardShouldPersistTaps="handled">
          {children}
        </ScrollView>
      ) : (
        <View style={[styles.fill, contentStyle]}>{children}</View>
      )}
      {footer !== undefined && <View style={[styles.footer, { paddingBottom: Math.max(insets.bottom, 12) }]}>{footer}</View>}
    </View>
  );
}

interface ScreenHeaderProps {
  title: string;
  onBack?: () => void;
  /** `close` for modal-like flows (X), `back` for pushes (arrow, mirrored in RTL). */
  backIcon?: 'back' | 'close';
  trailing?: ReactNode;
  large?: boolean;
}

/**
 * JS header used instead of the native-stack header so it mirrors instantly when the language
 * changes to Arabic (native headers only follow I18nManager after a restart).
 */
export function ScreenHeader({ title, onBack, backIcon = 'back', trailing, large = false }: ScreenHeaderProps) {
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const { t } = useTranslation();
  return (
    <View style={[styles.header, { paddingTop: insets.top + 4 }]}>
      <View style={styles.headerRow}>
        {onBack !== undefined ? (
          <IconButton icon={backIcon} label={backIcon === 'close' ? t('common.close') : t('common.back')} onPress={onBack} />
        ) : (
          <View style={styles.spacer} />
        )}
        {!large && (
          <AppText variant="headline" dense numberOfLines={1} style={styles.title} accessibilityRole="header">
            {title}
          </AppText>
        )}
        <View style={styles.trailing}>{trailing}</View>
      </View>
      {large && (
        <AppText variant="largeTitle" accessibilityRole="header" style={styles.largeTitle}>
          {title}
        </AppText>
      )}
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  root: { flex: 1, backgroundColor: t.colors.bg },
  fill: { flex: 1 },
  padded: { padding: t.layout.gutter },
  footer: {
    paddingHorizontal: t.layout.gutter,
    paddingTop: t.spacing.md,
    backgroundColor: t.colors.bg,
    borderTopWidth: 1,
    borderTopColor: t.colors.separator,
  },
  header: { backgroundColor: t.colors.bg, paddingHorizontal: t.spacing.xs },
  headerRow: { flexDirection: 'row', alignItems: 'center', minHeight: 48 },
  title: { flex: 1, textAlign: 'center' },
  spacer: { width: 44 },
  trailing: { minWidth: 44, flexDirection: 'row', justifyContent: 'flex-end' },
  largeTitle: { paddingHorizontal: t.spacing.md, paddingBottom: t.spacing.sm },
}));

/** Section heading inside scroll content. */
export function SectionTitle({ title, subtitle, trailing }: { title: string; subtitle?: string | null; trailing?: ReactNode }) {
  const styles = useSectionStyles();
  // The trailing action ("Alle ›") sits in the title's row and is centred on its first line, so
  // it lines up with the heading instead of the subtitle below it.
  return (
    <View style={styles.block}>
      <View style={styles.row}>
        <AppText variant="title" accessibilityRole="header" style={styles.title}>
          {title}
        </AppText>
        {trailing !== undefined && <View style={styles.trailing}>{trailing}</View>}
      </View>
      {subtitle != null && subtitle !== '' && (
        <AppText variant="subhead" color="textSecondary">
          {subtitle}
        </AppText>
      )}
    </View>
  );
}

const useSectionStyles = makeStyles((t) => ({
  block: { gap: 2, marginBottom: t.spacing.md },
  row: { flexDirection: 'row', alignItems: 'flex-start', gap: t.spacing.sm },
  title: { flex: 1 },
  // Centre the 44 pt action on the title's first line.
  trailing: { marginTop: ((t.type.title.lineHeight ?? 30) - t.touchTarget) / 2 },
}));
