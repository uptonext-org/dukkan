/*
 * Icon set: lucide-react-native (ISC) on react-native-svg, imported per icon so Metro bundles
 * only what we use. One consistent stroke style (icons.md: "one visual language").
 * Icons are decorative: the surrounding control carries the accessibility label.
 */
import Apple from 'lucide-react-native/icons/apple';
import ArrowLeft from 'lucide-react-native/icons/arrow-left';
import ArrowUpDown from 'lucide-react-native/icons/arrow-up-down';
import Banana from 'lucide-react-native/icons/banana';
import Bean from 'lucide-react-native/icons/bean';
import Beef from 'lucide-react-native/icons/beef';
import Beer from 'lucide-react-native/icons/beer';
import Bell from 'lucide-react-native/icons/bell';
import CakeSlice from 'lucide-react-native/icons/cake-slice';
import Camera from 'lucide-react-native/icons/camera';
import Candy from 'lucide-react-native/icons/candy';
import Carrot from 'lucide-react-native/icons/carrot';
import Check from 'lucide-react-native/icons/check';
import Cherry from 'lucide-react-native/icons/cherry';
import ChevronDown from 'lucide-react-native/icons/chevron-down';
import ChevronRight from 'lucide-react-native/icons/chevron-right';
import CircleAlert from 'lucide-react-native/icons/circle-alert';
import CircleCheck from 'lucide-react-native/icons/circle-check';
import Citrus from 'lucide-react-native/icons/citrus';
import ClipboardList from 'lucide-react-native/icons/clipboard-list';
import Clock from 'lucide-react-native/icons/clock';
import Coffee from 'lucide-react-native/icons/coffee';
import Croissant from 'lucide-react-native/icons/croissant';
import CupSoda from 'lucide-react-native/icons/cup-soda';
import Droplet from 'lucide-react-native/icons/droplet';
import Drumstick from 'lucide-react-native/icons/drumstick';
import Egg from 'lucide-react-native/icons/egg';
import Ellipsis from 'lucide-react-native/icons/ellipsis';
import FishIcon from 'lucide-react-native/icons/fish';
import Flame from 'lucide-react-native/icons/flame';
import Globe from 'lucide-react-native/icons/globe';
import Ham from 'lucide-react-native/icons/ham';
import Heart from 'lucide-react-native/icons/heart';
import House from 'lucide-react-native/icons/house';
import ImageIcon from 'lucide-react-native/icons/image';
import Leaf from 'lucide-react-native/icons/leaf';
import LeafyGreen from 'lucide-react-native/icons/leafy-green';
import List from 'lucide-react-native/icons/list';
import LocateFixed from 'lucide-react-native/icons/locate-fixed';
import Lock from 'lucide-react-native/icons/lock';
import LogOut from 'lucide-react-native/icons/log-out';
import MapIcon from 'lucide-react-native/icons/map';
import MapPin from 'lucide-react-native/icons/map-pin';
import Mic from 'lucide-react-native/icons/mic';
import Milk from 'lucide-react-native/icons/milk';
import Minus from 'lucide-react-native/icons/minus';
import Nut from 'lucide-react-native/icons/nut';
import Package from 'lucide-react-native/icons/package';
import Pencil from 'lucide-react-native/icons/pencil';
import Percent from 'lucide-react-native/icons/percent';
import Plus from 'lucide-react-native/icons/plus';
import ReceiptText from 'lucide-react-native/icons/receipt-text';
import Salad from 'lucide-react-native/icons/salad';
import Sandwich from 'lucide-react-native/icons/sandwich';
import Search from 'lucide-react-native/icons/search';
import ShoppingBasket from 'lucide-react-native/icons/shopping-basket';
import SlidersHorizontal from 'lucide-react-native/icons/sliders-horizontal';
import Snowflake from 'lucide-react-native/icons/snowflake';
import Soup from 'lucide-react-native/icons/soup';
import Sparkles from 'lucide-react-native/icons/sparkles';
import Store from 'lucide-react-native/icons/store';
import Tag from 'lucide-react-native/icons/tag';
import Download from 'lucide-react-native/icons/download';
import Trash from 'lucide-react-native/icons/trash';
import Truck from 'lucide-react-native/icons/truck';
import UserRound from 'lucide-react-native/icons/user-round';
import Video from 'lucide-react-native/icons/video';
import Wheat from 'lucide-react-native/icons/wheat';
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
  image: ImageIcon,
  camera: Camera,
  leaf: Leaf,
  list: List,
  locate: LocateFixed,
  lock: Lock,
  logout: LogOut,
  map: MapIcon,
  pin: MapPin,
  mic: Mic,
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
  download: Download,
  truck: Truck,
  user: UserRound,
  video: Video,
  offline: WifiOff,
  close: X,
  // Product placeholders (see productPlaceholder.ts)
  apple: Apple,
  banana: Banana,
  bean: Bean,
  beef: Beef,
  beer: Beer,
  bell: Bell,
  cake: CakeSlice,
  candy: Candy,
  carrot: Carrot,
  cherry: Cherry,
  citrus: Citrus,
  coffee: Coffee,
  croissant: Croissant,
  soda: CupSoda,
  droplet: Droplet,
  drumstick: Drumstick,
  egg: Egg,
  fish: FishIcon,
  flame: Flame,
  ham: Ham,
  leafyGreen: LeafyGreen,
  milk: Milk,
  nut: Nut,
  salad: Salad,
  sandwich: Sandwich,
  snowflake: Snowflake,
  soup: Soup,
  wheat: Wheat,
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
