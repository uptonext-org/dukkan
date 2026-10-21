import { useState } from 'react';
import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { errorMessageKey, type ProductDraft, type ProductDraftWarning } from '../../../shared/api';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Button, Icon, TextField } from '../../../shared/ui';
import { useProductDraft } from '../api/hooks';
import { PhotoField } from './PhotoField';

/** Media URLs end in their content-addressed key (`<sha256>.<ext>`), which the draft API takes. */
const MEDIA_KEY = /([0-9a-f]{64}\.(?:jpg|png|webp))$/;

export function mediaKeyOf(url: string | null): string | null {
  const match = url === null ? null : MEDIA_KEY.exec(url);
  return match?.[1] ?? null;
}

const NOTE_KEY = {
  AI_UNAVAILABLE: 'merchant.capture.aiUnavailable',
  AI_DECLINED: 'merchant.capture.aiDeclined',
  IMAGE_NOT_ANALYSED: 'merchant.capture.imageNotAnalysed',
} as const satisfies Partial<Record<ProductDraftWarning, string>>;

const MISSING_KEY = {
  NAME_MISSING: 'merchant.productName',
  CATEGORY_UNCERTAIN: 'merchant.category',
  UNIT_SIZE_MISSING: 'merchant.unitSize',
  PRICE_MISSING: 'merchant.price',
} as const satisfies Partial<Record<ProductDraftWarning, string>>;

function isNote(w: ProductDraftWarning): w is keyof typeof NOTE_KEY {
  return w in NOTE_KEY;
}

function isMissing(w: ProductDraftWarning): w is keyof typeof MISSING_KEY {
  return w in MISSING_KEY;
}

/**
 * "Schnell erfassen" (B-33): the merchant types or dictates a short note (keyboard dictation)
 * and/or photographs the pack; the server returns a draft that fills the form below. Nothing is
 * saved until the merchant creates the product. The result note says whether the AI or the
 * rule-based reader produced it and what is still missing.
 */
export function CapturePanel({ marketId, onDraft }: { marketId: string; onDraft: (draft: ProductDraft) => void }) {
  const styles = useStyles();
  const { t } = useTranslation();
  const { colors } = useTheme();
  const draft = useProductDraft(marketId);
  const [text, setText] = useState('');
  const [photoUrl, setPhotoUrl] = useState<string | null>(null);
  const [needInput, setNeedInput] = useState(false);
  const [result, setResult] = useState<ProductDraft | null>(null);

  const submit = () => {
    const imageKey = mediaKeyOf(photoUrl);
    const trimmed = text.trim();
    if (trimmed === '' && imageKey === null) {
      setNeedInput(true);
      return;
    }
    setNeedInput(false);
    draft.mutate(
      { ...(trimmed !== '' ? { text: trimmed } : {}), ...(imageKey !== null ? { imageKey } : {}) },
      {
        onSuccess: (d) => {
          setResult(d);
          onDraft(d);
        },
      },
    );
  };

  const notes = result?.warnings.filter(isNote) ?? [];
  const missing = result?.warnings.filter(isMissing) ?? [];

  return (
    <View style={styles.card} testID="capture-panel">
      <View style={styles.titleRow}>
        <Icon name="sparkles" size={22} color={colors.primary} />
        <AppText variant="headline" accessibilityRole="header">
          {t('merchant.capture.title')}
        </AppText>
      </View>
      <AppText variant="subhead" color="textSecondary">
        {t('merchant.capture.intro')}
      </AppText>
      <View style={styles.gap} />
      <TextField
        label={t('merchant.capture.textLabel')}
        value={text}
        onChangeText={setText}
        placeholder={t('merchant.capture.placeholder')}
        hint={t('merchant.capture.dictationHint')}
        multiline
        maxLength={1000}
        error={needInput ? t('merchant.capture.needInput') : undefined}
        testID="capture-text"
      />
      <PhotoField label={t('merchant.capture.photoLabel')} marketId={marketId} purpose="PRODUCT" value={photoUrl} onChange={setPhotoUrl} placeholderIcon="camera" testID="capture-photo" />
      <View style={styles.gap} />
      <Button
        title={draft.isPending ? t('merchant.capture.working') : t('merchant.capture.submit')}
        icon="sparkles"
        onPress={submit}
        loading={draft.isPending}
        testID="capture-submit"
      />
      {draft.isError && (
        <AppText variant="caption" color="accent" accessibilityRole="alert" testID="capture-error">
          {t(errorMessageKey(draft.error))}
        </AppText>
      )}
      {result !== null && !draft.isPending && (
        <View style={styles.result} accessibilityLiveRegion="polite" testID="capture-result">
          <AppText variant="subhead">{t(result.engine === 'AI' ? 'merchant.capture.resultAi' : 'merchant.capture.resultRules')}</AppText>
          {notes.map((w) => (
            <AppText key={w} variant="caption" color="textSecondary">
              {t(NOTE_KEY[w])}
            </AppText>
          ))}
          {missing.length > 0 && (
            <AppText variant="caption" color="accent" testID="capture-missing">
              {t('merchant.capture.missing', { fields: missing.map((w) => t(MISSING_KEY[w])).join(', ') })}
            </AppText>
          )}
        </View>
      )}
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  card: { backgroundColor: t.colors.surface, borderWidth: 1, borderColor: t.colors.separator, borderRadius: t.radii.tile, padding: t.spacing.md, gap: t.spacing.xs, marginBottom: t.spacing.lg },
  titleRow: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.sm },
  gap: { height: t.spacing.xs },
  result: { marginTop: t.spacing.sm, gap: t.spacing.xs, paddingTop: t.spacing.sm, borderTopWidth: 1, borderTopColor: t.colors.separator },
}));
