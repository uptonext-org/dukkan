import { useLayoutEffect, useRef, useState, type KeyboardEvent } from 'react';

export interface Column {
  key: string;
  /** Axis label; the chart leaves some out when they would not fit. */
  label: string;
  /** Full name of the column for the tooltip and the table, e.g. a date. */
  title: string;
  value: number;
}

const HEIGHT = 200;
const PAD = { top: 12, right: 16, bottom: 24, left: 48 };

/** Axis text sits in a left-to-right chart; bidi marks from Arabic date formats would reorder it. */
const plain = (text: string) => text.replace(/[\u200e\u200f\u061c]/g, '');
const MAX_BAR = 24;

/** 0 and up to four clean steps (1, 2, 5 × 10ⁿ) covering {@code max}. */
export function niceTicks(max: number): number[] {
  if (max <= 0) {
    return [0, 1];
  }
  const rough = max / 4;
  const power = 10 ** Math.floor(Math.log10(rough));
  const step = [1, 2, 5, 10].map((m) => m * power).find((s) => s >= rough) ?? 10 * power;
  const ticks = [];
  for (let v = 0; v < max + step; v += step) {
    ticks.push(Math.round(v * 1e6) / 1e6);
    if (v >= max) {
      break;
    }
  }
  return ticks;
}

/** Column path: 4px rounded data end, square at the baseline. */
function columnPath(x: number, y: number, width: number, base: number): string {
  const r = Math.min(4, width / 2, base - y);
  return `M${x},${base}V${y + r}Q${x},${y} ${x + r},${y}H${x + width - r}Q${x + width},${y} ${x + width},${y + r}V${base}Z`;
}

/**
 * One series as columns from a single baseline, with a hover/focus tooltip (arrow keys move it)
 * and recessive hairline grid. The caller provides a table view with the same values.
 */
export function ColumnChart({
  columns,
  format,
  label,
  labelAnchor = 'start',
}: {
  columns: Column[];
  format: (value: number) => string;
  label: string;
  /** Which end always keeps its axis label when labels are thinned out. */
  labelAnchor?: 'start' | 'end';
}) {
  const box = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(720);
  const [active, setActive] = useState<number | null>(null);

  useLayoutEffect(() => {
    const element = box.current;
    if (element === null || typeof ResizeObserver === 'undefined') {
      return;
    }
    const observer = new ResizeObserver(([entry]) => {
      if (entry !== undefined && entry.contentRect.width > 0) {
        setWidth(entry.contentRect.width);
      }
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  const ticks = niceTicks(Math.max(0, ...columns.map((c) => c.value)));
  const top = ticks[ticks.length - 1] ?? 1;
  const plotWidth = Math.max(1, width - PAD.left - PAD.right);
  const base = HEIGHT - PAD.bottom;
  const band = plotWidth / Math.max(1, columns.length);
  // Touching columns keep a 2px surface gap.
  const barWidth = Math.max(2, Math.min(MAX_BAR, band * 0.7, band - 2));
  // At least ~44px per label, so they never collide.
  const labelStep = Math.max(1, Math.ceil(44 / band));
  const showLabel = (index: number) => (labelAnchor === 'start' ? index : columns.length - 1 - index) % labelStep === 0;
  const y = (v: number) => PAD.top + (1 - v / top) * (base - PAD.top);
  const current = active === null ? undefined : columns[active];

  const onKey = (event: KeyboardEvent) => {
    if (columns.length === 0) {
      return;
    }
    const step = event.key === 'ArrowRight' ? 1 : event.key === 'ArrowLeft' ? -1 : 0;
    if (step !== 0) {
      event.preventDefault();
      setActive((index) => Math.min(columns.length - 1, Math.max(0, (index ?? -1) + step)));
    } else if (event.key === 'Escape') {
      setActive(null);
    }
  };

  return (
    // Time runs left to right in every language, as on receipts and calendars.
    <div className="chart" ref={box} dir="ltr">
      <svg
        width={width}
        height={HEIGHT}
        role="img"
        aria-label={label}
        tabIndex={0}
        onKeyDown={onKey}
        onFocus={() => setActive((index) => index ?? columns.length - 1)}
        onBlur={() => setActive(null)}
        onPointerLeave={() => setActive(null)}
      >
        {ticks.map((tick) => (
          <g key={tick}>
            <line x1={PAD.left} x2={width - PAD.right} y1={y(tick)} y2={y(tick)} className="chart-grid" />
            <text x={PAD.left - 6} y={y(tick)} className="chart-tick" textAnchor="end" dominantBaseline="middle">
              {plain(format(tick))}
            </text>
          </g>
        ))}
        {columns.map((column, index) => {
          const x = PAD.left + index * band + (band - barWidth) / 2;
          return (
            <g key={column.key}>
              {column.value > 0 && <path d={columnPath(x, y(column.value), barWidth, base)} className={`chart-bar${active === index ? ' active' : ''}`} />}
              {showLabel(index) && (
                <text x={x + barWidth / 2} y={HEIGHT - 6} className="chart-tick" textAnchor="middle">
                  {plain(column.label)}
                </text>
              )}
              {/* The hit target is the whole band, not just the bar. */}
              <rect x={PAD.left + index * band} y={PAD.top} width={band} height={base - PAD.top} fill="transparent" onPointerEnter={() => setActive(index)} />
            </g>
          );
        })}
        <line x1={PAD.left} x2={width - PAD.right} y1={base} y2={base} className="chart-axis" />
      </svg>
      {current !== undefined && active !== null && (
        <div
          className="chart-tooltip"
          role="status"
          style={{
            left: Math.min(Math.max(PAD.left + active * band + band / 2, 70), width - 70),
            top: Math.max(0, y(current.value) - 8),
          }}
        >
          <strong>{format(current.value)}</strong>
          <span>{current.title}</span>
        </div>
      )}
    </div>
  );
}
