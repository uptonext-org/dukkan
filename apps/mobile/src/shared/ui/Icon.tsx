/*
 * Icon set: lucide-react-native (ISC) on react-native-svg, imported per icon so Metro bundles
 * only what we use. One consistent stroke style (icons.md: "one visual language").
 * Icons are decorative: the surrounding control carries the accessibility label.
 */
import ArrowLeft from 'lucide-react-native/icons/arrow-left';
import ArrowUpDown from 'lucide-react-native/icons/arrow-up-down';
import Check from 'lucide-react-native/icons/check';
import ChevronDown from 'lucide-react-native/icons/chevron-down';
import ChevronRight from 'lucide-react-native/icons/chevron-right';
import CircleAlert from 'lucide-react-native/icons/circle-alert';
import CircleCheck from 'lucide-react-native/icons/circle-check';
import ClipboardList from 'lucide-react-native/icons/clipboard-list';
import Clock from 'lucide-react-native/icons/clock';
import Ellipsis from 'lucide-react-native/icons/ellipsis';
import Globe from 'lucide-react-native/icons/globe';
import Heart from 'lucide-react-native/icons/heart';
import House from 'lucide-react-native/icons/house';
import Leaf from 'lucide-react-native/icons/leaf';
import List from 'lucide-react-native/icons/list';
import LocateFixed from 'lucide-react-native/icons/locate-fixed';
import LogOut from 'lucide-react-native/icons/log-out';
import MapIcon from 'lucide-react-native/icons/map';
import MapPin from 'lucide-react-native/icons/map-pin';
import Minus from 'lucide-react-native/icons/minus';
import Package from 'lucide-react-native/icons/package';
import Pencil from 'lucide-react-native/icons/pencil';
import Percent from 'lucide-react-native/icons/percent';
import Plus from 'lucide-react-native/icons/plus';
import ReceiptText from 'lucide-react-native/icons/receipt-text';
import Search from 'lucide-react-native/icons/search';
import ShoppingBasket from 'lucide-react-native/icons/shopping-basket';
import SlidersHorizontal from 'lucide-react-native/icons/sliders-horizontal';
import Sparkles from 'lucide-react-native/icons/sparkles';
import Store from 'lucide-react-native/icons/store';
import Tag from 'lucide-react-native/icons/tag';
import Trash from 'lucide-react-native/icons/trash';
import Truck from 'lucide-react-native/icons/truck';
import UserRound from 'lucide-react-native/icons/user-round';
import WifiOff from 'lucide-react-native/icons/wifi-off';
import X from 'lucide-react-native/icons/x';
import type { ComponentType } from 'react';
import { View, type StyleProp, type ViewStyle } from 'react-native';
import type { SvgProps } from 'react-native-svg';

import { useLanguage } from '../i18n/LanguageProvider';
import { useTheme } from '../theme';

const ICONS = {
  back: ArrowLeft,
  sort: ArrowUpDown,
  check: Check,
  chevronDown: ChevronDown,
  chevron: ChevronRight,
  alert: CircleAlert,
  checkCircle: CircleCheck,
  orders: ClipboardList,
  clock: Clock,
  more: Ellipsis,
  globe: Globe,
  heart: Heart,
  home: House,
  leaf: Leaf,
  list: List,
  locate: LocateFixed,
  logout: LogOut,
  map: MapIcon,
  pin: MapPin,
  minus: Minus,
  package: Package,
  edit: Pencil,
  percent: Percent,
  plus: Plus,
  receipt: ReceiptText,
  search: Search,
  basket: ShoppingBasket,
  filter: SlidersHorizontal,
  sparkles: Sparkles,
  store: Store,
  tag: Tag,
  trash: Trash,
  truck: Truck,
  user: UserRound,
  offline: WifiOff,
  close: X,
} satisfies Record<string, ComponentType<SvgProps & { size?: number; strokeWidth?: number }>>;

export type IconName = keyof typeof ICONS;

/** Icons that show direction or forward/back motion flip in RTL (right-to-left.md › Interface icons). */
const DIRECTIONAL: ReadonlySet<IconName> = new Set<IconName>(['back', 'chevron']);

interface IconProps {
  name: IconName;
  size?: number;
  color?: string;
  filled?: boolean;
  strokeWidth?: number;
  style?: StyleProp<ViewStyle>;
}

export function Icon({ name, size = 24, color, filled = false, strokeWidth = 2, style }: IconProps) {
  const { colors } = useTheme();
  const { isRTL } = useLanguage();
  const Component = ICONS[name];
  const tint = color ?? colors.text;
  const mirrored = isRTL && DIRECTIONAL.has(name);
  return (
    <View
      accessible={false}
      importantForAccessibility="no-hide-descendants"
      style={[mirrored ? { transform: [{ scaleX: -1 }] } : null, style]}
      testID={`icon-${name}${mirrored ? '-mirrored' : ''}`}>
      <Component size={size} color={tint} strokeWidth={strokeWidth} fill={filled ? tint : 'none'} />
    </View>
  );
}
