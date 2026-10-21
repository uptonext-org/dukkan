import type { ReactNode } from 'react';
import { KeyboardAvoidingView, Modal, Platform, Pressable, ScrollView, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useTranslation } from 'react-i18next';

import { useReduceMotion } from '../hooks/useReduceMotion';
import { useLanguage } from '../i18n/LanguageProvider';
import { makeStyles } from '../theme';
import { AppText } from './AppText';
import { IconButton } from './Button';

interface SheetProps {
  visible: boolean;
  onClose: () => void;
  title: string;
  children: ReactNode;
  footer?: ReactNode;
  testID?: string;
}

/**
 * Bottom sheet built on Modal (sheets.md: one at a time, a way out besides Done). Modal content is
 * a separate native window, so the layout direction is set again explicitly here.
 */
export function Sheet({ visible, onClose, title, children, footer, testID }: SheetProps) {
  const styles = useStyles();
  const insets = useSafeAreaInsets();
  const reduceMotion = useReduceMotion();
  const { direction } = useLanguage();
  const { t } = useTranslation();
  return (
    <Modal
      visible={visible}
      transparent
      animationType={reduceMotion ? 'fade' : 'slide'}
      onRequestClose={onClose}
      statusBarTranslucent
      testID={testID}>
      <View style={[styles.root, { direction }]}>
        <Pressable
          style={styles.backdrop}
          onPress={onClose}
          accessibilityRole="button"
          accessibilityLabel={t('common.close')}
        />
        <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
          <View style={[styles.sheet, { paddingBottom: Math.max(insets.bottom, 16) }]} accessibilityViewIsModal>
            <View style={styles.grabber} />
            <View style={styles.header}>
              <AppText variant="headline" accessibilityRole="header" style={styles.title}>
                {title}
              </AppText>
              <IconButton icon="close" label={t('common.close')} onPress={onClose} />
            </View>
            <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
              {children}
            </ScrollView>
            {footer !== undefined && <View style={styles.footer}>{footer}</View>}
          </View>
        </KeyboardAvoidingView>
      </View>
    </Modal>
  );
}

const useStyles = makeStyles((t) => ({
  root: { flex: 1, justifyContent: 'flex-end' },
  backdrop: { ...{ position: 'absolute' as const, top: 0, bottom: 0, start: 0, end: 0 }, backgroundColor: 'rgba(0,0,0,0.4)' },
  sheet: {
    backgroundColor: t.colors.surface,
    borderTopStartRadius: t.radii.sheet,
    borderTopEndRadius: t.radii.sheet,
    maxHeight: '88%',
  },
  grabber: {
    alignSelf: 'center',
    width: 36,
    height: 5,
    borderRadius: 3,
    backgroundColor: t.colors.separator,
    marginTop: t.spacing.sm,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingStart: t.layout.gutter,
    paddingEnd: t.spacing.xs,
  },
  title: { flex: 1 },
  content: { paddingHorizontal: t.layout.gutter, paddingBottom: t.spacing.lg },
  footer: { paddingHorizontal: t.layout.gutter, paddingTop: t.spacing.sm },
}));
