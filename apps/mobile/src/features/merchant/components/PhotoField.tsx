import { useState } from 'react';
import { ActivityIndicator, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import { errorMessageKey, type MediaPurpose } from '../../../shared/api';
import { toUploadFile, usePhotoPicker, type PhotoPickFailure, type PhotoSource } from '../../../shared/media';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Button, ImageTile, type IconName } from '../../../shared/ui';
import { useUploadMedia } from '../api/hooks';

const PICK_FAILURE_KEY = {
  permission: 'merchant.photo.permissionDenied',
  camera_unavailable: 'merchant.photo.cameraUnavailable',
  other: 'merchant.photo.pickFailed',
} as const satisfies Record<PhotoPickFailure, string>;

interface PhotoFieldProps {
  label: string;
  marketId: string;
  purpose: MediaPurpose;
  /** Current image URL, or null. */
  value: string | null;
  /** Called with the uploaded URL, or null when the user removes the photo. */
  onChange: (url: string | null) => void;
  /** Busy state of the caller saving the value (e.g. a listing update). */
  saving?: boolean;
  aspectRatio?: number;
  placeholderIcon?: IconName;
  testID: string;
}

/**
 * Photo with "Foto wählen" (library), "Foto aufnehmen" (camera) and "Entfernen". The picked file
 * is uploaded right away; the field holds the returned URL. Errors use the API error mapping.
 */
export function PhotoField({ label, marketId, purpose, value, onChange, saving = false, aspectRatio = 1, placeholderIcon = 'image', testID }: PhotoFieldProps) {
  const styles = useStyles();
  const { t } = useTranslation();
  const { colors } = useTheme();
  const picker = usePhotoPicker();
  const upload = useUploadMedia(marketId);
  const [pickFailure, setPickFailure] = useState<PhotoPickFailure | null>(null);
  const busy = upload.isPending || saving;

  const choose = async (source: PhotoSource) => {
    setPickFailure(null);
    upload.reset();
    const result = await picker.pick(source);
    if (result.status === 'failed') {
      setPickFailure(result.reason);
      return;
    }
    if (result.status === 'cancelled') {
      return;
    }
    const { photo } = result;
    upload.mutate({ file: toUploadFile(photo), purpose }, { onSuccess: (media) => onChange(media.url) });
  };

  const error = pickFailure !== null ? t(PICK_FAILURE_KEY[pickFailure]) : upload.isError ? t(errorMessageKey(upload.error)) : undefined;

  return (
    <View style={styles.field} testID={testID}>
      <AppText variant="subhead" color="textSecondary">
        {label}
      </AppText>
      <View style={styles.row}>
        <View style={[styles.preview, { aspectRatio }]}>
          <ImageTile uri={value} aspectRatio={aspectRatio} radius={12} placeholderIcon={placeholderIcon} />
          {busy && (
            <View style={styles.overlay} testID={`${testID}-uploading`}>
              <ActivityIndicator color={colors.overlayText} accessibilityLabel={t('merchant.photo.uploading')} />
            </View>
          )}
        </View>
        <View style={styles.actions}>
          <Button
            title={t('merchant.photo.choose')}
            icon="image"
            variant="secondary"
            disabled={busy}
            onPress={() => void choose('library')}
            accessibilityLabel={t('merchant.photo.chooseFor', { label })}
            testID={`${testID}-choose`}
          />
          <Button
            title={t('merchant.photo.take')}
            icon="camera"
            variant="ghost"
            disabled={busy}
            onPress={() => void choose('camera')}
            accessibilityLabel={t('merchant.photo.takeFor', { label })}
            testID={`${testID}-camera`}
          />
          {value !== null && value !== '' && (
            <Button
              title={t('merchant.photo.remove')}
              icon="trash"
              variant="ghost"
              disabled={busy}
              onPress={() => {
                setPickFailure(null);
                upload.reset();
                onChange(null);
              }}
              accessibilityLabel={t('merchant.photo.removeFor', { label })}
              testID={`${testID}-remove`}
            />
          )}
        </View>
      </View>
      {busy && (
        <AppText variant="caption" color="textSecondary" accessibilityLiveRegion="polite">
          {t('merchant.photo.uploading')}
        </AppText>
      )}
      {error !== undefined && (
        <AppText variant="caption" color="accent" accessibilityRole="alert" testID={`${testID}-error`}>
          {error}
        </AppText>
      )}
    </View>
  );
}

const useStyles = makeStyles((t) => ({
  field: { gap: t.spacing.xs, marginTop: t.spacing.sm },
  row: { flexDirection: 'row', alignItems: 'center', gap: t.spacing.md },
  preview: { width: 96 },
  overlay: { ...{ position: 'absolute' as const, top: 0, bottom: 0, start: 0, end: 0 }, alignItems: 'center', justifyContent: 'center', backgroundColor: t.colors.scrim, borderRadius: 12 },
  actions: { flex: 1, gap: t.spacing.xs, alignItems: 'flex-start' },
}));
