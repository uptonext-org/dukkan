import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, ScrollView, TextInput, View } from 'react-native';
import { useTranslation } from 'react-i18next';

import type { MarketStackScreenProps } from '../../../navigation/types';
import { errorMessageKey, isApiError, type ShoppingListResult } from '../../../shared/api';
import { useLang } from '../../../shared/i18n';
import { usePhotoPicker, type PhotoSource, type PickedPhoto } from '../../../shared/media';
import { SpeechInputError, useShoppingInput, type SpeechFailure } from '../../../shared/shoppingInput';
import { makeStyles, useTheme } from '../../../shared/theme';
import { AppText, Button, Icon, IconButton, Screen, ScreenHeader } from '../../../shared/ui';
import { useAuthGate } from '../../auth/AuthGate';
import { useCart } from '../../cart/api/hooks';
import { useMarketContext } from '../../market/MarketContext';
import { PartialApplyError, useApplyShoppingList, useReadShoppingList, useShoppingListCapabilities } from '../api/hooks';
import { ShoppingListLineRow } from '../components/ShoppingListLineRow';
import { appendText, cartUpdates, chosen, initialSelections, speechLanguage, type LineSelection } from '../model';

/** Video frames sent to the model: enough to see a fridge or a list, few enough to stay fast. */
const VIDEO_FRAMES = 4;

/**
 * Shopping list by voice, photo or video (F1): the customer dictates, types, photographs or films
 * what they need; Dukkan matches it to this market's products; the customer checks the lines and
 * adds them to the cart in one go. Speech is recognised on the phone, only text and still images
 * are sent. Without a language model on the server, photos and videos are not offered and a
 * dictated list is read by simple rules (a notice says so).
 */
export function ShoppingListScreen({ navigation }: MarketStackScreenProps<'ShoppingList'>) {
  const styles = useStyles();
  const { colors } = useTheme();
  const { t } = useTranslation();
  const lang = useLang();
  const { marketId } = useMarketContext();
  const { speech, video } = useShoppingInput();
  const photoPicker = usePhotoPicker();
  const { requireAuth } = useAuthGate();
  const capabilities = useShoppingListCapabilities();
  const speechAvailable = useQuery({
    queryKey: ['speechAvailable'],
    queryFn: () => speech.isAvailable(),
    staleTime: Infinity,
  });
  const read = useReadShoppingList(marketId);
  const apply = useApplyShoppingList(marketId);
  const cartQuery = useCart(marketId);
  const cart = cartQuery.data;

  const [text, setText] = useState('');
  const [images, setImages] = useState<PickedPhoto[]>([]);
  const [listening, setListening] = useState(false);
  // The final words may take a moment after "done"; a second tap must not stop twice.
  const [stopping, setStopping] = useState(false);
  const [live, setLive] = useState('');
  const [problem, setProblem] = useState<string | null>(null);
  const [result, setResult] = useState<ShoppingListResult | null>(null);
  const [selections, setSelections] = useState<LineSelection[]>([]);
  const listeningRef = useRef(false);

  const ai = capabilities.data?.ai === true;
  const maxImages = capabilities.data?.maxImages ?? 4;
  const maxText = capabilities.data?.maxTextLength ?? 2000;

  // Live transcript while listening; the recogniser may also stop by itself (error, time limit).
  useEffect(() => {
    if (!listening) {
      return undefined;
    }
    return speech.watch((snapshot) => {
      setLive(snapshot.transcript);
      if (!snapshot.listening && listeningRef.current) {
        listeningRef.current = false;
        setListening(false);
        setText((current) => appendText(current, snapshot.transcript).slice(0, maxText));
        setLive('');
        if (snapshot.error !== null) {
          setProblem(t(`shoppingList.speechError.${snapshot.error}`));
        }
      }
    });
  }, [listening, speech, maxText, t]);

  useEffect(
    () => () => {
      if (listeningRef.current) {
        speech.cancel();
      }
    },
    [speech],
  );

  const inCart = useMemo(
    () => new Map((cart?.items ?? []).map((item) => [item.productId, item.quantity])),
    [cart],
  );
  const updates = result === null ? [] : cartUpdates(result.lines, selections, inCart);
  // Lines that change the cart (a product already at its limit adds nothing).
  const changing = new Set(updates.map((u) => u.productId));
  const selectedCount = selections.filter((s, i) => {
    const line = result?.lines[i];
    const choice = line === undefined ? undefined : chosen(line, s);
    return s.selected && choice !== undefined && changing.has(choice.product.id);
  }).length;

  const startListening = async () => {
    setProblem(null);
    try {
      await speech.start(speechLanguage(lang));
      listeningRef.current = true;
      setLive('');
      setListening(true);
    } catch (error) {
      const reason: SpeechFailure = error instanceof SpeechInputError ? error.reason : 'failed';
      setProblem(t(`shoppingList.speechError.${reason}`));
    }
  };

  const stopListening = async () => {
    if (stopping) {
      return;
    }
    listeningRef.current = false;
    setStopping(true);
    try {
      const spoken = await speech.stop();
      setText((current) => appendText(current, spoken).slice(0, maxText));
    } finally {
      setStopping(false);
      setListening(false);
      setLive('');
    }
  };

  const addPhoto = async (source: PhotoSource) => {
    setProblem(null);
    const picked = await photoPicker.pick(source);
    if (picked.status === 'picked') {
      setImages((current) => [...current, picked.photo].slice(0, maxImages));
    } else if (picked.status === 'failed') {
      setProblem(
        t(picked.reason === 'permission' ? 'shoppingList.mediaPermission' : 'shoppingList.mediaFailed'),
      );
    }
  };

  const addVideo = async (source: PhotoSource) => {
    setProblem(null);
    const picked = await video.pick(source, Math.min(VIDEO_FRAMES, maxImages));
    if (picked.status === 'picked') {
      // A video replaces earlier images: its frames already use the whole budget.
      setImages(picked.frames.slice(0, maxImages));
    } else if (picked.status === 'failed') {
      setProblem(
        t(picked.reason === 'permission' ? 'shoppingList.mediaPermission' : 'shoppingList.videoFailed'),
      );
    }
  };

  const recognise = () =>
    requireAuth('cart', () => {
      setProblem(null);
      read.mutate(
        {
          text: text.trim() === '' ? undefined : text,
          images: images.map((image) => ({ uri: image.uri, type: image.type, name: image.fileName })),
        },
        {
          onSuccess: (answer) => {
            setResult(answer);
            setSelections(initialSelections(answer));
          },
        },
      );
    });

  const addToCart = () =>
    requireAuth('cart', () =>
      apply.mutate(updates, {
        onSuccess: () => navigation.navigate('MarketTabs', { screen: 'MarketCart' }),
        onError: (error) => {
          if (error instanceof PartialApplyError && result !== null) {
            // What is in the cart already is unchecked, so trying again adds only the rest.
            const applied = new Set(error.applied);
            setSelections((current) =>
              current.map((s, i) => {
                const line = result.lines[i];
                const choice = line === undefined ? undefined : chosen(line, s);
                return choice !== undefined && applied.has(choice.product.id) ? { ...s, selected: false } : s;
              }),
            );
          }
        },
      }),
    );

  const restart = () => {
    setResult(null);
    setSelections([]);
    setText('');
    setImages([]);
    read.reset();
    apply.reset();
  };

  const header = <ScreenHeader title={t('shoppingList.title')} onBack={() => navigation.goBack()} />;
  const canSend = (text.trim() !== '' || images.length > 0) && !listening;
  const readError = read.error !== null ? t(errorMessageKey(read.error)) : null;
  const applyError =
    apply.error !== null ? t(errorMessageKey(apply.error instanceof PartialApplyError ? apply.error.reason : apply.error)) : null;

  if (result !== null) {
    const unmatched = result.lines.filter((line) => line.match === null).length;
    return (
      <Screen
        padded={false}
        header={header}
        testID="shopping-list-result"
        footer={
          <View style={styles.footer}>
            {applyError !== null && (
              <AppText variant="subhead" color="accent" accessibilityRole="alert">
                {applyError}
              </AppText>
            )}
            <Button
              title={t('shoppingList.addToCart', { count: selectedCount })}
              icon="basket"
              onPress={addToCart}
              // The quantities add to the cart: wait until it is known.
              disabled={updates.length === 0 || !cartQuery.isSuccess}
              loading={apply.isPending || cartQuery.fetchStatus === 'fetching'}
              testID="shopping-list-add"
            />
          </View>
        }>
        <ScrollView contentContainerStyle={styles.content}>
          <AppText variant="subhead" color="textSecondary" accessibilityLiveRegion="polite">
            {result.lines.length === 0
              ? t('shoppingList.nothingFound')
              : t('shoppingList.found', { count: result.lines.length - unmatched }) +
                (unmatched > 0 ? ` ${t('shoppingList.notFound', { count: unmatched })}` : '')}
          </AppText>
          {result.engine === 'RULES' && (
            <View style={styles.notice} testID="shopping-list-rules-notice">
              <Icon name="alert" size={18} color={colors.textSecondary} />
              <AppText variant="subhead" color="textSecondary" style={styles.noticeText}>
                {t(ai ? 'shoppingList.rulesFallback' : 'shoppingList.rulesOnly')}
              </AppText>
            </View>
          )}
          {result.lines.map((line, i) => {
            const selection = selections[i];
            if (selection === undefined) {
              return null;
            }
            return (
              <ShoppingListLineRow
                key={`${i}-${line.text}`}
                index={i}
                line={line}
                selection={selection}
                onChange={(next) => setSelections((current) => current.map((s, j) => (j === i ? next : s)))}
              />
            );
          })}
          <Button
            title={t('shoppingList.again')}
            variant="secondary"
            icon="edit"
            onPress={restart}
            testID="shopping-list-again"
          />
        </ScrollView>
      </Screen>
    );
  }

  const shown = listening ? appendText(text, live) : text;
  return (
    <Screen
      padded={false}
      header={header}
      testID="shopping-list"
      footer={
        <View style={styles.footer}>
          <Button
            title={t('shoppingList.recognise')}
            icon="sparkles"
            onPress={recognise}
            disabled={!canSend}
            loading={read.isPending}
            testID="shopping-list-recognise"
          />
        </View>
      }>
      <ScrollView contentContainerStyle={styles.content} keyboardShouldPersistTaps="handled">
        <AppText variant="body" color="textSecondary">
          {t(ai ? 'shoppingList.intro' : 'shoppingList.introNoAi')}
        </AppText>

        {speechAvailable.data !== false && (
          <Pressable
            onPress={() => void (listening ? stopListening() : startListening())}
            disabled={stopping}
            accessibilityRole="button"
            accessibilityLabel={listening ? t('shoppingList.stopSpeaking') : t('shoppingList.speak')}
            accessibilityState={{ busy: listening, disabled: stopping }}
            style={({ pressed }) => [styles.mic, listening && styles.micActive, pressed && styles.pressed]}
            testID="shopping-list-mic">
            <Icon name="mic" size={28} color={listening ? colors.onAccent : colors.primary} />
            <AppText variant="headline" style={listening ? styles.micActiveText : undefined}>
              {listening ? t('shoppingList.listening') : t('shoppingList.speak')}
            </AppText>
          </Pressable>
        )}

        <TextInput
          value={shown}
          onChangeText={(value) => setText(value.slice(0, maxText))}
          editable={!listening}
          multiline
          placeholder={t('shoppingList.placeholder')}
          placeholderTextColor={colors.textPlaceholder}
          accessibilityLabel={t('shoppingList.textLabel')}
          style={styles.input}
          testID="shopping-list-text"
        />

        {ai ? (
          <View style={styles.media}>
            <AppText variant="headline">{t('shoppingList.mediaTitle')}</AppText>
            <AppText variant="subhead" color="textSecondary">
              {t('shoppingList.mediaHint')}
            </AppText>
            <View style={styles.mediaButtons}>
              <Button
                title={t('shoppingList.recordVideo')}
                variant="secondary"
                icon="video"
                onPress={() => void addVideo('camera')}
                testID="shopping-list-video"
              />
              <Button
                title={t('shoppingList.takePhoto')}
                variant="secondary"
                icon="camera"
                onPress={() => void addPhoto('camera')}
                disabled={images.length >= maxImages}
                testID="shopping-list-photo"
              />
              <Button
                title={t('shoppingList.fromLibrary')}
                variant="secondary"
                icon="image"
                onPress={() => void addPhoto('library')}
                disabled={images.length >= maxImages}
                testID="shopping-list-library"
              />
            </View>
            {images.length > 0 && (
              <View style={styles.thumbs}>
                <AppText variant="subhead" color="textSecondary">
                  {t('shoppingList.images', { count: images.length })}
                </AppText>
                <IconButton
                  icon="trash"
                  label={t('shoppingList.removeImages')}
                  onPress={() => setImages([])}
                  testID="shopping-list-clear-images"
                />
              </View>
            )}
          </View>
        ) : (
          capabilities.isSuccess && (
            <View style={styles.notice} testID="shopping-list-no-ai">
              <Icon name="alert" size={18} color={colors.textSecondary} />
              <AppText variant="subhead" color="textSecondary" style={styles.noticeText}>
                {t('shoppingList.noAi')}
              </AppText>
            </View>
          )
        )}

        {(problem ?? readError) !== null && (
          <AppText variant="subhead" color="accent" accessibilityRole="alert" testID="shopping-list-problem">
            {problem ??
              (isApiError(read.error) && read.error.code === 'SHOPPING_LIST_AI_UNAVAILABLE'
                ? t('shoppingList.noAi')
                : readError)}
          </AppText>
        )}
      </ScrollView>
    </Screen>
  );
}

const useStyles = makeStyles((t) => ({
  content: { padding: t.layout.gutter, gap: t.spacing.lg, paddingBottom: t.spacing.xxxl },
  footer: { paddingHorizontal: t.layout.gutter, paddingVertical: t.spacing.sm, gap: t.spacing.xs },
  mic: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: t.spacing.sm,
    minHeight: 64,
    borderRadius: 32,
    backgroundColor: t.colors.surfaceMuted,
  },
  micActive: { backgroundColor: t.colors.accentFill },
  micActiveText: { color: t.colors.onAccent },
  pressed: { opacity: 0.7 },
  input: {
    minHeight: 120,
    borderRadius: 16,
    padding: t.spacing.md,
    backgroundColor: t.colors.surfaceMuted,
    color: t.colors.text,
    fontSize: 17,
    lineHeight: 24,
    textAlignVertical: 'top',
  },
  media: { gap: t.spacing.sm },
  mediaButtons: { gap: t.spacing.sm },
  thumbs: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' },
  notice: {
    flexDirection: 'row',
    gap: t.spacing.sm,
    padding: t.spacing.md,
    borderRadius: 12,
    backgroundColor: t.colors.surfaceMuted,
  },
  noticeText: { flex: 1 },
}));
