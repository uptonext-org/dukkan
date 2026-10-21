import { View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { Order } from '../../../shared/api';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Icon } from '../../../shared/ui';
import { isTerminalFailure, stepState, timelineSteps } from '../model';

/** Vertical status timeline (simulated fulfilment). Reading order is top to bottom in LTR and RTL. */
export function StatusTimeline({ order }: { order: Pick<Order, 'status' | 'fulfilmentType'> }) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  if (isTerminalFailure(order.status)) {
    return (
      <View style={styles.failed} accessible accessibilityLabel={t(`orderStatus.${order.status}`)}>
        <Icon name="alert" color={colors.accent} />
        <AppText variant="bodyStrong" color="accent">
          {t(`orderStatus.${order.status}`)}
        </AppText>
      </View>
    );
  }
  const steps = timelineSteps(order.fulfilmentType);
  return (
    <View accessibilityRole="list" testID="status-timeline">
      {steps.map((step, index) => {
        const state = stepState(order, step);
        const last = index === steps.length - 1;
        return (
          <View
            key={step}
            style={styles.step}
            accessible
            accessibilityLabel={`${t(`orderStatus.${step}`)}, ${t(`orderStatus.state.${state}`)}`}>
            <View style={styles.markerCol}>
              <View style={[styles.dot, state === 'done' && styles.dotDone, state === 'current' && styles.dotCurrent]}>
                {state === 'done' && <Icon name="check" size={14} color={colors.onAccent} strokeWidth={3} />}
              </View>
              {!last && <View style={[styles.line, state === 'done' && styles.lineDone]} />}
            </View>
            <AppText variant={state === 'current' ? 'bodyStrong' : 'body'} color={state === 'upcoming' ? 'textSecondary' : 'text'} style={styles.label}>
              {t(`orderStatus.${step}`)}
            </AppText>
          </View>
        );
      })}
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  step: { flexDirection: 'row', gap: t.spacing.md, minHeight: 48 },
  markerCol: { alignItems: 'center', width: 24 },
  dot: { width: 24, height: 24, borderRadius: 12, borderWidth: 2, borderColor: t.colors.separator, alignItems: 'center', justifyContent: 'center', backgroundColor: t.colors.bg },
  dotDone: { backgroundColor: t.colors.primaryStrong, borderColor: t.colors.primaryStrong },
  dotCurrent: { borderColor: t.colors.primary, borderWidth: 6 },
  line: { flex: 1, width: 2, backgroundColor: t.colors.separator },
  lineDone: { backgroundColor: t.colors.primaryStrong },
  label: { flex: 1, paddingTop: 1 },
  failed: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.sm },
}));
