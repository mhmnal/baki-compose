# Baki Design Law

Baki is an **auditable field ledger**.

Every number should look like it can be checked.

## Three materials

### Paper — money records

Monthly statement, week close, day ledger, Spend history, Shifts history.

- white paper surface
- 1 px ink rule at 55%
- 4 dp corners, shallow side notches
- 5 dp offset pine shadow at 18%
- dashed rules between entries
- monospace metadata
- at most one lime stamp per receipt
- receipt entrance: 280 ms fade/rise
- receipt lines: 220 ms, 45 ms stagger, cap at 8 delays

Paper is for **reading a record**, never for data entry.

### Instrument — analysis

Charts, heatmaps, trends, comparisons, envelopes.

- warm tinted fill
- 24 dp+ radius
- no border
- no offset shadow
- no receipt lines

An instrument panel may sit near paper, but must not compete with it.

### Action — capture and control

Forms, sheets, buttons, pickers, FABs, settings, confirmations.

- standard Material 3 Expressive
- 48 dp touch targets
- accessible semantics
- no notches, receipt rules or paper stamps

Input should feel easy. Records should feel exact.

## Weight

```text
Paper       100%
Instrument   40%
Action       20%
Never alternate 100 → 90 → 100.
```

If two adjacent surfaces have equal visual weight, one is wrong.

## Type

- Display numbers: Archivo variable, tabular figures.
- Body: normal Baki sans.
- Metadata and receipt headers: monospace, uppercase, tracked.
- Never set body copy in monospace.
- Never use all-caps for paragraphs.
- Receipt grammar: `/ SECTION`, `01`, `■`, `RM … OUT`, `RM … NET`.

## Colour

```text
background     #F4EFE6
paper          #FFFCF5
pine           #0B331E
terracotta     #B8432A
amber          #7A5100
lime           #DFF56A
```

Lime is reserved for one of: positive month movement, closed week, reconciled state,
current/selected document stamp. Never a page background or general button colour.

## Shape and elevation

```text
receipt corner      2–4 dp
action radius       24 dp+
instrument radius   24 dp+
paper border        1 px outline at 55%
paper shadow        5 dp offset, pine at 18%
instrument shadow   none
```

A surface may have **either** a border plus offset shadow, **or** soft tinted elevation.
Never both.

## Motion

- Paper entrance: 280 ms, 12 dp rise, fade.
- Instrument entrance: 220–280 ms fade only.
- Receipt lines: 220 ms, 45 ms stagger, maximum 8 delayed lines.
- Actions: Material defaults.
- All paper/line motion respects `reducedMotion`.
- Motion reveals hierarchy. It must never delay reading or block input.
- No scroll hijacking, marquee information, hover-only discovery or looping decoration.

## Layout rhythm

- Paper documents sit together.
- A deliberate gap separates paper from analysis.
- Analysis follows paper.
- Controls precede the document list they act on.
- Do not sandwich a heavy instrument between two receipts.

## Surface map

| Surface | Material |
|---|---|
| Today day ledger | paper |
| Spend history | paper, one per day |
| Shifts history | paper, one per week |
| Monthly statement | paper |
| Week close | paper |
| Charts, envelopes | instrument |
| Forms, sheets, settings | action |

## Do not

- use paper for forms or buttons
- put notches on every card
- use acid-yellow full backgrounds
- use a pixel font for body text
- use scroll-driven storytelling
- add marquees, hovers or animation that do not communicate state
- introduce a second high-energy accent
- let Material defaults decide Baki's visual identity

## Decision test

Before adding a surface:

1. Is this a record, an interpretation or an action?
2. Does it have a clear material tier?
3. Does it compete with the document beside it?
4. Would removing the styling make it harder to audit?
5. Does it work in dark mode, large font, reduced motion and TalkBack?

If the answer is unclear, it is not ready.
