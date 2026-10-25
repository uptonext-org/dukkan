import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { errorMessageKey } from '../../../shared/api';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Button, Icon, TextField } from '../../../shared/ui';
import { useAuth } from '../../auth/AuthProvider';
import { useAuthGate } from '../../auth/AuthGate';
import { useFeedbackAvailability, useSendFeedback } from '../api/hooks';

/**
 * Feedback to the market at the bottom of its start page, shown only while the merchant takes
 * feedback. An optional 1–5 star rating and a message; guests sign in first.
 */
export function MarketFeedbackCard({ marketId, marketName }: { marketId: string; marketName: string }) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const { status } = useAuth();
  const { requireAuth } = useAuthGate();
  const availability = useFeedbackAvailability(marketId);
  const send = useSendFeedback(marketId);
  const [message, setMessage] = useState('');
  const [rating, setRating] = useState<number | null>(null);
  const [problem, setProblem] = useState<string | undefined>();

  if (availability.data?.enabled !== true) {
    return null;
  }
  const maxLength = availability.data.maxLength;
  const submit = () => {
    if (message.trim() === '') {
      setProblem(t('feedback.empty'));
      return;
    }
    setProblem(undefined);
    send.mutate(
      { message: message.trim(), rating },
      {
        onSuccess: () => {
          setMessage('');
          setRating(null);
        },
      },
    );
  };

  return (
    <View style={styles.card} testID="market-feedback">
      <View style={styles.title}>
        <Icon name="message" size={20} color={colors.primary} />
        <AppText variant="headline" style={styles.flex}>
          {t('feedback.title')}
        </AppText>
      </View>
      <AppText variant="subhead" color="textSecondary">
        {t('feedback.hint', { market: marketName })}
      </AppText>
      {status !== 'signedIn' ? (
        <Button title={t('feedback.signIn')} variant="secondary" onPress={() => requireAuth('account')} testID="feedback-sign-in" />
      ) : send.isSuccess ? (
        <View style={styles.gap}>
          <AppText variant="callout" accessibilityLiveRegion="polite" testID="feedback-sent">
            {t('feedback.sent')}
          </AppText>
          <Button title={t('feedback.another')} variant="ghost" onPress={() => send.reset()} />
        </View>
      ) : (
        <View style={styles.gap}>
          <View style={styles.stars} accessibilityRole="radiogroup" accessibilityLabel={t('feedback.rating')}>
            {[1, 2, 3, 4, 5].map((n) => (
              <Pressable
                key={n}
                onPress={() => setRating(rating === n ? null : n)}
                accessibilityRole="radio"
                accessibilityState={{ checked: rating === n }}
                accessibilityLabel={t('feedback.stars', { n })}
                hitSlop={6}
                style={styles.star}
                testID={`feedback-star-${n}`}>
                <Icon name="star" size={26} color={rating !== null && n <= rating ? colors.primary : colors.textSecondary} />
              </Pressable>
            ))}
          </View>
          <TextField
            label={t('feedback.message')}
            value={message}
            onChangeText={setMessage}
            multiline
            maxLength={maxLength}
            error={problem}
            testID="feedback-message"
          />
          <Button title={t('feedback.send')} onPress={submit} loading={send.isPending} testID="feedback-send" />
          {send.isError && (
            <AppText variant="caption" color="accent" accessibilityRole="alert" testID="feedback-error">
              {t(errorMessageKey(send.error))}
            </AppText>
          )}
        </View>
      )}
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  card: { backgroundColor: t.colors.surface, borderRadius: t.radii.tile, padding: t.spacing.lg, gap: t.spacing.sm, borderWidth: 1, borderColor: t.colors.separator },
  title: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.sm },
  flex: { flex: 1 },
  gap: { gap: t.spacing.sm },
  stars: { flexDirection: 'row', gap: t.spacing.xs },
  star: { minWidth: 44, minHeight: 44, alignItems: 'center', justifyContent: 'center' },
}));
